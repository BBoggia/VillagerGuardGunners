package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.mixin.accessor.StructureTemplatePoolAccessor;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Runtime-only assertions that require Minecraft registries and applied mixins. */
public final class VillageBuildingPoolGameTestAccess {

    public static void verifyStepTwoIntegration(GameTestHelper helper) {
        var level = helper.getLevel();
        var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        StructureTemplatePool pool = pools.get(VillageBuildingPools.PLAINS_HOUSES);
        helper.assertTrue(pool != null, "The vanilla plains houses pool was missing");
        StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;

        List<Pair<StructurePoolElement, Integer>> originalRaw = new ArrayList<>(
                accessor.villagerGuardGunners$getRawTemplates());
        ObjectArrayList<StructurePoolElement> originalTemplates = new ObjectArrayList<>(
                accessor.villagerGuardGunners$getTemplates());
        int originalMaxSize = accessor.villagerGuardGunners$getMaxSize();
        VillageBuildingPoolEntry probe = VillageBuildingDefinitions.get(
                VillageBuildingPools.PLAINS_INTEGRATION_PROBE_ID);
        helper.assertTrue(probe != null, "The built-in Step 3 definition was not loaded");
        List<VillageBuildingPoolEntry> probeEntries = List.of(probe);

        try {
            VillagePoolInjectionReport disabled = VillageBuildingPoolIntegration.install(
                    level.registryAccess(), false, probeEntries);
            helper.assertTrue(
                    !disabled.enabled()
                            && accessor.villagerGuardGunners$getRawTemplates().size()
                                    == originalRaw.size()
                            && accessor.villagerGuardGunners$getTemplates().size()
                                    == originalTemplates.size(),
                    "The disabled integration path changed a village pool");

            VillagePoolInjectionReport first = VillageBuildingPoolIntegration.install(
                    level.registryAccess(), true, probeEntries);
            helper.assertTrue(
                    first.injected() == 1
                            && first.alreadyPresent() == 0
                            && accessor.villagerGuardGunners$getRawTemplates().size()
                                    == originalRaw.size() + 1
                            && accessor.villagerGuardGunners$getTemplates().size()
                                    == originalTemplates.size() + 1,
                    "The first additive pool integration did not append exactly one entry");

            List<Pair<StructurePoolElement, Integer>> updatedRaw =
                    accessor.villagerGuardGunners$getRawTemplates();
            for (int index = 0; index < originalRaw.size(); index++) {
                helper.assertTrue(
                        updatedRaw.get(index).getFirst() == originalRaw.get(index).getFirst()
                                && updatedRaw.get(index).getSecond().equals(
                                        originalRaw.get(index).getSecond()),
                        "An existing raw pool entry or weight changed at index " + index);
            }
            for (int index = 0; index < originalTemplates.size(); index++) {
                helper.assertTrue(
                        accessor.villagerGuardGunners$getTemplates().get(index)
                                == originalTemplates.get(index),
                        "An existing flattened pool entry changed at index " + index);
            }

            StructurePoolElement appended = updatedRaw.get(updatedRaw.size() - 1).getFirst();
            helper.assertTrue(
                    appended instanceof VillageBuildingPoolElement villageElement
                            && villageElement.entryId().equals(
                                    probe.entryId())
                            && villageElement.buildingType() == VillageBuildingType.BARRACKS
                            && updatedRaw.get(updatedRaw.size() - 1).getSecond() == 1,
                    "The appended element did not retain its stable ID and weight");

            VillagePoolInjectionReport repeated = VillageBuildingPoolIntegration.install(
                    level.registryAccess(), true, probeEntries);
            helper.assertTrue(
                    repeated.injected() == 0
                            && repeated.alreadyPresent() == 1
                            && accessor.villagerGuardGunners$getRawTemplates().size()
                                    == originalRaw.size() + 1
                            && accessor.villagerGuardGunners$getTemplates().size()
                                    == originalTemplates.size() + 1,
                    "Repeated integration duplicated the probe");

            var jigsaws = appended.getShuffledJigsawBlocks(
                    level.getStructureManager(),
                    BlockPos.ZERO,
                    Rotation.NONE,
                    RandomSource.create(1L));
            helper.assertTrue(
                    jigsaws.stream().anyMatch(info -> info.nbt() != null
                            && "minecraft:village/plains/streets".equals(
                                    info.nbt().getString("pool"))),
                    "The inert probe template did not retain its plains-road connector");

            RegistryOps<JsonElement> ops = RegistryOps.create(
                    JsonOps.INSTANCE, level.registryAccess());
            JsonElement encoded = StructurePoolElement.CODEC.encodeStart(ops, appended)
                    .result()
                    .orElse(null);
            StructurePoolElement decoded = encoded == null
                    ? null
                    : StructurePoolElement.CODEC.parse(ops, encoded).result().orElse(null);
            helper.assertTrue(
                    decoded instanceof VillageBuildingPoolElement decodedVillage
                            && decodedVillage.entryId().equals(
                                    probe.entryId())
                            && decodedVillage.buildingType() == VillageBuildingType.BARRACKS,
                    "The registered village-building pool element did not round-trip");

            VillageBuildingPoolElement anchorElement =
                    (VillageBuildingPoolElement) appended;
            BlockPos anchorPosition = helper.absolutePos(new BlockPos(1, 2, 1));
            CompoundTag anchorTag = new CompoundTag();
            anchorTag.putString("metadata", "village_anchor:barracks");
            StructureTemplate.StructureBlockInfo anchorInfo =
                    new StructureTemplate.StructureBlockInfo(
                            anchorPosition,
                            Blocks.STRUCTURE_BLOCK.defaultBlockState(),
                            anchorTag);
            level.setBlock(anchorPosition, Blocks.STONE.defaultBlockState(), 2);
            anchorElement.handleDataMarker(
                    level,
                    anchorInfo,
                    BlockPos.ZERO,
                    Rotation.NONE,
                    RandomSource.create(2L),
                    BoundingBox.infinite());
            VillageBuildingIdentity anchorIdentity = VillageBuildingIdentity.anchored(
                    level.getSeed(),
                    level.dimension().location(),
                    probe.entryId(),
                    VillageBuildingType.BARRACKS,
                    anchorPosition);
            VillageBuildingSavedData buildingData = VillageBuildingSavedData.get(level);
            int recordedBuildings = buildingData.size();
            helper.assertTrue(
                    level.getBlockState(anchorPosition).isAir()
                            && buildingData.get(anchorIdentity.buildingId()) != null
                            && buildingData.get(anchorIdentity.buildingId())
                                    .hasClaimed(VillageBuildingAction.ANCHOR),
                    "A valid building anchor was not consumed and persisted");

            anchorElement.handleDataMarker(
                    level,
                    anchorInfo,
                    BlockPos.ZERO,
                    Rotation.NONE,
                    RandomSource.create(3L),
                    BoundingBox.infinite());
            helper.assertTrue(
                    buildingData.size() == recordedBuildings,
                    "Repeated anchor handling duplicated persistent building state");

            List<VillageBuildingPoolEntry> missingDependencies = List.of(
                    new VillageBuildingPoolEntry(
                            id("missing_pool"),
                            VillageBuildingType.BARRACKS,
                            ResourceLocation.fromNamespaceAndPath("example", "missing_pool"),
                            probe.template(),
                            VillageBuildingPools.EMPTY_PROCESSORS,
                            StructureTemplatePool.Projection.RIGID,
                            1),
                    new VillageBuildingPoolEntry(
                            id("missing_processors"),
                            VillageBuildingType.BARRACKS,
                            VillageBuildingPools.PLAINS_HOUSES,
                            probe.template(),
                            ResourceLocation.fromNamespaceAndPath("example", "missing_processors"),
                            StructureTemplatePool.Projection.RIGID,
                            1));
            VillagePoolInjectionReport missing = VillageBuildingPoolIntegration.install(
                    level.registryAccess(), true, missingDependencies);
            helper.assertTrue(
                    missing.injected() == 0
                            && missing.missingPools() == 1
                            && missing.missingProcessorLists() == 1,
                    "Missing pool dependencies did not fail closed independently");
        } finally {
            accessor.villagerGuardGunners$setRawTemplates(originalRaw);
            accessor.villagerGuardGunners$setTemplates(originalTemplates);
            accessor.villagerGuardGunners$setMaxSize(originalMaxSize);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("villagerguardgunners", path);
    }

    private VillageBuildingPoolGameTestAccess() {
    }
}
