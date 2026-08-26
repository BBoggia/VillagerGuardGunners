package com.gamergaming.villagerguardgunners.ai.condition_nodes;

import com.gamergaming.villagerguardgunners.api.ai.ConditionNode;
import com.gamergaming.villagerguardgunners.api.ai.NodeStatus;

import net.minecraft.world.entity.LivingEntity;
import tallestegg.guardvillagers.entities.Guard;

public class RecentlyDamaged extends GuardCondition {
    private final int lastDamagedTimeoutTicks;
    private int ticksSinceLastDamaged;

    public RecentlyDamaged(Guard guard) {
        this(guard, guard.hurtTime);
    }

    public RecentlyDamaged(Guard guard, int lastDamagedTimeoutTicks) {
        this(guard, lastDamagedTimeoutTicks, lastDamagedTimeoutTicks + 1);
    }

    public RecentlyDamaged(Guard guard, int lastDamagedTimeoutTicks, int cooldown) {
        super(guard);
        this.lastDamagedTimeoutTicks = lastDamagedTimeoutTicks;
        this.ticksSinceLastDamaged = cooldown;
    }

    // @Override
    // protected boolean condition() {
    //     return System.currentTimeMillis() - lastDamagedTimeoutTicks < ticksSinceLastDamaged;
    // }

    @Override
    protected boolean condition() {
        if (guard.hurtTime > 0) {
            this.ticksSinceLastDamaged = 0;
        } else {
            this.ticksSinceLastDamaged++;
        }
        return this.ticksSinceLastDamaged <= this.lastDamagedTimeoutTicks;
    }
}