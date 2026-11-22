package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlacement;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureStart.class)
public abstract class PopulationStructureStartMixin {
    @Unique private CompoundTag villagerGuardGunners$populationBasis;
    @Redirect(method = "placeInChunk", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/structure/StructurePiece;postProcess(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/core/BlockPos;)V"))
    private void populationScope(StructurePiece piece, WorldGenLevel level, StructureManager manager,
                                 ChunkGenerator generator, RandomSource random, BoundingBox bounds,
                                 ChunkPos chunk, BlockPos pivot) {
        CompoundTag state;
        synchronized (this) {
            if (villagerGuardGunners$populationBasis == null) villagerGuardGunners$populationBasis =
                    VillagePopulationPlacement.initialize(level, (StructureStart) (Object) this);
            state = villagerGuardGunners$populationBasis;
        }
        try (var scope = new VillagePopulationPlacement(level, (StructureStart) (Object) this, piece, state)) {
            piece.postProcess(level, manager, generator, random, bounds, chunk, pivot);
        }
    }
    @Inject(method = "createTag", at = @At("RETURN"))
    private void savePopulation(StructurePieceSerializationContext context, ChunkPos chunk, CallbackInfoReturnable<CompoundTag> callback) {
        synchronized (this) {
            // A start saved before its first placement is new generation, not a legacy village.
            CompoundTag state = villagerGuardGunners$populationBasis;
            if (state != null) callback.getReturnValue().put(VillagePopulationPlacement.START_TAG, state.copy());
            else callback.getReturnValue().putBoolean("villagerguardgunners:population_new", true);
        }
    }
    @Inject(method = "loadStaticStart", at = @At("RETURN"))
    private static void loadPopulation(StructurePieceSerializationContext context, CompoundTag tag, long seed,
                                       CallbackInfoReturnable<StructureStart> callback) {
        if (callback.getReturnValue() == null || callback.getReturnValue() == StructureStart.INVALID_START) return;
        var mixin = (PopulationStructureStartMixin) (Object) callback.getReturnValue();
        if (tag.contains(VillagePopulationPlacement.START_TAG)) {
            mixin.villagerGuardGunners$populationBasis = tag.getCompound(VillagePopulationPlacement.START_TAG).copy();
        } else if (!tag.getBoolean("villagerguardgunners:population_new")) {
            mixin.villagerGuardGunners$populationBasis = VillagePopulationPlacement.legacy();
        }
    }
}
