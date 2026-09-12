package com.gamergaming.villagerguardgunners.squad;

/**
 * Immutable, server-owned policy for executing an already persisted squad
 * patrol route. Route authoring and persistence deliberately remain available
 * while execution is disabled.
 */
public record PatrolRouteExecutionSettings(
        boolean enabled,
        double movementSpeed,
        int waypointDwellTicks,
        int maximumFailuresPerWaypoint) {
    public static final double MINIMUM_MOVEMENT_SPEED = 0.1D;
    public static final double MAXIMUM_MOVEMENT_SPEED = 2.0D;
    public static final int MINIMUM_DWELL_TICKS = 0;
    public static final int MAXIMUM_DWELL_TICKS = 1_200;
    public static final int MINIMUM_FAILURES_PER_WAYPOINT = 1;
    public static final int MAXIMUM_FAILURES_PER_WAYPOINT = 8;

    public static final PatrolRouteExecutionSettings DEFAULT =
            new PatrolRouteExecutionSettings(true, 0.8D, 20, 3);
    public static final PatrolRouteExecutionSettings DISABLED =
            new PatrolRouteExecutionSettings(
                    false,
                    DEFAULT.movementSpeed,
                    DEFAULT.waypointDwellTicks,
                    DEFAULT.maximumFailuresPerWaypoint);

    public PatrolRouteExecutionSettings {
        if (!Double.isFinite(movementSpeed)
                || movementSpeed < MINIMUM_MOVEMENT_SPEED
                || movementSpeed > MAXIMUM_MOVEMENT_SPEED
                || waypointDwellTicks < MINIMUM_DWELL_TICKS
                || waypointDwellTicks > MAXIMUM_DWELL_TICKS
                || maximumFailuresPerWaypoint < MINIMUM_FAILURES_PER_WAYPOINT
                || maximumFailuresPerWaypoint > MAXIMUM_FAILURES_PER_WAYPOINT) {
            throw new IllegalArgumentException(
                    "Patrol-route execution settings exceed their hard bounds");
        }
    }
}
