package com.gamergaming.villagerguardgunners.armory.resupply;

import java.util.UUID;

/** Hard safety ceilings and provisional defaults fixed by the Step 1 contract. */
public final class ArmoryResupplyLimits {
    public static final int TICKS_PER_SECOND = 20;
    public static final int DEFAULT_CHECK_INTERVAL_TICKS = 2 * TICKS_PER_SECOND;
    public static final int DEFAULT_REQUEST_LIFETIME_TICKS = 60 * TICKS_PER_SECOND;
    public static final int HARD_MAX_REQUEST_LIFETIME_TICKS = 2 * 60 * TICKS_PER_SECOND;
    public static final int DEFAULT_SUCCESS_COOLDOWN_TICKS = 5 * TICKS_PER_SECOND;
    public static final int DEFAULT_NO_SUPPLY_RETRY_TICKS = 10 * TICKS_PER_SECOND;
    public static final int DEFAULT_UNREACHABLE_RETRY_TICKS = 10 * TICKS_PER_SECOND;
    public static final int HARD_MAX_RETRY_TICKS = 2 * 60 * TICKS_PER_SECOND;
    public static final int DEFAULT_LOW_RESERVE_MAGAZINES = 1;
    public static final int DEFAULT_TARGET_RESERVE_MAGAZINES = 3;
    public static final int HARD_MAX_RESERVE_MAGAZINES = 64;
    public static final long HARD_MAX_REQUESTED_UNITS = 65_536L;

    private ArmoryResupplyLimits() {
    }

    /** Stable staggering prevents every guard from assessing need on one tick. */
    public static int checkOffset(UUID guardId, int intervalTicks) {
        if (guardId == null) {
            throw new NullPointerException("guardId");
        }
        if (intervalTicks <= 0) {
            throw new IllegalArgumentException("intervalTicks must be positive");
        }
        return Math.floorMod(guardId.hashCode(), intervalTicks);
    }

    /**
     * Exponential path-failure backoff with a hard ceiling. Failure one uses
     * the base delay; invalid inputs are rejected instead of producing a retry
     * storm.
     */
    public static int unreachableRetryDelay(int consecutiveFailures, int baseTicks) {
        if (consecutiveFailures <= 0) {
            throw new IllegalArgumentException("consecutiveFailures must be positive");
        }
        if (baseTicks <= 0 || baseTicks > HARD_MAX_RETRY_TICKS) {
            throw new IllegalArgumentException("baseTicks is outside the hard retry bounds");
        }
        long multiplier = 1L << Math.min(30, consecutiveFailures - 1);
        long delay = Math.min((long) HARD_MAX_RETRY_TICKS, multiplier * baseTicks);
        return (int) delay;
    }

    public static long retryAt(long gameTick, int delayTicks) {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must not be negative");
        }
        if (delayTicks <= 0 || delayTicks > HARD_MAX_RETRY_TICKS) {
            throw new IllegalArgumentException("delayTicks is outside the hard retry bounds");
        }
        return gameTick > Long.MAX_VALUE - delayTicks
                ? Long.MAX_VALUE
                : gameTick + delayTicks;
    }
}
