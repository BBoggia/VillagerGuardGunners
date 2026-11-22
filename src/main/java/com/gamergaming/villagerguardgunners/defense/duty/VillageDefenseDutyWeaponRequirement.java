package com.gamergaming.villagerguardgunners.defense.duty;

import java.util.Arrays;
import java.util.Optional;

/** Coarse equipment requirement used by the future bounded staffing pass. */
public enum VillageDefenseDutyWeaponRequirement {
    ANY("any"),
    RANGED("ranged");

    private final String serializedName;

    VillageDefenseDutyWeaponRequirement(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<VillageDefenseDutyWeaponRequirement> byName(
            String serializedName) {
        if (serializedName == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(requirement -> requirement.serializedName.equals(serializedName))
                .findFirst();
    }
}
