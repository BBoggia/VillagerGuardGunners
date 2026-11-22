package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import java.util.Objects;

/**
 * Constant-time precedence gate evaluated before an ammunition summary or
 * Armory query is requested.
 */
public final class ArmoryResupplyGate {
    private ArmoryResupplyGate() {
    }

    public static GateStatus evaluate(
            boolean featureEnabled,
            boolean serverSide,
            boolean guardAlive,
            boolean guardAiEnabled,
            SidearmState sidearmState,
            boolean combatActive,
            boolean squadCommandActive,
            boolean following,
            GuardOrders.MovementOrder movementOrder,
            boolean guardPatrolling,
            boolean nativeWeaponBusy) {
        if (!featureEnabled) {
            return GateStatus.FEATURE_DISABLED;
        }
        if (!serverSide) {
            return GateStatus.NOT_SERVER_SIDE;
        }
        if (!guardAlive) {
            return GateStatus.GUARD_NOT_ALIVE;
        }
        if (!guardAiEnabled) {
            return GateStatus.AI_DISABLED;
        }
        if (sidearmState == null || sidearmState == SidearmState.RECOVERY_REQUIRED) {
            return GateStatus.RECOVERY_REQUIRED;
        }
        if (combatActive) {
            return GateStatus.COMBAT_ACTIVE;
        }
        if (squadCommandActive) {
            return GateStatus.SQUAD_COMMAND_ACTIVE;
        }
        if (following) {
            return GateStatus.FOLLOWING;
        }

        GuardOrders.MovementOrder normalizedMovement = Objects.requireNonNullElse(
                movementOrder,
                GuardOrders.MovementOrder.INHERIT);
        if (normalizedMovement.holdsPosition()) {
            return GateStatus.HOLD_POSITION;
        }
        if (normalizedMovement.treatAsPatrolling(guardPatrolling)) {
            return GateStatus.PATROL_BOUND;
        }
        if (nativeWeaponBusy) {
            return GateStatus.NATIVE_WEAPON_BUSY;
        }
        return GateStatus.READY;
    }

    public enum GateStatus {
        READY(true),
        FEATURE_DISABLED(false),
        NOT_SERVER_SIDE(false),
        GUARD_NOT_ALIVE(false),
        AI_DISABLED(false),
        RECOVERY_REQUIRED(false),
        COMBAT_ACTIVE(false),
        SQUAD_COMMAND_ACTIVE(false),
        FOLLOWING(false),
        HOLD_POSITION(false),
        PATROL_BOUND(false),
        NATIVE_WEAPON_BUSY(false),
        STATE_UNAVAILABLE(false);

        private final boolean ready;

        GateStatus(boolean ready) {
            this.ready = ready;
        }

        public boolean ready() {
            return ready;
        }
    }
}
