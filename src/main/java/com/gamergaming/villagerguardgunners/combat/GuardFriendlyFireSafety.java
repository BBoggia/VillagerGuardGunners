package com.gamergaming.villagerguardgunners.combat;

import com.gamergaming.villagerguardgunners.api.GuardAllyProtection;
import com.gamergaming.villagerguardgunners.api.GuardAllyProtection.ProtectionReason;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Performs the one entity query allowed for a shot that is actually due. */
public final class GuardFriendlyFireSafety {
    private GuardFriendlyFireSafety() {
    }

    public static boolean hasClearShot(
            Guard shooter,
            LivingEntity target,
            float muzzlePadding,
            float projectilePadding,
            float blastPadding) {
        if (!validPaddings(muzzlePadding, projectilePadding, blastPadding)
                || target == shooter
                || isProtected(shooter, target)) {
            return false;
        }

        Vec3 start = new Vec3(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        Vec3 end = new Vec3(target.getX(), target.getEyeY(), target.getZ());
        boolean hasExpandedProjectileEnvelope = projectilePadding > muzzlePadding;
        Vec3 projectileStart = hasExpandedProjectileEnvelope
                ? corridorStart(start, end, projectilePadding - muzzlePadding)
                : start;
        boolean hasExpandedBlastEnvelope = blastPadding > projectilePadding;
        Vec3 blastStart = hasExpandedBlastEnvelope
                ? corridorStart(start, end, blastPadding - muzzlePadding)
                : projectileStart;
        AABB queryBounds = new AABB(start, end).inflate(muzzlePadding);
        if (hasExpandedProjectileEnvelope) {
            queryBounds = queryBounds.minmax(
                    new AABB(projectileStart, end).inflate(projectilePadding));
        }
        if (hasExpandedBlastEnvelope) {
            queryBounds = queryBounds.minmax(new AABB(blastStart, end).inflate(blastPadding));
        }
        List<LivingEntity> candidates = shooter.level().getEntitiesOfClass(
                LivingEntity.class,
                queryBounds,
                entity -> entity != shooter && entity != target && isProtected(shooter, entity));

        for (LivingEntity candidate : candidates) {
            if (intersectsSafetyEnvelope(
                    candidate.getBoundingBox(),
                    start,
                    end,
                    projectileStart,
                    blastStart,
                    muzzlePadding,
                    projectilePadding,
                    blastPadding,
                    hasExpandedProjectileEnvelope,
                    hasExpandedBlastEnvelope)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Allocating, descriptive variant used only by the on-demand administrator
     * command. The hot combat path continues to use {@link #hasClearShot}.
     */
    public static DiagnosticResult diagnoseClearShot(
            Guard shooter,
            LivingEntity target,
            float muzzlePadding,
            float projectilePadding,
            float blastPadding) {
        if (!validPaddings(muzzlePadding, projectilePadding, blastPadding)) {
            return new DiagnosticResult(DiagnosticStatus.INVALID_PADDING, null, null);
        }
        if (target == shooter) {
            return new DiagnosticResult(
                    DiagnosticStatus.TARGET_IS_SHOOTER,
                    shooter,
                    ProtectionReason.SHOOTER);
        }
        ProtectionReason targetReason = GuardAllyProtection.classify(shooter, target);
        if (targetReason.protectedEntity()) {
            return new DiagnosticResult(
                    DiagnosticStatus.TARGET_IS_PROTECTED,
                    target,
                    targetReason);
        }

        Vec3 start = new Vec3(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        Vec3 end = new Vec3(target.getX(), target.getEyeY(), target.getZ());
        boolean hasExpandedProjectileEnvelope = projectilePadding > muzzlePadding;
        Vec3 projectileStart = hasExpandedProjectileEnvelope
                ? corridorStart(start, end, projectilePadding - muzzlePadding)
                : start;
        boolean hasExpandedBlastEnvelope = blastPadding > projectilePadding;
        Vec3 blastStart = hasExpandedBlastEnvelope
                ? corridorStart(start, end, blastPadding - muzzlePadding)
                : projectileStart;
        AABB queryBounds = new AABB(start, end).inflate(muzzlePadding);
        if (hasExpandedProjectileEnvelope) {
            queryBounds = queryBounds.minmax(
                    new AABB(projectileStart, end).inflate(projectilePadding));
        }
        if (hasExpandedBlastEnvelope) {
            queryBounds = queryBounds.minmax(new AABB(blastStart, end).inflate(blastPadding));
        }
        List<LivingEntity> candidates = shooter.level().getEntitiesOfClass(
                LivingEntity.class,
                queryBounds,
                entity -> entity != shooter && entity != target && isProtected(shooter, entity));

        for (LivingEntity candidate : candidates) {
            if (intersectsSafetyEnvelope(
                    candidate.getBoundingBox(),
                    start,
                    end,
                    projectileStart,
                    blastStart,
                    muzzlePadding,
                    projectilePadding,
                    blastPadding,
                    hasExpandedProjectileEnvelope,
                    hasExpandedBlastEnvelope)) {
                return new DiagnosticResult(
                        DiagnosticStatus.BLOCKED_BY_PROTECTED_ENTITY,
                        candidate,
                        GuardAllyProtection.classify(shooter, candidate));
            }
        }
        return new DiagnosticResult(DiagnosticStatus.CLEAR, null, targetReason);
    }

    private static boolean validPaddings(
            float muzzlePadding,
            float projectilePadding,
            float blastPadding) {
        return Float.isFinite(muzzlePadding)
                && muzzlePadding >= 0.0F
                && Float.isFinite(projectilePadding)
                && projectilePadding >= muzzlePadding
                && Float.isFinite(blastPadding)
                && blastPadding >= projectilePadding;
    }

    static Vec3 corridorStart(Vec3 start, Vec3 end, float wideningRadius) {
        Vec3 direction = end.subtract(start);
        double distance = direction.length();
        if (!Double.isFinite(distance) || distance <= 0.0D) {
            return end;
        }
        double offset = Math.min(Math.max(0.0F, wideningRadius), distance);
        return start.add(direction.scale(offset / distance));
    }

    static boolean intersectsSafetyEnvelope(
            AABB bounds,
            Vec3 start,
            Vec3 end,
            float muzzlePadding,
            float projectilePadding,
            float blastPadding) {
        boolean hasExpandedProjectileEnvelope = projectilePadding > muzzlePadding;
        Vec3 projectileStart = hasExpandedProjectileEnvelope
                ? corridorStart(start, end, projectilePadding - muzzlePadding)
                : start;
        boolean hasExpandedBlastEnvelope = blastPadding > projectilePadding;
        Vec3 blastStart = hasExpandedBlastEnvelope
                ? corridorStart(start, end, blastPadding - muzzlePadding)
                : projectileStart;
        return intersectsSafetyEnvelope(
                bounds,
                start,
                end,
                projectileStart,
                blastStart,
                muzzlePadding,
                projectilePadding,
                blastPadding,
                hasExpandedProjectileEnvelope,
                hasExpandedBlastEnvelope);
    }

    private static boolean intersectsSafetyEnvelope(
            AABB bounds,
            Vec3 start,
            Vec3 end,
            Vec3 projectileStart,
            Vec3 blastStart,
            float muzzlePadding,
            float projectilePadding,
            float blastPadding,
            boolean hasExpandedProjectileEnvelope,
            boolean hasExpandedBlastEnvelope) {
        return intersectsInflatedBounds(bounds, start, end, muzzlePadding)
                || hasExpandedProjectileEnvelope
                        && reachesCorridorStart(bounds, start, end, projectileStart)
                        && intersectsInflatedBounds(
                                bounds,
                                projectileStart,
                                end,
                                projectilePadding)
                || hasExpandedBlastEnvelope
                        && reachesCorridorStart(bounds, start, end, blastStart)
                        && intersectsInflatedBounds(bounds, blastStart, end, blastPadding);
    }

    /**
     * Prevents an inflated forward envelope's rectangular end cap from
     * extending its full width backward around the shooter. Entity bounds must
     * themselves reach the forward section before that section can block.
     */
    static boolean reachesCorridorStart(
            AABB bounds,
            Vec3 shotStart,
            Vec3 shotEnd,
            Vec3 corridorStart) {
        double deltaX = shotEnd.x - shotStart.x;
        double deltaY = shotEnd.y - shotStart.y;
        double deltaZ = shotEnd.z - shotStart.z;
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (!Double.isFinite(distance) || distance <= 0.0D) {
            return true;
        }

        double directionX = deltaX / distance;
        double directionY = deltaY / distance;
        double directionZ = deltaZ / distance;
        double centerX = (bounds.minX + bounds.maxX) * 0.5D;
        double centerY = (bounds.minY + bounds.maxY) * 0.5D;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        double centerProjection = (centerX - shotStart.x) * directionX
                + (centerY - shotStart.y) * directionY
                + (centerZ - shotStart.z) * directionZ;
        double projectedHalfExtent = (bounds.maxX - bounds.minX) * 0.5D * Math.abs(directionX)
                + (bounds.maxY - bounds.minY) * 0.5D * Math.abs(directionY)
                + (bounds.maxZ - bounds.minZ) * 0.5D * Math.abs(directionZ);
        double requiredProjection = (corridorStart.x - shotStart.x) * directionX
                + (corridorStart.y - shotStart.y) * directionY
                + (corridorStart.z - shotStart.z) * directionZ;
        return centerProjection + projectedHalfExtent >= requiredProjection;
    }

    /**
     * The single protection rule used by both the pre-fire corridor and the
     * damage backstops. Hostile entities that a guard merely refuses to target
     * (for example Guard Villagers' configurable mob blacklist) are deliberately
     * not treated as allies.
     */
    public static boolean isProtected(Guard shooter, Entity entity) {
        return GuardAllyProtection.isProtected(shooter, entity);
    }

    static boolean intersectsInflatedBounds(AABB bounds, Vec3 start, Vec3 end, float padding) {
        AABB protectedBounds = bounds.inflate(padding);
        return protectedBounds.contains(start) || protectedBounds.clip(start, end).isPresent();
    }

    public enum DiagnosticStatus {
        CLEAR,
        INVALID_PADDING,
        TARGET_IS_SHOOTER,
        TARGET_IS_PROTECTED,
        BLOCKED_BY_PROTECTED_ENTITY
    }

    public record DiagnosticResult(
            DiagnosticStatus status,
            @Nullable LivingEntity blocker,
            @Nullable ProtectionReason protectionReason) {
        public boolean clear() {
            return status == DiagnosticStatus.CLEAR;
        }
    }
}
