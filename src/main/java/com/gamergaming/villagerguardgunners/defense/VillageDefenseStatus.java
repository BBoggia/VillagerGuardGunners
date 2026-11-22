package com.gamergaming.villagerguardgunners.defense;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable persisted readiness state for one village defense network. */
public record VillageDefenseStatus(
        int schemaVersion,
        VillageDefenseState state,
        long stateSinceGameTime,
        long nextReviewGameTime,
        long revision) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final long NO_REVIEW = Long.MAX_VALUE;

    public VillageDefenseStatus {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported defense-status schema");
        }
        state = Objects.requireNonNull(state, "state");
        if (stateSinceGameTime < 0L || revision < 0L) {
            throw new IllegalArgumentException(
                    "Defense-status times and revisions cannot be negative");
        }
        boolean scheduledState = state == VillageDefenseState.WATCH
                || state == VillageDefenseState.ALERT
                || state == VillageDefenseState.RECOVERY;
        if (scheduledState
                ? nextReviewGameTime <= stateSinceGameTime
                        || nextReviewGameTime == NO_REVIEW
                : nextReviewGameTime != NO_REVIEW) {
            throw new IllegalArgumentException(
                    "Defense-status review deadline does not match its state");
        }
    }

    public static VillageDefenseStatus peaceful() {
        return new VillageDefenseStatus(
                CURRENT_SCHEMA_VERSION,
                VillageDefenseState.PEACE,
                0L,
                NO_REVIEW,
                0L);
    }

    public boolean hasScheduledReview() {
        return nextReviewGameTime != NO_REVIEW;
    }

    /**
     * Combines states when two spatial networks merge. The most ready state
     * wins, with the latest compatible deadline so merging cannot shorten an
     * active warning window.
     */
    public static VillageDefenseStatus merge(
            Collection<VillageDefenseStatus> statuses) {
        List<VillageDefenseStatus> normalized = Objects.requireNonNull(
                statuses, "statuses").stream()
                .map(status -> Objects.requireNonNull(status, "status"))
                .toList();
        if (normalized.isEmpty()) {
            return peaceful();
        }
        VillageDefenseState strongest = normalized.stream()
                .map(VillageDefenseStatus::state)
                .max(Comparator.comparingInt(VillageDefenseState::readinessRank))
                .orElse(VillageDefenseState.PEACE);
        List<VillageDefenseStatus> strongestStatuses = normalized.stream()
                .filter(status -> status.state() == strongest)
                .toList();
        long stateSince = strongestStatuses.stream()
                .mapToLong(VillageDefenseStatus::stateSinceGameTime)
                .min()
                .orElse(0L);
        long nextReview = strongestStatuses.stream()
                .mapToLong(VillageDefenseStatus::nextReviewGameTime)
                .max()
                .orElse(NO_REVIEW);
        long revision = normalized.stream()
                .mapToLong(VillageDefenseStatus::revision)
                .max()
                .orElse(0L);
        return new VillageDefenseStatus(
                CURRENT_SCHEMA_VERSION,
                strongest,
                stateSince,
                nextReview,
                incrementRevision(revision));
    }

    static long incrementRevision(long current) {
        return current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L;
    }
}
