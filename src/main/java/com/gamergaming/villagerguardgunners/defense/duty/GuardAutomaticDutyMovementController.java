package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseRuntimePolicy;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Bounded, transient navigation owner for one guard's automatic defense duty.
 * It never changes Guard Villagers' follow or patrol data and never loads a
 * destination chunk.
 */
public final class GuardAutomaticDutyMovementController {
    static final double ARRIVAL_HORIZONTAL_DISTANCE = 1.5D;
    static final double ARRIVAL_VERTICAL_DISTANCE = 1.25D;
    static final int MINIMUM_RETRY_TICKS = 20;
    static final int MAXIMUM_RETRY_TICKS = 200;
    static final int PROGRESS_CHECK_TICKS = 40;
    static final double MINIMUM_PROGRESS_DISTANCE = 0.5D;
    static final double MINIMUM_MOVEMENT_DISTANCE = 0.25D;

    private final Guard guard;
    private final PathNavigation navigation;
    @Nullable
    private final AssignmentLookup assignmentLookup;

    @Nullable
    private VillageDefenseDutyAssignment assignment;
    @Nullable
    private UUID observedSlotId;
    private GuardAutomaticDutyGate.GateStatus gateStatus =
            GuardAutomaticDutyGate.GateStatus.NO_DUTY;
    private MovementStatus movementStatus = MovementStatus.IDLE;
    private long evaluationGameTime = Long.MIN_VALUE;
    private long nextPathAttemptGameTime = Long.MIN_VALUE;
    private long nextProgressCheckGameTime = Long.MIN_VALUE;
    private double lastDistance = Double.POSITIVE_INFINITY;
    private double lastProgressX;
    private double lastProgressY;
    private double lastProgressZ;
    private int consecutivePathFailures;
    private int pathAttempts;
    private boolean destinationLoaded;
    private boolean destinationValid;
    private boolean atDutyPosition;
    private boolean facingApplied;
    private boolean restaffReviewRequested;
    private boolean ownsMovement;

    public GuardAutomaticDutyMovementController(Guard guard) {
        this(guard, null);
    }

    GuardAutomaticDutyMovementController(
            Guard guard,
            @Nullable AssignmentLookup assignmentLookup) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.navigation = guard.getNavigation();
        this.assignmentLookup = assignmentLookup;
    }

    public boolean canStart() {
        refreshEvaluation();
        if (!gateStatus.ready() || !destinationValid || !destinationLoaded) {
            return false;
        }
        return atDutyPosition
                || guard.level().getGameTime() >= nextPathAttemptGameTime;
    }

    public boolean shouldContinue() {
        refreshEvaluation();
        return gateStatus.ready() && destinationValid && destinationLoaded;
    }

    public void tick() {
        refreshEvaluation();
        VillageDefenseDutyAssignment current = assignment;
        if (current == null
                || !gateStatus.ready()
                || !destinationValid
                || !destinationLoaded) {
            return;
        }

        long gameTime = guard.level().getGameTime();
        if (atDutyPosition) {
            if (movementStatus != MovementStatus.HOLDING_POST) {
                navigation.stop();
            }
            movementStatus = MovementStatus.HOLDING_POST;
            consecutivePathFailures = 0;
            lastDistance = 0.0D;
            applyFacingOnce(current.slot().facing());
            return;
        }

        facingApplied = false;
        if (movementStatus == MovementStatus.TRAVELLING
                && gameTime >= nextProgressCheckGameTime) {
            double distance = distanceTo(current.slot().position());
            if (!madeProgress(
                    lastDistance,
                    distance,
                    lastProgressX,
                    lastProgressY,
                    lastProgressZ,
                    guard.getX(),
                    guard.getY(),
                    guard.getZ())) {
                navigation.stop();
                recordPathFailure(gameTime, MovementStatus.STALLED);
                return;
            }
            lastDistance = distance;
            rememberProgressPosition();
            nextProgressCheckGameTime = gameTime + PROGRESS_CHECK_TICKS;
        }

        if (!navigation.isDone()) {
            movementStatus = MovementStatus.TRAVELLING;
            return;
        }
        if (gameTime < nextPathAttemptGameTime) {
            movementStatus = MovementStatus.WAITING_TO_RETRY;
            return;
        }
        attemptPath(current.slot().position(), gameTime);
    }

    /** Begins ownership with no navigation path left over from a prior goal. */
    public void start() {
        ownsMovement = true;
        navigation.stop();
        nextPathAttemptGameTime = guard.level().getGameTime();
        tick();
    }

    /** Stops only navigation owned while this MOVE goal was running. */
    public void stopForHigherPriorityGoal() {
        navigation.stop();
        facingApplied = false;
        if (assignment != null
                && destinationValid
                && destinationLoaded
                && !atDutyPosition) {
            movementStatus = MovementStatus.INTERRUPTED;
            nextPathAttemptGameTime = guard.level().getGameTime();
        }
        ownsMovement = false;
        invalidateEvaluation();
    }

    public RuntimeSnapshot snapshot() {
        refreshEvaluation();
        VillageDefenseDutyAssignment current = assignment;
        return new RuntimeSnapshot(
                movementStatus,
                gateStatus,
                current == null ? null : current.networkId(),
                current == null ? null : current.slotId(),
                current == null ? null : current.slot().position(),
                destinationLoaded,
                atDutyPosition,
                consecutivePathFailures,
                pathAttempts,
                diagnosticNextPathAttempt());
    }

    private void refreshEvaluation() {
        long gameTime = guard.level().getGameTime();
        if (evaluationGameTime == gameTime) {
            return;
        }
        evaluationGameTime = gameTime;
        try {
            refreshEvaluationAt(gameTime);
        } catch (RuntimeException exception) {
            gateStatus = GuardAutomaticDutyGate.GateStatus.STATE_UNAVAILABLE;
            destinationValid = false;
            destinationLoaded = false;
            atDutyPosition = false;
            facingApplied = false;
            movementStatus = MovementStatus.STATE_UNAVAILABLE;
        }
    }

    private void refreshEvaluationAt(long gameTime) {
        VillageDefenseDutyAssignment next = assignmentLookup == null
                ? VillageDefenseStaffingRuntime.assignment(guard)
                : assignmentLookup.assignment(guard);
        UUID nextSlotId = next == null ? null : next.slotId();
        boolean destinationChanged = assignment == null
                ? next != null
                : next == null
                        || !assignment.networkId().equals(next.networkId())
                        || !assignment.slot().equals(next.slot());
        if (!Objects.equals(observedSlotId, nextSlotId) || destinationChanged) {
            if (ownsMovement) {
                navigation.stop();
            }
            observedSlotId = nextSlotId;
            assignment = next;
            nextPathAttemptGameTime = gameTime;
            nextProgressCheckGameTime = gameTime + PROGRESS_CHECK_TICKS;
            lastDistance = Double.POSITIVE_INFINITY;
            consecutivePathFailures = 0;
            pathAttempts = 0;
            destinationLoaded = false;
            destinationValid = false;
            atDutyPosition = false;
            facingApplied = false;
            restaffReviewRequested = false;
            movementStatus = next == null
                    ? MovementStatus.IDLE
                    : MovementStatus.ASSIGNED;
        } else {
            assignment = next;
        }

        VillageDefenseDutyAssignment current = assignment;
        if (current == null) {
            gateStatus = GuardAutomaticDutyLiveGate.evaluate(
                    featureEnabled(), guard, null, false);
            movementStatus = MovementStatus.IDLE;
            return;
        }

        BlockPos destination = current.slot().position();
        atDutyPosition = atPosition(
                guard.getX(), guard.getY(), guard.getZ(), destination);
        gateStatus = GuardAutomaticDutyLiveGate.evaluate(
                featureEnabled(), guard, AutomaticDutyKind.DEFENSE_POST, atDutyPosition);
        destinationValid = validateAssignment(current);
        VillageDefenseDutyDestination.Status destinationStatus = destinationValid
                ? VillageDefenseDutyDestination.evaluate(level(), destination)
                : VillageDefenseDutyDestination.Status.OUTSIDE_WORLD;
        destinationLoaded = destinationStatus
                        == VillageDefenseDutyDestination.Status.WALKABLE
                || destinationStatus == VillageDefenseDutyDestination.Status.BLOCKED;
        if (destinationStatus.invalidWhileLoaded()) {
            destinationValid = false;
            requestRestaffReview(current);
        }
        if (!destinationValid) {
            movementStatus = MovementStatus.INVALID_ASSIGNMENT;
        } else if (!withinTravelDistance(destination)) {
            destinationValid = false;
            movementStatus = MovementStatus.TOO_FAR_AWAY;
            requestRestaffReview(current);
        } else if (!destinationLoaded) {
            movementStatus = MovementStatus.DESTINATION_UNLOADED;
        } else if (!gateStatus.ready()) {
            movementStatus = MovementStatus.GATE_BLOCKED;
            facingApplied = false;
        }
    }

    private boolean validateAssignment(VillageDefenseDutyAssignment current) {
        if (!(guard.level() instanceof ServerLevel level)) {
            return false;
        }
        return current.guardId().equals(guard.getUUID())
                && current.slot().dimensionId().equals(level.dimension().location());
    }

    private boolean withinTravelDistance(BlockPos destination) {
        return VillageDefenseStaffingPlanner.withinTravelDistance(
                guard.blockPosition(), destination);
    }

    private ServerLevel level() {
        return (ServerLevel) guard.level();
    }

    private void attemptPath(BlockPos destination, long gameTime) {
        pathAttempts = increment(pathAttempts);
        try {
            Path path = navigation.createPath(destination, 0);
            if (path == null || !navigation.moveTo(path, movementSpeed())) {
                recordPathFailure(gameTime, MovementStatus.PATH_UNAVAILABLE);
                return;
            }
        } catch (RuntimeException exception) {
            recordPathFailure(gameTime, MovementStatus.PATH_UNAVAILABLE);
            return;
        }
        consecutivePathFailures = 0;
        movementStatus = MovementStatus.TRAVELLING;
        lastDistance = distanceTo(destination);
        rememberProgressPosition();
        nextProgressCheckGameTime = gameTime + PROGRESS_CHECK_TICKS;
        nextPathAttemptGameTime = gameTime + Math.max(
                MINIMUM_RETRY_TICKS,
                pathRecalculationInterval());
    }

    private void recordPathFailure(long gameTime, MovementStatus status) {
        consecutivePathFailures = increment(consecutivePathFailures);
        movementStatus = status;
        nextPathAttemptGameTime = gameTime + retryDelayTicks(
                MINIMUM_RETRY_TICKS, consecutivePathFailures);
        nextProgressCheckGameTime = nextPathAttemptGameTime + PROGRESS_CHECK_TICKS;
        lastDistance = Double.POSITIVE_INFINITY;
    }

    private void rememberProgressPosition() {
        lastProgressX = guard.getX();
        lastProgressY = guard.getY();
        lastProgressZ = guard.getZ();
    }

    private void requestRestaffReview(VillageDefenseDutyAssignment current) {
        if (!restaffReviewRequested && guard.level() instanceof ServerLevel level) {
            restaffReviewRequested = true;
            VillageDefenseStaffingRuntime.synchronizeGuard(guard);
            VillageDefenseStaffingRuntime.requestReview(level, current.networkId());
        }
    }

    private void applyFacingOnce(Direction facing) {
        if (facingApplied) {
            return;
        }
        float yaw = facing.toYRot();
        guard.setYRot(yaw);
        guard.setYHeadRot(yaw);
        guard.setYBodyRot(yaw);
        facingApplied = true;
    }

    private double distanceTo(BlockPos destination) {
        double distanceSquared = guard.distanceToSqr(
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D);
        return Double.isFinite(distanceSquared)
                ? Math.sqrt(distanceSquared)
                : Double.POSITIVE_INFINITY;
    }

    private boolean featureEnabled() {
        return VillageDefenseRuntimePolicy.automaticDutiesEnabled();
    }

    private double movementSpeed() {
        return VillageDefenseRuntimePolicy.dutyMovementSpeed();
    }

    private int pathRecalculationInterval() {
        try {
            return ModConfigs.GUARD_GUNNERS.performance.pathRecalculationIntervalTicks.get();
        } catch (RuntimeException exception) {
            return MINIMUM_RETRY_TICKS;
        }
    }

    private long diagnosticNextPathAttempt() {
        return nextPathAttemptGameTime == Long.MIN_VALUE
                ? -1L
                : Math.max(0L, nextPathAttemptGameTime);
    }

    private void invalidateEvaluation() {
        evaluationGameTime = Long.MIN_VALUE;
    }

    static boolean atPosition(double x, double y, double z, BlockPos destination) {
        double dx = x - (destination.getX() + 0.5D);
        double dz = z - (destination.getZ() + 0.5D);
        return dx * dx + dz * dz
                        <= ARRIVAL_HORIZONTAL_DISTANCE * ARRIVAL_HORIZONTAL_DISTANCE
                && Math.abs(y - destination.getY()) <= ARRIVAL_VERTICAL_DISTANCE;
    }

    static int retryDelayTicks(int baseTicks, int consecutiveFailures) {
        int normalizedBase = Math.max(MINIMUM_RETRY_TICKS, baseTicks);
        int shifts = Math.max(0, Math.min(4, consecutiveFailures - 1));
        long delay = (long) normalizedBase << shifts;
        return (int) Math.min(MAXIMUM_RETRY_TICKS, delay);
    }

    static boolean madeProgress(
            double previousDistance,
            double currentDistance,
            double previousX,
            double previousY,
            double previousZ,
            double currentX,
            double currentY,
            double currentZ) {
        if (!Double.isFinite(previousDistance)
                || !Double.isFinite(currentDistance)
                || !Double.isFinite(previousX)
                || !Double.isFinite(previousY)
                || !Double.isFinite(previousZ)
                || !Double.isFinite(currentX)
                || !Double.isFinite(currentY)
                || !Double.isFinite(currentZ)) {
            return false;
        }
        if (previousDistance - currentDistance >= MINIMUM_PROGRESS_DISTANCE) {
            return true;
        }
        double deltaX = currentX - previousX;
        double deltaY = currentY - previousY;
        double deltaZ = currentZ - previousZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
                >= MINIMUM_MOVEMENT_DISTANCE * MINIMUM_MOVEMENT_DISTANCE;
    }

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    @FunctionalInterface
    interface AssignmentLookup {
        @Nullable
        VillageDefenseDutyAssignment assignment(Guard guard);
    }

    public enum MovementStatus {
        IDLE,
        ASSIGNED,
        TRAVELLING,
        HOLDING_POST,
        WAITING_TO_RETRY,
        PATH_UNAVAILABLE,
        STALLED,
        INTERRUPTED,
        DESTINATION_UNLOADED,
        TOO_FAR_AWAY,
        INVALID_ASSIGNMENT,
        STATE_UNAVAILABLE,
        GATE_BLOCKED
    }

    public record RuntimeSnapshot(
            MovementStatus status,
            GuardAutomaticDutyGate.GateStatus gateStatus,
            @Nullable UUID networkId,
            @Nullable UUID slotId,
            @Nullable BlockPos destination,
            boolean destinationLoaded,
            boolean atDutyPosition,
            int consecutivePathFailures,
            int pathAttempts,
            long nextPathAttemptGameTime) {
        public RuntimeSnapshot {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(gateStatus, "gateStatus");
            destination = destination == null ? null : destination.immutable();
            if ((slotId == null) != (networkId == null)
                    || (slotId == null) != (destination == null)
                    || consecutivePathFailures < 0
                    || pathAttempts < 0
                    || nextPathAttemptGameTime < -1L) {
                throw new IllegalArgumentException("Automatic-duty snapshot is contradictory");
            }
        }
    }
}
