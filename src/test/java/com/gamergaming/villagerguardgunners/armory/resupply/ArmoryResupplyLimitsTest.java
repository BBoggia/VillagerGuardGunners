package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ArmoryResupplyLimitsTest {
    @Test
    void stableOffsetSpreadsChecksInsideTheConfiguredInterval() {
        UUID guard = UUID.fromString("ffffffff-ffff-ffff-8000-000000000000");
        int offset = ArmoryResupplyLimits.checkOffset(guard, 40);

        assertEquals(offset, ArmoryResupplyLimits.checkOffset(guard, 40));
        assertTrue(offset >= 0 && offset < 40);
        assertThrows(
                IllegalArgumentException.class,
                () -> ArmoryResupplyLimits.checkOffset(guard, 0));
    }

    @Test
    void unreachableBackoffDoublesAndStopsAtTheHardCeiling() {
        assertEquals(200, ArmoryResupplyLimits.unreachableRetryDelay(1, 200));
        assertEquals(400, ArmoryResupplyLimits.unreachableRetryDelay(2, 200));
        assertEquals(800, ArmoryResupplyLimits.unreachableRetryDelay(3, 200));
        assertEquals(
                ArmoryResupplyLimits.HARD_MAX_RETRY_TICKS,
                ArmoryResupplyLimits.unreachableRetryDelay(100, 200));
        assertThrows(
                IllegalArgumentException.class,
                () -> ArmoryResupplyLimits.unreachableRetryDelay(0, 200));
    }

    @Test
    void retryTimeSaturatesInsteadOfWrappingIntoAnImmediateRetry() {
        assertEquals(1_200L, ArmoryResupplyLimits.retryAt(1_000L, 200));
        assertEquals(
                Long.MAX_VALUE,
                ArmoryResupplyLimits.retryAt(Long.MAX_VALUE - 5L, 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> ArmoryResupplyLimits.retryAt(-1L, 10));
    }
}
