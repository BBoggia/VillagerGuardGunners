package com.gamergaming.villagerguardgunners.ai.selector_nodes;

import java.util.List;

import com.gamergaming.villagerguardgunners.api.ai.BTNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;
import com.gamergaming.villagerguardgunners.api.ai.UtilityNode;

public class UtilitySelectorNode extends BTNode {
    private final List<BTNode> children;

    public UtilitySelectorNode(List<BTNode> children) {
        this.children = children;
    }

    @Override
    protected NodeStatus evaluate() {
        BTNode bestNode = null;
        double highestUtility = Double.NEGATIVE_INFINITY;

        for (BTNode child : children) {
            double utility = calculateUtility(child);
            if (utility > highestUtility) {
                highestUtility = utility;
                bestNode = child;
            }
        }

        if (bestNode != null) {
            return bestNode.tick();
        }

        return NodeStatus.FAILURE;
    }

    private double calculateUtility(BTNode node) {
        if (node instanceof UtilityNode) {
            return ((UtilityNode) node).getUtility();
        }
        return 0;
    }
}
