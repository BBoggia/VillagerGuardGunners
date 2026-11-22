package com.gamergaming.villagerguardgunners.defense.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VillageDefenseNetworkIdsTest {
    @Test
    void buildingDerivedNetworkIdsAreStableAndDomainSeparated() {
        UUID building = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID other = UUID.fromString("10000000-0000-0000-0000-000000000002");

        assertEquals(
                VillageDefenseNetworkIds.forBuilding(building),
                VillageDefenseNetworkIds.forBuilding(building));
        assertNotEquals(building, VillageDefenseNetworkIds.forBuilding(building));
        assertNotEquals(
                VillageDefenseNetworkIds.forBuilding(building),
                VillageDefenseNetworkIds.forBuilding(other));
    }
}
