package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseState;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseWeaponSuitability.WeaponClass;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/** Pure, bounded assignment planner; it performs no entity or world access. */
public final class VillageDefenseStaffingPlanner {
    public static final int MAXIMUM_SLOTS = 256;
    public static final int MAXIMUM_CANDIDATES = 128;
    public static final double MAXIMUM_TRAVEL_DISTANCE = 128.0D;

    private static final double MAXIMUM_TRAVEL_DISTANCE_SQUARED =
            MAXIMUM_TRAVEL_DISTANCE * MAXIMUM_TRAVEL_DISTANCE;

    private VillageDefenseStaffingPlanner() {
    }

    public static Plan plan(
            UUID networkId,
            VillageDefenseState state,
            Collection<VillageDefenseDutySlot> slots,
            Collection<Candidate> candidates,
            Collection<VillageDefenseDutyAssignment> existingAssignments,
            long gameTime) {
        UUID normalizedNetworkId = Objects.requireNonNull(networkId, "networkId");
        VillageDefenseState normalizedState = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(existingAssignments, "existingAssignments");
        if (slots.size() > MAXIMUM_SLOTS
                || candidates.size() > MAXIMUM_CANDIDATES
                || existingAssignments.size()
                        > VillageDefenseDutyReservationBook.MAXIMUM_RESERVATIONS
                || gameTime < 0L) {
            throw new IllegalArgumentException("Staffing input exceeds its hard bound");
        }

        List<VillageDefenseDutySlot> activeSlots = slots.stream()
                .map(slot -> Objects.requireNonNull(slot, "slot"))
                .filter(slot -> VillageDefenseShiftPolicy.active(
                        normalizedState, slot.kind()))
                .sorted(SLOT_ORDER)
                .toList();
        Map<UUID, Candidate> candidateById = new HashMap<>();
        for (Candidate candidate : candidates) {
            Candidate normalized = Objects.requireNonNull(candidate, "candidate");
            if (candidateById.put(normalized.guardId(), normalized) != null) {
                throw new IllegalArgumentException("Duplicate staffing candidate ID");
            }
        }
        Map<UUID, VillageDefenseDutyAssignment> existingBySlot = new HashMap<>();
        Set<UUID> existingGuards = new HashSet<>();
        for (VillageDefenseDutyAssignment assignment : existingAssignments) {
            VillageDefenseDutyAssignment normalized = Objects.requireNonNull(
                    assignment, "existing assignment");
            if (normalizedNetworkId.equals(normalized.networkId())
                    && (existingBySlot.put(normalized.slotId(), normalized) != null
                            || !existingGuards.add(normalized.guardId()))) {
                throw new IllegalArgumentException(
                        "Existing staffing reservations are not one-to-one");
            }
        }

        List<VillageDefenseDutyAssignment> assignments = new ArrayList<>();
        Set<UUID> reservedGuards = new HashSet<>();
        Set<UUID> reservedSlots = new HashSet<>();
        for (VillageDefenseDutySlot slot : activeSlots) {
            VillageDefenseDutyAssignment existing = existingBySlot.get(slot.slotId());
            if (existing == null || existing.shiftDue(gameTime)) {
                continue;
            }
            Candidate candidate = candidateById.get(existing.guardId());
            if (!eligible(candidate, slot) || !reservedGuards.add(existing.guardId())) {
                continue;
            }
            if (slot.weaponRequirement() == VillageDefenseDutyWeaponRequirement.ANY
                    && candidate.weaponClass() == WeaponClass.RANGED
                    && activeSlots.stream().anyMatch(restricted ->
                            restricted.weaponRequirement()
                                    == VillageDefenseDutyWeaponRequirement.RANGED
                                    && !reservedSlots.contains(restricted.slotId())
                                    && eligible(candidate, restricted))) {
                reservedGuards.remove(existing.guardId());
                continue;
            }
            long replacementShiftEnd = VillageDefenseShiftPolicy.shiftEnd(
                    normalizedState, gameTime);
            boolean bothFrozen = existing.shiftEndGameTime()
                    == VillageDefenseDutyAssignment.NO_SHIFT_END
                    && replacementShiftEnd == VillageDefenseDutyAssignment.NO_SHIFT_END;
            boolean sameFiniteCadence = existing.shiftEndGameTime()
                            != VillageDefenseDutyAssignment.NO_SHIFT_END
                    && replacementShiftEnd
                            != VillageDefenseDutyAssignment.NO_SHIFT_END
                    && existing.shiftEndGameTime() - existing.assignedGameTime()
                            == replacementShiftEnd - gameTime;
            VillageDefenseDutyAssignment retained = (bothFrozen || sameFiniteCadence)
                    && existing.slot().equals(slot)
                    ? existing
                    : new VillageDefenseDutyAssignment(
                            normalizedNetworkId,
                            slot,
                            existing.guardId(),
                            gameTime,
                            replacementShiftEnd);
            assignments.add(retained);
            reservedSlots.add(slot.slotId());
        }

        long shiftNumber = VillageDefenseShiftPolicy.shiftNumber(
                normalizedState, gameTime);
        for (VillageDefenseDutySlot slot : activeSlots) {
            if (reservedSlots.contains(slot.slotId())) {
                continue;
            }
            VillageDefenseDutyAssignment previous = existingBySlot.get(slot.slotId());
            Candidate selected = candidateById.values().stream()
                    .filter(candidate -> !reservedGuards.contains(candidate.guardId()))
                    .filter(candidate -> eligible(candidate, slot))
                    .min(candidateOrder(
                            normalizedNetworkId,
                            slot,
                            previous == null ? null : previous.guardId(),
                            shiftNumber))
                    .orElse(null);
            if (selected == null) {
                continue;
            }
            reservedGuards.add(selected.guardId());
            assignments.add(new VillageDefenseDutyAssignment(
                    normalizedNetworkId,
                    slot,
                    selected.guardId(),
                    gameTime,
                    VillageDefenseShiftPolicy.shiftEnd(normalizedState, gameTime)));
        }
        assignments.sort(Comparator.comparing(VillageDefenseDutyAssignment::slotId));
        return new Plan(
                List.copyOf(assignments),
                activeSlots.size(),
                candidateById.size(),
                activeSlots.size() - assignments.size());
    }

    private static boolean eligible(
            @Nullable Candidate candidate,
            VillageDefenseDutySlot slot) {
        return candidate != null
                && VillageDefenseWeaponSuitability.meets(
                        candidate.weaponClass(), slot.weaponRequirement())
                && withinTravelDistance(candidate.position(), slot.position());
    }

    static boolean withinTravelDistance(BlockPos from, BlockPos destination) {
        double distanceSquared = Objects.requireNonNull(from, "from")
                .distSqr(Objects.requireNonNull(destination, "destination"));
        return Double.isFinite(distanceSquared)
                && distanceSquared <= MAXIMUM_TRAVEL_DISTANCE_SQUARED;
    }

    static boolean canFillAnyActiveSlot(
            Candidate candidate,
            VillageDefenseState state,
            Collection<VillageDefenseDutySlot> slots) {
        Candidate normalizedCandidate = Objects.requireNonNull(candidate, "candidate");
        VillageDefenseState normalizedState = Objects.requireNonNull(state, "state");
        return Objects.requireNonNull(slots, "slots").stream()
                .filter(slot -> VillageDefenseShiftPolicy.active(
                        normalizedState, slot.kind()))
                .anyMatch(slot -> eligible(normalizedCandidate, slot));
    }

    static double nearestActiveSlotDistanceSquared(
            Candidate candidate,
            VillageDefenseState state,
            Collection<VillageDefenseDutySlot> slots) {
        Candidate normalizedCandidate = Objects.requireNonNull(candidate, "candidate");
        VillageDefenseState normalizedState = Objects.requireNonNull(state, "state");
        return Objects.requireNonNull(slots, "slots").stream()
                .filter(slot -> VillageDefenseShiftPolicy.active(
                        normalizedState, slot.kind()))
                .filter(slot -> eligible(normalizedCandidate, slot))
                .mapToDouble(slot -> normalizedCandidate.position().distSqr(slot.position()))
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    private static Comparator<Candidate> candidateOrder(
            UUID networkId,
            VillageDefenseDutySlot slot,
            @Nullable UUID previousGuard,
            long shiftNumber) {
        return Comparator
                .comparing((Candidate candidate) -> !slot.buildingId().equals(
                        candidate.preferredBuildingId()))
                .thenComparing(candidate -> previousGuard != null
                        && previousGuard.equals(candidate.guardId()))
                .thenComparingDouble(candidate -> candidate.position().distSqr(
                        slot.position()))
                .thenComparingLong(candidate -> tieBreaker(
                        networkId, slot.slotId(), candidate.guardId(), shiftNumber))
                .thenComparing(Candidate::guardId);
    }

    private static long tieBreaker(
            UUID networkId,
            UUID slotId,
            UUID guardId,
            long shiftNumber) {
        long value = networkId.getMostSignificantBits()
                ^ Long.rotateLeft(networkId.getLeastSignificantBits(), 7)
                ^ Long.rotateLeft(slotId.getMostSignificantBits(), 17)
                ^ Long.rotateLeft(slotId.getLeastSignificantBits(), 29)
                ^ Long.rotateLeft(guardId.getMostSignificantBits(), 41)
                ^ guardId.getLeastSignificantBits()
                ^ shiftNumber;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return value;
    }

    private static final Comparator<VillageDefenseDutySlot> SLOT_ORDER =
            Comparator.comparing(
                            (VillageDefenseDutySlot slot) ->
                                    slot.weaponRequirement()
                                            != VillageDefenseDutyWeaponRequirement.RANGED)
                    .thenComparingInt(slot -> switch (slot.kind()) {
                        case OVERWATCH -> 0;
                        case SENTRY -> 1;
                        case MUSTER -> 2;
                        case DRILL -> 3;
                    })
                    .thenComparing(VillageDefenseDutySlot::slotId);

    public record Candidate(
            UUID guardId,
            BlockPos position,
            WeaponClass weaponClass,
            @Nullable UUID preferredBuildingId) {
        public Candidate {
            guardId = Objects.requireNonNull(guardId, "guardId");
            position = Objects.requireNonNull(position, "position").immutable();
            weaponClass = Objects.requireNonNull(weaponClass, "weaponClass");
        }
    }

    public record Plan(
            List<VillageDefenseDutyAssignment> assignments,
            int activeSlots,
            int eligibleCandidates,
            int unfilledSlots) {
        public Plan {
            assignments = List.copyOf(Objects.requireNonNull(
                    assignments, "assignments"));
            if (activeSlots < 0
                    || eligibleCandidates < 0
                    || unfilledSlots < 0
                    || assignments.size() + unfilledSlots != activeSlots) {
                throw new IllegalArgumentException("Staffing plan counts are inconsistent");
            }
        }
    }
}
