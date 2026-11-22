package com.gamergaming.villagerguardgunners.village.coverage;

import com.gamergaming.villagerguardgunners.defense.duty.GuardAutomaticDutyGate;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import java.util.Objects;

/** Additional coverage gates. Does not change membership, flags, orders or equipment. */
public final class VillageCoverageAvailability {
    private VillageCoverageAvailability() { }

    public static Reason evaluate(Context context) {
        Objects.requireNonNull(context, "context");
        if (!context.loaded()) { return Reason.UNLOADED; }
        if (context.downedOrTransported()) { return Reason.DOWNED_OR_TRANSPORTED; }
        if (!context.nativeActionIdle()) { return Reason.NATIVE_ACTION; }
        if (!context.participationAllowed()) { return Reason.NOT_ENROLLED; }
        if (context.manualSquad() && !context.squadOptedIn()) { return Reason.MANUAL_SQUAD; }
        if (context.savedRoute()) { return Reason.MANUAL_ROUTE; }
        if (context.movement().holdsPosition()) { return Reason.HOLD_POSITION; }
        if (context.movement() == MovementOrder.PATROL_BOUND) { return Reason.MANUAL_PATROL; }
        if (context.upstreamPatrol()) { return Reason.MANUAL_PATROL; }
        if (context.urgentMedical()) { return Reason.MEDICAL; }
        if (context.otherMovementOwner()) { return Reason.OTHER_DUTY; }
        if (!context.healthy()) { return Reason.INJURED; }
        if (!context.equipped()) { return Reason.EQUIPMENT; }
        return switch (context.dutyGate()) {
            case READY_TO_TRAVEL, READY_AT_POST -> Reason.AVAILABLE;
            case COMBAT_ACTIVE -> Reason.FIGHTING;
            case FOLLOWING -> Reason.FOLLOWING;
            case SQUAD_COMMAND_ACTIVE -> Reason.MANUAL_COMMAND;
            case HOLD_POSITION -> Reason.HOLD_POSITION;
            case PATROL_BOUND, UPSTREAM_PATROL_ACTIVE -> Reason.MANUAL_PATROL;
            case RESUPPLY_ACTIVE -> Reason.RESUPPLY;
            case GUARD_NOT_ALIVE -> Reason.NOT_ALIVE;
            case AI_DISABLED -> Reason.NO_AI;
            case RECOVERY_REQUIRED -> Reason.NATIVE_ACTION;
            default -> Reason.DISABLED_OR_UNKNOWN;
        };
    }

    public record Context(boolean loaded, boolean downedOrTransported,
            boolean nativeActionIdle, boolean participationAllowed,
            boolean manualSquad, boolean squadOptedIn, boolean savedRoute,
            MovementOrder movement, boolean upstreamPatrol, boolean urgentMedical,
            boolean otherMovementOwner, boolean healthy, boolean equipped,
            GuardAutomaticDutyGate.GateStatus dutyGate) {
        public Context {
            Objects.requireNonNull(movement, "movement");
            Objects.requireNonNull(dutyGate, "dutyGate");
        }
    }

    public enum Reason {
        AVAILABLE, UNLOADED, NOT_ALIVE, NO_AI, DOWNED_OR_TRANSPORTED,
        NATIVE_ACTION, NOT_ENROLLED, MANUAL_SQUAD, MANUAL_ROUTE, HOLD_POSITION,
        MANUAL_PATROL, MEDICAL, OTHER_DUTY, INJURED, EQUIPMENT, FIGHTING,
        FOLLOWING, MANUAL_COMMAND, RESUPPLY, DISABLED_OR_UNKNOWN
    }
}
