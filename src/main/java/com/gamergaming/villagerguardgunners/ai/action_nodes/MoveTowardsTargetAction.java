package com.gamergaming.villagerguardgunners.ai.action_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ActionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;
import com.gamergaming.villagerguardgunners.init.ModConfigs;

import net.minecraft.world.entity.LivingEntity;
import tallestegg.guardvillagers.entities.Guard;

public class MoveTowardsTargetAction extends GuardAction {
    private final double moveSpeed;

    public MoveTowardsTargetAction(Guard guard) {
        this(guard, ModConfigs.GUARD_GUNNER.movementSettings.movementSpeed.get());
    }

    public MoveTowardsTargetAction(Guard guard, double moveSpeed) {
        super(guard);
        this.moveSpeed = moveSpeed;
    }

    @Override
    public void start() {
    
    }

    @Override
    public void end() {
        
    }

    @Override
    protected NodeStatus evaluate() {
        if (!isTargetAlive()) {
            return NodeStatus.FAILURE;
        }
        
        guard.getNavigation().moveTo(guard.getTarget(), moveSpeed);
        return NodeStatus.RUNNING;
    }

    @Override
    protected boolean shouldReevaluate() {
        // Implement logic to determine if reevaluation is needed
        // For example, only reevaluate if the target's state has changed
        return true; // For simplicity, always reevaluate
    }
}