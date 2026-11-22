package com.gamergaming.villagerguardgunners.defense.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VillageDefenseNetworkTest {
    private static final ResourceLocation OVERWORLD = id("minecraft", "overworld");
    private static final ResourceLocation NETHER = id("minecraft", "the_nether");

    @Test
    void membersAreSortedAndIdempotentWithoutChangingTheStableNetworkId() {
        VillageBuildingIdentity first = building(1, 0, OVERWORLD);
        VillageBuildingIdentity second = building(2, 10, OVERWORLD);
        UUID networkId = VillageDefenseNetworkIds.forBuilding(second.buildingId());
        VillageDefenseNetwork network = new VillageDefenseNetwork(
                VillageDefenseNetwork.CURRENT_SCHEMA_VERSION,
                networkId,
                OVERWORLD,
                List.of(second, first));

        assertEquals(
                List.of(first, second).stream()
                        .sorted(java.util.Comparator.comparing(
                                VillageBuildingIdentity::buildingId))
                        .toList(),
                network.buildings());
        assertEquals(network, network.withBuilding(first));
        assertEquals(networkId, network.networkId());
    }

    @Test
    void malformedIdentityNetworkAndAnchorCombinationsAreRejected() {
        VillageBuildingIdentity first = building(1, 0, OVERWORLD);
        VillageBuildingIdentity sameAnchor = building(2, 0, OVERWORLD);
        VillageBuildingIdentity crossDimension = building(3, 10, NETHER);

        assertThrows(IllegalArgumentException.class, () -> new VillageDefenseNetwork(
                VillageDefenseNetwork.CURRENT_SCHEMA_VERSION,
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                OVERWORLD,
                List.of(first)));
        assertThrows(IllegalArgumentException.class, () -> new VillageDefenseNetwork(
                VillageDefenseNetwork.CURRENT_SCHEMA_VERSION,
                VillageDefenseNetworkIds.forBuilding(first.buildingId()),
                OVERWORLD,
                List.of(first, sameAnchor)));
        assertThrows(IllegalArgumentException.class, () -> new VillageDefenseNetwork(
                VillageDefenseNetwork.CURRENT_SCHEMA_VERSION,
                VillageDefenseNetworkIds.forBuilding(first.buildingId()),
                OVERWORLD,
                List.of(first, crossDimension)));
    }

    private static VillageBuildingIdentity building(
            int suffix,
            int x,
            ResourceLocation dimension) {
        return new VillageBuildingIdentity(
                VillageBuildingIdentity.CURRENT_SCHEMA_VERSION,
                UUID.fromString("10000000-0000-0000-0000-00000000000" + suffix),
                id("example", "building_" + suffix),
                VillageBuildingType.SENTRY_POST,
                dimension,
                new BlockPos(x, 64, 0));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
