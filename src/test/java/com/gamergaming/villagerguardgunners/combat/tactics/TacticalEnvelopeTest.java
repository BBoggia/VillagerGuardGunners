package com.gamergaming.villagerguardgunners.combat.tactics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TacticalEnvelopeTest {

    @Test
    void generalistExactlyReproducesTheCurrentTacticalInputs() {
        TacticalEnvelope envelope = resolve(
                TacticalProfile.GENERALIST,
                WeaponTacticalTraits.NONE,
                24.0D,
                32.0D,
                4.0D,
                5,
                10.0D);

        assertAll(
                () -> assertEquals(0.0D, envelope.minimumFiringDistance()),
                () -> assertEquals(4.0D, envelope.retreatDistance()),
                () -> assertEquals(24.0D, envelope.preferredApproachDistance()),
                () -> assertEquals(32.0D, envelope.maximumFiringDistance()),
                () -> assertEquals(5, envelope.requiredVisibleTicks()),
                () -> assertEquals(10.0D, envelope.aimToleranceDegrees()),
                () -> assertTrue(envelope.validFiringWindow()));
    }

    @Test
    void closeRangeProfilesUseEffectiveRangeWithoutChangingNativeStatistics() {
        TacticalEnvelope skirmisher = resolve(
                TacticalProfile.SKIRMISHER,
                WeaponTacticalTraits.NONE,
                24.0D,
                32.0D,
                4.0D,
                5,
                10.0D);
        TacticalEnvelope breacher = resolve(
                TacticalProfile.BREACHER,
                new WeaponTacticalTraits(false, 9, 0.0D),
                18.0D,
                32.0D,
                4.0D,
                5,
                10.0D);

        assertAll(
                () -> assertEquals(18.0D, skirmisher.preferredApproachDistance()),
                () -> assertEquals(30.0D, skirmisher.maximumFiringDistance()),
                () -> assertEquals(4, skirmisher.requiredVisibleTicks()),
                () -> assertEquals(11.7D, breacher.preferredApproachDistance(), 0.000_001D),
                () -> assertEquals(19.8D, breacher.maximumFiringDistance(), 0.000_001D),
                () -> assertTrue(breacher.traits().multiProjectile()));
    }

    @Test
    void deliberateProfilesAdjustOnlyGuardDecisionInputs() {
        TacticalEnvelope marksman = resolve(
                TacticalProfile.MARKSMAN,
                WeaponTacticalTraits.NONE,
                48.0D,
                64.0D,
                4.0D,
                5,
                10.0D);
        TacticalEnvelope support = resolve(
                TacticalProfile.SUPPORT,
                WeaponTacticalTraits.NONE,
                35.0D,
                32.0D,
                4.0D,
                5,
                10.0D);

        assertAll(
                () -> assertEquals(8.0D, marksman.retreatDistance()),
                () -> assertEquals(48.0D, marksman.preferredApproachDistance()),
                () -> assertEquals(64.0D, marksman.maximumFiringDistance()),
                () -> assertEquals(8, marksman.requiredVisibleTicks()),
                () -> assertEquals(6.5D, marksman.aimToleranceDegrees()),
                () -> assertEquals(6.0D, support.retreatDistance()),
                () -> assertEquals(28.8D, support.preferredApproachDistance(), 0.000_001D),
                () -> assertEquals(6, support.requiredVisibleTicks()),
                () -> assertEquals(8.0D, support.aimToleranceDegrees()));
    }

    @Test
    void explosiveSafetyIsLayeredOverEverySelectedProfile() {
        WeaponTacticalTraits explosive = new WeaponTacticalTraits(true, 1, 3.0D);
        TacticalEnvelope demolitions = resolve(
                TacticalProfile.DEMOLITIONS,
                explosive,
                24.0D,
                32.0D,
                4.0D,
                5,
                10.0D);
        TacticalEnvelope overriddenGeneralist = resolve(
                TacticalProfile.GENERALIST,
                explosive,
                24.0D,
                32.0D,
                4.0D,
                5,
                10.0D);

        assertAll(
                () -> assertEquals(10.0D, demolitions.minimumFiringDistance()),
                () -> assertEquals(10.0D, demolitions.retreatDistance()),
                () -> assertEquals(8, demolitions.requiredVisibleTicks()),
                () -> assertEquals(6.5D, demolitions.aimToleranceDegrees()),
                () -> assertEquals(10.0D, overriddenGeneralist.minimumFiringDistance()),
                () -> assertEquals(10.0D, overriddenGeneralist.retreatDistance()),
                () -> assertFalse(overriddenGeneralist.canFireAtDistanceSquared(99.99D)),
                () -> assertTrue(overriddenGeneralist.canFireAtDistanceSquared(100.0D)));
    }

    @Test
    void impossibleExplosiveWindowFailsClosed() {
        TacticalEnvelope envelope = resolve(
                TacticalProfile.DEMOLITIONS,
                new WeaponTacticalTraits(true, 1, 20.0D),
                24.0D,
                32.0D,
                4.0D,
                5,
                10.0D);

        assertAll(
                () -> assertEquals(44.0D, envelope.minimumFiringDistance()),
                () -> assertEquals(44.0D, envelope.retreatDistance()),
                () -> assertEquals(44.0D, envelope.preferredApproachDistance()),
                () -> assertEquals(32.0D, envelope.maximumFiringDistance()),
                () -> assertFalse(envelope.validFiringWindow()),
                () -> assertFalse(envelope.canFireAtDistanceSquared(44.0D * 44.0D)));
    }

    @Test
    void malformedExplosivePolicyFallsBackWithoutWeakeningSafety() {
        ExplosiveStandoffPolicy policy = new ExplosiveStandoffPolicy(
                -100.0D,
                Double.POSITIVE_INFINITY);

        assertAll(
                () -> assertEquals(ExplosiveStandoffPolicy.DEFAULT, policy),
                () -> assertEquals(10.0D, policy.minimumDistance(3.0D, 4.0D)));
    }

    @Test
    void firingBandIncludesBothBoundariesAndRejectsInvalidDistances() {
        TacticalEnvelope envelope = resolve(
                TacticalProfile.DEMOLITIONS,
                new WeaponTacticalTraits(true, 1, 3.0D),
                24.0D,
                32.0D,
                4.0D,
                5,
                10.0D);

        assertAll(
                () -> assertTrue(envelope.canFireAtDistanceSquared(100.0D)),
                () -> assertTrue(envelope.canFireAtDistanceSquared(1_024.0D)),
                () -> assertFalse(envelope.canFireAtDistanceSquared(99.99D)),
                () -> assertFalse(envelope.canFireAtDistanceSquared(1_024.01D)),
                () -> assertFalse(envelope.canFireAtDistanceSquared(-1.0D)),
                () -> assertFalse(envelope.canFireAtDistanceSquared(Double.NaN)),
                () -> assertFalse(envelope.canFireAtDistanceSquared(Double.POSITIVE_INFINITY)));
    }

    @Test
    void directConstructionCannotForceAnInvalidFiringWindowOpen() {
        TacticalEnvelope envelope = new TacticalEnvelope(
                TacticalProfile.GENERALIST,
                WeaponTacticalTraits.NONE,
                40.0D,
                40.0D,
                40.0D,
                32.0D,
                5,
                10.0D,
                true);

        assertAll(
                () -> assertFalse(envelope.validFiringWindow()),
                () -> assertFalse(envelope.canFireAtDistanceSquared(1_600.0D)));
    }

    @Test
    void narrowCustomFiringCapNormalizesNonExplosiveMovementInsideTheWindow() {
        TacticalProfileDefinition narrowDefinition = new TacticalProfileDefinition(
                TacticalProfile.GENERALIST,
                4.0D,
                4.0D,
                0,
                1.0D,
                true,
                0.25D);
        TacticalEnvelope envelope = TacticalEnvelope.resolve(
                narrowDefinition,
                WeaponTacticalTraits.NONE,
                ExplosiveStandoffPolicy.DEFAULT,
                8.0D,
                32.0D,
                8.0D,
                5,
                10.0D);

        assertAll(
                () -> assertEquals(2.0D, envelope.retreatDistance()),
                () -> assertEquals(2.0D, envelope.preferredApproachDistance()),
                () -> assertEquals(2.0D, envelope.maximumFiringDistance()),
                () -> assertTrue(envelope.validFiringWindow()),
                () -> assertTrue(envelope.canFireAtDistanceSquared(4.0D)));
    }

    @Test
    void directConstructionRejectsContradictoryMovementOrdering() {
        TacticalEnvelope envelope = new TacticalEnvelope(
                TacticalProfile.GENERALIST,
                WeaponTacticalTraits.NONE,
                0.0D,
                20.0D,
                10.0D,
                32.0D,
                5,
                10.0D,
                true);

        assertFalse(envelope.validFiringWindow());
    }

    @Test
    void malformedInputsUseBoundedCompatibilityFallbacks() {
        TacticalProfileDefinition malformedDefinition = new TacticalProfileDefinition(
                TacticalProfile.GENERALIST,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                500,
                -2.0D,
                true,
                Double.NaN);
        TacticalEnvelope envelope = TacticalEnvelope.resolve(
                malformedDefinition,
                null,
                null,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                -5.0D,
                -1,
                Double.NaN);

        assertAll(
                () -> assertEquals(4.0D, envelope.retreatDistance()),
                () -> assertEquals(16.0D, envelope.preferredApproachDistance()),
                () -> assertEquals(16.0D, envelope.maximumFiringDistance()),
                () -> assertEquals(40, envelope.requiredVisibleTicks()),
                () -> assertEquals(2.5D, envelope.aimToleranceDegrees()),
                () -> assertTrue(envelope.validFiringWindow()));
    }

    private static TacticalEnvelope resolve(
            TacticalProfile profile,
            WeaponTacticalTraits traits,
            double effectiveRange,
            double maximumRange,
            double closeRange,
            int visibleTicks,
            double aimTolerance) {
        return TacticalEnvelope.resolve(
                TacticalProfileDefinition.defaultsFor(profile),
                traits,
                ExplosiveStandoffPolicy.DEFAULT,
                effectiveRange,
                maximumRange,
                closeRange,
                visibleTicks,
                aimTolerance);
    }
}
