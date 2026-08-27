package com.gamergaming.villagerguardgunners.ai;

import java.util.EnumSet;

import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Lightweight guard decision layer for TaCZ guns.
 *
 * <p>The goal controls looking, coarse navigation, and whether the trigger may
 * be pressed. All firearm behavior is delegated to {@link NativeGunController}
 * and, through it, TaCZ's {@code IGunOperator}.</p>
 */
public final class GuardGunCombatGoal extends Goal {
    private static final int UNSEEN_SAMPLES_BEFORE_APPROACH = 2;
    private static final double MIN_REPOSITION_PROGRESS_SQUARED = 0.2D * 0.2D;

    private final Guard guard;
    private final PathNavigation navigation;
    private final NativeGunController gunController;
    private final VillagerGuardGunnersConfig config;

    private LivingEntity target;
    private boolean canSeeTarget;
    private int visibleTicks;
    private int unseenSamples;
    private long nextVisibilitySampleGameTick;
    private long nextMovementDecisionGameTick;
    private long nextPathRecalculationGameTick;
    private long friendlyBlockedUntilGameTick = Long.MIN_VALUE;
    private GuardCombatMovement.State movementState = GuardCombatMovement.State.HOLD;
    private float repositionDirection;
    private long nextRepositionProgressCheckGameTick = Long.MIN_VALUE;
    private double repositionAnchorX;
    private double repositionAnchorZ;

    private LivingEntity safetyTarget;
    private long safetyCheckGameTime = Long.MIN_VALUE;
    private float safetyPadding;
    private boolean cachedSafeShot;
    private double safetyShooterX;
    private double safetyShooterY;
    private double safetyShooterZ;
    private double safetyTargetX;
    private double safetyTargetY;
    private double safetyTargetZ;

    public GuardGunCombatGoal(Guard guard) {
        this.guard = guard;
        this.navigation = guard.getNavigation();
        this.gunController = new NativeGunController(guard);
        this.config = ModConfigs.GUARD_GUNNERS;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity currentTarget = guard.getTarget();
        return !guard.level().isClientSide
                && config.enabled.get()
                && currentTarget != null
                && currentTarget.isAlive()
                && NativeGunController.isGun(guard.getMainHandItem())
                && gunController.canUseGunGoal(guard.getMainHandItem());
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity currentTarget = guard.getTarget();
        return !guard.level().isClientSide
                && config.enabled.get()
                && currentTarget != null
                && currentTarget.isAlive()
                && NativeGunController.isGun(guard.getMainHandItem())
                && gunController.canUseGunGoal(guard.getMainHandItem());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        target = guard.getTarget();
        canSeeTarget = false;
        visibleTicks = 0;
        unseenSamples = 0;
        movementState = GuardCombatMovement.State.HOLD;
        repositionDirection = GuardCombatMovement.lateralDirection(guard.getId());
        nextRepositionProgressCheckGameTick = Long.MIN_VALUE;
        friendlyBlockedUntilGameTick = Long.MIN_VALUE;
        initializeCadence(guard.level().getGameTime());
        invalidateSafetyCache();
        // Guard Villagers' model applies a melee pose when aggressive, after
        // TaCZ's humanoid gun pose. Target ownership is sufficient combat state.
        guard.setAggressive(false);
        guard.setSprinting(false);
        navigation.stop();
        gunController.startAiming();
    }

    @Override
    public void stop() {
        gunController.stopAiming();
        navigation.stop();
        target = null;
        movementState = GuardCombatMovement.State.HOLD;
        friendlyBlockedUntilGameTick = Long.MIN_VALUE;
        invalidateSafetyCache();
    }

    @Override
    public void tick() {
        LivingEntity currentTarget = guard.getTarget();
        if (currentTarget == null || !currentTarget.isAlive()) {
            return;
        }
        long gameTime = guard.level().getGameTime();
        if (currentTarget != target) {
            target = currentTarget;
            visibleTicks = 0;
            unseenSamples = 0;
            friendlyBlockedUntilGameTick = Long.MIN_VALUE;
            transitionMovementState(GuardCombatMovement.State.HOLD, gameTime);
            initializeCadence(gameTime);
            invalidateSafetyCache();
        }

        int requiredVisibleTicks = config.combat.requiredVisibleTicks.get();
        sampleVisibility(gameTime, requiredVisibleTicks);
        updateLook();
        double distanceSquared = guard.distanceToSqr(currentTarget);
        double preferredEngagementRange = gunController.getPreferredEngagementRange();
        double maximumFiringRange = gunController.getMaximumFiringRange();
        updateMovement(gameTime, distanceSquared, preferredEngagementRange);

        boolean mayFire = canSeeTarget
                && visibleTicks >= requiredVisibleTicks
                && isFacingTarget()
                && gunController.hasCurrentWeaponData(guard.getMainHandItem())
                && GuardCombatMovement.isWithinEngagementRange(distanceSquared, maximumFiringRange);
        LivingEntity burstTarget = target;
        gunController.tick(
                burstTarget,
                mayFire,
                this::hasClearShot,
                () -> hasClearScheduledBurstShot(burstTarget));
    }

    private void sampleVisibility(long gameTime, int requiredVisibleTicks) {
        if (gameTime >= nextVisibilitySampleGameTick) {
            canSeeTarget = guard.getSensing().hasLineOfSight(target);
            unseenSamples = canSeeTarget
                    ? 0
                    : Math.min(unseenSamples + 1, UNSEEN_SAMPLES_BEFORE_APPROACH);
            nextVisibilitySampleGameTick = gameTime
                    + config.performance.visibilitySampleIntervalTicks.get();
        }
        visibleTicks = canSeeTarget ? Math.min(visibleTicks + 1, requiredVisibleTicks) : 0;
    }

    private void updateLook() {
        guard.getLookControl().setLookAt(target, 55.0F, 35.0F);
        guard.yBodyRot = guard.getYHeadRot();
        guard.setYRot(guard.getYHeadRot());
    }

    private void updateMovement(long gameTime, double distanceSquared, double engagementRange) {
        boolean pathSlotDue = gameTime >= nextPathRecalculationGameTick;
        if (pathSlotDue) {
            // Advance by one interval only. A lag spike never creates a path
            // backlog, and idle/holding guards consume no path request.
            nextPathRecalculationGameTick = gameTime
                    + config.performance.pathRecalculationIntervalTicks.get();
        }

        if (gameTime >= nextMovementDecisionGameTick) {
            double closeRange = config.combat.closeRangeDistance.get();
            GuardCombatMovement.State nextState = GuardCombatMovement.selectState(
                    guard.isPatrolling(),
                    canSeeTarget,
                    unseenSamples,
                    UNSEEN_SAMPLES_BEFORE_APPROACH,
                    distanceSquared,
                    closeRange * closeRange,
                    engagementRange * engagementRange,
                    gameTime <= friendlyBlockedUntilGameTick);
            transitionMovementState(nextState, gameTime);
            nextMovementDecisionGameTick = gameTime
                    + config.performance.movementDecisionIntervalTicks.get();
        }

        switch (movementState) {
            case APPROACH -> {
                if (pathSlotDue) {
                    navigation.moveTo(target, config.movement.approachSpeed.get());
                }
            }
            case RETREAT -> guard.getMoveControl().strafe(
                    -config.movement.retreatSpeed.get().floatValue(),
                    0.0F);
            case REPOSITION -> {
                recoverBlockedReposition(gameTime);
                guard.getMoveControl().strafe(
                        0.0F,
                        repositionDirection * config.movement.repositionSpeed.get().floatValue());
            }
            case HOLD -> {
                // Navigation is stopped once on entry, not every tick.
            }
        }
    }

    private void transitionMovementState(GuardCombatMovement.State nextState, long gameTime) {
        if (movementState == nextState) {
            return;
        }
        if (nextState != GuardCombatMovement.State.APPROACH) {
            navigation.stop();
        }
        movementState = nextState;
        if (nextState == GuardCombatMovement.State.REPOSITION) {
            repositionAnchorX = guard.getX();
            repositionAnchorZ = guard.getZ();
            nextRepositionProgressCheckGameTick = gameTime
                    + config.performance.repositionProgressCheckTicks.get();
        }
    }

    private void recoverBlockedReposition(long gameTime) {
        if (gameTime < nextRepositionProgressCheckGameTick) {
            return;
        }
        if (!GuardCombatMovement.madeLateralProgress(
                repositionAnchorX,
                repositionAnchorZ,
                guard.getX(),
                guard.getZ(),
                MIN_REPOSITION_PROGRESS_SQUARED)) {
            repositionDirection = -repositionDirection;
        }
        repositionAnchorX = guard.getX();
        repositionAnchorZ = guard.getZ();
        nextRepositionProgressCheckGameTick = gameTime
                + config.performance.repositionProgressCheckTicks.get();
    }

    private void initializeCadence(long gameTime) {
        int entityId = guard.getId();
        nextVisibilitySampleGameTick = GuardCombatMovement.firstDueTick(
                gameTime,
                entityId,
                config.performance.visibilitySampleIntervalTicks.get());
        nextMovementDecisionGameTick = GuardCombatMovement.firstDueTick(
                gameTime,
                entityId,
                config.performance.movementDecisionIntervalTicks.get());
        nextPathRecalculationGameTick = GuardCombatMovement.firstDueTick(
                gameTime,
                entityId,
                config.performance.pathRecalculationIntervalTicks.get());
    }

    private boolean isFacingTarget() {
        return isFacingTarget(target);
    }

    private boolean isFacingTarget(LivingEntity observedTarget) {
        AimError error = measureAimError(observedTarget);
        double tolerance = config.combat.aimToleranceDegrees.get();
        return error.yawDegrees() <= tolerance && error.pitchDegrees() <= tolerance;
    }

    private AimError measureAimError(LivingEntity observedTarget) {
        double deltaX = observedTarget.getX() - guard.getX();
        double deltaY = observedTarget.getEyeY() - guard.getEyeY();
        double deltaZ = observedTarget.getZ() - guard.getZ();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float targetYaw = (float) (Mth.atan2(deltaZ, deltaX) * Mth.RAD_TO_DEG) - 90.0F;
        float targetPitch = (float) -(Mth.atan2(deltaY, horizontalDistance) * Mth.RAD_TO_DEG);
        return new AimError(
                Math.abs(Mth.wrapDegrees(targetYaw - guard.getYHeadRot())),
                Math.abs(Mth.wrapDegrees(targetPitch - guard.getXRot())));
    }

    /**
     * Builds an allocating snapshot only for the explicit administrator
     * inspection command. It is not called from the combat tick.
     */
    public Diagnostics captureDiagnostics() {
        LivingEntity observedTarget = target != null ? target : guard.getTarget();
        boolean active = target != null;
        NativeGunController.Diagnostics nativeState = gunController.captureDiagnostics();
        int requiredVisibleTicks = config.combat.requiredVisibleTicks.get();
        double distance = observedTarget == null
                ? Double.NaN
                : Math.sqrt(guard.distanceToSqr(observedTarget));
        float preferredRange = gunController.getPreferredEngagementRange();
        float maximumRange = gunController.getMaximumFiringRange();
        boolean withinMaximumRange = observedTarget != null
                && GuardCombatMovement.isWithinEngagementRange(
                        guard.distanceToSqr(observedTarget),
                        maximumRange);
        AimError aimError = observedTarget == null
                ? new AimError(Double.NaN, Double.NaN)
                : measureAimError(observedTarget);
        float safetyPadding = observedTarget == null || !nativeState.weaponDataReady()
                ? Float.NaN
                : gunController.getSafetyPadding(distance);
        GuardFriendlyFireSafety.DiagnosticResult safety = observedTarget == null
                ? new GuardFriendlyFireSafety.DiagnosticResult(
                        GuardFriendlyFireSafety.DiagnosticStatus.INVALID_PADDING,
                        null)
                : GuardFriendlyFireSafety.diagnoseClearShot(guard, observedTarget, safetyPadding);
        FireDecision decision = classifyDecision(
                observedTarget,
                active,
                requiredVisibleTicks,
                aimError,
                withinMaximumRange,
                safety,
                nativeState);

        return new Diagnostics(
                active,
                decision,
                observedTarget,
                distance,
                preferredRange,
                maximumRange,
                withinMaximumRange,
                canSeeTarget,
                visibleTicks,
                requiredVisibleTicks,
                aimError.yawDegrees(),
                aimError.pitchDegrees(),
                config.combat.aimToleranceDegrees.get(),
                movementState.name(),
                safetyPadding,
                safety,
                nativeState);
    }

    private FireDecision classifyDecision(
            LivingEntity observedTarget,
            boolean active,
            int requiredVisibleTicks,
            AimError aimError,
            boolean withinMaximumRange,
            GuardFriendlyFireSafety.DiagnosticResult safety,
            NativeGunController.Diagnostics nativeState) {
        if (!config.enabled.get()) {
            return FireDecision.DISABLED;
        }
        if (observedTarget == null) {
            return FireDecision.NO_TARGET;
        }
        if (!observedTarget.isAlive()) {
            return FireDecision.TARGET_DEAD;
        }
        if (!NativeGunController.isGun(guard.getMainHandItem())) {
            return FireDecision.NO_GUN;
        }
        if (nativeState.suspended()) {
            return FireDecision.WEAPON_SUSPENDED;
        }
        if (nativeState.dryRetryTicks() > 0L) {
            return FireDecision.WAITING_FOR_AMMO;
        }
        if (!active) {
            return FireDecision.GOAL_INACTIVE;
        }
        if (!nativeState.weaponDataReady()) {
            return FireDecision.WEAPON_DATA_NOT_READY;
        }
        if (!canSeeTarget) {
            return FireDecision.NO_LINE_OF_SIGHT;
        }
        if (visibleTicks < requiredVisibleTicks) {
            return FireDecision.ACQUIRING_TARGET;
        }
        if (aimError.yawDegrees() > config.combat.aimToleranceDegrees.get()
                || aimError.pitchDegrees() > config.combat.aimToleranceDegrees.get()) {
            return FireDecision.AIMING;
        }
        if (!withinMaximumRange) {
            return FireDecision.OUT_OF_RANGE;
        }
        if (!safety.clear()) {
            return FireDecision.UNSAFE_SHOT;
        }
        if (nativeState.reloading()) {
            return FireDecision.RELOADING;
        }
        if (nativeState.bolting()) {
            return FireDecision.BOLTING;
        }
        if (nativeState.drawCooldownMillis() > 0L) {
            return FireDecision.DRAWING;
        }
        if (nativeState.shootCooldownMillis() > 0L) {
            return FireDecision.COOLING_DOWN;
        }
        return FireDecision.READY_TO_FIRE;
    }

    private boolean hasClearShot() {
        long gameTime = guard.level().getGameTime();
        double shooterX = guard.getX();
        double shooterY = guard.getEyeY();
        double shooterZ = guard.getZ();
        double targetX = target.getX();
        double targetY = target.getEyeY();
        double targetZ = target.getZ();
        double deltaX = targetX - shooterX;
        double deltaY = targetY - shooterY;
        double deltaZ = targetZ - shooterZ;
        long cacheAge = gameTime - safetyCheckGameTime;
        boolean endpointsUnchanged = shooterX == safetyShooterX
                && shooterY == safetyShooterY
                && shooterZ == safetyShooterZ
                && targetX == safetyTargetX
                && targetY == safetyTargetY
                && targetZ == safetyTargetZ;
        if (target == safetyTarget && cacheAge == 0L && endpointsUnchanged) {
            recordShotSafety(cachedSafeShot, gameTime);
            return cachedSafeShot;
        }

        float currentPadding = gunController.getSafetyPadding(
                Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
        if (target == safetyTarget
                && Float.compare(currentPadding, safetyPadding) == 0
                && cacheAge >= 0L
                && !cachedSafeShot
                && cacheAge <= config.performance.unsafeShotCacheTicks.get()) {
            recordShotSafety(false, gameTime);
            return cachedSafeShot;
        }

        safetyTarget = target;
        safetyPadding = currentPadding;
        safetyCheckGameTime = gameTime;
        safetyShooterX = shooterX;
        safetyShooterY = shooterY;
        safetyShooterZ = shooterZ;
        safetyTargetX = targetX;
        safetyTargetY = targetY;
        safetyTargetZ = targetZ;
        cachedSafeShot = GuardFriendlyFireSafety.hasClearShot(guard, target, currentPadding);
        recordShotSafety(cachedSafeShot, gameTime);
        return cachedSafeShot;
    }

    private boolean hasClearScheduledBurstShot(LivingEntity expectedTarget) {
        if (!config.enabled.get()
                || target != expectedTarget
                || guard.getTarget() != expectedTarget
                || !expectedTarget.isAlive()
                || !NativeGunController.isGun(guard.getMainHandItem())
                || !isFacingTarget()) {
            return false;
        }

        double distanceSquared = guard.distanceToSqr(expectedTarget);
        double maximumFiringRange = gunController.getMaximumFiringRange();
        if (!GuardCombatMovement.isWithinEngagementRange(distanceSquared, maximumFiringRange)) {
            return false;
        }

        double deltaX = expectedTarget.getX() - guard.getX();
        double deltaY = expectedTarget.getEyeY() - guard.getEyeY();
        double deltaZ = expectedTarget.getZ() - guard.getZ();
        float currentPadding = gunController.getSafetyPadding(
                Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
        return GuardFriendlyFireSafety.hasClearShot(guard, expectedTarget, currentPadding);
    }

    private void recordShotSafety(boolean safeShot, long gameTime) {
        friendlyBlockedUntilGameTick = safeShot
                ? Long.MIN_VALUE
                : gameTime + config.movement.friendlyRepositionTicks.get();
    }

    private void invalidateSafetyCache() {
        safetyTarget = null;
        safetyCheckGameTime = Long.MIN_VALUE;
        safetyPadding = 0.0F;
        cachedSafeShot = false;
        safetyShooterX = 0.0D;
        safetyShooterY = 0.0D;
        safetyShooterZ = 0.0D;
        safetyTargetX = 0.0D;
        safetyTargetY = 0.0D;
        safetyTargetZ = 0.0D;
    }

    private record AimError(double yawDegrees, double pitchDegrees) {
    }

    public enum FireDecision {
        DISABLED,
        NO_TARGET,
        TARGET_DEAD,
        NO_GUN,
        GOAL_INACTIVE,
        WEAPON_DATA_NOT_READY,
        WEAPON_SUSPENDED,
        WAITING_FOR_AMMO,
        NO_LINE_OF_SIGHT,
        ACQUIRING_TARGET,
        AIMING,
        OUT_OF_RANGE,
        UNSAFE_SHOT,
        RELOADING,
        BOLTING,
        DRAWING,
        COOLING_DOWN,
        READY_TO_FIRE
    }

    public record Diagnostics(
            boolean goalActive,
            FireDecision decision,
            LivingEntity target,
            double distance,
            float preferredRange,
            float maximumRange,
            boolean withinMaximumRange,
            boolean cachedLineOfSight,
            int visibleTicks,
            int requiredVisibleTicks,
            double yawErrorDegrees,
            double pitchErrorDegrees,
            double aimToleranceDegrees,
            String movementState,
            float safetyPadding,
            GuardFriendlyFireSafety.DiagnosticResult safety,
            NativeGunController.Diagnostics nativeState) {
    }
}
