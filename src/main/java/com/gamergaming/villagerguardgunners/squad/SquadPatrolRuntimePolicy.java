package com.gamergaming.villagerguardgunners.squad;

import com.gamergaming.villagerguardgunners.init.ModConfigs;

/** Single fail-closed view of the live patrol-route execution policy. */
public final class SquadPatrolRuntimePolicy {
    private static volatile PatrolRouteExecutionSettings cachedSettings =
            PatrolRouteExecutionSettings.DISABLED;

    private SquadPatrolRuntimePolicy() {
    }

    public static PatrolRouteExecutionSettings settings() {
        try {
            boolean enabled = ModConfigs.GUARD_GUNNERS.enabled.get()
                    && ModConfigs.GUARD_GUNNERS.squads.patrolRouteExecution.get();
            double movementSpeed =
                    ModConfigs.GUARD_GUNNERS.squads.patrolMovementSpeed.get();
            int waypointDwellTicks =
                    ModConfigs.GUARD_GUNNERS.squads.patrolWaypointDwellTicks.get();
            int maximumFailures = ModConfigs.GUARD_GUNNERS.squads
                    .patrolMaximumFailuresPerWaypoint.get();
            PatrolRouteExecutionSettings current = cachedSettings;
            if (current.enabled() == enabled
                    && Double.compare(current.movementSpeed(), movementSpeed) == 0
                    && current.waypointDwellTicks() == waypointDwellTicks
                    && current.maximumFailuresPerWaypoint() == maximumFailures) {
                return current;
            }
            PatrolRouteExecutionSettings updated = new PatrolRouteExecutionSettings(
                    enabled,
                    movementSpeed,
                    waypointDwellTicks,
                    maximumFailures);
            cachedSettings = updated;
            return updated;
        } catch (RuntimeException exception) {
            return PatrolRouteExecutionSettings.DISABLED;
        }
    }

    public static boolean executionEnabled() {
        return settings().enabled();
    }
}
