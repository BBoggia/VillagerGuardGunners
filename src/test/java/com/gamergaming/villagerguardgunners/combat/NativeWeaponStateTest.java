package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NativeWeaponStateTest {
    @Test
    void dryWeaponYieldsUntilTheExactRetryTick() {
        NativeWeaponState state = new NativeWeaponState();
        assertEquals(0L, state.remainingDryTicks(100L));
        state.markDry(100L, 20);

        assertFalse(state.mayEngage(100L));
        assertEquals(20L, state.remainingDryTicks(100L));
        assertFalse(state.mayEngage(119L));
        assertEquals(1L, state.remainingDryTicks(119L));
        assertTrue(state.mayEngage(120L));
        assertEquals(0L, state.remainingDryTicks(120L));
    }

    @Test
    void clearingDryStateDoesNotClearAnInvalidWeaponSuspension() {
        NativeWeaponState state = new NativeWeaponState();
        state.markDry(100L, 20);
        state.suspend();
        state.clearDry();

        assertFalse(state.mayEngage(1_000L));
        assertTrue(state.isSuspended());
    }

    @Test
    void changingWeaponsClearsDryAndSuspendedState() {
        NativeWeaponState state = new NativeWeaponState();
        state.markDry(100L, 20);
        state.suspend();

        state.resetForWeaponChange();

        assertTrue(state.mayEngage(100L));
        assertFalse(state.isSuspended());
    }

    @Test
    void aReloadedGunDefinitionCanResumeWithoutChangingTheHeldStack() {
        NativeWeaponState state = new NativeWeaponState();
        state.suspend();

        state.resume();

        assertTrue(state.mayEngage(100L));
        assertFalse(state.isSuspended());
    }
}
