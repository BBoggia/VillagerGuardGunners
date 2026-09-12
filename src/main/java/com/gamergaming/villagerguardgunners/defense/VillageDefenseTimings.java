package com.gamergaming.villagerguardgunners.defense;

import java.util.Objects;

/**
 * Validated quiet-time durations for scheduled defense-state reviews.
 *
 * <p>These defaults are deliberately centralized so the later configuration
 * step can replace them without changing the persistence or transition
 * contracts.</p>
 */
public record VillageDefenseTimings(
        long watchTicks,
        long alertTicks,
        long recoveryTicks) {
    public static final long MAXIMUM_DURATION_TICKS = 1_728_000L;
    public static final VillageDefenseTimings DEFAULT = new VillageDefenseTimings(
            600L,
            1_200L,
            600L);

    public VillageDefenseTimings {
        validateDuration(watchTicks, "watchTicks");
        validateDuration(alertTicks, "alertTicks");
        validateDuration(recoveryTicks, "recoveryTicks");
    }

    public long durationFor(VillageDefenseState state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case WATCH -> watchTicks;
            case ALERT -> alertTicks;
            case RECOVERY -> recoveryTicks;
            case PEACE, SIEGE -> 0L;
        };
    }

    public long deadlineFrom(long gameTime, VillageDefenseState state) {
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Game time cannot be negative");
        }
        long duration = durationFor(state);
        if (duration == 0L) {
            return VillageDefenseStatus.NO_REVIEW;
        }
        if (gameTime >= VillageDefenseStatus.NO_REVIEW - duration) {
            throw new IllegalArgumentException(
                    "Defense review deadline cannot be represented");
        }
        return gameTime + duration;
    }

    private static void validateDuration(long duration, String name) {
        if (duration <= 0L || duration > MAXIMUM_DURATION_TICKS) {
            throw new IllegalArgumentException(
                    name + " must be within the defense timing hard bounds");
        }
    }
}
