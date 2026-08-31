package com.gamergaming.villagerguardgunners.sidearm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SidearmStateContractTest {
    @Test
    void ordinaryLayoutAllowsAnEmptyOrOccupiedConfiguredSidearmSlot() {
        assertEquals(
                SidearmState.PRIMARY_ACTIVE,
                SidearmStateContract.resolve(false, false, false, false));
        assertEquals(
                SidearmState.PRIMARY_ACTIVE,
                SidearmStateContract.resolve(false, true, false, true));
    }

    @Test
    void activeLayoutRequiresMainHandAndStowedPrimaryWithEmptySidearmSlot() {
        assertEquals(
                SidearmState.SIDEARM_ACTIVE,
                SidearmStateContract.resolve(true, false, true, true));
        assertEquals(
                SidearmState.RECOVERY_REQUIRED,
                SidearmStateContract.resolve(true, true, true, true));
        assertEquals(
                SidearmState.RECOVERY_REQUIRED,
                SidearmStateContract.resolve(true, false, false, true));
        assertEquals(
                SidearmState.RECOVERY_REQUIRED,
                SidearmStateContract.resolve(true, false, true, false));
    }

    @Test
    void unexpectedStowedPrimaryNeverGetsGuessedIntoTheMainHand() {
        assertEquals(
                SidearmState.RECOVERY_REQUIRED,
                SidearmStateContract.resolve(false, true, true, true));
    }

    @Test
    void networkIdsAreStableAndUnknownValuesFailClosed() {
        assertEquals(0, SidearmState.PRIMARY_ACTIVE.networkId());
        assertEquals(1, SidearmState.SIDEARM_ACTIVE.networkId());
        assertEquals(2, SidearmState.RECOVERY_REQUIRED.networkId());
        assertEquals(SidearmState.PRIMARY_ACTIVE, SidearmState.fromNetworkId(0));
        assertEquals(SidearmState.SIDEARM_ACTIVE, SidearmState.fromNetworkId(1));
        assertEquals(SidearmState.RECOVERY_REQUIRED, SidearmState.fromNetworkId(2));
        assertEquals(SidearmState.RECOVERY_REQUIRED, SidearmState.fromNetworkId(-1));
        assertEquals(SidearmState.RECOVERY_REQUIRED, SidearmState.fromNetworkId(99));
    }

    @Test
    void successfulAndRolledBackPostconditionsAreExplicit() {
        assertEquals(SidearmTransitionStatus.ACTIVATED,
                SidearmTransitionResult.activated().status());
        assertEquals(SidearmTransitionStatus.RETURNED,
                SidearmTransitionResult.returned().status());
        assertEquals(
                SidearmState.PRIMARY_ACTIVE,
                SidearmTransitionResult.rolledBack(SidearmState.PRIMARY_ACTIVE).after());
        assertEquals(
                SidearmState.SIDEARM_ACTIVE,
                SidearmTransitionResult.rolledBack(SidearmState.SIDEARM_ACTIVE).after());
    }

    @Test
    void incompleteRollbackMustSurfaceRecoveryState() {
        assertEquals(
                SidearmState.RECOVERY_REQUIRED,
                SidearmTransitionResult.rollbackFailed(SidearmState.PRIMARY_ACTIVE).after());
        assertEquals(
                SidearmState.RECOVERY_REQUIRED,
                SidearmTransitionResult.recoveryRequired(SidearmState.SIDEARM_ACTIVE).after());
    }

    @Test
    void contradictoryTransitionResultsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SidearmTransitionResult(
                        SidearmState.PRIMARY_ACTIVE,
                        SidearmState.PRIMARY_ACTIVE,
                        SidearmTransitionStatus.ACTIVATED));
        assertThrows(
                IllegalArgumentException.class,
                () -> SidearmTransitionResult.rolledBack(SidearmState.RECOVERY_REQUIRED));
        assertThrows(
                NullPointerException.class,
                () -> new SidearmTransitionResult(
                        null,
                        SidearmState.RECOVERY_REQUIRED,
                        SidearmTransitionStatus.RECOVERY_REQUIRED));
    }
}
