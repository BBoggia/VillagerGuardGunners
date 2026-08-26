package com.gamergaming.villagerguardgunners.api.ai;

public abstract class ConditionNode extends BTNode {
    
    @Override
    protected boolean shouldReevaluate() {
        return false;
    }
    
    @Override
    protected NodeStatus evaluate() {
        return condition() ? NodeStatus.SUCCESS : NodeStatus.FAILURE;
    }

    protected abstract boolean condition();
}
