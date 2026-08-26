package com.gamergaming.villagerguardgunners.ai.condition_nodes;

import com.gamergaming.villagerguardgunners.api.IGuardBTreeHelper;
import com.gamergaming.villagerguardgunners.api.ai.ConditionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;

import tallestegg.guardvillagers.entities.Guard;

public class IsPatrolTimeCondition extends GuardCondition {
    private final int idleTimeThreshold;
    private int idleTime;

    public IsPatrolTimeCondition(Guard guard) {
        this(guard, 120);
    }

    public IsPatrolTimeCondition(Guard guard, int idleTimeThreshold) {
        super(guard);
        this.idleTimeThreshold = idleTimeThreshold;
        this.idleTime = 0;
    }

    @Override
    protected boolean condition() {
        if (guard instanceof IGuardBTreeHelper guardB) {
            return guardB.hasStaticPatrolPoints();
        }
        if (!hasTarget()) {
            idleTime++;
        } else {
            idleTime = 0;
        }
        return idleTime >= idleTimeThreshold;
    }
}
