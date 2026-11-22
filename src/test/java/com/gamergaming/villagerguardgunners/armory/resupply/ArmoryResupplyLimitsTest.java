package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ArmoryResupplyLimitsTest {
    @Test
    void reservationAndWithdrawalBoundsAreFiniteAndTighterThanRequests() {
        assertTrue(ArmoryResupplyLimits.DEFAULT_RESERVATION_LIFETIME_TICKS > 0);
        assertTrue(ArmoryResupplyLimits.DEFAULT_RESERVATION_LIFETIME_TICKS
                <= ArmoryResupplyLimits.HARD_MAX_RESERVATION_LIFETIME_TICKS);
        assertTrue(ArmoryResupplyLimits.HARD_MAX_RESERVATION_LIFETIME_TICKS
                <= ArmoryResupplyLimits.HARD_MAX_REQUEST_LIFETIME_TICKS);
        assertEquals(1_024, ArmoryResupplyLimits.HARD_MAX_ACTIVE_RESERVATIONS);
        assertEquals(16.0D, ArmoryResupplyLimits.HARD_MAX_WITHDRAWAL_DISTANCE_SQUARED);
        assertEquals(8, ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS);
        assertEquals(4, ArmoryResupplyLimits.HARD_MAX_NATIVE_PATH_RESUMES);
        assertTrue(ArmoryResupplyLimits.DEFAULT_TRIP_REVALIDATION_TICKS > 0);
    }

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
    void movementRetryBackoffCapsSoAllApproachesFitTheReservationWindow() {
        assertEquals(10, ArmoryResupplyLimits.movementPathRetryDelay(1));
        assertEquals(20, ArmoryResupplyLimits.movementPathRetryDelay(2));
        assertEquals(40, ArmoryResupplyLimits.movementPathRetryDelay(3));
        assertEquals(40, ArmoryResupplyLimits.movementPathRetryDelay(8));
        assertThrows(
                IllegalArgumentException.class,
                () -> ArmoryResupplyLimits.movementPathRetryDelay(0));
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
