package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess;
import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadCommandAccess;
import com.gamergaming.villagerguardgunners.armory.resupply.GuardArmoryResupplyController;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import tallestegg.guardvillagers.entities.Guard;

/** Fail-closed live adapter for the pure automatic-duty precedence contract. */
public final class GuardAutomaticDutyLiveGate {
    private GuardAutomaticDutyLiveGate() {
    }

    public static GuardAutomaticDutyGate.GateStatus evaluate(
            boolean featureEnabled,
            @Nullable Guard guard,
            @Nullable AutomaticDutyKind dutyKind,
            boolean atDutyPosition) {
        if (guard == null) {
            return GuardAutomaticDutyGate.GateStatus.GUARD_NOT_ALIVE;
        }
        try {
            if (!featureEnabled) {
                return GuardAutomaticDutyGate.GateStatus.FEATURE_DISABLED;
            }
            if (guard.level().isClientSide) {
                return GuardAutomaticDutyGate.GateStatus.NOT_SERVER_SIDE;
            }
            if (!guard.isAlive() || guard.isRemoved()) {
                return GuardAutomaticDutyGate.GateStatus.GUARD_NOT_ALIVE;
            }
            if (guard.isNoAi() || com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard)) {
                return GuardAutomaticDutyGate.GateStatus.AI_DISABLED;
            }
            if (dutyKind == null) {
                return GuardAutomaticDutyGate.GateStatus.NO_DUTY;
            }
            SidearmState sidearmState = GuardSidearmAccess.getState(guard);
            if (sidearmState == null || sidearmState == SidearmState.RECOVERY_REQUIRED) {
                return GuardAutomaticDutyGate.GateStatus.RECOVERY_REQUIRED;
            }
            LivingEntity target = guard.getTarget();
            if (target != null && target.isAlive()) {
                return GuardAutomaticDutyGate.GateStatus.COMBAT_ACTIVE;
            }
            if (GuardSquadCommandAccess.getCommand(guard) != null) {
                return GuardAutomaticDutyGate.GateStatus.SQUAD_COMMAND_ACTIVE;
            }
            if (guard.isFollowing()) {
                return GuardAutomaticDutyGate.GateStatus.FOLLOWING;
            }
            GuardArmoryResupplyController resupply =
                    GuardArmoryResupplyAccess.getController(guard);
            if (com.gamergaming.villagerguardgunners.medical.MedicRuntime.busy(guard) || (resupply != null && resupply.hasActiveRequest())
                    || com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime.plan(guard) != null) {
                return GuardAutomaticDutyGate.GateStatus.RESUPPLY_ACTIVE;
            }
            MovementOrder movement = GuardRoleResolver.effectiveOrders(guard).movement();
            return GuardAutomaticDutyGate.evaluate(new GuardAutomaticDutyGate.Context(
                    true,
                    true,
                    true,
                    true,
                    sidearmState,
                    false,
                    false,
                    false,
                    false,
                    movement,
                    guard.isPatrolling(),
                    dutyKind,
                    atDutyPosition));
        } catch (RuntimeException exception) {
            return GuardAutomaticDutyGate.GateStatus.STATE_UNAVAILABLE;
        }
    }
}
