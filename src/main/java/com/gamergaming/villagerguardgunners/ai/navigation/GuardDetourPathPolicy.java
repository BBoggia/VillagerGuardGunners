package com.gamergaming.villagerguardgunners.ai.navigation;

/** Limits a second walking search without changing detection range or its search region. */
public final class GuardDetourPathPolicy {
    public static final int MAX_ROUTE_RANGE = 40;
    public static final int RETRY_INTERVAL_TICKS = 20;
    private static final double MAX_DESTINATION_DISTANCE_SQUARED = 24 * 24;
    private boolean attempted;
    private long lastAttempt;

    public static boolean eligible(double range, double distanceSquared, long verticalDifference) {
        return Double.isFinite(range) && range >= 4 && range < MAX_ROUTE_RANGE
                && Double.isFinite(distanceSquared) && distanceSquared >= 0
                && distanceSquared <= MAX_DESTINATION_DISTANCE_SQUARED
                && verticalDifference >= 2 && verticalDifference <= 8;
    }

    public static int retryRange(double range) {
        if (!Double.isFinite(range) || range < 4 || range >= MAX_ROUTE_RANGE) {
            throw new IllegalArgumentException("Range must be finite and between 4 and 40, excluding 40");
        }
        return Math.min(MAX_ROUTE_RANGE, (int) Math.ceil(range * 2));
    }

    public static int retryPadding(double range, int originalPadding) {
        // PathNavigation adds range + padding to construct its cubic region.
        // A negative padding is intentional: only route length increases.
        return (int) ((float) range + originalPadding) - retryRange(range);
    }

    public boolean tryAcquire(long gameTime) {
        if (attempted && gameTime >= lastAttempt
                && gameTime - lastAttempt >= 0 && gameTime - lastAttempt < RETRY_INTERVAL_TICKS) {
            return false;
        }
        attempted = true;
        lastAttempt = gameTime;
        return true;
    }
}
