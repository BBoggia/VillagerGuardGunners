package com.gamergaming.villagerguardgunners.defense.duty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

/** Bidirectionally indexed transient slot reservations for loaded guards only. */
public final class VillageDefenseDutyReservationBook {
    public static final int MAXIMUM_RESERVATIONS = 4_096;

    private final Map<UUID, VillageDefenseDutyAssignment> bySlot = new HashMap<>();
    private final Map<UUID, VillageDefenseDutyAssignment> byGuard = new HashMap<>();

    public @Nullable VillageDefenseDutyAssignment forGuard(UUID guardId) {
        return byGuard.get(Objects.requireNonNull(guardId, "guardId"));
    }

    public @Nullable VillageDefenseDutyAssignment forSlot(UUID slotId) {
        return bySlot.get(Objects.requireNonNull(slotId, "slotId"));
    }

    public List<VillageDefenseDutyAssignment> forNetwork(UUID networkId) {
        Objects.requireNonNull(networkId, "networkId");
        return bySlot.values().stream()
                .filter(assignment -> networkId.equals(assignment.networkId()))
                .sorted((left, right) -> left.slotId().compareTo(right.slotId()))
                .toList();
    }

    /** Replaces one network's reservations only after the complete plan validates. */
    public boolean replaceNetwork(
            UUID networkId,
            Collection<VillageDefenseDutyAssignment> replacements) {
        Objects.requireNonNull(networkId, "networkId");
        Objects.requireNonNull(replacements, "replacements");
        if (replacements.size() > MAXIMUM_RESERVATIONS) {
            return false;
        }
        Map<UUID, VillageDefenseDutyAssignment> candidateSlots = new HashMap<>();
        Map<UUID, VillageDefenseDutyAssignment> candidateGuards = new HashMap<>();
        for (VillageDefenseDutyAssignment replacement : replacements) {
            if (replacement == null
                    || !networkId.equals(replacement.networkId())
                    || candidateSlots.put(replacement.slotId(), replacement) != null
                    || candidateGuards.put(replacement.guardId(), replacement) != null) {
                return false;
            }
            VillageDefenseDutyAssignment occupiedSlot = bySlot.get(replacement.slotId());
            VillageDefenseDutyAssignment occupiedGuard = byGuard.get(replacement.guardId());
            if ((occupiedSlot != null && !networkId.equals(occupiedSlot.networkId()))
                    || (occupiedGuard != null
                            && !networkId.equals(occupiedGuard.networkId()))) {
                return false;
            }
        }
        int networkReservations = 0;
        for (VillageDefenseDutyAssignment assignment : bySlot.values()) {
            if (networkId.equals(assignment.networkId())) {
                networkReservations++;
            }
        }
        int retainedOutsideNetwork = bySlot.size() - networkReservations;
        if ((long) retainedOutsideNetwork + replacements.size() > MAXIMUM_RESERVATIONS) {
            return false;
        }
        releaseNetwork(networkId);
        candidateSlots.forEach(bySlot::put);
        candidateGuards.forEach(byGuard::put);
        return true;
    }

    public @Nullable VillageDefenseDutyAssignment releaseGuard(UUID guardId) {
        VillageDefenseDutyAssignment removed = byGuard.remove(
                Objects.requireNonNull(guardId, "guardId"));
        if (removed != null) {
            bySlot.remove(removed.slotId(), removed);
        }
        return removed;
    }

    public int releaseNetwork(UUID networkId) {
        Objects.requireNonNull(networkId, "networkId");
        List<UUID> guards = new ArrayList<>();
        for (VillageDefenseDutyAssignment assignment : bySlot.values()) {
            if (networkId.equals(assignment.networkId())) {
                guards.add(assignment.guardId());
            }
        }
        guards.forEach(this::releaseGuard);
        return guards.size();
    }

    public int size() {
        return bySlot.size();
    }

    public void clear() {
        bySlot.clear();
        byGuard.clear();
    }
}
