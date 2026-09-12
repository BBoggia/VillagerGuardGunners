package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VillageBuildingTerrainSafetyTest {
    @Test void checksNineDistinctLocationsIncludingOutsideEdges() {
        var locations = new HashSet<String>();
        assertTrue(VillageBuildingTerrainSafety.allows(-12, -6, 4, 10, 64, (x, z) -> {
            assertTrue(locations.add(x + "," + z));
            return new VillageBuildingTerrainSafety.Column(64, 64);
        }));
        assertEquals(9, locations.size());
        assertTrue(locations.contains("-13,-7"));
        assertTrue(locations.contains("5,11"));
    }

    @Test void rejectsWaterAboveFloorEvenWhenTheLakeBedIsLower() {
        assertFalse(VillageBuildingTerrainSafety.allows(0, 0, 14, 14, 51,
                (x, z) -> new VillageBuildingTerrainSafety.Column(63, 45)));
        assertFalse(VillageBuildingTerrainSafety.allows(0, 0, 14, 14, 63,
                (x, z) -> new VillageBuildingTerrainSafety.Column(64, 62)));
        assertTrue(VillageBuildingTerrainSafety.allows(0, 0, 14, 14, 64,
                (x, z) -> new VillageBuildingTerrainSafety.Column(64, 62)));
    }

    @Test void allowsModestSlopesButRejectsDeepExcavation() {
        assertTrue(VillageBuildingTerrainSafety.allows(0, 0, 14, 14, 64,
                (x, z) -> new VillageBuildingTerrainSafety.Column(68, 68)));
        assertFalse(VillageBuildingTerrainSafety.allows(0, 0, 14, 14, 64,
                (x, z) -> new VillageBuildingTerrainSafety.Column(69, 69)));
    }
}
