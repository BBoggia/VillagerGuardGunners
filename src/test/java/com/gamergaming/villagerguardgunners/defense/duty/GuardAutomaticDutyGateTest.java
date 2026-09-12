package com.gamergaming.villagerguardgunners.defense.duty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.defense.duty.GuardAutomaticDutyGate.Context;
import com.gamergaming.villagerguardgunners.defense.duty.GuardAutomaticDutyGate.GateStatus;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import org.junit.jupiter.api.Test;

final class GuardAutomaticDutyGateTest {
    @Test
    void ordinaryPostAndRouteDutiesMayTravel() {
        assertAll(
                () -> assertEquals(
                        GateStatus.READY_TO_TRAVEL,
                        evaluate(AutomaticDutyKind.DEFENSE_POST, MovementOrder.INHERIT,
                                false, false)),
                () -> assertEquals(
                        GateStatus.READY_TO_TRAVEL,
                        evaluate(AutomaticDutyKind.PATROL_ROUTE, MovementOrder.PATROL_BOUND,
                                true, false)),
                () -> assertTrue(GateStatus.READY_TO_TRAVEL.ready()),
                () -> assertTrue(GateStatus.READY_TO_TRAVEL.mayTravel()));
    }

    @Test
    void lifecycleSafetyCombatAndExplicitCommandsHaveStrictPrecedence() {
        Context blockedEveryWay = new Context(
                false, false, false, false, SidearmState.RECOVERY_REQUIRED,
                true, true, true, true, MovementOrder.HOLD_POSITION,
                true, null, false);
        assertEquals(GateStatus.FEATURE_DISABLED,
                GuardAutomaticDutyGate.evaluate(blockedEveryWay));

        assertAll(
                () -> assertEquals(GateStatus.NOT_SERVER_SIDE,
                        GuardAutomaticDutyGate.evaluate(context(false, true, true,
                                SidearmState.RECOVERY_REQUIRED, true, true, true, true))),
                () -> assertEquals(GateStatus.GUARD_NOT_ALIVE,
                        GuardAutomaticDutyGate.evaluate(context(true, false, true,
                                SidearmState.RECOVERY_REQUIRED, true, true, true, true))),
                () -> assertEquals(GateStatus.AI_DISABLED,
                        GuardAutomaticDutyGate.evaluate(context(true, true, false,
                                SidearmState.RECOVERY_REQUIRED, true, true, true, true))),
                () -> assertEquals(GateStatus.RECOVERY_REQUIRED,
                        GuardAutomaticDutyGate.evaluate(context(true, true, true,
                                SidearmState.RECOVERY_REQUIRED, true, true, true, true))),
                () -> assertEquals(GateStatus.COMBAT_ACTIVE,
                        GuardAutomaticDutyGate.evaluate(context(true, true, true,
                                SidearmState.PRIMARY_ACTIVE, true, true, true, true))),
                () -> assertEquals(GateStatus.SQUAD_COMMAND_ACTIVE,
                        GuardAutomaticDutyGate.evaluate(context(true, true, true,
                                SidearmState.PRIMARY_ACTIVE, false, true, true, true))),
                () -> assertEquals(GateStatus.FOLLOWING,
                        GuardAutomaticDutyGate.evaluate(context(true, true, true,
                                SidearmState.PRIMARY_ACTIVE, false, false, true, true))),
                () -> assertEquals(GateStatus.RESUPPLY_ACTIVE,
                        GuardAutomaticDutyGate.evaluate(context(true, true, true,
                                SidearmState.PRIMARY_ACTIVE, false, false, false, true))));
    }

    @Test
    void inheritedGuardVillagersPatrolYieldsPostsButNotExplicitRoutes() {
        assertAll(
                () -> assertEquals(
                        GateStatus.UPSTREAM_PATROL_ACTIVE,
                        evaluate(AutomaticDutyKind.DEFENSE_POST, MovementOrder.INHERIT,
                                true, false)),
                () -> assertEquals(
                        GateStatus.READY_TO_TRAVEL,
                        evaluate(AutomaticDutyKind.PATROL_ROUTE, MovementOrder.INHERIT,
                                true, false)),
                () -> assertEquals(
                        GateStatus.READY_TO_TRAVEL,
                        evaluate(AutomaticDutyKind.DEFENSE_POST, MovementOrder.TACTICAL,
                                true, false)));
    }

    @Test
    void restrictiveOrdersMaintainReachedPostsWithoutAuthorizingTravel() {
        assertAll(
                () -> assertEquals(
                        GateStatus.HOLD_POSITION,
                        evaluate(AutomaticDutyKind.DEFENSE_POST, MovementOrder.HOLD_POSITION,
                                false, false)),
                () -> assertEquals(
                        GateStatus.PATROL_BOUND,
                        evaluate(AutomaticDutyKind.DEFENSE_POST, MovementOrder.PATROL_BOUND,
                                false, false)),
                () -> assertEquals(
                        GateStatus.READY_AT_POST,
                        evaluate(AutomaticDutyKind.DEFENSE_POST, MovementOrder.HOLD_POSITION,
                                false, true)),
                () -> assertEquals(
                        GateStatus.READY_AT_POST,
                        evaluate(AutomaticDutyKind.DEFENSE_POST, MovementOrder.PATROL_BOUND,
                                false, true)),
                () -> assertTrue(GateStatus.READY_AT_POST.ready()),
                () -> assertFalse(GateStatus.READY_AT_POST.mayTravel()),
                () -> assertEquals(
                        GateStatus.HOLD_POSITION,
                        evaluate(AutomaticDutyKind.PATROL_ROUTE, MovementOrder.HOLD_POSITION,
                                false, true)));
    }

    @Test
    void missingDutyAndUnreadableSidearmStateFailClosed() {
        Context noDuty = new Context(
                true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                false, false, false, false, MovementOrder.INHERIT,
                false, null, false);
        Context unreadableSidearm = new Context(
                true, true, true, true, null,
                false, false, false, false, MovementOrder.INHERIT,
                false, AutomaticDutyKind.DEFENSE_POST, false);

        assertAll(
                () -> assertEquals(
                        GateStatus.NO_DUTY,
                        GuardAutomaticDutyGate.evaluate(noDuty)),
                () -> assertEquals(
                        GateStatus.RECOVERY_REQUIRED,
                        GuardAutomaticDutyGate.evaluate(unreadableSidearm)),
                () -> assertFalse(GateStatus.STATE_UNAVAILABLE.ready()));
    }

    private static GateStatus evaluate(
            AutomaticDutyKind kind,
            MovementOrder movement,
            boolean patrolling,
            boolean atPosition) {
        return GuardAutomaticDutyGate.evaluate(new Context(
                true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                false, false, false, false, movement,
                patrolling, kind, atPosition));
    }

    private static Context context(
            boolean serverSide,
            boolean alive,
            boolean aiEnabled,
            SidearmState sidearmState,
            boolean combat,
            boolean command,
            boolean following,
            boolean resupply) {
        return new Context(
                true, serverSide, alive, aiEnabled, sidearmState,
                combat, command, following, resupply, MovementOrder.HOLD_POSITION,
                true, AutomaticDutyKind.DEFENSE_POST, false);
    }
}
