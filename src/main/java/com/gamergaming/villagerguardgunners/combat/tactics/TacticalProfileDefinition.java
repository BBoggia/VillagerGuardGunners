package com.gamergaming.villagerguardgunners.combat.tactics;

import java.util.Objects;

/**
 * Bounded tactical modifiers. None of these values modify native TaCZ weapon
 * statistics; they only shape guard positioning and trigger eligibility.
 */
public record TacticalProfileDefinition(
        TacticalProfile profile,
        double preferredRangeMultiplier,
        double retreatDistanceMultiplier,
        int visibleTicksAdjustment,
        double aimToleranceMultiplier,
        boolean limitFiringToEffectiveRange,
        double maximumFiringRangeMultiplier) {
    private static final double MIN_RANGE_MULTIPLIER = 0.25D;
    private static final double MAX_RANGE_MULTIPLIER = 4.0D;
    private static final int MAX_VISIBLE_TICKS_ADJUSTMENT = 40;

    public TacticalProfileDefinition {
        profile = Objects.requireNonNull(profile, "profile");
        preferredRangeMultiplier = boundedMultiplier(preferredRangeMultiplier);
        retreatDistanceMultiplier = boundedMultiplier(retreatDistanceMultiplier);
        visibleTicksAdjustment = Math.max(
                -MAX_VISIBLE_TICKS_ADJUSTMENT,
                Math.min(visibleTicksAdjustment, MAX_VISIBLE_TICKS_ADJUSTMENT));
        aimToleranceMultiplier = boundedMultiplier(aimToleranceMultiplier);
        maximumFiringRangeMultiplier = boundedMultiplier(maximumFiringRangeMultiplier);
    }

    public static TacticalProfileDefinition defaultsFor(TacticalProfile profile) {
        return switch (Objects.requireNonNull(profile, "profile")) {
            case GENERALIST -> new TacticalProfileDefinition(
                    profile, 1.0D, 1.0D, 0, 1.0D, false, 1.0D);
            case SKIRMISHER -> new TacticalProfileDefinition(
                    profile, 0.75D, 1.0D, -1, 1.0D, true, 1.25D);
            case BREACHER -> new TacticalProfileDefinition(
                    profile, 0.65D, 1.0D, 0, 1.0D, true, 1.1D);
            case MARKSMAN -> new TacticalProfileDefinition(
                    profile, 1.0D, 2.0D, 3, 0.65D, false, 1.0D);
            case SUPPORT -> new TacticalProfileDefinition(
                    profile, 0.9D, 1.5D, 1, 0.8D, false, 1.0D);
            case DEMOLITIONS -> new TacticalProfileDefinition(
                    profile, 1.0D, 1.0D, 3, 0.65D, false, 1.0D);
        };
    }

    private static double boundedMultiplier(double value) {
        if (!Double.isFinite(value)) {
            return 1.0D;
        }
        return Math.max(MIN_RANGE_MULTIPLIER, Math.min(value, MAX_RANGE_MULTIPLIER));
    }
}
