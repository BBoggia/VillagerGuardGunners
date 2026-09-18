package com.gamergaming.villagerguardgunners.ai;

import java.util.EnumSet;
import com.gamergaming.villagerguardgunners.ai.tactics.GuardTacticalController;
import com.gamergaming.villagerguardgunners.ai.tactics.CombatIntentPolicy;
import com.gamergaming.villagerguardgunners.ai.tactics.TacticalFiringLane;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;

import com.gamergaming.villagerguardgunners.combat.GuardShotSafety;
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
    private final com.gamergaming.villagerguardgunners.ai.tactics.GuardTargetRanking targetRanking;
    private final com.gamergaming.villagerguardgunners.ai.tactics.GuardInvestigationController investigation;
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
    private boolean cachedSafeShot;
    private GuardShotSafety.Result recordedShotSafety;
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
        this.targetRanking = new com.gamergaming.villagerguardgunners.ai.tactics.GuardTargetRanking(guard);
        this.investigation = new com.gamergaming.villagerguardgunners.ai.tactics.GuardInvestigationController(guard);
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
        com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.forget(guard);
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
        com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.capture(guard);
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
            cancelEmergencyResupply();
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
        var emergency = com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess.getController(guard);
        if (emergency != null) emergency.cancelEmergency();
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
        targetRanking.clear(); investigation.clear();
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
            cancelEmergencyResupply();
            tacticalNavigation.clear();
            targetRanking.clear(); investigation.clear();
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
        com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.capture(guard);
        SquadCommand activeCommand = commandMovement.synchronize(true);
        LivingEntity currentTarget = guard.getTarget();
        long gameTime = guard.level().getGameTime();
        if (sidearmController.requiresRecoverySuppression()) {
            cancelEmergencyResupply();
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
        LivingEntity preferred = targetRanking.choose(tactics, currentTarget, activeCommand,
                gunController.getMinimumFiringRange(), gunController.getMaximumFiringRange(),
                gameTime <= friendlyBlockedUntilGameTick);
        if (preferred != currentTarget) {
            LivingEntity previousTarget = currentTarget;
            guard.setTarget(preferred);
            currentTarget = guard.getTarget(); // A Forge hook can replace or reject the proposal.
            if (currentTarget != previousTarget) {
                gunController.stopAiming();
                tacticalNavigation.cancel(); investigation.clear(); commandMovement.invalidateCombatPath();
            } else targetRanking.rejectedChange();
            engagementReason = currentTarget == null ? GuardRulesOfEngagement.EngagementReason.NO_TARGET
                    : classifyEngagement(currentTarget);
            targetMayEngage = engagementReason.mayEngage();
        }
        investigation.observe(currentTarget, tactics.tracker());
        var resupply = com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess.getController(guard);
        boolean dry = gunController.captureReadiness() == WeaponReadiness.DRY;
        boolean supplyWindow = dry && tactics.assessment().complete()
                && tactics.assessment().nearestDistance() >= 8 && !tactics.assessment().explosiveDanger()
                && tactics.assessment().recentDamageFraction() == 0
                && (currentTarget == null || guard.distanceToSqr(currentTarget) >= 64);
        if (resupply != null && (dry || resupply.isEmergency())) {
            if (supplyWindow) {
                gunController.stopAiming();
                if (resupply.tickEmergency(true, tactics.tracker().observations(), () -> {
                    tacticalNavigation.cancel(); investigation.cancel();
                    commandMovement.invalidateCombatPath();
                    navigation.stop();
                })) {
                    movementState = GuardCombatMovement.State.RETREAT;
                    publishSupport(gameTime, false);
                    return;
                }
            } else resupply.cancelEmergency();
        }
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
            gunController.stopAiming();
            // Target replacement must not move the engagement's patrol/follow anchor.
            tacticalNavigation.cancel(); investigation.cancel(); commandMovement.invalidateCombatPath();
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
                (gameTime <= friendlyBlockedUntilGameTick || tacticalNavigation.canContinueLane(
                        target, canSeeTarget, mayEngage, gunController.captureReadiness())), gunController.getRetreatDistance(),
                investigation.canInvestigate(target, canSeeTarget, mayEngage, gunController.captureReadiness(), activeCommand));
        aimRequirementMet = canSeeTarget
                && visibleTicks >= requiredVisibleTicks
                && isFacingTarget(aimToleranceDegrees);
        boolean routeWasMoving = tacticalNavigation.isMoving() || investigation.isMoving();
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
                canSeeTarget && !tacticalNavigation.isMoving() && !investigation.isMoving()
                        && ((intent.intent() != CombatIntentPolicy.Intent.WITHDRAW
                                && intent.intent() != CombatIntentPolicy.Intent.CREATE_DISTANCE)
                            || gunController.captureReadiness() == WeaponReadiness.READY));
        var shotSafety = gunController.lastShotSafety();
        if (gunController.lastShotTarget() == target && shotSafety != recordedShotSafety) {
            recordedShotSafety = shotSafety;
            // Tactical/support history only: never reused as permission for another discharge.
            safetyTarget = target;
            safetyCheckGameTime = gunController.lastShotGameTick();
            cachedSafeShot = shotSafety.clear();
            if (shotSafety.clear()) recordShotSafety(true, gameTime);
            else if (shotSafety.status() == GuardShotSafety.Status.PROTECTED_ENTITY_IN_PATH
                    || shotSafety.status() == GuardShotSafety.Status.EXPLOSIVE_BLOCK_IMPACT
                    || shotSafety.status() == GuardShotSafety.Status.EXPLOSIVE_ENTITY_IMPACT) {
                recordShotSafety(false, safetyCheckGameTime);
            }
        }
        evaluatePrimarySidearm(currentTarget, engagementReason.mayEngage());
        // Observe native reload/exhaustion and weapon swaps before issuing movement.
        tactics.decide(currentTarget, canSeeTarget, mayEngage, gunController.captureReadiness(),
                activeCommand, (gameTime <= friendlyBlockedUntilGameTick || tacticalNavigation.canContinueLane(
                        target, canSeeTarget, mayEngage, gunController.captureReadiness())), gunController.getRetreatDistance(),
                investigation.canInvestigate(target, canSeeTarget, mayEngage, gunController.captureReadiness(), activeCommand));
        updateMovement(gameTime, distanceSquared, mayEngage, activeCommand);
        // Route selection happens after native maintenance. Close aim and any
        // scheduled-burst latch in the same tick that movement starts.
        if (tacticalNavigation.isMoving() || investigation.isMoving()) {
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

    public com.gamergaming.villagerguardgunners.ai.tactics.GuardInvestigationController investigation() { return investigation; }
    public String targetRankingStatus() { return targetRanking.status(); }
    private void cancelEmergencyResupply() {
        var controller = com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess.getController(guard);
        if (controller != null) controller.cancelEmergency();
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
        return supportMayFire && !tacticalNavigation.isMoving() && !investigation.isMoving() && threatenedTarget != null && guard.getTarget() == threatenedTarget
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
        GuardShotSafety.Status shotStatus = trackingCurrentTarget
                ? gunController.currentShotSafety(currentTarget).status() : GuardShotSafety.Status.NOT_EVALUATED;

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
                shotStatus,
                investigation.isMoving() || (tacticalNavigation.isMoving() && tacticalNavigation.isLaneChange()),
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
        var escapeLook = investigation.isMoving() ? investigation.lookDestination() : tacticalNavigation.lookDestination();
        if (escapeLook != null) {
            guard.getLookControl().setLookAt(escapeLook.x, guard.getEyeY(), escapeLook.z, 55.0F, 35.0F);
            return;
        }
        if (target == null) return;
        if (canSeeTarget) {
            var point = gunController.intentionalAimPoint(target);
            guard.getLookControl().setLookAt(point.x, point.y, point.z, 55.0F, 35.0F);
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
        if (tactics.decision().intent() != CombatIntentPolicy.Intent.INVESTIGATE || canSeeTarget
                || !investigation.canInvestigate(target, canSeeTarget, mayEngage, gunController.captureReadiness(), activeCommand)) {
            investigation.cancel();
        }

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
        if (selected == CombatIntentPolicy.Intent.INVESTIGATE && !tacticalNavigation.needsHazardEscape()
                && investigation.canInvestigate(target, canSeeTarget, mayEngage, gunController.captureReadiness(), activeCommand)) {
            tacticalNavigation.cancel(); commandMovement.invalidateCombatPath();
            transitionMovementState(GuardCombatMovement.State.REPOSITION, gameTime);
            investigation.tick(tactics, activeCommand, () -> commandMovement.takePathSlot(gameTime),
                    config.movement.repositionSpeed.get());
            return;
        }
        investigation.cancel();
        boolean defensive = selected == CombatIntentPolicy.Intent.WITHDRAW
                || selected == CombatIntentPolicy.Intent.CREATE_DISTANCE
                || selected == CombatIntentPolicy.Intent.REPOSITION;
        defensive = defensive || tacticalNavigation.needsHazardEscape();
        if (defensive || (tacticalNavigation.isMoving() && selected != CombatIntentPolicy.Intent.FINISH_COMBAT)) {
            transitionMovementState(selected == CombatIntentPolicy.Intent.REPOSITION
                    ? GuardCombatMovement.State.REPOSITION : GuardCombatMovement.State.RETREAT, gameTime);
            tacticalNavigation.tick(tactics, activeCommand, gunController.getPreferredApproachDistance(),
                    () -> commandMovement.takePathSlot(gameTime), selected == CombatIntentPolicy.Intent.REPOSITION
                            ? config.movement.repositionSpeed.get() : config.movement.retreatSpeed.get(),
                    selected == CombatIntentPolicy.Intent.REPOSITION, defensive, firingLaneRequest());
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
            case WITHDRAW, HOLD, INVESTIGATE, FINISH_COMBAT -> GuardCombatMovement.State.HOLD;
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

    private TacticalFiringLane.Request firingLaneRequest() {
        if (!canSeeTarget || target == null) return null;
        var shot = gunController.lastShotSafety();
        double blast = shot != null && shot.envelope() != null ? shot.envelope().ballistics().blastReach() : 0;
        return new TacticalFiringLane.Request(target,
                gunController.getMinimumFiringRange(), gunController.getPreferredApproachDistance(),
                gunController.getMaximumFiringRange(), config.safety.baseClearance.get(), blast);
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
        var point = gunController.intentionalAimPoint(observedTarget);
        double deltaX = point.x - guard.getX();
        double deltaY = point.y - guard.getEyeY();
        double deltaZ = point.z - guard.getZ();
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
        GuardShotSafety.Result safety = gunController.currentShotSafety(observedTarget);
        float muzzleSafetyPadding = safety.envelope() == null ? Float.NaN : (float)safety.envelope().clearance();
        float projectileSafetyPadding = Float.NaN;
        float blastSafetyPadding = safety.envelope() == null ? Float.NaN
                : (float)safety.envelope().ballistics().blastReach();
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
            GuardShotSafety.Result safety,
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
        if (!safety.clear() && safety.status() != GuardShotSafety.Status.NOT_EVALUATED) {
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
        // Geometry is checked once at the native per-round hook, using its captured ballistics.
        return hasClearScheduledBurstShot(target);
    }

    private boolean hasClearScheduledBurstShot(LivingEntity expectedTarget) {
        if (expectedTarget == null || !config.enabled.get()
                || tacticalNavigation.isMoving() || investigation.isMoving()
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

        return guard.hasLineOfSight(expectedTarget);
    }

    private void recordShotSafety(boolean safeShot, long gameTime) {
        friendlyBlockedUntilGameTick = safeShot
                ? Long.MIN_VALUE
                : gameTime + config.movement.friendlyRepositionTicks.get();
    }

    private void invalidateSafetyCache() {
        recordedShotSafety = null;
        safetyTarget = null;
        safetyCheckGameTime = Long.MIN_VALUE;
        cachedSafeShot = false;
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
            GuardShotSafety.Result safety,
            EngagementMode engagementMode,
            GuardRulesOfEngagement.EngagementReason engagementReason,
            NativeGunController.Diagnostics nativeState) {
    }
}
