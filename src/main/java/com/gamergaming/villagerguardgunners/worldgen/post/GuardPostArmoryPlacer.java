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
import net.minecraft.world.level.block.Blocks;

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
                2)) {
            return PlacementResult.PLACEMENT_FAILED;
        }
        if (!(level.getBlockEntity(position) instanceof ArmoryBlockEntity armory)) {
            rollbackNewArmory(level, position);
            return PlacementResult.PLACEMENT_FAILED;
        }

        ArmorySquadLock lock = marker.armoryAccess()
                        == GuardPostMarker.ArmoryAccess.SQUAD_LOCKED
                ? GuardPostSquads.armoryLock(postId)
                : null;
        ResourceLocation lootTableId = marker.lootTableId();
        if (lootTableId == null || !stockGeneratedArmory) {
            if (armory.initializeGenerated(postId, lootTableId, lock, java.util.List.of())) {
                return PlacementResult.PLACED_EMPTY;
            }
            rollbackNewArmory(level, position, armory);
            return PlacementResult.PLACEMENT_FAILED;
        }
        if (armory.prepareGenerated(
                postId,
                lootTableId,
                GuardPostIds.armoryLootSeed(postId),
                lock)) {
            return PlacementResult.PLACED_PENDING_LOOT;
        }
        rollbackNewArmory(level, position, armory);
        return PlacementResult.PLACEMENT_FAILED;
    }

    private static void rollbackNewArmory(
            ServerLevelAccessor level,
            BlockPos position,
            ArmoryBlockEntity armory) {
        if (level.getBlockEntity(position) == armory) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void rollbackNewArmory(ServerLevelAccessor level, BlockPos position) {
        if (level.getBlockState(position).is(ModBlocks.ARMORY.get())) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    public enum PlacementResult {
        PLACED_PENDING_LOOT,
        PLACED_EMPTY,
        ALREADY_PRESENT,
        BLOCKED_BY_EXISTING_ARMORY,
        PLACEMENT_FAILED
    }
}
