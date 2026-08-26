package com.gamergaming.villagerguardgunners.api.ai;

import java.util.List;

public class SequenceNode extends BTNode {
    private final List<BTNode> children;

    public SequenceNode(List<BTNode> children) {
        this.children = children;
    }

    @Override
    public NodeStatus tick() {
        for (BTNode child : children) {
            NodeStatus status = child.tick();
            if (status != NodeStatus.SUCCESS) {
                return status;
            }
        }
        return NodeStatus.SUCCESS;
    }

    @Override
    protected NodeStatus evaluate() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'evaluate'");
    }
}