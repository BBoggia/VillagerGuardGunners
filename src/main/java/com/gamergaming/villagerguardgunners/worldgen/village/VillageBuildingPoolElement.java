package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.init.ModStructures;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Identifiable single-template element used for additive, idempotent village
 * integration. Marker behavior is intentionally absent during Step 3.
 */
public final class VillageBuildingPoolElement extends SinglePoolElement {
    public static final Codec<VillageBuildingPoolElement> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("entry_id")
                            .forGetter(VillageBuildingPoolElement::entryId),
                    VillageBuildingType.CODEC.fieldOf("building_type")
                            .forGetter(VillageBuildingPoolElement::buildingType),
                    templateCodec(),
                    processorsCodec(),
                    projectionCodec())
                    .apply(instance, VillageBuildingPoolElement::new));

    private final ResourceLocation entryId;
    private final VillageBuildingType buildingType;

    private VillageBuildingPoolElement(
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            Either<ResourceLocation, StructureTemplate> template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection) {
        super(template, processors, projection);
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.buildingType = Objects.requireNonNull(buildingType, "buildingType");
    }

    public static VillageBuildingPoolElement create(
            ResourceLocation entryId,
            VillageBuildingType buildingType,
            ResourceLocation template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection) {
        return new VillageBuildingPoolElement(
                entryId,
                Objects.requireNonNull(buildingType, "buildingType"),
                Either.left(Objects.requireNonNull(template, "template")),
                Objects.requireNonNull(processors, "processors"),
                Objects.requireNonNull(projection, "projection"));
    }

    public ResourceLocation entryId() {
        return entryId;
    }

    public VillageBuildingType buildingType() {
        return buildingType;
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
