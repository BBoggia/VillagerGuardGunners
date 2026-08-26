package com.gamergaming.villagerguardgunners.ai.action_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ActionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;

import tallestegg.guardvillagers.entities.Guard;

public class IdleAction extends GuardAction {

    public IdleAction(Guard guard) {
        super(guard);
    }

    @Override
    public void start() {
    
    }

    @Override
    public void end() {
        
    }

    @Override
    protected NodeStatus evaluate() {
        // Optionally, implement idle look-around behavior
        if (guard.getNavigation().isDone()) {
            guard.getLookControl().setLookAt(
                guard.getX() + guard.getRandom().nextGaussian(),
                guard.getEyeY(),
                guard.getZ() + guard.getRandom().nextGaussian(),
                10.0F,
                guard.getMaxHeadXRot()
            );
        }
        return NodeStatus.SUCCESS;
    }

    @Override
    protected boolean shouldReevaluate() {
        // Implement logic to determine if reevaluation is needed
        // For example, only reevaluate if the target's state has changed
        return true; // For simplicity, always reevaluate
    }
}