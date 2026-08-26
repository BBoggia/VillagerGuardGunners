package com.gamergaming.villagerguardgunners.api.ai;

import java.util.List;

public class SelectorNode extends BTNode {
    private final List<BTNode> children;

    public SelectorNode(List<BTNode> children) {
        this.children = children;
    }

    @Override
    public NodeStatus tick() {
        for (BTNode child : children) {
            NodeStatus status = child.tick();
            if (status != NodeStatus.FAILURE) {
                return status;
            }
        }
        return NodeStatus.FAILURE;
    }

    @Override
    protected NodeStatus evaluate() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'evaluate'");
    }
}