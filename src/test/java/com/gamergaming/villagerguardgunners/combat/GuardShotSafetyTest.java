package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.gamergaming.villagerguardgunners.combat.aim.GuardShotAim;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class GuardShotSafetyTest {
    private static final Vec3 ORIGIN = new Vec3(0, 1.6, 0);
    private static final AABB SHOOTER = new AABB(-.3, 0, -.3, .3, 1.8, .3);

    @Test void offAxisAllyUsesSampledDirectionRatherThanTargetEyeLine() {
        Scene scene = new Scene();
        scene.entities.add(ally(1, 10, 1, 2));
        assertTrue(check(envelope(new Vec3(1, 0, 0), 0), scene).clear());
        assertEquals(GuardShotSafety.Status.PROTECTED_ENTITY_IN_PATH,
                check(envelope(new Vec3(1, 0, .2), 0), scene).status());
    }

    @Test void missContinuesBeyondTheTargetAndDoesNotAssumeBlocksStopPenetration() {
        Scene scene = new Scene();
        scene.entities.add(ally(1, 38, 1, 0));
        scene.blocks.put(new BlockPos(20, 1, 0), List.of(new AABB(20, 1, 0, 21, 2, 1)));
        assertEquals(GuardShotSafety.Status.PROTECTED_ENTITY_IN_PATH,
                check(envelope(new Vec3(1, 0, 0), 0), scene).status());
        assertEquals(1, scene.queries);
        assertEquals(0, scene.blockReads, "Kinetic safety does not need distant block scans");
    }

    @Test void sameTickDirectionChangesAndMovingBurstAlliesCannotReuseApproval() {
        Scene scene = new Scene();
        GuardShotEnvelope straight = envelope(new Vec3(1, 0, 0), 0);
        scene.entities.add(ally(1, 10, 1, 2));
        assertTrue(check(straight, scene).clear());
        assertFalse(check(envelope(new Vec3(1, 0, .2), 0), scene).clear());
        scene.entities.set(0, ally(1, 10, 1, 0));
        assertFalse(check(straight, scene).clear());
        scene.entities.clear();
        assertTrue(check(straight, scene).clear(), "Refusals are not cached either");
        assertEquals(4, scene.queries);
    }

    @Test void nearWallBlocksExplosiveFireButDoesNotCreateAnUnconditionalMuzzleBlast() {
        Scene scene = new Scene();
        GuardShotEnvelope rocket = envelope(new Vec3(1, 0, 0), 3);
        scene.entities.add(ally(1, -2, .9, 2));
        assertTrue(check(rocket, scene).clear(), "Owner beside/behind guard is allowed in open space");
        scene.blocks.put(new BlockPos(2, 1, 0), List.of(new AABB(2, 1, -.5, 3, 2, .5)));
        var result = check(rocket, scene);
        assertEquals(GuardShotSafety.Status.EXPLOSIVE_BLOCK_IMPACT, result.status());
        assertNotNull(result.block());
        assertSame(rocket, result.envelope(), "Diagnostics retain the exact evaluated geometry");
    }

    @Test void nativeSpreadCanHitNearbyWallOutsideTheCenterRay() {
        Scene scene = new Scene();
        var b = new GuardShotEnvelope.Ballistics(5, 3, 0, 0, 3, Vec3.ZERO, 3, Integer.MAX_VALUE);
        GuardShotEnvelope rocket = prepared(aim(new Vec3(1, 0, 0)), b, .1);
        scene.blocks.put(new BlockPos(2, 1, 0), List.of(new AABB(2, 1, .12, 3, 2, .3)));
        assertEquals(GuardShotSafety.Status.EXPLOSIVE_BLOCK_IMPACT, check(rocket, scene).status());
    }

    @Test void hostileEarlyImpactAndImmediateFuseProtectShooter() {
        Scene scene = new Scene();
        scene.entities.add(new GuardShotSafety.Candidate(2, box(2, 1, 0), false));
        assertEquals(GuardShotSafety.Status.EXPLOSIVE_ENTITY_IMPACT,
                check(envelope(new Vec3(1, 0, 0), 3), scene).status());
        var instant = new GuardShotEnvelope.Ballistics(5, 10, 0, 0, 0, Vec3.ZERO, 3, 0);
        assertEquals(GuardShotSafety.Status.UNSAFE_FUSE,
                check(prepared(aim(new Vec3(1, 0, 0)), instant, .1), new Scene()).status());
    }

    @Test void forwardBlastProtectsAlliesBesideThePath() {
        Scene scene = new Scene();
        scene.entities.add(ally(1, 20, 1, 2));
        assertTrue(check(envelope(new Vec3(1, 0, 0), 0), scene).clear());
        assertFalse(check(envelope(new Vec3(1, 0, 0), 3), scene).clear());
    }

    @Test void verticalAndDiagonalShotsHaveFiniteWorkingGeometry() {
        for (Vec3 direction : List.of(new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(1, 1, 1))) {
            GuardShotEnvelope envelope = envelope(direction, 0);
            assertTrue(envelope.intersects(AABB.ofSize(ORIGIN.add(direction.normalize().scale(20)), .1, .1, .1), 0));
            assertTrue(Double.isFinite(envelope.bounds().getSize()));
        }
    }

    @Test void capturesGravityInheritedVelocityAndEverySampledAirWaterTrajectory() {
        Random random = new Random(941);
        for (int fixture = 0; fixture < 80; fixture++) {
            var aim = aim(new Vec3(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5));
            double speed = 3 + random.nextDouble() * 8;
            double gravity = random.nextDouble() * .15;
            double friction = random.nextDouble() * .7;
            double inaccuracy = random.nextDouble() * 2;
            Vec3 inherited = new Vec3(.2, -.15, .3);
            var b = new GuardShotEnvelope.Ballistics(speed, 20, gravity, friction, inaccuracy,
                    inherited, 0, Integer.MAX_VALUE);
            var envelope = prepared(aim, b, .1);
            for (int sample = 0; sample < 50; sample++) {
                Vec3 error = new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1,
                        random.nextDouble() * 2 - 1).scale(.0172275 * inaccuracy);
                Vec3 velocity = aim.direction().add(error).scale(speed).add(inherited);
                Vec3 position = ORIGIN;
                for (int tick = 0; tick < 20; tick++) {
                    // Include intermediate collision positions, not just tick endpoints.
                    Vec3 midway = position.add(velocity.scale(.5));
                    assertTrue(envelope.intersects(AABB.ofSize(midway, .001, .001, .001), 0));
                    position = position.add(velocity);
                    assertTrue(envelope.intersects(AABB.ofSize(position, .001, .001, .001), 0));
                    boolean water = random.nextBoolean();
                    velocity = velocity.scale(water ? 1 - .4F : (double) (1 - (float) friction))
                            .add(0, -(water ? .6 * gravity : gravity), 0);
                }
            }
        }
    }

    @Test void invalidGeometryAndOversizedProfilesFailClosedBeforeQuerying() {
        var normal = ballistic(0);
        var a = aim(new Vec3(1, 0, 0));
        assertEquals(GuardShotEnvelope.Failure.INVALID_AIM, GuardShotEnvelope.prepare(
                new GuardShotAim(1, 0, ORIGIN, ORIGIN, Vec3.ZERO, new Vec3(Double.NaN, 0, 0), 0, 0), normal, .1).failure());
        assertEquals(GuardShotEnvelope.Failure.INVALID_AIM, GuardShotEnvelope.prepare(
                new GuardShotAim(1, 0, ORIGIN, ORIGIN, Vec3.ZERO, new Vec3(1, 0, 0), 0, 0), normal, .1).failure());
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertFalse(GuardShotEnvelope.prepare(a,
                    new GuardShotEnvelope.Ballistics(value, 10, 0, 0, 0, Vec3.ZERO, 0, 99), .1).valid());
        }
        assertFalse(GuardShotEnvelope.prepare(a,
                new GuardShotEnvelope.Ballistics(5, 201, 0, 0, 0, Vec3.ZERO, 0, 99), .1).valid());
        assertFalse(GuardShotEnvelope.prepare(a,
                new GuardShotEnvelope.Ballistics(500, 10, 0, 0, 0, Vec3.ZERO, 0, 99), .1).valid());
        assertFalse(GuardShotEnvelope.prepare(a,
                new GuardShotEnvelope.Ballistics(5, 10, 0, 1.1, 0, Vec3.ZERO, 0, 99), .1).valid());
        assertFalse(GuardShotEnvelope.prepare(a,
                new GuardShotEnvelope.Ballistics(5, 10, 0, 0, 90, Vec3.ZERO, 0, 99), .1).valid());
    }

    @Test void entityAndBlockBudgetsAndUnloadedImpactsFailClosed() {
        Scene crowded = new Scene();
        for (int id = 0; id <= GuardShotSafety.MAX_ENTITIES; id++)
            crowded.entities.add(new GuardShotSafety.Candidate(id, box(20, 1, 10), false));
        assertEquals(GuardShotSafety.Status.ENTITY_LIMIT,
                check(envelope(new Vec3(1, 0, 0), 0), crowded).status());
        Scene unloaded = new Scene();
        unloaded.unloaded = true;
        assertEquals(GuardShotSafety.Status.UNLOADED_IMPACT_AREA,
                check(envelope(new Vec3(1, 0, 0), 3), unloaded).status());
        assertEquals(GuardShotSafety.Status.BLOCK_LIMIT,
                check(envelope(new Vec3(1, 0, 0), 25), new Scene()).status());
    }

    @Test void ordinaryDefaultGunTravelFitsBounds() {
        for (double[] gun : new double[][]{{15,16,.15,.025}, {25,16,.15,.02},
                {31,15,.15,.015}, {38,20,.15,.015}, {8,60,.015,.005}}) {
            var b = new GuardShotEnvelope.Ballistics(gun[0], (int)gun[1], gun[2], gun[3], .75,
                    Vec3.ZERO, gun[0] == 8 ? 6 : 0, Integer.MAX_VALUE);
            var envelope = prepared(aim(new Vec3(1, 0, 1)), b, .75);
            assertTrue(check(envelope, new Scene()).clear());
        }
    }

    private static GuardShotSafety.Result check(GuardShotEnvelope envelope, Scene scene) {
        return GuardShotSafety.check(envelope, SHOOTER, scene);
    }
    private static GuardShotEnvelope envelope(Vec3 direction, double blast) {
        return prepared(aim(direction), ballistic(blast), .1);
    }
    private static GuardShotEnvelope.Ballistics ballistic(double blast) {
        return new GuardShotEnvelope.Ballistics(5, 10, 0, 0, 0, Vec3.ZERO, blast, Integer.MAX_VALUE);
    }
    private static GuardShotEnvelope prepared(GuardShotAim aim, GuardShotEnvelope.Ballistics b, double clearance) {
        var prepared = GuardShotEnvelope.prepare(aim, b, clearance);
        assertTrue(prepared.valid(), () -> "Rejected geometry: " + prepared.failure());
        return prepared.envelope();
    }
    private static GuardShotAim aim(Vec3 direction) {
        Vec3 unit = direction.normalize();
        float yaw = (float)Math.toDegrees(Math.atan2(-unit.x, unit.z));
        float pitch = (float)-Math.toDegrees(Math.asin(unit.y));
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new GuardShotAim(1, 0, ORIGIN, ORIGIN, ORIGIN.add(16, 0, 0),
                new Vec3(-Math.sin(y)*Math.cos(p), -Math.sin(p), Math.cos(y)*Math.cos(p)), yaw, pitch);
    }
    private static AABB box(double x, double y, double z) { return AABB.ofSize(new Vec3(x,y,z), .6, 1.8, .6); }
    private static GuardShotSafety.Candidate ally(int id, double x, double y, double z) {
        return new GuardShotSafety.Candidate(id, box(x,y,z), true);
    }
    private static final class Scene implements GuardShotSafety.Scene {
        final List<GuardShotSafety.Candidate> entities = new ArrayList<>();
        final Map<BlockPos, List<AABB>> blocks = new HashMap<>();
        int queries, blockReads;
        boolean unloaded;
        public List<GuardShotSafety.Candidate> entities(AABB bounds, int limit) {
            queries++;
            assertEquals(GuardShotSafety.MAX_ENTITIES + 1, limit);
            return List.copyOf(entities);
        }
        public List<AABB> blockShapes(BlockPos pos) {
            blockReads++;
            return unloaded ? null : blocks.getOrDefault(pos, List.of());
        }
    }
}
