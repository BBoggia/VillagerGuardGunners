package com.gamergaming.villagerguardgunners.combat.aim;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-thread-only temporary model. It performs no world queries, serialization,
 * native gun actions, or eligibility checks. The caller owns those lifecycle gates.
 */
public final class GuardAimState {
    static final int DRIFT_PERIOD_TICKS = 12;
    private static final double DRIFT_WEIGHT = 0.8;
    private static final long SHOT_SALT = 0x6a09e667f3bcc909L;
    private final long seed;
    private Context context;
    private long shotIndex;
    private long lastTick = Long.MIN_VALUE;
    private GuardShotAim pending;

    /** Weapon/config revisions must change when their identity or relevant policy changes. */
    public record Context(UUID targetId, long weaponRevision, long policyRevision, GuardAimSettings settings) {
        public Context {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(settings, "settings");
        }
    }

    public GuardAimState(long seed) {
        this.seed = seed;
    }

    /**
     * Repeated preparation uses the same shot variation until committed. Drift is
     * a function of game time, not calls or rounds fired. Changed geometry replaces
     * the pending proposal and therefore requires fresh external safety approval.
     * The eye origin preserves existing zero-error aim; the projectile origin is
     * kept separately to let safety account for TaCZ's lower muzzle position.
     */
    public Optional<GuardShotAim> prepare(Context next, long gameTick, Vec3 aimOrigin,
                                         Vec3 projectileOrigin, AABB targetBounds, Vec3 targetEyes) {
        Objects.requireNonNull(next, "context");
        if (!next.equals(context) || gameTick < lastTick) {
            reset();
            context = next;
        }
        lastTick = gameTick;
        if (!valid(aimOrigin) || !valid(projectileOrigin) || !valid(targetEyes) || !valid(targetBounds)) {
            invalidateProposal();
            return Optional.empty();
        }
        Vec3 point = next.settings().aimPoint() == GuardAimPoint.EYE_LEVEL
                ? targetEyes : targetBounds.getCenter();
        Vec3 delta = point.subtract(aimOrigin);
        double length = delta.length();
        if (!Double.isFinite(length) || length < 1.0e-12) {
            invalidateProposal();
            return Optional.empty();
        }
        Vec3 direction = delta.scale(1.0 / length);
        Vec3 rotated = direction;
        if (next.settings().errorDegrees() > 0) {
            long contextSeed = mix(seed ^ next.targetId().getMostSignificantBits()
                    ^ Long.rotateLeft(next.targetId().getLeastSignificantBits(), 17)
                    ^ mix(next.weaponRevision()));
            long interval = Math.floorDiv(gameTick, DRIFT_PERIOD_TICKS);
            double t = Math.floorMod(gameTick, DRIFT_PERIOD_TICKS) / (double) DRIFT_PERIOD_TICKS;
            t = t * t * (3 - 2 * t);
            Offset a = disk(contextSeed ^ mix(interval));
            Offset b = disk(contextSeed ^ mix(interval + 1));
            Offset shot = disk(contextSeed ^ SHOT_SALT ^ mix(shotIndex));
            double x = DRIFT_WEIGHT * (a.x + (b.x - a.x) * t) + (1 - DRIFT_WEIGHT) * shot.x;
            double y = DRIFT_WEIGHT * (a.y + (b.y - a.y) * t) + (1 - DRIFT_WEIGHT) * shot.y;
            double radius = Math.hypot(x, y);
            if (radius > 0) {
                // Choose a stable orthogonal basis, including shots near vertical.
                Vec3 reference = Math.abs(direction.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                Vec3 right = direction.cross(reference).normalize();
                Vec3 up = right.cross(direction).normalize();
                double angle = Math.toRadians(next.settings().errorDegrees()) * Math.min(1, radius);
                Vec3 lateral = right.scale(x / radius).add(up.scale(y / radius));
                rotated = direction.scale(Math.cos(angle)).add(lateral.scale(Math.sin(angle))).normalize();
            }
        }
        // Match the existing controller's exact atan2 inputs in zero-error mode.
        Vec3 angles = next.settings().errorDegrees() == 0 ? delta : rotated;
        float yaw = (float) (Mth.atan2(angles.z, angles.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(angles.y, Math.sqrt(angles.x * angles.x + angles.z * angles.z)) * Mth.RAD_TO_DEG);
        // Store the direction represented by the supplied float angles, not only
        // the pre-rounding vector. TaCZ applies its own spread after these angles.
        double yawRad = Math.toRadians(yaw), pitchRad = Math.toRadians(pitch);
        Vec3 supplied = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        GuardShotAim candidate = new GuardShotAim(gameTick, shotIndex, aimOrigin, projectileOrigin, point, supplied, yaw, pitch);
        if (!candidate.equals(pending)) pending = candidate;
        return Optional.of(pending);
    }

    /** Pure read: diagnostics and pitch/yaw suppliers must never advance state. */
    public Optional<GuardShotAim> pending() {
        return Optional.ofNullable(pending);
    }

    /** Called once only after a successful round; stale/foreign proposals cannot consume another round. */
    public boolean commit(GuardShotAim expected) {
        if (expected == null || expected != pending) return false;
        shotIndex++;
        pending = null;
        return true;
    }

    /** Visibility/movement/eligibility interruption: discard approval, retain shot variation. */
    public void invalidateProposal() {
        pending = null;
    }

    /** Target/weapon replacement, death, unload, dimension change, or disabling combat. */
    public void reset() {
        context = null;
        pending = null;
        shotIndex = 0;
        lastTick = Long.MIN_VALUE;
    }

    private static boolean valid(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static boolean valid(AABB b) {
        return b != null && Double.isFinite(b.minX) && Double.isFinite(b.minY) && Double.isFinite(b.minZ)
                && Double.isFinite(b.maxX) && Double.isFinite(b.maxY) && Double.isFinite(b.maxZ)
                && b.maxX > b.minX && b.maxY > b.minY && b.maxZ > b.minZ;
    }

    private record Offset(double x, double y) {}

    /** Uniform disk sample with no mutable/shared random generator. */
    private static Offset disk(long value) {
        double radius = Math.sqrt(unit(mix(value)));
        double theta = 2 * Math.PI * unit(mix(value ^ 0xbb67ae8584caa73bL));
        return new Offset(radius * Math.cos(theta), radius * Math.sin(theta));
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
