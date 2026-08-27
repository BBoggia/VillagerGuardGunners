package com.gamergaming.villagerguardgunners.combat;

import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardBurstSafetyAccess;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
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
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
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
    private GunData gunData;
    private FireMode fireMode = FireMode.UNKNOWN;
    private ChargeData chargeData;
    private float engagementRange;
    private long nextGunDataRefreshGameTick = Long.MIN_VALUE;
    private long nativeBurstSafetyUntilTimestamp = -1L;
    private float aimPitch;
    private float aimYaw;
    private ShootResult lastShootResult;

    public NativeGunController(Guard guard) {
        this.guard = guard;
        this.operator = IGunOperator.fromLivingEntity(guard);
        this.config = ModConfigs.GUARD_GUNNERS;
        this.mainHandSupplier = guard::getMainHandItem;
        this.pitchSupplier = () -> aimPitch;
        this.yawSupplier = () -> aimYaw;
        this.engagementRange = configuredFallbackEngagementRange();
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
        return weaponState.isSuspended()
                ? gameTime >= nextGunDataRefreshGameTick
                : weaponState.mayEngage(gameTime);
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
        boolean burstStillActive = isNativeBurstActive(currentRelativeTimestamp());
        GuardBurstSafetyAccess.setSuppressed(guard, burstStillActive);
        if (!burstStillActive) {
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

        triggerState.tick();
        updateAim(target);
        operator.aim(true);

        long nowTimestamp = currentRelativeTimestamp();
        if (isNativeBurstActive(nowTimestamp)) {
            if (!mayFire || !safeToFire.getAsBoolean()) {
                // TaCZ posts GunFireEvent before every scheduled burst round.
                // The event backstop cancels the remainder before ammo, heat,
                // projectiles, sound, and recoil are produced.
                GuardBurstSafetyAccess.setSuppressed(guard, true);
            }
        } else {
            nativeBurstSafetyUntilTimestamp = -1L;
            GuardBurstSafetyAccess.setSuppressed(guard, false);
            GuardBurstSafetyAccess.clearSafetyCheck(guard);
        }

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
        while (fireCadence.hasShotDue()) {
            long shotTimestamp = fireCadence.getDueTimestamp();
            if (calculateShootCooldown(
                    shotTimestamp,
                    shootInterval,
                    operator.getDataHolder().shootTimestamp) > 0L) {
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
        }
    }

    public float getSafetyPadding(double shotDistance) {
        if (gunData == null || trackedGun == null || !Double.isFinite(shotDistance)) {
            return Float.NaN;
        }

        try {
            float inaccuracy = resolveMaximumInaccuracy();
            float explosionRadius = resolveExplosionRadius();
            long burstDuration = resolveBurstDurationMillis();
            if (!Float.isFinite(inaccuracy)
                    || !Float.isFinite(explosionRadius)
                    || burstDuration > MAX_BURST_DURATION_MILLIS) {
                return Float.NaN;
            }

            double spreadRadius = calculateSpreadRadius(
                    Math.max(0.0D, shotDistance),
                    inaccuracy + SCRIPT_SPREAD_MARGIN_DEGREES);
            double burstMovement = calculateBurstMovementPadding(burstDuration);
            double padding = config.safety.baseClearance.get()
                    + explosionRadius
                    + spreadRadius
                    + burstMovement;
            return Double.isFinite(padding) && padding <= MAX_SAFETY_PADDING
                    ? (float) padding
                    : Float.NaN;
        } catch (RuntimeException exception) {
            suspendInvalidWeapon();
            return Float.NaN;
        }
    }

    /**
     * Returns the latest bounded TaCZ effective range resolved during the
     * controller's low-frequency gun-data refresh. This is a damage-falloff
     * breakpoint, so movement uses it as a preferred distance rather than a
     * hard firing limit.
     */
    public float getPreferredEngagementRange() {
        float maximumRange = config.combat.maximumEngagementRange.get().floatValue();
        return Float.isFinite(maximumRange)
                ? Math.min(engagementRange, maximumRange)
                : engagementRange;
    }

    /**
     * Returns the configured hard firing cap. TaCZ remains responsible for
     * projectile lifetime and damage falloff beyond the weapon's preferred
     * effective range.
     */
    public float getMaximumFiringRange() {
        return normalizeMaximumFiringRange(
                config.combat.maximumEngagementRange.get().floatValue());
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
                lastShootResult);
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
        ItemStack heldStack = guard.getMainHandItem();
        if (heldStack != trackedStack) {
            operator.cancelReload();
            trackedStack = heldStack;
            trackedGun = heldStack.getItem() instanceof IGun gun ? gun : null;
            trackedGunId = null;
            gunData = null;
            fireMode = FireMode.UNKNOWN;
            chargeData = null;
            lastShootResult = null;
            engagementRange = configuredFallbackEngagementRange();
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
        GunData refreshedData = gunIndex.map(CommonGunIndex::getGunData).orElse(null);
        if (refreshedData == null) {
            trackedGunId = gunId;
            gunData = null;
            suspendInvalidWeapon();
            return false;
        }

        boolean definitionChanged = !gunId.equals(trackedGunId) || refreshedData != gunData;
        boolean wasSuspended = weaponState.isSuspended();
        trackedGunId = gunId;
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
            refreshEngagementRange();
        } else {
            // Resource reload implementations may update a GunData instance in
            // place, so refresh mode-derived data even when identity is stable.
            chargeData = gunData.getChargeData(fireMode);
            if (!hasValidChargeData(chargeData)) {
                suspendInvalidWeapon();
                return false;
            }
            refreshEngagementRange();
        }
        return true;
    }

    private void refreshEngagementRange() {
        AttachmentCacheProperty cache = operator.getCacheProperty();
        if (cache == null || trackedGun == null) {
            engagementRange = configuredFallbackEngagementRange();
            return;
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
            engagementRange = normalizeEngagementRange(
                    resolvedRange,
                    config.combat.maximumEngagementRange.get().floatValue());
        } catch (RuntimeException exception) {
            // A malformed third-party property must not crash guard AI. TaCZ
            // remains authoritative for whether an eventual shot is valid.
            engagementRange = configuredFallbackEngagementRange();
        }
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

        boolean hasCompatibleAmmo = trackedGun instanceof AbstractGunItem gunItem
                && (!operator.needCheckAmmo() || gunItem.canReload(guard, gunStack));

        operator.reload();
        boolean reloadStarted = operator.getDataHolder().reloadStateType.isReloading();
        if (reloadStarted) {
            weaponState.clearDry();
            triggerState.defer(1);
            return;
        }

        if (hasCompatibleAmmo) {
            // An event or gun script may temporarily decline the request.
            triggerState.defer(SHORT_RETRY_TICKS);
            return;
        }

        // Usually no compatible ammunition. Yield this goal for about a second
        // so Guard Villagers' normal lower-priority melee goal can take over.
        weaponState.markDry(guard.level().getGameTime(), DRY_RETRY_TICKS);
        operator.aim(false);
        triggerState.reset();
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
        ShootResult result = chargeData == null
                ? operator.shoot(pitchSupplier, yawSupplier, timestamp)
                : operator.shoot(
                pitchSupplier,
                yawSupplier,
                timestamp,
                triggerState.getChargeProgress());
        if (result == ShootResult.SUCCESS && fireMode == FireMode.BURST) {
            long duration = resolveBurstDurationMillis();
            nativeBurstSafetyUntilTimestamp = duration > 0L
                    ? saturatingAdd(timestamp, duration)
                    : -1L;
            GuardBurstSafetyAccess.setSuppressed(guard, false);
            if (duration > 0L) {
                GuardBurstSafetyAccess.setSafetyCheck(guard, burstSafetyCheck);
            } else {
                GuardBurstSafetyAccess.clearSafetyCheck(guard);
            }
        }
        return result;
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
        boolean burstStillActive;
        try {
            burstStillActive = isNativeBurstActive(currentRelativeTimestamp());
        } catch (RuntimeException exception) {
            burstStillActive = true;
        }
        GuardBurstSafetyAccess.setSuppressed(guard, burstStillActive);
        if (!burstStillActive) {
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
                config.combat.maximumEngagementRange.get().floatValue());
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
            ShootResult lastShootResult) {
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

    private float resolveExplosionRadius() {
        ExplosionData explosion = null;
        AttachmentCacheProperty cache = operator.getCacheProperty();
        if (cache != null) {
            explosion = cache.getCache(GunProperties.EXPLOSION);
        }
        if (explosion == null && gunData.getBulletData() != null) {
            explosion = gunData.getBulletData().getExplosionData();
        }
        if (explosion == null) {
            return 0.0F;
        }

        boolean enabled = trackedGun.modifyProperty(
                operator.getDataHolder(),
                trackedStack,
                guard,
                "explode_enabled",
                Boolean.class,
                explosion.isExplode());
        if (!enabled) {
            return 0.0F;
        }
        return Math.max(0.0F, trackedGun.modifyProperty(
                operator.getDataHolder(),
                trackedStack,
                guard,
                "explosion_radius",
                Float.class,
                explosion.getRadius()));
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
