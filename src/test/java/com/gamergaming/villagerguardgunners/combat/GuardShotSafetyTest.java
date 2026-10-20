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
                new GuardShotEnvelope.Ballistics(5, GuardShotEnvelope.MAX_LIFE_TICKS + 1, 0, 0, 0, Vec3.ZERO, 0, 99), .1).valid());
        assertFalse(GuardShotEnvelope.prepare(a,
                new GuardShotEnvelope.Ballistics(5000, 10, 0, 0, 0, Vec3.ZERO, 0, 99), .1).valid());
        assertFalse(GuardShotEnvelope.prepare(a,
                new GuardShotEnvelope.Ballistics(5, 10, 0, 1.1, 0, Vec3.ZERO, 0, 99), .1).valid());
        assertFalse(GuardShotEnvelope.prepare(a,
                new GuardShotEnvelope.Ballistics(5, 10, 0, 0, 90, Vec3.ZERO, 0, 99), .1).valid());
    }

    @Test void entityAndBlockBudgetsAndUnloadedImpactsFailClosed() {
        Scene crowded = new Scene();
        for (int id = 0; id <= GuardShotSafety.MAX_ENTITIES; id++)
            crowded.entities.add(new GuardShotSafety.Candidate(id, box(20, 1, 0), false));
        assertEquals(GuardShotSafety.Status.ENTITY_LIMIT,
                check(envelope(new Vec3(1, 0, 0), 0), crowded).status());
        Scene unloaded = new Scene();
        unloaded.unloaded = true;
        assertEquals(GuardShotSafety.Status.UNLOADED_IMPACT_AREA,
                check(envelope(new Vec3(1, 0, 0), 3), unloaded).status());
        Scene oversized = new Scene();
        oversized.wet = true;
        assertEquals(GuardShotSafety.Status.BLOCK_LIMIT,
                check(envelope(new Vec3(1, 0, 0), 25), oversized).status());
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

    @Test void communityGunProfilesSupportFullFlightInEveryDirection() {
        // Published pack values, TaCZ default speed multiplier (2), and native spread margin (.5).
        double[][] guns = {{34,56,.0245,.01,.65}, {67,156,.098,.015,.55},
                {28,46,.15,.015,.675}, {97,246,.0245,.025,.7},
                {57,36,.098,.015,.55}, {57,146,.0245,.025,.7}, {40,22,.15,.03,4}};
        for (double[] gun : guns) for (Vec3 direction : List.of(new Vec3(1,0,0), new Vec3(-1,0,1),
                new Vec3(1,.4,1), new Vec3(0,1,0), new Vec3(0,-1,0))) {
            var b = new GuardShotEnvelope.Ballistics(gun[0], (int)gun[1], gun[2], gun[3], gun[4],
                    Vec3.ZERO, 0, Integer.MAX_VALUE);
            var env = prepared(aim(direction), b, .75);
            assertTrue(check(env, new Scene()).clear());
            Scene blocked = new Scene();
            blocked.entities.add(new GuardShotSafety.Candidate(77,
                    AABB.ofSize(ORIGIN.add(direction.normalize().scale(80)), .6, 1.8, .6), true));
            assertEquals(GuardShotSafety.Status.PROTECTED_ENTITY_IN_PATH, check(env, blocked).status());
        }
    }

    @Test void largeBlastCountsOnlyPossibleImpactBlocks() {
        var b = new GuardShotEnvelope.Ballistics(5, 100, .08, .05, 1, Vec3.ZERO, 12, 600);
        var env = prepared(aim(new Vec3(1,0,0)), b, .75);
        Scene open = new Scene();
        assertTrue(check(env, open).clear());
        assertTrue(open.blockReads < GuardShotSafety.MAX_BLOCKS);
        open.blocks.put(new BlockPos(3,1,0), List.of(new AABB(3,1,0,4,2,1)));
        assertEquals(GuardShotSafety.Status.EXPLOSIVE_BLOCK_IMPACT, check(env, open).status());
    }

    @Test void m79AboveDryGroundCanFire() {
        var b = new GuardShotEnvelope.Ballistics(5, 100, .08, .05, 1, Vec3.ZERO, 12, 600);
        Scene scene = new Scene();
        for (int x=-16;x<32;x++) for(int z=-16;z<16;z++)
            scene.blocks.put(new BlockPos(x,-1,z),List.of(new AABB(x,-1,z,x+1,0,z+1)));
        var env=prepared(aim(new Vec3(1,0,0)),b,.75);
        assertTrue(check(env,scene).clear());
        scene.wet=true;
        assertEquals(GuardShotSafety.Status.EXPLOSIVE_BLOCK_IMPACT, check(env,scene).status());
        scene.wet=false;
        scene.unloaded=true;
        assertEquals(GuardShotSafety.Status.UNLOADED_IMPACT_AREA, check(env,scene).status());
    }

    @Test void queryStripsContainSampledLongAirWaterFlights() {
        Random random = new Random(77201);
        for (int fixture=0; fixture<40; fixture++) {
            var a=aim(new Vec3(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5));
            var b=new GuardShotEnvelope.Ballistics(67,156,.098,.015,.55,new Vec3(.2,-.3,.2),0,999);
            var env=prepared(a,b,.75);
            for (int sample=0;sample<8;sample++) {
                Vec3 velocity=a.direction().add(new Vec3(random.nextDouble()*2-1,random.nextDouble()*2-1,
                        random.nextDouble()*2-1).scale(.0172275*b.inaccuracy())).scale(b.speed()).add(b.inheritedVelocity());
                Vec3 position=ORIGIN;
                for(int tick=0;tick<b.lifeTicks();tick++) {
                    Vec3 point=position.add(velocity.scale(.5));
                    assertTrue(env.queries().stream().anyMatch(q->q.contains(point)),"Query missed swept flight");
                    assertTrue(env.intersects(AABB.ofSize(point,.001,.001,.001),0));
                    position=position.add(velocity);
                    boolean water=sample>0 && random.nextBoolean();
                    velocity=velocity.scale(water?1-.4F:(double)(1-(float)b.friction()))
                            .add(0,-(water?.6*b.gravity():b.gravity()),0);
                }
            }
        }
    }

    @Test void entityBudgetIsSharedAcrossStripsAndDuplicateEntitiesCountOnce() {
        var env=prepared(aim(new Vec3(1,0,0)),
                new GuardShotEnvelope.Ballistics(34,56,.0245,.01,.65,Vec3.ZERO,0,999),.75);
        Scene scene=new Scene();
        for(int id=0;id<=GuardShotSafety.MAX_ENTITIES;id++)
            scene.entities.add(new GuardShotSafety.Candidate(id,box(10+id*4,1,0),false));
        assertEquals(GuardShotSafety.Status.ENTITY_LIMIT,check(env,scene).status());
        scene.entities.clear();
        scene.entities.add(new GuardShotSafety.Candidate(1,new AABB(0,0,-.3,1400,2,.3),false));
        assertTrue(check(env,scene).clear());
        assertTrue(scene.queries>1);
    }

    @Test void dryEscapeRetainsAnyThreatBeforeEscapeAndCannotReverseAfterWater() {
        Random random=new Random(8131);
        for(Vec3 direction:List.of(new Vec3(1,0,0),new Vec3(-1,.4,1),new Vec3(0,-.1,-1))) {
            var a=aim(direction);
            var b=new GuardShotEnvelope.Ballistics(5,100,.08,.05,1,Vec3.ZERO,12,600);
            var full=prepared(a,b,.75);
            var danger=SHOOTER.inflate(12.75);
            var prefix=full.dryEscapePrefix(danger);
            assertNotNull(prefix);
            for(int sample=0;sample<80;sample++) {
                Vec3 velocity=a.direction().add(new Vec3(random.nextDouble()*2-1,random.nextDouble()*2-1,
                        random.nextDouble()*2-1).scale(.0172275)).scale(5);
                Vec3 position=ORIGIN;
                for(int tick=0;tick<100;tick++) {
                    Vec3 next=position.add(velocity);
                    for(Vec3 point:List.of(position,position.add(velocity.scale(.5)),next))
                        if(danger.contains(point)) assertTrue(prefix.intersects(AABB.ofSize(point,.001,.001,.001),0));
                    position=next;
                    boolean water=!prefix.bounds().contains(position) && random.nextBoolean();
                    velocity=velocity.scale(water?1-.4F:(double)(1-.05F)).add(0,water?-.048:-.08,0);
                }
            }
        }
        assertNull(prepared(aim(new Vec3(0,1,0)),
                new GuardShotEnvelope.Ballistics(5,100,.08,.05,1,Vec3.ZERO,12,600),.75)
                .dryEscapePrefix(SHOOTER.inflate(12.75)));
    }

    @Test void dryEscapeStillBlocksFuseAndNearbyCollateralAndExtendedColliders() {
        Scene scene=new Scene();
        var b=new GuardShotEnvelope.Ballistics(5,100,.08,.05,1,Vec3.ZERO,12,1);
        assertEquals(GuardShotSafety.Status.UNSAFE_FUSE,check(prepared(aim(new Vec3(1,0,0)),b,.75),scene).status());
        var env=prepared(aim(new Vec3(1,0,0)),
                new GuardShotEnvelope.Ballistics(5,100,.08,.05,1,Vec3.ZERO,12,600),.75);
        scene.entities.add(new GuardShotSafety.Candidate(9,box(4,1,0),false));
        assertEquals(GuardShotSafety.Status.EXPLOSIVE_ENTITY_IMPACT,check(env,scene).status());
        scene.entities.clear();
        scene.blocks.put(new BlockPos(3,0,0),List.of(new AABB(3,0,0,4,2,1)));
        assertEquals(GuardShotSafety.Status.EXPLOSIVE_BLOCK_IMPACT,check(env,scene).status());
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
        boolean unloaded, wet;
        public boolean isDry(BlockPos pos) { return !unloaded && !wet; }
        public List<GuardShotSafety.Candidate> entities(AABB bounds, int limit) {
            queries++;
            assertEquals(GuardShotSafety.MAX_ENTITIES + 1, limit);
            return entities.stream().filter(e->bounds.intersects(e.bounds())).limit(limit).toList();
        }
        public List<AABB> blockShapes(BlockPos pos) {
            blockReads++;
            return unloaded ? null : blocks.getOrDefault(pos, List.of());
        }
    }
}
