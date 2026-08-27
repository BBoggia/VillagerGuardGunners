package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NativeTriggerStateTest {
    @Test
    void chargeIsCappedAndBecomesReadyAtTheDatapackMaximum() {
        NativeTriggerState state = new NativeTriggerState();

        assertFalse(state.advanceCharge(0.25F, 1.0F));
        assertFalse(state.advanceCharge(0.25F, 1.0F));
        assertFalse(state.advanceCharge(0.25F, 1.0F));
        assertTrue(state.advanceCharge(0.25F, 1.0F));
        assertEquals(1.0F, state.getChargeProgress());
    }

    @Test
    void chargedShotUsesTaCZDecreaseOrDelayResetSemantics() {
        NativeTriggerState state = new NativeTriggerState();
        state.advanceCharge(1.0F, 1.0F);

        state.finishChargedShot(0.4F, false);
        assertEquals(0.6F, state.getChargeProgress(), 0.0001F);

        state.finishChargedShot(0.0F, true);
        assertEquals(0.0F, state.getChargeProgress());
    }

    @Test
    void lostOrUnsafeTriggerDecaysUsingTaCZDatapackRate() {
        NativeTriggerState state = new NativeTriggerState();
        state.advanceCharge(1.0F, 1.0F);

        state.decayCharge(0.25F);
        assertEquals(0.75F, state.getChargeProgress(), 0.0001F);

        state.decayCharge(2.0F);
        assertEquals(0.0F, state.getChargeProgress());
    }

    @Test
    void failedActionBackoffExpiresWithoutAccumulatingWork() {
        NativeTriggerState state = new NativeTriggerState();
        state.defer(3);

        assertFalse(state.canAttemptAction());
        state.tick();
        state.tick();
        assertFalse(state.canAttemptAction());
        state.tick();
        assertTrue(state.canAttemptAction());
    }

    @Test
    void malformedChargeDefinitionsFailClosedWithoutCreatingNanState() {
        NativeTriggerState state = new NativeTriggerState();

        assertAll(
                () -> assertFalse(NativeTriggerState.hasValidChargeDefinition(0.0F, 0.5F, 0.0F, 1.0F, 0.6F)),
                () -> assertFalse(NativeTriggerState.hasValidChargeDefinition(Float.NaN, 0.5F, 0.0F, 1.0F, 0.6F)),
                () -> assertFalse(NativeTriggerState.hasValidChargeDefinition(0.2F, -0.1F, 0.0F, 1.0F, 0.6F)),
                () -> assertFalse(NativeTriggerState.hasValidChargeDefinition(0.2F, 0.5F, Float.NaN, 1.0F, 0.6F)),
                () -> assertFalse(NativeTriggerState.hasValidChargeDefinition(0.2F, 0.5F, 0.0F, Float.NaN, 0.6F)),
                () -> assertFalse(NativeTriggerState.hasValidChargeDefinition(0.2F, 0.5F, 0.0F, 1.0F, Float.NaN)),
                () -> assertTrue(NativeTriggerState.hasValidChargeDefinition(0.0F, 0.5F, 0.0F, 0.0F, 0.6F)),
                () -> assertTrue(NativeTriggerState.hasValidChargeDefinition(0.2F, 0.5F, 0.0F, 1.0F, 1.1F)),
                () -> assertTrue(NativeTriggerState.hasValidChargeDefinition(0.2F, 0.5F, 0.0F, 1.0F, 0.6F)));

        assertFalse(state.advanceCharge(Float.NaN, 1.0F));
        assertEquals(0.0F, state.getChargeProgress());
        assertTrue(state.advanceCharge(0.0F, 0.0F));
        assertEquals(0.0F, state.getChargeProgress());
    }
}
