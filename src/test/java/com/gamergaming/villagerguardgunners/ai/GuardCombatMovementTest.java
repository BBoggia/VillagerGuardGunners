package com.gamergaming.villagerguardgunners.ai;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GuardCombatMovementTest {
    @Test
    void movementStateUsesBoundedTacticalPriorities() {
        assertAll(
                () -> assertEquals(
                        GuardCombatMovement.State.HOLD,
                        select(true, true, 0, 100.0D, 16.0D, 256.0D, true)),
                () -> assertEquals(
                        GuardCombatMovement.State.RETREAT,
                        select(false, true, 0, 16.0D, 16.0D, 256.0D, true)),
                () -> assertEquals(
                        GuardCombatMovement.State.REPOSITION,
                        select(false, true, 0, 100.0D, 16.0D, 256.0D, true)),
                () -> assertEquals(
                        GuardCombatMovement.State.APPROACH,
                        select(false, false, 2, 100.0D, 16.0D, 256.0D, false)),
                () -> assertEquals(
                        GuardCombatMovement.State.APPROACH,
                        select(false, true, 0, 400.0D, 16.0D, 256.0D, false)),
                () -> assertEquals(
                        GuardCombatMovement.State.HOLD,
                        select(false, true, 0, 100.0D, 16.0D, 256.0D, false)));
    }

    @Test
    void closeRangeThresholdIsSuppliedByTheCaller() {
        assertAll(
                () -> assertEquals(
                        GuardCombatMovement.State.HOLD,
                        select(false, true, 0, 25.0D, 16.0D, 256.0D, false)),
                () -> assertEquals(
                        GuardCombatMovement.State.RETREAT,
                        select(false, true, 0, 25.0D, 36.0D, 256.0D, false)));
    }

    @Test
    void expensiveWorkIsDistributedByEntityPhase() {
        assertAll(
                () -> assertEquals(107L, GuardCombatMovement.firstDueTick(100L, 7, 20)),
                () -> assertEquals(107L, GuardCombatMovement.firstDueTick(107L, 7, 20)),
                () -> assertEquals(4L, GuardCombatMovement.firstDueTick(0L, -1, 5)),
                () -> assertEquals(12L, GuardCombatMovement.firstDueTick(12L, 99, 1)));
    }

    @Test
    void lateralRepositioningIsStableAndSplitAcrossGuards() {
        assertAll(
                () -> assertEquals(1.0F, GuardCombatMovement.lateralDirection(8)),
                () -> assertEquals(-1.0F, GuardCombatMovement.lateralDirection(9)),
                () -> assertEquals(-1.0F, GuardCombatMovement.lateralDirection(-1)));
    }

    @Test
    void lateralProgressUsesHorizontalDistanceAndAnInclusiveThreshold() {
        assertFalse(GuardCombatMovement.madeLateralProgress(4.0D, 7.0D, 4.1D, 7.1D, 0.04D));
        assertTrue(GuardCombatMovement.madeLateralProgress(4.0D, 7.0D, 4.2D, 7.0D, 0.04D));
        assertTrue(GuardCombatMovement.madeLateralProgress(4.0D, 7.0D, 4.0D, 7.0D, -1.0D));
    }

    @Test
    void firingRangeIncludesTheBoundaryAndFailsClosedForInvalidValues() {
        assertAll(
                () -> assertTrue(GuardCombatMovement.isWithinEngagementRange(256.0D, 16.0D)),
                () -> assertFalse(GuardCombatMovement.isWithinEngagementRange(256.01D, 16.0D)),
                () -> assertFalse(GuardCombatMovement.isWithinEngagementRange(-1.0D, 16.0D)),
                () -> assertFalse(GuardCombatMovement.isWithinEngagementRange(Double.NaN, 16.0D)),
                () -> assertFalse(GuardCombatMovement.isWithinEngagementRange(16.0D, Double.POSITIVE_INFINITY)),
                () -> assertFalse(GuardCombatMovement.isWithinEngagementRange(16.0D, Double.MAX_VALUE)));
    }

    private static GuardCombatMovement.State select(
            boolean patrolling,
            boolean canSee,
            int unseenSamples,
            double distanceSquared,
            double closeRangeSquared,
            double effectiveRangeSquared,
            boolean friendlyBlocked) {
        return GuardCombatMovement.selectState(
                patrolling,
                canSee,
                unseenSamples,
                2,
                distanceSquared,
                closeRangeSquared,
                effectiveRangeSquared,
                friendlyBlocked);
    }
}
