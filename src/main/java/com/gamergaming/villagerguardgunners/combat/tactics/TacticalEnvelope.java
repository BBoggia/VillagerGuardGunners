package com.gamergaming.villagerguardgunners.combat.tactics;

import java.util.Objects;

/**
 * Fully normalized tactical inputs consumed by guard movement and firing
 * gates. Instances are cached with the gun definition.
 */
public record TacticalEnvelope(
        TacticalProfile profile,
        WeaponTacticalTraits traits,
        double minimumFiringDistance,
        double retreatDistance,
        double preferredApproachDistance,
        double maximumFiringDistance,
        int requiredVisibleTicks,
        double aimToleranceDegrees,
        boolean validFiringWindow) {
    static final double FALLBACK_EFFECTIVE_RANGE = 16.0D;
    static final double FALLBACK_MAXIMUM_RANGE = 32.0D;
    static final double FALLBACK_CLOSE_RANGE = 4.0D;
    static final int FALLBACK_VISIBLE_TICKS = 5;
    static final double FALLBACK_AIM_TOLERANCE = 10.0D;

    private static final double MINIMUM_EFFECTIVE_RANGE = 8.0D;
    private static final double MINIMUM_MAXIMUM_RANGE = 8.0D;
    private static final double MAXIMUM_MAXIMUM_RANGE = 64.0D;
    private static final double MINIMUM_CLOSE_RANGE = 1.0D;
    private static final double MAXIMUM_CLOSE_RANGE = 8.0D;
    private static final int MAXIMUM_VISIBLE_TICKS = 40;
    private static final double MINIMUM_AIM_TOLERANCE = 1.0D;
    private static final double MAXIMUM_AIM_TOLERANCE = 45.0D;

    public TacticalEnvelope {
        profile = Objects.requireNonNull(profile, "profile");
        traits = Objects.requireNonNull(traits, "traits");
        // The flag may deliberately be false for a temporarily disabled
        // profile, but a caller cannot force malformed bounds open.
        validFiringWindow = validFiringWindow
                && finiteNonNegative(minimumFiringDistance)
                && finiteNonNegative(retreatDistance)
                && finiteNonNegative(preferredApproachDistance)
                && finiteNonNegative(maximumFiringDistance)
                && minimumFiringDistance <= retreatDistance
                && retreatDistance <= preferredApproachDistance
                && preferredApproachDistance <= maximumFiringDistance;
    }

    public static TacticalEnvelope resolve(
            TacticalProfileDefinition definition,
            WeaponTacticalTraits traits,
            ExplosiveStandoffPolicy explosiveStandoff,
            double effectiveRange,
            double globalMaximumRange,
            double baseCloseRange,
            int baseVisibleTicks,
            double baseAimToleranceDegrees) {
        Objects.requireNonNull(definition, "definition");
        WeaponTacticalTraits normalizedTraits = Objects.requireNonNullElse(
                traits,
                WeaponTacticalTraits.NONE);
        ExplosiveStandoffPolicy normalizedStandoff = Objects.requireNonNullElse(
                explosiveStandoff,
                ExplosiveStandoffPolicy.DEFAULT);

        double maximumRange = normalizeConfiguredValue(
                globalMaximumRange,
                FALLBACK_MAXIMUM_RANGE,
                MINIMUM_MAXIMUM_RANGE,
                MAXIMUM_MAXIMUM_RANGE);
        double normalizedEffectiveRange = normalizeEffectiveRange(effectiveRange, maximumRange);
        double normalizedCloseRange = normalizeConfiguredValue(
                baseCloseRange,
                FALLBACK_CLOSE_RANGE,
                MINIMUM_CLOSE_RANGE,
                MAXIMUM_CLOSE_RANGE);

        double maximumFiringDistance = definition.limitFiringToEffectiveRange()
                ? Math.min(
                        maximumRange,
                        normalizedEffectiveRange * definition.maximumFiringRangeMultiplier())
                : maximumRange;
        double retreatDistance = normalizedCloseRange * definition.retreatDistanceMultiplier();
        double minimumFiringDistance = 0.0D;
        if (normalizedTraits.explosive()) {
            minimumFiringDistance = normalizedStandoff.minimumDistance(
                    normalizedTraits.explosionRadius(),
                    retreatDistance);
            retreatDistance = Math.max(retreatDistance, minimumFiringDistance);
        } else {
            // A narrow custom firing cap must not leave a non-explosive guard
            // holding or retreating outside the only distance where it can fire.
            retreatDistance = Math.min(retreatDistance, maximumFiringDistance);
        }

        double preferredApproachDistance = clamp(
                normalizedEffectiveRange * definition.preferredRangeMultiplier(),
                retreatDistance,
                maximumFiringDistance);
        int requiredVisibleTicks = clamp(
                normalizeVisibleTicks(baseVisibleTicks) + definition.visibleTicksAdjustment(),
                0,
                MAXIMUM_VISIBLE_TICKS);
        double aimToleranceDegrees = clamp(
                normalizeConfiguredValue(
                        baseAimToleranceDegrees,
                        FALLBACK_AIM_TOLERANCE,
                        MINIMUM_AIM_TOLERANCE,
                        MAXIMUM_AIM_TOLERANCE)
                        * definition.aimToleranceMultiplier(),
                MINIMUM_AIM_TOLERANCE,
                MAXIMUM_AIM_TOLERANCE);
        boolean validFiringWindow = finiteNonNegative(minimumFiringDistance)
                && finiteNonNegative(retreatDistance)
                && finiteNonNegative(preferredApproachDistance)
                && finiteNonNegative(maximumFiringDistance)
                && minimumFiringDistance <= retreatDistance
                && retreatDistance <= preferredApproachDistance
                && preferredApproachDistance <= maximumFiringDistance;

        return new TacticalEnvelope(
                definition.profile(),
                normalizedTraits,
                minimumFiringDistance,
                retreatDistance,
                preferredApproachDistance,
                maximumFiringDistance,
                requiredVisibleTicks,
                aimToleranceDegrees,
                validFiringWindow);
    }

    public boolean canFireAtDistanceSquared(double distanceSquared) {
        if (!validFiringWindow || !finiteNonNegative(distanceSquared)) {
            return false;
        }
        double minimumSquared = minimumFiringDistance * minimumFiringDistance;
        double maximumSquared = maximumFiringDistance * maximumFiringDistance;
        return distanceSquared >= minimumSquared && distanceSquared <= maximumSquared;
    }

    private static int normalizeVisibleTicks(int visibleTicks) {
        return visibleTicks < 0 || visibleTicks > MAXIMUM_VISIBLE_TICKS
                ? FALLBACK_VISIBLE_TICKS
                : visibleTicks;
    }

    private static double normalizeEffectiveRange(double effectiveRange, double maximumRange) {
        if (!Double.isFinite(effectiveRange) || effectiveRange <= 0.0D) {
            return Math.min(FALLBACK_EFFECTIVE_RANGE, maximumRange);
        }
        return clamp(effectiveRange, MINIMUM_EFFECTIVE_RANGE, maximumRange);
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0D;
    }

    private static double normalizeConfiguredValue(
            double value,
            double fallback,
            double minimum,
            double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            return fallback;
        }
        return value;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
