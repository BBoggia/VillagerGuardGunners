package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.armory.ArmoryBlock;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.armory.ArmorySquadLock;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;

/** One-shot Armory placement that defers loot work until the live server thread. */
public final class GuardPostArmoryPlacer {
    private GuardPostArmoryPlacer() {
    }

    public static PlacementResult place(
            ServerLevelAccessor level,
            BlockPos position,
            Rotation rotation,
            UUID postId,
            GuardPostMarker marker) {
        return place(level, position, rotation, postId, marker, true);
    }

    public static PlacementResult place(
            ServerLevelAccessor level,
            BlockPos position,
            Rotation rotation,
            UUID postId,
            GuardPostMarker marker,
            boolean stockGeneratedArmory) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(marker, "marker");
        if (marker.action() != GuardPostMarker.Action.ARMORY) {
            throw new IllegalArgumentException("Armory placement requires an Armory marker");
        }

        if (level.getBlockEntity(position) instanceof ArmoryBlockEntity existing) {
            return existing.isGeneratedFor(postId)
                    ? PlacementResult.ALREADY_PRESENT
                    : PlacementResult.BLOCKED_BY_EXISTING_ARMORY;
        }

        Direction facing = rotation.rotate(Direction.NORTH);
        if (!level.setBlock(
                position,
                ModBlocks.ARMORY.get().defaultBlockState()
                        .setValue(ArmoryBlock.FACING, facing),
                2)
                || !(level.getBlockEntity(position) instanceof ArmoryBlockEntity armory)) {
            return PlacementResult.PLACEMENT_FAILED;
        }

        ArmorySquadLock lock = marker.armoryAccess()
                        == GuardPostMarker.ArmoryAccess.SQUAD_LOCKED
                ? GuardPostSquads.armoryLock(postId)
                : null;
        ResourceLocation lootTableId = marker.lootTableId();
        if (lootTableId == null || !stockGeneratedArmory) {
            return armory.initializeGenerated(postId, lootTableId, lock, java.util.List.of())
                    ? PlacementResult.PLACED_EMPTY
                    : PlacementResult.PLACEMENT_FAILED;
        }
        return armory.prepareGenerated(
                        postId,
                        lootTableId,
                        GuardPostIds.armoryLootSeed(postId),
                        lock)
                ? PlacementResult.PLACED_PENDING_LOOT
                : PlacementResult.PLACEMENT_FAILED;
    }

    public enum PlacementResult {
        PLACED_PENDING_LOOT,
        PLACED_EMPTY,
        ALREADY_PRESENT,
        BLOCKED_BY_EXISTING_ARMORY,
        PLACEMENT_FAILED
    }
}
