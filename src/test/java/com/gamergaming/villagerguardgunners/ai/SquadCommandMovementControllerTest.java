package com.gamergaming.villagerguardgunners.ai;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class SquadCommandMovementControllerTest {
    private static final BlockPos CENTER = new BlockPos(10, 64, 10);

    @Test
    void arrivalUsesInclusiveHorizontalAndVerticalBoundaries() {
        assertAll(
                () -> assertTrue(SquadCommandMovementController.atPosition(
                        12.0D, 66.0D, 10.5D, CENTER, 1.5D, 2.0D)),
                () -> assertFalse(SquadCommandMovementController.atPosition(
                        12.01D, 66.0D, 10.5D, CENTER, 1.5D, 2.0D)),
                () -> assertFalse(SquadCommandMovementController.atPosition(
                        10.5D, 66.01D, 10.5D, CENTER, 1.5D, 2.0D)),
                () -> assertFalse(SquadCommandMovementController.atPosition(
                        10.6D, 64.0D, 10.5D, CENTER, -1.0D, -1.0D)),
                () -> assertTrue(SquadCommandMovementController.atPosition(
                        10.5D, 64.0D, 10.5D, CENTER, 0.0D, 0.0D)));
    }

    @Test
    void defendBoundaryKeepsAOneBlockNavigationMargin() {
        assertAll(
                () -> assertFalse(SquadCommandMovementController.outsideDefendBoundary(
                        21.5D, 10.5D, CENTER, 12.0D)),
                () -> assertTrue(SquadCommandMovementController.outsideDefendBoundary(
                        21.51D, 10.5D, CENTER, 12.0D)),
                () -> assertFalse(SquadCommandMovementController.outsideDefendBoundary(
                        11.5D, 10.5D, CENTER, 2.0D)),
                () -> assertTrue(SquadCommandMovementController.outsideDefendBoundary(
                        11.51D, 10.5D, CENTER, 2.0D)));
    }

    @Test
    void defendApproachDestinationIsClampedWithoutChangingInsideTargets() {
        Vec3 outside = SquadCommandMovementController.constrainToDefendBoundary(
                30.5D, 70.0D, 10.5D, CENTER, 12.0D);
        Vec3 inside = SquadCommandMovementController.constrainToDefendBoundary(
                15.5D, 70.0D, 10.5D, CENTER, 12.0D);
        Vec3 invalid = SquadCommandMovementController.constrainToDefendBoundary(
                Double.NaN, 70.0D, 10.5D, CENTER, 12.0D);

        assertAll(
                () -> assertEquals(new Vec3(21.5D, 64.0D, 10.5D), outside),
                () -> assertEquals(new Vec3(15.5D, 70.0D, 10.5D), inside),
                () -> assertEquals(new Vec3(10.5D, 64.0D, 10.5D), invalid));
    }
}
