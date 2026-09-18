package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadCommandAccess;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.tacz.guns.api.entity.IGunOperator;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Fail-closed live counterpart to the pure Step 1 precedence gate. Active
 * movement and final withdrawal share this adapter so they cannot disagree
 * about which higher-priority behavior interrupts logistics.
 */
public final class ArmoryResupplyLiveGate {
    private ArmoryResupplyLiveGate() {
    }

    public static ArmoryResupplyGate.GateStatus evaluate(
            boolean featureEnabled,
            Guard guard) {
        if (guard == null) {
            return ArmoryResupplyGate.GateStatus.GUARD_NOT_ALIVE;
        }
        try {
            ArmoryResupplyGate.GateStatus lifecycle =
                    evaluateLifecycle(featureEnabled, guard);
            return lifecycle == null
                    ? evaluateAfterLifecycle(guard, GuardSidearmAccess.getState(guard))
                    : lifecycle;
        } catch (RuntimeException exception) {
            return ArmoryResupplyGate.GateStatus.STATE_UNAVAILABLE;
        }
    }

    public static ArmoryResupplyGate.GateStatus evaluate(
            boolean featureEnabled,
            Guard guard,
            SidearmState sidearmState) {
        if (guard == null) {
            return ArmoryResupplyGate.GateStatus.GUARD_NOT_ALIVE;
        }
        try {
            ArmoryResupplyGate.GateStatus lifecycle =
                    evaluateLifecycle(featureEnabled, guard);
            return lifecycle == null
                    ? evaluateAfterLifecycle(guard, sidearmState)
                    : lifecycle;
        } catch (RuntimeException exception) {
            return ArmoryResupplyGate.GateStatus.STATE_UNAVAILABLE;
        }
    }

    private static @Nullable ArmoryResupplyGate.GateStatus evaluateLifecycle(
            boolean featureEnabled,
            Guard guard) {
        if (!featureEnabled) {
            return ArmoryResupplyGate.GateStatus.FEATURE_DISABLED;
        }
        if (guard.level().isClientSide) {
            return ArmoryResupplyGate.GateStatus.NOT_SERVER_SIDE;
        }
        if (!guard.isAlive() || guard.isRemoved()) {
            return ArmoryResupplyGate.GateStatus.GUARD_NOT_ALIVE;
        }
        if (guard.isNoAi()) {
            return ArmoryResupplyGate.GateStatus.AI_DISABLED;
        }
        return null;
    }

    /** Preserves the pure gate's precedence while avoiding eager costly reads. */
    private static ArmoryResupplyGate.GateStatus evaluateAfterLifecycle(
            Guard guard,
            SidearmState sidearmState) {
        if (sidearmState == null || sidearmState == SidearmState.RECOVERY_REQUIRED) {
            return ArmoryResupplyGate.GateStatus.RECOVERY_REQUIRED;
        }
        LivingEntity target = guard.getTarget();
        var resupply = com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess.getController(guard);
        if (target != null && target.isAlive()
                && (resupply == null || !resupply.emergencyCombatPermitted())) {
            return ArmoryResupplyGate.GateStatus.COMBAT_ACTIVE;
        }
        if (GuardSquadCommandAccess.getCommand(guard) != null) {
            return ArmoryResupplyGate.GateStatus.SQUAD_COMMAND_ACTIVE;
        }
        if (guard.isFollowing() || (guard instanceof com.gamergaming.villagerguardgunners.api.GuardCoverageAccess access
                && access.villagerGuardGunners$hasFollowOrder())) {
            return ArmoryResupplyGate.GateStatus.FOLLOWING;
        }
        GuardOrders.MovementOrder movement =
                GuardRoleResolver.effectiveOrders(guard).movement();
        if (movement.holdsPosition()) {
            return ArmoryResupplyGate.GateStatus.HOLD_POSITION;
        }
        var duty = com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.current(guard);
        if (movement.treatAsPatrolling(guard.isPatrolling())
                || (resupply != null && resupply.isEmergency() && duty != null
                    && duty.source() == com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.Source.PATROL_ROUTE)) {
            return ArmoryResupplyGate.GateStatus.PATROL_BOUND;
        }
        return nativeWeaponBusy(guard)
                ? ArmoryResupplyGate.GateStatus.NATIVE_WEAPON_BUSY
                : ArmoryResupplyGate.GateStatus.READY;
    }

    /** Unreadable TaCZ state is busy so logistics never interrupts it. */
    private static boolean nativeWeaponBusy(Guard guard) {
        try {
            IGunOperator operator = IGunOperator.fromLivingEntity(guard);
            var data = operator.getDataHolder();
            return data.reloadStateType == null
                    || data.reloadStateType.isReloading()
                    || data.isBolting
                    || data.meleePrepTickCount >= 0
                    || data.chargeProgress > 0.0F
                    || operator.getSynReloadState().getStateType().isReloading()
                    || operator.getSynIsBolting()
                    || operator.getSynMeleeCoolDown() > 0L
                    || operator.getSynDrawCoolDown() > 0L
                    || operator.getSynShootCoolDown() > 0L;
        } catch (RuntimeException exception) {
            return true;
        }
    }
}
