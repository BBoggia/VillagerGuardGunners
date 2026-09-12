package com.gamergaming.villagerguardgunners.defense;

import java.util.Arrays;
import java.util.Optional;

/**
 * Stable readiness states shared by village alerts, staffing, and scheduled
 * patrol routes.
 *
 * <p>The readiness rank is deliberately separate from the enum ordinal so a
 * future serialized value can be added without silently changing precedence.
 * Recovery retains more staffing than Peace but is less urgent than Watch.</p>
 */
public enum VillageDefenseState {
    PEACE("peace", 0, false),
    RECOVERY("recovery", 1, false),
    WATCH("watch", 2, false),
    ALERT("alert", 3, true),
    SIEGE("siege", 4, true);

    private final String serializedName;
    private final int readinessRank;
    private final boolean confirmedThreatState;

    VillageDefenseState(
            String serializedName,
            int readinessRank,
            boolean confirmedThreatState) {
        this.serializedName = serializedName;
        this.readinessRank = readinessRank;
        this.confirmedThreatState = confirmedThreatState;
    }

    public String serializedName() {
        return serializedName;
    }

    public int readinessRank() {
        return readinessRank;
    }

    public boolean isMobilized() {
        return this != PEACE;
    }

    public boolean isConfirmedThreatState() {
        return confirmedThreatState;
    }

    public boolean isAtLeast(VillageDefenseState minimum) {
        return minimum != null && readinessRank >= minimum.readinessRank;
    }

    public static Optional<VillageDefenseState> byName(String serializedName) {
        if (serializedName == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(state -> state.serializedName.equals(serializedName))
                .findFirst();
    }
}
