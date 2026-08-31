package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class ArmoryResupplyNeedTest {
    private static final ResourceLocation PRIMARY_GUN =
            ResourceLocation.fromNamespaceAndPath("test", "primary");
    private static final ResourceLocation SIDEARM_GUN =
            ResourceLocation.fromNamespaceAndPath("test", "sidearm");

    @Test
    void lowPhysicalReserveRequestsOnlyTheDeficitToTheTarget() {
        ArmoryResupplyNeed.Assessment assessment = assess(
                loadout(
                        GuardWeaponAmmunition.ActiveRole.PRIMARY,
                        physical(PRIMARY_GUN, 12, 30, 29L, false, true),
                        noGun()),
                1,
                3);

        assertTrue(assessment.requestRequired());
        assertEquals(ArmoryResupplyNeed.NeedStatus.REQUEST_REQUIRED, assessment.status());
        assertEquals(ArmoryResupplyWeaponRole.PRIMARY, assessment.weaponRole());
        assertEquals(PRIMARY_GUN, assessment.gunId());
        assertEquals(ArmoryResupplyUrgency.LOW_RESERVE, assessment.urgency());
        assertEquals(61L, assessment.requestedUnits());
        assertEquals(90L, assessment.targetReserveUnits());
    }

    @Test
    void dryWeaponReceivesCriticalUrgency() {
        ArmoryResupplyNeed.Assessment assessment = assess(
                loadout(
                        GuardWeaponAmmunition.ActiveRole.PRIMARY,
                        physical(PRIMARY_GUN, 0, 30, 0L, false, true),
                        noGun()),
                1,
                3);

        assertEquals(ArmoryResupplyUrgency.OUT_OF_AMMO, assessment.urgency());
        assertEquals(90L, assessment.requestedUnits());
    }

    @Test
    void activeSidearmDeterminesTheRequestWithoutReadingTheStowedPrimaryRole() {
        ArmoryResupplyNeed.Assessment assessment = assess(
                new GuardWeaponAmmunition.LoadoutSummary(
                        SidearmState.SIDEARM_ACTIVE,
                        GuardWeaponAmmunition.ActiveRole.SIDEARM,
                        physical(PRIMARY_GUN, 30, 30, 300L, false, true),
                        physical(SIDEARM_GUN, 0, 15, 0L, false, true)),
                1,
                3);

        assertEquals(ArmoryResupplyWeaponRole.SIDEARM, assessment.weaponRole());
        assertEquals(SIDEARM_GUN, assessment.gunId());
        assertEquals(45L, assessment.requestedUnits());
    }

    @Test
    void reserveAtTheTriggerDoesNotCreateBackgroundWork() {
        ArmoryResupplyNeed.Assessment assessment = assess(
                loadout(
                        GuardWeaponAmmunition.ActiveRole.PRIMARY,
                        physical(PRIMARY_GUN, 30, 30, 30L, false, true),
                        noGun()),
                1,
                3);

        assertEquals(ArmoryResupplyNeed.NeedStatus.SUFFICIENT_RESERVE, assessment.status());
        assertFalse(assessment.requestRequired());
        assertEquals(0L, assessment.requestedUnits());
    }

    @Test
    void dummyInfiniteIncompleteAndRecoveryInputsFailClosed() {
        assertEquals(
                ArmoryResupplyNeed.NeedStatus.NON_PHYSICAL_RESERVE,
                assess(loadout(
                        GuardWeaponAmmunition.ActiveRole.PRIMARY,
                        snapshot(PRIMARY_GUN, GuardAmmoSummary.ReserveSource.DUMMY,
                                0, 30, 0L, false, true),
                        noGun()), 1, 3).status());
        assertEquals(
                ArmoryResupplyNeed.NeedStatus.INFINITE_RESERVE,
                assess(loadout(
                        GuardWeaponAmmunition.ActiveRole.PRIMARY,
                        physical(PRIMARY_GUN, 0, 30, 0L, true, true),
                        noGun()), 1, 3).status());
        assertEquals(
                ArmoryResupplyNeed.NeedStatus.RESERVE_SCAN_INCOMPLETE,
                assess(loadout(
                        GuardWeaponAmmunition.ActiveRole.PRIMARY,
                        physical(PRIMARY_GUN, 0, 30, 0L, false, false),
                        noGun()), 1, 3).status());
        assertEquals(
                ArmoryResupplyNeed.NeedStatus.RECOVERY_REQUIRED,
                assess(new GuardWeaponAmmunition.LoadoutSummary(
                        SidearmState.RECOVERY_REQUIRED,
                        GuardWeaponAmmunition.ActiveRole.RECOVERY_REQUIRED,
                        noGun(),
                        noGun()), 1, 3).status());
        assertEquals(
                ArmoryResupplyNeed.NeedStatus.RECOVERY_REQUIRED,
                assess(new GuardWeaponAmmunition.LoadoutSummary(
                        SidearmState.PRIMARY_ACTIVE,
                        GuardWeaponAmmunition.ActiveRole.SIDEARM,
                        physical(PRIMARY_GUN, 0, 30, 0L, false, true),
                        physical(SIDEARM_GUN, 0, 15, 0L, false, true)),
                        1,
                        3).status());
    }

    @Test
    void thresholdsAreBoundedAndAZeroTriggerDisablesRequests() {
        assertEquals(
                ArmoryResupplyNeed.NeedStatus.INVALID_THRESHOLDS,
                assess(loadout(GuardWeaponAmmunition.ActiveRole.PRIMARY,
                        physical(PRIMARY_GUN, 0, 30, 0L, false, true), noGun()),
                        4, 3).status());
        assertEquals(
                ArmoryResupplyNeed.NeedStatus.SUFFICIENT_RESERVE,
                assess(loadout(GuardWeaponAmmunition.ActiveRole.PRIMARY,
                        physical(PRIMARY_GUN, 0, 30, 0L, false, true), noGun()),
                        0, 3).status());
    }

    @Test
    void requiredAssessmentRejectsContradictoryOrUnboundedQuantities() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArmoryResupplyNeed.Assessment(
                        ArmoryResupplyNeed.NeedStatus.REQUEST_REQUIRED,
                        ArmoryResupplyWeaponRole.PRIMARY,
                        PRIMARY_GUN,
                        null,
                        30,
                        0,
                        0L,
                        90L,
                        90L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ArmoryResupplyNeed.Assessment(
                        ArmoryResupplyNeed.NeedStatus.REQUEST_REQUIRED,
                        ArmoryResupplyWeaponRole.PRIMARY,
                        PRIMARY_GUN,
                        ArmoryResupplyUrgency.OUT_OF_AMMO,
                        30,
                        0,
                        0L,
                        90L,
                        ArmoryResupplyLimits.HARD_MAX_REQUESTED_UNITS + 1L));
    }

    private static ArmoryResupplyNeed.Assessment assess(
            GuardWeaponAmmunition.LoadoutSummary loadout,
            int low,
            int target) {
        return ArmoryResupplyNeed.assess(loadout, low, target);
    }

    private static GuardWeaponAmmunition.LoadoutSummary loadout(
            GuardWeaponAmmunition.ActiveRole activeRole,
            GuardAmmoSummary.Snapshot primary,
            GuardAmmoSummary.Snapshot sidearm) {
        return new GuardWeaponAmmunition.LoadoutSummary(
                SidearmState.PRIMARY_ACTIVE,
                activeRole,
                primary,
                sidearm);
    }

    private static GuardAmmoSummary.Snapshot physical(
            ResourceLocation gunId,
            int loaded,
            int capacity,
            long reserve,
            boolean infinite,
            boolean complete) {
        return snapshot(
                gunId,
                GuardAmmoSummary.ReserveSource.PHYSICAL,
                loaded,
                capacity,
                reserve,
                infinite,
                complete);
    }

    private static GuardAmmoSummary.Snapshot snapshot(
            ResourceLocation gunId,
            GuardAmmoSummary.ReserveSource source,
            int loaded,
            int capacity,
            long reserve,
            boolean infinite,
            boolean complete) {
        return new GuardAmmoSummary.Snapshot(
                GuardAmmoSummary.Status.READY,
                gunId,
                null,
                loaded,
                capacity,
                false,
                false,
                reserve,
                infinite,
                source,
                0,
                0,
                0,
                complete);
    }

    private static GuardAmmoSummary.Snapshot noGun() {
        return new GuardAmmoSummary.Snapshot(
                GuardAmmoSummary.Status.NO_GUN,
                null,
                null,
                -1,
                -1,
                false,
                false,
                0L,
                false,
                GuardAmmoSummary.ReserveSource.NONE,
                0,
                0,
                0,
                false);
    }
}
