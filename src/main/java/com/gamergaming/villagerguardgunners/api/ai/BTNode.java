package com.gamergaming.villagerguardgunners.api.ai;

public abstract class BTNode {
    protected NodeStatus lastStatus = NodeStatus.SUCCESS;

    public NodeStatus tick() {
        if (shouldReevaluate()) {
            lastStatus = evaluate();
        }
        return lastStatus;
    }

    protected abstract NodeStatus evaluate();

    protected boolean shouldReevaluate() {
        return true;
    }
}
