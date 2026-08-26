package com.gamergaming.villagerguardgunners.ai.action_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ActionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;
import com.gamergaming.villagerguardgunners.api.ai.UtilityNode;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

public class MoveToCoverAction extends GuardAction implements UtilityNode {

    public MoveToCoverAction(Guard guard) {
        super(guard);
    }

    @Override
    public void start() {
    
    }

    @Override
    public void end() {
        
    }

    private Vec3 findCoverPosition() {
        // Simple implementation: Find a nearby solid block between guard and target
        if (!isTargetAlive()) {
            return null;
        }

        Vec3 guardPos = guard.position();
        Vec3 targetPos = guard.getTarget().position();
        Vec3 directionToTarget = targetPos.subtract(guardPos).normalize();

        for (int i = 1; i <= 10; i++) {
            Vec3 potentialCoverPos = guardPos.add(directionToTarget.scale(-i));
            BlockPos blockPos = new BlockPos((int) potentialCoverPos.x(), (int) potentialCoverPos.y(), (int) potentialCoverPos.z());
            if (guard.level().getBlockState(blockPos).isSolidRender(guard.level(), blockPos)) {
                return potentialCoverPos;
            }
        }
        return null;
    }

    @Override
    public double getUtility() {
        double healthFactor = 1.0 - (guard.getHealth() / guard.getMaxHealth());
        double underFireFactor = guard.hurtTime > 0 ? 1.0 : 0.0;
        return healthFactor * 0.7 + underFireFactor * 0.3;
    }

    @Override
    protected NodeStatus evaluate() {
        if (guard.getNavigation().isDone()) {
            Vec3 coverPosition = findCoverPosition();
            if (coverPosition != null) {
                guard.getNavigation().moveTo(coverPosition.x, coverPosition.y, coverPosition.z, 1.0);
                return NodeStatus.RUNNING;
            } else {
                return NodeStatus.FAILURE;
            }
        }
        return NodeStatus.RUNNING;
    }

    @Override
    protected boolean shouldReevaluate() {
        // Implement logic to determine if reevaluation is needed
        // For example, only reevaluate if the target's state has changed
        return true; // For simplicity, always reevaluate
    }
}