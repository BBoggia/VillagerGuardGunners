package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.mixin.accessor.StructureTemplatePoolAccessor;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

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
                VillageBuildingPools.PLAINS_BARRACKS_ID);
        helper.assertTrue(probe != null, "The built-in plains Barracks definition was not loaded");
        List<VillageBuildingPoolEntry> probeEntries = List.of(probe);

        try {
            VillagePoolInjectionReport disabled = VillageBuildingPoolIntegration.install(
                    level.registryAccess(), level.getStructureManager(), false, probeEntries);
            helper.assertTrue(
                    !disabled.enabled()
                            && accessor.villagerGuardGunners$getRawTemplates().size()
                                    == originalRaw.size()
                            && accessor.villagerGuardGunners$getTemplates().size()
                                    == originalTemplates.size(),
                    "The disabled integration path changed a village pool");

            VillagePoolInjectionReport first = VillageBuildingPoolIntegration.install(
                    level.registryAccess(), level.getStructureManager(), true, probeEntries);
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
                    level.registryAccess(), level.getStructureManager(), true, probeEntries);
            helper.assertTrue(
                    repeated.injected() == 0
                            && repeated.alreadyPresent() == 1
                            && accessor.villagerGuardGunners$getRawTemplates().size()
                                    == originalRaw.size() + 1
                            && accessor.villagerGuardGunners$getTemplates().size()
                                    == originalTemplates.size() + 1,
                    "Repeated integration duplicated the built-in Barracks");

            var jigsaws = appended.getShuffledJigsawBlocks(
                    level.getStructureManager(),
                    BlockPos.ZERO,
                    Rotation.NONE,
                    RandomSource.create(1L));
            helper.assertTrue(
                    jigsaws.stream().anyMatch(info -> info.nbt() != null
                            && "minecraft:village/plains/streets".equals(
                                    info.nbt().getString("pool"))),
                    "The plains Barracks did not retain its plains-road connector");

            RegistryOps<JsonElement> ops = RegistryOps.create(
                    JsonOps.INSTANCE, level.registryAccess());
            JsonElement encoded = StructurePoolElement.CODEC.encodeStart(ops, appended)
                    .result()
                    .orElse(null);
            StructurePoolElement decoded = encoded == null
                    ? null
                    : StructurePoolElement.CODEC.parse(ops, encoded).result().orElse(null);
            JsonElement legacyEncoded = encoded == null ? null : encoded.deepCopy();
            if (legacyEncoded != null && legacyEncoded.isJsonObject()) {
                legacyEncoded.getAsJsonObject().remove("building_type");
            }
            StructurePoolElement legacyDecoded = legacyEncoded == null
                    ? null
                    : StructurePoolElement.CODEC.parse(ops, legacyEncoded)
                            .result().orElse(null);
            helper.assertTrue(
                    decoded instanceof VillageBuildingPoolElement decodedVillage
                            && decodedVillage.entryId().equals(
                                    probe.entryId())
                            && decodedVillage.buildingType() == VillageBuildingType.BARRACKS
                            && legacyDecoded instanceof VillageBuildingPoolElement legacyVillage
                            && legacyVillage.entryId().equals(probe.entryId())
                            && legacyVillage.buildingType() == VillageBuildingType.BARRACKS,
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
                            1),
                    new VillageBuildingPoolEntry(
                            id("missing_template"),
                            VillageBuildingType.BARRACKS,
                            VillageBuildingPools.PLAINS_HOUSES,
                            ResourceLocation.fromNamespaceAndPath("example", "missing_template"),
                            VillageBuildingPools.EMPTY_PROCESSORS,
                            StructureTemplatePool.Projection.RIGID,
                            1));
            VillagePoolInjectionReport missing = VillageBuildingPoolIntegration.install(
                    level.registryAccess(),
                    level.getStructureManager(),
                    true,
                    missingDependencies);
            helper.assertTrue(
                    missing.injected() == 0
                            && missing.missingPools() == 1
                            && missing.missingProcessorLists() == 1
                            && missing.missingTemplates() == 1
                            && missing.rejectedEntries().equals(List.of(
                                    id("missing_pool"),
                                    id("missing_processors"),
                                    id("missing_template"))),
                    "Missing pool dependencies did not fail closed independently");
        } finally {
            accessor.villagerGuardGunners$setRawTemplates(originalRaw);
            accessor.villagerGuardGunners$setTemplates(originalTemplates);
            accessor.villagerGuardGunners$setMaxSize(originalMaxSize);
        }
    }

    public static void verifyRealTemplatePlacementAndPreflight(GameTestHelper helper) {
        var level = helper.getLevel();
        var templateManager = level.getStructureManager();
        Holder.Reference<StructureProcessorList> processors = level.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolder(ResourceKey.create(
                        Registries.PROCESSOR_LIST,
                        VillageBuildingPools.EMPTY_PROCESSORS))
                .orElseThrow();

        ResourceLocation validTemplateId = id("gametest/valid_anchor_template");
        BlockPos validSource = helper.absolutePos(new BlockPos(2, 2, 8));
        BlockPos validTarget = helper.absolutePos(new BlockPos(8, 2, 8));
        captureTemplate(
                level,
                templateManager.getOrCreate(validTemplateId),
                validSource,
                "village_anchor:barracks");
        VillageBuildingPoolElement validElement = VillageBuildingPoolElement.create(
                id("gametest/valid_anchor"),
                VillageBuildingType.BARRACKS,
                validTemplateId,
                processors,
                StructureTemplatePool.Projection.RIGID);
        level.setBlock(validTarget, Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(validTarget.east(), Blocks.DIRT.defaultBlockState(), 2);
        boolean placed = validElement.place(
                templateManager,
                level,
                level.structureManager(),
                level.getChunkSource().getGenerator(),
                validTarget,
                BlockPos.ZERO,
                Rotation.NONE,
                BoundingBox.infinite(),
                RandomSource.create(11L),
                false);
        VillageBuildingIdentity validIdentity = VillageBuildingIdentity.anchored(
                level.getSeed(),
                level.dimension().location(),
                validElement.entryId(),
                VillageBuildingType.BARRACKS,
                validTarget.east());
        boolean recordPresent = VillageBuildingSavedData.get(level)
                .get(validIdentity.buildingId()) != null;
        List<StructureTemplate.StructureBlockInfo> capturedMarkers =
                validElement.getDataMarkers(
                        templateManager,
                        validTarget,
                        Rotation.NONE,
                        false);
        helper.assertTrue(
                placed
                        && level.getBlockState(validTarget).is(Blocks.STONE)
                        && level.getBlockState(validTarget.east()).isAir()
                        && recordPresent,
                "A real valid template did not place and publish its anchor: placed="
                        + placed
                        + ", first=" + level.getBlockState(validTarget)
                        + ", marker=" + level.getBlockState(validTarget.east())
                        + ", record=" + recordPresent
                        + ", capturedMarkers=" + capturedMarkers);

        ResourceLocation invalidTemplateId = id("gametest/mismatched_anchor_template");
        BlockPos invalidSource = helper.absolutePos(new BlockPos(2, 2, 11));
        BlockPos invalidTarget = helper.absolutePos(new BlockPos(8, 2, 11));
        captureTemplate(
                level,
                templateManager.getOrCreate(invalidTemplateId),
                invalidSource,
                "village_anchor:armory");
        VillageBuildingPoolElement invalidElement = VillageBuildingPoolElement.create(
                id("gametest/mismatched_anchor"),
                VillageBuildingType.BARRACKS,
                invalidTemplateId,
                processors,
                StructureTemplatePool.Projection.RIGID);
        level.setBlock(invalidTarget, Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(invalidTarget.east(), Blocks.DIRT.defaultBlockState(), 2);
        boolean invalidPlaced = invalidElement.place(
                templateManager,
                level,
                level.structureManager(),
                level.getChunkSource().getGenerator(),
                invalidTarget,
                BlockPos.ZERO,
                Rotation.NONE,
                BoundingBox.infinite(),
                RandomSource.create(12L),
                false);

        VillageBuildingPoolEntry invalidEntry = new VillageBuildingPoolEntry(
                invalidElement.entryId(),
                VillageBuildingType.BARRACKS,
                VillageBuildingPools.PLAINS_HOUSES,
                invalidTemplateId,
                VillageBuildingPools.EMPTY_PROCESSORS,
                StructureTemplatePool.Projection.RIGID,
                1);
        VillagePoolInjectionReport preflight = VillageBuildingPoolIntegration.install(
                level.registryAccess(),
                templateManager,
                true,
                List.of(invalidEntry));
        helper.assertTrue(
                !invalidPlaced
                        && level.getBlockState(invalidTarget).is(Blocks.DIRT)
                        && level.getBlockState(invalidTarget.east()).is(Blocks.DIRT)
                        && preflight.injected() == 0
                        && preflight.invalidTemplates() == 1
                        && preflight.rejectedEntries().equals(List.of(invalidEntry.entryId())),
                "A mismatched template wrote blocks or passed startup preflight");
        helper.succeed();
    }

    public static void verifyBuiltInTemplateMatrix(GameTestHelper helper) {
        var level = helper.getLevel();
        var templateManager = level.getStructureManager();
        Holder.Reference<StructureProcessorList> processors = level.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolder(ResourceKey.create(
                        Registries.PROCESSOR_LIST,
                        VillageBuildingPools.EMPTY_PROCESSORS))
                .orElseThrow();
        Map<String, ResourceLocation> targetPools = new LinkedHashMap<>();
        targetPools.put("plains", VillageBuildingPools.PLAINS_HOUSES);
        targetPools.put("desert", VillageBuildingPools.DESERT_HOUSES);
        targetPools.put("savanna", VillageBuildingPools.SAVANNA_HOUSES);
        targetPools.put("snowy", VillageBuildingPools.SNOWY_HOUSES);
        targetPools.put("taiga", VillageBuildingPools.TAIGA_HOUSES);
        Map<String, String> streetPools = Map.of(
                "plains", "minecraft:village/plains/streets",
                "desert", "minecraft:village/desert/streets",
                "savanna", "minecraft:village/savanna/streets",
                "snowy", "minecraft:village/snowy/streets",
                "taiga", "minecraft:village/taiga/streets");

        int inspected = 0;
        for (Map.Entry<String, ResourceLocation> biome : targetPools.entrySet()) {
            for (VillageBuildingType type : VillageBuildingType.values()) {
                ResourceLocation entryId = id(type.serializedName() + "/" + biome.getKey());
                ResourceLocation templateId = id(
                        "village/" + biome.getKey() + "/" + type.serializedName());
                VillageBuildingPoolEntry entry = VillageBuildingDefinitions.get(entryId);
                helper.assertTrue(
                        entry != null
                                && entry.buildingType() == type
                                && entry.targetPool().equals(biome.getValue())
                                && entry.template().equals(templateId)
                                && entry.processors().equals(VillageBuildingPools.EMPTY_PROCESSORS)
                                && entry.projection() == StructureTemplatePool.Projection.RIGID
                                && entry.weight() == 1,
                        "Built-in definition was missing or malformed: " + entryId);
                StructureTemplate template = templateManager.get(templateId).orElse(null);
                helper.assertTrue(template != null, "Built-in template was missing: " + templateId);
                Vec3i size = template.getSize();
                helper.assertTrue(
                        size.getX() > 0 && size.getX() <= 16
                                && size.getY() > 0 && size.getY() <= 16
                                && size.getZ() > 0 && size.getZ() <= 16,
                        "Built-in template exceeded its reviewed bounds: " + templateId);

                VillageBuildingPoolElement element = VillageBuildingPoolElement.create(
                        entryId,
                        type,
                        templateId,
                        processors,
                        StructureTemplatePool.Projection.RIGID);
                List<StructureTemplate.StructureBlockInfo> markers = element.getDataMarkers(
                        templateManager,
                        BlockPos.ZERO,
                        Rotation.NONE,
                        false);
                helper.assertTrue(
                        markers.size() == 1
                                && ("village_anchor:" + type.serializedName()).equals(
                                        VillageBuildingAnchorMarker.metadata(markers.get(0).nbt())),
                        "Built-in template did not contain exactly one matching anchor: "
                                + templateId);
                List<StructureTemplate.StructureBlockInfo> jigsaws =
                        element.getShuffledJigsawBlocks(
                                templateManager,
                                BlockPos.ZERO,
                                Rotation.NONE,
                                RandomSource.create(31L + inspected));
                helper.assertTrue(
                        jigsaws.size() == 1
                                && jigsaws.get(0).nbt() != null
                                && "minecraft:building_entrance".equals(
                                        jigsaws.get(0).nbt().getString("name"))
                                && streetPools.get(biome.getKey()).equals(
                                        jigsaws.get(0).nbt().getString("pool")),
                        "Built-in template did not contain its biome street connector: "
                                + templateId);
                inspected++;
            }
        }
        helper.assertTrue(inspected == 25, "The complete 25-template matrix was not inspected");

        Map<StructureTemplatePool, PoolState> originalPools = new LinkedHashMap<>();
        for (ResourceLocation poolId : targetPools.values()) {
            StructureTemplatePool pool = level.registryAccess()
                    .registryOrThrow(Registries.TEMPLATE_POOL)
                    .get(poolId);
            helper.assertTrue(pool != null, "A supported vanilla houses pool was missing: " + poolId);
            StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;
            originalPools.put(pool, new PoolState(
                    new ArrayList<>(accessor.villagerGuardGunners$getRawTemplates()),
                    new ObjectArrayList<>(accessor.villagerGuardGunners$getTemplates()),
                    accessor.villagerGuardGunners$getMaxSize()));
        }
        try {
            List<VillageBuildingPoolEntry> entries = List.copyOf(
                    VillageBuildingDefinitions.snapshot().values());
            helper.assertTrue(entries.size() == 25,
                    "The built-in definition snapshot did not contain exactly 25 entries");
            VillagePoolInjectionReport installed = VillageBuildingPoolIntegration.install(
                    level.registryAccess(), templateManager, true, entries);
            VillagePoolInjectionReport repeated = VillageBuildingPoolIntegration.install(
                    level.registryAccess(), templateManager, true, entries);
            helper.assertTrue(
                    installed.injected() == 25
                            && !installed.hasWarnings()
                            && repeated.injected() == 0
                            && repeated.alreadyPresent() == 25,
                    "The complete built-in matrix was not installed exactly once");
            for (Map.Entry<StructureTemplatePool, PoolState> original : originalPools.entrySet()) {
                StructureTemplatePoolAccessor accessor =
                        (StructureTemplatePoolAccessor) original.getKey();
                helper.assertTrue(
                        accessor.villagerGuardGunners$getRawTemplates().size()
                                        == original.getValue().raw().size() + 5
                                && accessor.villagerGuardGunners$getTemplates().size()
                                        == original.getValue().flattened().size() + 5,
                        "A supported village pool did not receive exactly five weight-1 entries");
            }
        } finally {
            for (Map.Entry<StructureTemplatePool, PoolState> original : originalPools.entrySet()) {
                StructureTemplatePoolAccessor accessor =
                        (StructureTemplatePoolAccessor) original.getKey();
                accessor.villagerGuardGunners$setRawTemplates(original.getValue().raw());
                accessor.villagerGuardGunners$setTemplates(original.getValue().flattened());
                accessor.villagerGuardGunners$setMaxSize(original.getValue().maximumSize());
            }
        }

        int placementIndex = 0;
        for (VillageBuildingType type : VillageBuildingType.values()) {
            ResourceLocation entryId = id(type.serializedName() + "/plains");
            ResourceLocation templateId = id("village/plains/" + type.serializedName());
            VillageBuildingPoolElement element = VillageBuildingPoolElement.create(
                    entryId,
                    type,
                    templateId,
                    processors,
                    StructureTemplatePool.Projection.RIGID);
            Rotation rotation = Rotation.values()[placementIndex % Rotation.values().length];
            BlockPos target = helper.absolutePos(new BlockPos(
                    24 + placementIndex * 20,
                    2,
                    24));
            List<StructureTemplate.StructureBlockInfo> markers = element.getDataMarkers(
                    templateManager,
                    target,
                    rotation,
                    false);
            helper.assertTrue(markers.size() == 1,
                    "The placement candidate lost its anchor: " + templateId);
            BlockPos anchor = StructureTemplate.calculateRelativePosition(
                    new StructurePlaceSettings().setRotation(rotation),
                    markers.get(0).pos()).offset(target);
            boolean placed = element.place(
                    templateManager,
                    level,
                    level.structureManager(),
                    level.getChunkSource().getGenerator(),
                    target,
                    BlockPos.ZERO,
                    rotation,
                    BoundingBox.infinite(),
                    RandomSource.create(71L + placementIndex),
                    false);
            VillageBuildingIdentity identity = VillageBuildingIdentity.anchored(
                    level.getSeed(),
                    level.dimension().location(),
                    entryId,
                    type,
                    anchor);
            boolean anchorConsumed = level.getBlockState(anchor).isAir();
            boolean recordPresent = VillageBuildingSavedData.get(level)
                    .get(identity.buildingId()) != null;
            helper.assertTrue(
                    placed && anchorConsumed && recordPresent,
                    "Built-in template failed real rotated placement: " + templateId
                            + ", rotation=" + rotation
                            + ", placed=" + placed
                            + ", anchor=" + anchor
                            + ", anchorState=" + level.getBlockState(anchor)
                            + ", record=" + recordPresent);
            placementIndex++;
        }
        helper.succeed();
    }

    public static void verifyWorkerThreadAnchorHandoff(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 2, 4));
        ResourceLocation entryId = id("gametest/worker_handoff");
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                VillageBuildingAnchorLifecycle.record(
                        level,
                        entryId,
                        VillageBuildingType.SENTRY_POST,
                        anchor);
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "vgg-anchor-gametest-worker");
        worker.start();
        try {
            worker.join(2_000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting anchor worker", exception);
        }
        helper.assertTrue(!worker.isAlive(), "Anchor handoff worker did not finish");

        VillageBuildingIdentity expected = VillageBuildingIdentity.anchored(
                level.getSeed(),
                level.dimension().location(),
                entryId,
                VillageBuildingType.SENTRY_POST,
                anchor);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(failure.get() == null,
                    "Worker-thread anchor handoff threw " + failure.get());
            helper.assertTrue(
                    VillageBuildingSavedData.get(level).get(expected.buildingId()) != null,
                    "Worker-thread anchor handoff did not reach SavedData");
            helper.succeed();
        });
    }

    private static void captureTemplate(
            net.minecraft.server.level.ServerLevel level,
            StructureTemplate template,
            BlockPos source,
            String metadata) {
        level.setBlock(source, Blocks.STONE.defaultBlockState(), 2);
        BlockPos marker = source.east();
        level.setBlock(marker, Blocks.STRUCTURE_BLOCK.defaultBlockState(), 2);
        if (!(level.getBlockEntity(marker) instanceof StructureBlockEntity structureBlock)) {
            throw new IllegalStateException("Structure marker block entity was not created");
        }
        structureBlock.setMode(StructureMode.DATA);
        structureBlock.setMetaData(metadata);
        structureBlock.setChanged();
        template.fillFromWorld(
                level,
                source,
                new Vec3i(2, 1, 1),
                false,
                Blocks.STRUCTURE_VOID);
        level.setBlock(source, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(marker, Blocks.AIR.defaultBlockState(), 2);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("villagerguardgunners", path);
    }

    private record PoolState(
            List<Pair<StructurePoolElement, Integer>> raw,
            ObjectArrayList<StructurePoolElement> flattened,
            int maximumSize) {
    }

    private VillageBuildingPoolGameTestAccess() {
    }
}
