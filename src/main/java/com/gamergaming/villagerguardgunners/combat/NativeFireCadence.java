package com.gamergaming.villagerguardgunners.combat;

/**
 * Allocation-free timestamp scheduler for TaCZ trigger calls.
 *
 * <p>TaCZ validates every supplied timestamp and remains authoritative. This
 * scheduler only preserves sub-tick automatic cadence. It retains at most one
 * healthy server tick of timing debt, preventing a stalled tick from producing
 * a catch-up burst after the server recovers.</p>
 */
public final class NativeFireCadence {
    public static final long SERVER_TICK_MILLIS = 50L;
    public static final long TACZ_COOLDOWN_TOLERANCE_MILLIS = 5L;
    public static final int MAX_SHOTS_PER_TICK = 4;

    private static final long UNSET_TIMESTAMP = Long.MIN_VALUE;

    private long nextShotTimestamp = UNSET_TIMESTAMP;
    private long currentTimestamp;
    private long intervalMillis;
    private int maximumShots;
    private int successfulShots;

    public void beginTick(long nowTimestamp, long shootIntervalMillis, int shotLimit) {
        currentTimestamp = nowTimestamp;
        intervalMillis = shootIntervalMillis;
        maximumShots = Math.max(0, Math.min(MAX_SHOTS_PER_TICK, shotLimit));
        successfulShots = 0;

        boolean clockMovedBackwards = nextShotTimestamp > nowTimestamp + SERVER_TICK_MILLIS;
        boolean accumulatedBacklog = nextShotTimestamp < nowTimestamp - SERVER_TICK_MILLIS;
        if (nextShotTimestamp == UNSET_TIMESTAMP || clockMovedBackwards || accumulatedBacklog) {
            nextShotTimestamp = nowTimestamp;
        }
    }

    public boolean hasShotDue() {
        return intervalMillis > 0L
                && successfulShots < maximumShots
                && nextShotTimestamp <= currentTimestamp + TACZ_COOLDOWN_TOLERANCE_MILLIS;
    }

    public long getDueTimestamp() {
        return nextShotTimestamp;
    }

    public void recordSuccessfulShot() {
        if (!hasShotDue()) {
            return;
        }
        successfulShots++;
        nextShotTimestamp = saturatedAdd(nextShotTimestamp, intervalMillis);
    }

    public void reset() {
        nextShotTimestamp = UNSET_TIMESTAMP;
        successfulShots = 0;
    }

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }
}
