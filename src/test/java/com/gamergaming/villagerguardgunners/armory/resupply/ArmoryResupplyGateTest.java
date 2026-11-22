package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import org.junit.jupiter.api.Test;

final class ArmoryResupplyGateTest {
    @Test
    void ordinaryIdleTacticalGuardMayAssessNeed() {
        assertEquals(
                ArmoryResupplyGate.GateStatus.READY,
                evaluate(true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                        false, false, false, GuardOrders.MovementOrder.TACTICAL,
                        true, false));
        assertTrue(ArmoryResupplyGate.GateStatus.READY.ready());
    }

    @Test
    void lifecycleAndRecoveryChecksHaveStrictPrecedence() {
        assertEquals(
                ArmoryResupplyGate.GateStatus.FEATURE_DISABLED,
                evaluate(false, false, false, false, SidearmState.RECOVERY_REQUIRED,
                        true, true, true, GuardOrders.MovementOrder.HOLD_POSITION,
                        true, true));
        assertEquals(
                ArmoryResupplyGate.GateStatus.NOT_SERVER_SIDE,
                evaluate(true, false, false, false, SidearmState.RECOVERY_REQUIRED,
                        true, true, true, GuardOrders.MovementOrder.HOLD_POSITION,
                        true, true));
        assertEquals(
                ArmoryResupplyGate.GateStatus.GUARD_NOT_ALIVE,
                evaluate(true, true, false, false, SidearmState.RECOVERY_REQUIRED,
                        true, true, true, GuardOrders.MovementOrder.HOLD_POSITION,
                        true, true));
        assertEquals(
                ArmoryResupplyGate.GateStatus.AI_DISABLED,
                evaluate(true, true, true, false, SidearmState.RECOVERY_REQUIRED,
                        true, true, true, GuardOrders.MovementOrder.HOLD_POSITION,
                        true, true));
        assertEquals(
                ArmoryResupplyGate.GateStatus.RECOVERY_REQUIRED,
                evaluate(true, true, true, true, SidearmState.RECOVERY_REQUIRED,
                        true, true, true, GuardOrders.MovementOrder.HOLD_POSITION,
                        true, true));
    }

    @Test
    void combatCommandsAndNativeGuardVillagersMovementWinBeforeLogistics() {
        assertEquals(
                ArmoryResupplyGate.GateStatus.COMBAT_ACTIVE,
                evaluate(true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                        true, true, true, GuardOrders.MovementOrder.HOLD_POSITION,
                        true, true));
        assertEquals(
                ArmoryResupplyGate.GateStatus.SQUAD_COMMAND_ACTIVE,
                evaluate(true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                        false, true, true, GuardOrders.MovementOrder.HOLD_POSITION,
                        true, true));
        assertEquals(
                ArmoryResupplyGate.GateStatus.FOLLOWING,
                evaluate(true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                        false, false, true, GuardOrders.MovementOrder.HOLD_POSITION,
                        true, true));
        assertEquals(
                ArmoryResupplyGate.GateStatus.HOLD_POSITION,
                evaluate(true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                        false, false, false, GuardOrders.MovementOrder.HOLD_POSITION,
                        true, true));
        assertEquals(
                ArmoryResupplyGate.GateStatus.PATROL_BOUND,
                evaluate(true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                        false, false, false, GuardOrders.MovementOrder.PATROL_BOUND,
                        false, true));
        assertEquals(
                ArmoryResupplyGate.GateStatus.NATIVE_WEAPON_BUSY,
                evaluate(true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                        false, false, false, GuardOrders.MovementOrder.TACTICAL,
                        true, true));
    }

    @Test
    void inheritedPatrolBlocksButExplicitTacticalMovementDoesNot() {
        assertEquals(
                ArmoryResupplyGate.GateStatus.PATROL_BOUND,
                evaluate(true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                        false, false, false, GuardOrders.MovementOrder.INHERIT,
                        true, false));
        assertEquals(
                ArmoryResupplyGate.GateStatus.READY,
                evaluate(true, true, true, true, SidearmState.PRIMARY_ACTIVE,
                        false, false, false, GuardOrders.MovementOrder.TACTICAL,
                        true, false));
        assertFalse(ArmoryResupplyGate.GateStatus.PATROL_BOUND.ready());
    }

    private static ArmoryResupplyGate.GateStatus evaluate(
            boolean featureEnabled,
            boolean serverSide,
            boolean alive,
            boolean aiEnabled,
            SidearmState sidearmState,
            boolean combatActive,
            boolean squadCommandActive,
            boolean following,
            GuardOrders.MovementOrder movementOrder,
            boolean patrolling,
            boolean nativeBusy) {
        return ArmoryResupplyGate.evaluate(
                featureEnabled,
                serverSide,
                alive,
                aiEnabled,
                sidearmState,
                combatActive,
                squadCommandActive,
                following,
                movementOrder,
                patrolling,
                nativeBusy);
    }
}
