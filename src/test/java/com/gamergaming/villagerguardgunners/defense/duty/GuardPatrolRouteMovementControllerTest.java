package com.gamergaming.villagerguardgunners.defense.duty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.squad.PatrolRouteExecutionSettings;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class GuardPatrolRouteMovementControllerTest {
    @Test
    void nearestWaypointIsDeterministicAndUsesStandingPosition() {
        List<BlockPos> route = List.of(
                new BlockPos(0, 64, 0),
                new BlockPos(10, 64, 0),
                new BlockPos(10, 64, 10));

        assertAll(
                () -> assertEquals(0,
                        GuardPatrolRouteMovementController.nearestWaypoint(
                                route, 0.5D, 64.0D, 0.5D)),
                () -> assertEquals(1,
                        GuardPatrolRouteMovementController.nearestWaypoint(
                                route, 9.5D, 64.0D, 0.5D)),
                () -> assertEquals(0,
                        GuardPatrolRouteMovementController.nearestWaypoint(
                                route, 5.5D, 64.0D, 0.5D)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        GuardPatrolRouteMovementController.nearestWaypoint(
                                List.of(), 0.0D, 0.0D, 0.0D)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        GuardPatrolRouteMovementController.nearestWaypoint(
                                route, Double.NaN, 0.0D, 0.0D)));
    }

    @Test
    void routeProgressWrapsWithoutLeavingItsBounds() {
        assertAll(
                () -> assertEquals(1,
                        GuardPatrolRouteMovementController.nextWaypointIndex(0, 3)),
                () -> assertEquals(2,
                        GuardPatrolRouteMovementController.nextWaypointIndex(1, 3)),
                () -> assertEquals(0,
                        GuardPatrolRouteMovementController.nextWaypointIndex(2, 3)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        GuardPatrolRouteMovementController.nextWaypointIndex(-1, 3)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        GuardPatrolRouteMovementController.nextWaypointIndex(0, 1)));
    }

    @Test
    void arrivalRetryAndProgressChecksRemainBounded() {
        BlockPos destination = new BlockPos(10, 64, -4);

        assertAll(
                () -> assertTrue(GuardPatrolRouteMovementController.atPosition(
                        10.5D, 64.0D, -3.5D, destination)),
                () -> assertFalse(GuardPatrolRouteMovementController.atPosition(
                        12.01D, 64.0D, -3.5D, destination)),
                () -> assertEquals(20,
                        GuardPatrolRouteMovementController.retryDelayTicks(20, 1)),
                () -> assertEquals(40,
                        GuardPatrolRouteMovementController.retryDelayTicks(20, 2)),
                () -> assertEquals(200,
                        GuardPatrolRouteMovementController.retryDelayTicks(20, 10)),
                () -> assertTrue(GuardPatrolRouteMovementController.madeProgress(
                        20.0D, 20.0D,
                        0.0D, 64.0D, 0.0D,
                        0.5D, 64.0D, 0.0D)),
                () -> assertFalse(GuardPatrolRouteMovementController.madeProgress(
                        20.0D, 19.9D,
                        0.0D, 64.0D, 0.0D,
                        0.1D, 64.0D, 0.0D)));
    }

    @Test
    void diagnosticsRejectPartialRouteIdentity() {
        UUID squadId = UUID.randomUUID();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new GuardPatrolRouteMovementController.RuntimeSnapshot(
                                GuardPatrolRouteMovementController.MovementStatus.ROUTE_ASSIGNED,
                                GuardAutomaticDutyGate.GateStatus.READY_TO_TRAVEL,
                                squadId,
                                -1L,
                                0,
                                2,
                                BlockPos.ZERO,
                                true,
                                true,
                                false,
                                0,
                                0,
                                0,
                                0L,
                                true,
                                0.8D,
                                20,
                                3)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new GuardPatrolRouteMovementController.RuntimeSnapshot(
                                GuardPatrolRouteMovementController.MovementStatus.NO_ROUTE,
                                GuardAutomaticDutyGate.GateStatus.NO_DUTY,
                                null,
                                -1L,
                                0,
                                0,
                                null,
                                false,
                                false,
                                false,
                                0,
                                0,
                                0,
                                -1L,
                                true,
                                0.8D,
                                20,
                                3)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new GuardPatrolRouteMovementController.RuntimeSnapshot(
                                GuardPatrolRouteMovementController.MovementStatus.ROUTE_ASSIGNED,
                                GuardAutomaticDutyGate.GateStatus.READY_TO_TRAVEL,
                                squadId,
                                1L,
                                0,
                                2,
                                BlockPos.ZERO,
                                true,
                                true,
                                false,
                                0,
                                0,
                                0,
                                0L,
                                false,
                                PatrolRouteExecutionSettings.DEFAULT.movementSpeed(),
                                PatrolRouteExecutionSettings.DEFAULT.waypointDwellTicks(),
                                PatrolRouteExecutionSettings.DEFAULT
                                        .maximumFailuresPerWaypoint())));
    }
}
