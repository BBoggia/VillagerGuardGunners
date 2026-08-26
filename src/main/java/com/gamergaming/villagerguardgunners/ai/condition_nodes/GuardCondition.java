package com.gamergaming.villagerguardgunners.ai.condition_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ConditionNode;

import net.minecraft.world.entity.LivingEntity;
import tallestegg.guardvillagers.entities.Guard;

public abstract class GuardCondition extends ConditionNode {
    protected final Guard guard;

    public GuardCondition(Guard guard) {
        this.guard = guard;
    }

    protected boolean hasTarget() {
        return guard.getTarget() != null && guard.getTarget().isAlive();
    }

    public Guard getGuard() {
        return guard;
    }
}