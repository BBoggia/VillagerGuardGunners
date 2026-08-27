package com.gamergaming.villagerguardgunners.combat;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Performs the one entity query allowed for a shot that is actually due. */
public final class GuardFriendlyFireSafety {
    private GuardFriendlyFireSafety() {
    }

    public static boolean hasClearShot(Guard shooter, LivingEntity target, float padding) {
        if (!Float.isFinite(padding)
                || padding < 0.0F
                || target == shooter
                || isProtected(shooter, target)) {
            return false;
        }

        Vec3 start = new Vec3(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        Vec3 end = new Vec3(target.getX(), target.getEyeY(), target.getZ());

        AABB corridor = new AABB(start, end).inflate(padding);
        List<LivingEntity> candidates = shooter.level().getEntitiesOfClass(
                LivingEntity.class,
                corridor,
                entity -> entity != shooter && entity != target && isProtected(shooter, entity));

        for (LivingEntity candidate : candidates) {
            if (intersectsInflatedBounds(candidate.getBoundingBox(), start, end, padding)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Allocating, descriptive variant used only by the on-demand administrator
     * command. The hot combat path continues to use {@link #hasClearShot}.
     */
    public static DiagnosticResult diagnoseClearShot(Guard shooter, LivingEntity target, float padding) {
        if (!Float.isFinite(padding) || padding < 0.0F) {
            return new DiagnosticResult(DiagnosticStatus.INVALID_PADDING, null);
        }
        if (target == shooter) {
            return new DiagnosticResult(DiagnosticStatus.TARGET_IS_SHOOTER, shooter);
        }
        if (isProtected(shooter, target)) {
            return new DiagnosticResult(DiagnosticStatus.TARGET_IS_PROTECTED, target);
        }

        Vec3 start = new Vec3(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        Vec3 end = new Vec3(target.getX(), target.getEyeY(), target.getZ());
        AABB corridor = new AABB(start, end).inflate(padding);
        List<LivingEntity> candidates = shooter.level().getEntitiesOfClass(
                LivingEntity.class,
                corridor,
                entity -> entity != shooter && entity != target && isProtected(shooter, entity));

        for (LivingEntity candidate : candidates) {
            if (intersectsInflatedBounds(candidate.getBoundingBox(), start, end, padding)) {
                return new DiagnosticResult(DiagnosticStatus.BLOCKED_BY_PROTECTED_ENTITY, candidate);
            }
        }
        return new DiagnosticResult(DiagnosticStatus.CLEAR, null);
    }

    /**
     * The single protection rule used by both the pre-fire corridor and the
     * damage backstops. Hostile entities that a guard merely refuses to target
     * (for example Guard Villagers' configurable mob blacklist) are deliberately
     * not treated as allies.
     */
    public static boolean isProtected(Guard shooter, Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }

        UUID ownerId = shooter.getOwnerId();
        return entity instanceof Guard
                || entity instanceof AbstractVillager
                || entity instanceof IronGolem
                || ownerId != null && ownerId.equals(entity.getUUID())
                || shooter.isAlliedTo(entity);
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

    public record DiagnosticResult(DiagnosticStatus status, @Nullable LivingEntity blocker) {
        public boolean clear() {
            return status == DiagnosticStatus.CLEAR;
        }
    }
}
