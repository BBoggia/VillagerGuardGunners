package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Immutable provenance for one placed village-building anchor. */
public record VillageBuildingIdentity(
        int schemaVersion,
        UUID buildingId,
        ResourceLocation entryId,
        VillageBuildingType buildingType,
        ResourceLocation dimensionId,
        BlockPos anchorPosition) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public VillageBuildingIdentity {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported village-building identity schema");
        }
        buildingId = Objects.requireNonNull(buildingId, "buildingId");
        if (NIL_UUID.equals(buildingId)) {
            throw new IllegalArgumentException("Village-building ID cannot be the nil UUID");
        }
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(buildingType, "buildingType");
        Objects.requireNonNull(dimensionId, "dimensionId");
        anchorPosition = Objects.requireNonNull(
                anchorPosition, "anchorPosition").immutable();
    }

    public static VillageBuildingIdentity anchored(
            long worldSeed,
            ResourceLocation dimensionId,
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            BlockPos anchorPosition) {
        return new VillageBuildingIdentity(
                CURRENT_SCHEMA_VERSION,
                VillageBuildingIds.buildingId(
                        worldSeed,
                        dimensionId,
                        entryId,
                        buildingType,
                        anchorPosition),
                entryId,
                buildingType,
                dimensionId,
                anchorPosition);
    }
}
