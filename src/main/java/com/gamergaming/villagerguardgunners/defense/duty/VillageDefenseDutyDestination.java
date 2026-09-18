package com.gamergaming.villagerguardgunners.defense.duty;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

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
        return com.gamergaming.villagerguardgunners.ai.navigation.GuardGroundTraversal.safeStandingPosition(level, destination)
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
