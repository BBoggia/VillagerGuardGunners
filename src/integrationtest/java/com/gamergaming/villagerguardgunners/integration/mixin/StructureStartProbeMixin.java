package com.gamergaming.villagerguardgunners.integration.mixin;

import com.gamergaming.villagerguardgunners.integration.ProbeScope;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = StructureStart.class, remap = false)
public abstract class StructureStartProbeMixin {
    @Redirect(method = "placeInChunk", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/structure/StructurePiece;postProcess(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/core/BlockPos;)V"))
    private void scopedPiece(StructurePiece piece, WorldGenLevel level, StructureManager manager,
                             ChunkGenerator generator, RandomSource random, BoundingBox bounds,
                             ChunkPos chunk, BlockPos pivot) {
        StructureStart previous = ProbeScope.START.get();
        ProbeScope.START.set((StructureStart) (Object) this);
        try {
            piece.postProcess(level, manager, generator, random, bounds, chunk, pivot);
        } finally {
            if (previous == null) ProbeScope.START.remove();
            else ProbeScope.START.set(previous);
        }
    }
}
