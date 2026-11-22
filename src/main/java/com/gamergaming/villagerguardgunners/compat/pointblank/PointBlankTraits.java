package com.gamergaming.villagerguardgunners.compat.pointblank;

/**
 * Turns Point Blank's numbers into the terms guard tactics and shot safety
 * use. No game classes, so the rules are unit tested directly.
 */
final class PointBlankTraits {
    /** Farthest a guard's hit scan reaches. The pre-fire ally check covers all of it. */
    static final double MAX_HIT_SCAN_RANGE = 96.0D;
    static final double MIN_HIT_SCAN_RANGE = 8.0D;
    /** Widest the checked cone may grow, leaving room for clearance inside the safety envelope's limit. */
    static final double MAX_CONE_RADIUS = 24.0D;
    static final int DEFAULT_RELOAD_TICKS = 40;
    static final int MAX_RELOAD_TICKS = 400;

    // Vanilla projectile spread: each direction component moves by at most this much per unit of inaccuracy.
    private static final double VANILLA_SPREAD_COMPONENT_SCALE = 0.0172275D;

    private PointBlankTraits() {
    }

    /** The mod has no weapon classes, so one is inferred from what the gun does. */
    static String inferType(int pellets, int capacity, int rpm, boolean automatic, boolean singleOnly, float damage) {
        if (pellets > 1) {
            return "shotgun";
        }
        if (capacity >= 75) {
            return "mg";
        }
        if (singleOnly && rpm <= 200) {
            return "sniper";
        }
        if (singleOnly && capacity <= 21) {
            return damage >= 9.0F && capacity >= 10 ? "dmr" : "pistol";
        }
        if (automatic && (damage <= 4.0F || capacity <= 20)) {
            return "smg";
        }
        return "rifle";
    }

    /**
     * The mod moves each component of a hit scan's direction by up to its
     * inaccuracy, exactly as vanilla does per unit of projectile inaccuracy.
     * This returns the vanilla value with the same cone.
     */
    static double safetyInaccuracy(double inaccuracy) {
        if (!Double.isFinite(inaccuracy) || inaccuracy <= 0.0D) {
            return 0.0D;
        }
        return inaccuracy / VANILLA_SPREAD_COMPONENT_SCALE;
    }

    /**
     * How far a guard's hit scan may reach: the gun's range, cut off where the
     * spread cone would grow wider than the ally check can cover. NaN when the
     * gun's spread leaves no usable range.
     */
    static double hitScanRange(double gunRange, double inaccuracy) {
        if (!Double.isFinite(gunRange) || gunRange <= 0.0D || !Double.isFinite(inaccuracy) || inaccuracy < 0.0D) {
            return Double.NaN;
        }
        double range = Math.min(gunRange, MAX_HIT_SCAN_RANGE);
        double error = Math.sqrt(3.0D) * inaccuracy;
        if (error >= 0.9D) {
            return Double.NaN;
        }
        if (error > 0.0D) {
            range = Math.min(range, MAX_CONE_RADIUS / (error / (1.0D - error)));
        }
        return range >= MIN_HIT_SCAN_RANGE ? range : Double.NaN;
    }

    /** Milliseconds between rounds, or -1 when the gun declares no usable rate. */
    static int fireIntervalMillis(int rpm) {
        return rpm <= 0 ? -1 : Math.max(1, Math.round(60_000.0F / rpm));
    }

    static int reloadTicks(long reloadMillis) {
        if (reloadMillis <= 0L) {
            return DEFAULT_RELOAD_TICKS;
        }
        return (int) Math.max(1L, Math.min(MAX_RELOAD_TICKS, (reloadMillis + 49L) / 50L));
    }
}
