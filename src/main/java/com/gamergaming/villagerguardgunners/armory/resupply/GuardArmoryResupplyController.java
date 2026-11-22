package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Per-guard Step 6 orchestrator joining need, loaded-index selection,
 * reservation, bounded movement, and transactional withdrawal.
 *
 * <p>The controller is owned by one low-priority MOVE goal. Idle calls perform
 * only constant-time scheduling until the guard's staggered assessment tick.
 * A due request inspects no more than the configured Armory cap and each
 * Armory's fixed inventory. No world, entity, block, or unloaded-chunk search
 * is available from this class.</p>
 */
public final class GuardArmoryResupplyController {
    private boolean emergency;
    private long emergencyPermitTick = Long.MIN_VALUE;
    private java.util.List<com.gamergaming.villagerguardgunners.ai.tactics.ThreatMemory.Observation> emergencyThreats = java.util.List.of();
    private final Guard guard;
    private final VillagerGuardGunnersConfig config;

    private ArmoryResupplyState state = ArmoryResupplyState.IDLE;
    private RuntimeOutcome lastOutcome = RuntimeOutcome.NOT_ASSESSED;
    @Nullable
    private ArmoryResupplyGate.GateStatus lastGateStatus;
    @Nullable
    private ArmoryResupplyNeed.Assessment lastNeed;
    @Nullable
    private ArmoryResupplyRequest request;
    @Nullable
    private ArmoryResupplyMovementController movement;
    @Nullable
    private ArmoryResupplySettings activeSettings;
    @Nullable
    private ArmoryReservationTarget selectedTarget;
    private long generation;
    private long nextActionGameTick = Long.MIN_VALUE;
    private int candidateInspections;
    private int pathAttempts;
    private int consecutiveUnreachable;
    private long lastTransferredUnits;
    private long assessmentCount;
    private long needAssessmentCount;
    private long armoryQueryCount;
    private long candidateRecordCount;
    private long sourceSlotInspectionCount;
    private long reservationAttemptCount;
    private long pathConstructionAttemptCount;
    private int accountedTripPathAttempts;
    private int maximumPathAttemptsPerTrip;
    private long successfulTransferCount;
    private long transferredUnitCount;
    private long noSupplyOutcomeCount;
    private long unreachableOutcomeCount;

    public GuardArmoryResupplyController(Guard guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.config = ModConfigs.GUARD_GUNNERS;
    }

    /** Called only by the owning combat goal after observing threats and closing its trigger. */
    public boolean tickEmergency(boolean safe, java.util.List<com.gamergaming.villagerguardgunners.ai.tactics.ThreatMemory.Observation> threats, Runnable takeMovement) {
        if (!safe) { cancelEmergency(); return false; }
        if (hasActiveRequest() && !emergency) return false;
        emergency = true;
        emergencyPermitTick = guard.level().getGameTime();
        emergencyThreats = java.util.List.copyOf(threats);
        boolean starting = !hasActiveRequest();
        if (starting && !canStart()) { emergency = false; emergencyThreats = java.util.List.of(); return false; }
        if (starting) takeMovement.run();
        tick();
        if (!hasActiveRequest()) { emergency = false; emergencyThreats = java.util.List.of(); }
        return true;
    }

    public boolean emergencyCombatPermitted() {
        return emergency && emergencyPermitTick == guard.level().getGameTime()
                && (guard.getTarget() == null || guard.distanceToSqr(guard.getTarget()) >= 64)
                && completelyDry(guard);
    }
    public boolean isEmergency() { return emergency; }
    public boolean ownsEmergencyPath() { return emergency && movement != null && movement.ownsPath(); }
    public void invalidateEmergencyPath() { if (ownsEmergencyPath()) movement.invalidateForWorldChange(); }
    public void cancelEmergency() {
        if (emergency) { cancelActive(RuntimeOutcome.CANCELLED, true); emergency = false; }
        emergencyPermitTick = Long.MIN_VALUE;
        emergencyThreats = java.util.List.of();
    }
    public boolean safeEmergencyRoute(net.minecraft.world.level.pathfinder.Path path) {
        try {
            if (!emergencyCombatPermitted()) return false;
            var planner = new com.gamergaming.villagerguardgunners.ai.tactics.TacticalPositionPlanner(guard);
            return path != null && path.canReach() && planner.safeRoute(path, path.getNextNodeIndex(), emergencyThreats,
                    com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.bounds(guard), false);
        } catch (RuntimeException ignored) { return false; }
    }
    public static boolean completelyDry(Guard guard) {
        try {
            var loadout = GuardWeaponAmmunition.captureSummary(guard, GuardAmmoAccess.getTaCZInventoryHandler(guard));
            return loadout.state() != com.gamergaming.villagerguardgunners.sidearm.SidearmState.RECOVERY_REQUIRED
                    && dryWeapon(loadout.primary()) && dryWeapon(loadout.sidearm())
                    && (loadout.primary().isReady() || loadout.sidearm().isReady());
        } catch (RuntimeException ignored) { return false; }
    }
    private static boolean dryWeapon(com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary.Snapshot weapon) {
        return weapon != null && (weapon.status() == com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary.Status.NO_GUN
                || (weapon.isReady() && weapon.reserveScanComplete() && !weapon.infiniteReserve()
                    && weapon.reserveSource() == com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary.ReserveSource.PHYSICAL
                    && weapon.displayedLoadedAmmo() == 0 && weapon.reserveUnits() == 0));
    }

    /** Called from Goal#canUse; expensive work occurs only on a due tick. */
    public boolean canStart() {
        if (com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard) || com.gamergaming.villagerguardgunners.medical.MedicRuntime.busy(guard)) return false;
        if (!(guard.level() instanceof ServerLevel level)
                || !guard.isAlive()
                || guard.isRemoved()) {
            return false;
        }
        if (state.hasActiveRequest()) {
            return true;
        }
        long gameTick = level.getGameTime();
        if (nextActionGameTick == Long.MIN_VALUE) {
            nextActionGameTick = nextStaggeredTick(
                    gameTick,
                    config.armoryResupply.checkIntervalTicks.get(),
                    guard.getUUID());
            lastOutcome = RuntimeOutcome.NOT_DUE;
            return false;
        }
        if (gameTick < nextActionGameTick) {
            lastOutcome = RuntimeOutcome.NOT_DUE;
            return false;
        }
        if (state.isBackoff()) {
            state = ArmoryResupplyStateContract.transition(
                    state,
                    ArmoryResupplyEvent.RETRY_READY);
        }
        return assessAndReserve(level, gameTick);
    }

    public boolean shouldContinue() {
        return state == ArmoryResupplyState.TRAVELLING
                || state == ArmoryResupplyState.WITHDRAWING
                || state == ArmoryResupplyState.RESERVED;
    }

    /** Advances at most the already-owned movement or one final withdrawal. */
    public void tick() {
        if (!(guard.level() instanceof ServerLevel level)) {
            cancelActive(RuntimeOutcome.CANCELLED, false);
            return;
        }
        if (state == ArmoryResupplyState.WITHDRAWING) {
            withdraw(level);
            return;
        }
        if (state != ArmoryResupplyState.TRAVELLING || movement == null) {
            return;
        }

        ArmoryResupplyMovementController.MovementResult result =
                movement.tick(featureEnabledNow());
        accountPathAttempts(result.pathAttempts());
        if (result.nextPathAttemptGameTick() != Long.MIN_VALUE) {
            nextActionGameTick = result.nextPathAttemptGameTick();
        }
        switch (result.status()) {
            case MOVING, SESSION_STARTED, WAITING_TO_RETRY ->
                    lastOutcome = RuntimeOutcome.MOVING;
            case WAITING_FOR_NATIVE_WEAPON -> {
                lastGateStatus = result.gateStatus();
                lastOutcome = RuntimeOutcome.WAITING_FOR_NATIVE_WEAPON;
            }
            case WITHDRAWAL_READY -> {
                state = ArmoryResupplyStateContract.transition(
                        state,
                        ArmoryResupplyEvent.WITHDRAWAL_REACHED);
                lastOutcome = RuntimeOutcome.WITHDRAWING;
                withdraw(level);
            }
            case PATH_ATTEMPTS_EXHAUSTED, NATIVE_RESUME_LIMIT_REACHED, TOO_FAR_AWAY ->
                    enterUnreachable(level.getGameTime());
            case ARMORY_UNAVAILABLE, ACCESS_DENIED, RESERVATION_LOST,
                    WEAPON_CHANGED, GUARD_INVENTORY_UNAVAILABLE,
                    RESERVE_UNAVAILABLE, NEED_SATISFIED, SOURCE_CHANGED ->
                    enterNoSupply(level.getGameTime(), RuntimeOutcome.SUPPLY_CHANGED);
            case INVALID_INPUT, GUARD_UNAVAILABLE, DIMENSION_CHANGED, SQUAD_CHANGED,
                    LOGISTICS_PREEMPTED, NAVIGATION_PREEMPTED ->
                    cancelActive(RuntimeOutcome.CANCELLED, true);
            case IDLE -> cancelActive(RuntimeOutcome.CANCELLED, true);
        }
    }

    /** Goal preemption releases only logistics-owned resources. */
    public void stopForHigherPriorityGoal() {
        if (state.hasActiveRequest()) {
            cancelActive(RuntimeOutcome.CANCELLED, true);
        }
    }

    /** Entity/server lifecycle cleanup; leaves no retry state behind. */
    public void discard() {
        cancelEmergency();
        releaseActiveResources();
        state = ArmoryResupplyState.IDLE;
        request = null;
        activeSettings = null;
        selectedTarget = null;
        nextActionGameTick = Long.MIN_VALUE;
        lastOutcome = RuntimeOutcome.CANCELLED;
    }

    public RuntimeSnapshot snapshot() {
        return new RuntimeSnapshot(
                state,
                lastOutcome,
                lastGateStatus,
                lastNeed,
                request,
                selectedTarget,
                candidateInspections,
                pathAttempts,
                diagnosticNextAction(),
                lastTransferredUnits);
    }

    /** Allocation-free precedence probe for lower-priority movement goals. */
    public boolean hasActiveRequest() {
        return state.hasActiveRequest();
    }

    /**
     * Monotonic primitive counters for the opt-in performance evidence suite.
     * Capturing them allocates only when explicitly requested outside a timed
     * sample; normal goal ticks merely update primitive fields on due work.
     */
    public RuntimeMetrics performanceMetrics() {
        return new RuntimeMetrics(
                assessmentCount,
                needAssessmentCount,
                armoryQueryCount,
                candidateRecordCount,
                sourceSlotInspectionCount,
                reservationAttemptCount,
                pathConstructionAttemptCount,
                maximumPathAttemptsPerTrip,
                successfulTransferCount,
                transferredUnitCount,
                noSupplyOutcomeCount,
                unreachableOutcomeCount);
    }

    private boolean assessAndReserve(ServerLevel level, long gameTick) {
        assessmentCount = incrementSaturated(assessmentCount);
        ArmoryResupplySettings settings;
        try {
            settings = config.armoryResupply.snapshot(config.enabled.get());
        } catch (RuntimeException exception) {
            lastOutcome = RuntimeOutcome.STATE_UNAVAILABLE;
            scheduleStaggered(gameTick, config.armoryResupply.checkIntervalTicks.get());
            return false;
        }
        activeSettings = settings;
        candidateInspections = 0;
        pathAttempts = 0;
        accountedTripPathAttempts = 0;
        lastTransferredUnits = 0L;

        lastGateStatus = ArmoryResupplyLiveGate.evaluate(settings.enabled(), guard);
        if (!lastGateStatus.ready()) {
            lastNeed = null;
            lastOutcome = RuntimeOutcome.GATE_BLOCKED;
            scheduleStaggered(gameTick, settings.checkIntervalTicks());
            return false;
        }

        GuardWeaponAmmunition.LoadoutSummary loadout;
        try {
            needAssessmentCount = incrementSaturated(needAssessmentCount);
            loadout = GuardWeaponAmmunition.captureSummary(
                    guard,
                    GuardAmmoAccess.getTaCZInventoryHandler(guard));
            lastNeed = ArmoryResupplyNeed.assess(
                    loadout,
                    settings.lowReserveMagazines(),
                    settings.targetReserveMagazines());
        } catch (RuntimeException exception) {
            lastNeed = null;
            lastOutcome = RuntimeOutcome.STATE_UNAVAILABLE;
            scheduleStaggered(gameTick, settings.checkIntervalTicks());
            return false;
        }
        if (!lastNeed.requestRequired()) {
            lastOutcome = RuntimeOutcome.NO_ACTIONABLE_NEED;
            scheduleStaggered(gameTick, settings.checkIntervalTicks());
            return false;
        }

        final UUID squadId;
        try {
            squadId = GuardSquadAccess.getSquadId(guard);
            request = ArmoryResupplyRequest.create(
                    nextGeneration(),
                    guard.getUUID(),
                    level.dimension().location(),
                    guard.blockPosition(),
                    squadId,
                    lastNeed,
                    gameTick,
                    settings.requestLifetimeTicks());
        } catch (RuntimeException exception) {
            request = null;
            lastOutcome = RuntimeOutcome.STATE_UNAVAILABLE;
            scheduleStaggered(gameTick, settings.checkIntervalTicks());
            return false;
        }
        state = ArmoryResupplyStateContract.transition(
                state,
                ArmoryResupplyEvent.REQUEST_CREATED);

        ArmoryResupplyServerServices.Services services =
                ArmoryResupplyServerServices.forLevel(level);
        armoryQueryCount = incrementSaturated(armoryQueryCount);
        LoadedArmoryIndex.QueryResult query = services.loadedArmories().query(
                level,
                guard.getX(),
                guard.getY(),
                guard.getZ(),
                settings.searchRadius(),
                settings.maximumCandidateArmories());
        candidateInspections = query.visitedRecords();
        candidateRecordCount = addSaturated(
                candidateRecordCount,
                query.visitedRecords());
        ItemStack activeGun = guard.getMainHandItem().copy();
        ArmoryAmmoReservation reserved = reserveFirstCompatible(
                services.reservations(),
                query,
                activeGun,
                gameTick,
                settings.reservationLifetimeTicks());
        if (reserved == null) {
            state = ArmoryResupplyStateContract.transition(
                    state,
                    ArmoryResupplyEvent.NO_SUPPLY);
            clearActiveReferences(false);
            lastOutcome = RuntimeOutcome.NO_SUPPLY;
            noSupplyOutcomeCount = incrementSaturated(noSupplyOutcomeCount);
            nextActionGameTick = ArmoryResupplyLimits.retryAt(
                    gameTick,
                    settings.noSupplyRetryTicks());
            return false;
        }

        selectedTarget = reserved.target();
        state = ArmoryResupplyStateContract.transition(
                state,
                ArmoryResupplyEvent.CANDIDATE_RESERVED);
        movement = new ArmoryResupplyMovementController(
                guard,
                services.reservations(),
                settings.movementSpeed());
        ArmoryResupplyMovementController.MovementResult movementResult =
                movement.begin(Objects.requireNonNull(request).key(), settings.enabled());
        accountPathAttempts(movementResult.pathAttempts());
        if (movementResult.withdrawalReady()) {
            state = ArmoryResupplyStateContract.transition(
                    state,
                    ArmoryResupplyEvent.WITHDRAWAL_REACHED);
            lastOutcome = RuntimeOutcome.WITHDRAWING;
            nextActionGameTick = gameTick;
            return true;
        }
        if (movementResult.status()
                        == ArmoryResupplyMovementController.MovementStatus.SESSION_STARTED
                || movementResult.status()
                        == ArmoryResupplyMovementController.MovementStatus.WAITING_FOR_NATIVE_WEAPON) {
            state = ArmoryResupplyStateContract.transition(
                    state,
                    ArmoryResupplyEvent.TRAVEL_STARTED);
            lastGateStatus = movementResult.gateStatus();
            lastOutcome = movementResult.status()
                            == ArmoryResupplyMovementController.MovementStatus.WAITING_FOR_NATIVE_WEAPON
                    ? RuntimeOutcome.WAITING_FOR_NATIVE_WEAPON
                    : RuntimeOutcome.RESERVED;
            nextActionGameTick = gameTick;
            return true;
        }
        switch (movementResult.status()) {
            case PATH_ATTEMPTS_EXHAUSTED, NATIVE_RESUME_LIMIT_REACHED, TOO_FAR_AWAY ->
                    enterUnreachable(gameTick);
            case ARMORY_UNAVAILABLE, ACCESS_DENIED, RESERVATION_LOST,
                    WEAPON_CHANGED, GUARD_INVENTORY_UNAVAILABLE,
                    RESERVE_UNAVAILABLE, NEED_SATISFIED, SOURCE_CHANGED ->
                    enterNoSupply(gameTick, RuntimeOutcome.SUPPLY_CHANGED);
            case INVALID_INPUT, GUARD_UNAVAILABLE, DIMENSION_CHANGED, SQUAD_CHANGED,
                    LOGISTICS_PREEMPTED, NAVIGATION_PREEMPTED, IDLE ->
                    cancelActive(RuntimeOutcome.CANCELLED, true);
            case MOVING, WAITING_TO_RETRY -> {
                state = ArmoryResupplyStateContract.transition(
                        state,
                        ArmoryResupplyEvent.TRAVEL_STARTED);
                lastOutcome = RuntimeOutcome.MOVING;
            }
            case SESSION_STARTED, WAITING_FOR_NATIVE_WEAPON, WITHDRAWAL_READY ->
                    throw new IllegalStateException("Handled movement start status fell through");
        }
        return state.hasActiveRequest();
    }

    @Nullable
    private ArmoryAmmoReservation reserveFirstCompatible(
            ArmoryReservationBook reservations,
            LoadedArmoryIndex.QueryResult query,
            ItemStack activeGun,
            long gameTick,
            int reservationLifetimeTicks) {
        ArmoryResupplyRequest activeRequest = Objects.requireNonNull(request);
        for (ArmoryBlockEntity armory : query.armories()) {
            if (emergency && (guard.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(armory.getBlockPos())) > 24 * 24
                    || !com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.bounds(guard)
                        .contains(net.minecraft.world.phys.Vec3.atCenterOf(armory.getBlockPos())))) continue;
            int slots;
            try {
                slots = Math.min(
                        armory.getInventory().getSlots(),
                        ArmoryResupplyLimits.HARD_MAX_RESERVATION_SOURCE_SLOTS);
            } catch (RuntimeException exception) {
                continue;
            }
            for (int slot = 0; slot < slots; slot++) {
                sourceSlotInspectionCount = incrementSaturated(sourceSlotInspectionCount);
                ArmorySupplyCandidate.Inspection inspection = ArmorySupplyCandidate.inspect(
                        activeRequest,
                        activeRequest.dimension(),
                        armory,
                        activeGun,
                        slot,
                        gameTick);
                if (!inspection.ready()) {
                    continue;
                }
                reservationAttemptCount = incrementSaturated(reservationAttemptCount);
                ArmoryReservationBook.ReservationResult reservation = reservations.reserve(
                        activeRequest,
                        Objects.requireNonNull(inspection.candidate()),
                        gameTick,
                        reservationLifetimeTicks);
                if (reservation.reserved()) {
                    return reservation.reservation();
                }
                if (reservation.status()
                        == ArmoryReservationBook.ReservationStatus.CAPACITY_REACHED) {
                    return null;
                }
            }
        }
        return null;
    }

    private void withdraw(ServerLevel level) {
        ArmoryResupplyRequest activeRequest = request;
        ArmoryResupplySettings settings = activeSettings;
        ArmoryReservationTarget target = selectedTarget;
        ArmoryResupplyMovementController activeMovement = movement;
        if (activeRequest == null || settings == null || target == null) {
            cancelActive(RuntimeOutcome.STATE_UNAVAILABLE, true);
            return;
        }
        ArmoryBlockEntity armory = null;
        if (level.hasChunkAt(target.position())
                && level.getBlockEntity(target.position()) instanceof ArmoryBlockEntity found) {
            armory = found;
        }
        ArmoryAmmunitionWithdrawal.WithdrawalResult result =
                ArmoryAmmunitionWithdrawal.withdraw(
                        ArmoryResupplyServerServices.forLevel(level).reservations(),
                        activeRequest.key(),
                        armory,
                        guard,
                        featureEnabledNow());
        if (activeMovement != null) {
            activeMovement.finishAfterWithdrawal();
        }
        if (result.transferred()) {
            state = ArmoryResupplyStateContract.transition(
                    state,
                    ArmoryResupplyEvent.TRANSFER_SUCCEEDED);
            lastTransferredUnits = result.transferredUnits();
            successfulTransferCount = incrementSaturated(successfulTransferCount);
            transferredUnitCount = addSaturated(
                    transferredUnitCount,
                    result.transferredUnits());
            lastOutcome = RuntimeOutcome.TRANSFERRED;
            consecutiveUnreachable = 0;
            clearActiveReferences(false);
            nextActionGameTick = ArmoryResupplyLimits.retryAt(
                    level.getGameTime(),
                    settings.successCooldownTicks());
            return;
        }
        switch (result.status()) {
            case ARMORY_UNAVAILABLE, NO_RESERVATION, RESERVATION_EXPIRED,
                    NEED_SATISFIED, SOURCE_CHANGED, INCOMPATIBLE_AMMUNITION,
                    DESTINATION_FULL, TRANSACTION_INVALID, TRANSACTION_ROLLED_BACK,
                    TRANSACTION_ROLLBACK_FAILED ->
                    enterNoSupply(level.getGameTime(), RuntimeOutcome.WITHDRAWAL_FAILED);
            case INVALID_INPUT, GUARD_UNAVAILABLE, DIMENSION_CHANGED, TOO_FAR_AWAY,
                    SQUAD_CHANGED, ACCESS_DENIED, GUARD_INVENTORY_UNAVAILABLE,
                    STATE_UNAVAILABLE, LOGISTICS_PREEMPTED, WEAPON_CHANGED,
                    RESERVE_UNAVAILABLE ->
                    cancelActive(RuntimeOutcome.CANCELLED, true);
            case TRANSFERRED -> throw new IllegalStateException(
                    "Transferred status contradicted its payload");
        }
    }

    private void enterNoSupply(long gameTick, RuntimeOutcome outcome) {
        ArmoryResupplySettings settings = activeSettings;
        if (state == ArmoryResupplyState.RESERVED
                || state == ArmoryResupplyState.TRAVELLING
                || state == ArmoryResupplyState.WITHDRAWING) {
            state = ArmoryResupplyStateContract.transition(
                    state,
                    ArmoryResupplyEvent.NO_SUPPLY);
        }
        releaseActiveResources();
        clearActiveReferences(false);
        lastOutcome = outcome;
        noSupplyOutcomeCount = incrementSaturated(noSupplyOutcomeCount);
        int delay = settings == null
                ? config.armoryResupply.noSupplyRetryTicks.get()
                : settings.noSupplyRetryTicks();
        nextActionGameTick = ArmoryResupplyLimits.retryAt(gameTick, delay);
    }

    private void enterUnreachable(long gameTick) {
        ArmoryResupplySettings settings = activeSettings;
        if (state == ArmoryResupplyState.RESERVED
                || state == ArmoryResupplyState.TRAVELLING) {
            state = ArmoryResupplyStateContract.transition(
                    state,
                    ArmoryResupplyEvent.PATH_FAILED);
        }
        releaseActiveResources();
        clearActiveReferences(false);
        if (consecutiveUnreachable < Integer.MAX_VALUE) {
            consecutiveUnreachable++;
        }
        int baseDelay = settings == null
                ? config.armoryResupply.unreachableRetryTicks.get()
                : settings.unreachableRetryTicks();
        int delay = ArmoryResupplyLimits.unreachableRetryDelay(
                consecutiveUnreachable,
                baseDelay);
        nextActionGameTick = ArmoryResupplyLimits.retryAt(gameTick, delay);
        lastOutcome = RuntimeOutcome.UNREACHABLE;
        unreachableOutcomeCount = incrementSaturated(unreachableOutcomeCount);
    }

    private void cancelActive(RuntimeOutcome outcome, boolean scheduleRetry) {
        if (state.hasActiveRequest()) {
            state = ArmoryResupplyStateContract.transition(
                    state,
                    ArmoryResupplyEvent.CANCELLED);
        }
        releaseActiveResources();
        clearActiveReferences(false);
        lastOutcome = outcome;
        if (scheduleRetry && guard.level() instanceof ServerLevel level) {
            scheduleStaggered(
                    level.getGameTime(),
                    config.armoryResupply.checkIntervalTicks.get());
        }
    }

    private void releaseActiveResources() {
        if (movement != null) {
            movement.cancel();
            return;
        }
        if (request != null && guard.level() instanceof ServerLevel level) {
            ArmoryResupplyServerServices.Services services =
                    ArmoryResupplyServerServices.existingForLevel(level);
            if (services != null) {
                services.reservations().release(request.key());
            }
        }
    }

    private void clearActiveReferences(boolean clearNeed) {
        request = null;
        movement = null;
        activeSettings = null;
        selectedTarget = null;
        if (clearNeed) {
            lastNeed = null;
        }
    }

    private boolean featureEnabledNow() {
        try {
            return config.enabled.get() && config.armoryResupply.enabled.get();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private long nextGeneration() {
        long current = generation;
        if (generation < Long.MAX_VALUE) {
            generation++;
        }
        return current;
    }

    private void scheduleStaggered(long gameTick, int intervalTicks) {
        nextActionGameTick = nextStaggeredTick(
                gameTick,
                intervalTicks,
                guard.getUUID());
    }

    static long nextStaggeredTick(
            long gameTick,
            int intervalTicks,
            UUID guardId) {
        if (gameTick < 0L || intervalTicks <= 0 || guardId == null) {
            throw new IllegalArgumentException("Invalid Armory assessment schedule input");
        }
        int offset = ArmoryResupplyLimits.checkOffset(guardId, intervalTicks);
        int current = (int) Math.floorMod(gameTick, (long) intervalTicks);
        int delay = Math.floorMod(offset - current, intervalTicks);
        if (delay == 0) {
            delay = intervalTicks;
        }
        return gameTick > Long.MAX_VALUE - delay ? Long.MAX_VALUE : gameTick + delay;
    }

    private long diagnosticNextAction() {
        return nextActionGameTick == Long.MIN_VALUE ? -1L : nextActionGameTick;
    }

    private void accountPathAttempts(int currentPathAttempts) {
        if (currentPathAttempts < 0
                || currentPathAttempts > ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS) {
            throw new IllegalArgumentException("Path-attempt evidence exceeded its bounds");
        }
        if (currentPathAttempts < accountedTripPathAttempts) {
            accountedTripPathAttempts = 0;
        }
        pathConstructionAttemptCount = addSaturated(
                pathConstructionAttemptCount,
                currentPathAttempts - accountedTripPathAttempts);
        accountedTripPathAttempts = currentPathAttempts;
        maximumPathAttemptsPerTrip = Math.max(
                maximumPathAttemptsPerTrip,
                currentPathAttempts);
        pathAttempts = currentPathAttempts;
    }

    private static long incrementSaturated(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static long addSaturated(long value, long increment) {
        if (increment <= 0L) {
            return value;
        }
        return value > Long.MAX_VALUE - increment
                ? Long.MAX_VALUE
                : value + increment;
    }

    public enum RuntimeOutcome {
        NOT_ASSESSED,
        NOT_DUE,
        GATE_BLOCKED,
        NO_ACTIONABLE_NEED,
        STATE_UNAVAILABLE,
        NO_SUPPLY,
        RESERVED,
        MOVING,
        WAITING_FOR_NATIVE_WEAPON,
        WITHDRAWING,
        TRANSFERRED,
        SUPPLY_CHANGED,
        WITHDRAWAL_FAILED,
        UNREACHABLE,
        CANCELLED
    }

    public record RuntimeSnapshot(
            ArmoryResupplyState state,
            RuntimeOutcome lastOutcome,
            @Nullable ArmoryResupplyGate.GateStatus lastGateStatus,
            @Nullable ArmoryResupplyNeed.Assessment lastNeed,
            @Nullable ArmoryResupplyRequest request,
            @Nullable ArmoryReservationTarget selectedTarget,
            int candidateInspections,
            int pathAttempts,
            long nextActionGameTick,
            long lastTransferredUnits) {
        public RuntimeSnapshot {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(lastOutcome, "lastOutcome");
            if (candidateInspections < 0
                    || candidateInspections > ArmoryResupplyLimits.HARD_MAX_CANDIDATE_ARMORIES
                    || pathAttempts < 0
                    || pathAttempts > ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS
                    || nextActionGameTick < -1L
                    || lastTransferredUnits < 0L
                    || (state.ownsReservation() != (selectedTarget != null))
                    || (state.hasActiveRequest() != (request != null))) {
                throw new IllegalArgumentException("Armory runtime snapshot is contradictory");
            }
        }
    }

    public record RuntimeMetrics(
            long assessments,
            long needAssessments,
            long armoryQueries,
            long candidateRecords,
            long sourceSlotInspections,
            long reservationAttempts,
            long pathConstructionAttempts,
            int maximumPathAttemptsPerTrip,
            long successfulTransfers,
            long transferredUnits,
            long noSupplyOutcomes,
            long unreachableOutcomes) {
        public RuntimeMetrics {
            if (assessments < 0L
                    || needAssessments < 0L
                    || needAssessments > assessments
                    || armoryQueries < 0L
                    || armoryQueries > needAssessments
                    || candidateRecords < 0L
                    || sourceSlotInspections < 0L
                    || reservationAttempts < 0L
                    || reservationAttempts > sourceSlotInspections
                    || pathConstructionAttempts < 0L
                    || maximumPathAttemptsPerTrip < 0
                    || maximumPathAttemptsPerTrip
                            > ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS
                    || successfulTransfers < 0L
                    || transferredUnits < 0L
                    || noSupplyOutcomes < 0L
                    || unreachableOutcomes < 0L) {
                throw new IllegalArgumentException("Armory runtime metrics are contradictory");
            }
        }
    }
}
