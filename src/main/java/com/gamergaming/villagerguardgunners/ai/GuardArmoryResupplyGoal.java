package com.gamergaming.villagerguardgunners.ai;

import com.gamergaming.villagerguardgunners.armory.resupply.GuardArmoryResupplyController;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Low-priority owner of movement for one already-approved Armory request. */
public final class GuardArmoryResupplyGoal extends Goal {
    private final GuardArmoryResupplyController controller;

    public GuardArmoryResupplyGoal(GuardArmoryResupplyController controller) {
        this.controller = controller;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return controller.canStart();
    }

    @Override
    public boolean canContinueToUse() {
        return controller.shouldContinue();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        controller.tick();
    }

    @Override
    public void stop() {
        controller.stopForHigherPriorityGoal();
    }
}
