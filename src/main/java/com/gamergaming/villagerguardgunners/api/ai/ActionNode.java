package com.gamergaming.villagerguardgunners.api.ai;

public abstract class ActionNode extends BTNode {
    public void start() {}
    public void end() {}
    
    @Override
    protected boolean shouldReevaluate() {
        return false;
    }
    
    @Override
    protected abstract NodeStatus evaluate();
}
