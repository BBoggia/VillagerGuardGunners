package com.gamergaming.villagerguardgunners.defense.duty;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class GuardAutomaticDutyMovementControllerTest {
    @Test
    void arrivalUsesIndependentHorizontalAndVerticalBounds() {
        BlockPos destination = new BlockPos(10, 64, -4);

        assertAll(
                () -> assertTrue(GuardAutomaticDutyMovementController.atPosition(
                        10.5D, 64.0D, -3.5D, destination)),
                () -> assertTrue(GuardAutomaticDutyMovementController.atPosition(
                        12.0D, 65.25D, -3.5D, destination)),
                () -> assertFalse(GuardAutomaticDutyMovementController.atPosition(
                        12.01D, 64.0D, -3.5D, destination)),
                () -> assertFalse(GuardAutomaticDutyMovementController.atPosition(
                        10.5D, 65.26D, -3.5D, destination)));
    }

    @Test
    void failedPathsBackOffExponentiallyUnderAHardCeiling() {
        assertAll(
                () -> assertEquals(20,
                        GuardAutomaticDutyMovementController.retryDelayTicks(20, 1)),
                () -> assertEquals(40,
                        GuardAutomaticDutyMovementController.retryDelayTicks(20, 2)),
                () -> assertEquals(80,
                        GuardAutomaticDutyMovementController.retryDelayTicks(20, 3)),
                () -> assertEquals(160,
                        GuardAutomaticDutyMovementController.retryDelayTicks(20, 4)),
                () -> assertEquals(200,
                        GuardAutomaticDutyMovementController.retryDelayTicks(20, 5)),
                () -> assertEquals(200,
                        GuardAutomaticDutyMovementController.retryDelayTicks(
                                Integer.MAX_VALUE, Integer.MAX_VALUE)));
    }

    @Test
    void progressAcceptsDetoursButRejectsAnUnmovingGuard() {
        assertAll(
                () -> assertTrue(GuardAutomaticDutyMovementController.madeProgress(
                        20.0D, 20.0D,
                        0.0D, 64.0D, 0.0D,
                        0.5D, 64.0D, 0.0D)),
                () -> assertTrue(GuardAutomaticDutyMovementController.madeProgress(
                        20.0D, 19.5D,
                        0.0D, 64.0D, 0.0D,
                        0.0D, 64.0D, 0.0D)),
                () -> assertFalse(GuardAutomaticDutyMovementController.madeProgress(
                        20.0D, 19.9D,
                        0.0D, 64.0D, 0.0D,
                        0.1D, 64.0D, 0.0D)),
                () -> assertFalse(GuardAutomaticDutyMovementController.madeProgress(
                        Double.NaN, 19.0D,
                        0.0D, 64.0D, 0.0D,
                        1.0D, 64.0D, 0.0D)));
    }

    @Test
    void diagnosticsRejectPartialAssignmentIdentity() {
        UUID networkId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        BlockPos destination = BlockPos.ZERO;

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new GuardAutomaticDutyMovementController.RuntimeSnapshot(
                                GuardAutomaticDutyMovementController.MovementStatus.ASSIGNED,
                                GuardAutomaticDutyGate.GateStatus.READY_TO_TRAVEL,
                                networkId,
                                null,
                                destination,
                                true,
                                false,
                                0,
                                0,
                                0L)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new GuardAutomaticDutyMovementController.RuntimeSnapshot(
                                GuardAutomaticDutyMovementController.MovementStatus.IDLE,
                                GuardAutomaticDutyGate.GateStatus.NO_DUTY,
                                null,
                                slotId,
                                destination,
                                false,
                                false,
                                0,
                                0,
                                -1L)));
    }
}
