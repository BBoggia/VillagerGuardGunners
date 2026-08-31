package com.gamergaming.villagerguardgunners.worldgen.structure;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModStructures;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
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
    private static final BlockPos ROTATION_PIVOT = new BlockPos(
            WatchtowerPlacement.FOOTPRINT_RADIUS,
            0,
            WatchtowerPlacement.FOOTPRINT_RADIUS);

    public VillageWatchtowerPiece(
            StructureTemplateManager templateManager,
            BlockPos templatePosition,
            Rotation rotation) {
        super(
                ModStructures.WATCHTOWER_PIECE.get(),
                0,
                templateManager,
                TEMPLATE_LOCATION,
                TEMPLATE_LOCATION.toString(),
                settings(rotation),
                templatePosition);
    }

    public VillageWatchtowerPiece(
            StructureTemplateManager templateManager,
            CompoundTag tag) {
        super(
                ModStructures.WATCHTOWER_PIECE.get(),
                tag,
                templateManager,
                ignored -> settings(readRotation(tag)));
    }

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

    @Override
    protected void addAdditionalSaveData(
            StructurePieceSerializationContext context,
            CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString(ROTATION_TAG, placeSettings.getRotation().name());
    }

    @Override
    protected void handleDataMarker(
            String metadata,
            BlockPos position,
            ServerLevelAccessor level,
            RandomSource random,
            BoundingBox bounds) {
        // Step 11 intentionally has no functional data markers. Later steps
        // add bounded marker handling here without scanning generated chunks.
    }
}
