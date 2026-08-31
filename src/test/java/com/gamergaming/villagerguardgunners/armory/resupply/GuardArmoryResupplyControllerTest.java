package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuardArmoryResupplyControllerTest {

    @Test
    void nextAssessmentIsStableStrictlyFutureAndPreservesUuidOffset() {
        UUID guardId = UUID.fromString("46f89b0e-8b6a-4fc5-b592-55d154821c1e");
        int interval = 40;
        long first = GuardArmoryResupplyController.nextStaggeredTick(
                1_000L,
                interval,
                guardId);
        long second = GuardArmoryResupplyController.nextStaggeredTick(
                first,
                interval,
                guardId);
        int expectedOffset = ArmoryResupplyLimits.checkOffset(guardId, interval);

        assertAll(
                () -> assertTrue(first > 1_000L && first <= 1_040L),
                () -> assertEquals(expectedOffset, Math.floorMod(first, interval)),
                () -> assertEquals(interval, second - first),
                () -> assertEquals(expectedOffset, Math.floorMod(second, interval)));
    }

    @Test
    void schedulingSaturatesAtTheEndOfTheGameClock() {
        UUID guardId = UUID.fromString("f0211695-3005-47e2-a4c3-f834c097399e");

        assertEquals(
                Long.MAX_VALUE,
                GuardArmoryResupplyController.nextStaggeredTick(
                        Long.MAX_VALUE,
                        200,
                        guardId));
    }
}
