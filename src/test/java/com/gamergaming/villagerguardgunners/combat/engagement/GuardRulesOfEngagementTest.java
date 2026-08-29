package com.gamergaming.villagerguardgunners.combat.engagement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GuardRulesOfEngagementTest {

    @Test
    void aggressionMemoryIncludesBothBoundaryTicks() {
        assertTrue(EngagementMemory.isRecent(100, 100, 20));
        assertTrue(EngagementMemory.isRecent(100, 80, 20));
        assertFalse(EngagementMemory.isRecent(100, 79, 20));
    }

    @Test
    void futureOrWrappedInvalidTimestampDoesNotAuthorizeFire() {
        assertFalse(EngagementMemory.isRecent(100, 101, 20));
    }

    @Test
    void tickCounterOverflowStillProducesTheCorrectShortAge() {
        assertTrue(EngagementMemory.isRecent(
                Integer.MIN_VALUE + 5,
                Integer.MAX_VALUE - 4,
                10));
    }
}
