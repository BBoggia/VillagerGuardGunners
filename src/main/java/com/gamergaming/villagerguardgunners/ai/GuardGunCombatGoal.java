package com.gamergaming.villagerguardgunners.ai;

import java.util.EnumSet;

import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.gamergaming.villagerguardgunners.combat.GuardCombatStatus;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.combat.engagement.EngagementMode;
import com.gamergaming.villagerguardgunners.combat.engagement.GuardRulesOfEngagement;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;

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
    private boolean aimRequirementMet;
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
    private float muzzleSafetyPadding;
    private float projectileSafetyPadding;
    private float blastSafetyPadding;
    private boolean cachedSafeShot;
    private double safetyShooterX;
    private double safetyShooterY;
    private double safetyShooterZ;
    private double safetyTargetX;
    private double safetyTargetY;
    private double safetyTargetZ;
    private LivingEntity lastEngagementTarget;
    private GuardRulesOfEngagement.EngagementReason lastEngagementReason =
            GuardRulesOfEngagement.EngagementReason.NO_TARGET;

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
                && shouldOwnTarget(currentTarget, false);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity currentTarget = guard.getTarget();
        return !guard.level().isClientSide
                && config.enabled.get()
                && currentTarget != null
                && currentTarget.isAlive()
                && NativeGunController.isGun(guard.getMainHandItem())
                && shouldOwnTarget(currentTarget, true);
    }

    /**
     * HOLD_FIRE must retain MOVE/LOOK ownership even when the weapon is dry or
     * temporarily unreadable; otherwise Guard Villagers' melee goal can take
     * over. Targets denied by a higher-priority safety rule also enter this
     * goal long enough to be released safely. Ordinary permitted targets still
     * yield to melee while the gun cannot engage.
     */
    private boolean shouldOwnTarget(LivingEntity currentTarget, boolean continuing) {
        GuardRulesOfEngagement.EngagementReason reason =
                classifyEngagement(currentTarget);
        if (reason == GuardRulesOfEngagement.EngagementReason.HOLD_FIRE
                || reason.releaseTarget()) {
            return true;
        }
        if (!gunController.isWeaponAllowedByOrders()) {
            return true;
        }
        return gunController.canUseGunGoal(guard.getMainHandItem())
                && (!continuing || gunController.hasValidFiringWindow());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        target = guard.getTarget();
        canSeeTarget = false;
        aimRequirementMet = false;
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
        aimRequirementMet = false;
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
            aimRequirementMet = false;
            unseenSamples = 0;
            friendlyBlockedUntilGameTick = Long.MIN_VALUE;
            transitionMovementState(GuardCombatMovement.State.HOLD, gameTime);
            initializeCadence(gameTime);
            invalidateSafetyCache();
        }

        GuardRulesOfEngagement.EngagementReason engagementReason =
                classifyEngagement(currentTarget);
        boolean mayEngage = engagementReason.mayEngage()
                && gunController.isWeaponAllowedByOrders();
        if (!mayEngage) {
            updateLook();
            updateMovement(gameTime, 0.0D, false);
            LivingEntity heldTarget = target;
            gunController.tick(
                    heldTarget,
                    false,
                    this::hasClearShot,
                    () -> hasClearScheduledBurstShot(heldTarget));
            if (engagementReason.releaseTarget() && guard.getTarget() == currentTarget) {
                // Let Guard Villagers' existing target selector choose again.
                // The native controller has already released its trigger and
                // closed any scheduled-burst latch for this tick.
                guard.setTarget(null);
            }
            return;
        }

        int requiredVisibleTicks = gunController.getRequiredVisibleTicks();
        double aimToleranceDegrees = gunController.getAimToleranceDegrees();
        sampleVisibility(gameTime, requiredVisibleTicks);
        updateLook();
        double distanceSquared = guard.distanceToSqr(currentTarget);
        updateMovement(gameTime, distanceSquared, mayEngage);

        aimRequirementMet = canSeeTarget
                && visibleTicks >= requiredVisibleTicks
                && isFacingTarget(aimToleranceDegrees);
        boolean mayFire = mayEngage
                && aimRequirementMet
                && gunController.hasCurrentWeaponData(guard.getMainHandItem())
                && gunController.canFireAtDistanceSquared(distanceSquared);
        LivingEntity burstTarget = target;
        gunController.tick(
                burstTarget,
                mayFire,
                this::hasClearShot,
                () -> hasClearScheduledBurstShot(burstTarget));
    }

    /**
     * Produces the value synchronized by an open guard inventory. All combat
     * inputs come from fields already maintained by this goal, immutable
     * tactical metadata, and TaCZ's synchronized action state. No safety or
     * entity query is performed here.
     */
    public GuardCombatStatus getPlayerFacingStatus() {
        if (!config.enabled.get()) {
            return GuardCombatStatus.DISABLED;
        }
        if (!NativeGunController.isGun(guard.getMainHandItem())) {
            return GuardCombatStatus.UNARMED;
        }

        LivingEntity currentTarget = guard.getTarget();
        if (currentTarget == null || !currentTarget.isAlive()) {
            return GuardCombatStatus.IDLE;
        }

        GuardRulesOfEngagement.EngagementReason engagementReason =
                currentTarget == lastEngagementTarget
                        ? lastEngagementReason
                        : classifyEngagement(currentTarget);
        boolean trackingCurrentTarget = currentTarget == target;
        double distanceSquared = guard.distanceToSqr(currentTarget);
        double minimumRange = gunController.getMinimumFiringRange();
        boolean validFiringWindow = gunController.hasValidFiringWindow();
        boolean belowMinimumRange = validFiringWindow
                && Double.isFinite(distanceSquared)
                && distanceSquared < minimumRange * minimumRange;
        boolean withinMaximumRange = GuardCombatMovement.isWithinEngagementRange(
                distanceSquared,
                gunController.getMaximumFiringRange());
        boolean unsafeShot = trackingCurrentTarget
                && currentTarget == safetyTarget
                && !cachedSafeShot
                && guard.level().getGameTime() <= friendlyBlockedUntilGameTick;

        return GuardCombatStatus.resolveActiveCombat(
                engagementReason.mayEngage(),
                gunController.isWeaponAllowedByOrders(),
                trackingCurrentTarget,
                trackingCurrentTarget && canSeeTarget,
                trackingCurrentTarget
                        && visibleTicks >= gunController.getRequiredVisibleTicks(),
                trackingCurrentTarget && aimRequirementMet,
                validFiringWindow,
                belowMinimumRange,
                withinMaximumRange,
                unsafeShot,
                movementState == GuardCombatMovement.State.REPOSITION,
                gunController.getPlayerFacingActivity());
    }

    private GuardRulesOfEngagement.EngagementReason classifyEngagement(
            LivingEntity currentTarget) {
        lastEngagementTarget = currentTarget;
        lastEngagementReason = GuardRulesOfEngagement.classify(guard, currentTarget);
        return lastEngagementReason;
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

    private void updateMovement(long gameTime, double distanceSquared, boolean mayEngage) {
        if (!mayEngage) {
            transitionMovementState(GuardCombatMovement.State.HOLD, gameTime);
            return;
        }
        MovementOrder movementOrder = GuardOrdersAccess.getOrders(guard).movement();
        if (movementOrder.holdsPosition()) {
            transitionMovementState(GuardCombatMovement.State.HOLD, gameTime);
            return;
        }
        boolean pathSlotDue = gameTime >= nextPathRecalculationGameTick;
        if (pathSlotDue) {
            // Advance by one interval only. A lag spike never creates a path
            // backlog, and idle/holding guards consume no path request.
            nextPathRecalculationGameTick = gameTime
                    + config.performance.pathRecalculationIntervalTicks.get();
        }

        if (gameTime >= nextMovementDecisionGameTick) {
            double retreatDistance = gunController.getRetreatDistance();
            double preferredApproachDistance = gunController.getPreferredApproachDistance();
            GuardCombatMovement.State nextState = GuardCombatMovement.selectState(
                    movementOrder.treatAsPatrolling(guard.isPatrolling()),
                    canSeeTarget,
                    unseenSamples,
                    UNSEEN_SAMPLES_BEFORE_APPROACH,
                    distanceSquared,
                    retreatDistance * retreatDistance,
                    preferredApproachDistance * preferredApproachDistance,
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

    private boolean isFacingTarget(double aimToleranceDegrees) {
        return isFacingTarget(target, aimToleranceDegrees);
    }

    private boolean isFacingTarget(
            LivingEntity observedTarget,
            double aimToleranceDegrees) {
        AimError error = measureAimError(observedTarget);
        return error.yawDegrees() <= aimToleranceDegrees
                && error.pitchDegrees() <= aimToleranceDegrees;
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
        int requiredVisibleTicks = gunController.getRequiredVisibleTicks();
        double aimToleranceDegrees = gunController.getAimToleranceDegrees();
        double distance = observedTarget == null
                ? Double.NaN
                : Math.sqrt(guard.distanceToSqr(observedTarget));
        double preferredRange = gunController.getPreferredApproachDistance();
        double retreatDistance = gunController.getRetreatDistance();
        double minimumRange = gunController.getMinimumFiringRange();
        float maximumRange = gunController.getMaximumFiringRange();
        boolean validFiringWindow = gunController.hasValidFiringWindow();
        boolean withinMaximumRange = observedTarget != null
                && GuardCombatMovement.isWithinEngagementRange(
                        guard.distanceToSqr(observedTarget),
                        maximumRange);
        boolean belowMinimumRange = observedTarget != null
                && validFiringWindow
                && Double.isFinite(distance)
                && distance < minimumRange;
        boolean withinFiringWindow = observedTarget != null
                && gunController.canFireAtDistanceSquared(
                        guard.distanceToSqr(observedTarget));
        AimError aimError = observedTarget == null
                ? new AimError(Double.NaN, Double.NaN)
                : measureAimError(observedTarget);
        float muzzleSafetyPadding = observedTarget == null || !nativeState.weaponDataReady()
                ? Float.NaN
                : gunController.getMuzzleSafetyPadding();
        float projectileSafetyPadding = gunController.getProjectileSafetyPadding(
                distance,
                muzzleSafetyPadding);
        float blastSafetyPadding = gunController.getBlastSafetyPadding(projectileSafetyPadding);
        GuardFriendlyFireSafety.DiagnosticResult safety = observedTarget == null
                ? new GuardFriendlyFireSafety.DiagnosticResult(
                        GuardFriendlyFireSafety.DiagnosticStatus.INVALID_PADDING,
                        null,
                        null)
                : GuardFriendlyFireSafety.diagnoseClearShot(
                        guard,
                        observedTarget,
                        muzzleSafetyPadding,
                        projectileSafetyPadding,
                        blastSafetyPadding);
        GuardRulesOfEngagement.EngagementReason engagementReason =
                GuardRulesOfEngagement.classify(guard, observedTarget);
        boolean weaponAllowedByOrders = gunController.isWeaponAllowedByOrders();
        EngagementMode engagementMode = GuardOrdersAccess.getOrders(guard)
                .resolveEngagementMode(config.engagement.mode.get());
        FireDecision decision = classifyDecision(
                observedTarget,
                active,
                requiredVisibleTicks,
                aimToleranceDegrees,
                aimError,
                validFiringWindow,
                belowMinimumRange,
                withinMaximumRange,
                safety,
                nativeState,
                engagementReason,
                weaponAllowedByOrders);

        return new Diagnostics(
                active,
                decision,
                observedTarget,
                distance,
                preferredRange,
                retreatDistance,
                minimumRange,
                maximumRange,
                validFiringWindow,
                withinFiringWindow,
                withinMaximumRange,
                canSeeTarget,
                visibleTicks,
                requiredVisibleTicks,
                aimError.yawDegrees(),
                aimError.pitchDegrees(),
                aimToleranceDegrees,
                movementState.name(),
                muzzleSafetyPadding,
                projectileSafetyPadding,
                blastSafetyPadding,
                safety,
                engagementMode,
                engagementReason,
                nativeState);
    }

    private FireDecision classifyDecision(
            LivingEntity observedTarget,
            boolean active,
            int requiredVisibleTicks,
            double aimToleranceDegrees,
            AimError aimError,
            boolean validFiringWindow,
            boolean belowMinimumRange,
            boolean withinMaximumRange,
            GuardFriendlyFireSafety.DiagnosticResult safety,
            NativeGunController.Diagnostics nativeState,
            GuardRulesOfEngagement.EngagementReason engagementReason,
            boolean weaponAllowedByOrders) {
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
        if (!engagementReason.mayEngage() || !weaponAllowedByOrders) {
            return FireDecision.RULES_OF_ENGAGEMENT;
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
        if (aimError.yawDegrees() > aimToleranceDegrees
                || aimError.pitchDegrees() > aimToleranceDegrees) {
            return FireDecision.AIMING;
        }
        if (!validFiringWindow) {
            return FireDecision.INVALID_FIRING_WINDOW;
        }
        if (belowMinimumRange) {
            return FireDecision.TOO_CLOSE;
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
            float refreshedBlastPadding =
                    gunController.getBlastSafetyPadding(projectileSafetyPadding);
            boolean cachedTacticsStillValid = gunController.isWeaponAllowedByOrders()
                    && gunController.canFireAtDistanceSquared(guard.distanceToSqr(target));
            if (cachedTacticsStillValid
                    && Float.compare(refreshedBlastPadding, blastSafetyPadding) == 0) {
                recordShotSafety(cachedSafeShot, gameTime);
                return cachedSafeShot;
            }
        }

        float currentMuzzlePadding = gunController.getMuzzleSafetyPadding();
        float currentProjectilePadding = gunController.getProjectileSafetyPadding(
                Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ),
                currentMuzzlePadding);
        float currentBlastPadding = gunController.getBlastSafetyPadding(currentProjectilePadding);
        if (!gunController.isWeaponAllowedByOrders()
                || !gunController.canFireAtDistanceSquared(guard.distanceToSqr(target))) {
            recordShotSafety(false, gameTime);
            return false;
        }
        if (target == safetyTarget
                && Float.compare(currentMuzzlePadding, muzzleSafetyPadding) == 0
                && Float.compare(currentProjectilePadding, projectileSafetyPadding) == 0
                && Float.compare(currentBlastPadding, blastSafetyPadding) == 0
                && cacheAge >= 0L
                && !cachedSafeShot
                && cacheAge <= config.performance.unsafeShotCacheTicks.get()) {
            recordShotSafety(false, gameTime);
            return cachedSafeShot;
        }

        safetyTarget = target;
        muzzleSafetyPadding = currentMuzzlePadding;
        projectileSafetyPadding = currentProjectilePadding;
        blastSafetyPadding = currentBlastPadding;
        safetyCheckGameTime = gameTime;
        safetyShooterX = shooterX;
        safetyShooterY = shooterY;
        safetyShooterZ = shooterZ;
        safetyTargetX = targetX;
        safetyTargetY = targetY;
        safetyTargetZ = targetZ;
        cachedSafeShot = GuardFriendlyFireSafety.hasClearShot(
                guard,
                target,
                currentMuzzlePadding,
                currentProjectilePadding,
                currentBlastPadding);
        recordShotSafety(cachedSafeShot, gameTime);
        return cachedSafeShot;
    }

    private boolean hasClearScheduledBurstShot(LivingEntity expectedTarget) {
        if (!config.enabled.get()
                || target != expectedTarget
                || guard.getTarget() != expectedTarget
                || !expectedTarget.isAlive()
                || !GuardRulesOfEngagement.mayEngage(guard, expectedTarget)
                || !gunController.isWeaponAllowedByOrders()
                || !NativeGunController.isGun(guard.getMainHandItem())) {
            return false;
        }

        int requiredVisibleTicks = gunController.getRequiredVisibleTicks();
        double aimToleranceDegrees = gunController.getAimToleranceDegrees();
        if (!canSeeTarget
                || visibleTicks < requiredVisibleTicks
                || !isFacingTarget(expectedTarget, aimToleranceDegrees)) {
            return false;
        }

        double distanceSquared = guard.distanceToSqr(expectedTarget);
        if (!gunController.canFireAtDistanceSquared(distanceSquared)) {
            return false;
        }

        double deltaX = expectedTarget.getX() - guard.getX();
        double deltaY = expectedTarget.getEyeY() - guard.getEyeY();
        double deltaZ = expectedTarget.getZ() - guard.getZ();
        float currentMuzzlePadding = gunController.getMuzzleSafetyPadding();
        float currentProjectilePadding = gunController.getProjectileSafetyPadding(
                Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ),
                currentMuzzlePadding);
        float currentBlastPadding = gunController.getBlastSafetyPadding(currentProjectilePadding);
        if (!gunController.isWeaponAllowedByOrders()
                || !gunController.canFireAtDistanceSquared(distanceSquared)) {
            recordShotSafety(false, guard.level().getGameTime());
            return false;
        }
        boolean safeShot = GuardFriendlyFireSafety.hasClearShot(
                guard,
                expectedTarget,
                currentMuzzlePadding,
                currentProjectilePadding,
                currentBlastPadding);
        recordShotSafety(safeShot, guard.level().getGameTime());
        return safeShot;
    }

    private void recordShotSafety(boolean safeShot, long gameTime) {
        friendlyBlockedUntilGameTick = safeShot
                ? Long.MIN_VALUE
                : gameTime + config.movement.friendlyRepositionTicks.get();
    }

    private void invalidateSafetyCache() {
        safetyTarget = null;
        safetyCheckGameTime = Long.MIN_VALUE;
        muzzleSafetyPadding = 0.0F;
        projectileSafetyPadding = 0.0F;
        blastSafetyPadding = 0.0F;
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
        RULES_OF_ENGAGEMENT,
        WEAPON_DATA_NOT_READY,
        WEAPON_SUSPENDED,
        WAITING_FOR_AMMO,
        NO_LINE_OF_SIGHT,
        ACQUIRING_TARGET,
        AIMING,
        INVALID_FIRING_WINDOW,
        TOO_CLOSE,
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
            double preferredRange,
            double retreatDistance,
            double minimumRange,
            float maximumRange,
            boolean validFiringWindow,
            boolean withinFiringWindow,
            boolean withinMaximumRange,
            boolean cachedLineOfSight,
            int visibleTicks,
            int requiredVisibleTicks,
            double yawErrorDegrees,
            double pitchErrorDegrees,
            double aimToleranceDegrees,
            String movementState,
            float muzzleSafetyPadding,
            float projectileSafetyPadding,
            float blastSafetyPadding,
            GuardFriendlyFireSafety.DiagnosticResult safety,
            EngagementMode engagementMode,
            GuardRulesOfEngagement.EngagementReason engagementReason,
            NativeGunController.Diagnostics nativeState) {
    }
}
