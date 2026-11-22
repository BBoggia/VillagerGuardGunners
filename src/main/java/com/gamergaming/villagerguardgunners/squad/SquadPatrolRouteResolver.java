package com.gamergaming.villagerguardgunners.squad;

import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import tallestegg.guardvillagers.entities.Guard;

/** Fail-closed lookup with a fixed maximum of 64 waypoint chunk checks. */
public final class SquadPatrolRouteResolver {
    private SquadPatrolRouteResolver() {
    }

    public static Optional<SquadPatrolRoute> resolve(Guard guard) {
        if (guard == null
                || !(guard.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()
                || !guard.isAlive()
                || guard.isRemoved()) {
            return Optional.empty();
        }
        UUID squadId = GuardSquadAccess.getSquadId(guard);
        if (squadId == null) {
            return Optional.empty();
        }
        try {
            SquadRegistrySavedData registry = SquadRegistrySavedData.get(level.getServer());
            if (!registry.enabled()) {
                return Optional.empty();
            }
            return registry.findPatrolRoute(squadId)
                    .filter(SquadPatrolRoute::executable)
                    .filter(route -> route.dimensionId().equals(
                            level.dimension().location()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * True only when a saved route has at least one destination that can be
     * inspected without loading a chunk. This is the live movement-precedence
     * boundary; persistence and editing remain independent of chunk state.
     */
    public static boolean hasLoadedWaypoint(Guard guard) {
        if (guard == null || !(guard.level() instanceof ServerLevel level)) {
            return false;
        }
        return resolve(guard)
                .map(route -> route.waypoints().stream().anyMatch(waypoint ->
                        level.hasChunk(
                                SectionPos.blockToSectionCoord(waypoint.getX()),
                                SectionPos.blockToSectionCoord(waypoint.getZ()))))
                .orElse(false);
    }
}
