package com.gamergaming.villagerguardgunners.defense.duty;

import java.util.Objects;
import java.util.UUID;

/** Transient server-authoritative ownership of one loaded building duty slot. */
public record VillageDefenseDutyAssignment(
        UUID networkId,
        VillageDefenseDutySlot slot,
        UUID guardId,
        long assignedGameTime,
        long shiftEndGameTime) {
    public static final long NO_SHIFT_END = Long.MAX_VALUE;

    public VillageDefenseDutyAssignment {
        networkId = Objects.requireNonNull(networkId, "networkId");
        Objects.requireNonNull(slot, "slot");
        guardId = Objects.requireNonNull(guardId, "guardId");
        if (assignedGameTime < 0L
                || shiftEndGameTime < assignedGameTime) {
            throw new IllegalArgumentException("Defense-duty assignment time is invalid");
        }
    }

    public UUID slotId() {
        return slot.slotId();
    }

    public boolean shiftDue(long gameTime) {
        return shiftEndGameTime != NO_SHIFT_END && gameTime >= shiftEndGameTime;
    }
}
