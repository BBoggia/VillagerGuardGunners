package com.gamergaming.villagerguardgunners.combat;

import com.gamergaming.villagerguardgunners.combat.aim.GuardShotAim;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Immutable, conservative envelope for one native kinetic discharge, not a firing approval. */
public final class GuardShotEnvelope {
    public static final int MAX_LIFE_TICKS = 200;
    public static final double MAX_TRAVEL = 1024;
    public static final double MAX_PADDING = 32;
    public static final int MAX_QUERY_CHUNKS = 1024;
    private static final double ROUNDING = 0.0002;

    /** Speed is blocks/tick; blast reach is damage reach, not the native explosion power. */
    public record Ballistics(double speed, int lifeTicks, double gravity, double friction,
                             double inaccuracy, Vec3 inheritedVelocity, double blastReach,
                             int fuseTicks) {}

    public enum Failure { NONE, INVALID_AIM, INVALID_BALLISTICS, EXCESSIVE_ENVELOPE }
    public record Preparation(Failure failure, GuardShotEnvelope envelope) {
        public boolean valid() { return failure == Failure.NONE; }
    }

    private final GuardShotAim aim;
    private final Ballistics ballistics;
    private final double clearance;
    private final Vec3 velocity;
    private final double spread;
    private final List<Slice> slices;
    private final AABB bounds;

    private GuardShotEnvelope(GuardShotAim aim, Ballistics ballistics, double clearance,
                              Vec3 velocity, double spread, List<Slice> slices, AABB bounds) {
        this.aim = aim;
        this.ballistics = ballistics;
        this.clearance = clearance;
        this.velocity = velocity;
        this.spread = spread;
        this.slices = List.copyOf(slices);
        this.bounds = bounds;
    }

    public static Preparation prepare(GuardShotAim aim, Ballistics b, double clearance) {
        if (aim == null || !finite(aim.aimOrigin()) || !finite(aim.projectileOrigin())
                || !finite(aim.targetPoint()) || !finite(aim.direction())
                || !Float.isFinite(aim.yaw()) || !Float.isFinite(aim.pitch())
                || Math.abs(aim.direction().lengthSqr() - 1) > 0.00001
                || aim.direction().distanceToSqr(angles(aim.yaw(), aim.pitch())) > 0.000001) {
            return new Preparation(Failure.INVALID_AIM, null);
        }
        if (b == null || !positive(b.speed) || b.lifeTicks < 1
                || !nonnegative(b.gravity) || !nonnegative(b.friction) || b.friction > 1
                || !nonnegative(b.inaccuracy) || !finite(b.inheritedVelocity)
                || !nonnegative(b.blastReach) || b.fuseTicks < 0 || !nonnegative(clearance)) {
            return new Preparation(Failure.INVALID_BALLISTICS, null);
        }
        if (b.lifeTicks > MAX_LIFE_TICKS || clearance + b.blastReach > MAX_PADDING) {
            return new Preparation(Failure.EXCESSIVE_ENVELOPE, null);
        }
        Vec3 velocity = aim.direction().scale(b.speed).add(b.inheritedVelocity);
        // Vanilla perturbs each direction component before multiplying by speed.
        // This also bounds versions which normalize again after the perturbation.
        double error = Math.sqrt(3) * 0.0172275 * b.inaccuracy;
        double spread = b.speed * (error / (1 - error) + ROUNDING);
        if (error >= 1 || !positive(velocity.length() - spread)) {
            return new Preparation(Failure.EXCESSIVE_ENVELOPE, null);
        }
        // Water can replace drag with .4 and reduce gravity to .6 of normal.
        // Bound every air/water sequence without reading or loading distant blocks.
        double fastRetention = 1 - Math.min(b.friction, (double) 0.4F);
        double slowRetention = 1 - Math.max(b.friction, (double) 0.4F);
        // Account for TaCZ's float subtraction when calculating drag.
        fastRetention = Math.min(1, fastRetention + 0.0000001);
        double fastVelocity = 1, slowVelocity = 1, fastDistance = 0, slowDistance = 0;
        double fallSpeed = 0, fall = 0;
        List<Slice> slices = new ArrayList<>();
        AABB bounds = new AABB(aim.projectileOrigin(), aim.projectileOrigin());
        for (int tick = 0; tick < b.lifeTicks; tick++) {
            double end = fastDistance + fastVelocity;
            double nextFall = fall + fallSpeed;
            Slice slice = new Slice(slowDistance, end, nextFall);
            double padding = spread * end + clearance + b.blastReach;
            if (padding > MAX_PADDING || nextFall > MAX_TRAVEL
                    || velocity.length() * end + nextFall > MAX_TRAVEL) {
                return new Preparation(Failure.EXCESSIVE_ENVELOPE, null);
            }
            AABB segmentBounds = new AABB(aim.projectileOrigin().add(velocity.scale(slice.start)),
                    aim.projectileOrigin().add(velocity.scale(slice.end)))
                    .expandTowards(0, -nextFall, 0).inflate(padding);
            bounds = bounds.minmax(segmentBounds);
            slices.add(slice);
            fastDistance = end;
            slowDistance += slowVelocity;
            fastVelocity *= fastRetention;
            slowVelocity *= Math.max(0, slowRetention - 0.0000001);
            fall = nextFall;
            fallSpeed = fallSpeed * fastRetention + b.gravity;
        }
        // TaCZ's entity search expands its sweep by one block before applying fixed hitboxes.
        bounds = bounds.inflate(1);
        long chunksX = ((long) Math.floor(bounds.maxX) >> 4) - ((long) Math.floor(bounds.minX) >> 4) + 1;
        long chunksZ = ((long) Math.floor(bounds.maxZ) >> 4) - ((long) Math.floor(bounds.minZ) >> 4) + 1;
        if (chunksX * chunksZ > MAX_QUERY_CHUNKS) {
            return new Preparation(Failure.EXCESSIVE_ENVELOPE, null);
        }
        return new Preparation(Failure.NONE,
                new GuardShotEnvelope(aim, b, clearance, velocity, spread, slices, bounds));
    }

    public GuardShotAim aim() { return aim; }
    public Ballistics ballistics() { return ballistics; }
    public double clearance() { return clearance; }
    public AABB bounds() { return bounds; }

    public boolean intersects(AABB candidate, double padding) {
        for (Slice slice : slices) {
            if (intersects(candidate, padding, slice)) return true;
        }
        return false;
    }

    /** Full forward blast reservation; near-muzzle exceptions require explicit impact checks. */
    public boolean forwardBlastIntersects(AABB candidate) {
        Vec3 start = aim.projectileOrigin();
        Vec3 end = start.add(velocity);
        Vec3 forwardStart = start.add(velocity.normalize().scale(ballistics.blastReach + clearance));
        return GuardFriendlyFireSafety.reachesCorridorStart(candidate, start, end, forwardStart)
                && intersects(candidate, clearance + ballistics.blastReach);
    }

    public boolean fuseThreatens(AABB candidate) {
        int fuse = ballistics.fuseTicks;
        return fuse < slices.size()
                && intersects(candidate, clearance + ballistics.blastReach, slices.get(fuse));
    }

    private boolean intersects(AABB candidate, double padding, Slice slice) {
        // p = origin + initialVelocity * s - (0, fall, 0) + spreadError * s.
        // Move the candidate up by the possible fall instead of widening every axis.
        AABB lifted = candidate.expandTowards(0, slice.fall, 0);
        Vec3 unit = velocity.normalize();
        Vec3 farCorner = new Vec3(unit.x >= 0 ? lifted.maxX : lifted.minX,
                unit.y >= 0 ? lifted.maxY : lifted.minY, unit.z >= 0 ? lifted.maxZ : lifted.minZ);
        // AABB inflation projects by the L1 norm, not just its scalar padding.
        double projectionPadding = padding * (Math.abs(unit.x) + Math.abs(unit.y) + Math.abs(unit.z));
        double end = Math.min(slice.end,
                (farCorner.subtract(aim.projectileOrigin()).dot(unit) + projectionPadding)
                        / (velocity.length() - spread));
        if (end < slice.start) return false;
        Vec3 startPoint = aim.projectileOrigin().add(velocity.scale(slice.start));
        Vec3 endPoint = aim.projectileOrigin().add(velocity.scale(end));
        AABB expanded = lifted.inflate(padding + spread * end);
        return expanded.contains(startPoint) || expanded.clip(startPoint, endPoint).isPresent();
    }

    private record Slice(double start, double end, double fall) {}

    private static Vec3 angles(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
                && Math.abs(v.x) <= 30_001_024 && Math.abs(v.y) <= 30_001_024 && Math.abs(v.z) <= 30_001_024;
    }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
    private static boolean nonnegative(double value) { return Double.isFinite(value) && value >= 0; }
}
