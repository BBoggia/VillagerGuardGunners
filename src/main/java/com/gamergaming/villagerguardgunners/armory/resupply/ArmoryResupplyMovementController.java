package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Bounded navigation for one already-reserved Armory request.
 *
 * <p>The controller never searches for an Armory, loads a chunk, transfers an
 * item, or changes permanent Guard Villagers orders. A future runtime goal is
 * responsible for owning the MOVE flag while this controller is active and
 * for invoking {@link ArmoryAmmunitionWithdrawal} after
 * {@link MovementStatus#WITHDRAWAL_READY}. Until that orchestrator exists this
 * class is deliberately dormant.</p>
 */
public final class ArmoryResupplyMovementController {
    private static final List<Direction> APPROACH_DIRECTIONS = List.of(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST);

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
    private int pathAttempts;
    private long nextPathAttemptGameTick = Long.MIN_VALUE;
    private long nextProgressCheckGameTick = Long.MIN_VALUE;
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
                || movementSpeed <= 0.0D
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

        MovementResult validation = validateSession(featureEnabled, level, gameTick);
        if (validation != null) {
            if (validation.status() == MovementStatus.WAITING_FOR_NATIVE_WEAPON) {
                stopOwnedNavigation();
                nextPathAttemptGameTick = gameTick;
                return result(validation.status(), validation.gateStatus());
            }
            return terminate(validation.status(), validation.gateStatus(), true);
        }

        BlockPos armoryPosition = reservation.target().position();
        if (withinWithdrawalDistance(guard, armoryPosition)) {
            stopOwnedNavigation();
            return result(MovementStatus.WITHDRAWAL_READY, null);
        }

        if (ownedNavigationTarget != null) {
            if (!ownedNavigationTarget.equals(navigation.getTargetPos())) {
                return terminate(MovementStatus.NAVIGATION_PREEMPTED, null, true);
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

        boolean started;
        try {
            started = navigation.moveTo(
                    approach.getX() + 0.5D,
                    approach.getY(),
                    approach.getZ() + 0.5D,
                    movementSpeed);
        } catch (RuntimeException exception) {
            navigation.stop();
            return pathAttemptFailed(gameTick);
        }
        if (!started) {
            return pathAttemptFailed(gameTick);
        }
        BlockPos actualTarget = navigation.getTargetPos();
        if (actualTarget == null || !actualTarget.equals(approach)) {
            navigation.stop();
            return pathAttemptFailed(gameTick);
        }
        ownedNavigationTarget = actualTarget.immutable();
        captureProgressAnchor(gameTick);
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
     * if a future caller invokes completion after a failed withdrawal.
     */
    public void finishAfterWithdrawal() {
        cancel();
    }

    public boolean active() {
        return requestKey != null;
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

    private void prepareApproaches(
            ArmoryAmmoReservation current,
            long gameTick) {
        this.approachPositions = approachPositions(
                current.target().position(),
                guard.getX(),
                guard.getY(),
                guard.getZ());
        this.nextPathAttemptGameTick = gameTick;
        captureProgressAnchor(gameTick);
    }

    private MovementResult pathAttemptFailed(long gameTick) {
        if (pathAttempts >= ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS) {
            return terminate(MovementStatus.PATH_ATTEMPTS_EXHAUSTED, null, true);
        }
        int retryDelay = ArmoryResupplyLimits.unreachableRetryDelay(
                pathAttempts,
                ArmoryResupplyLimits.DEFAULT_PATH_RETRY_TICKS);
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

    private void stopOwnedNavigation() {
        if (ownedNavigationTarget != null
                && ownedNavigationTarget.equals(navigation.getTargetPos())) {
            navigation.stop();
        }
        clearNavigationOwnership();
    }

    private void clearNavigationOwnership() {
        ownedNavigationTarget = null;
        nextProgressCheckGameTick = Long.MIN_VALUE;
    }

    private void clearSession() {
        reservation = null;
        requestKey = null;
        approachPositions = List.of();
        clearNavigationOwnership();
        pathAttempts = 0;
        nextPathAttemptGameTick = Long.MIN_VALUE;
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
        List<BlockPos> positions = new ArrayList<>(APPROACH_DIRECTIONS.size());
        for (Direction direction : APPROACH_DIRECTIONS) {
            positions.add(armoryPosition.relative(direction).immutable());
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
        PATH_ATTEMPTS_EXHAUSTED
    }

    public record MovementResult(
            MovementStatus status,
            @Nullable ArmoryResupplyGate.GateStatus gateStatus,
            int pathAttempts,
            long nextPathAttemptGameTick,
            @Nullable BlockPos navigationTarget) {
        public MovementResult {
            Objects.requireNonNull(status, "status");
            navigationTarget = navigationTarget == null
                    ? null
                    : navigationTarget.immutable();
            if (pathAttempts < 0
                    || pathAttempts > ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS
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
}
