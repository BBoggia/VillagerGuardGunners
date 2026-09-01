package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** One bounded, additive entry destined for one existing village pool. */
public record VillageBuildingPoolEntry(
        ResourceLocation entryId,
        VillageBuildingType buildingType,
        ResourceLocation targetPool,
        ResourceLocation template,
        ResourceLocation processors,
        StructureTemplatePool.Projection projection,
        int weight) {
    public static final int MINIMUM_WEIGHT = 1;
    public static final int MAXIMUM_WEIGHT = 5;

    public VillageBuildingPoolEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(buildingType, "buildingType");
        Objects.requireNonNull(targetPool, "targetPool");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(processors, "processors");
        Objects.requireNonNull(projection, "projection");
        if (weight < MINIMUM_WEIGHT || weight > MAXIMUM_WEIGHT) {
            throw new IllegalArgumentException(
                    "Village entry weight must be in ["
                            + MINIMUM_WEIGHT + ", " + MAXIMUM_WEIGHT + "]");
        }
    }
}
