package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.squad.PatrolRouteExecutionSettings;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRuntimePolicy;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRoute;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteResolver;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Bounded, transient execution of one guard's persistent squad patrol route.
 * It never rewrites Guard Villagers' patrol state and never loads chunks.
 */
public final class GuardPatrolRouteMovementController {
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
    private final RouteLookup routeLookup;

    @Nullable
    private SquadPatrolRoute route;
    private PatrolRouteExecutionSettings settings =
            PatrolRouteExecutionSettings.DISABLED;
    @Nullable
    private UUID observedSquadId;
    private int observedMembershipRevision = Integer.MIN_VALUE;
    private int waypointIndex = -1;
    private GuardAutomaticDutyGate.GateStatus gateStatus =
            GuardAutomaticDutyGate.GateStatus.NO_DUTY;
    private MovementStatus movementStatus = MovementStatus.NO_ROUTE;
    private long evaluationGameTime = Long.MIN_VALUE;
    private long nextPathAttemptGameTime = Long.MIN_VALUE;
    private long nextProgressCheckGameTime = Long.MIN_VALUE;
    private long dwellUntilGameTime = Long.MIN_VALUE;
    private double lastDistance = Double.POSITIVE_INFINITY;
    private double lastProgressX;
    private double lastProgressY;
    private double lastProgressZ;
    private int consecutivePathFailures;
    private int pathAttempts;
    private int skippedWaypoints;
    private boolean destinationLoaded;
    private boolean destinationValid;
    private boolean atWaypoint;
    private boolean routeHasLoadedWaypoint;
    private boolean ownsMovement;

    public GuardPatrolRouteMovementController(Guard guard) {
        this(guard, null);
    }

    GuardPatrolRouteMovementController(
            Guard guard,
            @Nullable RouteLookup routeLookup) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.navigation = guard.getNavigation();
        this.routeLookup = routeLookup;
    }

    /** True when a route, rather than a defense post, owns automatic duty. */
    public boolean hasExecutableRoute() {
        refreshEvaluation();
        return route != null && routeHasLoadedWaypoint;
    }

    public boolean canStart() {
        refreshEvaluation();
        return route != null && routeHasLoadedWaypoint && gateStatus.ready();
    }

    public boolean shouldContinue() {
        refreshEvaluation();
        return route != null && routeHasLoadedWaypoint && gateStatus.ready();
    }

    public void start() {
        ownsMovement = true;
        navigation.stop();
        nextPathAttemptGameTime = guard.level().getGameTime();
        tick();
    }

    public void tick() {
        refreshEvaluation();
        SquadPatrolRoute current = route;
        if (current == null || !gateStatus.ready()) {
            return;
        }
        long gameTime = guard.level().getGameTime();
        if (!destinationValid) {
            navigation.stop();
            if (gameTime >= nextPathAttemptGameTime) {
                recordPathFailure(gameTime, MovementStatus.INVALID_DESTINATION);
            }
            return;
        }
        if (!destinationLoaded) {
            navigation.stop();
            movementStatus = MovementStatus.DESTINATION_UNLOADED;
            if (gameTime >= nextPathAttemptGameTime) {
                recordPathFailure(gameTime, MovementStatus.DESTINATION_UNLOADED);
            }
            return;
        }
        if (atWaypoint) {
            navigation.stop();
            if (dwellUntilGameTime == Long.MIN_VALUE) {
                dwellUntilGameTime = saturatingAdd(
                        gameTime, settings.waypointDwellTicks());
                movementStatus = MovementStatus.DWELLING;
                return;
            }
            if (gameTime < dwellUntilGameTime) {
                movementStatus = MovementStatus.DWELLING;
                return;
            }
            advanceWaypoint(gameTime, false);
            return;
        }

        dwellUntilGameTime = Long.MIN_VALUE;
        if (movementStatus == MovementStatus.TRAVELLING
                && gameTime >= nextProgressCheckGameTime) {
            BlockPos destination = destination(current);
            double distance = distanceTo(destination);
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
            nextProgressCheckGameTime = saturatingAdd(gameTime, PROGRESS_CHECK_TICKS);
        }
        if (!navigation.isDone()) {
            movementStatus = MovementStatus.TRAVELLING;
            return;
        }
        if (gameTime < nextPathAttemptGameTime) {
            movementStatus = MovementStatus.WAITING_TO_RETRY;
            return;
        }
        attemptPath(destination(current), gameTime);
    }

    public void stopForHigherPriorityGoal() {
        navigation.stop();
        if (route != null) {
            movementStatus = MovementStatus.INTERRUPTED;
            nextPathAttemptGameTime = guard.level().getGameTime();
        }
        ownsMovement = false;
        invalidateEvaluation();
    }

    public RuntimeSnapshot snapshot() {
        refreshEvaluation();
        SquadPatrolRoute current = route;
        BlockPos destination = current == null ? null : destination(current);
        return new RuntimeSnapshot(
                movementStatus,
                gateStatus,
                current == null ? null : current.squadId(),
                current == null ? -1L : current.revision(),
                current == null ? -1 : waypointIndex,
                current == null ? 0 : current.waypoints().size(),
                destination,
                destinationLoaded,
                destinationValid,
                atWaypoint,
                consecutivePathFailures,
                pathAttempts,
                skippedWaypoints,
                diagnosticNextPathAttempt(current != null),
                settings.enabled(),
                settings.movementSpeed(),
                settings.waypointDwellTicks(),
                settings.maximumFailuresPerWaypoint());
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
            route = null;
            waypointIndex = -1;
            gateStatus = GuardAutomaticDutyGate.GateStatus.STATE_UNAVAILABLE;
            destinationLoaded = false;
            destinationValid = false;
            atWaypoint = false;
            routeHasLoadedWaypoint = false;
            movementStatus = MovementStatus.STATE_UNAVAILABLE;
        }
    }

    private void refreshEvaluationAt(long gameTime) {
        settings = SquadPatrolRuntimePolicy.settings();
        UUID squadId = GuardSquadAccess.getSquadId(guard);
        int membershipRevision = GuardSquadAccess.getRevision(guard);
        SquadPatrolRoute candidate = settings.enabled()
                ? routeLookup == null
                        ? SquadPatrolRouteResolver.resolve(guard).orElse(null)
                        : routeLookup.route(guard, squadId)
                : null;
        if (candidate != null
                && (squadId == null
                        || !candidate.squadId().equals(squadId)
                        || !candidate.executable()
                        || !(guard.level() instanceof ServerLevel level)
                        || !candidate.dimensionId().equals(level.dimension().location()))) {
            candidate = null;
        }
        boolean routeChanged = !Objects.equals(observedSquadId, squadId)
                || observedMembershipRevision != membershipRevision
                || !sameRoute(route, candidate);
        if (routeChanged) {
            if (ownsMovement) {
                navigation.stop();
            }
            observedSquadId = squadId;
            observedMembershipRevision = membershipRevision;
            route = candidate;
            waypointIndex = candidate == null
                    ? -1
                    : nearestWaypoint(
                            candidate.waypoints(), guard.getX(), guard.getY(), guard.getZ());
            resetProgress(gameTime);
            movementStatus = candidate == null
                    ? MovementStatus.NO_ROUTE
                    : MovementStatus.ROUTE_ASSIGNED;
        } else {
            route = candidate;
        }
        SquadPatrolRoute current = route;
        if (current == null) {
            gateStatus = GuardAutomaticDutyLiveGate.evaluate(
                    settings.enabled(), guard, null, false);
            destinationLoaded = false;
            destinationValid = false;
            atWaypoint = false;
            routeHasLoadedWaypoint = false;
            return;
        }

        BlockPos destination = destination(current);
        atWaypoint = atPosition(guard.getX(), guard.getY(), guard.getZ(), destination);
        gateStatus = GuardAutomaticDutyLiveGate.evaluate(
                settings.enabled(), guard, AutomaticDutyKind.PATROL_ROUTE, atWaypoint);
        destinationValid = basicDestinationValid(current, destination);
        destinationLoaded = destinationValid && isLoaded(destination);
        routeHasLoadedWaypoint = destinationLoaded || anyWaypointLoaded(current);
        if (destinationLoaded) {
            destinationValid = walkableDestination(destination);
        }
        if (!destinationValid) {
            movementStatus = MovementStatus.INVALID_DESTINATION;
        } else if (!destinationLoaded) {
            movementStatus = MovementStatus.DESTINATION_UNLOADED;
        } else if (!gateStatus.ready()) {
            movementStatus = MovementStatus.GATE_BLOCKED;
        }
    }

    private boolean basicDestinationValid(
            SquadPatrolRoute current,
            BlockPos destination) {
        if (!(guard.level() instanceof ServerLevel level)) {
            return false;
        }
        return current.squadId().equals(GuardSquadAccess.getSquadId(guard))
                && current.dimensionId().equals(level.dimension().location())
                && level.isInWorldBounds(destination)
                && level.isInWorldBounds(destination.above())
                && level.isInWorldBounds(destination.below())
                && level.getWorldBorder().isWithinBounds(destination);
    }

    private boolean isLoaded(BlockPos destination) {
        return guard.level() instanceof ServerLevel level
                && level.hasChunk(
                        SectionPos.blockToSectionCoord(destination.getX()),
                        SectionPos.blockToSectionCoord(destination.getZ()));
    }

    private boolean anyWaypointLoaded(SquadPatrolRoute current) {
        if (!(guard.level() instanceof ServerLevel level)) {
            return false;
        }
        for (BlockPos waypoint : current.waypoints()) {
            if (level.hasChunk(
                    SectionPos.blockToSectionCoord(waypoint.getX()),
                    SectionPos.blockToSectionCoord(waypoint.getZ()))) {
                return true;
            }
        }
        return false;
    }

    private boolean walkableDestination(BlockPos destination) {
        if (!(guard.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos headPosition = destination.above();
        BlockPos floorPosition = destination.below();
        BlockState feet = level.getBlockState(destination);
        BlockState head = level.getBlockState(headPosition);
        BlockState floor = level.getBlockState(floorPosition);
        return feet.getCollisionShape(level, destination).isEmpty()
                && head.getCollisionShape(level, headPosition).isEmpty()
                && feet.getFluidState().isEmpty()
                && head.getFluidState().isEmpty()
                && floor.isFaceSturdy(level, floorPosition, Direction.UP);
    }

    private void attemptPath(BlockPos destination, long gameTime) {
        pathAttempts = increment(pathAttempts);
        try {
            Path path = navigation.createPath(destination, 0);
            if (path == null || !navigation.moveTo(path, settings.movementSpeed())) {
                recordPathFailure(gameTime, MovementStatus.PATH_UNAVAILABLE);
                return;
            }
        } catch (RuntimeException exception) {
            recordPathFailure(gameTime, MovementStatus.PATH_UNAVAILABLE);
            return;
        }
        movementStatus = MovementStatus.TRAVELLING;
        lastDistance = distanceTo(destination);
        rememberProgressPosition();
        nextProgressCheckGameTime = saturatingAdd(gameTime, PROGRESS_CHECK_TICKS);
        nextPathAttemptGameTime = saturatingAdd(
                gameTime,
                Math.max(MINIMUM_RETRY_TICKS, pathRecalculationInterval()));
    }

    private void recordPathFailure(long gameTime, MovementStatus status) {
        consecutivePathFailures = increment(consecutivePathFailures);
        if (consecutivePathFailures >= settings.maximumFailuresPerWaypoint()) {
            movementStatus = MovementStatus.SKIPPING_UNREACHABLE;
            advanceWaypoint(gameTime, true);
            return;
        }
        movementStatus = status;
        nextPathAttemptGameTime = saturatingAdd(
                gameTime,
                retryDelayTicks(MINIMUM_RETRY_TICKS, consecutivePathFailures));
        nextProgressCheckGameTime = saturatingAdd(
                nextPathAttemptGameTime, PROGRESS_CHECK_TICKS);
        lastDistance = Double.POSITIVE_INFINITY;
    }

    private void advanceWaypoint(long gameTime, boolean skipped) {
        SquadPatrolRoute current = route;
        if (current == null) {
            return;
        }
        waypointIndex = nextWaypointIndex(waypointIndex, current.waypoints().size());
        if (skipped) {
            skippedWaypoints = increment(skippedWaypoints);
        }
        consecutivePathFailures = 0;
        lastDistance = Double.POSITIVE_INFINITY;
        dwellUntilGameTime = Long.MIN_VALUE;
        atWaypoint = false;
        destinationLoaded = false;
        destinationValid = false;
        nextPathAttemptGameTime = saturatingAdd(
                gameTime, skipped ? MINIMUM_RETRY_TICKS : 0L);
        nextProgressCheckGameTime = saturatingAdd(
                nextPathAttemptGameTime, PROGRESS_CHECK_TICKS);
        movementStatus = skipped
                ? MovementStatus.SKIPPING_UNREACHABLE
                : MovementStatus.ADVANCING;
        invalidateEvaluation();
    }

    private void resetProgress(long gameTime) {
        nextPathAttemptGameTime = gameTime;
        nextProgressCheckGameTime = saturatingAdd(gameTime, PROGRESS_CHECK_TICKS);
        dwellUntilGameTime = Long.MIN_VALUE;
        lastDistance = Double.POSITIVE_INFINITY;
        consecutivePathFailures = 0;
        pathAttempts = 0;
        skippedWaypoints = 0;
        destinationLoaded = false;
        destinationValid = false;
        atWaypoint = false;
        routeHasLoadedWaypoint = false;
    }

    private void rememberProgressPosition() {
        lastProgressX = guard.getX();
        lastProgressY = guard.getY();
        lastProgressZ = guard.getZ();
    }

    private BlockPos destination(SquadPatrolRoute current) {
        return current.waypoints().get(waypointIndex);
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

    private int pathRecalculationInterval() {
        try {
            return ModConfigs.GUARD_GUNNERS.performance.pathRecalculationIntervalTicks.get();
        } catch (RuntimeException exception) {
            return MINIMUM_RETRY_TICKS;
        }
    }

    private long diagnosticNextPathAttempt(boolean hasRoute) {
        return !hasRoute || nextPathAttemptGameTime == Long.MIN_VALUE
                ? -1L
                : Math.max(0L, nextPathAttemptGameTime);
    }

    private void invalidateEvaluation() {
        evaluationGameTime = Long.MIN_VALUE;
    }

    static int nearestWaypoint(List<BlockPos> waypoints, double x, double y, double z) {
        Objects.requireNonNull(waypoints, "waypoints");
        if (waypoints.isEmpty()
                || !Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)) {
            throw new IllegalArgumentException("A route requires finite position data");
        }
        int nearest = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < waypoints.size(); index++) {
            BlockPos waypoint = Objects.requireNonNull(waypoints.get(index), "waypoint");
            double dx = x - (waypoint.getX() + 0.5D);
            double dy = y - waypoint.getY();
            double dz = z - (waypoint.getZ() + 0.5D);
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearest = index;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    static int nextWaypointIndex(int currentIndex, int waypointCount) {
        if (currentIndex < 0 || currentIndex >= waypointCount || waypointCount < 2) {
            throw new IllegalArgumentException("Route progress is outside its executable route");
        }
        return currentIndex + 1 == waypointCount ? 0 : currentIndex + 1;
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
        double dx = currentX - previousX;
        double dy = currentY - previousY;
        double dz = currentZ - previousZ;
        return dx * dx + dy * dy + dz * dz
                >= MINIMUM_MOVEMENT_DISTANCE * MINIMUM_MOVEMENT_DISTANCE;
    }

    private static boolean sameRoute(
            @Nullable SquadPatrolRoute left,
            @Nullable SquadPatrolRoute right) {
        return left == right
                || left != null
                        && right != null
                        && left.squadId().equals(right.squadId())
                        && left.revision() == right.revision();
    }

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    private static long saturatingAdd(long value, long amount) {
        return amount > 0L && value > Long.MAX_VALUE - amount
                ? Long.MAX_VALUE
                : value + amount;
    }

    @FunctionalInterface
    interface RouteLookup {
        @Nullable
        SquadPatrolRoute route(Guard guard, @Nullable UUID squadId);
    }

    public enum MovementStatus {
        NO_ROUTE,
        ROUTE_ASSIGNED,
        TRAVELLING,
        DWELLING,
        ADVANCING,
        WAITING_TO_RETRY,
        PATH_UNAVAILABLE,
        STALLED,
        SKIPPING_UNREACHABLE,
        INTERRUPTED,
        DESTINATION_UNLOADED,
        INVALID_DESTINATION,
        STATE_UNAVAILABLE,
        GATE_BLOCKED
    }

    public record RuntimeSnapshot(
            MovementStatus status,
            GuardAutomaticDutyGate.GateStatus gateStatus,
            @Nullable UUID squadId,
            long routeRevision,
            int waypointIndex,
            int waypointCount,
            @Nullable BlockPos destination,
            boolean destinationLoaded,
            boolean destinationValid,
            boolean atWaypoint,
            int consecutivePathFailures,
            int pathAttempts,
            int skippedWaypoints,
            long nextPathAttemptGameTime,
            boolean executionEnabled,
            double movementSpeed,
            int waypointDwellTicks,
            int maximumFailuresPerWaypoint) {
        public RuntimeSnapshot {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(gateStatus, "gateStatus");
            destination = destination == null ? null : destination.immutable();
            boolean hasRoute = squadId != null;
            if (hasRoute != (destination != null)
                    || hasRoute != (routeRevision > 0L)
                    || hasRoute != (waypointCount >= 2)
                    || hasRoute != (waypointIndex >= 0 && waypointIndex < waypointCount)
                    || !hasRoute && (routeRevision != -1L
                            || waypointIndex != -1
                            || waypointCount != 0)
                    || consecutivePathFailures < 0
                    || pathAttempts < 0
                    || skippedWaypoints < 0
                    || nextPathAttemptGameTime < -1L
                    || !Double.isFinite(movementSpeed)
                    || movementSpeed < PatrolRouteExecutionSettings.MINIMUM_MOVEMENT_SPEED
                    || movementSpeed > PatrolRouteExecutionSettings.MAXIMUM_MOVEMENT_SPEED
                    || waypointDwellTicks < PatrolRouteExecutionSettings.MINIMUM_DWELL_TICKS
                    || waypointDwellTicks > PatrolRouteExecutionSettings.MAXIMUM_DWELL_TICKS
                    || maximumFailuresPerWaypoint
                            < PatrolRouteExecutionSettings.MINIMUM_FAILURES_PER_WAYPOINT
                    || maximumFailuresPerWaypoint
                            > PatrolRouteExecutionSettings.MAXIMUM_FAILURES_PER_WAYPOINT
                    || !executionEnabled && hasRoute) {
                throw new IllegalArgumentException("Patrol-route snapshot is contradictory");
            }
        }
    }
}
