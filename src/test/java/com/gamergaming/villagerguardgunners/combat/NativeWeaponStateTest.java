package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NativeWeaponStateTest {
    @Test
    void dryEvidenceSurvivesTheExactRetryTick() {
        NativeWeaponState state = new NativeWeaponState();
        assertEquals(0L, state.remainingDryTicks(100L));
        state.markDry(100L, 20);

        assertFalse(state.mayAttemptAction(100L));
        assertEquals(20L, state.remainingDryTicks(100L));
        assertFalse(state.mayAttemptAction(119L));
        assertEquals(1L, state.remainingDryTicks(119L));
        assertTrue(state.mayAttemptAction(120L));
        assertEquals(0L, state.remainingDryTicks(120L));
        assertTrue(state.isDryConfirmed());
        assertTrue(state.mayAttemptAction(1_000L));
        assertTrue(state.isDryConfirmed());
        state.clearDry();
        assertFalse(state.isDryConfirmed());
    }

    @Test
    void clearingDryStateDoesNotClearAnInvalidWeaponSuspension() {
        NativeWeaponState state = new NativeWeaponState();
        state.markDry(100L, 20);
        state.suspend();
        state.clearDry();

        assertFalse(state.mayAttemptAction(1_000L));
        assertTrue(state.isSuspended());
    }

    @Test
    void changingWeaponsClearsDryAndSuspendedState() {
        NativeWeaponState state = new NativeWeaponState();
        state.markDry(100L, 20);
        state.suspend();

        state.resetForWeaponChange();

        assertTrue(state.mayAttemptAction(100L));
        assertFalse(state.isSuspended());
        assertFalse(state.isDryConfirmed());
    }

    @Test
    void recoveringMetadataDoesNotEraseConfirmedExhaustion() {
        NativeWeaponState state = new NativeWeaponState();
        state.markDry(10L, 20);
        state.suspend();
        state.resume();
        assertFalse(state.mayAttemptAction(29L));
        assertTrue(state.mayAttemptAction(30L));
        assertTrue(state.isDryConfirmed());
        state.markDry(30L, 0);
        assertFalse(state.mayAttemptAction(30L));
        assertTrue(state.mayAttemptAction(31L));
        assertTrue(state.isDryConfirmed());
    }

    @Test
    void aReloadedGunDefinitionCanResumeWithoutChangingTheHeldStack() {
        NativeWeaponState state = new NativeWeaponState();
        state.suspend();

        state.resume();

        assertTrue(state.mayAttemptAction(100L));
        assertFalse(state.isSuspended());
    }
}
