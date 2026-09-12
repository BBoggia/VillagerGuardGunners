package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolElement;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingAnchorMarker;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingTerrainSafety;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reject before free space, junctions, pieces or world blocks are changed. */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class VillageBuildingTerrainMixin {
    @Shadow @Final private ChunkGenerator chunkGenerator;
    @Unique private StructurePoolElement vgg$candidate;
    @Unique private BlockPos vgg$origin;
    @Unique private Rotation vgg$rotation;
    @Unique private BoundingBox vgg$bounds;
    @Unique private StructureTemplateManager vgg$templates;
    @Unique private final Map<Long, VillageBuildingTerrainSafety.Column> vgg$columns = new HashMap<>();

    // The first call measures a rotated template at zero. The second is the
    // matched candidate, before Minecraft adjusts its vertical placement.
    @Redirect(method = "tryPlacingChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructurePoolElement;getBoundingBox(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;)Lnet/minecraft/world/level/levelgen/structure/BoundingBox;", ordinal = 1))
    private BoundingBox vgg$rememberCandidate(StructurePoolElement element,
            StructureTemplateManager templates, BlockPos origin, Rotation rotation) {
        BoundingBox bounds = element.getBoundingBox(templates, origin, rotation);
        vgg$candidate = element;
        vgg$origin = origin;
        vgg$rotation = rotation;
        vgg$bounds = bounds;
        vgg$templates = templates;
        return bounds;
    }

    @Redirect(method = "tryPlacingChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"))
    private boolean vgg$rejectUnsafeTerrain(VoxelShape available, VoxelShape candidate, BooleanOp operation,
            PoolElementStructurePiece parent, MutableObject<VoxelShape> free, int depth,
            boolean expansion, LevelHeightAccessor height, RandomState randomState) {
        if (Shapes.joinIsNotEmpty(available, candidate, operation)) return true;
        if (!(vgg$candidate instanceof VillageBuildingPoolElement building) || !building.terrainSafety()) return false;
        // The collision shape is inset 0.25 blocks; flooring restores the
        // candidate's minimum Y, including Minecraft's terrain adjustment.
        int deltaY = (int) Math.floor(candidate.bounds().minY) - vgg$bounds.minY();
        var markers = building.getDataMarkers(vgg$templates, vgg$origin.offset(0, deltaY, 0), vgg$rotation, true).stream()
                .filter(marker -> marker.nbt() != null
                        && VillageBuildingAnchorMarker.isAnchorMetadata(marker.nbt().getString("metadata")))
                .toList();
        if (markers.size() != 1) return true;
        int floor = markers.get(0).pos().getY();
        return !VillageBuildingTerrainSafety.allows(vgg$bounds.minX(), vgg$bounds.minZ(),
                vgg$bounds.maxX(), vgg$bounds.maxZ(), floor, (x, z) -> {
                    long key = (long) x << 32 | z & 0xffffffffL;
                    if (vgg$columns.size() >= 4096) vgg$columns.clear();
                    return vgg$columns.computeIfAbsent(key, ignored -> new VillageBuildingTerrainSafety.Column(
                            chunkGenerator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, randomState),
                            chunkGenerator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState)));
                });
    }
}
