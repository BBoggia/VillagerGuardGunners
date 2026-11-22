package com.gamergaming.villagerguardgunners.ai.navigation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GuardDetourPathPolicyTest {
    @Test void retryKeepsTheOriginalRegionForNormalAndEntityRequests() {
        for (double range : new double[]{4, 8.3, 16, 20, 24.5, 39.9}) {
            for (int padding : new int[]{8, 16}) {
                int retry = GuardDetourPathPolicy.retryRange(range);
                assertTrue(retry > range && retry <= 40);
                assertEquals((int)((float)range + padding),
                        retry + GuardDetourPathPolicy.retryPadding(range, padding));
            }
        }
        assertEquals(40, GuardDetourPathPolicy.retryRange(20));
        assertEquals(-12, GuardDetourPathPolicy.retryPadding(20, 8));
    }
    @Test void excludesLevelTravelDistantDestinationsAndAlreadyLargeRanges() {
        assertTrue(GuardDetourPathPolicy.eligible(20, 576, 8));
        assertFalse(GuardDetourPathPolicy.eligible(20, 576.01, 5));
        for (long dy : new long[]{0,1,9,Long.MAX_VALUE})
            assertFalse(GuardDetourPathPolicy.eligible(20,25,dy));
        for (double range : new double[]{-1,0,3.99,40,64,Double.NaN,Double.POSITIVE_INFINITY}) {
            assertFalse(GuardDetourPathPolicy.eligible(range,25,5));
            assertThrows(IllegalArgumentException.class, ()->GuardDetourPathPolicy.retryRange(range));
        }
        assertFalse(GuardDetourPathPolicy.eligible(20,Double.NaN,5));
    }
    @Test void changedTargetsShareTheSamePerGuardBudget() {
        var budget = new GuardDetourPathPolicy();
        assertTrue(budget.tryAcquire(100));
        for (int t=100;t<120;t++) assertFalse(budget.tryAcquire(t));
        assertTrue(budget.tryAcquire(120));
        assertFalse(budget.tryAcquire(120));
        assertTrue(budget.tryAcquire(10)); // A game-time rollback must not freeze future retries.
        assertFalse(budget.tryAcquire(10));
        assertTrue(budget.tryAcquire(Long.MAX_VALUE));
    }
}
