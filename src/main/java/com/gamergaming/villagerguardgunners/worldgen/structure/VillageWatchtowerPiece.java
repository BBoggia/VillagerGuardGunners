package com.gamergaming.villagerguardgunners.worldgen.structure;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModStructures;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostGroupPool;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostGroupSelection;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostGroups;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostArmoryPlacer;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostMarker;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostMarkerState;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** One persisted, rotation-safe instance of the built-in watchtower template. */
public final class VillageWatchtowerPiece extends TemplateStructurePiece {
    public static final ResourceLocation TEMPLATE_LOCATION = ResourceLocation.fromNamespaceAndPath(
            VillagerGuardGunners.MODID,
            "watchtower");

    private static final String ROTATION_TAG = "Rot";
    private static final String POST_STATE_TAG = "VggPost";
    private static final String PLACEMENT_ENABLED_TAG = "VggPlacementEnabled";
    private static final BlockPos ROTATION_PIVOT = new BlockPos(
            WatchtowerPlacement.FOOTPRINT_RADIUS,
            0,
            WatchtowerPlacement.FOOTPRINT_RADIUS);

    public VillageWatchtowerPiece(
            StructureTemplateManager templateManager,
            BlockPos templatePosition,
            Rotation rotation) {
        this(templateManager, templatePosition, rotation, 0L);
    }

    public VillageWatchtowerPiece(
            StructureTemplateManager templateManager,
            BlockPos templatePosition,
            Rotation rotation,
            long guardGroupSeed) {
        super(
                ModStructures.WATCHTOWER_PIECE.get(),
                0,
                templateManager,
                TEMPLATE_LOCATION,
                TEMPLATE_LOCATION.toString(),
                settings(rotation),
                templatePosition);
        markerState = GuardPostMarkerState.fresh(guardGroupSeed);
        placementEnabled = true;
    }

    public VillageWatchtowerPiece(
            StructureTemplateManager templateManager,
            CompoundTag tag) {
        super(
                ModStructures.WATCHTOWER_PIECE.get(),
                tag,
                templateManager,
                ignored -> settings(readRotation(tag)));
        placementEnabled = hasValidPersistedRotation(tag)
                && (!tag.contains(PLACEMENT_ENABLED_TAG)
                        || (tag.contains(PLACEMENT_ENABLED_TAG, Tag.TAG_BYTE)
                                && tag.getBoolean(PLACEMENT_ENABLED_TAG)));
        markerState = GuardPostMarkerState.load(placementEnabled
                && tag.contains(POST_STATE_TAG, Tag.TAG_COMPOUND)
                        ? tag.getCompound(POST_STATE_TAG)
                        : null);
    }

    private GuardPostMarkerState markerState;
    private final boolean placementEnabled;

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(rotation)
                .setRotationPivot(ROTATION_PIVOT)
                .setIgnoreEntities(true)
                .setKeepLiquids(false)
                .addProcessor(new BlockIgnoreProcessor(List.of(
                        Blocks.STRUCTURE_BLOCK,
                        Blocks.STRUCTURE_VOID)));
    }

    private static Rotation readRotation(CompoundTag tag) {
        try {
            return Rotation.valueOf(tag.getString(ROTATION_TAG));
        } catch (IllegalArgumentException exception) {
            return Rotation.NONE;
        }
    }

    private static boolean hasValidPersistedRotation(CompoundTag tag) {
        if (!tag.contains(ROTATION_TAG, Tag.TAG_STRING)) {
            return false;
        }
        try {
            Rotation.valueOf(tag.getString(ROTATION_TAG));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    protected void addAdditionalSaveData(
            StructurePieceSerializationContext context,
            CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString(ROTATION_TAG, placeSettings.getRotation().name());
        tag.put(POST_STATE_TAG, markerState.save());
        tag.putBoolean(PLACEMENT_ENABLED_TAG, placementEnabled);
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BoundingBox bounds,
            ChunkPos chunk,
            BlockPos origin) {
        if (!placementEnabled) {
            return;
        }
        super.postProcess(
                level,
                structureManager,
                chunkGenerator,
                random,
                bounds,
                chunk,
                origin);
    }

    @Override
    protected void handleDataMarker(
            String metadata,
            BlockPos position,
            ServerLevelAccessor level,
            RandomSource random,
            BoundingBox bounds) {
        if (!bounds.isInside(position)) {
            return;
        }
        Optional<GuardPostMarker> parsed = GuardPostMarker.parse(metadata);
        if (parsed.isEmpty()) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
            return;
        }
        GuardPostMarker marker = parsed.get();
        GuardPostMarkerState.ClaimResult claim = markerState.claim(position, marker.action());
        if (claim == GuardPostMarkerState.ClaimResult.ALREADY_CLAIMED) {
            // A completed marker revisit is a strict no-op. In particular, do
            // not replace a generated Armory that was later broken, corrupted,
            // or deliberately replaced by a player/modpack.
            return;
        }
        if (claim != GuardPostMarkerState.ClaimResult.CLAIMED) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
            return;
        }

        UUID postId = markerState.ensurePostId(
                level.getLevel().getSeed(),
                level.getLevel().dimension().location(),
                templatePosition);
        WatchtowerGenerationPolicy policy =
                ModConfigs.GUARD_GUNNERS.watchtowers.snapshot();
        if (marker.action() == GuardPostMarker.Action.ARMORY) {
            if (!policy.armoryPlacementEnabled()) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
                return;
            }
            GuardPostArmoryPlacer.place(
                    level,
                    position,
                    placeSettings.getRotation(),
                    postId,
                    marker,
                    policy.armoryStockEnabled());
            return;
        }

        // Guard markers create entities at and around their cell, so the
        // marker itself must become air before spawn-cell validation.
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
        if (!policy.guardSpawningEnabled()
                || policy.maximumGuardsPerPost() == 0) {
            return;
        }
        GuardPostGroupPool group = GuardPostGroups.get(marker.groupId());
        if (group == null) {
            return;
        }
        NaturalGuardDifficulty generationDifficulty =
                GuardPostSpawner.mapDifficulty(level.getLevel().getDifficulty());
        Optional<GuardPostGroupSelection> selected =
                group.select(generationDifficulty, markerState.selectionSeed());
        if (selected.isEmpty()) {
            return;
        }
        GuardPostGroupSelection selection = selected.get();
        markerState.recordSelection(selection);
        int guardCount = policy.limitGuardCount(selection.members().size());
        for (int memberIndex = 0; memberIndex < guardCount; memberIndex++) {
            if (GuardPostSpawner.spawnMember(
                    level,
                    bounds,
                    position,
                    placeSettings.getRotation(),
                    postId,
                    selection.groupId(),
                    selection.variantName(),
                    memberIndex,
                    selection.members().get(memberIndex),
                    generationDifficulty)) {
                markerState.recordSpawnedGuard();
            }
        }
    }

    public long guardGroupSeed() {
        return markerState.selectionSeed();
    }

    public int claimedMarkerCount() {
        return markerState.claimedMarkerCount();
    }

    public int spawnedGuardCount() {
        return markerState.spawnedGuards();
    }

    public @Nullable UUID postId() {
        return markerState.postId();
    }
}
