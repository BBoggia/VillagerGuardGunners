package com.gamergaming.villagerguardgunners.worldgen.village;

/** Stable identifiers reserved for the five 1.5 village-defense buildings. */
public enum VillageBuildingType {
    BARRACKS("barracks"),
    ARMORY("armory"),
    SENTRY_POST("sentry_post"),
    WATCHTOWER("watchtower"),
    TRAINING_YARD("training_yard");

    private final String serializedName;

    VillageBuildingType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
