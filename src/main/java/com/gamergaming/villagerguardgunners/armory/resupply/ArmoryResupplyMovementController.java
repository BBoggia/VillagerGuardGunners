package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Bounded navigation for one already-reserved Armory request.
 *
 * <p>The controller never searches for an Armory, loads a chunk, transfers an
 * item, or changes permanent Guard Villagers orders. The Step 6 runtime goal
 * owns the MOVE flag while this controller is active and invokes
 * {@link ArmoryAmmunitionWithdrawal} after
 * {@link MovementStatus#WITHDRAWAL_READY}.</p>
 */
public final class ArmoryResupplyMovementController {
    private static final List<ApproachOffset> HORIZONTAL_APPROACH_OFFSETS = List.of(
            new ApproachOffset(0, -1),
            new ApproachOffset(1, 0),
            new ApproachOffset(0, 1),
            new ApproachOffset(-1, 0),
            new ApproachOffset(1, -1),
            new ApproachOffset(1, 1),
            new ApproachOffset(-1, 1),
            new ApproachOffset(-1, -1));
    private static final List<Integer> APPROACH_VERTICAL_OFFSETS = List.of(0, -1, 1);

    private final Guard guard;
    private final PathNavigation navigation;
    private final ArmoryReservationBook reservationBook;
    private final double movementSpeed;

    @Nullable
    private ArmoryAmmoReservation reservation;
    @Nullable
    private ArmoryResupplyRequest.RequestKey requestKey;
    private List<BlockPos> approachPositions = List.of();
    @Nullable
    private BlockPos ownedNavigationTarget;
    private net.minecraft.world.level.pathfinder.Path ownedNavigationPath;
    @Nullable
    private BlockPos pausedNavigationTarget;
    private long nextEmergencyValidation;
    private int pathAttempts;
    private int nativePathResumes;
    private long nextPathAttemptGameTick = Long.MIN_VALUE;
    private long nextProgressCheckGameTick = Long.MIN_VALUE;
    private long nextTripRevalidationGameTick = Long.MIN_VALUE;
    private double progressAnchorX;
    private double progressAnchorY;
    private double progressAnchorZ;

    public ArmoryResupplyMovementController(
            Guard guard,
            ArmoryReservationBook reservationBook) {
        this(
                guard,
                reservationBook,
                ArmoryResupplyLimits.DEFAULT_RESUPPLY_MOVEMENT_SPEED);
    }

    public ArmoryResupplyMovementController(
            Guard guard,
            ArmoryReservationBook reservationBook,
            double movementSpeed) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.navigation = guard.getNavigation();
        this.reservationBook = Objects.requireNonNull(reservationBook, "reservationBook");
        if (!Double.isFinite(movementSpeed)
                || movementSpeed < ArmoryResupplyLimits.MIN_RESUPPLY_MOVEMENT_SPEED
                || movementSpeed > ArmoryResupplyLimits.HARD_MAX_RESUPPLY_MOVEMENT_SPEED) {
            throw new IllegalArgumentException("movementSpeed is outside the hard bounds");
        }
        this.movementSpeed = movementSpeed;
    }

    /**
     * Binds to a reservation but intentionally creates no path. Rebinding
     * cancels and releases the previous session before inspecting the new key.
     */
    public MovementResult begin(
            ArmoryResupplyRequest.RequestKey requestedKey,
            boolean featureEnabled) {
        if (requestedKey != null && requestedKey.equals(requestKey)) {
            return repeatedBegin(featureEnabled);
        }
        if (requestKey != null) {
            cancel();
        }
        if (requestedKey == null || !(guard.level() instanceof ServerLevel level)) {
            return result(MovementStatus.INVALID_INPUT, null);
        }

        long gameTick = level.getGameTime();
        ArmoryAmmoReservation found = reservationBook
                .find(requestedKey, gameTick)
                .orElse(null);
        if (found == null) {
            return result(MovementStatus.RESERVATION_LOST, null);
        }
        this.reservation = found;
        this.requestKey = requestedKey;

        MovementResult validation = validateSession(featureEnabled, level, gameTick);
        if (validation != null) {
            if (validation.status() == MovementStatus.WAITING_FOR_NATIVE_WEAPON) {
                prepareApproaches(found, gameTick);
                return validation;
            }
            return terminate(validation.status(), validation.gateStatus(), true);
        }
        prepareApproaches(found, gameTick);
        MovementResult tripValidation = validateTripState(featureEnabled, level, gameTick);
        if (tripValidation != null) {
            return terminate(
                    tripValidation.status(),
                    tripValidation.gateStatus(),
                    true);
        }
        BlockPos armoryPosition = found.target().position();
        if (withinWithdrawalDistance(guard, armoryPosition)) {
            return result(MovementStatus.WITHDRAWAL_READY, null);
        }
        return result(MovementStatus.SESSION_STARTED, null);
    }

    /**
     * Advances one active session. It creates at most one path on a due tick,
     * samples progress only every bounded interval, and rechecks precedence,
     * reservation ownership, dimension, squad, range, load state, and lock.
     */
    public MovementResult tick(boolean featureEnabled) {
        if (requestKey == null || reservation == null) {
            return result(MovementStatus.IDLE, null);
        }
        if (!(guard.level() instanceof ServerLevel level)) {
            return terminate(MovementStatus.GUARD_UNAVAILABLE, null, true);
        }
        long gameTick = level.getGameTime();
        ArmoryAmmoReservation liveReservation = reservationBook
                .find(requestKey, gameTick)
                .orElse(null);
        if (!reservation.equals(liveReservation)) {
            return terminate(MovementStatus.RESERVATION_LOST, null, false);
        }
        if (ownedNavigationTarget != null
                && (!ownedNavigationTarget.equals(navigation.getTargetPos()) || navigation.getPath() != ownedNavigationPath)) {
            return terminate(MovementStatus.NAVIGATION_PREEMPTED, null, true);
        }

        MovementResult validation = validateSession(featureEnabled, level, gameTick);
        if (validation != null) {
            if (validation.status() == MovementStatus.WAITING_FOR_NATIVE_WEAPON) {
                pauseOwnedNavigation();
                nextPathAttemptGameTick = gameTick;
                return result(validation.status(), validation.gateStatus());
            }
            return terminate(validation.status(), validation.gateStatus(), true);
        }

        MovementResult tripValidation = validateTripState(featureEnabled, level, gameTick);
        if (tripValidation != null) {
            return terminate(
                    tripValidation.status(),
                    tripValidation.gateStatus(),
                    true);
        }

        BlockPos armoryPosition = reservation.target().position();
        if (withinWithdrawalDistance(guard, armoryPosition)) {
            stopOwnedNavigation();
            return result(MovementStatus.WITHDRAWAL_READY, null);
        }

        if (ownedNavigationTarget != null) {
            var emergency = com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess.getController(guard);
            if (emergency != null && emergency.isEmergency() && gameTick >= nextEmergencyValidation) {
                nextEmergencyValidation = gameTick + 10;
                if (!emergency.safeEmergencyRoute(navigation.getPath())) {
                    stopOwnedNavigation();
                    return terminate(MovementStatus.LOGISTICS_PREEMPTED, ArmoryResupplyGate.GateStatus.STATE_UNAVAILABLE, true);
                }
            }
            if (!navigation.isDone()) {
                if (gameTick >= nextProgressCheckGameTick) {
                    if (!madeProgress(
                            progressAnchorX,
                            progressAnchorY,
                            progressAnchorZ,
                            guard.getX(),
                            guard.getY(),
                            guard.getZ(),
                            ArmoryResupplyLimits.MIN_PROGRESS_DISTANCE_SQUARED)) {
                        stopOwnedNavigation();
                        return pathAttemptFailed(gameTick);
                    }
                    captureProgressAnchor(gameTick);
                }
                return result(MovementStatus.MOVING, null);
            }
            clearNavigationOwnership();
            return pathAttemptFailed(gameTick);
        }

        if (pausedNavigationTarget != null) {
            if (!navigation.isDone()) {
                return terminate(MovementStatus.NAVIGATION_PREEMPTED, null, true);
            }
            if (nativePathResumes >= ArmoryResupplyLimits.HARD_MAX_NATIVE_PATH_RESUMES) {
                return terminate(MovementStatus.NATIVE_RESUME_LIMIT_REACHED, null, true);
            }
            BlockPos resumeTarget = pausedNavigationTarget;
            pausedNavigationTarget = null;
            nativePathResumes++;
            if (!level.hasChunkAt(resumeTarget)) {
                return pathAttemptFailed(gameTick);
            }
            if (!startNavigation(resumeTarget, gameTick)) {
                return pathAttemptFailed(gameTick);
            }
            return result(MovementStatus.MOVING, null);
        }

        if (gameTick < nextPathAttemptGameTick) {
            return result(MovementStatus.WAITING_TO_RETRY, null);
        }
        if (pathAttempts >= ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS) {
            return terminate(MovementStatus.PATH_ATTEMPTS_EXHAUSTED, null, true);
        }

        BlockPos approach = approachPositions.get(
                pathAttempts % approachPositions.size());
        pathAttempts++;
        if (!level.hasChunkAt(approach)) {
            return pathAttemptFailed(gameTick);
        }

        if (!navigation.isDone()) {
            return terminate(MovementStatus.NAVIGATION_PREEMPTED, null, true);
        }
        if (!startNavigation(approach, gameTick)) {
            return pathAttemptFailed(gameTick);
        }
        return result(MovementStatus.MOVING, null);
    }

    /** Releases the reservation and only a navigation path still owned here. */
    public boolean cancel() {
        boolean released = requestKey != null && reservationBook.release(requestKey);
        stopOwnedNavigation();
        clearSession();
        return released;
    }

    /**
     * Clears controller bookkeeping after Step 3 withdrawal. The defensive
     * release is a no-op after a successful claim and prevents a leaked claim
     * if the orchestrator invokes completion after a failed withdrawal.
     */
    public void finishAfterWithdrawal() {
        cancel();
    }

    public boolean active() {
        return requestKey != null;
    }

    /** Repeated starts for the same key are observational and never self-cancel. */
    private MovementResult repeatedBegin(boolean featureEnabled) {
        if (!(guard.level() instanceof ServerLevel level)) {
            return terminate(MovementStatus.GUARD_UNAVAILABLE, null, true);
        }
        long gameTick = level.getGameTime();
        ArmoryAmmoReservation liveReservation = reservationBook
                .find(Objects.requireNonNull(requestKey), gameTick)
                .orElse(null);
        if (!Objects.equals(reservation, liveReservation)) {
            return terminate(MovementStatus.RESERVATION_LOST, null, false);
        }
        if (ownedNavigationTarget != null
                && (!ownedNavigationTarget.equals(navigation.getTargetPos()) || navigation.getPath() != ownedNavigationPath)) {
            return terminate(MovementStatus.NAVIGATION_PREEMPTED, null, true);
        }
        MovementResult validation = validateSession(featureEnabled, level, gameTick);
        if (validation != null) {
            if (validation.status() == MovementStatus.WAITING_FOR_NATIVE_WEAPON) {
                pauseOwnedNavigation();
                nextPathAttemptGameTick = gameTick;
                return result(validation.status(), validation.gateStatus());
            }
            return terminate(validation.status(), validation.gateStatus(), true);
        }
        MovementResult tripValidation = validateTripState(featureEnabled, level, gameTick);
        if (tripValidation != null) {
            return terminate(
                    tripValidation.status(),
                    tripValidation.gateStatus(),
                    true);
        }
        if (withinWithdrawalDistance(guard, Objects.requireNonNull(reservation).target().position())) {
            stopOwnedNavigation();
            return result(MovementStatus.WITHDRAWAL_READY, null);
        }
        if (ownedNavigationTarget != null && !navigation.isDone()) {
            return result(MovementStatus.MOVING, null);
        }
        if (gameTick < nextPathAttemptGameTick) {
            return result(MovementStatus.WAITING_TO_RETRY, null);
        }
        return result(MovementStatus.SESSION_STARTED, null);
    }

    private @Nullable MovementResult validateSession(
            boolean featureEnabled,
            ServerLevel level,
            long gameTick) {
        ArmoryAmmoReservation current = Objects.requireNonNull(reservation);
        ArmoryResupplyRequest request = current.request();
        BlockPos armoryPosition = current.target().position();
        if (!request.guardId().equals(guard.getUUID())
                || !guard.isAlive()
                || guard.isRemoved()) {
            return result(MovementStatus.GUARD_UNAVAILABLE, null);
        }
        if (current.isExpired(gameTick) || request.isExpired(gameTick)) {
            return result(MovementStatus.RESERVATION_LOST, null);
        }
        if (!request.dimension().equals(level.dimension().location())
                || !current.target().dimension().equals(level.dimension().location())) {
            return result(MovementStatus.DIMENSION_CHANGED, null);
        }
        try {
            if (!Objects.equals(request.squadId(), GuardSquadAccess.getSquadId(guard))) {
                return result(MovementStatus.SQUAD_CHANGED, null);
            }
        } catch (RuntimeException exception) {
            return result(
                    MovementStatus.LOGISTICS_PREEMPTED,
                    ArmoryResupplyGate.GateStatus.STATE_UNAVAILABLE);
        }
        if (!withinTravelDistance(request.origin(), armoryPosition)
                || !withinTravelDistance(guard, armoryPosition)) {
            return result(MovementStatus.TOO_FAR_AWAY, null);
        }

        ArmoryResupplyGate.GateStatus gate =
                ArmoryResupplyLiveGate.evaluate(featureEnabled, guard);
        if (!gate.ready()
                && gate != ArmoryResupplyGate.GateStatus.NATIVE_WEAPON_BUSY) {
            return result(MovementStatus.LOGISTICS_PREEMPTED, gate);
        }
        if (!level.hasChunkAt(armoryPosition)) {
            return result(MovementStatus.ARMORY_UNAVAILABLE, null);
        }
        if (!(level.getBlockEntity(armoryPosition) instanceof ArmoryBlockEntity armory)
                || armory.isRemoved()
                || armory.getLevel() != level) {
            return result(MovementStatus.ARMORY_UNAVAILABLE, null);
        }
        if (!armory.maySupply(request.squadId())) {
            return result(MovementStatus.ACCESS_DENIED, null);
        }
        if (gate == ArmoryResupplyGate.GateStatus.NATIVE_WEAPON_BUSY) {
            return result(MovementStatus.WAITING_FOR_NATIVE_WEAPON, gate);
        }
        return null;
    }

    /**
     * Revalidates the current weapon, need, destination, and exact source on a
     * bounded cadence. Final withdrawal repeats the same shared preflight.
     */
    private @Nullable MovementResult validateTripState(
            boolean featureEnabled,
            ServerLevel level,
            long gameTick) {
        if (gameTick < nextTripRevalidationGameTick) {
            return null;
        }
        nextTripRevalidationGameTick = ArmoryResupplyLimits.retryAt(
                gameTick,
                ArmoryResupplyLimits.DEFAULT_TRIP_REVALIDATION_TICKS);
        ArmoryAmmoReservation current = Objects.requireNonNull(reservation);
        if (!(level.getBlockEntity(current.target().position())
                instanceof ArmoryBlockEntity armory)) {
            return result(MovementStatus.ARMORY_UNAVAILABLE, null);
        }
        ArmoryAmmunitionWithdrawal.LiveStatus status =
                ArmoryAmmunitionWithdrawal.validateForTravel(
                        current,
                        armory,
                        guard,
                        featureEnabled);
        return switch (status) {
            case READY -> null;
            case GUARD_UNAVAILABLE -> result(MovementStatus.GUARD_UNAVAILABLE, null);
            case DIMENSION_CHANGED -> result(MovementStatus.DIMENSION_CHANGED, null);
            case ARMORY_UNAVAILABLE, INVALID_INPUT ->
                    result(MovementStatus.ARMORY_UNAVAILABLE, null);
            case TOO_FAR_AWAY -> result(MovementStatus.TOO_FAR_AWAY, null);
            case SQUAD_CHANGED -> result(MovementStatus.SQUAD_CHANGED, null);
            case ACCESS_DENIED -> result(MovementStatus.ACCESS_DENIED, null);
            case GUARD_INVENTORY_UNAVAILABLE ->
                    result(MovementStatus.GUARD_INVENTORY_UNAVAILABLE, null);
            case WEAPON_CHANGED -> result(MovementStatus.WEAPON_CHANGED, null);
            case RESERVE_UNAVAILABLE -> result(MovementStatus.RESERVE_UNAVAILABLE, null);
            case NEED_SATISFIED -> result(MovementStatus.NEED_SATISFIED, null);
            case SOURCE_CHANGED, INCOMPATIBLE_AMMUNITION ->
                    result(MovementStatus.SOURCE_CHANGED, null);
            case STATE_UNAVAILABLE, LOGISTICS_PREEMPTED -> result(
                    MovementStatus.LOGISTICS_PREEMPTED,
                    ArmoryResupplyGate.GateStatus.STATE_UNAVAILABLE);
        };
    }

    private void prepareApproaches(
            ArmoryAmmoReservation current,
            long gameTick) {
        this.approachPositions = approachPositions(
                current.target().position(),
                guard.getX(),
                guard.getY(),
                guard.getZ());
        this.nextPathAttemptGameTick = gameTick;
        this.nextTripRevalidationGameTick = gameTick;
        captureProgressAnchor(gameTick);
    }

    private boolean startNavigation(BlockPos approach, long gameTick) {
        boolean started;
        try {
            var emergency = com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess.getController(guard);
            if (emergency != null && emergency.isEmergency()) {
                var planner = new com.gamergaming.villagerguardgunners.ai.tactics.TacticalPositionPlanner(guard);
                if (!planner.pathRegionLoaded()) return false;
                var path = navigation.createPath(approach, 0,
                        com.gamergaming.villagerguardgunners.ai.tactics.TacticalPositionPlanner.PATH_RANGE);
                if (path == null || path.getEndNode() == null || path.getEndNode().asBlockPos().distSqr(approach) > 1
                        || !emergency.safeEmergencyRoute(path)) return false;
                started = navigation.moveTo(path, movementSpeed);
            } else {
                started = navigation.moveTo(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D, movementSpeed);
            }
        } catch (RuntimeException exception) {
            stopNavigationAt(approach);
            return false;
        }
        BlockPos actualTarget = navigation.getTargetPos();
        if (!started || actualTarget == null || !actualTarget.equals(approach)) {
            stopNavigationAt(approach);
            return false;
        }
        ownedNavigationTarget = actualTarget.immutable();
        ownedNavigationPath = navigation.getPath();
        var emergency = com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess.getController(guard);
        if (ownedNavigationPath == null || (emergency != null && emergency.isEmergency()
                && (ownedNavigationPath.getEndNode() == null
                    || ownedNavigationPath.getEndNode().asBlockPos().distSqr(approach) > 1
                    || !emergency.safeEmergencyRoute(ownedNavigationPath)))) {
            stopOwnedNavigation(); return false;
        }
        nextEmergencyValidation = gameTick + 10;
        captureProgressAnchor(gameTick);
        return true;
    }

    private MovementResult pathAttemptFailed(long gameTick) {
        if (pathAttempts >= ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS) {
            return terminate(MovementStatus.PATH_ATTEMPTS_EXHAUSTED, null, true);
        }
        int retryDelay = ArmoryResupplyLimits.movementPathRetryDelay(pathAttempts);
        nextPathAttemptGameTick = ArmoryResupplyLimits.retryAt(gameTick, retryDelay);
        return result(MovementStatus.WAITING_TO_RETRY, null);
    }

    private MovementResult terminate(
            MovementStatus status,
            @Nullable ArmoryResupplyGate.GateStatus gateStatus,
            boolean releaseReservation) {
        if (releaseReservation && requestKey != null) {
            reservationBook.release(requestKey);
        }
        stopOwnedNavigation();
        MovementResult result = result(status, gateStatus);
        clearSession();
        return result;
    }

    private MovementResult result(
            MovementStatus status,
            @Nullable ArmoryResupplyGate.GateStatus gateStatus) {
        return new MovementResult(
                status,
                gateStatus,
                pathAttempts,
                nativePathResumes,
                nextPathAttemptGameTick,
                ownedNavigationTarget);
    }

    private void captureProgressAnchor(long gameTick) {
        progressAnchorX = guard.getX();
        progressAnchorY = guard.getY();
        progressAnchorZ = guard.getZ();
        nextProgressCheckGameTick = ArmoryResupplyLimits.retryAt(
                gameTick,
                ArmoryResupplyLimits.DEFAULT_PROGRESS_CHECK_TICKS);
    }

    public boolean ownsPath() { return ownedNavigationPath != null && navigation.getPath() == ownedNavigationPath; }
    public void invalidateForWorldChange() {
        stopOwnedNavigation();
        nextPathAttemptGameTick = Math.max(nextPathAttemptGameTick, guard.level().getGameTime() + 20);
    }

    private void stopOwnedNavigation() {
        if (ownedNavigationTarget != null
                && ownedNavigationTarget.equals(navigation.getTargetPos()) && navigation.getPath() == ownedNavigationPath) {
            navigation.stop();
        }
        clearNavigationOwnership();
        pausedNavigationTarget = null;
    }

    private void pauseOwnedNavigation() {
        if (ownedNavigationTarget == null) {
            return;
        }
        if (ownedNavigationTarget.equals(navigation.getTargetPos()) && navigation.getPath() == ownedNavigationPath) {
            pausedNavigationTarget = ownedNavigationTarget;
            navigation.stop();
        }
        clearNavigationOwnership();
    }

    private void stopNavigationAt(BlockPos target) {
        if (target.equals(navigation.getTargetPos())) {
            navigation.stop();
        }
    }

    private void clearNavigationOwnership() {
        ownedNavigationTarget = null;
        ownedNavigationPath = null;
        nextProgressCheckGameTick = Long.MIN_VALUE;
    }

    private void clearSession() {
        reservation = null;
        requestKey = null;
        approachPositions = List.of();
        clearNavigationOwnership();
        pausedNavigationTarget = null;
        pathAttempts = 0;
        nativePathResumes = 0;
        nextPathAttemptGameTick = Long.MIN_VALUE;
        nextTripRevalidationGameTick = Long.MIN_VALUE;
        progressAnchorX = 0.0D;
        progressAnchorY = 0.0D;
        progressAnchorZ = 0.0D;
    }

    static List<BlockPos> approachPositions(
            BlockPos armoryPosition,
            double guardX,
            double guardY,
            double guardZ) {
        Objects.requireNonNull(armoryPosition, "armoryPosition");
        if (!Double.isFinite(guardX)
                || !Double.isFinite(guardY)
                || !Double.isFinite(guardZ)) {
            throw new IllegalArgumentException("guard position must be finite");
        }
        List<BlockPos> positions = new ArrayList<>(HORIZONTAL_APPROACH_OFFSETS.size());
        for (ApproachOffset horizontal : HORIZONTAL_APPROACH_OFFSETS) {
            BlockPos nearestHeight = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (int verticalOffset : APPROACH_VERTICAL_OFFSETS) {
                BlockPos candidate = armoryPosition.offset(
                                horizontal.x(),
                                verticalOffset,
                                horizontal.z())
                        .immutable();
                double candidateDistance = distanceSquared(
                        guardX,
                        guardY,
                        guardZ,
                        candidate.getX() + 0.5D,
                        candidate.getY(),
                        candidate.getZ() + 0.5D);
                if (candidateDistance < nearestDistance) {
                    nearestHeight = candidate;
                    nearestDistance = candidateDistance;
                }
            }
            positions.add(Objects.requireNonNull(nearestHeight));
        }
        positions.sort(Comparator
                .comparingDouble((BlockPos position) -> distanceSquared(
                        guardX,
                        guardY,
                        guardZ,
                        position.getX() + 0.5D,
                        position.getY(),
                        position.getZ() + 0.5D)));
        return List.copyOf(positions);
    }

    static boolean withinWithdrawalDistance(Guard guard, BlockPos armoryPosition) {
        return distanceSquared(
                        guard.getX(),
                        guard.getY(),
                        guard.getZ(),
                        armoryPosition.getX() + 0.5D,
                        armoryPosition.getY() + 0.5D,
                        armoryPosition.getZ() + 0.5D)
                <= ArmoryResupplyLimits.HARD_MAX_WITHDRAWAL_DISTANCE_SQUARED;
    }

    static boolean withinTravelDistance(BlockPos origin, BlockPos armoryPosition) {
        return distanceSquared(
                        origin.getX() + 0.5D,
                        origin.getY() + 0.5D,
                        origin.getZ() + 0.5D,
                        armoryPosition.getX() + 0.5D,
                        armoryPosition.getY() + 0.5D,
                        armoryPosition.getZ() + 0.5D)
                <= ArmoryResupplyLimits.HARD_MAX_TRAVEL_DISTANCE_SQUARED;
    }

    static boolean withinTravelDistance(Guard guard, BlockPos armoryPosition) {
        return distanceSquared(
                        guard.getX(),
                        guard.getY(),
                        guard.getZ(),
                        armoryPosition.getX() + 0.5D,
                        armoryPosition.getY() + 0.5D,
                        armoryPosition.getZ() + 0.5D)
                <= ArmoryResupplyLimits.HARD_MAX_TRAVEL_DISTANCE_SQUARED;
    }

    static boolean madeProgress(
            double previousX,
            double previousY,
            double previousZ,
            double currentX,
            double currentY,
            double currentZ,
            double minimumDistanceSquared) {
        if (!Double.isFinite(minimumDistanceSquared) || minimumDistanceSquared < 0.0D) {
            return false;
        }
        double displacementSquared = distanceSquared(
                        previousX,
                        previousY,
                        previousZ,
                        currentX,
                        currentY,
                        currentZ);
        return Double.isFinite(displacementSquared)
                && displacementSquared >= minimumDistanceSquared;
    }

    private static double distanceSquared(
            double firstX,
            double firstY,
            double firstZ,
            double secondX,
            double secondY,
            double secondZ) {
        if (!Double.isFinite(firstX)
                || !Double.isFinite(firstY)
                || !Double.isFinite(firstZ)
                || !Double.isFinite(secondX)
                || !Double.isFinite(secondY)
                || !Double.isFinite(secondZ)) {
            return Double.POSITIVE_INFINITY;
        }
        double x = firstX - secondX;
        double y = firstY - secondY;
        double z = firstZ - secondZ;
        return x * x + y * y + z * z;
    }

    public enum MovementStatus {
        IDLE,
        SESSION_STARTED,
        MOVING,
        WAITING_TO_RETRY,
        WAITING_FOR_NATIVE_WEAPON,
        WITHDRAWAL_READY,
        INVALID_INPUT,
        RESERVATION_LOST,
        GUARD_UNAVAILABLE,
        DIMENSION_CHANGED,
        SQUAD_CHANGED,
        ARMORY_UNAVAILABLE,
        ACCESS_DENIED,
        TOO_FAR_AWAY,
        LOGISTICS_PREEMPTED,
        NAVIGATION_PREEMPTED,
        PATH_ATTEMPTS_EXHAUSTED,
        NATIVE_RESUME_LIMIT_REACHED,
        WEAPON_CHANGED,
        GUARD_INVENTORY_UNAVAILABLE,
        RESERVE_UNAVAILABLE,
        NEED_SATISFIED,
        SOURCE_CHANGED
    }

    public record MovementResult(
            MovementStatus status,
            @Nullable ArmoryResupplyGate.GateStatus gateStatus,
            int pathAttempts,
            int nativePathResumes,
            long nextPathAttemptGameTick,
            @Nullable BlockPos navigationTarget) {
        public MovementResult {
            Objects.requireNonNull(status, "status");
            navigationTarget = navigationTarget == null
                    ? null
                    : navigationTarget.immutable();
            if (pathAttempts < 0
                    || pathAttempts > ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS
                    || nativePathResumes < 0
                    || nativePathResumes > ArmoryResupplyLimits.HARD_MAX_NATIVE_PATH_RESUMES
                    || ((status == MovementStatus.LOGISTICS_PREEMPTED
                                    || status == MovementStatus.WAITING_FOR_NATIVE_WEAPON)
                            != (gateStatus != null))) {
                throw new IllegalArgumentException("Movement result fields contradict status");
            }
        }

        public boolean withdrawalReady() {
            return status == MovementStatus.WITHDRAWAL_READY;
        }
    }

    private record ApproachOffset(int x, int z) {
        private ApproachOffset {
            if ((x == 0 && z == 0)
                    || Math.abs(x) > 1
                    || Math.abs(z) > 1) {
                throw new IllegalArgumentException("Approach offset must be adjacent");
            }
        }
    }
}
