package com.gamergaming.villagerguardgunners.ai;

import com.gamergaming.villagerguardgunners.ai.action_nodes.*;
import com.gamergaming.villagerguardgunners.ai.condition_nodes.*;
import com.gamergaming.villagerguardgunners.api.IGuardBTreeHelper;
import com.gamergaming.villagerguardgunners.api.ai.BTNode;
import com.gamergaming.villagerguardgunners.init.ModConfigs;

import tallestegg.guardvillagers.entities.Guard;
import net.minecraft.core.BlockPos;

import java.util.List;

public class NodeFactory {
    public static AttackAction createAttackAction(Guard guard) {
        return new AttackAction(guard);
    }

    public static IsHealthCriticalCondition createHealthCriticalCondition(Guard guard) {
        return new IsHealthCriticalCondition(guard);
    }

    public static MoveTowardsTargetAction createMoveTowardsTargetAction(Guard guard) {
        return new MoveTowardsTargetAction(guard);
    }

    public static PatrolAction createPatrolAction(Guard guard) {
        // Use default patrol points or retrieve from guard
        if (guard instanceof IGuardBTreeHelper guardB) {
            return new PatrolAction(guard, guardB.getStaticPatrolPoints());
        } else {
            return new PatrolAction(guard, List.of(guard.blockPosition()));
        }
    }

    // Add other factory methods as needed
}
