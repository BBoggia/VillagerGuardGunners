package com.gamergaming.villagerguardgunners.defense;

import java.util.Objects;

/** Pure deadline and transition logic for a persisted defense status. */
public final class VillageDefenseStateLifecycle {
    private VillageDefenseStateLifecycle() {
    }

    public static VillageDefenseStatus onSignal(
            VillageDefenseStatus current,
            VillageDefenseSignal signal,
            long gameTime,
            VillageDefenseTimings timings) {
        VillageDefenseSignal normalizedSignal = Objects.requireNonNull(signal, "signal");
        VillageDefenseTimings normalizedTimings = Objects.requireNonNull(
                timings, "timings");
        VillageDefenseStatus status = advanceExpired(
                requireCurrentTime(current, gameTime),
                gameTime,
                normalizedTimings);
        return applySignal(status, normalizedSignal, gameTime, normalizedTimings);
    }

    /**
     * Rechecks bounded runtime facts before a quiet deadline de-escalates the
     * network. An active fact refreshes readiness directly; only a genuinely
     * quiet review advances expired state.
     */
    public static VillageDefenseStatus onReview(
            VillageDefenseStatus current,
            VillageDefenseReviewFacts facts,
            long gameTime,
            VillageDefenseTimings timings) {
        VillageDefenseStatus status = requireCurrentTime(current, gameTime);
        VillageDefenseReviewFacts normalizedFacts = Objects.requireNonNull(
                facts, "facts");
        VillageDefenseTimings normalizedTimings = Objects.requireNonNull(
                timings, "timings");
        if (normalizedFacts.raidActive()) {
            return applySignal(
                    status,
                    VillageDefenseSignal.RAID_ACTIVE,
                    gameTime,
                    normalizedTimings);
        }
        if (normalizedFacts.confirmedThreatActive()) {
            return applySignal(
                    status,
                    VillageDefenseSignal.HOSTILE_CONFIRMED,
                    gameTime,
                    normalizedTimings);
        }
        return advanceExpired(status, gameTime, normalizedTimings);
    }

    private static VillageDefenseStatus applySignal(
            VillageDefenseStatus status,
            VillageDefenseSignal signal,
            long gameTime,
            VillageDefenseTimings timings) {
        VillageDefenseState requested = VillageDefenseTransitionPolicy.onSignal(
                status.state(), signal);
        if (requested != status.state()) {
            return transition(status, requested, gameTime, timings);
        }
        if (signal.minimumState() != status.state()
                || !status.hasScheduledReview()) {
            return status;
        }
        long extendedDeadline = Math.max(
                status.nextReviewGameTime(),
                timings.deadlineFrom(gameTime, status.state()));
        if (extendedDeadline == status.nextReviewGameTime()) {
            return status;
        }
        return new VillageDefenseStatus(
                VillageDefenseStatus.CURRENT_SCHEMA_VERSION,
                status.state(),
                status.stateSinceGameTime(),
                extendedDeadline,
                VillageDefenseStatus.incrementRevision(status.revision()));
    }

    public static VillageDefenseStatus onRaidEnded(
            VillageDefenseStatus current,
            long gameTime,
            boolean confirmedThreatActive,
            VillageDefenseTimings timings) {
        VillageDefenseTimings normalizedTimings = Objects.requireNonNull(
                timings, "timings");
        VillageDefenseStatus status = advanceExpired(
                requireCurrentTime(current, gameTime),
                gameTime,
                normalizedTimings);
        VillageDefenseState requested = VillageDefenseTransitionPolicy.onRaidEnded(
                status.state(), confirmedThreatActive);
        return requested == status.state()
                ? status
                : transition(
                        status,
                        requested,
                        gameTime,
                        normalizedTimings);
    }

    /**
     * Applies every deadline that elapsed by {@code gameTime}. Starting later
     * states at the prior deadline makes restart catch-up independent of how
     * long the server waited before loading the dimension.
     */
    public static VillageDefenseStatus advanceExpired(
            VillageDefenseStatus current,
            long gameTime,
            VillageDefenseTimings timings) {
        VillageDefenseStatus status = requireCurrentTime(current, gameTime);
        VillageDefenseTimings normalizedTimings = Objects.requireNonNull(
                timings, "timings");
        int transitions = 0;
        while (status.hasScheduledReview()
                && status.nextReviewGameTime() <= gameTime) {
            long transitionTime = status.nextReviewGameTime();
            VillageDefenseState next = switch (status.state()) {
                case WATCH, RECOVERY -> VillageDefenseState.PEACE;
                case ALERT -> VillageDefenseState.RECOVERY;
                case PEACE, SIEGE -> status.state();
            };
            if (next == status.state()) {
                break;
            }
            status = transition(status, next, transitionTime, normalizedTimings);
            if (++transitions > 2) {
                throw new IllegalStateException(
                        "Defense-state deadline transition did not converge");
            }
        }
        return status;
    }

    private static VillageDefenseStatus transition(
            VillageDefenseStatus current,
            VillageDefenseState requested,
            long gameTime,
            VillageDefenseTimings timings) {
        return new VillageDefenseStatus(
                VillageDefenseStatus.CURRENT_SCHEMA_VERSION,
                requested,
                gameTime,
                timings.deadlineFrom(gameTime, requested),
                VillageDefenseStatus.incrementRevision(current.revision()));
    }

    private static VillageDefenseStatus requireCurrentTime(
            VillageDefenseStatus current,
            long gameTime) {
        VillageDefenseStatus status = Objects.requireNonNull(current, "current");
        if (gameTime < status.stateSinceGameTime()) {
            throw new IllegalArgumentException(
                    "Defense event predates the persisted state");
        }
        return status;
    }
}
