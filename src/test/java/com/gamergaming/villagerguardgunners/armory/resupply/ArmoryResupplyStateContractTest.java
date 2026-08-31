package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ArmoryResupplyStateContractTest {
    @Test
    void successfulAttemptUsesOneExplicitPath() {
        ArmoryResupplyState state = ArmoryResupplyState.IDLE;
        state = transition(state, ArmoryResupplyEvent.REQUEST_CREATED);
        assertEquals(ArmoryResupplyState.SEARCHING, state);
        state = transition(state, ArmoryResupplyEvent.CANDIDATE_RESERVED);
        assertEquals(ArmoryResupplyState.RESERVED, state);
        state = transition(state, ArmoryResupplyEvent.TRAVEL_STARTED);
        assertEquals(ArmoryResupplyState.TRAVELLING, state);
        state = transition(state, ArmoryResupplyEvent.WITHDRAWAL_REACHED);
        assertEquals(ArmoryResupplyState.WITHDRAWING, state);
        state = transition(state, ArmoryResupplyEvent.TRANSFER_SUCCEEDED);
        assertEquals(ArmoryResupplyState.COOLDOWN, state);
        state = transition(state, ArmoryResupplyEvent.RETRY_READY);
        assertEquals(ArmoryResupplyState.IDLE, state);
    }

    @Test
    void adjacentArmoryMaySkipNavigationButNotReservation() {
        ArmoryResupplyState state = transition(
                ArmoryResupplyState.IDLE,
                ArmoryResupplyEvent.REQUEST_CREATED);
        state = transition(state, ArmoryResupplyEvent.CANDIDATE_RESERVED);
        state = transition(state, ArmoryResupplyEvent.WITHDRAWAL_REACHED);

        assertEquals(ArmoryResupplyState.WITHDRAWING, state);
        assertTrue(state.ownsReservation());
        assertFalse(state.ownsNavigation());
    }

    @Test
    void candidateExhaustionAndPathFailureUseDifferentBackoffStates() {
        assertEquals(
                ArmoryResupplyState.COOLDOWN,
                transition(ArmoryResupplyState.SEARCHING, ArmoryResupplyEvent.NO_SUPPLY));
        assertEquals(
                ArmoryResupplyState.UNREACHABLE,
                transition(ArmoryResupplyState.TRAVELLING, ArmoryResupplyEvent.PATH_FAILED));
    }

    @Test
    void everyReservationExitAndNavigationExitIsObservable() {
        assertTrue(ArmoryResupplyStateContract.releasesReservation(
                ArmoryResupplyState.RESERVED,
                ArmoryResupplyEvent.CANCELLED));
        assertTrue(ArmoryResupplyStateContract.releasesReservation(
                ArmoryResupplyState.TRAVELLING,
                ArmoryResupplyEvent.PATH_FAILED));
        assertTrue(ArmoryResupplyStateContract.releasesReservation(
                ArmoryResupplyState.WITHDRAWING,
                ArmoryResupplyEvent.TRANSFER_SUCCEEDED));
        assertTrue(ArmoryResupplyStateContract.releasesNavigation(
                ArmoryResupplyState.TRAVELLING,
                ArmoryResupplyEvent.WITHDRAWAL_REACHED));
        assertFalse(ArmoryResupplyStateContract.releasesReservation(
                ArmoryResupplyState.SEARCHING,
                ArmoryResupplyEvent.NO_SUPPLY));
    }

    @Test
    void invalidTransitionsFailInsteadOfLeavingStaleOwnership() {
        assertThrows(
                IllegalStateException.class,
                () -> transition(
                        ArmoryResupplyState.IDLE,
                        ArmoryResupplyEvent.CANDIDATE_RESERVED));
        assertThrows(
                IllegalStateException.class,
                () -> transition(
                        ArmoryResupplyState.TRAVELLING,
                        ArmoryResupplyEvent.TRANSFER_SUCCEEDED));
        assertThrows(
                NullPointerException.class,
                () -> transition(null, ArmoryResupplyEvent.CANCELLED));
    }

    private static ArmoryResupplyState transition(
            ArmoryResupplyState state,
            ArmoryResupplyEvent event) {
        return ArmoryResupplyStateContract.transition(state, event);
    }
}
