package com.gamergaming.villagerguardgunners.ai.condition_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ConditionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;
import com.gamergaming.villagerguardgunners.init.ModConfigs;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

public class CanAttackCondition extends GuardCondition {
    private final double attackRange;

    public CanAttackCondition(Guard guard) {
        this(guard, ModConfigs.GUARD_GUNNER.attackSettings.maxAttackRange.get().doubleValue());
    }

    public CanAttackCondition(Guard guard, double attackRange) {
        super(guard);
        this.attackRange = attackRange;
    }

    @Override
    protected boolean condition() {
        if (!hasTarget()) {
            return false;
        }
        LivingEntity target = guard.getTarget();

        double distanceSq = guard.distanceToSqr(target);
        if (distanceSq > attackRange * attackRange) {
            return false;
        }
        // Check if guard is facing the target
        double angle = getAngleToTarget(target);
        double threshold = 20.0; // degrees, adjust as needed
        return angle <= threshold;
    }

    private double getAngleToTarget(LivingEntity target) {
        Vec3 guardFacingVec = guard.getViewVector(1.0F);
        Vec3 vecToTarget = target.position().subtract(guard.position()).normalize();
        double angleRadians = Math.acos(guardFacingVec.dot(vecToTarget));
        return Math.toDegrees(angleRadians);
    }
}
