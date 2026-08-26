package com.gamergaming.villagerguardgunners.ai;

import java.util.Arrays;

import com.gamergaming.villagerguardgunners.ai.action_nodes.AttackAction;
import com.gamergaming.villagerguardgunners.ai.action_nodes.IdleAction;
import com.gamergaming.villagerguardgunners.ai.action_nodes.MoveToCoverAction;
import com.gamergaming.villagerguardgunners.ai.action_nodes.MoveTowardsTargetAction;
import com.gamergaming.villagerguardgunners.ai.action_nodes.PatrolAction;
import com.gamergaming.villagerguardgunners.ai.action_nodes.ReloadAction;
import com.gamergaming.villagerguardgunners.ai.action_nodes.RetreatAction;
import com.gamergaming.villagerguardgunners.ai.condition_nodes.CanAttackCondition;
import com.gamergaming.villagerguardgunners.ai.condition_nodes.IsHealthCriticalCondition;
import com.gamergaming.villagerguardgunners.ai.condition_nodes.IsPatrolTimeCondition;
import com.gamergaming.villagerguardgunners.ai.condition_nodes.IsTargetVisibleCondition;
import com.gamergaming.villagerguardgunners.ai.condition_nodes.NeedToReloadCondition;
import com.gamergaming.villagerguardgunners.ai.condition_nodes.RecentlyDamaged;
import com.gamergaming.villagerguardgunners.api.ai.BTNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;
import com.gamergaming.villagerguardgunners.api.ai.SequenceNode;
import com.gamergaming.villagerguardgunners.api.ai.SelectorNode;

import tallestegg.guardvillagers.entities.Guard;

public class GuardBehaviorTree {
    private final BTNode rootNode;

    public GuardBehaviorTree(Guard guard) {
        // Emergency Actions Sequence
        BTNode checkEmergencyActions = new SequenceNode(Arrays.asList(
            new IsHealthCriticalCondition(guard),
            new RetreatAction(guard)
        ));

        // Attack Selector
        BTNode attackSelector = new SelectorNode(Arrays.asList(
            new SequenceNode(Arrays.asList(
                new NeedToReloadCondition(guard),
                new ReloadAction(guard)
            )),
            new SequenceNode(Arrays.asList(
                new CanAttackCondition(guard),
                new AttackAction(guard)
            ))
        ));

        // Engage Target Sequence
        BTNode engageTargetSequence = new SequenceNode(Arrays.asList(
            new IsTargetVisibleCondition(guard),
            attackSelector,
            new MoveTowardsTargetAction(guard)
        ));

        // Seek Cover Selector
        BTNode seekCover = new SequenceNode(Arrays.asList(
            new RecentlyDamaged(guard),
            new MoveToCoverAction(guard)
        ));

        // Patrol Sequence
        BTNode patrolSequence = new SequenceNode(Arrays.asList(
            new IsPatrolTimeCondition(guard),   // THIS NEEDS TO BE FIXED!!!
            new PatrolAction(guard)
        ));

        // Root Selector
        rootNode = new SelectorNode(Arrays.asList(
            checkEmergencyActions,
            engageTargetSequence,
            seekCover,
            patrolSequence,
            new IdleAction(guard)
        ));
    }

    public void tick() {
        rootNode.tick();
    }
}
