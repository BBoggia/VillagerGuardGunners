package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.init.ModStructures;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.Rotation;

/**
 * Identifiable single-template element used for additive, idempotent village
 * integration and bounded anchor handling.
 */
public final class VillageBuildingPoolElement extends SinglePoolElement {
    private static final Codec<VillageBuildingPoolElement> RAW_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("entry_id")
                            .forGetter(VillageBuildingPoolElement::entryId),
                    VillageBuildingType.CODEC.optionalFieldOf("building_type")
                            .forGetter(element -> java.util.Optional.of(
                                    element.buildingType())),
                    templateCodec(),
                    processorsCodec(),
                    projectionCodec(),
                    Codec.doubleRange(0.0, 1.0).optionalFieldOf("selection_chance", 1.0)
                            .forGetter(VillageBuildingPoolElement::selectionChance),
                    Codec.BOOL.optionalFieldOf("terrain_safety", false)
                            .forGetter(VillageBuildingPoolElement::terrainSafety))
                    .apply(instance, VillageBuildingPoolElement::new));
    public static final Codec<VillageBuildingPoolElement> CODEC = RAW_CODEC.flatXmap(
            element -> element.legacyTypeFallback
                    && !element.entryId.equals(
                            VillageBuildingPools.PLAINS_INTEGRATION_PROBE_ID)
                    ? DataResult.error(() -> "Only the legacy plains integration probe may omit building_type")
                    : element.terrainSafety && element.getProjection() != StructureTemplatePool.Projection.RIGID
                            ? DataResult.error(() -> "terrain_safety requires rigid projection")
                            : DataResult.success(element),
            DataResult::success);

    private final ResourceLocation entryId;
    private final VillageBuildingType buildingType;
    private final boolean legacyTypeFallback;
    private final double selectionChance;
    private final boolean terrainSafety;

    private VillageBuildingPoolElement(
            ResourceLocation entryId,
            java.util.Optional<VillageBuildingType> buildingType,
            Either<ResourceLocation, StructureTemplate> template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection,
            double selectionChance,
            boolean terrainSafety) {
        this(
                entryId,
                buildingType.orElse(VillageBuildingType.BARRACKS),
                template,
                processors,
                projection,
                selectionChance,
                terrainSafety,
                buildingType.isEmpty());
    }

    private VillageBuildingPoolElement(
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            Either<ResourceLocation, StructureTemplate> template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection,
            double selectionChance,
            boolean terrainSafety,
            boolean legacyTypeFallback) {
        super(template, processors, projection);
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.buildingType = Objects.requireNonNull(buildingType, "buildingType");
        this.legacyTypeFallback = legacyTypeFallback;
        if (!Double.isFinite(selectionChance) || selectionChance < 0.0 || selectionChance > 1.0) {
            throw new IllegalArgumentException("Selection chance must be finite and in [0, 1]");
        }
        this.selectionChance = selectionChance;
        this.terrainSafety = terrainSafety;
    }

    public static VillageBuildingPoolElement create(
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            ResourceLocation template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection) {
        return create(entryId, buildingType, template, processors, projection, 1.0);
    }

    public static VillageBuildingPoolElement create(
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            ResourceLocation template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection,
            double selectionChance) {
        return create(entryId, buildingType, template, processors, projection, selectionChance, false);
    }

    public static VillageBuildingPoolElement create(
            ResourceLocation entryId, VillageBuildingType buildingType, ResourceLocation template,
            Holder<StructureProcessorList> processors, StructureTemplatePool.Projection projection,
            double selectionChance, boolean terrainSafety) {
        if (terrainSafety && projection != StructureTemplatePool.Projection.RIGID) {
            throw new IllegalArgumentException("terrain_safety requires rigid projection");
        }
        return new VillageBuildingPoolElement(
                entryId,
                Objects.requireNonNull(buildingType, "buildingType"),
                Either.left(Objects.requireNonNull(template, "template")),
                Objects.requireNonNull(processors, "processors"),
                Objects.requireNonNull(projection, "projection"),
                selectionChance,
                terrainSafety,
                false);
    }

    public boolean terrainSafety() { return terrainSafety; }

    public double selectionChance() { return selectionChance; }

    public ResourceLocation entryId() {
        return entryId;
    }

    public VillageBuildingType buildingType() {
        return buildingType;
    }

    @Override
    protected StructurePlaceSettings getSettings(
            Rotation rotation,
            BoundingBox bounds,
            boolean keepJigsaws) {
        // SinglePoolElement normally treats jigsaw templates as already having
        // their final shapes. These authored buildings contain fences, iron
        // bars, stairs, doors, and other blocks whose state depends on their
        // generated neighbors, so let StructureTemplate run Minecraft's
        // standard bounded shape reconciliation after placement.
        return super.getSettings(rotation, bounds, keepJigsaws)
                .setKnownShape(false);
    }

    @Override
    public boolean place(
            StructureTemplateManager templateManager,
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            BlockPos position,
            BlockPos pivot,
            Rotation rotation,
            BoundingBox bounds,
            RandomSource random,
            boolean keepJigsaws) {
        if (!VillageBuildingAnchorMarker.validateTemplateMarkers(
                getDataMarkers(templateManager, position, rotation, false),
                buildingType)) {
            return false;
        }
        boolean placed = super.place(
                templateManager,
                level,
                structureManager,
                chunkGenerator,
                position,
                pivot,
                rotation,
                bounds,
                random,
                keepJigsaws);
        if (!placed) {
            return false;
        }

        StructurePlaceSettings markerSettings = getSettings(rotation, bounds, keepJigsaws);
        markerSettings.getProcessors().removeIf(
                processor -> processor == BlockIgnoreProcessor.STRUCTURE_BLOCK);
        for (StructureTemplate.StructureBlockInfo marker : StructureTemplate.processBlockInfos(
                level,
                position,
                pivot,
                markerSettings,
                getDataMarkers(templateManager, position, rotation, false))) {
            if (VillageBuildingAnchorMarker.isAnchorMetadata(
                    VillageBuildingAnchorMarker.metadata(marker.nbt()))) {
                handleDataMarker(level, marker, position, rotation, random, bounds);
            }
        }
        return true;
    }

    @Override
    public void handleDataMarker(
            LevelAccessor level,
            StructureTemplate.StructureBlockInfo markerInfo,
            BlockPos origin,
            Rotation rotation,
            RandomSource random,
            BoundingBox bounds) {
        BlockPos markerPosition = markerInfo.pos();
        if (!bounds.isInside(markerPosition)) {
            return;
        }
        CompoundTag markerTag = markerInfo.nbt();
        String metadata = VillageBuildingAnchorMarker.metadata(markerTag);
        if (!VillageBuildingAnchorMarker.isAnchorMetadata(metadata)) {
            return;
        }
        level.setBlock(markerPosition, Blocks.AIR.defaultBlockState(), 2);
        VillageBuildingAnchorMarker marker = VillageBuildingAnchorMarker.parse(metadata)
                .filter(candidate -> candidate.buildingType() == buildingType)
                .orElse(null);
        if (marker == null || !(level instanceof WorldGenLevel worldGenLevel)) {
            return;
        }
        VillageBuildingAnchorLifecycle.recordAndPopulate(
                worldGenLevel,
                entryId,
                buildingType,
                markerPosition,
                rotation);
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return ModStructures.VILLAGE_BUILDING_POOL_ELEMENT.get();
    }

    @Override
    public String toString() {
        return "VillageBuildingPoolElement[" + entryId + ", " + buildingType
                + ", " + super.toString() + "]";
    }
}
