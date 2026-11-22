package com.gamergaming.villagerguardgunners.guns.managed;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSharing;
import com.gamergaming.villagerguardgunners.ammunition.GuardInfiniteAmmo;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.combat.GuardCombatStatus;
import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.gamergaming.villagerguardgunners.combat.GuardShotEnvelope;
import com.gamergaming.villagerguardgunners.combat.GuardShotSafety;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimPoint;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimSettings;
import com.gamergaming.villagerguardgunners.combat.aim.GuardAimState;
import com.gamergaming.villagerguardgunners.combat.aim.GuardShotAim;
import com.gamergaming.villagerguardgunners.combat.engagement.GuardRulesOfEngagement;
import com.gamergaming.villagerguardgunners.combat.tactics.CachedTacticalState;
import com.gamergaming.villagerguardgunners.combat.tactics.WeaponTacticalTraits;
import com.gamergaming.villagerguardgunners.combat.tactics.WeaponTacticsState;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.guns.GunDescriptor;
import com.gamergaming.villagerguardgunners.guns.GunProvider;
import com.gamergaming.villagerguardgunners.guns.WeaponDriver;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Operates guns from mods that only let players fire.
 *
 * <p>This driver pulls the trigger itself. It keeps the rate of fire, runs
 * reloads from the guard's ammo slots, shares ammo when dry, aims, and checks
 * every round for allies before it leaves the gun. The gun mod is reached only
 * through a {@link ManagedGunBackend}, which reads the gun's numbers and
 * discharges one approved round.</p>
 *
 * <p>Loose ammo counts as one round per item. Ammo containers are not loaded.</p>
 */
public final class ManagedWeaponDriver implements WeaponDriver {
    private static final GuardShotSafety.Result UNCHECKED_SHOT =
            new GuardShotSafety.Result(GuardShotSafety.Status.NOT_EVALUATED, -1, null, null);
    private static final GuardShotSafety.Result UNAVAILABLE_SHOT =
            new GuardShotSafety.Result(GuardShotSafety.Status.INVALID_WORLD, -1, null, null);
    private static final int SHOT_SAFETY_DISPLAY_TICKS = 20;

    private final Guard guard;
    private final GunProvider provider;
    private final ManagedGunBackend backend;
    private final VillagerGuardGunnersConfig config;
    private final WeaponTacticsState tactics;
    private final GuardAimState aimState;
    private final ManagedWeaponCycle cycle = new ManagedWeaponCycle();
    private final LiveWeapon liveWeapon = new LiveWeapon();

    private ItemStack trackedStack = ItemStack.EMPTY;
    private boolean trackedStackIsGun;
    private ManagedGunStats stats;
    private ResourceLocation gunId;
    private String gunType;
    private long nextStatsRefreshGameTick = Long.MIN_VALUE;
    private int trackedOrdersRevision = Integer.MIN_VALUE;
    private long aimWeaponRevision;
    private boolean aiming;

    private LivingEntity aimingTarget;
    private GuardShotSafety.Result lastShotSafety;
    private LivingEntity lastShotTarget;
    private long lastShotGameTick = Long.MIN_VALUE;
    private long lastShotWeaponRevision;
    private int lastShotOrdersRevision;
    private GuardAimSettings lastShotSettings;
    private long successfulAimRounds;
    private GuardAmmoSharing.SharingStatus lastAmmoSharingStatus =
            GuardAmmoSharing.SharingStatus.NOT_ATTEMPTED;

    public ManagedWeaponDriver(Guard guard, GunProvider provider, ManagedGunBackend backend) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.config = ModConfigs.GUARD_GUNNERS;
        this.tactics = WeaponTacticsState.forGuard(guard, config);
        this.aimState = new GuardAimState(guard.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(guard.getUUID().getLeastSignificantBits(), 17));
    }

    private int lastAmmoSharedItems;

    // Trigger

    @Override
    public void startAiming() {
        try {
            aiming = synchronizeWeapon();
        } catch (RuntimeException exception) {
            suspendInvalidWeapon();
        }
    }

    @Override
    public void stopAiming() {
        invalidateShot(true);
        aiming = false;
        cycle.releaseTrigger();
    }

    /** Maintenance remains available without a target or an aimed firing pose. */
    @Override
    public void tick(LivingEntity target, boolean mayFire, BooleanSupplier safeToFire,
            BooleanSupplier burstSafetyCheck, boolean aimAtTarget) {
        try {
            tickSafely(target, mayFire && target != null && aimAtTarget, safeToFire, burstSafetyCheck, aimAtTarget);
        } catch (RuntimeException exception) {
            // A gun mod's own code runs under most backend calls. A broken
            // definition suspends this weapon instead of escaping into the
            // goal selector and the server tick.
            suspendInvalidWeapon();
        }
    }

    private void tickSafely(LivingEntity target, boolean mayFire, BooleanSupplier safeToFire,
            BooleanSupplier burstSafetyCheck, boolean aimAtTarget) {
        if (!mayFire) {
            invalidateShot(target == null || !config.enabled.get());
        }
        aimingTarget = target;
        if (guard.level().isClientSide || !synchronizeWeapon()) {
            aiming = false;
            return;
        }

        if (GuardInfiniteAmmo.withoutReloading(guard)
                && backend.loaded(trackedStack) < stats.magazineCapacity()) {
            backend.setLoaded(trackedStack, stats.magazineCapacity());
        }
        if (GuardInfiniteAmmo.virtualReserve(guard)) {
            cycle.clearDry();
        }

        liveWeapon.safeToFire = safeToFire;
        liveWeapon.burstSafetyCheck = burstSafetyCheck;
        try {
            cycle.tick(guard.level().getGameTime(), mayFire, liveWeapon);
        } finally {
            liveWeapon.safeToFire = null;
            liveWeapon.burstSafetyCheck = null;
        }
        aiming = aimAtTarget && target != null && !cycle.isDryConfirmed();
    }

    /** Whether this driver currently has the guard's gun raised. */
    public boolean isAiming() {
        return aiming;
    }

    /** Cancels an in-progress reload and lowers the gun. No ammo has moved for a cancelled reload. */
    public void interrupt() {
        invalidateShot(true);
        aiming = false;
        cycle.cancelReload();
        cycle.releaseTrigger();
    }

    @Override
    public void deactivate() {
        interrupt();
    }

    /** True while reloading, waiting between rounds, or backing off after a declined round. */
    public boolean isBusy() {
        return cycle.isBusy(guard.level().getGameTime());
    }

    public long successfulAimRounds() {
        return successfulAimRounds;
    }

    public GuardAmmoSharing.SharingStatus lastAmmoSharingStatus() {
        return lastAmmoSharingStatus;
    }

    /** State for an explicit administrator inspection. Never called by the combat loop. */
    @Override
    public Optional<com.gamergaming.villagerguardgunners.combat.WeaponControllerDiagnostics> captureControllerDiagnostics() {
        Diagnostics state = captureDiagnostics();
        int reserveItems = 0;
        int occupiedSlots = 0;
        var handler = GuardAmmoAccess.getAmmoHandler(guard);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                reserveItems += stack.getCount();
                occupiedSlots++;
            }
        }
        return Optional.of(new com.gamergaming.villagerguardgunners.combat.WeaponControllerDiagnostics(
                state.bound() ? state.gunId() : null, "MANAGED", state.bound() && state.loadedAmmo() >= 0,
                state.bound(), state.weaponDataReady(), state.suspended(), state.readiness(),
                state.dryConfirmed(), state.dryRetryTicks(), state.loadedAmmo(), false, false,
                reserveItems, occupiedSlots, state.reloading() ? "RELOADING" : "IDLE", state.reloading(),
                cycle.remainingCooldownMillis(guard.level().getGameTime()), 0L, false, state.aiming(), 0.0F,
                null, state.lastAmmoSharingStatus(), lastAmmoSharedItems, state.tacticalState()));
    }

    public Diagnostics captureDiagnostics() {
        boolean bound = guard.getMainHandItem() == trackedStack && trackedStackIsGun;
        int loaded = -1;
        if (bound) {
            try {
                loaded = backend.loaded(trackedStack);
            } catch (RuntimeException ignored) {
                // An unreadable magazine is reported as unknown, not as a failed command.
            }
        }
        long gameTime = guard.level().getGameTime();
        return new Diagnostics(
                provider.id(),
                gunId,
                gunType,
                bound,
                bound && stats != null && !cycle.isSuspended(),
                cycle.isSuspended(),
                captureReadiness(),
                loaded,
                stats == null ? -1 : stats.magazineCapacity(),
                cycle.isReloading(),
                cycle.remainingCooldownMillis(gameTime) > 0L,
                cycle.isDryConfirmed(),
                cycle.remainingDryTicks(gameTime),
                aiming,
                lastAmmoSharingStatus,
                successfulAimRounds,
                tactics.snapshot());
    }

    public record Diagnostics(
            String provider,
            @javax.annotation.Nullable ResourceLocation gunId,
            @javax.annotation.Nullable String gunType,
            boolean bound,
            boolean weaponDataReady,
            boolean suspended,
            WeaponReadiness readiness,
            int loadedAmmo,
            int magazineCapacity,
            boolean reloading,
            boolean coolingDown,
            boolean dryConfirmed,
            long dryRetryTicks,
            boolean aiming,
            GuardAmmoSharing.SharingStatus lastAmmoSharingStatus,
            long roundsFired,
            CachedTacticalState tacticalState) {
    }

    /** The cached tactical snapshot, for administrator inspection only. */
    public CachedTacticalState tacticalState() {
        return tactics.snapshot();
    }

    // Binding the held gun

    private boolean synchronizeWeapon() {
        int ordersRevision = GuardRoleResolver.effectiveRevision(guard);
        if (ordersRevision != trackedOrdersRevision) {
            trackedOrdersRevision = ordersRevision;
            nextStatsRefreshGameTick = Long.MIN_VALUE;
        }

        ItemStack heldStack = guard.getMainHandItem();
        if (heldStack != trackedStack) {
            invalidateShot(true);
            aimWeaponRevision++;
            trackedStack = heldStack;
            trackedStackIsGun = provider.isGun(heldStack);
            stats = null;
            gunId = null;
            gunType = null;
            lastAmmoSharingStatus = GuardAmmoSharing.SharingStatus.NOT_ATTEMPTED;
            lastAmmoSharedItems = 0;
            tactics.reset();
            nextStatsRefreshGameTick = Long.MIN_VALUE;
            cycle.resetForWeaponChange();
            if (!trackedStackIsGun) {
                return false;
            }
            backend.prepare(heldStack);
        } else if (!trackedStackIsGun) {
            return false;
        }

        long gameTime = guard.level().getGameTime();
        if (gameTime >= nextStatsRefreshGameTick && !refreshStats(heldStack, gameTime)) {
            return false;
        }
        return stats != null && !cycle.isSuspended();
    }

    private boolean refreshStats(ItemStack gunStack, long gameTime) {
        nextStatsRefreshGameTick = gameTime + config.performance.gunDataRefreshIntervalTicks.get();
        Optional<GunDescriptor> descriptor = provider.describe(gunStack);
        Optional<ManagedGunStats> refreshed = descriptor.isEmpty() ? Optional.empty() : backend.stats(gunStack);
        if (refreshed.isEmpty()) {
            stats = null;
            tactics.reset();
            suspendInvalidWeapon();
            return false;
        }

        ResourceLocation refreshedId = descriptor.get().ref().id();
        boolean definitionChanged = !refreshedId.equals(gunId) || !refreshed.get().equals(stats);
        boolean wasSuspended = cycle.isSuspended();
        gunId = refreshedId;
        gunType = descriptor.get().type();
        stats = refreshed.get();
        cycle.resume();
        if (definitionChanged || wasSuspended) {
            invalidateShot(true);
            aimWeaponRevision++;
            cycle.releaseTrigger();
        }

        float effectiveRange = stats.effectiveRange();
        boolean metadataReadable = Float.isFinite(effectiveRange) && effectiveRange > 0.0F;
        tactics.setEngagementRange(effectiveRange);
        WeaponTacticalTraits traits = WeaponTacticalTraits.NONE;
        try {
            traits = new WeaponTacticalTraits(stats.explosive(), stats.pellets(), stats.explosionRadius());
        } catch (RuntimeException exception) {
            // Tactical data shapes live firing safety; an unreadable trait closes the firing window.
            metadataReadable = false;
        }
        tactics.cache(gunId, gunType, metadataReadable, traits);
        return true;
    }

    private void suspendInvalidWeapon() {
        invalidateShot(true);
        aiming = false;
        cycle.cancelReload();
        cycle.suspend();
        nextStatsRefreshGameTick = guard.level().getGameTime()
                + config.performance.gunDataRefreshIntervalTicks.get();
    }

    /** The held stack is the one last read, its numbers are loaded, and no error has suspended it. */
    private boolean weaponBound() {
        return guard.getMainHandItem() == trackedStack
                && stats != null
                && !cycle.isSuspended();
    }

    // Readiness

    /** Reads bound state only; never scans reserves. */
    @Override
    public WeaponReadiness captureReadiness() {
        if (guard.getMainHandItem() != trackedStack || !trackedStackIsGun) {
            return WeaponReadiness.UNBOUND;
        }
        if (cycle.isSuspended() || stats == null) {
            return WeaponReadiness.SUSPENDED;
        }
        if (!hasValidFiringWindow()) {
            return WeaponReadiness.INVALID_FIRING_WINDOW;
        }
        try {
            return cycle.readiness(liveWeapon);
        } catch (RuntimeException ignored) {
            return WeaponReadiness.SUSPENDED;
        }
    }

    @Override
    public boolean hasCurrentWeaponData(ItemStack heldStack) {
        return heldStack == trackedStack
                && stats != null
                && !cycle.isSuspended();
    }

    @Override
    public boolean isCurrentWeaponDryConfirmed() {
        return guard.getMainHandItem() == trackedStack
                && cycle.isDryConfirmed();
    }

    @Override
    public GuardCombatStatus.WeaponActivity getPlayerFacingActivity() {
        if (cycle.isSuspended()) {
            return GuardCombatStatus.WeaponActivity.WEAPON_ERROR;
        }
        if (cycle.isDryConfirmed()) {
            return GuardCombatStatus.WeaponActivity.OUT_OF_AMMO;
        }
        if (!hasCurrentWeaponData(guard.getMainHandItem())) {
            return GuardCombatStatus.WeaponActivity.WEAPON_DATA_NOT_READY;
        }
        if (cycle.isReloading()) {
            return GuardCombatStatus.WeaponActivity.RELOADING;
        }
        if (cycle.isCoolingDown(guard.level().getGameTime())) {
            return GuardCombatStatus.WeaponActivity.COOLING_DOWN;
        }
        return GuardCombatStatus.WeaponActivity.READY;
    }

    // Tactical envelope

    @Override
    public double getPreferredApproachDistance() {
        return tactics.preferredApproachDistance(weaponBound(), gunId);
    }

    @Override
    public double getRetreatDistance() {
        return tactics.retreatDistance(weaponBound(), gunId);
    }

    @Override
    public int getRequiredVisibleTicks() {
        return tactics.requiredVisibleTicks(weaponBound(), gunId);
    }

    @Override
    public double getAimToleranceDegrees() {
        return tactics.aimToleranceDegrees(weaponBound(), gunId);
    }

    @Override
    public double getMinimumFiringRange() {
        return tactics.minimumFiringRange(weaponBound(), gunId);
    }

    @Override
    public float getMaximumFiringRange() {
        return tactics.maximumFiringRange(weaponBound(), gunId);
    }

    @Override
    public boolean hasValidFiringWindow() {
        return tactics.hasValidFiringWindow(weaponBound(), gunId);
    }

    @Override
    public boolean canFireAtDistanceSquared(double distanceSquared) {
        return trackedOrdersRevision == GuardRoleResolver.effectiveRevision(guard)
                && tactics.canFireAtDistanceSquared(weaponBound(), gunId, distanceSquared);
    }

    @Override
    public boolean isCurrentWeaponExplosive() {
        return guard.getMainHandItem() == trackedStack && tactics.isExplosiveFor(gunId);
    }

    @Override
    public boolean isWeaponAllowedByOrders() {
        return !isCurrentWeaponExplosive()
                || GuardRoleResolver.effectiveOrders(guard).permitsExplosiveWeapons();
    }

    // Aim and shot safety

    @Override
    public Vec3 intentionalAimPoint(LivingEntity target) {
        return aimSettings(target).aimPoint() == GuardAimPoint.CENTER_MASS
                ? target.getBoundingBox().getCenter() : target.getEyePosition();
    }

    private GuardAimSettings aimSettings(LivingEntity target) {
        return config.combat.aiming.resolve(target instanceof Player);
    }

    private void invalidateShot(boolean reset) {
        if (reset) {
            lastShotSafety = null;
            lastShotTarget = null;
            lastShotGameTick = Long.MIN_VALUE;
            aimState.reset();
        } else {
            aimState.invalidateProposal();
        }
    }

    /** Historical display only, never permission to fire. No world query or gun-mod call. */
    @Override
    public GuardShotSafety.Result currentShotSafety(LivingEntity target) {
        try {
            long age = guard.level().getGameTime() - lastShotGameTick;
            if (target == null || target != lastShotTarget || target != guard.getTarget()
                    || !target.isAlive() || age < 0 || age > SHOT_SAFETY_DISPLAY_TICKS
                    || target.level() != guard.level()
                    || lastShotWeaponRevision != aimWeaponRevision
                    || guard.getMainHandItem() != trackedStack
                    || lastShotOrdersRevision != GuardRoleResolver.effectiveRevision(guard)
                    || !aimSettings(target).equals(lastShotSettings)) {
                return UNCHECKED_SHOT;
            }
            return lastShotSafety();
        } catch (RuntimeException exception) {
            return UNAVAILABLE_SHOT;
        }
    }

    @Override
    public GuardShotSafety.Result lastShotSafety() {
        return lastShotSafety == null ? UNCHECKED_SHOT : lastShotSafety;
    }

    @Override
    public LivingEntity lastShotTarget() {
        return lastShotTarget;
    }

    @Override
    public long lastShotGameTick() {
        return lastShotGameTick;
    }

    /**
     * One round: eligibility, the goal's own check, a fresh aim, the ally and
     * blast check against the current world, then the gun mod's discharge.
     * Nothing is cached between rounds.
     */
    private ManagedWeaponCycle.Shot fireRound(BooleanSupplier roundCheck) {
        LivingEntity target = aimingTarget;
        if (!roundEligible(target) || !roundCheck.getAsBoolean()) {
            aimState.invalidateProposal();
            return ManagedWeaponCycle.Shot.HELD;
        }

        long gameTime = guard.level().getGameTime();
        GuardAimSettings settings = aimSettings(target);
        int ordersRevision = GuardRoleResolver.effectiveRevision(guard);
        lastShotTarget = target;
        lastShotGameTick = gameTime;
        lastShotWeaponRevision = aimWeaponRevision;
        lastShotOrdersRevision = ordersRevision;
        lastShotSettings = settings;
        lastShotSafety = UNCHECKED_SHOT;

        Optional<GuardShotAim> proposal = aimState.prepare(
                new GuardAimState.Context(target.getUUID(), aimWeaponRevision, ordersRevision, settings),
                gameTime,
                guard.getEyePosition(),
                GuardFriendlyFireSafety.nativeProjectileOrigin(guard),
                target.getBoundingBox(),
                target.getEyePosition());
        if (proposal.isEmpty()) {
            lastShotSafety = new GuardShotSafety.Result(GuardShotSafety.Status.INVALID_AIM, -1, null, null);
            return ManagedWeaponCycle.Shot.HELD;
        }
        GuardShotAim aim = proposal.get();
        lastShotSafety = GuardFriendlyFireSafety.diagnoseShot(
                guard, target, aim, shotBallistics(), config.safety.baseClearance.get(),
                stats.inheritsShooterMotion());
        if (!lastShotSafety.clear()) {
            return ManagedWeaponCycle.Shot.HELD;
        }

        ManagedGunBackend.Outcome outcome = backend.fire(
                guard, trackedStack, aim, entity -> !GuardFriendlyFireSafety.isProtected(guard, entity));
        if (outcome != ManagedGunBackend.Outcome.FIRED) {
            aimState.invalidateProposal();
            return ManagedWeaponCycle.Shot.FAILED;
        }
        backend.setLoaded(trackedStack, Math.max(0, backend.loaded(trackedStack) - 1));
        if (aimState.commit(aim)) {
            successfulAimRounds++;
        }
        return ManagedWeaponCycle.Shot.FIRED;
    }

    private boolean roundEligible(LivingEntity target) {
        return target != null
                && config.enabled.get()
                && guard.isAlive()
                && !guard.isRemoved()
                && target.isAlive()
                && target.level() == guard.level()
                && target == guard.getTarget()
                && !GuardRulesOfEngagement.isNonCombatPlayer(target)
                && guard.getMainHandItem() == trackedStack
                && guard.hasLineOfSight(target);
    }

    private GuardShotEnvelope.Ballistics shotBallistics() {
        GuardShotEnvelope.Ballistics base = stats.ballistics();
        if (!stats.inheritsShooterMotion()) {
            return base;
        }
        Vec3 motion = guard.getDeltaMovement();
        Vec3 inherited = new Vec3(motion.x, guard.onGround() ? 0 : motion.y, motion.z);
        return new GuardShotEnvelope.Ballistics(base.speed(), base.lifeTicks(), base.gravity(),
                base.friction(), base.inaccuracy(), inherited, base.blastReach(), base.fuseTicks());
    }

    // Reserve

    private boolean hasLoadableReserve() {
        if (GuardInfiniteAmmo.virtualReserve(guard)) {
            return true;
        }
        GuardAmmoHandler handler = GuardAmmoAccess.getAmmoHandler(guard);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (isLoadable(handler.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean isLoadable(ItemStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        AmmoCompatibility.Assessment assessment = provider.assessAmmo(trackedStack, candidate);
        return assessment.isCompatible() && assessment.kind() == AmmoCompatibility.AmmoKind.LOOSE;
    }

    /** Takes rounds from the ammo slots and puts them in the magazine, or changes nothing. */
    private int loadFromReserve(int rounds) {
        if (rounds <= 0) {
            return 0;
        }
        if (GuardInfiniteAmmo.virtualReserve(guard)) {
            backend.setLoaded(trackedStack, backend.loaded(trackedStack) + rounds);
            return rounds;
        }
        GuardAmmoHandler handler = GuardAmmoAccess.getAmmoHandler(guard);
        List<ItemStack> taken = new ArrayList<>();
        int moved = 0;
        for (int slot = 0; slot < handler.getSlots() && moved < rounds; slot++) {
            if (!isLoadable(handler.getStackInSlot(slot))) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, rounds - moved, false);
            if (!extracted.isEmpty()) {
                moved += extracted.getCount();
                taken.add(extracted);
            }
        }
        if (moved <= 0) {
            return 0;
        }
        try {
            backend.setLoaded(trackedStack, backend.loaded(trackedStack) + moved);
        } catch (RuntimeException exception) {
            // The magazine rejected the rounds. Put them back; drop any that no longer fit.
            for (ItemStack stack : taken) {
                ItemStack remainder = handler.insert(stack);
                if (!remainder.isEmpty()) {
                    guard.spawnAtLocation(remainder);
                }
            }
            throw exception;
        }
        return moved;
    }

    private boolean requestSharedAmmo() {
        GuardAmmoSharing.SharingResult sharing = GuardAmmoSharing.tryShare(guard);
        lastAmmoSharingStatus = sharing.status();
        lastAmmoSharedItems = sharing.transferred();
        return sharing.shared();
    }

    // Gates for sidearms, Quartermaster service, and coverage movement

    @Override
    public boolean isEquipmentServiceSafe() {
        return isCoverageMovementSafe();
    }

    @Override
    public boolean isCoverageMovementSafe() {
        return !isBusy() && !guard.isUsingItem();
    }

    @Override
    public boolean isSidearmSwapSafe() {
        return guard.getMainHandItem() == trackedStack && !isBusy();
    }

    @Override
    public boolean isCurrentWeaponSidearmCompatible() {
        return hasCurrentWeaponData(guard.getMainHandItem())
                && hasValidFiringWindow()
                && !isCurrentWeaponExplosive();
    }

    @Override
    public void prepareForSidearmTransition() {
        invalidateShot(true);
        aiming = false;
        try {
            synchronizeWeapon();
        } catch (RuntimeException exception) {
            suspendInvalidWeapon();
            return;
        }
        cycle.releaseTrigger();
    }

    @Override
    public void refreshAfterSidearmTransaction() {
        try {
            synchronizeWeapon();
        } catch (RuntimeException exception) {
            suspendInvalidWeapon();
        }
    }

    /** The bound gun as the cycle sees it. Only valid while {@link #stats} is loaded. */
    private final class LiveWeapon implements ManagedWeaponCycle.Weapon {
        private BooleanSupplier safeToFire;
        private BooleanSupplier burstSafetyCheck;

        @Override
        public int loaded() {
            return backend.loaded(trackedStack);
        }

        @Override
        public int capacity() {
            return stats.magazineCapacity();
        }

        @Override
        public long fireIntervalMillis() {
            return stats.fireIntervalMillis();
        }

        @Override
        public boolean automatic() {
            return stats.automatic();
        }

        @Override
        public int reloadTicks() {
            return stats.reloadTicks();
        }

        @Override
        public int roundsPerReloadStep() {
            return stats.roundsPerReloadStep();
        }

        @Override
        public boolean hasReserve() {
            return hasLoadableReserve();
        }

        @Override
        public int loadFromReserve(int rounds) {
            return ManagedWeaponDriver.this.loadFromReserve(rounds);
        }

        @Override
        public boolean requestAmmo() {
            return requestSharedAmmo();
        }

        @Override
        public ManagedWeaponCycle.Shot fire(boolean firstRoundThisTick) {
            BooleanSupplier roundCheck = firstRoundThisTick ? safeToFire : burstSafetyCheck;
            return roundCheck == null ? ManagedWeaponCycle.Shot.HELD : fireRound(roundCheck);
        }
    }
}
