package com.gamergaming.villagerguardgunners.worldgen.village;

/** Immutable, one-way controls for a newly generated village Armory. */
public record VillageBuildingArmoryPolicy(
        boolean placementEnabled,
        boolean initialStockEnabled) {

    public VillageBuildingArmoryPolicy {
        if (initialStockEnabled && !placementEnabled) {
            throw new IllegalArgumentException(
                    "Initial Armory stock cannot be enabled when placement is disabled");
        }
    }
}
