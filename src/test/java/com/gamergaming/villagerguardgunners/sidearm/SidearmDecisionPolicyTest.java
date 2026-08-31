package com.gamergaming.villagerguardgunners.sidearm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SidearmDecisionPolicyTest {
    @Test
    void dryPrimaryTakesPrecedenceOverExplosiveConditions() {
        assertEquals(
                SidearmActivationReason.PRIMARY_DRY,
                SidearmDecisionPolicy.activationReason(true, true, true, false, true));
    }

    @Test
    void explosiveActivationReasonsAreNarrowAndOrdered() {
        assertEquals(
                SidearmActivationReason.EXPLOSIVE_DISALLOWED,
                SidearmDecisionPolicy.activationReason(true, false, true, false, true));
        assertEquals(
                SidearmActivationReason.EXPLOSIVE_STANDOFF,
                SidearmDecisionPolicy.activationReason(true, false, true, true, true));
        assertEquals(
                SidearmActivationReason.NONE,
                SidearmDecisionPolicy.activationReason(true, false, false, false, true));
        assertEquals(
                SidearmActivationReason.NONE,
                SidearmDecisionPolicy.activationReason(false, true, true, false, true));
    }

    @Test
    void activationGatesFailClosedWithoutMutating() {
        SidearmActivationReason reason = SidearmActivationReason.PRIMARY_DRY;
        assertEquals(
                SidearmDecision.NO_TRANSITION,
                SidearmDecisionPolicy.activationDecision(
                        SidearmActivationReason.NONE, true, true, true, true));
        assertEquals(
                SidearmDecision.BLOCKED_FEATURE_DISABLED,
                SidearmDecisionPolicy.activationDecision(reason, false, true, true, true));
        assertEquals(
                SidearmDecision.BLOCKED_SIDEARM_INELIGIBLE,
                SidearmDecisionPolicy.activationDecision(reason, true, false, true, true));
        assertEquals(
                SidearmDecision.BLOCKED_SIDEARM_NOT_READY,
                SidearmDecisionPolicy.activationDecision(reason, true, true, false, true));
        assertEquals(
                SidearmDecision.DEFERRED_NATIVE_BUSY,
                SidearmDecisionPolicy.activationDecision(reason, true, true, true, false));
        assertEquals(
                SidearmDecision.ACTIVATE,
                SidearmDecisionPolicy.activationDecision(reason, true, true, true, true));
    }

    @Test
    void dryFallbackRemainsActiveForTheSameEngagement() {
        assertEquals(
                SidearmReturnReason.NONE,
                returnReason(
                        true,
                        true,
                        true,
                        SidearmActivationReason.PRIMARY_DRY,
                        1_000,
                        true,
                        true,
                        100.0D,
                        6.0D,
                        2.0D));
        assertEquals(
                SidearmReturnReason.ENGAGEMENT_ENDED,
                returnReason(
                        true,
                        false,
                        true,
                        SidearmActivationReason.PRIMARY_DRY,
                        1,
                        true,
                        true,
                        100.0D,
                        6.0D,
                        2.0D));
    }

    @Test
    void standoffReturnRequiresMinimumTimeAndDistanceMargin() {
        assertEquals(
                SidearmReturnReason.NONE,
                returnReason(
                        true,
                        true,
                        true,
                        SidearmActivationReason.EXPLOSIVE_STANDOFF,
                        39,
                        true,
                        true,
                        20.0D,
                        6.0D,
                        2.0D));
        assertEquals(
                SidearmReturnReason.NONE,
                returnReason(
                        true,
                        true,
                        true,
                        SidearmActivationReason.EXPLOSIVE_STANDOFF,
                        40,
                        true,
                        true,
                        7.99D,
                        6.0D,
                        2.0D));
        assertEquals(
                SidearmReturnReason.PRIMARY_STANDOFF_CLEARED,
                returnReason(
                        true,
                        true,
                        true,
                        SidearmActivationReason.EXPLOSIVE_STANDOFF,
                        40,
                        true,
                        true,
                        8.0D,
                        6.0D,
                        2.0D));
    }

    @Test
    void invalidStandoffNumbersFailClosed() {
        assertFalse(SidearmDecisionPolicy.hasClearedStandoff(
                Double.NaN, 6.0D, 2.0D));
        assertFalse(SidearmDecisionPolicy.hasClearedStandoff(
                10.0D, Double.POSITIVE_INFINITY, 2.0D));
        assertFalse(SidearmDecisionPolicy.hasClearedStandoff(
                10.0D, 6.0D, -1.0D));
        assertFalse(SidearmDecisionPolicy.hasClearedStandoff(
                Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE));
        assertTrue(SidearmDecisionPolicy.hasClearedStandoff(
                8.0D, 6.0D, 2.0D));
    }

    @Test
    void orderChangeReturnsExplosivePrimaryOnlyAfterTheLockout() {
        assertEquals(
                SidearmReturnReason.NONE,
                returnReason(
                        true,
                        true,
                        true,
                        SidearmActivationReason.EXPLOSIVE_DISALLOWED,
                        39,
                        true,
                        true,
                        12.0D,
                        6.0D,
                        2.0D));
        assertEquals(
                SidearmReturnReason.PRIMARY_ALLOWED_AGAIN,
                returnReason(
                        true,
                        true,
                        true,
                        SidearmActivationReason.EXPLOSIVE_DISALLOWED,
                        40,
                        true,
                        true,
                        12.0D,
                        6.0D,
                        2.0D));
    }

    @Test
    void lifecycleReturnsBypassLockoutButNeverNativeSafety() {
        SidearmReturnReason disabled = returnReason(
                false,
                true,
                true,
                SidearmActivationReason.PRIMARY_DRY,
                0,
                true,
                false,
                0.0D,
                6.0D,
                2.0D);
        SidearmReturnReason changedTarget = returnReason(
                true,
                true,
                false,
                SidearmActivationReason.PRIMARY_DRY,
                0,
                true,
                false,
                0.0D,
                6.0D,
                2.0D);

        assertEquals(SidearmReturnReason.FEATURE_DISABLED, disabled);
        assertEquals(SidearmReturnReason.TARGET_CHANGED, changedTarget);
        assertEquals(
                SidearmDecision.DEFERRED_NATIVE_BUSY,
                SidearmDecisionPolicy.returnDecision(disabled, false));
        assertEquals(
                SidearmDecision.RETURN,
                SidearmDecisionPolicy.returnDecision(changedTarget, true));
        assertEquals(
                SidearmDecision.NO_TRANSITION,
                SidearmDecisionPolicy.returnDecision(SidearmReturnReason.NONE, true));
    }

    @Test
    void unusableBoundSidearmReturnsWithoutWaitingForHysteresis() {
        assertEquals(
                SidearmReturnReason.SIDEARM_UNUSABLE,
                SidearmDecisionPolicy.returnReason(
                        true,
                        true,
                        true,
                        false,
                        SidearmActivationReason.PRIMARY_DRY,
                        0,
                        SidearmDecisionPolicy.DEFAULT_MINIMUM_ACTIVE_TICKS,
                        false,
                        true,
                        8.0D,
                        0.0D,
                        SidearmDecisionPolicy.DEFAULT_RETURN_MARGIN_BLOCKS));
    }

    private static SidearmReturnReason returnReason(
            boolean featureEnabled,
            boolean targetActive,
            boolean sameTarget,
            SidearmActivationReason activationReason,
            long activeTicks,
            boolean primaryExplosive,
            boolean primaryAllowed,
            double targetDistance,
            double standoff,
            double margin) {
        return SidearmDecisionPolicy.returnReason(
                featureEnabled,
                targetActive,
                sameTarget,
                true,
                activationReason,
                activeTicks,
                SidearmDecisionPolicy.DEFAULT_MINIMUM_ACTIVE_TICKS,
                primaryExplosive,
                primaryAllowed,
                targetDistance,
                standoff,
                margin);
    }
}
