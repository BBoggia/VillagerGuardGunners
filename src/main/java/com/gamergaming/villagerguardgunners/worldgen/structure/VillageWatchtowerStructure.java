package com.gamergaming.villagerguardgunners.worldgen.structure;

import com.gamergaming.villagerguardgunners.init.ModStructures;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * Places one compact watchtower after bounded dry-ground and slope checks.
 */
public final class VillageWatchtowerStructure extends Structure {
    public static final Codec<VillageWatchtowerStructure> CODEC =
            simpleCodec(VillageWatchtowerStructure::new);

    public VillageWatchtowerStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int originX = chunk.getMiddleBlockX() - WatchtowerPlacement.FOOTPRINT_RADIUS;
        int originZ = chunk.getMiddleBlockZ() - WatchtowerPlacement.FOOTPRINT_RADIUS;
        OptionalInt groundY = WatchtowerPlacement.findGroundY(context, originX, originZ);
        if (groundY.isEmpty()) {
            return Optional.empty();
        }

        BlockPos templateOrigin = new BlockPos(originX, groundY.getAsInt(), originZ);
        BlockPos biomeCheckPosition = templateOrigin.offset(
                WatchtowerPlacement.FOOTPRINT_RADIUS,
                0,
                WatchtowerPlacement.FOOTPRINT_RADIUS);
        Rotation rotation = Rotation.getRandom(context.random());
        return Optional.of(new GenerationStub(
                biomeCheckPosition,
                pieces -> pieces.addPiece(new VillageWatchtowerPiece(
                        context.structureTemplateManager(),
                        templateOrigin,
                        rotation))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.WATCHTOWER.get();
    }
}
