package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayout;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayouts;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

/** Minimal durable context for one unfinished village-building action. */
public record VillageBuildingPendingActivation(
        Rotation rotation,
        NaturalGuardDifficulty generationDifficulty,
        VillageBuildingAction requiredAction,
        VillageDefenseDutyLayout dutyLayout) {

    public VillageBuildingPendingActivation {
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(generationDifficulty, "generationDifficulty");
        Objects.requireNonNull(requiredAction, "requiredAction");
        Objects.requireNonNull(dutyLayout, "dutyLayout");
        if (requiredAction != VillageBuildingAction.GARRISON
                && requiredAction != VillageBuildingAction.INITIAL_STOCK) {
            throw new IllegalArgumentException(
                    "Pending village-building action must be functional");
        }
        if (dutyLayout.rotation() != rotation) {
            throw new IllegalArgumentException(
                    "Pending village-building duty layout must retain its rotation");
        }
    }

    public static VillageBuildingPendingActivation forBuilding(
            VillageBuildingIdentity identity,
            Rotation rotation,
            NaturalGuardDifficulty generationDifficulty) {
        Objects.requireNonNull(identity, "identity");
        return new VillageBuildingPendingActivation(
                rotation,
                generationDifficulty,
                identity.buildingType() == VillageBuildingType.ARMORY
                        ? VillageBuildingAction.INITIAL_STOCK
                        : VillageBuildingAction.GARRISON,
                VillageDefenseDutyLayouts.forBuilding(identity, rotation));
    }

    public static VillageBuildingPendingActivation forBuilding(
            VillageBuildingIdentity identity,
            Rotation rotation,
            NaturalGuardDifficulty generationDifficulty,
            VillageDefenseDutyLayout dutyLayout) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(dutyLayout, "dutyLayout");
        if (!identity.equals(dutyLayout.building())) {
            throw new IllegalArgumentException(
                    "Pending village-building duty layout belongs to another building");
        }
        return new VillageBuildingPendingActivation(
                rotation,
                generationDifficulty,
                identity.buildingType() == VillageBuildingType.ARMORY
                        ? VillageBuildingAction.INITIAL_STOCK
                        : VillageBuildingAction.GARRISON,
                dutyLayout);
    }

    /**
     * Returns only chunks that may contain the anchor or one of the four
     * hard-bounded guard spawn cells.
     */
    public Set<Long> relevantChunks(VillageBuildingIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        LinkedHashSet<Long> chunks = new LinkedHashSet<>();
        BlockPos anchor = identity.anchorPosition();
        chunks.add(ChunkPos.asLong(anchor));
        if (requiredAction == VillageBuildingAction.GARRISON) {
            for (int memberIndex = 0;
                    memberIndex < VillageBuildingGarrisonPolicy.MAXIMUM_GUARDS_PER_BUILDING;
                    memberIndex++) {
                chunks.add(ChunkPos.asLong(
                        GuardPostSpawner.spawnPosition(anchor, rotation, memberIndex)));
            }
        }
        return Set.copyOf(chunks);
    }
}
