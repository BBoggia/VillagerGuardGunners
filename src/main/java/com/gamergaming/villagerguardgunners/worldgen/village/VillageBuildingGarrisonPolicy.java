package com.gamergaming.villagerguardgunners.worldgen.village;

/** Immutable, bounded runtime controls for newly generated village garrisons. */
public record VillageBuildingGarrisonPolicy(
        boolean enabled,
        int maximumGuardsPerBuilding,
        double guardGunChanceMultiplier) {
    public static final int MINIMUM_GUARDS_PER_BUILDING = 0;
    public static final int MAXIMUM_GUARDS_PER_BUILDING = 4;
    public static final double MINIMUM_GUN_CHANCE_MULTIPLIER = 0.0D;
    public static final double MAXIMUM_GUN_CHANCE_MULTIPLIER = 3.0D;

    public VillageBuildingGarrisonPolicy {
        if (maximumGuardsPerBuilding < MINIMUM_GUARDS_PER_BUILDING
                || maximumGuardsPerBuilding > MAXIMUM_GUARDS_PER_BUILDING) {
            throw new IllegalArgumentException(
                    "Village garrison guard limit must be from zero through four");
        }
        if (!Double.isFinite(guardGunChanceMultiplier)
                || guardGunChanceMultiplier < MINIMUM_GUN_CHANCE_MULTIPLIER
                || guardGunChanceMultiplier > MAXIMUM_GUN_CHANCE_MULTIPLIER) {
            throw new IllegalArgumentException(
                    "Village garrison gun chance multiplier must be finite and in [0, 3]");
        }
    }

    public int limitGuardCount(VillageBuildingType buildingType, int selectedCount) {
        if (selectedCount < 0) {
            throw new IllegalArgumentException("Selected guard count cannot be negative");
        }
        int buildingLimit = switch (buildingType) {
            case BARRACKS -> 4;
            case SENTRY_POST -> 1;
            case WATCHTOWER, TRAINING_YARD -> 2;
            case ARMORY -> 0;
        };
        return Math.min(selectedCount, Math.min(maximumGuardsPerBuilding, buildingLimit));
    }

    public double guardGunUpgradeChance(double baseChance) {
        if (!Double.isFinite(baseChance) || baseChance < 0.0D || baseChance > 1.0D) {
            throw new IllegalArgumentException("Base gun chance must be finite and in [0, 1]");
        }
        return Math.min(1.0D, baseChance * guardGunChanceMultiplier);
    }
}
