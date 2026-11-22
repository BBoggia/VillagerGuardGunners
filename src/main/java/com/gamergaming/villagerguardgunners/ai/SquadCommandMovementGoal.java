package com.gamergaming.villagerguardgunners.ai;

import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import tallestegg.guardvillagers.entities.Guard;

/** Owns only positional command movement while a guard is outside combat. */
public final class SquadCommandMovementGoal extends Goal {
    private final Guard guard;
    private final SquadCommandMovementController movement;
    private final VillagerGuardGunnersConfig config;

    public SquadCommandMovementGoal(Guard guard) {
        this.guard = guard;
        this.movement = new SquadCommandMovementController(guard);
        this.config = ModConfigs.GUARD_GUNNERS;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!serverCommandsEnabled()) {
            return false;
        }
        return mayRun(movement.synchronizeSharedFocusTarget());
    }

    @Override
    public boolean canContinueToUse() {
        if (!serverCommandsEnabled()) {
            return false;
        }
        return mayRun(movement.synchronizeSharedFocusTarget());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!serverCommandsEnabled()) {
            return;
        }
        SquadCommand command = movement.synchronizeSharedFocusTarget();
        if (!mayRun(command)) {
            return;
        }
        long gameTime = guard.level().getGameTime();
        movement.apply(
                command,
                true,
                movement.pathSlotAvailable(gameTime),
                config.movement.approachSpeed.get());
    }

    @Override
    public void stop() {
        movement.stop();
    }

    private boolean mayRun(@Nullable SquadCommand command) {
        if (command == null
                || !command.type().requiresPosition()) {
            return false;
        }
        // An ended engagement must give the gun goal one short ownership
        // window to restore a stowed primary before positional movement wins
        // the shared MOVE flag again.
        if (GuardSidearmAccess.getState(guard) != SidearmState.PRIMARY_ACTIVE) {
            return false;
        }
        for (var wrapped : guard.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof GuardGunCombatGoal gun && gun.retainsCombatOwnership()) return false;
        }
        LivingEntity target = guard.getTarget();
        return target == null || !target.isAlive();
    }

    private boolean serverCommandsEnabled() {
        return !guard.level().isClientSide && config.enabled.get()
                && !com.gamergaming.villagerguardgunners.medical.TransportRuntime.busy(guard)
                && !com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard)
                && !com.gamergaming.villagerguardgunners.medical.MedicRuntime.retreating(guard);
    }

}
