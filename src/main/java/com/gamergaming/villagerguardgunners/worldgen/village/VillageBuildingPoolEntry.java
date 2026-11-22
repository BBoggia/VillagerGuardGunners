package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayout;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutySlotDefinition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        int weight,
        double selectionChance,
        boolean terrainSafety,
        Optional<List<VillageDefenseDutySlotDefinition>> dutySlots) {
    public static final int MINIMUM_WEIGHT = 1;
    public static final int MAXIMUM_WEIGHT = 5;

    public VillageBuildingPoolEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(buildingType, "buildingType");
        Objects.requireNonNull(targetPool, "targetPool");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(processors, "processors");
        Objects.requireNonNull(projection, "projection");
        if (!Double.isFinite(selectionChance) || selectionChance < 0.0 || selectionChance > 1.0) {
            throw new IllegalArgumentException("Village entry selection chance must be finite and in [0, 1]");
        }
        if (terrainSafety && projection != StructureTemplatePool.Projection.RIGID) {
            throw new IllegalArgumentException("terrain_safety requires rigid projection");
        }
        dutySlots = Objects.requireNonNull(dutySlots, "dutySlots")
                .map(slots -> {
                    if (slots.isEmpty()
                            || slots.size()
                                    > VillageDefenseDutyLayout.MAXIMUM_SLOTS_PER_BUILDING) {
                        throw new IllegalArgumentException(
                                "Pack-authored defense-duty slot count is outside its hard bound");
                    }
                    return List.copyOf(slots);
                });
        if (weight < MINIMUM_WEIGHT || weight > MAXIMUM_WEIGHT) {
            throw new IllegalArgumentException(
                    "Village entry weight must be in ["
                            + MINIMUM_WEIGHT + ", " + MAXIMUM_WEIGHT + "]");
        }
    }

    public VillageBuildingPoolEntry(
            ResourceLocation entryId, VillageBuildingType buildingType, ResourceLocation targetPool,
            ResourceLocation template, ResourceLocation processors, StructureTemplatePool.Projection projection,
            int weight, double selectionChance, Optional<List<VillageDefenseDutySlotDefinition>> dutySlots) {
        this(entryId, buildingType, targetPool, template, processors, projection, weight,
                selectionChance, false, dutySlots);
    }

    public VillageBuildingPoolEntry(
            ResourceLocation entryId, VillageBuildingType buildingType, ResourceLocation targetPool,
            ResourceLocation template, ResourceLocation processors, StructureTemplatePool.Projection projection,
            int weight, Optional<List<VillageDefenseDutySlotDefinition>> dutySlots) {
        this(entryId, buildingType, targetPool, template, processors, projection, weight, 1.0, dutySlots);
    }

    public VillageBuildingPoolEntry(
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            ResourceLocation targetPool,
            ResourceLocation template,
            ResourceLocation processors,
            StructureTemplatePool.Projection projection,
            int weight) {
        this(
                entryId,
                buildingType,
                targetPool,
                template,
                processors,
                projection,
                weight,
                Optional.empty());
    }
}
