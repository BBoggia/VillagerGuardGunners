package com.gamergaming.villagerguardgunners.combat.aim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class GuardAimStateTest {
    private static final UUID TARGET = new UUID(3, 4);
    private static final Vec3 EYE = new Vec3(0, 1.62, 0);
    private static final Vec3 MUZZLE = new Vec3(0, 1.52, 0);
    private static final AABB BOUNDS = new AABB(15.7, 0, -.3, 16.3, 1.8, .3);
    private static final Vec3 TARGET_EYE = new Vec3(16, 1.62, 0);

    private static GuardAimState.Context context(double error) {
        return new GuardAimState.Context(TARGET, 1, 1, new GuardAimSettings(error, GuardAimPoint.EYE_LEVEL));
    }

    private static GuardShotAim prepare(GuardAimState state, GuardAimState.Context context, long tick) {
        return state.prepare(context, tick, EYE, MUZZLE, BOUNDS, TARGET_EYE).orElseThrow();
    }

    @Test void zeroErrorPreservesLegacyAnglesAndKeepsActualMuzzleSeparate() {
        for (Vec3 point : new Vec3[]{TARGET_EYE, new Vec3(-12, 9, -7), new Vec3(0, 30, 0)}) {
            var state = new GuardAimState(7);
            var aim = state.prepare(context(0), 10, EYE, MUZZLE, BOUNDS, point).orElseThrow();
            Vec3 delta = point.subtract(EYE);
            float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90;
            float pitch = (float) -(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG);
            assertEquals(yaw, aim.yaw()); assertEquals(pitch, aim.pitch());
            assertEquals(EYE, aim.aimOrigin()); assertEquals(MUZZLE, aim.projectileOrigin());
        }
    }

    @Test void centerMassUsesCurrentPoseBoundsIncludingTinyAndLowTargets() {
        var context = new GuardAimState.Context(TARGET, 1, 1, new GuardAimSettings(0, GuardAimPoint.CENTER_MASS));
        var state = new GuardAimState(1);
        for (AABB bounds : new AABB[]{BOUNDS, new AABB(5, 0, 0, 5.6, .6, .6),
                new AABB(2, 0, 0, 2.01, .01, .01)}) {
            var aim = state.prepare(context, 4, EYE, MUZZLE, bounds, TARGET_EYE).orElseThrow();
            assertEquals(bounds.getCenter(), aim.targetPoint());
            assertTrue(bounds.contains(aim.targetPoint()));
        }
    }

    @Test void seededSamplesRemainInsideConeAndAreSymmetricAcrossGuards() {
        double sumY = 0, sumZ = 0;
        for (int seed = 0; seed < 12000; seed++) {
            var state = new GuardAimState(seed);
            var aim = prepare(state, context(10), seed % 91);
            assertTrue(Math.toDegrees(Math.acos(Math.min(1, aim.direction().x))) <= 10.001);
            assertEquals(1, aim.direction().length(), 1e-12);
            sumY += aim.direction().y; sumZ += aim.direction().z;
        }
        assertEquals(0, sumY / 12000, .002);
        assertEquals(0, sumZ / 12000, .002);
    }

    @Test void verticalAndNearVerticalShotsStayFiniteAndBounded() {
        for (Vec3 point : new Vec3[]{new Vec3(0, 30, 0), new Vec3(0, -30, 0), new Vec3(.00001, 30, .00001)}) {
            for (int tick = 0; tick < 30; tick++) {
                var aim = new GuardAimState(tick).prepare(context(10), tick, EYE, MUZZLE, BOUNDS, point).orElseThrow();
                assertTrue(Float.isFinite(aim.yaw()) && Float.isFinite(aim.pitch()));
                double angle = Math.toDegrees(Math.acos(Math.min(1, aim.direction().dot(point.subtract(EYE).normalize()))));
                assertTrue(angle <= 10.001, "angle=" + angle);
            }
        }
    }

    @Test void diagnosticsAndFailedRetriesDoNotRerollAndSuccessfulShotsAdvanceOnce() {
        var state = new GuardAimState(7);
        var aim = prepare(state, context(5), 20);
        for (int i = 0; i < 100; i++) {
            assertSame(aim, state.pending().orElseThrow());
            assertSame(aim, prepare(state, context(5), 20));
        }
        assertTrue(state.commit(aim)); assertFalse(state.commit(aim));
        assertTrue(state.pending().isEmpty());
        var next = prepare(state, context(5), 20);
        assertEquals(1, next.shotIndex()); assertNotEquals(aim.direction(), next.direction());
        assertFalse(state.commit(aim)); assertSame(next, state.pending().orElseThrow());
    }

    @Test void interruptionInvalidatesProposalWithoutRerollingShotVariation() {
        var state = new GuardAimState(9);
        var original = prepare(state, context(4), 11);
        state.invalidateProposal();
        assertTrue(state.pending().isEmpty()); assertFalse(state.commit(original));
        var retry = prepare(state, context(4), 11);
        assertNotSame(original, retry); assertEquals(original, retry);
        assertFalse(state.commit(original)); assertTrue(state.commit(retry));
    }

    @Test void timeAdvancesDriftIndependentlyOfPreparationFrequency() {
        var frequent = new GuardAimState(8); var sparse = new GuardAimState(8);
        for (int tick = 0; tick < 100; tick++) prepare(frequent, context(5), tick);
        assertEquals(prepare(frequent, context(5), 100), prepare(sparse, context(5), 100));
        var before = prepare(frequent, context(5), 11);
        var after = prepare(frequent, context(5), 12);
        double angle = Math.toDegrees(Math.acos(Math.min(1, before.direction().dot(after.direction()))));
        assertTrue(angle < .3, "drift must remain smooth across interval boundaries");
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> prepare(sparse, context(5), Long.MAX_VALUE));
    }

    @Test void changedGeometryCannotConsumeEarlierApproval() {
        var state = new GuardAimState(8);
        var old = prepare(state, context(5), 10);
        var changed = state.prepare(context(5), 10, EYE, MUZZLE.add(.1, 0, 0), BOUNDS, TARGET_EYE).orElseThrow();
        assertFalse(state.commit(old)); assertTrue(state.commit(changed));
    }

    @Test void aNewTickInvalidatesAnOldProposalEvenWithZeroError() {
        var state = new GuardAimState(8);
        var old = prepare(state, context(0), 10);
        var fresh = prepare(state, context(0), 11);
        assertEquals(old.direction(), fresh.direction());
        assertFalse(state.commit(old)); assertTrue(state.commit(fresh));
    }

    @Test void targetWeaponPolicyAndClockChangesInvalidateOldProposals() {
        var initial = context(5);
        for (var changed : new GuardAimState.Context[]{
                new GuardAimState.Context(new UUID(5, 6), 1, 1, initial.settings()),
                new GuardAimState.Context(TARGET, 2, 1, initial.settings()),
                new GuardAimState.Context(TARGET, 1, 2, initial.settings()), context(2)}) {
            var state = new GuardAimState(8);
            var old = prepare(state, initial, 10);
            prepare(state, changed, 10);
            assertFalse(state.commit(old));
        }
        var state = new GuardAimState(8);
        var old = prepare(state, initial, 10); prepare(state, initial, 9);
        assertFalse(state.commit(old));
        state.reset(); assertTrue(state.pending().isEmpty());
        assertFalse(state.commit(old));
    }

    @Test void foreignAndStructurallyEqualProposalsAreNotAccepted() {
        var first = new GuardAimState(8); var second = new GuardAimState(8);
        var a = prepare(first, context(5), 0); var b = prepare(second, context(5), 0);
        assertEquals(a, b); assertFalse(first.commit(b)); assertTrue(first.commit(a));
    }

    @Test void invalidGeometryClearsPendingAndDoesNotAdvanceShotIndex() {
        var state = new GuardAimState(5); var old = prepare(state, context(4), 0);
        assertTrue(state.prepare(context(4), 0, EYE, MUZZLE, BOUNDS, EYE).isEmpty());
        assertFalse(state.commit(old)); assertEquals(0, prepare(state, context(4), 0).shotIndex());
        assertTrue(state.prepare(context(4), 0, new Vec3(Double.NaN, 0, 0), MUZZLE, BOUNDS, TARGET_EYE).isEmpty());
        assertTrue(state.prepare(context(4), 0, EYE, MUZZLE, new AABB(0, 0, 0, 0, 0, 0), TARGET_EYE).isEmpty());
        assertTrue(state.prepare(context(4), 0, EYE, MUZZLE, BOUNDS, new Vec3(Double.MAX_VALUE, 0, 0)).isEmpty());
    }

    @Test void invalidResolvedSettingsAreRejected() {
        for (double error : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 11}) {
            assertThrows(IllegalArgumentException.class, () -> new GuardAimSettings(error, GuardAimPoint.EYE_LEVEL));
        }
        assertThrows(NullPointerException.class, () -> new GuardAimSettings(1, null));
    }
}
