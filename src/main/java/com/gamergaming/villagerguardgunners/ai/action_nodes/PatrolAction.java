package com.gamergaming.villagerguardgunners.ai.action_nodes;

import java.util.List;

import com.gamergaming.villagerguardgunners.api.IGuardBTreeHelper;
import com.gamergaming.villagerguardgunners.api.ai.ActionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;

import net.minecraft.core.BlockPos;
import tallestegg.guardvillagers.entities.Guard;

public class PatrolAction extends GuardAction {
    private final List<BlockPos> patrolPoints;
    private int currentPatrolIndex;

    public PatrolAction(Guard guard) {
        super(guard);
        if (guard instanceof IGuardBTreeHelper guardB) {
            this.patrolPoints = guardB.getStaticPatrolPoints();
        } else {
            this.patrolPoints = List.of(guard.blockPosition());
        }
        this.currentPatrolIndex = 0;
    }

    public PatrolAction(Guard guard, BlockPos patrolPoint) {
        this(guard, List.of(patrolPoint));
        this.currentPatrolIndex = 0;
    }

    public PatrolAction(Guard guard, List<BlockPos> patrolPoints) {
        super(guard);
        this.patrolPoints = patrolPoints;
        this.currentPatrolIndex = 0;
    }

    @Override
    public void start() {
    
    }

    @Override
    public void end() {
        
    }

    @Override
    protected NodeStatus evaluate() {
        if (guard.getNavigation().isDone()) {
            BlockPos patrolTarget = patrolPoints.get(currentPatrolIndex);
            guard.getNavigation().moveTo(patrolTarget.getX() + 0.5, patrolTarget.getY(), patrolTarget.getZ() + 0.5, 1.0);
            currentPatrolIndex = (currentPatrolIndex + 1) % patrolPoints.size();
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
