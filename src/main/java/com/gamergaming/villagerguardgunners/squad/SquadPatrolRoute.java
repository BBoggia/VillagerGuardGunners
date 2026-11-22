package com.gamergaming.villagerguardgunners.squad;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Persistent, ordered patrol route owned by one registered squad. */
public record SquadPatrolRoute(
        int schemaVersion,
        UUID squadId,
        ResourceLocation dimensionId,
        List<BlockPos> waypoints,
        long revision,
        long createdGameTime,
        long updatedGameTime) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAXIMUM_WAYPOINTS = 64;
    public static final int MAXIMUM_SEGMENT_DISTANCE = 256;
    public static final long MAXIMUM_SEGMENT_DISTANCE_SQUARED =
            (long) MAXIMUM_SEGMENT_DISTANCE * MAXIMUM_SEGMENT_DISTANCE;

    private static final int MAXIMUM_HORIZONTAL_COORDINATE = 30_000_000;
    private static final int MINIMUM_PACKED_Y = -2_048;
    private static final int MAXIMUM_PACKED_Y = 2_047;
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public SquadPatrolRoute {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported patrol-route schema version");
        }
        squadId = Objects.requireNonNull(squadId, "squadId");
        if (NIL_UUID.equals(squadId)) {
            throw new IllegalArgumentException("A patrol route may not use the nil squad ID");
        }
        dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        waypoints = List.copyOf(Objects.requireNonNull(waypoints, "waypoints"));
        if (waypoints.isEmpty() || waypoints.size() > MAXIMUM_WAYPOINTS) {
            throw new IllegalArgumentException("A patrol route must contain 1-"
                    + MAXIMUM_WAYPOINTS + " waypoints");
        }
        for (BlockPos waypoint : waypoints) {
            requirePersistablePosition(waypoint);
        }
        for (int index = 1; index < waypoints.size(); index++) {
            requireBoundedSegment(waypoints.get(index - 1), waypoints.get(index));
        }
        if (waypoints.size() > 1) {
            requireBoundedSegment(waypoints.get(waypoints.size() - 1), waypoints.get(0));
        }
        if (revision <= 0L) {
            throw new IllegalArgumentException("A patrol-route revision must be positive");
        }
        if (createdGameTime < 0L || updatedGameTime < createdGameTime) {
            throw new IllegalArgumentException("A patrol route contains invalid game times");
        }
    }

    public static SquadPatrolRoute create(
            UUID squadId,
            ResourceLocation dimensionId,
            List<BlockPos> waypoints,
            long gameTime) {
        return createAtRevision(squadId, dimensionId, waypoints, 1L, gameTime);
    }

    static SquadPatrolRoute createAtRevision(
            UUID squadId,
            ResourceLocation dimensionId,
            List<BlockPos> waypoints,
            long revision,
            long gameTime) {
        return new SquadPatrolRoute(
                CURRENT_SCHEMA_VERSION,
                squadId,
                dimensionId,
                waypoints,
                revision,
                gameTime,
                gameTime);
    }

    public SquadPatrolRoute revised(List<BlockPos> replacement, long gameTime) {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("A patrol-route revision is exhausted");
        }
        return revisedAtRevision(replacement, revision + 1L, gameTime);
    }

    SquadPatrolRoute revisedAtRevision(
            List<BlockPos> replacement,
            long replacementRevision,
            long gameTime) {
        if (gameTime < updatedGameTime) {
            throw new IllegalArgumentException("A patrol route update cannot move backward in time");
        }
        if (replacementRevision <= revision) {
            throw new IllegalArgumentException("A patrol route update must increase its revision");
        }
        return new SquadPatrolRoute(
                CURRENT_SCHEMA_VERSION,
                squadId,
                dimensionId,
                replacement,
                replacementRevision,
                createdGameTime,
                gameTime);
    }

    /** A single waypoint is a saved editor draft, not an executable loop. */
    public boolean executable() {
        return waypoints.size() >= 2;
    }

    private static void requirePersistablePosition(BlockPos position) {
        BlockPos waypoint = Objects.requireNonNull(position, "waypoint");
        if (waypoint.getX() < -MAXIMUM_HORIZONTAL_COORDINATE
                || waypoint.getX() >= MAXIMUM_HORIZONTAL_COORDINATE
                || waypoint.getZ() < -MAXIMUM_HORIZONTAL_COORDINATE
                || waypoint.getZ() >= MAXIMUM_HORIZONTAL_COORDINATE
                || waypoint.getY() < MINIMUM_PACKED_Y
                || waypoint.getY() > MAXIMUM_PACKED_Y) {
            throw new IllegalArgumentException("A patrol waypoint is outside packed world bounds");
        }
    }

    private static void requireBoundedSegment(BlockPos from, BlockPos to) {
        long x = (long) to.getX() - from.getX();
        long y = (long) to.getY() - from.getY();
        long z = (long) to.getZ() - from.getZ();
        long distanceSquared = x * x + y * y + z * z;
        if (distanceSquared == 0L || distanceSquared > MAXIMUM_SEGMENT_DISTANCE_SQUARED) {
            throw new IllegalArgumentException(
                    "Patrol-route segments must be distinct and no longer than "
                            + MAXIMUM_SEGMENT_DISTANCE + " blocks");
        }
    }
}
