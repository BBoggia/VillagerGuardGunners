package com.gamergaming.villagerguardgunners.defense.network;

import static org.junit.jupiter.api.Assertions.*;
import com.gamergaming.villagerguardgunners.village.*;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class VillageAssociationTest {
    @Test void networkMergeAliasesNeverMergeSettlementIdentityOrBuildingAffiliation() {
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var villages = new VillageRegistrySavedData(dimension);
        var networks = new VillageDefenseNetworkSavedData(dimension, 42L);
        var left = building(dimension, 0);
        var right = building(dimension, 160);
        var a = new VillageIdentity(dimension, ResourceLocation.parse("minecraft:village_plains"), ChunkPos.asLong(0, 0));
        var b = new VillageIdentity(dimension, ResourceLocation.parse("minecraft:village_plains"), ChunkPos.asLong(10, 0));
        villages.observe(a, footprint(0)); villages.observe(b, footprint(160));
        villages.associateBuilding(left.buildingId(), a.id());
        villages.associateBuilding(right.buildingId(), b.id());
        var leftNetwork = networks.claimBuilding(left).networkId();
        var rightNetwork = networks.claimBuilding(right).networkId();
        assertNotEquals(leftNetwork, rightNetwork);
        networks.claimBuilding(building(dimension, 80));
        var first = VillageDefenseAssociation.resolve(villages, networks, left.buildingId());
        var second = VillageDefenseAssociation.resolve(villages, networks, right.buildingId());
        assertEquals(first.readinessNetwork(), second.readinessNetwork());
        assertEquals(first.readinessNetwork(), networks.resolveNetworkId(leftNetwork));
        assertEquals(first.readinessNetwork(), networks.resolveNetworkId(rightNetwork));
        assertNotEquals(first.village(), second.village());
        var restored = VillageRegistrySavedData.load(villages.save(new net.minecraft.nbt.CompoundTag()), dimension);
        assertEquals(first, VillageDefenseAssociation.resolve(restored, networks, left.buildingId()));
        assertEquals(2, restored.size());
    }
    private static VillageBuildingIdentity building(ResourceLocation dimension, int x) {
        return VillageBuildingIdentity.anchored(42L, dimension, ResourceLocation.parse("test:post_" + x),
                VillageBuildingType.SENTRY_POST, new BlockPos(x, 64, 0));
    }
    private static VillageFootprint footprint(int x) {
        return VillageFootprint.of(List.of(new VillageFootprint.Piece(new BlockPos(x, 64, 0), "NONE",
                ResourceLocation.parse("test:house"), new VillageFootprint.Bounds(x, 60, 0, x + 31, 80, 31))));
    }
}
