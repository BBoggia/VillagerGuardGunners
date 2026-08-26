package com.gamergaming.villagerguardgunners.ai.condition_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ConditionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;
import com.gamergaming.villagerguardgunners.init.ModConfigs;

import tallestegg.guardvillagers.entities.Guard;

public class IsHealthCriticalCondition extends GuardCondition {
    private final float criticalThreshold;  // Percentage of health

    public IsHealthCriticalCondition(Guard guard) {
        this(guard, ModConfigs.GUARD_GUNNER.retreatHealthThreshold.get().floatValue());
    }

    public IsHealthCriticalCondition(Guard guard, float criticalThreshold) {
        super(guard);
        this.criticalThreshold = criticalThreshold;
    }

    @Override
    protected boolean condition() {
        float healthPercentage = guard.getHealth() / guard.getMaxHealth();
        return healthPercentage <= criticalThreshold;
    }
}