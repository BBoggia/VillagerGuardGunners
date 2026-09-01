package com.gamergaming.villagerguardgunners.worldgen.village;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Pure deterministic identity derivation for village-attached buildings. */
public final class VillageBuildingIds {
    private static final String BUILDING_DOMAIN =
            "villagerguardgunners:village_building:v1";

    public static UUID buildingId(
            long worldSeed,
            ResourceLocation dimensionId,
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            BlockPos anchorPosition) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(buildingType, "buildingType");
        Objects.requireNonNull(anchorPosition, "anchorPosition");
        String input = BUILDING_DOMAIN + '\n'
                + worldSeed + '\n'
                + dimensionId + '\n'
                + entryId + '\n'
                + buildingType.serializedName() + '\n'
                + anchorPosition.getX() + ','
                + anchorPosition.getY() + ','
                + anchorPosition.getZ();
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    private VillageBuildingIds() {
    }
}
