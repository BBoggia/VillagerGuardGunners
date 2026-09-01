package com.gamergaming.villagerguardgunners.worldgen.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Stable identifiers reserved for the five 1.5 village-defense buildings. */
public enum VillageBuildingType {
    BARRACKS("barracks"),
    ARMORY("armory"),
    SENTRY_POST("sentry_post"),
    WATCHTOWER("watchtower"),
    TRAINING_YARD("training_yard");

    public static final Codec<VillageBuildingType> CODEC = Codec.STRING.comapFlatMap(
            name -> byName(name)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(
                            () -> "Unknown village building type: " + name)),
            VillageBuildingType::serializedName);

    private final String serializedName;

    VillageBuildingType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<VillageBuildingType> byName(String name) {
        if (name == null || !name.equals(name.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(name))
                .findFirst();
    }
}
