package com.gamergaming.villagerguardgunners.worldgen.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

final class WatchtowerPlacementTest {
    @Test
    void acceptsDryBoundedTerrainAtTheLowestSample() {
        int[] surface = {64, 65, 64, 64, 66, 65, 65, 64, 64};

        assertEquals(
                OptionalInt.of(64),
                WatchtowerPlacement.acceptedGroundY(surface, surface.clone(), -64, 320));
    }

    @Test
    void rejectsWaterAtAnySample() {
        int[] surface = {64, 64, 64, 64, 64, 64, 64, 64, 64};
        int[] oceanFloor = surface.clone();
        oceanFloor[7] = 61;

        assertTrue(WatchtowerPlacement.acceptedGroundY(
                surface, oceanFloor, -64, 320).isEmpty());

        boolean[] drySupport = {
            true, true, true,
            true, true, true,
            true, false, true
        };
        assertTrue(WatchtowerPlacement.acceptedGroundY(
                surface, surface.clone(), drySupport, -64, 320).isEmpty());
    }

    @Test
    void rejectsTerrainBeyondTheSlopeBudget() {
        int[] surface = {64, 64, 64, 64, 67, 64, 64, 64, 64};

        assertTrue(WatchtowerPlacement.acceptedGroundY(
                surface, surface.clone(), -64, 320).isEmpty());
    }

    @Test
    void rejectsTemplatesOutsideTheBuildHeight() {
        int[] low = {-65, -65, -65, -65, -65, -65, -65, -65, -65};
        int topStart = 320 - WatchtowerPlacement.TEMPLATE_HEIGHT + 1;
        int[] high = {
            topStart, topStart, topStart,
            topStart, topStart, topStart,
            topStart, topStart, topStart
        };

        assertTrue(WatchtowerPlacement.acceptedGroundY(
                low, low.clone(), -64, 320).isEmpty());
        assertTrue(WatchtowerPlacement.acceptedGroundY(
                high, high.clone(), -64, 320).isEmpty());
    }

    @Test
    void rejectsIncompleteOrOversizedSampleSets() {
        assertTrue(WatchtowerPlacement.acceptedGroundY(
                new int[8], new int[8], -64, 320).isEmpty());
        assertTrue(WatchtowerPlacement.acceptedGroundY(
                new int[10], new int[10], -64, 320).isEmpty());
    }
}
