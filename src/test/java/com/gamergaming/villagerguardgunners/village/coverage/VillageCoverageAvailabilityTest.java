package com.gamergaming.villagerguardgunners.village.coverage;

import static org.junit.jupiter.api.Assertions.*;

import com.gamergaming.villagerguardgunners.defense.duty.GuardAutomaticDutyGate.GateStatus;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageAvailability.Context;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageAvailability.Reason;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class VillageCoverageAvailabilityTest {
    @Test void uncommandedResidentCanParticipateAndManualSquadRequiresExplicitOptIn() {
        var input = new Input();
        assertEquals(Reason.AVAILABLE, input.evaluate());
        input.squad = true;
        assertEquals(Reason.MANUAL_SQUAD, input.evaluate());
        input.optIn = true;
        assertEquals(Reason.AVAILABLE, input.evaluate());
        input.route = true;
        assertEquals(Reason.MANUAL_ROUTE, input.evaluate());
        input.route = false;
        input.gate = GateStatus.SQUAD_COMMAND_ACTIVE;
        assertEquals(Reason.MANUAL_COMMAND, input.evaluate());
    }

    @ParameterizedTest @MethodSource("blockers")
    void coverageNeverBypassesAnExistingOwnerOrAvailabilityBlock(Consumer<Input> change, Reason expected) {
        Input input = new Input();
        change.accept(input);
        assertEquals(expected, input.evaluate());
    }

    static Stream<Arguments> blockers() {
        return Stream.of(
                blocker(i -> i.loaded = false, Reason.UNLOADED),
                blocker(i -> i.downed = true, Reason.DOWNED_OR_TRANSPORTED),
                blocker(i -> i.idle = false, Reason.NATIVE_ACTION),
                blocker(i -> i.enrolled = false, Reason.NOT_ENROLLED),
                blocker(i -> i.movement = MovementOrder.HOLD_POSITION, Reason.HOLD_POSITION),
                blocker(i -> i.movement = MovementOrder.PATROL_BOUND, Reason.MANUAL_PATROL),
                blocker(i -> i.patrolling = true, Reason.MANUAL_PATROL),
                blocker(i -> i.medical = true, Reason.MEDICAL),
                blocker(i -> i.owner = true, Reason.OTHER_DUTY),
                blocker(i -> i.healthy = false, Reason.INJURED),
                blocker(i -> i.equipped = false, Reason.EQUIPMENT),
                blocker(i -> i.gate = GateStatus.COMBAT_ACTIVE, Reason.FIGHTING),
                blocker(i -> i.gate = GateStatus.FOLLOWING, Reason.FOLLOWING),
                blocker(i -> i.gate = GateStatus.RESUPPLY_ACTIVE, Reason.RESUPPLY),
                blocker(i -> i.gate = GateStatus.GUARD_NOT_ALIVE, Reason.NOT_ALIVE),
                blocker(i -> i.gate = GateStatus.AI_DISABLED, Reason.NO_AI),
                blocker(i -> i.gate = GateStatus.RECOVERY_REQUIRED, Reason.NATIVE_ACTION),
                blocker(i -> i.gate = GateStatus.STATE_UNAVAILABLE, Reason.DISABLED_OR_UNKNOWN),
                blocker(i -> i.gate = GateStatus.FEATURE_DISABLED, Reason.DISABLED_OR_UNKNOWN),
                blocker(i -> i.gate = GateStatus.NOT_SERVER_SIDE, Reason.DISABLED_OR_UNKNOWN));
    }
    private static Arguments blocker(Consumer<Input> change, Reason expected) { return Arguments.of(change, expected); }

    private static final class Input {
        boolean loaded = true, downed, idle = true, enrolled = true, squad, optIn, route, patrolling, medical, owner;
        boolean healthy = true, equipped = true;
        MovementOrder movement = MovementOrder.INHERIT;
        GateStatus gate = GateStatus.READY_TO_TRAVEL;
        Reason evaluate() {
            return VillageCoverageAvailability.evaluate(new Context(loaded, downed, idle, enrolled, squad, optIn,
                    route, movement, patrolling, medical, owner, healthy, equipped, gate));
        }
    }
}
