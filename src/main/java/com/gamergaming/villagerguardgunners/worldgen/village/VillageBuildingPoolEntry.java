package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** One bounded, additive entry destined for one existing village pool. */
public record VillageBuildingPoolEntry(
        ResourceLocation entryId,
        ResourceLocation targetPool,
        ResourceLocation template,
        ResourceLocation processors,
        int weight) {
    public static final int MINIMUM_WEIGHT = 1;
    public static final int MAXIMUM_WEIGHT = 5;

    public VillageBuildingPoolEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(targetPool, "targetPool");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(processors, "processors");
        if (!VillagerGuardGunners.MODID.equals(entryId.getNamespace())) {
            throw new IllegalArgumentException("Village entry IDs must be owned by the add-on");
        }
        if (weight < MINIMUM_WEIGHT || weight > MAXIMUM_WEIGHT) {
            throw new IllegalArgumentException(
                    "Village entry weight must be in ["
                            + MINIMUM_WEIGHT + ", " + MAXIMUM_WEIGHT + "]");
        }
    }
}
