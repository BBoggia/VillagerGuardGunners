package com.gamergaming.villagerguardgunners.ai.condition_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ConditionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;

import net.minecraft.world.entity.LivingEntity;
import tallestegg.guardvillagers.entities.Guard;

public class IsTargetVisibleCondition extends GuardCondition {

    public IsTargetVisibleCondition(Guard guard) {
        super(guard);
    }

    @Override
    protected boolean condition() {
        if (!hasTarget()) {
            return false;
        }

        return guard.getSensing().hasLineOfSight(guard.getTarget());
    }
}
