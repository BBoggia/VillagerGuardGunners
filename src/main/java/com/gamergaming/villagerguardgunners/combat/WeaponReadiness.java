package com.gamergaming.villagerguardgunners.combat;

/** Immutable, cheap snapshot of the held weapon's combat movement readiness. */
public enum WeaponReadiness {
    READY,
    UNBOUND,
    SUSPENDED,
    INVALID_FIRING_WINDOW,
    DRY,
    RELOADING,
    CYCLING_ACTION,
    DRAWING,
    NEEDS_RELOAD;

    public boolean permitsOffensiveMovement() {
        return this == READY;
    }
}
