package com.gamergaming.villagerguardgunners.combat.tactics;

/** Non-bypassable distance policy layered over any profile using explosives. */
public record ExplosiveStandoffPolicy(
        double radiusMultiplier,
        double additionalBlocks) {
    private static final double DEFAULT_RADIUS_MULTIPLIER = 2.0D;
    private static final double DEFAULT_ADDITIONAL_BLOCKS = 4.0D;

    public static final ExplosiveStandoffPolicy DEFAULT =
            new ExplosiveStandoffPolicy(DEFAULT_RADIUS_MULTIPLIER, DEFAULT_ADDITIONAL_BLOCKS);

    public ExplosiveStandoffPolicy {
        radiusMultiplier = bounded(
                radiusMultiplier,
                DEFAULT_RADIUS_MULTIPLIER,
                1.0D,
                8.0D);
        additionalBlocks = bounded(
                additionalBlocks,
                DEFAULT_ADDITIONAL_BLOCKS,
                0.0D,
                32.0D);
    }

    public double minimumDistance(double explosionRadius, double retreatDistance) {
        double normalizedRadius = Double.isFinite(explosionRadius) && explosionRadius > 0.0D
                ? explosionRadius
                : 0.0D;
        double normalizedRetreat = Double.isFinite(retreatDistance) && retreatDistance > 0.0D
                ? retreatDistance
                : 0.0D;
        return Math.max(
                normalizedRetreat,
                normalizedRadius * radiusMultiplier + additionalBlocks);
    }

    private static double bounded(double value, double fallback, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            return fallback;
        }
        return value;
    }
}
