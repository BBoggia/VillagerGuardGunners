package com.gamergaming.villagerguardgunners.ai.action_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ActionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

public class RetreatAction extends GuardAction {
    private final double retreatDistance;

    public RetreatAction(Guard guard) {
        this(guard, 5.0);
    }

    public RetreatAction(Guard guard, double retreatDistance) {
        super(guard);
        this.retreatDistance = retreatDistance;
    }

    @Override
    public void start() {
    
    }

    @Override
    public void end() {
        
    }

    private boolean isPositionSafe(Vec3 position) {
        BlockPos pos = new BlockPos((int) position.x(), (int) position.y(), (int) position.z());
        return guard.level().getBlockState(pos).isAir() && guard.level().getBlockState(pos.below()).isFaceSturdy(guard.level(), pos.below(), Direction.UP);
    }

    @Override
    protected NodeStatus evaluate() {
        LivingEntity target = guard.getTarget();
        if (!isTargetAlive())  // || target.distanceTo(guard) < retreatDistance) {
            return NodeStatus.FAILURE;
        
        if (guard.getNavigation().isDone()) {
            // Find a retreat position
            Vec3 retreatDirection = guard.position().subtract(target.position()).normalize();
            Vec3 retreatPosition = guard.position().add(retreatDirection.scale(retreatDistance));

            if (isPositionSafe(retreatPosition)) {
                guard.getNavigation().moveTo(retreatPosition.x, retreatPosition.y, retreatPosition.z, 1.0);
                return NodeStatus.RUNNING;
            } else {
                // Can't find a safe position
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