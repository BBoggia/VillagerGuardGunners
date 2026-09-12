package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Constant-time, mutation-free precedence contract for future automatic post
 * and patrol movement.
 *
 * <p>A ready result does not start navigation. The eventual movement goal must
 * still validate its assignment, destination, loaded chunks, and path cadence.</p>
 */
public final class GuardAutomaticDutyGate {
    private GuardAutomaticDutyGate() {
    }

    public static GateStatus evaluate(Context context) {
        Context state = Objects.requireNonNull(context, "context");
        if (!state.featureEnabled()) {
            return GateStatus.FEATURE_DISABLED;
        }
        if (!state.serverSide()) {
            return GateStatus.NOT_SERVER_SIDE;
        }
        if (!state.guardAlive()) {
            return GateStatus.GUARD_NOT_ALIVE;
        }
        if (!state.guardAiEnabled()) {
            return GateStatus.AI_DISABLED;
        }
        if (state.dutyKind() == null) {
            return GateStatus.NO_DUTY;
        }
        if (state.sidearmState() == null
                || state.sidearmState() == SidearmState.RECOVERY_REQUIRED) {
            return GateStatus.RECOVERY_REQUIRED;
        }
        if (state.combatActive()) {
            return GateStatus.COMBAT_ACTIVE;
        }
        if (state.squadCommandActive()) {
            return GateStatus.SQUAD_COMMAND_ACTIVE;
        }
        if (state.following()) {
            return GateStatus.FOLLOWING;
        }
        if (state.resupplyActive()) {
            return GateStatus.RESUPPLY_ACTIVE;
        }

        MovementOrder movement = Objects.requireNonNullElse(
                state.movementOrder(), MovementOrder.INHERIT);
        if (state.dutyKind() == AutomaticDutyKind.PATROL_ROUTE) {
            // A saved VGG route is itself the explicit patrol assignment. It
            // temporarily owns movement without rewriting Guard Villagers'
            // patrol flag or patrol position.
            return movement.holdsPosition()
                    ? GateStatus.HOLD_POSITION
                    : GateStatus.READY_TO_TRAVEL;
        }

        if (movement == MovementOrder.INHERIT && state.guardPatrolling()) {
            return GateStatus.UPSTREAM_PATROL_ACTIVE;
        }
        if (movement.holdsPosition()) {
            return state.atDutyPosition()
                    ? GateStatus.READY_AT_POST
                    : GateStatus.HOLD_POSITION;
        }
        if (movement == MovementOrder.PATROL_BOUND) {
            return state.atDutyPosition()
                    ? GateStatus.READY_AT_POST
                    : GateStatus.PATROL_BOUND;
        }
        return state.atDutyPosition()
                ? GateStatus.READY_AT_POST
                : GateStatus.READY_TO_TRAVEL;
    }

    public record Context(
            boolean featureEnabled,
            boolean serverSide,
            boolean guardAlive,
            boolean guardAiEnabled,
            @Nullable SidearmState sidearmState,
            boolean combatActive,
            boolean squadCommandActive,
            boolean following,
            boolean resupplyActive,
            @Nullable MovementOrder movementOrder,
            boolean guardPatrolling,
            @Nullable AutomaticDutyKind dutyKind,
            boolean atDutyPosition) {
    }

    public enum GateStatus {
        READY_TO_TRAVEL(true, true),
        READY_AT_POST(true, false),
        FEATURE_DISABLED(false, false),
        NOT_SERVER_SIDE(false, false),
        GUARD_NOT_ALIVE(false, false),
        AI_DISABLED(false, false),
        NO_DUTY(false, false),
        RECOVERY_REQUIRED(false, false),
        COMBAT_ACTIVE(false, false),
        SQUAD_COMMAND_ACTIVE(false, false),
        FOLLOWING(false, false),
        RESUPPLY_ACTIVE(false, false),
        HOLD_POSITION(false, false),
        PATROL_BOUND(false, false),
        UPSTREAM_PATROL_ACTIVE(false, false),
        STATE_UNAVAILABLE(false, false);

        private final boolean ready;
        private final boolean mayTravel;

        GateStatus(boolean ready, boolean mayTravel) {
            this.ready = ready;
            this.mayTravel = mayTravel;
        }

        public boolean ready() {
            return ready;
        }

        public boolean mayTravel() {
            return mayTravel;
        }
    }
}
