package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class ArmoryResupplyMovementControllerTest {
    private static final BlockPos ARMORY = new BlockPos(10, 64, 10);

    @Test
    void approachPointsAreCardinalUniqueAndNearestFirst() {
        List<BlockPos> fromNorth = ArmoryResupplyMovementController.approachPositions(
                ARMORY, 10.5D, 64.0D, 2.5D);
        List<BlockPos> centered = ArmoryResupplyMovementController.approachPositions(
                ARMORY, 10.5D, 64.0D, 10.5D);

        assertAll(
                () -> assertEquals(4, fromNorth.size()),
                () -> assertEquals(4, fromNorth.stream().distinct().count()),
                () -> assertEquals(new BlockPos(10, 64, 9), fromNorth.get(0)),
                () -> assertEquals(new BlockPos(11, 64, 10), fromNorth.get(1)),
                () -> assertEquals(new BlockPos(9, 64, 10), fromNorth.get(2)),
                () -> assertEquals(new BlockPos(10, 64, 11), fromNorth.get(3)),
                () -> assertEquals(
                        List.of(
                                new BlockPos(10, 64, 9),
                                new BlockPos(11, 64, 10),
                                new BlockPos(10, 64, 11),
                                new BlockPos(9, 64, 10)),
                        centered),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> ArmoryResupplyMovementController.approachPositions(
                                ARMORY, Double.NaN, 64.0D, 10.5D)));
    }

    @Test
    void travelBoundaryIsInclusiveAndRejectsLongTrips() {
        assertAll(
                () -> assertTrue(ArmoryResupplyMovementController.withinTravelDistance(
                        new BlockPos(-54, 64, 10), ARMORY)),
                () -> assertFalse(ArmoryResupplyMovementController.withinTravelDistance(
                        new BlockPos(-55, 64, 10), ARMORY)));
    }

    @Test
    void progressRequiresFiniteHalfBlockDisplacement() {
        assertAll(
                () -> assertFalse(ArmoryResupplyMovementController.madeProgress(
                        0.0D, 0.0D, 0.0D,
                        0.49D, 0.0D, 0.0D,
                        ArmoryResupplyLimits.MIN_PROGRESS_DISTANCE_SQUARED)),
                () -> assertTrue(ArmoryResupplyMovementController.madeProgress(
                        0.0D, 0.0D, 0.0D,
                        0.5D, 0.0D, 0.0D,
                        ArmoryResupplyLimits.MIN_PROGRESS_DISTANCE_SQUARED)),
                () -> assertFalse(ArmoryResupplyMovementController.madeProgress(
                        0.0D, 0.0D, 0.0D,
                        Double.POSITIVE_INFINITY, 0.0D, 0.0D,
                        ArmoryResupplyLimits.MIN_PROGRESS_DISTANCE_SQUARED)),
                () -> assertFalse(ArmoryResupplyMovementController.madeProgress(
                        0.0D, 0.0D, 0.0D,
                        1.0D, 0.0D, 0.0D,
                        Double.NaN)));
    }
}
