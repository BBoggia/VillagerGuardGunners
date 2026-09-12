package com.gamergaming.villagerguardgunners.ai;

import java.util.EnumSet;
import com.gamergaming.villagerguardgunners.ai.tactics.GuardTacticalController;
import com.gamergaming.villagerguardgunners.ai.tactics.CombatIntentPolicy;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;

import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.gamergaming.villagerguardgunners.combat.GuardCombatStatus;
import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import com.gamergaming.villagerguardgunners.combat.engagement.EngagementMode;
import com.gamergaming.villagerguardgunners.combat.engagement.GuardRulesOfEngagement;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import com.gamergaming.villagerguardgunners.squad.SquadCommandType;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmRuntimeController;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmRuntimeController.RuntimeOutcome;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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

    private final Guard guard;
    private final PathNavigation navigation;
    private final NativeGunController gunController;
    private final GuardTacticalController tactics;
    private final com.gamergaming.villagerguardgunners.ai.tactics.TacticalNavigationController tacticalNavigation;
    private WeaponReadiness supportReadiness = WeaponReadiness.UNBOUND;
    private ItemStack supportStack = ItemStack.EMPTY;
    private long supportTick = Long.MIN_VALUE;
    private boolean supportMayFire;
    private final GuardSidearmRuntimeController sidearmController;
    private final SquadCommandMovementController commandMovement;
    private final VillagerGuardGunnersConfig config;

    private LivingEntity target;
    private boolean canSeeTarget;
    private boolean aimRequirementMet;
    private int visibleTicks;
    private long nextVisibilitySampleGameTick;
    private long friendlyBlockedUntilGameTick = Long.MIN_VALUE;
    private GuardCombatMovement.State movementState = GuardCombatMovement.State.HOLD;

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

    public boolean equipmentServiceSafe() { return !retainsCombatOwnership() && gunController.isEquipmentServiceSafe(); }

    public boolean coverageMovementSafe() { return !retainsCombatOwnership() && gunController.isCoverageMovementSafe(); }

    public GuardGunCombatGoal(Guard guard) {
        this.guard = guard;
        this.navigation = guard.getNavigation();
        this.gunController = new NativeGunController(guard);
        this.tactics = new GuardTacticalController(guard);
        this.tacticalNavigation = new com.gamergaming.villagerguardgunners.ai.tactics.TacticalNavigationController(guard);
        this.sidearmController = new GuardSidearmRuntimeController(guard);
        this.commandMovement = new SquadCommandMovementController(guard);
        this.config = ModConfigs.GUARD_GUNNERS;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private boolean running;
    private String lastInterruption = "none";
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> combatDimension;

    public boolean retainsCombatOwnership() {
        return config.enabled.get() && (running || tactics.hasContinuation());
    }
    public String movementOwner() {
        return guard.goalSelector.getRunningGoals()
                .filter(goal -> goal.getFlags().contains(Goal.Flag.MOVE))
                .map(goal -> goal.getGoal().getClass().getSimpleName())
                .collect(java.util.stream.Collectors.joining(", "));
    }
    public String lastInterruption() { return lastInterruption; }

    /** Entity removal/unload cannot leave native trigger callbacks or route references alive. */
    public static void onEntityLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Guard guard)) return;
        for (var wrapped : guard.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof GuardGunCombatGoal goal) goal.interrupt("left level");
        }
    }

    public void interrupt(String reason) {
        stop();
        lastInterruption = reason;
    }

    @Override
    public boolean canUse() {
        if (com.gamergaming.villagerguardgunners.medical.TransportRuntime.busy(guard)) return false;
        if (com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard) || com.gamergaming.villagerguardgunners.medical.MedicRuntime.retreating(guard) || guard.level().isClientSide || !com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).readable()) {
            return false;
        }
        LivingEntity currentTarget = guard.getTarget();
        boolean targetMayEngage = targetMayEngage(currentTarget);
        if (sidearmController.requiresRecoverySuppression()) {
            return true;
        }
        if (sidearmController.requiresLifecycleCleanup(
                currentTarget,
                config.enabled.get(),
                targetMayEngage)) {
            return true;
        }
        if (!config.enabled.get()
                || !NativeGunController.isGun(guard.getMainHandItem())) {
            return false;
        }
        commandMovement.synchronize(true);
        return ((currentTarget != null && currentTarget.isAlive()) || tactics.hasContinuation())
                && gunController.canUseGunGoal(guard.getMainHandItem());
    }

    @Override
    public boolean canContinueToUse() {
        if (com.gamergaming.villagerguardgunners.medical.TransportRuntime.busy(guard)) return false;
        if (com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard) || com.gamergaming.villagerguardgunners.medical.MedicRuntime.retreating(guard) || guard.level().isClientSide || !com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).readable()) {
            return false;
        }
        LivingEntity currentTarget = guard.getTarget();
        boolean targetMayEngage = targetMayEngage(currentTarget);
        if (sidearmController.requiresRecoverySuppression()) {
            return true;
        }
        if (sidearmController.requiresLifecycleCleanup(
                currentTarget,
                config.enabled.get(),
                targetMayEngage)) {
            return true;
        }
        if (!config.enabled.get()
                || !NativeGunController.isGun(guard.getMainHandItem())) {
            return false;
        }
        commandMovement.synchronize(true);
        return ((currentTarget != null && currentTarget.isAlive()) || tactics.hasContinuation())
                && gunController.canUseGunGoal(guard.getMainHandItem());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        running = true;
        combatDimension = guard.level().dimension();
        target = guard.getTarget();
        canSeeTarget = false;
        aimRequirementMet = false;
        visibleTicks = 0;
        movementState = GuardCombatMovement.State.HOLD;
        friendlyBlockedUntilGameTick = Long.MIN_VALUE;
        initializeCadence(guard.level().getGameTime());
        invalidateSafetyCache();
        // Guard Villagers' model applies a melee pose when aggressive, after
        // TaCZ's humanoid gun pose. Target ownership is sufficient combat state.
        guard.setAggressive(false);
        guard.setSprinting(false);
        navigation.stop();
        // A naturally equipped shield can already be raised before this goal
        // wins MOVE/LOOK ownership. TaCZ may reject aim while another item is
        // in use, while the upstream shield goal yields only after aim begins.
        // Release that circular wait when gun combat takes ownership.
        boolean targetMayEngage = targetMayEngage(target);
        if (sidearmController.requiresRecoverySuppression()) {
            gunController.prepareForSidearmTransition();
            navigation.stop();
            return;
        }
        boolean lifecycleCleanup = sidearmController.requiresLifecycleCleanup(
                target,
                config.enabled.get(),
                targetMayEngage);
        if (lifecycleCleanup) {
            gunController.prepareForSidearmTransition();
            RuntimeOutcome outcome = sidearmController.evaluate(
                    target,
                    gunController,
                    config.enabled.get(),
                    targetMayEngage);
            if (outcome.suppressSidearmFireForReturn()) {
                gunController.prepareForSidearmTransition();
            }
        } else {
            if (guard.isUsingItem()
                    && guard.getUsedItemHand() == InteractionHand.OFF_HAND) {
                guard.stopUsingItem();
            }
            gunController.startAiming();
        }
    }

    @Override
    public void stop() {
        running = false;
        combatDimension = null;
        lastInterruption = "goal stopped";
        if (sidearmController.isSidearmActive()) {
            gunController.prepareForSidearmTransition();
        }
        gunController.stopAiming();
        navigation.stop();
        target = null;
        lastEngagementTarget = null;
        lastEngagementReason = GuardRulesOfEngagement.EngagementReason.NO_TARGET;
        canSeeTarget = false;
        visibleTicks = 0;
        tactics.clear();
        tacticalNavigation.clear();
        supportReadiness = WeaponReadiness.UNBOUND;
        supportStack = ItemStack.EMPTY;
        supportTick = Long.MIN_VALUE;
        supportMayFire = false;
        aimRequirementMet = false;
        movementState = GuardCombatMovement.State.HOLD;
        friendlyBlockedUntilGameTick = Long.MIN_VALUE;
        commandMovement.stop();
        invalidateSafetyCache();
    }

    @Override
    public void tick() {
        supportMayFire = false;
        if (guard.isRemoved() || !guard.isAlive() || (combatDimension != null
                && combatDimension != guard.level().dimension())) {
            interrupt("entity lifecycle changed");
            return;
        }
        if (com.gamergaming.villagerguardgunners.medical.TransportRuntime.busy(guard)
                || com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard)
                || com.gamergaming.villagerguardgunners.medical.MedicRuntime.retreating(guard)
                || !com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).readable()) {
            interrupt("medical or equipment preemption");
            return;
        }
        if (!config.enabled.get()) {
            tacticalNavigation.clear();
            tactics.clear();
            navigation.stop();
            gunController.stopAiming();
            lastInterruption = "feature disabled";
            // Restoration may need several native-safe ticks, but cannot keep moving or firing.
            if (sidearmController.isSidearmActive()) {
                gunController.prepareForSidearmTransition();
                sidearmController.evaluate(guard.getTarget(), gunController, false, false);
            }
            return;
        }
        SquadCommand activeCommand = commandMovement.synchronize(true);
        LivingEntity currentTarget = guard.getTarget();
        long gameTime = guard.level().getGameTime();
        if (sidearmController.requiresRecoverySuppression()) {
            gunController.prepareForSidearmTransition();
            navigation.stop();
            return;
        }
        GuardRulesOfEngagement.EngagementReason engagementReason =
                currentTarget == null || !currentTarget.isAlive()
                        ? GuardRulesOfEngagement.EngagementReason.NO_TARGET
                        : classifyEngagement(currentTarget);
        boolean targetMayEngage = engagementReason.mayEngage();
        tactics.observe(currentTarget, targetMayEngage);
        boolean continuingTargetGap = config.enabled.get() && tactics.hasContinuation()
                && (currentTarget == null || !currentTarget.isAlive());
        if (sidearmController.isSidearmActive()) {
            RuntimeOutcome sidearmOutcome = sidearmController.evaluate(
                    currentTarget,
                    gunController,
                    config.enabled.get(),
                    targetMayEngage,
                    continuingTargetGap);
            if (sidearmOutcome.changedWeapon()) {
                transitionMovementState(GuardCombatMovement.State.HOLD, gameTime);
                invalidateSafetyCache();
                visibleTicks = 0;
                aimRequirementMet = false;
                return;
            }
            if (sidearmOutcome.suppressSidearmFireForReturn()) {
                gunController.prepareForSidearmTransition();
                navigation.stop();
                return;
            }
        }
        if (currentTarget == null || !currentTarget.isAlive()) {
            target = null;
            canSeeTarget = false;
            aimRequirementMet = false;
            invalidateSafetyCache();
            tactics.decide(null, false, false, gunController.captureReadiness(), activeCommand,
                    false, gunController.getRetreatDistance());
            gunController.tick(null, false, () -> false, () -> false, false);
            updateMovement(gameTime, Double.NaN, false, activeCommand);
            publishSupport(gameTime, false);
            return;
        }
        if (currentTarget != target) {
            target = currentTarget;
            canSeeTarget = false;
            visibleTicks = 0;
            aimRequirementMet = false;
            friendlyBlockedUntilGameTick = Long.MIN_VALUE;
            transitionMovementState(GuardCombatMovement.State.HOLD, gameTime);
            initializeCadence(gameTime);
            invalidateSafetyCache();
        }

        boolean mayEngage = engagementReason.mayEngage()
                && gunController.isWeaponAllowedByOrders();
        sampleVisibility(gameTime, gunController.getRequiredVisibleTicks());
        if (!mayEngage) {
            tactics.decide(currentTarget, canSeeTarget, false, gunController.captureReadiness(), activeCommand,
                    false, gunController.getRetreatDistance());
            updateLook();
            updateMovement(gameTime, 0.0D, false, activeCommand);
            LivingEntity heldTarget = target;
            gunController.tick(
                    heldTarget,
                    false,
                    this::hasClearShot,
                    () -> hasClearScheduledBurstShot(heldTarget), false);
            evaluatePrimarySidearm(currentTarget, engagementReason.mayEngage());
            if ((engagementReason.releaseTarget()
                    || tactics.decision().intent() == CombatIntentPolicy.Intent.FINISH_COMBAT)
                    && guard.getTarget() == currentTarget) {
                // Let Guard Villagers' existing target selector choose again.
                // The native controller has already released its trigger and
                // closed any scheduled-burst latch for this tick.
                guard.setTarget(null);
                tactics.clear();
            }
            publishSupport(gameTime, false);
            return;
        }

        int requiredVisibleTicks = gunController.getRequiredVisibleTicks();
        double aimToleranceDegrees = gunController.getAimToleranceDegrees();
        updateLook();
        double distanceSquared = canSeeTarget ? guard.distanceToSqr(currentTarget) : Double.NaN;
        var intent = tactics.decide(currentTarget, canSeeTarget, mayEngage,
                gunController.captureReadiness(), activeCommand,
                gameTime <= friendlyBlockedUntilGameTick, gunController.getRetreatDistance());
        aimRequirementMet = canSeeTarget
                && visibleTicks >= requiredVisibleTicks
                && isFacingTarget(aimToleranceDegrees);
        boolean routeWasMoving = tacticalNavigation.isMoving();
        boolean mayFire = mayEngage && !routeWasMoving
                && aimRequirementMet
                && gunController.hasCurrentWeaponData(guard.getMainHandItem())
                && gunController.canFireAtDistanceSquared(distanceSquared);
        LivingEntity burstTarget = target;
        gunController.tick(
                burstTarget,
                mayFire,
                this::hasClearShot,
                () -> hasClearScheduledBurstShot(burstTarget),
                canSeeTarget && !tacticalNavigation.isMoving()
                        && ((intent.intent() != CombatIntentPolicy.Intent.WITHDRAW
                                && intent.intent() != CombatIntentPolicy.Intent.CREATE_DISTANCE)
                            || gunController.captureReadiness() == WeaponReadiness.READY));
        evaluatePrimarySidearm(currentTarget, engagementReason.mayEngage());
        // Observe native reload/exhaustion and weapon swaps before issuing movement.
        tactics.decide(currentTarget, canSeeTarget, mayEngage, gunController.captureReadiness(),
                activeCommand, gameTime <= friendlyBlockedUntilGameTick, gunController.getRetreatDistance());
        updateMovement(gameTime, distanceSquared, mayEngage, activeCommand);
        // Route selection happens after native maintenance. Close aim and any
        // scheduled-burst latch in the same tick that movement starts.
        if (tacticalNavigation.isMoving()) {
            if (!routeWasMoving) gunController.stopAiming();
            mayFire = false;
        }
        publishSupport(gameTime, mayFire);
        if (tactics.decision().intent() == CombatIntentPolicy.Intent.FINISH_COMBAT
                && guard.getTarget() == currentTarget) {
            // Expired observations, no recent damage, and no credible continuing threat.
            // This is a combat-end policy, never an attempt to bypass a supply gate.
            guard.setTarget(null);
        }
    }

    public boolean ownsScreenedCombatApproach() { return commandMovement.ownsScreenedCombatPath(); }
    public void invalidateCombatApproach() { commandMovement.invalidateCombatPath(); }

    public GuardTacticalController tacticalController() { return tactics; }
    public com.gamergaming.villagerguardgunners.ai.tactics.TacticalNavigationController tacticalNavigation() { return tacticalNavigation; }

    private void publishSupport(long now, boolean mayFire) {
        supportMayFire = mayFire;
        supportReadiness = gunController.captureReadiness();
        supportStack = guard.getMainHandItem();
        supportTick = now;
    }

    /** Other guards read only this controller's already-computed shot and weapon facts. */
    public boolean providesTacticalSupport(long now, LivingEntity threatenedTarget) {
        return supportMayFire && !tacticalNavigation.isMoving() && threatenedTarget != null && guard.getTarget() == threatenedTarget
                && target == threatenedTarget && supportStack == guard.getMainHandItem()
                && supportReadiness == WeaponReadiness.READY && now >= supportTick && now - supportTick <= 10
                && safetyTarget == threatenedTarget && cachedSafeShot
                && now >= safetyCheckGameTime && now - safetyCheckGameTime <= 10;
    }

    private void evaluatePrimarySidearm(
            LivingEntity currentTarget,
            boolean targetMayEngage) {
        if (sidearmController.isSidearmActive()) {
            return;
        }
        RuntimeOutcome outcome = sidearmController.evaluate(
                currentTarget,
                gunController,
                config.enabled.get(),
                targetMayEngage);
        if (outcome.changedWeapon()) {
            invalidateSafetyCache();
            visibleTicks = 0;
            aimRequirementMet = false;
        }
    }

    private boolean targetMayEngage(@Nullable LivingEntity currentTarget) {
        return currentTarget != null
                && currentTarget.isAlive()
                && GuardRulesOfEngagement.mayEngage(guard, currentTarget);
    }

    /**
     * Produces the value synchronized by an open guard inventory. All combat
     * inputs come from fields already maintained by this goal, immutable
     * tactical metadata, and TaCZ's synchronized action state. No safety or
     * entity query is performed here.
     */
    public GuardCombatStatus getPlayerFacingStatus() {
        if (sidearmController.requiresRecoverySuppression()) {
            return GuardCombatStatus.RECOVERY_REQUIRED;
        }
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
            nextVisibilitySampleGameTick = gameTime
                    + config.performance.visibilitySampleIntervalTicks.get();
        }
        visibleTicks = canSeeTarget ? Math.min(visibleTicks + 1, requiredVisibleTicks) : 0;
    }

    private void updateLook() {
        var escapeLook = tacticalNavigation.lookDestination();
        if (escapeLook != null) {
            guard.getLookControl().setLookAt(escapeLook.x, guard.getEyeY(), escapeLook.z, 55.0F, 35.0F);
            return;
        }
        if (target == null) return;
        if (canSeeTarget) {
            guard.getLookControl().setLookAt(target, 55.0F, 35.0F);
        } else {
            var remembered = tactics.tracker().remembered(target.getUUID());
            if (remembered != null) {
                var position = remembered.position();
                guard.getLookControl().setLookAt(position.x, position.y, position.z, 55.0F, 35.0F);
            }
        }
        guard.yBodyRot = guard.getYHeadRot();
        guard.setYRot(guard.getYHeadRot());
    }

    private void updateMovement(
            long gameTime,
            double distanceSquared,
            boolean mayEngage,
            @Nullable SquadCommand activeCommand) {
        boolean pathSlotDue = commandMovement.pathSlotAvailable(gameTime);

        if (activeCommand != null) {
            SquadCommandMovementController.MovementOutcome outcome =
                    commandMovement.apply(
                            activeCommand,
                            false,
                            pathSlotDue,
                            config.movement.approachSpeed.get());
            if (outcome == SquadCommandMovementController.MovementOutcome.MOVING_TO_COMMAND) {
                tacticalNavigation.cancel();
                transitionMovementState(GuardCombatMovement.State.APPROACH, gameTime);
                return;
            }
            if (outcome == SquadCommandMovementController.MovementOutcome.HOLDING_POSITION) {
                tacticalNavigation.cancel();
                transitionMovementState(GuardCombatMovement.State.HOLD, gameTime);
                return;
            }
            if (outcome == SquadCommandMovementController.MovementOutcome.COMPLETED) {
                activeCommand = null;
            }
        }

        MovementOrder movementOrder = GuardRoleResolver.effectiveOrders(guard).movement();
        boolean defendOverride = activeCommand != null && activeCommand.type() == SquadCommandType.DEFEND;
        if (!defendOverride && movementOrder.holdsPosition()) {
            tacticalNavigation.cancel();
            transitionMovementState(GuardCombatMovement.State.HOLD, gameTime);
            return;
        }
        CombatIntentPolicy.Intent selected = tactics.decision().intent();
        if (tacticalNavigation.isLaneChange() && selected != CombatIntentPolicy.Intent.REPOSITION) {
            tacticalNavigation.cancel();
        }
        boolean defensive = selected == CombatIntentPolicy.Intent.WITHDRAW
                || selected == CombatIntentPolicy.Intent.CREATE_DISTANCE
                || selected == CombatIntentPolicy.Intent.REPOSITION;
        defensive = defensive || tacticalNavigation.needsHazardEscape();
        if (defensive || (tacticalNavigation.isMoving() && selected != CombatIntentPolicy.Intent.FINISH_COMBAT)) {
            transitionMovementState(GuardCombatMovement.State.RETREAT, gameTime);
            tacticalNavigation.tick(tactics, activeCommand, gunController.getPreferredApproachDistance(),
                    () -> commandMovement.takePathSlot(gameTime), selected == CombatIntentPolicy.Intent.REPOSITION
                            ? config.movement.repositionSpeed.get() : config.movement.retreatSpeed.get(),
                    selected == CombatIntentPolicy.Intent.REPOSITION, defensive);
            return;
        }
        tacticalNavigation.cancel();
        if (!mayEngage || !gunController.captureReadiness().permitsOffensiveMovement()) {
            transitionMovementState(GuardCombatMovement.State.HOLD, gameTime);
            return;
        }

        CombatIntentPolicy.Intent intent = tactics.decision().intent();
        GuardCombatMovement.State nextState = switch (intent) {
            case ENGAGE -> !canSeeTarget || (!defendOverride && movementOrder.treatAsPatrolling(guard.isPatrolling()))
                    || distanceSquared <= gunController.getPreferredApproachDistance()
                            * gunController.getPreferredApproachDistance()
                    ? GuardCombatMovement.State.HOLD : GuardCombatMovement.State.APPROACH;
            case REPOSITION -> GuardCombatMovement.State.REPOSITION;
            case CREATE_DISTANCE -> GuardCombatMovement.State.RETREAT;
            case WITHDRAW, HOLD, FINISH_COMBAT -> GuardCombatMovement.State.HOLD;
        };
        transitionMovementState(nextState, gameTime);

        switch (movementState) {
            case APPROACH -> {
                if (pathSlotDue) {
                    commandMovement.moveTowardCombatTarget(
                            activeCommand,
                            target,
                            config.movement.approachSpeed.get());
                }
            }
            case RETREAT, REPOSITION -> {
                // Defensive intents are handled by the route executor above.
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
    }

    private void initializeCadence(long gameTime) {
        int entityId = guard.getId();
        nextVisibilitySampleGameTick = GuardCombatMovement.firstDueTick(
                gameTime,
                entityId,
                config.performance.visibilitySampleIntervalTicks.get());

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
        EngagementMode engagementMode = GuardRoleResolver.effectiveOrders(guard)
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
                || tacticalNavigation.isMoving()
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
