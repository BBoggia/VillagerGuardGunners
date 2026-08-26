package com.gamergaming.villagerguardgunners.util;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AIUtils {

    public static double getAngleToTarget(LivingEntity shooter, LivingEntity target) {
        Vec3 shooterFacingVec = shooter.getViewVector(1.0F);
        Vec3 vecToTarget = target.position().subtract(shooter.position()).normalize();
        double dotProduct = shooterFacingVec.dot(vecToTarget);
        dotProduct = Math.max(-1.0, Math.min(1.0, dotProduct));
        double angleRadians = Math.acos(dotProduct);
        return Math.toDegrees(angleRadians);
    }

    public static boolean isFacingTarget(LivingEntity shooter, LivingEntity target, double thresholdDegrees) {
        double angleToTarget = getAngleToTarget(shooter, target);
        return angleToTarget <= thresholdDegrees && angleToTarget >= -thresholdDegrees;
    }

    public static boolean isFacingTargetWithOffset(LivingEntity shooter, LivingEntity target, double thresholdDegrees, double offset) {
        double angleToTarget = getAngleToTarget(shooter, target);
        return angleToTarget + offset <= thresholdDegrees && angleToTarget + offset >= -thresholdDegrees;
    }

    public static boolean hasLineOfSight(LivingEntity observer, LivingEntity target) {
        return observer.hasLineOfSight(target);
    }

    public static boolean isLineOfSightBlocked(LivingEntity observer, LivingEntity target) {
        Vec3 observerEyePos = observer.getEyePosition();
        Vec3 targetEyePos = target.getEyePosition();
        BlockHitResult hitResult = observer.level().clip(new ClipContext(
            observerEyePos, targetEyePos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, observer));

        return hitResult.getType() != HitResult.Type.MISS && !hitResult.getBlockPos().equals(target.blockPosition());
    }

    public static boolean hasLineOfSightFromPosition(Level level, Vec3 fromPos, Vec3 toPos, LivingEntity observer) {
        BlockHitResult hitResult = level.clip(new ClipContext(
            fromPos, toPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, observer));
        return hitResult.getType() == HitResult.Type.MISS || hitResult.getBlockPos().equals(observer.blockPosition());
    }
}