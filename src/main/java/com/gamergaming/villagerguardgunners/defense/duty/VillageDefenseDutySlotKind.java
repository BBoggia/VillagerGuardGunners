package com.gamergaming.villagerguardgunners.defense.duty;

import java.util.Arrays;
import java.util.Optional;

/** Stable tactical purpose authored for one Village Defense Building cell. */
public enum VillageDefenseDutySlotKind {
    MUSTER("muster"),
    SENTRY("sentry"),
    OVERWATCH("overwatch"),
    DRILL("drill");

    private final String serializedName;

    VillageDefenseDutySlotKind(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Optional<VillageDefenseDutySlotKind> byName(String serializedName) {
        if (serializedName == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(kind -> kind.serializedName.equals(serializedName))
                .findFirst();
    }
}
