package com.gamergaming.villagerguardgunners.defense;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class VillageDefenseStateLifecycleTest {
    private static final VillageDefenseTimings TIMINGS =
            new VillageDefenseTimings(10L, 20L, 5L);

    @Test
    void signalsEscalateAndOnlyMatchingSignalsExtendTheDeadline() {
        VillageDefenseStatus watch = VillageDefenseStateLifecycle.onSignal(
                VillageDefenseStatus.peaceful(),
                VillageDefenseSignal.BELL_RUNG,
                100L,
                TIMINGS);
        VillageDefenseStatus extendedWatch = VillageDefenseStateLifecycle.onSignal(
                watch,
                VillageDefenseSignal.BELL_RUNG,
                105L,
                TIMINGS);
        VillageDefenseStatus alert = VillageDefenseStateLifecycle.onSignal(
                extendedWatch,
                VillageDefenseSignal.HOSTILE_CONFIRMED,
                106L,
                TIMINGS);
        VillageDefenseStatus lowerSignal = VillageDefenseStateLifecycle.onSignal(
                alert,
                VillageDefenseSignal.BELL_RUNG,
                110L,
                TIMINGS);

        assertAll(
                () -> assertEquals(VillageDefenseState.WATCH, watch.state()),
                () -> assertEquals(110L, watch.nextReviewGameTime()),
                () -> assertEquals(115L, extendedWatch.nextReviewGameTime()),
                () -> assertEquals(2L, extendedWatch.revision()),
                () -> assertEquals(VillageDefenseState.ALERT, alert.state()),
                () -> assertEquals(126L, alert.nextReviewGameTime()),
                () -> assertSame(alert, lowerSignal));
    }

    @Test
    void restartCatchUpUsesPersistedDeadlinesWithoutGrantingExtraTime() {
        VillageDefenseStatus alert = VillageDefenseStateLifecycle.onSignal(
                VillageDefenseStatus.peaceful(),
                VillageDefenseSignal.HOSTILE_CONFIRMED,
                100L,
                TIMINGS);

        VillageDefenseStatus caughtUp = VillageDefenseStateLifecycle.advanceExpired(
                alert, 200L, TIMINGS);

        assertAll(
                () -> assertEquals(VillageDefenseState.PEACE, caughtUp.state()),
                () -> assertEquals(125L, caughtUp.stateSinceGameTime()),
                () -> assertEquals(VillageDefenseStatus.NO_REVIEW,
                        caughtUp.nextReviewGameTime()),
                () -> assertEquals(3L, caughtUp.revision()));
    }

    @Test
    void siegeRequiresAnExplicitRaidEndBeforeRecoveryCanBegin() {
        VillageDefenseStatus siege = VillageDefenseStateLifecycle.onSignal(
                VillageDefenseStatus.peaceful(),
                VillageDefenseSignal.RAID_ACTIVE,
                50L,
                TIMINGS);
        VillageDefenseStatus stillSiege = VillageDefenseStateLifecycle.advanceExpired(
                siege, 10_000L, TIMINGS);
        VillageDefenseStatus recovery = VillageDefenseStateLifecycle.onRaidEnded(
                siege, 10_000L, false, TIMINGS);

        assertAll(
                () -> assertSame(siege, stillSiege),
                () -> assertEquals(VillageDefenseState.SIEGE, siege.state()),
                () -> assertEquals(VillageDefenseStatus.NO_REVIEW,
                        siege.nextReviewGameTime()),
                () -> assertEquals(VillageDefenseState.RECOVERY, recovery.state()),
                () -> assertEquals(10_005L, recovery.nextReviewGameTime()));
    }

    @Test
    void aNewSignalFirstCatchesUpAnOverdueState() {
        VillageDefenseStatus alert = VillageDefenseStateLifecycle.onSignal(
                VillageDefenseStatus.peaceful(),
                VillageDefenseSignal.HOSTILE_CONFIRMED,
                100L,
                TIMINGS);

        VillageDefenseStatus watch = VillageDefenseStateLifecycle.onSignal(
                alert,
                VillageDefenseSignal.BELL_RUNG,
                200L,
                TIMINGS);

        assertAll(
                () -> assertEquals(VillageDefenseState.WATCH, watch.state()),
                () -> assertEquals(200L, watch.stateSinceGameTime()),
                () -> assertEquals(210L, watch.nextReviewGameTime()),
                () -> assertEquals(4L, watch.revision()));
    }

    @Test
    void dueReviewsRefreshActiveFactsInsteadOfDecaying() {
        VillageDefenseStatus alert = VillageDefenseStateLifecycle.onSignal(
                VillageDefenseStatus.peaceful(),
                VillageDefenseSignal.HOSTILE_CONFIRMED,
                100L,
                TIMINGS);

        VillageDefenseStatus maintained = VillageDefenseStateLifecycle.onReview(
                alert,
                new VillageDefenseReviewFacts(false, true),
                120L,
                TIMINGS);
        VillageDefenseStatus siege = VillageDefenseStateLifecycle.onReview(
                maintained,
                new VillageDefenseReviewFacts(true, true),
                121L,
                TIMINGS);

        assertAll(
                () -> assertEquals(VillageDefenseState.ALERT, maintained.state()),
                () -> assertEquals(140L, maintained.nextReviewGameTime()),
                () -> assertEquals(VillageDefenseState.SIEGE, siege.state()),
                () -> assertEquals(VillageDefenseStatus.NO_REVIEW,
                        siege.nextReviewGameTime()));
    }

    @Test
    void staleEventsAndInvalidTimingBoundsFailClosed() {
        VillageDefenseStatus watch = VillageDefenseStateLifecycle.onSignal(
                VillageDefenseStatus.peaceful(),
                VillageDefenseSignal.BELL_RUNG,
                100L,
                TIMINGS);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> VillageDefenseStateLifecycle.onSignal(
                                watch,
                                VillageDefenseSignal.HOSTILE_CONFIRMED,
                                99L,
                                TIMINGS)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new VillageDefenseTimings(0L, 20L, 5L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new VillageDefenseTimings(
                                10L,
                                VillageDefenseTimings.MAXIMUM_DURATION_TICKS + 1L,
                                5L)));
    }
}
