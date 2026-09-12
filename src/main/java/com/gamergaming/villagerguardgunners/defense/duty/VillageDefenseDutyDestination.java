package com.gamergaming.villagerguardgunners.defense.duty;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded-chunk-only physical validation shared by staffing and movement. */
final class VillageDefenseDutyDestination {
    private VillageDefenseDutyDestination() {
    }

    static Status evaluate(ServerLevel level, BlockPos destination) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(destination, "destination");
        BlockPos headPosition = destination.above();
        BlockPos floorPosition = destination.below();
        if (!level.isInWorldBounds(destination)
                || !level.isInWorldBounds(headPosition)
                || !level.isInWorldBounds(floorPosition)
                || !level.getWorldBorder().isWithinBounds(destination)) {
            return Status.OUTSIDE_WORLD;
        }
        if (!level.hasChunk(
                SectionPos.blockToSectionCoord(destination.getX()),
                SectionPos.blockToSectionCoord(destination.getZ()))) {
            return Status.UNLOADED;
        }
        BlockState feet = level.getBlockState(destination);
        BlockState head = level.getBlockState(headPosition);
        BlockState floor = level.getBlockState(floorPosition);
        return feet.getCollisionShape(level, destination).isEmpty()
                        && head.getCollisionShape(level, headPosition).isEmpty()
                        && feet.getFluidState().isEmpty()
                        && head.getFluidState().isEmpty()
                        && floor.isFaceSturdy(level, floorPosition, Direction.UP)
                ? Status.WALKABLE
                : Status.BLOCKED;
    }

    enum Status {
        WALKABLE,
        UNLOADED,
        BLOCKED,
        OUTSIDE_WORLD;

        boolean usableForStaffing() {
            return this == WALKABLE || this == UNLOADED;
        }

        boolean invalidWhileLoaded() {
            return this == BLOCKED || this == OUTSIDE_WORLD;
        }
    }
}
