package com.gamergaming.villagerguardgunners.armory.resupply;

/**
 * Authoritative, server-only state of one guard's Armory resupply attempt.
 *
 * <p>The state is deliberately transient. Requests and reservations expire,
 * so none of these values are persisted across a save or chunk unload.</p>
 */
public enum ArmoryResupplyState {
    IDLE(false, false, false),
    SEARCHING(true, false, false),
    RESERVED(true, true, false),
    TRAVELLING(true, true, true),
    WITHDRAWING(true, true, false),
    COOLDOWN(false, false, false),
    UNREACHABLE(false, false, false);

    private final boolean activeRequest;
    private final boolean ownsReservation;
    private final boolean ownsNavigation;

    ArmoryResupplyState(
            boolean activeRequest,
            boolean ownsReservation,
            boolean ownsNavigation) {
        this.activeRequest = activeRequest;
        this.ownsReservation = ownsReservation;
        this.ownsNavigation = ownsNavigation;
    }

    public boolean hasActiveRequest() {
        return activeRequest;
    }

    public boolean ownsReservation() {
        return ownsReservation;
    }

    public boolean ownsNavigation() {
        return ownsNavigation;
    }

    public boolean isBackoff() {
        return this == COOLDOWN || this == UNREACHABLE;
    }
}
