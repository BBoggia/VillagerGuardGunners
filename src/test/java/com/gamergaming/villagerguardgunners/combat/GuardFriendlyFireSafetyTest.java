package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class GuardFriendlyFireSafetyTest {
    @Test
    void detectsAHitAnywhereInTheProtectedEntityBoundingBox() {
        AABB tallBounds = new AABB(4.0D, 0.0D, -0.5D, 6.0D, 4.0D, 0.5D);

        assertTrue(GuardFriendlyFireSafety.intersectsInflatedBounds(
                tallBounds,
                new Vec3(0.0D, 3.75D, 0.0D),
                new Vec3(10.0D, 3.75D, 0.0D),
                0.0F));
    }

    @Test
    void inflationConservativelyReservesSpreadAroundTheShot() {
        AABB bounds = new AABB(4.0D, 0.0D, 1.0D, 6.0D, 2.0D, 2.0D);

        assertFalse(GuardFriendlyFireSafety.intersectsInflatedBounds(
                bounds,
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(10.0D, 1.0D, 0.0D),
                0.9F));
        assertTrue(GuardFriendlyFireSafety.intersectsInflatedBounds(
                bounds,
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(10.0D, 1.0D, 0.0D),
                1.0F));
    }

    @Test
    void detectsWhenTheShotStartsInsideProtectedBounds() {
        AABB bounds = new AABB(-1.0D, 0.0D, -1.0D, 1.0D, 2.0D, 1.0D);

        assertTrue(GuardFriendlyFireSafety.intersectsInflatedBounds(
                bounds,
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(10.0D, 1.0D, 0.0D),
                0.0F));
    }

    @Test
    void explosiveEnvelopeAllowsProtectedOwnerBehindAndBesideShooter() {
        Vec3 start = new Vec3(0.0D, 1.5D, 0.0D);
        Vec3 end = new Vec3(0.0D, 1.75D, -16.0D);
        AABB ownerBounds = AABB.ofSize(
                new Vec3(2.625D, 0.9D, 2.138D),
                0.6D,
                1.8D,
                0.6D);

        assertTrue(GuardFriendlyFireSafety.intersectsInflatedBounds(
                ownerBounds,
                start,
                end,
                6.231621F), "The previous uniform blast corridor should reproduce the reported block");
        assertFalse(GuardFriendlyFireSafety.intersectsSafetyEnvelope(
                ownerBounds,
                start,
                end,
                0.75F,
                3.231621F,
                6.231621F), "Spread and blast padding should widen only after leaving the guard");
    }

    @Test
    void widenedEnvelopeDoesNotWrapSidewaysAroundShooter() {
        Vec3 start = new Vec3(0.0D, 1.5D, 0.0D);
        Vec3 end = new Vec3(0.0D, 1.75D, -16.0D);
        AABB ownerBounds = AABB.ofSize(
                new Vec3(3.4D, 0.9D, 0.0D),
                0.6D,
                1.8D,
                0.6D);

        assertFalse(GuardFriendlyFireSafety.intersectsSafetyEnvelope(
                ownerBounds,
                start,
                end,
                0.75F,
                3.231621F,
                6.231621F), "Forward envelope end caps should not recreate a wide muzzle bubble");
    }

    @Test
    void explosiveEnvelopeStillProtectsDirectLaneAndForwardBlastCorridor() {
        Vec3 start = new Vec3(0.0D, 1.5D, 0.0D);
        Vec3 end = new Vec3(0.0D, 1.75D, -16.0D);
        AABB directlyBehind = AABB.ofSize(
                new Vec3(0.0D, 0.9D, 1.0D),
                0.6D,
                1.8D,
                0.6D);
        AABB besideForwardPath = AABB.ofSize(
                new Vec3(4.0D, 0.9D, -10.0D),
                0.6D,
                1.8D,
                0.6D);

        assertTrue(GuardFriendlyFireSafety.intersectsSafetyEnvelope(
                directlyBehind,
                start,
                end,
                0.75F,
                3.231621F,
                6.231621F), "Direct projectile clearance must still protect the muzzle line");
        assertTrue(GuardFriendlyFireSafety.intersectsSafetyEnvelope(
                besideForwardPath,
                start,
                end,
                0.75F,
                3.231621F,
                6.231621F), "Full blast clearance must remain active along the forward path");
    }

    @Test
    void convertsTaCZComponentInaccuracyIntoAConservativeDistanceAwareClearance() {
        double componentError = 0.0172275D * 5.0D;
        double vectorError = Math.sqrt(3.0D) * componentError;
        assertEquals(
                20.0D * vectorError / (1.0D - vectorError),
                NativeGunController.calculateSpreadRadius(20.0D, 5.0F),
                0.000001D);
    }

    @Test
    void reservesSpaceForProtectedEntitiesMovingDuringANativeBurst() {
        assertEquals(
                3.6D,
                NativeGunController.calculateBurstMovementPadding(600L),
                0.000001D);
    }

    @Test
    void failsClosedWhenSpreadCanReverseTheForwardComponent() {
        assertEquals(
                Double.POSITIVE_INFINITY,
                NativeGunController.calculateSpreadRadius(10.0D, 90.0F));
    }
}
