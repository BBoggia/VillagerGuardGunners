package com.gamergaming.villagerguardgunners.integration.mixin;

import com.gamergaming.villagerguardgunners.integration.ProbeScope;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PoolElementStructurePiece.class, remap = false)
public abstract class PoolPieceProbeMixin {
    @Redirect(method = "place", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/structure/pools/StructurePoolElement;place(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/util/RandomSource;Z)Z"))
    private boolean scopedTemplate(StructurePoolElement element, StructureTemplateManager templates,
                                    WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                    BlockPos position, BlockPos pivot, Rotation rotation, BoundingBox bounds,
                                    RandomSource random, boolean keepJigsaws) {
        ResourceLocation previous = ProbeScope.TEMPLATE.get();
        ResourceLocation id = element instanceof SinglePoolElement
                ? ((SinglePoolElementAccess) element).integrationTemplate().left().orElse(null) : null;
        if (id == null) ProbeScope.TEMPLATE.remove();
        else ProbeScope.TEMPLATE.set(id);
        try {
            return element.place(templates, level, structures, generator, position, pivot,
                    rotation, bounds, random, keepJigsaws);
        } finally {
            if (previous == null) ProbeScope.TEMPLATE.remove();
            else ProbeScope.TEMPLATE.set(previous);
        }
    }
}
