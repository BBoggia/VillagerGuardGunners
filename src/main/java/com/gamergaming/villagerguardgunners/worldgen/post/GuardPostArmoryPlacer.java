package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.armory.ArmoryBlock;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.armory.ArmorySquadLock;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Bounded one-shot placement and loot evaluation for one Armory marker. */
public final class GuardPostArmoryPlacer {
    public static final int MAXIMUM_LOOT_STACKS = 64;
    private GuardPostArmoryPlacer() {
    }

    public static PlacementResult place(
            ServerLevelAccessor level,
            BlockPos position,
            Rotation rotation,
            UUID postId,
            GuardPostMarker marker) {
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
        List<ItemStack> loot = evaluateLoot(
                level.getLevel(),
                position,
                postId,
                lootTableId);
        boolean initialized = loot != null
                && armory.initializeGenerated(postId, lootTableId, lock, loot);
        if (!initialized) {
            // A malformed/oversized/non-ammunition datapack table must not
            // leave partial stock or become retryable. Persist a completed,
            // empty setup using the same declared table identity.
            armory.initializeGenerated(postId, lootTableId, lock, List.of());
            return PlacementResult.PLACED_EMPTY;
        }
        return loot.isEmpty()
                ? PlacementResult.PLACED_EMPTY
                : PlacementResult.PLACED_WITH_LOOT;
    }

    private static List<ItemStack> evaluateLoot(
            ServerLevel level,
            BlockPos position,
            UUID postId,
            ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return List.of();
        }
        try {
            LootTable table = level.getServer().getLootData().getLootTable(lootTableId);
            LootParams parameters = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(position))
                    .create(LootContextParamSets.CHEST);
            List<ItemStack> generated = table.getRandomItems(
                    parameters,
                    GuardPostIds.armoryLootSeed(postId));
            if (generated.size() > MAXIMUM_LOOT_STACKS) {
                return null;
            }
            return List.copyOf(generated);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public enum PlacementResult {
        PLACED_WITH_LOOT,
        PLACED_EMPTY,
        ALREADY_PRESENT,
        BLOCKED_BY_EXISTING_ARMORY,
        PLACEMENT_FAILED
    }
}
