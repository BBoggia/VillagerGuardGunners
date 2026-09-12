package com.gamergaming.villagerguardgunners.defense.duty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseState;
import org.junit.jupiter.api.Test;

final class VillageDefenseShiftPolicyTest {
    @Test
    void readinessActivatesOnlyItsIntendedDuties() {
        assertAll(
                () -> assertTrue(VillageDefenseShiftPolicy.active(
                        VillageDefenseState.PEACE, VillageDefenseDutySlotKind.SENTRY)),
                () -> assertTrue(VillageDefenseShiftPolicy.active(
                        VillageDefenseState.PEACE, VillageDefenseDutySlotKind.DRILL)),
                () -> assertFalse(VillageDefenseShiftPolicy.active(
                        VillageDefenseState.PEACE, VillageDefenseDutySlotKind.OVERWATCH)),
                () -> assertTrue(VillageDefenseShiftPolicy.active(
                        VillageDefenseState.WATCH, VillageDefenseDutySlotKind.OVERWATCH)),
                () -> assertFalse(VillageDefenseShiftPolicy.active(
                        VillageDefenseState.WATCH, VillageDefenseDutySlotKind.DRILL)),
                () -> assertTrue(VillageDefenseShiftPolicy.active(
                        VillageDefenseState.ALERT, VillageDefenseDutySlotKind.MUSTER)),
                () -> assertFalse(VillageDefenseShiftPolicy.active(
                        VillageDefenseState.RECOVERY, VillageDefenseDutySlotKind.MUSTER)));
    }

    @Test
    void routineStatesRotateAndThreatStatesFreeze() {
        assertAll(
                () -> assertEquals(7_000L, VillageDefenseShiftPolicy.shiftEnd(
                        VillageDefenseState.PEACE, 1_000L)),
                () -> assertEquals(3_400L, VillageDefenseShiftPolicy.shiftEnd(
                        VillageDefenseState.WATCH, 1_000L)),
                () -> assertEquals(VillageDefenseDutyAssignment.NO_SHIFT_END,
                        VillageDefenseShiftPolicy.shiftEnd(
                                VillageDefenseState.ALERT, 1_000L)),
                () -> assertEquals(VillageDefenseDutyAssignment.NO_SHIFT_END,
                        VillageDefenseShiftPolicy.shiftEnd(
                                VillageDefenseState.SIEGE, 1_000L)));
    }
}
