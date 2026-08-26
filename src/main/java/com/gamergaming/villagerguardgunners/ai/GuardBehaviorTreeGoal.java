package com.gamergaming.villagerguardgunners.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import tallestegg.guardvillagers.entities.Guard;
import java.util.EnumSet;

import com.gamergaming.villagerguardgunners.init.ModConfigs;

public class GuardBehaviorTreeGoal extends Goal {
    private final Guard guard;
    private final GuardBehaviorTree behaviorTree;

    public GuardBehaviorTreeGoal(Guard guard) {
        this.guard = guard;
        this.behaviorTree = new GuardBehaviorTree(guard);
        // Set goal flags if necessary
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Check if the guard can start using this goal
        LivingEntity target = guard.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        if (guard.isDeadOrDying()) {
            return false;
        }

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // Check if the guard should continue using this goal
        if (!this.canUse()) {
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        super.start();

        // 1. Initialize or reset the behavior tree if necessary
        // behaviorTree.reset();

        // 2. Set the guard's state to indicate it is in combat
        // guard.setAggressive(true);

        // 3. Initialize movement or navigation components if required
        guard.getNavigation().stop();
    }

    @Override
    public void stop() {
        super.stop();

        guard.getNavigation().stop();

        guard.setTarget(null);
    }

    @Override
    public void tick() {
        // Execute the behavior tree
        behaviorTree.tick();
    }
}
