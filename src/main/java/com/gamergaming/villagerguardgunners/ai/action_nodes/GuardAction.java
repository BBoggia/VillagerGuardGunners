package com.gamergaming.villagerguardgunners.ai.action_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ActionNode;

import net.minecraft.world.entity.LivingEntity;
import tallestegg.guardvillagers.entities.Guard;

public abstract class GuardAction extends ActionNode {
    protected final Guard guard;

    public GuardAction(Guard guard) {
        this.guard = guard;
    }

    protected boolean isTargetAlive() {
        LivingEntity target = guard.getTarget();
        return target != null && target.isAlive();
    }

    public Guard getGuard() {
        return guard;
    }
}