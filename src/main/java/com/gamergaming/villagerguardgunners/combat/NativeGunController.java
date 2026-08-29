package com.gamergaming.villagerguardgunners.combat;

import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoMaintenance;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSharing;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardBurstSafetyAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalEnvelope;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfile;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfileDefinition;
import com.gamergaming.villagerguardgunners.combat.tactics.WeaponTacticalResolver;
import com.gamergaming.villagerguardgunners.combat.tactics.WeaponTacticalTraits;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.BurstData;
import com.tacz.guns.resource.pojo.data.gun.ChargeData;
import com.tacz.guns.resource.pojo.data.gun.ChargeType;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Converts guard AI decisions into TaCZ's native server-side gun operations.
 * Projectile creation, spread, recoil, sounds, fire modes, ammunition, heat,
 * reload scripts, and synchronization remain entirely owned by TaCZ.
 */
public final class NativeGunController {
    private static final int SHORT_RETRY_TICKS = 2;
    private static final int DRY_RETRY_TICKS = 20;
    private static final int OVERHEAT_RETRY_TICKS = 5;
    private static final float SCRIPT_SPREAD_MARGIN_DEGREES = 0.5F;
    private static final float PROTECTED_ENTITY_MOVEMENT_PER_SECOND = 6.0F;
    private static final float MAX_SAFETY_PADDING = 32.0F;
    private static final long MAX_BURST_DURATION_MILLIS = 5_000L;
    private static final double VANILLA_SPREAD_COMPONENT_SCALE = 0.0172275D;
    private static final float FALLBACK_ENGAGEMENT_RANGE = 16.0F;
    private static final float MIN_ENGAGEMENT_RANGE = 8.0F;
    private static final float DEFAULT_MAX_ENGAGEMENT_RANGE = 32.0F;
    private static final double DEFAULT_RETREAT_DISTANCE = 4.0D;
    private static final int DEFAULT_REQUIRED_VISIBLE_TICKS = 5;
    private static final double DEFAULT_AIM_TOLERANCE_DEGREES = 10.0D;

    private final Guard guard;
    private final IGunOperator operator;
    private final NativeTriggerState triggerState = new NativeTriggerState();
    private final NativeWeaponState weaponState = new NativeWeaponState();
    private final NativeFireCadence fireCadence = new NativeFireCadence();
    private final VillagerGuardGunnersConfig config;
    private final Supplier<ItemStack> mainHandSupplier;
    private final Supplier<Float> pitchSupplier;
    private final Supplier<Float> yawSupplier;

    private ItemStack trackedStack = ItemStack.EMPTY;
    private IGun trackedGun;
    private ResourceLocation trackedGunId;
    private String trackedGunType;
    private GunData gunData;
    private FireMode fireMode = FireMode.UNKNOWN;
    private ChargeData chargeData;
    private float engagementRange;
    private CachedTacticalState cachedTacticalState;
    private long nextGunDataRefreshGameTick = Long.MIN_VALUE;
    private long nativeBurstSafetyUntilTimestamp = -1L;
    private int trackedOrdersRevision = Integer.MIN_VALUE;
    private long dynamicExplosionRefreshGameTick = Long.MIN_VALUE;
    private float aimPitch;
    private float aimYaw;
    private ShootResult lastShootResult;
    private GuardAmmoSharing.SharingStatus lastAmmoSharingStatus =
            GuardAmmoSharing.SharingStatus.NOT_ATTEMPTED;
    private int lastAmmoSharedItems;

    public NativeGunController(Guard guard) {
        this.guard = guard;
        this.operator = IGunOperator.fromLivingEntity(guard);
        this.config = ModConfigs.GUARD_GUNNERS;
        this.mainHandSupplier = guard::getMainHandItem;
        this.pitchSupplier = () -> aimPitch;
        this.yawSupplier = () -> aimYaw;
        this.engagementRange = configuredFallbackEngagementRange();
        resetCachedTacticalState();
    }

    public static boolean isGun(ItemStack stack) {
        return stack.getItem() instanceof IGun;
    }

    /**
     * Cheap goal-selector check. A changed stack is allowed through once so the
     * controller can resolve its TaCZ gun data and clear old dry/suspended state.
     */
    public boolean canUseGunGoal(ItemStack heldStack) {
        if (heldStack != trackedStack) {
            return true;
        }

        long gameTime = guard.level().getGameTime();
        if (weaponState.isSuspended() || !hasValidFiringWindow()) {
            // Let Guard Villagers' lower-priority combat goals run while this
            // weapon has no coherent, readable firing window. Retry only in
            // the existing low-frequency metadata slot so a broken datapack
            // or contradictory explosive standoff cannot thrash the selector.
            return gameTime >= nextGunDataRefreshGameTick;
        }
        return weaponState.mayEngage(gameTime);
    }

    public void startAiming() {
        try {
            operator.aim(synchronizeWeapon());
        } catch (RuntimeException exception) {
            suspendInvalidWeapon();
        }
    }

    public void stopAiming() {
        try {
            operator.aim(false);
        } catch (RuntimeException exception) {
            suspendInvalidWeapon();
        }
        boolean burstSafetyArmed = hasNativeBurstSafetyState();
        GuardBurstSafetyAccess.setSuppressed(guard, burstSafetyArmed);
        if (!burstSafetyArmed) {
            GuardBurstSafetyAccess.clearSafetyCheck(guard);
        }
        triggerState.reset();
        fireCadence.reset();
    }

    public void tick(LivingEntity target, boolean mayFire, BooleanSupplier safeToFire) {
        tick(target, mayFire, safeToFire, safeToFire);
    }

    public void tick(
            LivingEntity target,
            boolean mayFire,
            BooleanSupplier safeToFire,
            BooleanSupplier burstSafetyCheck) {
        try {
            tickSafely(target, mayFire, safeToFire, burstSafetyCheck);
        } catch (RuntimeException exception) {
            // Third-party gun definitions can run Lua/property hooks from most
            // TaCZ operations. A malformed script suspends this weapon instead
            // of escaping into Guard Villagers' goal selector and server tick.
            suspendInvalidWeapon();
        }
    }

    private void tickSafely(
            LivingEntity target,
            boolean mayFire,
            BooleanSupplier safeToFire,
            BooleanSupplier burstSafetyCheck) {
        if (guard.level().isClientSide || !synchronizeWeapon()) {
            return;
        }

        long nowTimestamp = currentRelativeTimestamp();
        boolean weaponMayEngage = weaponState.mayEngage(guard.level().getGameTime());
        updateNativeBurstSafety(nowTimestamp, mayFire && weaponMayEngage);
        if (!weaponMayEngage) {
            // HOLD_FIRE deliberately keeps the gun goal selected while dry.
            // Preserve the normal retry cadence instead of issuing a rejected
            // native reload request every server tick during that hold.
            operator.aim(false);
            triggerState.reset();
            fireCadence.reset();
            return;
        }

        triggerState.tick();
        updateAim(target);
        operator.aim(true);

        if (!mayFire) {
            decayCharge();
            fireCadence.reset();
        }

        if (operator.getSynReloadState().getStateType().isReloading()
                || operator.getSynIsBolting()
                || operator.getSynDrawCoolDown() > 0) {
            if (mayFire) {
                decayCharge();
            }
            fireCadence.reset();
            return;
        }

        ItemStack gunStack = trackedStack;
        if (needsManualBolt(gunStack)) {
            if (triggerState.canAttemptAction()) {
                operator.bolt();
                triggerState.defer(SHORT_RETRY_TICKS);
            }
            if (mayFire) {
                decayCharge();
            }
            fireCadence.reset();
            return;
        }

        if (needsReload(gunStack)) {
            requestReload(gunStack);
            if (mayFire) {
                decayCharge();
            }
            fireCadence.reset();
            return;
        }

        if (!mayFire) {
            return;
        }

        long shootInterval = resolveShootInterval(gunStack);
        if (shootInterval < 1L) {
            suspendInvalidWeapon();
            return;
        }

        long shootCooldown = calculateShootCooldown(
                nowTimestamp,
                shootInterval,
                operator.getDataHolder().shootTimestamp);
        if (!triggerState.canAttemptAction()) {
            decayCharge();
            return;
        }
        if (!prepareCharge(shootCooldown) || shootCooldown > 0L) {
            return;
        }

        if (!safeToFire.getAsBoolean()) {
            decayCharge();
            fireCadence.reset();
            triggerState.defer(SHORT_RETRY_TICKS);
            return;
        }

        if (!supportsSubTickAutomaticFire(shootInterval)) {
            fireCadence.reset();
            handleShootResult(shoot(nowTimestamp, burstSafetyCheck));
            return;
        }

        fireCadence.beginTick(
                nowTimestamp,
                shootInterval,
                NativeFireCadence.MAX_SHOTS_PER_TICK);
        boolean firstDueShot = true;
        while (fireCadence.hasShotDue()) {
            long shotTimestamp = fireCadence.getDueTimestamp();
            if (calculateShootCooldown(
                    shotTimestamp,
                    shootInterval,
                    operator.getDataHolder().shootTimestamp) > 0L) {
                fireCadence.reset();
                return;
            }

            if (!firstDueShot && !burstSafetyCheck.getAsBoolean()) {
                fireCadence.reset();
                return;
            }

            ShootResult result = shoot(shotTimestamp, burstSafetyCheck);
            handleShootResult(result);
            if (result != ShootResult.SUCCESS) {
                fireCadence.reset();
                return;
            }
            fireCadence.recordSuccessfulShot();
            firstDueShot = false;
        }
    }

    private void updateNativeBurstSafety(long nowTimestamp, boolean mayFire) {
        if (hasNativeBurstSafetyState()) {
            if (!isNativeBurstActive(nowTimestamp) || !mayFire) {
                // TaCZ posts GunFireEvent before every scheduled burst round.
                // Once the expected window expires, keep the latch closed
                // until another already-validated trigger is attempted. This
                // prevents a delayed END-phase task from racing goal cleanup.
                GuardBurstSafetyAccess.setSuppressed(guard, true);
            }
        } else {
            GuardBurstSafetyAccess.setSuppressed(guard, false);
            GuardBurstSafetyAccess.clearSafetyCheck(guard);
        }
    }

    /**
     * Fixed clearance at the muzzle. Spread and blast radius are deliberately
     * excluded because both envelopes widen only after leaving the shooter.
     */
    public float getMuzzleSafetyPadding() {
        if (gunData == null || trackedGun == null) {
            return Float.NaN;
        }

        try {
            long burstDuration = resolveBurstDurationMillis();
            if (burstDuration > MAX_BURST_DURATION_MILLIS) {
                return Float.NaN;
            }

            double burstMovement = calculateBurstMovementPadding(burstDuration);
            double padding = config.safety.baseClearance.get() + burstMovement;
            return Double.isFinite(padding) && padding <= MAX_SAFETY_PADDING
                    ? (float) padding
                    : Float.NaN;
        } catch (RuntimeException exception) {
            suspendInvalidWeapon();
            return Float.NaN;
        }
    }

    /** Full direct-projectile clearance at the target distance. */
    public float getProjectileSafetyPadding(double shotDistance, float muzzlePadding) {
        if (!Float.isFinite(muzzlePadding)
                || muzzlePadding < 0.0F
                || !Double.isFinite(shotDistance)) {
            return Float.NaN;
        }

        try {
            float inaccuracy = resolveMaximumInaccuracy();
            if (!Float.isFinite(inaccuracy)) {
                return Float.NaN;
            }
            double spreadRadius = calculateSpreadRadius(
                    Math.max(0.0D, shotDistance),
                    inaccuracy + SCRIPT_SPREAD_MARGIN_DEGREES);
            double padding = muzzlePadding + spreadRadius;
            return Double.isFinite(padding) && padding <= MAX_SAFETY_PADDING
                    ? (float) padding
                    : Float.NaN;
        } catch (RuntimeException exception) {
            suspendInvalidWeapon();
            return Float.NaN;
        }
    }

    /**
     * Full clearance used after an explosive projectile has cleared the
     * shooter. For ordinary guns this is identical to projectile clearance.
     */
    public float getBlastSafetyPadding(float projectilePadding) {
        if (!Float.isFinite(projectilePadding) || projectilePadding < 0.0F) {
            return Float.NaN;
        }

        try {
            if (!refreshDynamicExplosionTacticalState(true)) {
                return Float.NaN;
            }
            double explosionRadius = cachedTacticalState.traits().explosionRadius();
            double padding = projectilePadding + explosionRadius;
            return Double.isFinite(explosionRadius)
                            && explosionRadius >= 0.0F
                            && Double.isFinite(padding)
                            && padding <= MAX_SAFETY_PADDING
                    ? (float) padding
                    : Float.NaN;
        } catch (RuntimeException exception) {
            suspendInvalidWeapon();
            return Float.NaN;
        }
    }

    /**
     * Returns the cached profile-adjusted distance at which movement stops
     * approaching. A missing, stale, or degraded tactical cache falls back to
     * the previous bounded TaCZ effective-range behavior.
     */
    public double getPreferredApproachDistance() {
        if (hasUsableCachedTacticalState()) {
            return cachedTacticalState.envelope().preferredApproachDistance();
        }
        return getCompatibilityPreferredEngagementRange();
    }

    /** Returns the cached profile-adjusted distance at which retreat begins. */
    public double getRetreatDistance() {
        if (hasUsableCachedTacticalState()) {
            return cachedTacticalState.envelope().retreatDistance();
        }
        return configuredFallbackRetreatDistance();
    }

    /** Returns the cached profile-adjusted continuous-visibility requirement. */
    public int getRequiredVisibleTicks() {
        if (hasUsableCachedTacticalState()) {
            return cachedTacticalState.envelope().requiredVisibleTicks();
        }
        return configuredFallbackRequiredVisibleTicks();
    }

    /** Returns the cached profile-adjusted maximum yaw and pitch error. */
    public double getAimToleranceDegrees() {
        if (hasUsableCachedTacticalState()) {
            return cachedTacticalState.envelope().aimToleranceDegrees();
        }
        return configuredFallbackAimToleranceDegrees();
    }

    /** Returns the cached lower firing bound, including explosive standoff. */
    public double getMinimumFiringRange() {
        if (hasUsableCachedTacticalState()) {
            return cachedTacticalState.envelope().minimumFiringDistance();
        }
        return 0.0D;
    }

    /** True only when current, readable metadata produced a coherent window. */
    public boolean hasValidFiringWindow() {
        return hasUsableCachedTacticalState()
                && cachedTacticalState.envelope().validFiringWindow();
    }

    /**
     * Cheap hot-loop gate over the immutable cached envelope. Missing,
     * unreadable, stale, or contradictory metadata fails closed.
     */
    public boolean canFireAtDistanceSquared(double distanceSquared) {
        return ordersRevisionIsCurrent()
                && hasValidFiringWindow()
                && cachedTacticalState.envelope().canFireAtDistanceSquared(distanceSquared);
    }

    private boolean ordersRevisionIsCurrent() {
        return trackedOrdersRevision == GuardOrdersAccess.getRevision(guard);
    }

    /**
     * Compatibility accessor retained for integrations compiled against the
     * original controller API. New movement code uses the double-precision
     * profile-aware accessor above.
     */
    public float getPreferredEngagementRange() {
        return (float) getPreferredApproachDistance();
    }

    private float getCompatibilityPreferredEngagementRange() {
        if (guard.getMainHandItem() != trackedStack
                || gunData == null
                || weaponState.isSuspended()) {
            return configuredFallbackEngagementRange();
        }
        float maximumRange = (float) effectiveMaximumRange();
        return Float.isFinite(maximumRange)
                ? Math.min(engagementRange, maximumRange)
                : engagementRange;
    }

    private boolean hasUsableCachedTacticalState() {
        return guard.getMainHandItem() == trackedStack
                && gunData != null
                && !weaponState.isSuspended()
                && cachedTacticalState.metadataReadable()
                && cachedTacticalState.gunId() != null
                && cachedTacticalState.gunId().equals(trackedGunId);
    }

    /** Returns the cached profile cap, or the configured cap before metadata is ready. */
    public float getMaximumFiringRange() {
        if (hasUsableCachedTacticalState()) {
            return (float) cachedTacticalState.envelope().maximumFiringDistance();
        }
        return normalizeMaximumFiringRange(
                (float) effectiveMaximumRange());
    }

    static float normalizeMaximumFiringRange(float range) {
        return Float.isFinite(range) && range >= MIN_ENGAGEMENT_RANGE
                ? range
                : DEFAULT_MAX_ENGAGEMENT_RANGE;
    }

    public boolean hasCurrentWeaponData(ItemStack heldStack) {
        return heldStack == trackedStack
                && gunData != null
                && !weaponState.isSuspended();
    }

    /** Explosive traits are read from the cached tactical snapshot. */
    public boolean isWeaponAllowedByOrders() {
        if (!refreshDynamicExplosionTacticalState()) {
            return false;
        }
        boolean currentExplosiveWeapon = guard.getMainHandItem() == trackedStack
                && cachedTacticalState.gunId() != null
                && cachedTacticalState.gunId().equals(trackedGunId)
                && cachedTacticalState.traits().explosive();
        return !currentExplosiveWeapon
                || GuardOrdersAccess.getOrders(guard).permitsExplosiveWeapons();
    }

    /**
     * Reads only TaCZ's already-synchronized action fields for the currently
     * open guard menu. It performs no ammunition scan, gun-data lookup, script
     * evaluation, or state mutation.
     */
    public GuardCombatStatus.WeaponActivity getPlayerFacingActivity() {
        if (weaponState.isSuspended()) {
            return GuardCombatStatus.WeaponActivity.WEAPON_ERROR;
        }
        if (weaponState.remainingDryTicks(guard.level().getGameTime()) > 0L) {
            return GuardCombatStatus.WeaponActivity.OUT_OF_AMMO;
        }
        if (!hasCurrentWeaponData(guard.getMainHandItem())) {
            return GuardCombatStatus.WeaponActivity.WEAPON_DATA_NOT_READY;
        }

        try {
            if (operator.getSynReloadState().getStateType().isReloading()) {
                return GuardCombatStatus.WeaponActivity.RELOADING;
            }
            if (operator.getSynIsBolting()) {
                return GuardCombatStatus.WeaponActivity.CYCLING_ACTION;
            }
            if (operator.getSynDrawCoolDown() > 0L) {
                return GuardCombatStatus.WeaponActivity.DRAWING_WEAPON;
            }
            if (chargeData != null && triggerState.getChargeProgress() > 0.0F) {
                return GuardCombatStatus.WeaponActivity.CHARGING;
            }
            if (operator.getSynShootCoolDown() > 0L) {
                return GuardCombatStatus.WeaponActivity.COOLING_DOWN;
            }
            return GuardCombatStatus.WeaponActivity.READY;
        } catch (RuntimeException ignored) {
            // A broken third-party synchronized accessor should produce a
            // useful warning in the UI without mutating combat state from a
            // menu data read.
            return GuardCombatStatus.WeaponActivity.WEAPON_ERROR;
        }
    }

    /**
     * Captures the controller's current state for an explicit administrator
     * inspection. This method is never called by the combat loop.
     */
    public Diagnostics captureDiagnostics() {
        ItemStack heldStack = guard.getMainHandItem();
        ResourceLocation heldGunId = null;
        FireMode heldFireMode = FireMode.UNKNOWN;
        int loadedAmmo = -1;
        boolean chambered = false;
        boolean inventoryFed = false;
        boolean readableGunState = false;

        if (heldStack.getItem() instanceof IGun heldGun) {
            try {
                heldGunId = heldGun.getGunId(heldStack);
                heldFireMode = heldGun.getFireMode(heldStack);
                loadedAmmo = heldGun.getCurrentAmmoCount(heldStack);
                chambered = heldGun.hasBulletInBarrel(heldStack);
                inventoryFed = heldGun.useInventoryAmmo(heldStack);
                readableGunState = true;
            } catch (RuntimeException ignored) {
                // A broken third-party definition should be reported as
                // unreadable, not make the diagnostic command fail.
            }
        }

        int reserveItems = 0;
        int occupiedReserveSlots = 0;
        var ammoHandler = GuardAmmoAccess.getAmmoHandler(guard);
        for (int slot = 0; slot < ammoHandler.getSlots(); slot++) {
            ItemStack stack = ammoHandler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                reserveItems += stack.getCount();
                occupiedReserveSlots++;
            }
        }

        String reloadState = "unavailable";
        boolean reloading = false;
        long shootCooldown = -1L;
        long drawCooldown = -1L;
        boolean bolting = false;
        boolean aiming = false;
        float chargeProgress = 0.0F;
        try {
            var stateType = operator.getSynReloadState().getStateType();
            reloadState = stateType.toString();
            reloading = stateType.isReloading();
            shootCooldown = operator.getSynShootCoolDown();
            drawCooldown = operator.getSynDrawCoolDown();
            bolting = operator.getSynIsBolting();
            aiming = operator.getSynIsAiming();
            chargeProgress = operator.getDataHolder().chargeProgress;
        } catch (RuntimeException ignored) {
            // Keep the remaining fields useful if a third-party callback has
            // left one native accessor unreadable.
        }

        return new Diagnostics(
                heldGunId,
                heldFireMode,
                readableGunState,
                heldStack == trackedStack && trackedGunId != null,
                hasCurrentWeaponData(heldStack),
                weaponState.isSuspended(),
                weaponState.remainingDryTicks(guard.level().getGameTime()),
                loadedAmmo,
                chambered,
                inventoryFed,
                reserveItems,
                occupiedReserveSlots,
                reloadState,
                reloading,
                shootCooldown,
                drawCooldown,
                bolting,
                aiming,
                chargeProgress,
                lastShootResult,
                lastAmmoSharingStatus,
                lastAmmoSharedItems,
                cachedTacticalState);
    }

    static float normalizeEngagementRange(float range, float maximumRange) {
        float boundedMaximum = Float.isFinite(maximumRange) && maximumRange >= MIN_ENGAGEMENT_RANGE
                ? maximumRange
                : DEFAULT_MAX_ENGAGEMENT_RANGE;
        if (!Float.isFinite(range) || range <= 0.0F) {
            return Mth.clamp(FALLBACK_ENGAGEMENT_RANGE, MIN_ENGAGEMENT_RANGE, boundedMaximum);
        }
        return Mth.clamp(range, MIN_ENGAGEMENT_RANGE, boundedMaximum);
    }

    static double calculateSpreadRadius(double shotDistance, float inaccuracy) {
        // Projectile#shoot perturbs all three normalized direction components
        // by at most 0.0172275 * inaccuracy. sqrt(3) bounds the total error;
        // dividing by the smallest possible forward component gives a
        // conservative cone that includes every value of the triangular RNG.
        double componentError = VANILLA_SPREAD_COMPONENT_SCALE * Math.max(0.0F, inaccuracy);
        double vectorError = Math.sqrt(3.0D) * componentError;
        if (vectorError >= 1.0D) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0D, shotDistance) * vectorError / (1.0D - vectorError);
    }

    static double calculateBurstMovementPadding(long burstDurationMillis) {
        return Math.max(0L, burstDurationMillis) / 1000.0D * PROTECTED_ENTITY_MOVEMENT_PER_SECOND;
    }

    static long calculateShootCooldown(long timestamp, long shootInterval, long lastShootTimestamp) {
        if (lastShootTimestamp < 0L) {
            return 0L;
        }

        long requiredElapsed = Math.max(
                0L,
                shootInterval - NativeFireCadence.TACZ_COOLDOWN_TOLERANCE_MILLIS);
        if (timestamp < lastShootTimestamp) {
            return requiredElapsed;
        }

        long elapsed = timestamp - lastShootTimestamp;
        return elapsed >= requiredElapsed ? 0L : requiredElapsed - elapsed;
    }

    private boolean synchronizeWeapon() {
        int ordersRevision = GuardOrdersAccess.getRevision(guard);
        if (ordersRevision != trackedOrdersRevision) {
            trackedOrdersRevision = ordersRevision;
            nextGunDataRefreshGameTick = Long.MIN_VALUE;
        }

        ItemStack heldStack = guard.getMainHandItem();
        if (heldStack != trackedStack) {
            operator.cancelReload();
            trackedStack = heldStack;
            trackedGun = heldStack.getItem() instanceof IGun gun ? gun : null;
            trackedGunId = null;
            trackedGunType = null;
            gunData = null;
            fireMode = FireMode.UNKNOWN;
            chargeData = null;
            lastShootResult = null;
            lastAmmoSharingStatus = GuardAmmoSharing.SharingStatus.NOT_ATTEMPTED;
            lastAmmoSharedItems = 0;
            engagementRange = configuredFallbackEngagementRange();
            resetCachedTacticalState();
            dynamicExplosionRefreshGameTick = Long.MIN_VALUE;
            nextGunDataRefreshGameTick = Long.MIN_VALUE;
            nativeBurstSafetyUntilTimestamp = -1L;
            GuardBurstSafetyAccess.setSuppressed(guard, false);
            GuardBurstSafetyAccess.clearSafetyCheck(guard);
            triggerState.reset();
            fireCadence.reset();
            weaponState.resetForWeaponChange();

            if (trackedGun == null) {
                operator.aim(false);
                return false;
            }
        } else if (trackedGun == null) {
            return false;
        }

        long gameTime = guard.level().getGameTime();
        if (gameTime >= nextGunDataRefreshGameTick
                && !refreshGunData(heldStack, gameTime)) {
            return false;
        }
        if (gunData == null || weaponState.isSuspended()) {
            return false;
        }
        return synchronizeFireMode(heldStack);
    }

    private boolean refreshGunData(ItemStack gunStack, long gameTime) {
        nextGunDataRefreshGameTick = gameTime
                + config.performance.gunDataRefreshIntervalTicks.get();
        ResourceLocation gunId = trackedGun.getGunId(gunStack);
        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gunId);
        CommonGunIndex refreshedIndex = gunIndex.orElse(null);
        GunData refreshedData = refreshedIndex == null ? null : refreshedIndex.getGunData();
        if (refreshedData == null) {
            trackedGunId = gunId;
            gunData = null;
            engagementRange = configuredFallbackEngagementRange();
            resetCachedTacticalState();
            suspendInvalidWeapon();
            return false;
        }

        boolean definitionChanged = !gunId.equals(trackedGunId) || refreshedData != gunData;
        boolean wasSuspended = weaponState.isSuspended();
        trackedGunId = gunId;
        trackedGunType = refreshedIndex.getType();
        gunData = refreshedData;
        weaponState.resume();

        if (definitionChanged || wasSuspended) {
            fireMode = FireMode.UNKNOWN;
            chargeData = null;
            triggerState.reset();
            fireCadence.reset();
            if (definitionChanged) {
                weaponState.resetForWeaponChange();
            }
            if (!synchronizeFireMode(gunStack)) {
                return false;
            }
            operator.draw(mainHandSupplier);
        } else {
            // Resource reload implementations may update a GunData instance in
            // place, so refresh mode-derived data even when identity is stable.
            chargeData = gunData.getChargeData(fireMode);
            if (!hasValidChargeData(chargeData)) {
                suspendInvalidWeapon();
                return false;
            }
        }
        boolean engagementRangeReadable = refreshEngagementRange();
        refreshCachedTacticalState(refreshedIndex, engagementRangeReadable);
        return true;
    }

    private boolean refreshEngagementRange() {
        AttachmentCacheProperty cache = operator.getCacheProperty();
        if (cache == null || trackedGun == null) {
            engagementRange = configuredFallbackEngagementRange();
            return false;
        }

        try {
            Float cachedRange = cache.getCache(GunProperties.EFFECTIVE_RANGE);
            Float resolvedRange = trackedGun.modifyProperty(
                    operator.getDataHolder(),
                    trackedStack,
                    guard,
                    GunProperties.EFFECTIVE_RANGE,
                    Float.class,
                    cachedRange);
            boolean readable = resolvedRange != null
                    && Float.isFinite(resolvedRange)
                    && resolvedRange > 0.0F;
            engagementRange = normalizeEngagementRange(
                    resolvedRange,
                    (float) effectiveMaximumRange());
            return readable;
        } catch (RuntimeException exception) {
            // A malformed third-party property must not crash guard AI or
            // silently widen an effective-range-limited tactical profile.
            engagementRange = configuredFallbackEngagementRange();
            return false;
        }
    }

    /**
     * Resolves profile metadata beside the existing low-frequency TaCZ data
     * refresh. The combat loop only reads this immutable snapshot; it never
     * invokes gun scripts or scans config maps per tick.
     */
    private void refreshCachedTacticalState(
            CommonGunIndex gunIndex,
            boolean engagementRangeReadable) {
        WeaponTacticalTraits traits = WeaponTacticalTraits.NONE;
        boolean metadataReadable = engagementRangeReadable;
        try {
            traits = resolveTacticalTraits();
        } catch (RuntimeException exception) {
            // Tactical metadata now shapes live firing safety. Preserve the
            // native controller state, but mark the cached window unreadable
            // so trigger gates fail closed until a later refresh recovers.
            metadataReadable = false;
        }

        cacheTacticalState(gunIndex.getType(), metadataReadable, traits);
    }

    private void cacheTacticalState(
            String gunType,
            boolean metadataReadable,
            WeaponTacticalTraits traits) {

        TacticalProfile guardOverride = GuardOrdersAccess.getOrders(guard)
                .tacticalProfileOverride();
        WeaponTacticalResolver.Resolution resolution;
        try {
            TacticalProfile gunOverride = config.tactics.gunOverrides.get().get(trackedGunId);
            resolution = WeaponTacticalResolver.resolve(
                    guardOverride,
                    gunOverride,
                    gunType,
                    traits,
                    config.tactics.typeProfiles.get());
        } catch (RuntimeException exception) {
            metadataReadable = false;
            resolution = WeaponTacticalResolver.resolve(
                    guardOverride,
                    null,
                    gunType,
                    traits,
                    WeaponTacticalResolver.DEFAULT_TYPE_PROFILES);
        }

        TacticalEnvelope envelope;
        try {
            envelope = resolveTacticalEnvelope(resolution.profile(), traits);
        } catch (RuntimeException exception) {
            metadataReadable = false;
            envelope = resolveDefaultTacticalEnvelope(resolution.profile(), traits);
        }
        if (!metadataReadable) {
            envelope = disableFiringWindow(envelope);
        }
        cachedTacticalState = new CachedTacticalState(
                trackedGunId,
                metadataReadable,
                resolution,
                traits,
                envelope);
        dynamicExplosionRefreshGameTick = guard.level().getGameTime();
    }

    /**
     * Runtime scripts can toggle explosive behavior without a datapack reload.
     * Evaluate that safety-critical state at most once per guard per tick and
     * rebuild the immutable tactical snapshot only when it actually changes.
     */
    private boolean refreshDynamicExplosionTacticalState() {
        return refreshDynamicExplosionTacticalState(false);
    }

    private boolean refreshDynamicExplosionTacticalState(boolean force) {
        if (guard.getMainHandItem() != trackedStack
                || trackedGun == null
                || trackedGunId == null
                || trackedGunType == null
                || gunData == null
                || weaponState.isSuspended()) {
            // Preserve first-use synchronization: canUseGunGoal will populate
            // the native cache later in the same goal-selection pass.
            return true;
        }

        long gameTime = guard.level().getGameTime();
        if (!force && dynamicExplosionRefreshGameTick == gameTime) {
            return hasUsableCachedTacticalState();
        }
        dynamicExplosionRefreshGameTick = gameTime;

        try {
            TaCZExplosionResolver.Traits explosion = resolveCurrentExplosionTraits();
            WeaponTacticalTraits previous = cachedTacticalState.traits();
            if (previous.explosive() != explosion.enabled()
                    || Double.compare(previous.explosionRadius(), explosion.radius()) != 0) {
                WeaponTacticalTraits updated = new WeaponTacticalTraits(
                        explosion.enabled(),
                        previous.projectileCount(),
                        explosion.radius());
                cacheTacticalState(
                        trackedGunType,
                        cachedTacticalState.metadataReadable(),
                        updated);
            }
            return hasUsableCachedTacticalState();
        } catch (RuntimeException exception) {
            suspendInvalidWeapon();
            return false;
        }
    }

    private void resetCachedTacticalState() {
        WeaponTacticalResolver.Resolution resolution = WeaponTacticalResolver.resolve(
                null,
                null,
                WeaponTacticalTraits.NONE);
        cachedTacticalState = new CachedTacticalState(
                null,
                false,
                resolution,
                WeaponTacticalTraits.NONE,
                disableFiringWindow(resolveDefaultTacticalEnvelope(
                        TacticalProfile.GENERALIST,
                        WeaponTacticalTraits.NONE)));
        dynamicExplosionRefreshGameTick = Long.MIN_VALUE;
    }

    private TacticalEnvelope resolveTacticalEnvelope(
            TacticalProfile profile,
            WeaponTacticalTraits traits) {
        return TacticalEnvelope.resolve(
                config.tactics.definition(profile),
                traits,
                config.safety.explosiveStandoffPolicy(),
                engagementRange,
                effectiveMaximumRange(),
                config.combat.closeRangeDistance.get(),
                config.combat.requiredVisibleTicks.get(),
                config.combat.aimToleranceDegrees.get());
    }

    private TacticalEnvelope resolveDefaultTacticalEnvelope(
            TacticalProfile profile,
            WeaponTacticalTraits traits) {
        return TacticalEnvelope.resolve(
                TacticalProfileDefinition.defaultsFor(profile),
                traits,
                config.safety.explosiveStandoffPolicy(),
                engagementRange,
                effectiveMaximumRange(),
                config.combat.closeRangeDistance.get(),
                config.combat.requiredVisibleTicks.get(),
                config.combat.aimToleranceDegrees.get());
    }

    private WeaponTacticalTraits resolveTacticalTraits() {
        TaCZExplosionResolver.Traits explosion = resolveCurrentExplosionTraits();
        return new WeaponTacticalTraits(
                explosion.enabled(),
                resolveProjectileCount(),
                explosion.radius());
    }

    private int resolveProjectileCount() {
        if (gunData.getBulletData() == null) {
            throw new IllegalStateException("TaCZ gun definition has no bullet data");
        }
        int baseAmount = Math.max(gunData.getBulletData().getBulletAmount(), 1);
        Integer resolvedAmount = trackedGun.modifyProperty(
                operator.getDataHolder(),
                trackedStack,
                guard,
                GunProperties.RuntimeOnly.BULLET_AMOUNT,
                Integer.class,
                baseAmount);
        if (resolvedAmount == null
                || resolvedAmount < 1
                || resolvedAmount > WeaponTacticalTraits.MAX_PROJECTILE_COUNT) {
            throw new IllegalStateException("TaCZ gun resolved an invalid projectile count");
        }
        return resolvedAmount;
    }

    private static TacticalEnvelope disableFiringWindow(TacticalEnvelope envelope) {
        return new TacticalEnvelope(
                envelope.profile(),
                envelope.traits(),
                envelope.minimumFiringDistance(),
                envelope.retreatDistance(),
                envelope.preferredApproachDistance(),
                envelope.maximumFiringDistance(),
                envelope.requiredVisibleTicks(),
                envelope.aimToleranceDegrees(),
                false);
    }

    private boolean synchronizeFireMode(ItemStack gunStack) {
        FireMode currentFireMode = trackedGun.getFireMode(gunStack);
        if (currentFireMode == FireMode.UNKNOWN) {
            suspendInvalidWeapon();
            return false;
        }
        if (currentFireMode != fireMode) {
            fireMode = currentFireMode;
            chargeData = gunData.getChargeData(currentFireMode);
            triggerState.reset();
            fireCadence.reset();
        }
        if (!hasValidChargeData(chargeData)) {
            suspendInvalidWeapon();
            return false;
        }
        return true;
    }

    private static boolean hasValidChargeData(ChargeData data) {
        return data == null
                || (data.getChargeType() != null
                        && NativeTriggerState.hasValidChargeDefinition(
                                data.getIncreasePerTick(),
                                data.getDecreasePerTick(),
                                data.getDecreaseOnFire(),
                                data.getMaxCharge(),
                                data.getFireThreshold()));
    }

    private void updateAim(LivingEntity target) {
        double deltaX = target.getX() - guard.getX();
        double deltaY = target.getEyeY() - guard.getEyeY();
        double deltaZ = target.getZ() - guard.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        aimYaw = (float) (Mth.atan2(deltaZ, deltaX) * Mth.RAD_TO_DEG) - 90.0F;
        aimPitch = (float) -(Mth.atan2(deltaY, horizontalDistance) * Mth.RAD_TO_DEG);
    }

    private boolean needsManualBolt(ItemStack gunStack) {
        return gunData.getBolt() == Bolt.MANUAL_ACTION
                && !trackedGun.hasBulletInBarrel(gunStack)
                && trackedGun.getCurrentAmmoCount(gunStack) > 0;
    }

    private boolean needsReload(ItemStack gunStack) {
        if (trackedGun.useInventoryAmmo(gunStack)) {
            return false;
        }
        boolean usableBarrelRound = gunData.getBolt() != Bolt.OPEN_BOLT
                && trackedGun.hasBulletInBarrel(gunStack);
        return trackedGun.getCurrentAmmoCount(gunStack) <= 0 && !usableBarrelRound;
    }

    private void requestReload(ItemStack gunStack) {
        if (!triggerState.canAttemptAction()) {
            return;
        }

        long shootInterval = resolveShootInterval(gunStack);
        long shootCooldown = shootInterval < 1L ? 0L : calculateShootCooldown(
                currentRelativeTimestamp(),
                shootInterval,
                operator.getDataHolder().shootTimestamp);
        if (shootCooldown > 0L || operator.getSynShootCoolDown() > 0L) {
            // TaCZ rejects reload() while its native shoot cooldown is active.
            // This is a temporary state, not evidence that the guard is dry.
            triggerState.defer(1);
            return;
        }

        // TaCZ deliberately leaves depleted finite ammo boxes in place. Make
        // room only when a reload is actually requested, preserving each box
        // as a nearby drop instead of scanning every guard on every tick.
        GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard);

        boolean nativeCanReload = trackedGun instanceof AbstractGunItem gunItem
                && (!operator.needCheckAmmo() || gunItem.canReload(guard, gunStack));

        operator.reload();
        boolean reloadStarted = operator.getDataHolder().reloadStateType.isReloading();
        if (reloadStarted) {
            weaponState.clearDry();
            triggerState.defer(1);
            return;
        }

        GuardAmmoSummary.Snapshot ammunition = GuardAmmoSummary.capture(
                gunStack.copy(),
                gunData,
                GuardAmmoAccess.getTaCZInventoryHandler(guard));
        if (failedReloadHasUsableReserve(nativeCanReload, ammunition)) {
            // An event or gun script may temporarily decline the request.
            triggerState.defer(SHORT_RETRY_TICKS);
            return;
        }

        GuardAmmoSharing.SharingResult sharing = GuardAmmoSharing.tryShare(guard);
        lastAmmoSharingStatus = sharing.status();
        lastAmmoSharedItems = sharing.transferred();
        if (sharing.shared()) {
            weaponState.clearDry();
            triggerState.defer(1);
            return;
        }

        // Usually no compatible ammunition. Yield this goal for about a second
        // so Guard Villagers' normal lower-priority melee goal can take over.
        weaponState.markDry(guard.level().getGameTime(), DRY_RETRY_TICKS);
        operator.aim(false);
        triggerState.reset();
    }

    static boolean failedReloadHasUsableReserve(
            boolean nativeCanReload,
            GuardAmmoSummary.Snapshot ammunition) {
        if (!nativeCanReload
                || ammunition == null
                || !ammunition.isReady()
                || !ammunition.hasCompatibleReserve()) {
            return false;
        }
        return ammunition.reserveSource() != GuardAmmoSummary.ReserveSource.PHYSICAL
                || ammunition.reserveScanComplete();
    }

    private long resolveShootInterval(ItemStack gunStack) {
        if (fireMode == FireMode.BURST) {
            BurstData burstData = gunData.getBurstData();
            if (burstData == null) {
                return -1L;
            }
            double intervalMillis = burstData.getMinInterval() * 1000.0D;
            if (!Double.isFinite(intervalMillis) || intervalMillis < 0.0D) {
                return -1L;
            }
            if (intervalMillis >= Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            return Math.max(1L, (long) intervalMillis);
        }

        if (gunData.getRoundsPerMinute(fireMode) <= 0) {
            return -1L;
        }
        try {
            long intervalMillis = gunData.getShootInterval(guard, fireMode, gunStack);
            return intervalMillis < 0L ? -1L : Math.max(1L, intervalMillis);
        } catch (ArithmeticException exception) {
            return -1L;
        }
    }

    private boolean prepareCharge(long shootCooldown) {
        if (chargeData == null) {
            return true;
        }
        if (!chargeData.isChargeDuringCooldown()
                && shootCooldown >= NativeFireCadence.SERVER_TICK_MILLIS) {
            decayCharge();
            return false;
        }

        // AUTO and DELAY fire when fully charged. A player fires HOLD on trigger
        // release; the guard emulates that release after taking a full charge.
        return switch (chargeData.getChargeType()) {
            case AUTO, DELAY, HOLD -> triggerState.advanceCharge(
                    chargeData.getIncreasePerTick(),
                    chargeData.getMaxCharge());
        };
    }

    private void decayCharge() {
        if (chargeData != null) {
            triggerState.decayCharge(chargeData.getDecreasePerTick());
        }
    }

    private boolean supportsSubTickAutomaticFire(long shootInterval) {
        return fireMode == FireMode.AUTO
                && chargeData == null
                && shootInterval < NativeFireCadence.SERVER_TICK_MILLIS;
    }

    private long currentRelativeTimestamp() {
        return System.currentTimeMillis() - operator.getDataHolder().baseTimestamp;
    }

    private ShootResult shoot(long timestamp, BooleanSupplier burstSafetyCheck) {
        boolean previousBurstSafetyArmed = hasNativeBurstSafetyState();
        boolean acceptedBurst = false;

        // Server execution is single-threaded here. Temporarily disarming the
        // old latch allows only this initial shot, whose corridor was checked
        // immediately before this method. Any later CycleTask round observes
        // the newly armed validator or the fail-closed exception path below.
        GuardBurstSafetyAccess.setSuppressed(guard, false);
        GuardBurstSafetyAccess.clearSafetyCheck(guard);
        try {
            ShootResult result = chargeData == null
                    ? operator.shoot(pitchSupplier, yawSupplier, timestamp)
                    : operator.shoot(
                    pitchSupplier,
                    yawSupplier,
                    timestamp,
                    triggerState.getChargeProgress());
            acceptedBurst = result == ShootResult.SUCCESS && fireMode == FireMode.BURST;
            if (!acceptedBurst) {
                if (result == ShootResult.SUCCESS) {
                    nativeBurstSafetyUntilTimestamp = -1L;
                } else if (previousBurstSafetyArmed) {
                    GuardBurstSafetyAccess.setSuppressed(guard, true);
                }
                return result;
            }

            long duration = resolveBurstDurationMillis();
            if (duration > MAX_BURST_DURATION_MILLIS) {
                throw new IllegalStateException("TaCZ gun resolved an invalid burst duration");
            }
            nativeBurstSafetyUntilTimestamp = duration > 0L
                    ? saturatingAdd(timestamp, duration)
                    : -1L;
            if (duration > 0L) {
                GuardBurstSafetyAccess.setSafetyCheck(guard, burstSafetyCheck);
            } else {
                GuardBurstSafetyAccess.clearSafetyCheck(guard);
            }
            return result;
        } catch (RuntimeException exception) {
            if (fireMode == FireMode.BURST || previousBurstSafetyArmed) {
                // A native burst may already have scheduled later rounds. Keep
                // a marker and latch them closed if metadata resolution fails.
                nativeBurstSafetyUntilTimestamp = Math.max(0L, timestamp);
                GuardBurstSafetyAccess.setSuppressed(guard, true);
            }
            throw exception;
        }
    }

    private void handleShootResult(ShootResult result) {
        lastShootResult = result;
        switch (result) {
            case SUCCESS -> {
                weaponState.clearDry();
                if (chargeData != null) {
                    triggerState.finishChargedShot(
                            chargeData.getDecreaseOnFire(),
                            chargeData.getChargeType() == ChargeType.DELAY);
                }
            }
            case NO_AMMO -> requestReload(trackedStack);
            case NEED_BOLT -> {
                if (triggerState.canAttemptAction()) {
                    operator.bolt();
                }
                triggerState.defer(SHORT_RETRY_TICKS);
            }
            case OVERHEATED -> triggerState.defer(OVERHEAT_RETRY_TICKS);
            case IS_SPRINTING -> {
                guard.setSprinting(false);
                triggerState.defer(1);
            }
            case NOT_DRAW -> {
                operator.draw(mainHandSupplier);
                triggerState.defer(SHORT_RETRY_TICKS);
            }
            case COOL_DOWN, IS_RELOADING, IS_DRAWING, IS_BOLTING -> triggerState.defer(1);
            case IS_MELEE -> triggerState.defer(SHORT_RETRY_TICKS);
            case NOT_GUN, ID_NOT_EXIST -> suspendInvalidWeapon();
            case UNKNOWN_FAIL, NETWORK_FAIL, FORGE_EVENT_CANCEL -> triggerState.defer(OVERHEAT_RETRY_TICKS);
        }
    }

    private void suspendInvalidWeapon() {
        weaponState.suspend();
        nextGunDataRefreshGameTick = guard.level().getGameTime()
                + config.performance.gunDataRefreshIntervalTicks.get();
        boolean burstSafetyArmed = hasNativeBurstSafetyState();
        GuardBurstSafetyAccess.setSuppressed(guard, burstSafetyArmed);
        if (!burstSafetyArmed) {
            GuardBurstSafetyAccess.clearSafetyCheck(guard);
        }
        try {
            operator.aim(false);
        } catch (RuntimeException ignored) {
            // The controller is already suspended; do not let a second broken
            // third-party callback escape while attempting cleanup.
        }
        triggerState.reset();
        fireCadence.reset();
    }

    private boolean isNativeBurstActive(long timestamp) {
        return nativeBurstSafetyUntilTimestamp >= 0L && timestamp <= nativeBurstSafetyUntilTimestamp;
    }

    private boolean hasNativeBurstSafetyState() {
        return nativeBurstSafetyUntilTimestamp >= 0L;
    }

    static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    private float configuredFallbackEngagementRange() {
        return normalizeEngagementRange(
                FALLBACK_ENGAGEMENT_RANGE,
                (float) effectiveMaximumRange());
    }

    private double effectiveMaximumRange() {
        GuardOrders orders = GuardOrdersAccess.getOrders(guard);
        return orders.resolveMaximumRange(config.combat.maximumEngagementRange.get());
    }

    private double configuredFallbackRetreatDistance() {
        double retreatDistance = config.combat.closeRangeDistance.get();
        return Double.isFinite(retreatDistance)
                        && retreatDistance >= 1.0D
                        && retreatDistance <= 8.0D
                ? retreatDistance
                : DEFAULT_RETREAT_DISTANCE;
    }

    private int configuredFallbackRequiredVisibleTicks() {
        int requiredVisibleTicks = config.combat.requiredVisibleTicks.get();
        return requiredVisibleTicks >= 0 && requiredVisibleTicks <= 40
                ? requiredVisibleTicks
                : DEFAULT_REQUIRED_VISIBLE_TICKS;
    }

    private double configuredFallbackAimToleranceDegrees() {
        double aimToleranceDegrees = config.combat.aimToleranceDegrees.get();
        return Double.isFinite(aimToleranceDegrees)
                        && aimToleranceDegrees >= 1.0D
                        && aimToleranceDegrees <= 45.0D
                ? aimToleranceDegrees
                : DEFAULT_AIM_TOLERANCE_DEGREES;
    }

    public record Diagnostics(
            ResourceLocation gunId,
            FireMode fireMode,
            boolean readableGunState,
            boolean controllerBound,
            boolean weaponDataReady,
            boolean suspended,
            long dryRetryTicks,
            int loadedAmmo,
            boolean chambered,
            boolean inventoryFed,
            int reserveItems,
            int occupiedReserveSlots,
            String reloadState,
            boolean reloading,
            long shootCooldownMillis,
            long drawCooldownMillis,
            boolean bolting,
            boolean aiming,
            float chargeProgress,
            ShootResult lastShootResult,
            GuardAmmoSharing.SharingStatus lastAmmoSharingStatus,
            int lastAmmoSharedItems,
            CachedTacticalState tacticalState) {
    }

    public record CachedTacticalState(
            ResourceLocation gunId,
            boolean metadataReadable,
            WeaponTacticalResolver.Resolution resolution,
            WeaponTacticalTraits traits,
            TacticalEnvelope envelope) {
    }

    private float resolveMaximumInaccuracy() {
        float maximum = 0.0F;
        AttachmentCacheProperty cache = operator.getCacheProperty();
        if (cache != null) {
            Map<?, ?> values = cache.getCache(GunProperties.INACCURACY);
            if (values != null) {
                for (Object value : values.values()) {
                    if (value instanceof Number number) {
                        maximum = Math.max(maximum, number.floatValue());
                    }
                }
            }
        }
        if (maximum <= 0.0F) {
            for (Object value : gunData.getInaccuracy().values()) {
                if (value instanceof Number number) {
                    maximum = Math.max(maximum, number.floatValue());
                }
            }
        }

        maximum *= Math.max(0.0F, trackedGun.lerpInaccuracy(trackedStack));
        return Math.max(0.0F, trackedGun.modifyProperty(
                operator.getDataHolder(),
                trackedStack,
                guard,
                GunProperties.INACCURACY,
                Float.class,
                maximum));
    }

    private TaCZExplosionResolver.Traits resolveCurrentExplosionTraits() {
        return TaCZExplosionResolver.resolve(
                guard,
                trackedStack,
                trackedGun,
                gunData,
                operator.getCacheProperty());
    }

    private long resolveBurstDurationMillis() {
        if (fireMode != FireMode.BURST || gunData == null) {
            return 0L;
        }
        BurstData burstData = gunData.getBurstData();
        if (burstData == null) {
            return Long.MAX_VALUE;
        }

        int count = trackedGun.modifyProperty(
                operator.getDataHolder(),
                trackedStack,
                guard,
                "burst_count",
                Integer.class,
                burstData.getCount());
        long period = trackedGun.modifyProperty(
                operator.getDataHolder(),
                trackedStack,
                guard,
                "burst_shoot_interval",
                Long.class,
                gunData.getBurstShootInterval());
        if (count <= 1) {
            return 0L;
        }
        if (period <= 0L || count > 1_024 || period > MAX_BURST_DURATION_MILLIS) {
            return Long.MAX_VALUE;
        }
        try {
            return Math.multiplyExact((long) count - 1L, period);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
