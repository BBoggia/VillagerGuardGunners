package com.gamergaming.villagerguardgunners.squad;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Closed-key NBT codec for one squad patrol route. */
public final class SquadPatrolRouteCodec {
    private static final String VERSION_TAG = "Version";
    private static final String SQUAD_ID_TAG = "SquadId";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String WAYPOINTS_TAG = "Waypoints";
    private static final String REVISION_TAG = "Revision";
    private static final String CREATED_GAME_TIME_TAG = "CreatedGameTime";
    private static final String UPDATED_GAME_TIME_TAG = "UpdatedGameTime";
    private static final Set<String> ROUTE_KEYS = Set.of(
            VERSION_TAG,
            SQUAD_ID_TAG,
            DIMENSION_TAG,
            WAYPOINTS_TAG,
            REVISION_TAG,
            CREATED_GAME_TIME_TAG,
            UPDATED_GAME_TIME_TAG);

    private SquadPatrolRouteCodec() {
    }

    public static CompoundTag encode(SquadPatrolRoute route) {
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, route.schemaVersion());
        encoded.putUUID(SQUAD_ID_TAG, route.squadId());
        encoded.putString(DIMENSION_TAG, route.dimensionId().toString());
        ListTag waypoints = new ListTag();
        route.waypoints().stream()
                .map(BlockPos::asLong)
                .map(LongTag::valueOf)
                .forEach(waypoints::add);
        encoded.put(WAYPOINTS_TAG, waypoints);
        encoded.putLong(REVISION_TAG, route.revision());
        encoded.putLong(CREATED_GAME_TIME_TAG, route.createdGameTime());
        encoded.putLong(UPDATED_GAME_TIME_TAG, route.updatedGameTime());
        return encoded;
    }

    /** Returns {@code null} for unknown, future, or malformed records. */
    public static @Nullable SquadPatrolRoute decode(CompoundTag encoded) {
        if (!encoded.getAllKeys().equals(ROUTE_KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != SquadPatrolRoute.CURRENT_SCHEMA_VERSION
                || !encoded.hasUUID(SQUAD_ID_TAG)
                || !encoded.contains(DIMENSION_TAG, Tag.TAG_STRING)
                || !encoded.contains(WAYPOINTS_TAG, Tag.TAG_LIST)
                || !encoded.contains(REVISION_TAG, Tag.TAG_LONG)
                || !encoded.contains(CREATED_GAME_TIME_TAG, Tag.TAG_LONG)
                || !encoded.contains(UPDATED_GAME_TIME_TAG, Tag.TAG_LONG)) {
            return null;
        }
        Tag rawWaypoints = encoded.get(WAYPOINTS_TAG);
        if (!(rawWaypoints instanceof ListTag waypointTags)
                || waypointTags.isEmpty()
                || waypointTags.size() > SquadPatrolRoute.MAXIMUM_WAYPOINTS
                || waypointTags.getElementType() != Tag.TAG_LONG) {
            return null;
        }

        try {
            ResourceLocation dimension = ResourceLocation.tryParse(
                    encoded.getString(DIMENSION_TAG));
            if (dimension == null
                    || !dimension.toString().equals(encoded.getString(DIMENSION_TAG))) {
                return null;
            }
            List<BlockPos> waypoints = new ArrayList<>(waypointTags.size());
            for (Tag waypoint : waypointTags) {
                if (!(waypoint instanceof LongTag packed)) {
                    return null;
                }
                waypoints.add(BlockPos.of(packed.getAsLong()));
            }
            UUID squadId = encoded.getUUID(SQUAD_ID_TAG);
            return new SquadPatrolRoute(
                    SquadPatrolRoute.CURRENT_SCHEMA_VERSION,
                    squadId,
                    dimension,
                    waypoints,
                    encoded.getLong(REVISION_TAG),
                    encoded.getLong(CREATED_GAME_TIME_TAG),
                    encoded.getLong(UPDATED_GAME_TIME_TAG));
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
