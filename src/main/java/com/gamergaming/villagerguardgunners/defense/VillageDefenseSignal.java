package com.gamergaming.villagerguardgunners.defense;

import java.util.Objects;

/** Event-derived minimum readiness without any entity or chunk lookup. */
public enum VillageDefenseSignal {
    BELL_RUNG(VillageDefenseState.WATCH),
    HOSTILE_CONFIRMED(VillageDefenseState.ALERT),
    FRIENDLY_DAMAGED(VillageDefenseState.ALERT),
    FRIENDLY_KILLED(VillageDefenseState.ALERT),
    RAID_ACTIVE(VillageDefenseState.SIEGE);

    private final VillageDefenseState minimumState;

    VillageDefenseSignal(VillageDefenseState minimumState) {
        this.minimumState = Objects.requireNonNull(minimumState, "minimumState");
    }

    public VillageDefenseState minimumState() {
        return minimumState;
    }
}
