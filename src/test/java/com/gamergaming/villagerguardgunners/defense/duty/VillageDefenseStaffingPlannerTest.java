package com.gamergaming.villagerguardgunners.defense.duty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseState;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingPlanner.Candidate;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingPlanner.Plan;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseWeaponSuitability.WeaponClass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VillageDefenseStaffingPlannerTest {
    private static final UUID NETWORK = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID BUILDING = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");

    @Test
    void scarceRangedGuardsFillRangedSlotsBeforeGeneralSlots() {
        VillageDefenseDutySlot any = slot(
                "any", 0, VillageDefenseDutySlotKind.SENTRY,
                VillageDefenseDutyWeaponRequirement.ANY);
        VillageDefenseDutySlot ranged = slot(
                "ranged", 1, VillageDefenseDutySlotKind.SENTRY,
                VillageDefenseDutyWeaponRequirement.RANGED);
        Candidate rifle = candidate(1, WeaponClass.RANGED);
        Candidate sword = candidate(2, WeaponClass.MELEE);

        Plan plan = VillageDefenseStaffingPlanner.plan(
                NETWORK,
                VillageDefenseState.PEACE,
                List.of(any, ranged),
                List.of(rifle, sword),
                List.of(),
                20L);

        assertAll(
                () -> assertEquals(2, plan.assignments().size()),
                () -> assertEquals(rifle.guardId(), plan.assignments().stream()
                        .filter(assignment -> assignment.slotId().equals(ranged.slotId()))
                        .findFirst().orElseThrow().guardId()),
                () -> assertEquals(sword.guardId(), plan.assignments().stream()
                        .filter(assignment -> assignment.slotId().equals(any.slotId()))
                        .findFirst().orElseThrow().guardId()));
    }

    @Test
    void readinessAndWeaponRulesLeaveInvalidSlotsUnfilled() {
        List<VillageDefenseDutySlot> slots = List.of(
                slot("drill", 0, VillageDefenseDutySlotKind.DRILL,
                        VillageDefenseDutyWeaponRequirement.RANGED),
                slot("overwatch", 1, VillageDefenseDutySlotKind.OVERWATCH,
                        VillageDefenseDutyWeaponRequirement.RANGED),
                slot("muster", 2, VillageDefenseDutySlotKind.MUSTER,
                        VillageDefenseDutyWeaponRequirement.ANY));
        Plan watch = VillageDefenseStaffingPlanner.plan(
                NETWORK,
                VillageDefenseState.WATCH,
                slots,
                List.of(candidate(1, WeaponClass.UNARMED)),
                List.of(),
                0L);

        assertAll(
                () -> assertEquals(1, watch.activeSlots()),
                () -> assertEquals(0, watch.assignments().size()),
                () -> assertEquals(1, watch.unfilledSlots()));
    }

    @Test
    void watchPromotesAReservedRangedGuardOutOfAGeneralSlot() {
        VillageDefenseDutySlot any = slot(
                "any", 0, VillageDefenseDutySlotKind.SENTRY,
                VillageDefenseDutyWeaponRequirement.ANY);
        VillageDefenseDutySlot overwatch = slot(
                "overwatch", 1, VillageDefenseDutySlotKind.OVERWATCH,
                VillageDefenseDutyWeaponRequirement.RANGED);
        Candidate rifle = candidate(1, WeaponClass.RANGED);
        Candidate sword = candidate(2, WeaponClass.MELEE);
        VillageDefenseDutyAssignment oldSentry = new VillageDefenseDutyAssignment(
                NETWORK, any, rifle.guardId(), 0L, 6_000L);

        Plan plan = VillageDefenseStaffingPlanner.plan(
                NETWORK,
                VillageDefenseState.WATCH,
                List.of(any, overwatch),
                List.of(rifle, sword),
                List.of(oldSentry),
                100L);

        assertAll(
                () -> assertEquals(rifle.guardId(), plan.assignments().stream()
                        .filter(assignment -> assignment.slotId().equals(
                                overwatch.slotId()))
                        .findFirst().orElseThrow().guardId()),
                () -> assertEquals(sword.guardId(), plan.assignments().stream()
                        .filter(assignment -> assignment.slotId().equals(any.slotId()))
                        .findFirst().orElseThrow().guardId()));
    }

    @Test
    void reviewsKeepDeadlinesButExpiredShiftsPreferAReplacement() {
        VillageDefenseDutySlot slot = slot(
                "sentry", 0, VillageDefenseDutySlotKind.SENTRY,
                VillageDefenseDutyWeaponRequirement.ANY);
        Candidate first = candidate(1, WeaponClass.MELEE);
        Candidate second = candidate(2, WeaponClass.MELEE);
        VillageDefenseDutyAssignment existing = new VillageDefenseDutyAssignment(
                NETWORK, slot, first.guardId(), 100L, 6_100L);

        Plan beforeDeadline = VillageDefenseStaffingPlanner.plan(
                NETWORK,
                VillageDefenseState.PEACE,
                List.of(slot),
                List.of(first, second),
                List.of(existing),
                500L);
        Plan afterDeadline = VillageDefenseStaffingPlanner.plan(
                NETWORK,
                VillageDefenseState.PEACE,
                List.of(slot),
                List.of(first, second),
                List.of(existing),
                6_100L);

        assertAll(
                () -> assertEquals(first.guardId(),
                        beforeDeadline.assignments().get(0).guardId()),
                () -> assertEquals(6_100L,
                        beforeDeadline.assignments().get(0).shiftEndGameTime()),
                () -> assertEquals(second.guardId(),
                        afterDeadline.assignments().get(0).guardId()),
                () -> assertEquals(12_100L,
                        afterDeadline.assignments().get(0).shiftEndGameTime()));
    }

    @Test
    void threatStatesFreezeRetainedAssignmentsAndInputsAreBounded() {
        VillageDefenseDutySlot slot = slot(
                "sentry", 0, VillageDefenseDutySlotKind.SENTRY,
                VillageDefenseDutyWeaponRequirement.ANY);
        Candidate candidate = candidate(1, WeaponClass.RANGED);
        VillageDefenseDutyAssignment existing = new VillageDefenseDutyAssignment(
                NETWORK, slot, candidate.guardId(), 10L, 6_010L);
        Plan siege = VillageDefenseStaffingPlanner.plan(
                NETWORK,
                VillageDefenseState.SIEGE,
                List.of(slot),
                List.of(candidate),
                List.of(existing),
                100L);
        List<Candidate> tooMany = new ArrayList<>(Collections.nCopies(
                VillageDefenseStaffingPlanner.MAXIMUM_CANDIDATES + 1, candidate));

        assertAll(
                () -> assertEquals(VillageDefenseDutyAssignment.NO_SHIFT_END,
                        siege.assignments().get(0).shiftEndGameTime()),
                () -> assertEquals(100L,
                        siege.assignments().get(0).assignedGameTime()),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        VillageDefenseStaffingPlanner.plan(
                                NETWORK,
                                VillageDefenseState.PEACE,
                                List.of(slot),
                                tooMany,
                                List.of(),
                                0L)));
    }

    @Test
    void plannerAndMovementShareTheSameTravelBoundary() {
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos boundary = new BlockPos(128, 64, 0);
        BlockPos outside = new BlockPos(129, 64, 0);

        assertAll(
                () -> assertTrue(VillageDefenseStaffingPlanner.withinTravelDistance(
                        origin, boundary)),
                () -> assertTrue(VillageDefenseStaffingPlanner.withinTravelDistance(
                        boundary, origin)),
                () -> assertFalse(VillageDefenseStaffingPlanner.withinTravelDistance(
                        origin, outside)));
    }

    @Test
    void boundedSelectionCanDiscardCandidatesThatCannotFillActiveSlots() {
        VillageDefenseDutySlot overwatch = slot(
                "overwatch", 0, VillageDefenseDutySlotKind.OVERWATCH,
                VillageDefenseDutyWeaponRequirement.RANGED);
        Candidate melee = candidate(1, WeaponClass.MELEE);
        Candidate ranged = candidate(2, WeaponClass.RANGED);

        assertAll(
                () -> assertFalse(VillageDefenseStaffingPlanner.canFillAnyActiveSlot(
                        melee, VillageDefenseState.WATCH, List.of(overwatch))),
                () -> assertTrue(VillageDefenseStaffingPlanner.canFillAnyActiveSlot(
                        ranged, VillageDefenseState.WATCH, List.of(overwatch))),
                () -> assertEquals(4.0D,
                        VillageDefenseStaffingPlanner.nearestActiveSlotDistanceSquared(
                                ranged, VillageDefenseState.WATCH, List.of(overwatch))));
    }

    private static VillageDefenseDutySlot slot(
            String key,
            int x,
            VillageDefenseDutySlotKind kind,
            VillageDefenseDutyWeaponRequirement requirement) {
        return new VillageDefenseDutySlot(
                VillageDefenseDutySlot.CURRENT_SCHEMA_VERSION,
                VillageDefenseDutyIds.slotId(BUILDING, key),
                BUILDING,
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                key,
                new BlockPos(x, 64, 0),
                Direction.NORTH,
                kind,
                requirement);
    }

    private static Candidate candidate(int x, WeaponClass weaponClass) {
        return new Candidate(
                new UUID(0L, x),
                new BlockPos(x, 64, 0),
                weaponClass,
                BUILDING);
    }
}
