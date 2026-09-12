package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlock;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutySavedData;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyAssignment;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingRuntime;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.mixin.accessor.StructureTemplatePoolAccessor;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Runtime-only assertions that require Minecraft registries and applied mixins. */
public final class VillageBuildingPoolGameTestAccess {

    public static void verifyPersistedActivationRecovery(GameTestHelper helper) {
        var level = helper.getLevel();
        var settings = ModConfigs.GUARD_GUNNERS.villageBuildings;
        boolean originalEnabled = settings.enabled.get();
        boolean originalSpawnGarrisons = settings.spawnGarrisons.get();
        int originalMaximum = settings.maximumGuardsPerBuilding.get();
        BlockPos anchor = helper.absolutePos(new BlockPos(20008, 2, 20008));
        var area = new com.gamergaming.villagerguardgunners.defense.duty.LoadedDutyTestArea(level, anchor);
        Guard[] accepted = new Guard[1];
        List<Integer> spawnTicks = new ArrayList<>();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> rejectFirstSpawns = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof Guard guard
                    && guard.blockPosition().distManhattan(
                            anchor) <= 4) {
                spawnTicks.add(level.getServer().getTickCount());
                if (spawnTicks.size() <= 2) {
                    event.setCanceled(true);
                } else {
                    accepted[0] = guard;
                    // This fixture checks activation and assignment. Keep the production duty
                    // goal, but prevent unrelated wandering/combat from leaving its small floor.
                    guard.goalSelector.removeAllGoals(goal -> !(goal instanceof
                            com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal));
                    guard.targetSelector.removeAllGoals(goal -> true);
                    guard.setInvulnerable(true);
                }
            }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(rejectFirstSpawns);
        Runnable restore = () -> {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(rejectFirstSpawns);
            if (accepted[0] != null) accepted[0].discard();
            area.close();
            settings.enabled.accept(originalEnabled);
            settings.spawnGarrisons.accept(originalSpawnGarrisons);
            settings.maximumGuardsPerBuilding.accept(originalMaximum);
        };
        boolean deferredRestore = false;
        try {
            settings.enabled.accept(true);
            settings.spawnGarrisons.accept(true);
            settings.maximumGuardsPerBuilding.accept(4);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlock(anchor.offset(x, -1, z),
                            Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(anchor.offset(x, 0, z),
                            Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(anchor.offset(x, 1, z),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
            VillageBuildingIdentity identity = VillageBuildingIdentity.anchored(
                    level.getSeed(),
                    level.dimension().location(),
                    ResourceLocation.fromNamespaceAndPath(
                            "villagerguardgunners", "gametest/persisted_recovery"),
                    VillageBuildingType.SENTRY_POST,
                    anchor);
            VillageBuildingPendingActivation pending =
                    VillageBuildingPendingActivation.forBuilding(
                            identity,
                            Rotation.CLOCKWISE_90,
                            NaturalGuardDifficulty.NORMAL);
            VillageBuildingSavedData data = VillageBuildingSavedData.get(level);
            helper.assertTrue(
                    data.claimAnchor(identity, pending)
                            == VillageBuildingSavedData.ClaimResult.CLAIMED,
                    "The recovery fixture could not persist its unfinished action");

            VillageBuildingActivationRecovery.resumeLoadedChunk(
                    level, new ChunkPos(anchor));
            // Recovery is deliberately scheduled and may take a bounded retry
            // when chunk/block publication finishes after the load callback.
            // Poll the strict final state instead of assuming one fixed task
            // ordering relative to the GameTest sequence tick.
            helper.succeedWhen(() -> {
                var entity = level.getEntity(
                        com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIds
                                .guardId(identity.buildingId(), 0));
                VillageBuildingRecord recovered = data.get(identity.buildingId());
                boolean claimed = recovered != null
                        && recovered.hasClaimed(VillageBuildingAction.GARRISON);
                boolean pendingCleared = recovered != null
                        && recovered.pendingActivation().isEmpty();
                var dutyLayout = VillageDefenseDutySavedData.get(level)
                        .layout(identity.buildingId());
                VillageDefenseDutyAssignment dutyAssignment = entity instanceof Guard guard
                        ? VillageDefenseStaffingRuntime.assignment(guard)
                        : null;
                var defenseNetwork = com.gamergaming.villagerguardgunners.defense.network
                        .VillageDefenseNetworkSavedData.get(level)
                        .networkForBuilding(identity.buildingId());
                helper.assertTrue(
                        entity instanceof Guard
                                && claimed
                                && pendingCleared
                                && data.pendingActivationCount() == 0
                                && dutyLayout != null
                                && dutyLayout.rotation() == Rotation.CLOCKWISE_90
                                && dutyLayout.slots().size() == 1
                                && dutyLayout.slots().get(0).position().equals(anchor)
                                && dutyLayout.slots().get(0).facing() == Direction.EAST
                                && dutyAssignment != null
                                && defenseNetwork != null
                                && dutyAssignment.networkId().equals(
                                        defenseNetwork.networkId())
                                && dutyAssignment.guardId().equals(entity.getUUID()),
                        "A persisted loaded-chunk activation did not resume exactly once; "
                                + "entity="
                                + (entity == null
                                        ? "missing"
                                        : entity.getType().toString())
                                + ", actorState=" + (entity instanceof Guard g ? "alive=" + g.isAlive() + ",noAi=" + g.isNoAi() + ",life=" + com.gamergaming.villagerguardgunners.api.GuardLifeAccess.get(g).stage() + ",pos=" + g.blockPosition() : "missing")
                                + ", staffing=" + VillageDefenseStaffingRuntime.diagnostics(level)
                                + ", slot=" + List.of(level.getBlockState(anchor.below()), level.getBlockState(anchor), level.getBlockState(anchor.above()))
                                + ", enabled=" + com.gamergaming.villagerguardgunners.defense.VillageDefenseRuntimePolicy.automaticDutiesEnabled()
                                + ", held=" + (entity instanceof Guard g ? g.getMainHandItem() : "none")
                                + ", claimed="
                                + claimed
                                + ", pendingCleared="
                                + pendingCleared
                                + ", pendingCount="
                                + data.pendingActivationCount()
                                + ", dutyLayout="
                                + dutyLayout
                                + ", dutyAssignment="
                                + dutyAssignment
                                + ", defenseNetwork="
                                + (defenseNetwork == null
                                        ? "missing"
                                        : defenseNetwork.networkId()));
                helper.assertTrue(spawnTicks.size() >= 3
                                && spawnTicks.get(1) - spawnTicks.get(0) >= 1
                                && spawnTicks.get(2) - spawnTicks.get(1) >= 2,
                        "Activation retries did not wait for their server tick deadlines: " + spawnTicks);
                VillageBuildingActivationRecovery.resumeLoadedChunk(
                        level, new ChunkPos(anchor));
                helper.assertTrue(
                        level.getEntity(entity.getUUID()) == entity,
                        "A completed recovery was scheduled or duplicated again");
                restore.run();
            });
            // Also restore global configuration before this test's timeout if
            // recovery never reaches the success condition.
            helper.runAfterDelay(75, restore);
            deferredRestore = true;
        } finally {
            if (!deferredRestore) {
                restore.run();
            }
        }
    }

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
                JsonObject legacyPayload = codecPayload(legacyEncoded);
                legacyPayload.addProperty(
                        "entry_id",
                        VillageBuildingPools.PLAINS_INTEGRATION_PROBE_ID.toString());
                legacyPayload.remove("building_type");
            }
            var legacyResult = legacyEncoded == null
                    ? com.mojang.serialization.DataResult.<StructurePoolElement>error(
                            () -> "No encoded element")
                    : StructurePoolElement.CODEC.parse(ops, legacyEncoded);
            StructurePoolElement legacyDecoded = legacyResult.result().orElse(null);
            helper.assertTrue(
                    decoded instanceof VillageBuildingPoolElement decodedVillage
                            && decodedVillage.entryId().equals(
                                    probe.entryId())
                            && decodedVillage.buildingType() == VillageBuildingType.BARRACKS,
                    "The explicit registered village-building element did not round-trip: "
                            + decoded);
            helper.assertTrue(
                    legacyDecoded instanceof VillageBuildingPoolElement legacyVillage
                            && legacyVillage.entryId().equals(
                                    VillageBuildingPools.PLAINS_INTEGRATION_PROBE_ID)
                            && legacyVillage.buildingType() == VillageBuildingType.BARRACKS,
                    "The exact legacy probe did not decode without building_type: "
                            + "decoded=" + legacyDecoded + ", result=" + legacyResult + ", "
                            + "json=" + legacyEncoded + ", "
                            + legacyResult.error()
                                    .map(com.mojang.serialization.DataResult.PartialResult::message)
                                    .orElse("no codec error"));
            for (VillageBuildingType type : VillageBuildingType.values()) {
                JsonElement invalidMissingType = encoded.deepCopy();
                JsonObject invalidPayload = codecPayload(invalidMissingType);
                invalidPayload.addProperty(
                        "entry_id",
                        ResourceLocation.fromNamespaceAndPath(
                                VillagerGuardGunners.MODID,
                                "codec_missing_type/" + type.serializedName()).toString());
                invalidPayload.remove("building_type");
                helper.assertTrue(
                        StructurePoolElement.CODEC.parse(ops, invalidMissingType)
                                .result().isEmpty(),
                        "A non-legacy " + type.serializedName()
                                + " element decoded without building_type");
            }

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

    private static com.google.gson.JsonObject codecPayload(JsonElement encoded) {
        com.google.gson.JsonObject root = encoded.getAsJsonObject();
        if (root.has("value") && root.get("value").isJsonObject()) {
            return root.getAsJsonObject("value");
        }
        if (root.has("element") && root.get("element").isJsonObject()) {
            return root.getAsJsonObject("element");
        }
        return root;
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
        Map<VillageBuildingType, Integer> expectedWeights = Map.of(
                VillageBuildingType.BARRACKS, 1,
                VillageBuildingType.ARMORY, 1,
                VillageBuildingType.SENTRY_POST, 2,
                VillageBuildingType.WATCHTOWER, 1,
                VillageBuildingType.TRAINING_YARD, 1);

        int inspected = 0;
        int attachmentChecks = 0;
        for (Map.Entry<String, ResourceLocation> biome : targetPools.entrySet()) {
            int biomeWeight = 0;
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
                                && entry.weight() == expectedWeights.get(type),
                        "Built-in definition was missing or malformed: " + entryId);
                biomeWeight += entry.weight();
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
                for (Rotation rotation : Rotation.values()) {
                    List<StructureTemplate.StructureBlockInfo> jigsaws =
                            element.getShuffledJigsawBlocks(
                                    templateManager,
                                    BlockPos.ZERO,
                                    rotation,
                                    RandomSource.create(31L + attachmentChecks));
                    helper.assertTrue(
                            jigsaws.size() == 1
                                    && jigsaws.get(0).nbt() != null
                                    && "minecraft:building_entrance".equals(
                                            jigsaws.get(0).nbt().getString("name"))
                                    && streetPools.get(biome.getKey()).equals(
                                            jigsaws.get(0).nbt().getString("pool")),
                            "Built-in template did not contain its biome street connector: "
                                    + templateId + "/" + rotation);
                    StructureTemplate.StructureBlockInfo buildingConnector = jigsaws.get(0);
                    Direction buildingFront = JigsawBlock.getFrontFacing(
                            buildingConnector.state());
                    Direction buildingTop = JigsawBlock.getTopFacing(
                            buildingConnector.state());
                    CompoundTag streetTag = new CompoundTag();
                    streetTag.putString("target", buildingConnector.nbt().getString("name"));
                    streetTag.putString("name", "minecraft:street");
                    streetTag.putString("joint", "aligned");
                    StructureTemplate.StructureBlockInfo streetConnector =
                            new StructureTemplate.StructureBlockInfo(
                                    buildingConnector.pos().relative(buildingFront),
                                    Blocks.JIGSAW.defaultBlockState().setValue(
                                            JigsawBlock.ORIENTATION,
                                            FrontAndTop.fromFrontAndTop(
                                                    buildingFront.getOpposite(),
                                                    buildingTop)),
                                    streetTag);
                    helper.assertTrue(
                            JigsawBlock.canAttach(streetConnector, buildingConnector),
                            "Built-in entrance failed Minecraft's jigsaw attachment predicate: "
                                    + templateId + "/" + rotation);
                    streetTag.putString("target", "minecraft:not_the_building_entrance");
                    helper.assertTrue(
                            !JigsawBlock.canAttach(streetConnector, buildingConnector),
                            "Built-in entrance ignored a mismatched road target: "
                                    + templateId + "/" + rotation);
                    attachmentChecks++;
                }
                inspected++;
            }
            helper.assertTrue(
                    biomeWeight == 6,
                    "Built-in definitions did not retain total relative weight six for "
                            + biome.getKey());
        }
        helper.assertTrue(inspected == 25, "The complete 25-template matrix was not inspected");
        helper.assertTrue(attachmentChecks == 100,
                "The complete biome/building/rotation attachment matrix was not inspected");

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
                                        == original.getValue().flattened().size() + 6,
                        "A supported village pool did not receive five balanced entries "
                                + "with total relative weight six");
                int originalWeight = original.getValue().raw().stream()
                        .mapToInt(Pair::getSecond)
                        .sum();
                helper.assertTrue(
                        originalWeight > 6,
                        "Village Defense Buildings outweighed the original vanilla pool");
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
        int recordsBeforePlacementMatrix = VillageBuildingSavedData.get(level).size();
        for (VillageBuildingType type : VillageBuildingType.values()) {
            ResourceLocation entryId = id(type.serializedName() + "/plains");
            ResourceLocation templateId = id("village/plains/" + type.serializedName());
            StructureTemplate template = templateManager.get(templateId).orElse(null);
            helper.assertTrue(template != null,
                    "The built-in Village Defense Building template was unavailable: "
                            + templateId);
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
            List<StructureTemplate.StructureBlockInfo> connectors =
                    element.getShuffledJigsawBlocks(
                            templateManager,
                            target,
                            rotation,
                            RandomSource.create(61L + placementIndex));
            helper.assertTrue(markers.size() == 1,
                    "The placement candidate lost its anchor: " + templateId);
            helper.assertTrue(connectors.size() == 1,
                    "The placement candidate lost its entrance: " + templateId);
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
            BlockPos entrance = connectors.get(0).pos();
            boolean entranceClear = level.getBlockState(entrance).isAir()
                    && level.getBlockState(entrance.above())
                            .getCollisionShape(level, entrance.above()).isEmpty();
            int stableLanterns = 0;
            Vec3i placedSize = template.getSize();
            StructurePlaceSettings placedSettings =
                    new StructurePlaceSettings().setRotation(rotation);
            for (int x = 0; x < placedSize.getX(); x++) {
                for (int y = 0; y < placedSize.getY(); y++) {
                    for (int z = 0; z < placedSize.getZ(); z++) {
                        BlockPos position = StructureTemplate.calculateRelativePosition(
                                placedSettings, new BlockPos(x, y, z)).offset(target);
                        if (level.getBlockState(position).is(Blocks.LANTERN)) {
                            helper.assertTrue(
                                    level.getBlockState(position).canSurvive(level, position),
                                    "Built-in template placed an unsupported lantern: "
                                            + templateId + "/" + rotation);
                            stableLanterns++;
                        }
                    }
                }
            }
            helper.assertTrue(
                    placed && anchorConsumed && recordPresent && entranceClear
                            && stableLanterns == 1,
                    "Built-in template failed real rotated placement: " + templateId
                            + ", rotation=" + rotation
                            + ", placed=" + placed
                            + ", anchor=" + anchor
                            + ", anchorState=" + level.getBlockState(anchor)
                            + ", entranceClear=" + entranceClear
                            + ", stableLanterns=" + stableLanterns
                            + ", record=" + recordPresent);
            placementIndex++;
        }
        helper.assertTrue(
                VillageBuildingSavedData.get(level).size()
                        == recordsBeforePlacementMatrix + VillageBuildingType.values().length,
                "Multiple VGG building types did not retain independent persistent records");
        helper.succeed();
    }

    public static void verifyBarracksVariants(GameTestHelper helper) {
        var level = helper.getLevel();
        var templateManager = level.getStructureManager();
        Holder.Reference<StructureProcessorList> processors = level.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolder(ResourceKey.create(
                        Registries.PROCESSOR_LIST,
                        VillageBuildingPools.EMPTY_PROCESSORS))
                .orElseThrow();
        List<String> biomes = List.of("plains", "desert", "savanna", "snowy", "taiga");
        int placedCount = 0;
        for (int biomeIndex = 0; biomeIndex < biomes.size(); biomeIndex++) {
            String biome = biomes.get(biomeIndex);
            ResourceLocation entryId = id("barracks/" + biome);
            ResourceLocation templateId = id("village/" + biome + "/barracks");
            StructureTemplate template = templateManager.get(templateId).orElse(null);
            helper.assertTrue(
                    template != null && template.getSize().equals(new Vec3i(13, 10, 11)),
                    "Barracks lost its reviewed lodge bounds: " + templateId);
            VillageBuildingPoolElement element = VillageBuildingPoolElement.create(
                    entryId,
                    VillageBuildingType.BARRACKS,
                    templateId,
                    processors,
                    StructureTemplatePool.Projection.RIGID);
            List<StructureTemplate.StructureBlockInfo> jigsaws =
                    element.getShuffledJigsawBlocks(
                            templateManager,
                            BlockPos.ZERO,
                            Rotation.NONE,
                            RandomSource.create(1_401L + biomeIndex));
            helper.assertTrue(
                    jigsaws.size() == 1
                            && jigsaws.get(0).nbt() != null
                            && ("minecraft:village/" + biome + "/streets").equals(
                                    jigsaws.get(0).nbt().getString("pool")),
                    "Barracks lost its biome road connector: " + templateId);

            for (Rotation rotation : Rotation.values()) {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
                BlockPos target = helper.absolutePos(new BlockPos(
                        8 + biomeIndex * 24,
                        2,
                        8 + rotation.ordinal() * 24));
                List<StructureTemplate.StructureBlockInfo> markers = element.getDataMarkers(
                        templateManager,
                        target,
                        rotation,
                        false);
                List<StructureTemplate.StructureBlockInfo> connectors =
                        element.getShuffledJigsawBlocks(
                                templateManager,
                                target,
                                rotation,
                                RandomSource.create(1_501L + placedCount));
                helper.assertTrue(
                        markers.size() == 1 && connectors.size() == 1,
                        "Barracks lost its anchor or entrance: "
                                + templateId + "/" + rotation);
                BlockPos anchor = StructureTemplate.calculateRelativePosition(
                        settings, markers.get(0).pos()).offset(target);
                boolean placed = element.place(
                        templateManager,
                        level,
                        level.structureManager(),
                        level.getChunkSource().getGenerator(),
                        target,
                        BlockPos.ZERO,
                        rotation,
                        BoundingBox.infinite(),
                        RandomSource.create(1_601L + placedCount),
                        false);
                helper.assertTrue(placed,
                        "Barracks failed rotated placement: " + templateId + "/" + rotation);
                assertNeighborSensitiveStatesSettled(
                        helper, template, settings, target,
                        "Barracks " + templateId + "/" + rotation);

                BoundingBox guardBounds = new BoundingBox(
                        anchor.getX() - 1,
                        anchor.getY() - 1,
                        anchor.getZ() - 1,
                        anchor.getX() + 1,
                        anchor.getY() + 1,
                        anchor.getZ() + 1);
                for (int memberIndex = 0; memberIndex < 4; memberIndex++) {
                    BlockPos position = GuardPostSpawner.spawnPosition(
                            anchor, rotation, memberIndex);
                    helper.assertTrue(
                            GuardPostSpawner.isSafeSpawnCell(level, guardBounds, position),
                            "Barracks lost safe muster cell " + memberIndex
                                    + ": " + templateId + "/" + rotation);
                }

                BlockPos lowerDoor = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(6, 1, 2)).offset(target);
                BlockPos upperDoor = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(6, 2, 2)).offset(target);
                helper.assertTrue(
                        level.getBlockState(lowerDoor).getBlock() instanceof DoorBlock
                                && level.getBlockState(lowerDoor).getValue(DoorBlock.HALF)
                                        == DoubleBlockHalf.LOWER
                                && level.getBlockState(lowerDoor).getValue(DoorBlock.FACING)
                                        == rotation.rotate(Direction.NORTH)
                                && level.getBlockState(upperDoor).getBlock() instanceof DoorBlock
                                && level.getBlockState(upperDoor).getValue(DoorBlock.HALF)
                                        == DoubleBlockHalf.UPPER,
                        "Barracks entrance door did not rotate as one valid pair: "
                                + templateId + "/" + rotation);

                BlockPos ascendingRoof = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(2, 5, 5)).offset(target);
                BlockPos descendingRoof = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(10, 5, 5)).offset(target);
                helper.assertTrue(
                        level.getBlockState(ascendingRoof).getBlock() instanceof StairBlock
                                && level.getBlockState(ascendingRoof).getValue(StairBlock.FACING)
                                        == rotation.rotate(Direction.EAST)
                                && level.getBlockState(descendingRoof).getBlock()
                                        instanceof StairBlock
                                && level.getBlockState(descendingRoof).getValue(StairBlock.FACING)
                                        == rotation.rotate(Direction.WEST),
                        "Barracks gable stairs did not retain their inward pitch: "
                                + templateId + "/" + rotation);

                if (!biome.equals("desert")) {
                    BlockPos crossbeam = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(6, 4, 2)).offset(target);
                    BlockPos ridgeBeam = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(6, 8, 5)).offset(target);
                    helper.assertTrue(
                            level.getBlockState(crossbeam).hasProperty(RotatedPillarBlock.AXIS)
                                    && level.getBlockState(crossbeam).getValue(
                                            RotatedPillarBlock.AXIS)
                                            == rotation.rotate(Direction.EAST).getAxis()
                                    && level.getBlockState(ridgeBeam).hasProperty(
                                            RotatedPillarBlock.AXIS)
                                    && level.getBlockState(ridgeBeam).getValue(
                                            RotatedPillarBlock.AXIS)
                                            == rotation.rotate(Direction.SOUTH).getAxis(),
                            "Barracks beams did not rotate with their supported spans: "
                                    + templateId + "/" + rotation);
                }

                BlockPos entrance = connectors.get(0).pos();
                helper.assertTrue(
                        level.getBlockState(entrance).isAir()
                                && level.getBlockState(entrance.above())
                                        .getCollisionShape(level, entrance.above()).isEmpty(),
                        "Barracks road connector did not finalize as a clear entrance: "
                                + templateId + "/" + rotation);
                Guard pathProbe = GuardEntityType.GUARD.get().create(level);
                helper.assertTrue(pathProbe != null,
                        "Could not create a Barracks navigation probe");
                pathProbe.moveTo(
                        anchor.getX() + 0.5D,
                        anchor.getY(),
                        anchor.getZ() + 0.5D,
                        0.0F,
                        0.0F);
                pathProbe.setOnGround(true);
                Path exitPath = pathProbe.getNavigation().createPath(entrance, 0);
                helper.assertTrue(
                        exitPath != null && exitPath.canReach() && exitPath.getNodeCount() > 1,
                        "Guard Villagers navigation could not leave the Barracks: "
                                + templateId + "/" + rotation);
                for (BlockPos localWingDestination : List.of(
                        new BlockPos(1, 1, 6), new BlockPos(11, 1, 6))) {
                    BlockPos wingDestination = StructureTemplate.calculateRelativePosition(
                            settings, localWingDestination).offset(target);
                    Path wingPath = pathProbe.getNavigation().createPath(wingDestination, 0);
                    helper.assertTrue(
                            wingPath != null && wingPath.canReach()
                                    && wingPath.getNodeCount() > 1,
                            "Guard Villagers navigation could not reach a sleeping wing: "
                                    + templateId + "/" + rotation
                                    + ", destination=" + localWingDestination);
                }
                pathProbe.discard();

                int carpets = 0;
                int craftingTables = 0;
                int lanterns = 0;
                Vec3i size = template.getSize();
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            BlockPos position = StructureTemplate.calculateRelativePosition(
                                    settings, new BlockPos(x, y, z)).offset(target);
                            if (level.getBlockState(position).is(Blocks.GRAY_CARPET)) {
                                carpets++;
                            } else if (level.getBlockState(position).is(Blocks.CRAFTING_TABLE)) {
                                craftingTables++;
                            } else if (level.getBlockState(position).is(Blocks.LANTERN)) {
                                lanterns++;
                                helper.assertTrue(
                                        level.getBlockState(position).canSurvive(level, position),
                                        "Barracks placed an unsupported lantern: "
                                                + templateId + "/" + rotation);
                            }
                        }
                    }
                }
                helper.assertTrue(
                        carpets == 8 && craftingTables == 1 && lanterns == 1,
                        "Barracks interior cues changed unexpectedly: "
                                + templateId + "/" + rotation
                                + ", carpets=" + carpets
                                + ", craftingTables=" + craftingTables
                                + ", lanterns=" + lanterns);
                placedCount++;
            }
        }
        helper.assertTrue(placedCount == 20,
                "The complete Barracks biome and rotation matrix was not placed");
        helper.succeed();
    }

    public static void verifyArmoryVariants(GameTestHelper helper) {
        var level = helper.getLevel();
        var templateManager = level.getStructureManager();
        Holder.Reference<StructureProcessorList> processors = level.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolder(ResourceKey.create(
                        Registries.PROCESSOR_LIST,
                        VillageBuildingPools.EMPTY_PROCESSORS))
                .orElseThrow();
        List<String> biomes = List.of("plains", "desert", "savanna", "snowy", "taiga");
        int placedCount = 0;
        for (int biomeIndex = 0; biomeIndex < biomes.size(); biomeIndex++) {
            String biome = biomes.get(biomeIndex);
            ResourceLocation entryId = id("armory/" + biome);
            ResourceLocation templateId = id("village/" + biome + "/armory");
            StructureTemplate template = templateManager.get(templateId).orElse(null);
            helper.assertTrue(
                    template != null && template.getSize().equals(new Vec3i(9, 8, 10)),
                    "Armory lost its reviewed quartermaster-depot bounds: " + templateId);
            VillageBuildingPoolElement element = VillageBuildingPoolElement.create(
                    entryId,
                    VillageBuildingType.ARMORY,
                    templateId,
                    processors,
                    StructureTemplatePool.Projection.RIGID);
            List<StructureTemplate.StructureBlockInfo> jigsaws =
                    element.getShuffledJigsawBlocks(
                            templateManager,
                            BlockPos.ZERO,
                            Rotation.NONE,
                            RandomSource.create(1_701L + biomeIndex));
            helper.assertTrue(
                    jigsaws.size() == 1
                            && jigsaws.get(0).nbt() != null
                            && ("minecraft:village/" + biome + "/streets").equals(
                                    jigsaws.get(0).nbt().getString("pool")),
                    "Armory lost its biome road connector: " + templateId);

            for (Rotation rotation : Rotation.values()) {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
                BlockPos target = helper.absolutePos(new BlockPos(
                        8 + biomeIndex * 22,
                        2,
                        8 + rotation.ordinal() * 22));
                List<StructureTemplate.StructureBlockInfo> markers = element.getDataMarkers(
                        templateManager,
                        target,
                        rotation,
                        false);
                List<StructureTemplate.StructureBlockInfo> connectors =
                        element.getShuffledJigsawBlocks(
                                templateManager,
                                target,
                                rotation,
                                RandomSource.create(1_801L + placedCount));
                helper.assertTrue(
                        markers.size() == 1 && connectors.size() == 1,
                        "Armory lost its supply anchor or entrance: "
                                + templateId + "/" + rotation);
                BlockPos anchor = StructureTemplate.calculateRelativePosition(
                        settings, markers.get(0).pos()).offset(target);
                boolean placed = element.place(
                        templateManager,
                        level,
                        level.structureManager(),
                        level.getChunkSource().getGenerator(),
                        target,
                        BlockPos.ZERO,
                        rotation,
                        BoundingBox.infinite(),
                        RandomSource.create(1_901L + placedCount),
                        false);
                helper.assertTrue(placed,
                        "Armory failed rotated placement: " + templateId + "/" + rotation);
                assertNeighborSensitiveStatesSettled(
                        helper, template, settings, target,
                        "Armory " + templateId + "/" + rotation);

                BlockPos lowerDoor = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(4, 1, 2)).offset(target);
                BlockPos upperDoor = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(4, 2, 2)).offset(target);
                helper.assertTrue(
                        level.getBlockState(lowerDoor).getBlock() instanceof DoorBlock
                                && level.getBlockState(lowerDoor).getValue(DoorBlock.HALF)
                                        == DoubleBlockHalf.LOWER
                                && level.getBlockState(lowerDoor).getValue(DoorBlock.FACING)
                                        == rotation.rotate(Direction.NORTH)
                                && level.getBlockState(upperDoor).getBlock() instanceof DoorBlock
                                && level.getBlockState(upperDoor).getValue(DoorBlock.HALF)
                                        == DoubleBlockHalf.UPPER,
                        "Armory entrance door did not rotate as one valid pair: "
                                + templateId + "/" + rotation);

                BlockPos ascendingRoof = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(0, 4, 5)).offset(target);
                BlockPos descendingRoof = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(8, 4, 5)).offset(target);
                helper.assertTrue(
                        level.getBlockState(ascendingRoof).getBlock() instanceof StairBlock
                                && level.getBlockState(ascendingRoof).getValue(StairBlock.FACING)
                                        == rotation.rotate(Direction.EAST)
                                && level.getBlockState(descendingRoof).getBlock()
                                        instanceof StairBlock
                                && level.getBlockState(descendingRoof).getValue(StairBlock.FACING)
                                        == rotation.rotate(Direction.WEST),
                        "Armory gable stairs did not retain their inward pitch: "
                                + templateId + "/" + rotation);

                if (!biome.equals("desert")) {
                    BlockPos crossbeam = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(4, 4, 5)).offset(target);
                    BlockPos ridgeBeam = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(4, 7, 5)).offset(target);
                    helper.assertTrue(
                            level.getBlockState(crossbeam).hasProperty(RotatedPillarBlock.AXIS)
                                    && level.getBlockState(crossbeam).getValue(
                                            RotatedPillarBlock.AXIS)
                                            == rotation.rotate(Direction.EAST).getAxis()
                                    && level.getBlockState(ridgeBeam).hasProperty(
                                            RotatedPillarBlock.AXIS)
                                    && level.getBlockState(ridgeBeam).getValue(
                                            RotatedPillarBlock.AXIS)
                                            == rotation.rotate(Direction.SOUTH).getAxis(),
                            "Armory beams did not rotate with their supported spans: "
                                    + templateId + "/" + rotation);
                }

                BlockPos entrance = connectors.get(0).pos();
                helper.assertTrue(
                        level.getBlockState(entrance).isAir()
                                && level.getBlockState(entrance.above())
                                        .getCollisionShape(level, entrance.above()).isEmpty(),
                        "Armory road connector did not finalize as a clear entrance: "
                                + templateId + "/" + rotation);
                helper.assertTrue(
                        level.getBlockState(anchor).isAir()
                                || (level.getBlockState(anchor).is(ModBlocks.ARMORY.get())
                                        && level.getBlockState(anchor).getValue(ArmoryBlock.FACING)
                                                == rotation.rotate(Direction.NORTH)),
                        "Armory supply anchor was neither consumed nor correctly oriented: "
                                + templateId + "/" + rotation);

                List<BlockPos> serviceDestinations = List.of(
                        new BlockPos(4, 1, 6),
                        new BlockPos(3, 1, 7),
                        new BlockPos(5, 1, 7));
                Guard pathProbe = GuardEntityType.GUARD.get().create(level);
                helper.assertTrue(pathProbe != null,
                        "Could not create an Armory navigation probe");
                BlockPos probeStart = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(4, 1, 4)).offset(target);
                pathProbe.moveTo(
                        probeStart.getX() + 0.5D,
                        probeStart.getY(),
                        probeStart.getZ() + 0.5D,
                        0.0F,
                        0.0F);
                pathProbe.setOnGround(true);
                Path exitPath = pathProbe.getNavigation().createPath(entrance, 0);
                helper.assertTrue(
                        exitPath != null && exitPath.canReach() && exitPath.getNodeCount() > 1,
                        "Guard Villagers navigation could not leave the Armory: "
                                + templateId + "/" + rotation);
                for (BlockPos localDestination : serviceDestinations) {
                    BlockPos destination = StructureTemplate.calculateRelativePosition(
                            settings, localDestination).offset(target);
                    helper.assertTrue(
                            level.getBlockState(destination).isAir()
                                    && level.getBlockState(destination.above())
                                            .getCollisionShape(level, destination.above()).isEmpty(),
                            "Armory supply counter lost an approach cell: "
                                    + templateId + "/" + rotation
                                    + ", destination=" + localDestination);
                    Path path = pathProbe.getNavigation().createPath(destination, 0);
                    helper.assertTrue(
                            path != null && path.canReach() && path.getNodeCount() > 1,
                            "Guard Villagers navigation could not reach the Armory counter: "
                                    + templateId + "/" + rotation
                                    + ", destination=" + localDestination);
                }
                pathProbe.discard();

                int ironBars = 0;
                int craftingTables = 0;
                int targets = 0;
                int lanterns = 0;
                Vec3i size = template.getSize();
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            BlockPos position = StructureTemplate.calculateRelativePosition(
                                    settings, new BlockPos(x, y, z)).offset(target);
                            if (level.getBlockState(position).is(Blocks.IRON_BARS)) {
                                ironBars++;
                            } else if (level.getBlockState(position).is(Blocks.CRAFTING_TABLE)) {
                                craftingTables++;
                            } else if (level.getBlockState(position).is(Blocks.TARGET)) {
                                targets++;
                            } else if (level.getBlockState(position).is(Blocks.LANTERN)) {
                                lanterns++;
                                helper.assertTrue(
                                        level.getBlockState(position).canSurvive(level, position),
                                        "Armory placed an unsupported lantern: "
                                                + templateId + "/" + rotation);
                            }
                        }
                    }
                }
                helper.assertTrue(
                        ironBars == 8 && craftingTables == 1
                                && targets == 2 && lanterns == 1,
                        "Armory interior cues changed unexpectedly: "
                                + templateId + "/" + rotation
                                + ", ironBars=" + ironBars
                                + ", craftingTables=" + craftingTables
                                + ", targets=" + targets
                                + ", lanterns=" + lanterns);
                placedCount++;
            }
        }
        helper.assertTrue(placedCount == 20,
                "The complete Armory biome and rotation matrix was not placed");
        helper.succeed();
    }

    public static void verifySentryPostVariants(GameTestHelper helper) {
        var level = helper.getLevel();
        var templateManager = level.getStructureManager();
        Holder.Reference<StructureProcessorList> processors = level.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolder(ResourceKey.create(
                        Registries.PROCESSOR_LIST,
                        VillageBuildingPools.EMPTY_PROCESSORS))
                .orElseThrow();
        List<String> biomes = List.of("plains", "desert", "savanna", "snowy", "taiga");
        int placedCount = 0;
        for (int biomeIndex = 0; biomeIndex < biomes.size(); biomeIndex++) {
            String biome = biomes.get(biomeIndex);
            ResourceLocation entryId = id("sentry_post/" + biome);
            ResourceLocation templateId = id("village/" + biome + "/sentry_post");
            StructureTemplate template = templateManager.get(templateId).orElse(null);
            helper.assertTrue(
                    template != null && template.getSize().equals(new Vec3i(7, 7, 8)),
                    "Sentry Post lost its compact checkpoint bounds: " + templateId);
            VillageBuildingPoolElement element = VillageBuildingPoolElement.create(
                    entryId,
                    VillageBuildingType.SENTRY_POST,
                    templateId,
                    processors,
                    StructureTemplatePool.Projection.RIGID);
            List<StructureTemplate.StructureBlockInfo> jigsaws =
                    element.getShuffledJigsawBlocks(
                            templateManager,
                            BlockPos.ZERO,
                            Rotation.NONE,
                            RandomSource.create(2_001L + biomeIndex));
            helper.assertTrue(
                    jigsaws.size() == 1
                            && jigsaws.get(0).nbt() != null
                            && ("minecraft:village/" + biome + "/streets").equals(
                                    jigsaws.get(0).nbt().getString("pool")),
                    "Sentry Post lost its biome road connector: " + templateId);

            for (Rotation rotation : Rotation.values()) {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
                BlockPos target = helper.absolutePos(new BlockPos(
                        8 + biomeIndex * 18,
                        2,
                        8 + rotation.ordinal() * 18));
                List<StructureTemplate.StructureBlockInfo> markers = element.getDataMarkers(
                        templateManager,
                        target,
                        rotation,
                        false);
                List<StructureTemplate.StructureBlockInfo> connectors =
                        element.getShuffledJigsawBlocks(
                                templateManager,
                                target,
                                rotation,
                                RandomSource.create(2_101L + placedCount));
                helper.assertTrue(
                        markers.size() == 1 && connectors.size() == 1,
                        "Sentry Post lost its guard anchor or entrance: "
                                + templateId + "/" + rotation);
                BlockPos anchor = StructureTemplate.calculateRelativePosition(
                        settings, markers.get(0).pos()).offset(target);
                boolean placed = element.place(
                        templateManager,
                        level,
                        level.structureManager(),
                        level.getChunkSource().getGenerator(),
                        target,
                        BlockPos.ZERO,
                        rotation,
                        BoundingBox.infinite(),
                        RandomSource.create(2_201L + placedCount),
                        false);
                helper.assertTrue(placed,
                        "Sentry Post failed rotated placement: "
                                + templateId + "/" + rotation);
                assertNeighborSensitiveStatesSettled(
                        helper, template, settings, target,
                        "Sentry Post " + templateId + "/" + rotation);

                BoundingBox guardBounds = new BoundingBox(
                        anchor.getX() - 1,
                        anchor.getY() - 1,
                        anchor.getZ() - 1,
                        anchor.getX() + 1,
                        anchor.getY() + 1,
                        anchor.getZ() + 1);
                helper.assertTrue(
                        GuardPostSpawner.isSafeSpawnCell(level, guardBounds, anchor),
                        "Sentry Post lost its one safe guard cell: "
                                + templateId + "/" + rotation);

                BlockPos ascendingRoof = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(0, 4, 4)).offset(target);
                BlockPos descendingRoof = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(6, 4, 4)).offset(target);
                helper.assertTrue(
                        level.getBlockState(ascendingRoof).getBlock() instanceof StairBlock
                                && level.getBlockState(ascendingRoof).getValue(StairBlock.FACING)
                                        == rotation.rotate(Direction.EAST)
                                && level.getBlockState(descendingRoof).getBlock()
                                        instanceof StairBlock
                                && level.getBlockState(descendingRoof).getValue(StairBlock.FACING)
                                        == rotation.rotate(Direction.WEST),
                        "Sentry Post roof stairs did not retain their inward pitch: "
                                + templateId + "/" + rotation);

                BlockPos craftingBench = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(1, 1, 5)).offset(target);
                BlockPos seat = StructureTemplate.calculateRelativePosition(
                        settings, new BlockPos(5, 1, 5)).offset(target);
                helper.assertTrue(
                        level.getBlockState(craftingBench).is(Blocks.CRAFTING_TABLE)
                                && level.getBlockState(seat).getBlock() instanceof StairBlock
                                && level.getBlockState(seat).getValue(StairBlock.FACING)
                                        == rotation.rotate(Direction.SOUTH),
                        "Sentry Post interior utility details did not rotate correctly: "
                                + templateId + "/" + rotation);

                if (!biome.equals("desert")) {
                    BlockPos frontColumn = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(1, 3, 1)).offset(target);
                    BlockPos rearBeam = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(3, 3, 6)).offset(target);
                    BlockPos roofBeam = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(3, 4, 1)).offset(target);
                    BlockPos ridgeBeam = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(3, 6, 4)).offset(target);
                    helper.assertTrue(
                            level.getBlockState(frontColumn).hasProperty(RotatedPillarBlock.AXIS)
                                    && level.getBlockState(frontColumn).getValue(
                                            RotatedPillarBlock.AXIS)
                                            == Direction.Axis.Y
                                    && level.getBlockState(rearBeam).hasProperty(
                                            RotatedPillarBlock.AXIS)
                                    && level.getBlockState(rearBeam).getValue(
                                            RotatedPillarBlock.AXIS)
                                            == rotation.rotate(Direction.EAST).getAxis()
                                    && level.getBlockState(roofBeam).hasProperty(
                                            RotatedPillarBlock.AXIS)
                                    && level.getBlockState(roofBeam).getValue(
                                            RotatedPillarBlock.AXIS)
                                            == rotation.rotate(Direction.EAST).getAxis()
                                    && level.getBlockState(ridgeBeam).hasProperty(
                                            RotatedPillarBlock.AXIS)
                                    && level.getBlockState(ridgeBeam).getValue(
                                            RotatedPillarBlock.AXIS)
                                            == rotation.rotate(Direction.SOUTH).getAxis(),
                            "Sentry Post frame did not rotate with its supported spans: "
                                + templateId + "/" + rotation);
                }

                BlockPos entrance = connectors.get(0).pos();
                helper.assertTrue(
                        level.getBlockState(entrance).isAir()
                                && level.getBlockState(entrance.above())
                                        .getCollisionShape(level, entrance.above()).isEmpty(),
                        "Sentry Post road connector did not finalize as a clear entrance: "
                                + templateId + "/" + rotation);
                for (int localX = 2; localX <= 4; localX++) {
                    BlockPos aperture = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(localX, 1, 2)).offset(target);
                    helper.assertTrue(
                            level.getBlockState(aperture)
                                            .getCollisionShape(level, aperture).isEmpty()
                                    && level.getBlockState(aperture.above())
                                            .getCollisionShape(level, aperture.above()).isEmpty(),
                            "Sentry Post lost its three-wide checkpoint opening: "
                                    + templateId + "/" + rotation + ", x=" + localX);
                }
                Guard pathProbe = GuardEntityType.GUARD.get().create(level);
                helper.assertTrue(pathProbe != null,
                        "Could not create a Sentry Post navigation probe");
                pathProbe.moveTo(
                        anchor.getX() + 0.5D,
                        anchor.getY(),
                        anchor.getZ() + 0.5D,
                        0.0F,
                        0.0F);
                pathProbe.setOnGround(true);
                Path exitPath = pathProbe.getNavigation().createPath(entrance, 0);
                helper.assertTrue(
                        exitPath != null && exitPath.canReach() && exitPath.getNodeCount() > 1,
                        "Guard Villagers navigation could not leave the Sentry Post: "
                                + templateId + "/" + rotation);
                Vec3 sightOrigin = new Vec3(
                        anchor.getX() + 0.5D,
                        anchor.getY() + 1.6D,
                        anchor.getZ() + 0.5D);
                for (Direction localDirection : List.of(
                        Direction.NORTH,
                        Direction.WEST,
                        Direction.EAST)) {
                    Direction direction = rotation.rotate(localDirection);
                    double firingLaneLength = localDirection == Direction.NORTH
                            ? 4.49D
                            : 3.49D;
                    for (double rise : List.of(0.0D, 1.0D)) {
                        Vec3 sightTarget = sightOrigin.add(
                                direction.getStepX() * firingLaneLength,
                                rise,
                                direction.getStepZ() * firingLaneLength);
                        HitResult sightLine = level.clip(new ClipContext(
                                sightOrigin,
                                sightTarget,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                pathProbe));
                        BlockPos obstruction = BlockPos.containing(sightLine.getLocation());
                        helper.assertTrue(
                                sightLine.getType() == HitResult.Type.MISS,
                                "Sentry Post obstructed a guard firing lane: "
                                        + templateId + "/" + rotation
                                        + ", direction=" + localDirection
                                        + ", rise=" + rise
                                        + ", target=" + target
                                        + ", anchor=" + anchor
                                        + ", hit=" + obstruction
                                        + ", block=" + level.getBlockState(obstruction));
                    }
                }
                for (Direction localSide : List.of(Direction.WEST, Direction.EAST)) {
                    Direction forward = rotation.rotate(Direction.NORTH);
                    Direction side = rotation.rotate(localSide);
                    Vec3 diagonalTarget = sightOrigin.add(
                            forward.getStepX() * 4.25D + side.getStepX() * 1.75D,
                            1.0D,
                            forward.getStepZ() * 4.25D + side.getStepZ() * 1.75D);
                    HitResult diagonalLine = level.clip(new ClipContext(
                            sightOrigin,
                            diagonalTarget,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            pathProbe));
                    helper.assertTrue(
                            diagonalLine.getType() == HitResult.Type.MISS,
                            "Sentry Post obstructed a raised diagonal firing lane: "
                                    + templateId + "/" + rotation + "/" + localSide);
                }
                pathProbe.discard();

                for (BlockPos localCover : List.of(
                        new BlockPos(0, 1, 3),
                        new BlockPos(6, 1, 3),
                        new BlockPos(0, 1, 4),
                        new BlockPos(6, 1, 4),
                        new BlockPos(0, 1, 5),
                        new BlockPos(6, 1, 5))) {
                    BlockPos cover = StructureTemplate.calculateRelativePosition(
                            settings, localCover).offset(target);
                    var coverShape = level.getBlockState(cover)
                            .getCollisionShape(level, cover);
                    boolean fullCover = localCover.getZ() != 4;
                    helper.assertTrue(
                            !coverShape.isEmpty()
                                    && (fullCover
                                            ? coverShape.max(Direction.Axis.Y) == 1.0D
                                            : coverShape.max(Direction.Axis.Y) == 0.5D)
                                    && level.getBlockState(cover.above())
                                            .getCollisionShape(level, cover.above()).isEmpty(),
                            "Sentry Post mixed cover lost its protected firing gap: "
                                    + templateId + "/" + rotation
                                    + ", cover=" + localCover);
                }

                int glass = 0;
                int lanterns = 0;
                int doors = 0;
                int utilityBlocks = 0;
                Vec3i size = template.getSize();
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            BlockPos position = StructureTemplate.calculateRelativePosition(
                                    settings, new BlockPos(x, y, z)).offset(target);
                            if (level.getBlockState(position).is(Blocks.GLASS)
                                    || level.getBlockState(position).is(
                                            Blocks.ORANGE_STAINED_GLASS)
                                    || level.getBlockState(position).is(
                                            Blocks.LIGHT_BLUE_STAINED_GLASS)) {
                                glass++;
                            } else if (level.getBlockState(position).is(Blocks.LANTERN)) {
                                lanterns++;
                                helper.assertTrue(
                                        level.getBlockState(position).canSurvive(level, position),
                                        "Sentry Post placed an unsupported lantern: "
                                                + templateId + "/" + rotation);
                            } else if (level.getBlockState(position).getBlock()
                                    instanceof DoorBlock) {
                                doors++;
                            } else if (level.getBlockState(position).is(Blocks.CRAFTING_TABLE)
                                    || level.getBlockState(position).is(Blocks.BARREL)
                                    || level.getBlockState(position).is(Blocks.CHEST)) {
                                utilityBlocks++;
                            }
                            helper.assertTrue(
                                    !level.getBlockState(position).is(ModBlocks.ARMORY.get()),
                                    "Sentry Post unexpectedly placed an Armory: "
                                            + templateId + "/" + rotation);
                        }
                    }
                }
                helper.assertTrue(
                        glass == 2 && lanterns == 1 && doors == 0 && utilityBlocks == 1,
                        "Sentry Post checkpoint cues changed unexpectedly: "
                                + templateId + "/" + rotation
                                + ", glass=" + glass
                                + ", lanterns=" + lanterns
                                + ", doors=" + doors
                                + ", utilityBlocks=" + utilityBlocks);
                placedCount++;
            }
        }
        helper.assertTrue(placedCount == 20,
                "The complete Sentry Post biome and rotation matrix was not placed");
        helper.succeed();
    }

    public static void verifyIntegratedWatchtowerVariants(GameTestHelper helper) {
        var level = helper.getLevel();
        var templateManager = level.getStructureManager();
        Holder.Reference<StructureProcessorList> processors = level.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolder(ResourceKey.create(
                        Registries.PROCESSOR_LIST,
                        VillageBuildingPools.EMPTY_PROCESSORS))
                .orElseThrow();
        List<String> biomes = List.of("plains", "desert", "savanna", "snowy", "taiga");
        int placedCount = 0;
        for (int biomeIndex = 0; biomeIndex < biomes.size(); biomeIndex++) {
            String biome = biomes.get(biomeIndex);
            ResourceLocation entryId = id("watchtower/" + biome);
            ResourceLocation templateId = id("village/" + biome + "/watchtower");
            StructureTemplate template = templateManager.get(templateId).orElse(null);
            helper.assertTrue(
                    template != null && template.getSize().equals(new Vec3i(9, 13, 10)),
                    "Integrated Village Watchtower lost its reviewed bounds: " + templateId);
            VillageBuildingPoolElement element = VillageBuildingPoolElement.create(
                    entryId,
                    VillageBuildingType.WATCHTOWER,
                    templateId,
                    processors,
                    StructureTemplatePool.Projection.RIGID);
            List<StructureTemplate.StructureBlockInfo> jigsaws =
                    element.getShuffledJigsawBlocks(
                            templateManager,
                            BlockPos.ZERO,
                            Rotation.NONE,
                            RandomSource.create(901L + biomeIndex));
            helper.assertTrue(
                    jigsaws.size() == 1
                            && jigsaws.get(0).nbt() != null
                            && ("minecraft:village/" + biome + "/streets").equals(
                                    jigsaws.get(0).nbt().getString("pool")),
                    "Integrated Village Watchtower lost its biome road connector: "
                            + templateId);

            for (Rotation rotation : Rotation.values()) {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
                BlockPos target = helper.absolutePos(new BlockPos(
                        8 + biomeIndex * 18,
                        2,
                        8 + rotation.ordinal() * 18));
                List<StructureTemplate.StructureBlockInfo> markers = element.getDataMarkers(
                        templateManager,
                        target,
                        rotation,
                        false);
                List<StructureTemplate.StructureBlockInfo> connectors =
                        element.getShuffledJigsawBlocks(
                                templateManager,
                                target,
                                rotation,
                                RandomSource.create(951L + placedCount));
                helper.assertTrue(markers.size() == 1 && connectors.size() == 1,
                        "Integrated Village Watchtower lost its anchor or entrance: "
                                + templateId);
                BlockPos anchor = StructureTemplate.calculateRelativePosition(
                        settings, markers.get(0).pos()).offset(target);
                boolean placed = element.place(
                        templateManager,
                        level,
                        level.structureManager(),
                        level.getChunkSource().getGenerator(),
                        target,
                        BlockPos.ZERO,
                        rotation,
                        BoundingBox.infinite(),
                        RandomSource.create(1_001L + placedCount),
                        false);
                helper.assertTrue(placed,
                        "Integrated Village Watchtower failed rotated placement: "
                                + templateId + "/" + rotation);
                assertNeighborSensitiveStatesSettled(
                        helper, template, settings, target,
                        "Integrated Village Watchtower " + templateId + "/" + rotation);

                BlockPos upperAnchor = anchor.above(5);
                BoundingBox lowerGuardBounds = new BoundingBox(
                        anchor.getX() - 1,
                        anchor.getY() - 1,
                        anchor.getZ() - 1,
                        anchor.getX() + 1,
                        anchor.getY() + 1,
                        anchor.getZ() + 1);
                BoundingBox upperGuardBounds = new BoundingBox(
                        upperAnchor.getX() - 1,
                        upperAnchor.getY() - 1,
                        upperAnchor.getZ() - 1,
                        upperAnchor.getX() + 1,
                        upperAnchor.getY() + 1,
                        upperAnchor.getZ() + 1);
                for (int memberIndex = 0; memberIndex < 2; memberIndex++) {
                    BlockPos lowerPosition = GuardPostSpawner.spawnPosition(
                            anchor, rotation, memberIndex);
                    BlockPos upperPosition = GuardPostSpawner.spawnPosition(
                            upperAnchor, rotation, memberIndex);
                    helper.assertTrue(
                            GuardPostSpawner.isSafeSpawnCell(
                                    level, lowerGuardBounds, lowerPosition)
                                    && GuardPostSpawner.isSafeSpawnCell(
                                            level, upperGuardBounds, upperPosition),
                            "Integrated Village Watchtower lost a safe ready-room or deck cell "
                                    + memberIndex + ": " + templateId + "/" + rotation);
                }

                List<BlockPos> localRoute = List.of(
                        new BlockPos(4, 0, 7),
                        new BlockPos(4, 1, 6),
                        new BlockPos(4, 2, 5),
                        new BlockPos(4, 3, 4),
                        new BlockPos(4, 4, 3),
                        new BlockPos(4, 5, 2));
                for (int routeIndex = 0; routeIndex < localRoute.size(); routeIndex++) {
                    BlockPos routeFloor = StructureTemplate.calculateRelativePosition(
                            settings, localRoute.get(routeIndex)).offset(target);
                    helper.assertTrue(
                            !level.getBlockState(routeFloor)
                                    .getCollisionShape(level, routeFloor).isEmpty()
                                    && level.getBlockState(routeFloor.above())
                                            .getCollisionShape(level, routeFloor.above()).isEmpty()
                                    && level.getBlockState(routeFloor.above(2))
                                            .getCollisionShape(level, routeFloor.above(2)).isEmpty(),
                            "Integrated Village Watchtower stair route is blocked at step "
                                    + routeIndex + ": " + templateId + "/" + rotation);
                }

                Guard routeProbe = GuardEntityType.GUARD.get().create(level);
                helper.assertTrue(routeProbe != null,
                        "Could not create an integrated Watchtower navigation probe");
                routeProbe.moveTo(
                        anchor.getX() + 0.5D,
                        anchor.getY(),
                        anchor.getZ() + 0.5D,
                        0.0F,
                        0.0F);
                routeProbe.setOnGround(true);
                List<BlockPos> localWaypoints = List.of(
                        new BlockPos(4, 2, 6),
                        new BlockPos(4, 3, 5),
                        new BlockPos(4, 4, 4),
                        new BlockPos(4, 5, 3),
                        new BlockPos(4, 6, 2),
                        new BlockPos(4, 6, 5),
                        new BlockPos(4, 6, 7));
                for (int waypointIndex = 0;
                        waypointIndex < localWaypoints.size();
                        waypointIndex++) {
                    BlockPos waypoint = StructureTemplate.calculateRelativePosition(
                            settings, localWaypoints.get(waypointIndex)).offset(target);
                    Path waypointPath = routeProbe.getNavigation().createPath(waypoint, 0);
                    helper.assertTrue(
                            waypointPath != null && waypointPath.canReach(),
                            "A Guard Villager could not navigate through Watchtower waypoint "
                                    + waypointIndex + " (" + localWaypoints.get(waypointIndex)
                                    + "): " + templateId + "/" + rotation
                                    + ", path=" + (waypointPath == null
                                            ? "null"
                                            : waypointPath.getNodeCount() + " nodes"));
                }
                Path upperRoute = routeProbe.getNavigation().createPath(upperAnchor, 0);
                helper.assertTrue(
                        upperRoute != null && upperRoute.canReach()
                                && upperRoute.getNodeCount() > 1,
                        "A Guard Villager could not navigate from the ready room to the deck: "
                                + templateId + "/" + rotation);

                for (int x = 1; x <= 7; x++) {
                    for (int z = 2; z <= 8; z++) {
                        BlockPos upperVisibilityCell = StructureTemplate.calculateRelativePosition(
                                settings, new BlockPos(x, 8, z)).offset(target);
                        helper.assertTrue(
                                level.getBlockState(upperVisibilityCell).isAir(),
                                "Integrated Watchtower lost its raised observation clearance: "
                                        + templateId + "/" + rotation
                                        + ", cell=" + new BlockPos(x, 8, z));
                    }
                }

                Vec3 upperSightOrigin = new Vec3(
                        upperAnchor.getX() + 0.5D,
                        upperAnchor.getY() + 1.6D,
                        upperAnchor.getZ() + 0.5D);
                for (Direction localDirection : List.of(
                        Direction.NORTH,
                        Direction.SOUTH,
                        Direction.WEST,
                        Direction.EAST)) {
                    Direction direction = rotation.rotate(localDirection);
                    double length = switch (localDirection) {
                        case NORTH -> 6.0D;
                        case SOUTH -> 2.0D;
                        default -> 4.0D;
                    };
                    HitResult sightLine = level.clip(new ClipContext(
                            upperSightOrigin,
                            upperSightOrigin.add(
                                    direction.getStepX() * length,
                                    0.0D,
                                    direction.getStepZ() * length),
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            routeProbe));
                    BlockPos obstruction = BlockPos.containing(sightLine.getLocation());
                    helper.assertTrue(
                            sightLine.getType() == HitResult.Type.MISS,
                            "Integrated Watchtower parapet blocked an upper firing lane: "
                                    + templateId + "/" + rotation + "/" + localDirection
                                    + ", hit=" + obstruction
                                    + ", block=" + level.getBlockState(obstruction));
                }
                routeProbe.discard();

                List<BlockPos> localParapet = new ArrayList<>();
                for (int x = 1; x <= 7; x++) {
                    localParapet.add(new BlockPos(x, 6, 1));
                    localParapet.add(new BlockPos(x, 6, 9));
                }
                for (int z = 2; z <= 8; z++) {
                    localParapet.add(new BlockPos(0, 6, z));
                    localParapet.add(new BlockPos(8, 6, z));
                }
                for (BlockPos localCover : localParapet) {
                    BlockPos cover = StructureTemplate.calculateRelativePosition(
                            settings, localCover).offset(target);
                    var coverState = level.getBlockState(cover);
                    var shape = coverState.getCollisionShape(level, cover);
                    helper.assertTrue(
                            coverState.getBlock() instanceof FenceBlock
                                    && !shape.isEmpty()
                                    && shape.max(Direction.Axis.Y) == 1.5D
                                    && level.getBlockState(cover.above())
                                            .getCollisionShape(level, cover.above()).isEmpty(),
                            "Integrated Watchtower lost its connected fence deck railing: "
                                    + templateId + "/" + rotation
                                    + ", cover=" + localCover);
                }

                BlockPos entrance = connectors.get(0).pos();
                helper.assertTrue(
                        level.getBlockState(entrance).isAir()
                                && level.getBlockState(entrance.above())
                                        .getCollisionShape(level, entrance.above()).isEmpty(),
                        "Integrated Village Watchtower road connector did not finalize as air: "
                                + templateId + "/" + rotation);

                Vec3i size = template.getSize();
                int ironBars = 0;
                int fences = 0;
                int lanterns = 0;
                int doors = 0;
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            BlockPos position = StructureTemplate.calculateRelativePosition(
                                    settings, new BlockPos(x, y, z)).offset(target);
                            if (level.getBlockState(position).is(Blocks.IRON_BARS)) {
                                ironBars++;
                            } else if (level.getBlockState(position).getBlock()
                                    instanceof FenceBlock) {
                                fences++;
                            } else if (level.getBlockState(position).is(Blocks.LANTERN)) {
                                lanterns++;
                                helper.assertTrue(
                                        level.getBlockState(position).canSurvive(level, position),
                                        "Integrated Village Watchtower placed an unsupported lantern: "
                                                + templateId + "/" + rotation);
                            } else if (level.getBlockState(position).getBlock()
                                    instanceof DoorBlock) {
                                doors++;
                            }
                            helper.assertTrue(
                                    !level.getBlockState(position).is(ModBlocks.ARMORY.get()),
                                    "Integrated Village Watchtower unexpectedly placed an Armory: "
                                            + templateId + "/" + rotation);
                        }
                    }
                }
                helper.assertTrue(
                        ironBars == 17 && fences == 28 && lanterns == 1 && doors == 2,
                        "Integrated Village Watchtower lost its ready-room details: "
                                + templateId + "/" + rotation
                                + ", ironBars=" + ironBars
                                + ", fences=" + fences
                                + ", lanterns=" + lanterns
                                + ", doors=" + doors);
                placedCount++;
            }
        }
        helper.assertTrue(placedCount == 20,
                "The complete integrated Village Watchtower rotation matrix was not placed");
        helper.succeed();
    }

    public static void verifyTrainingYardVariants(GameTestHelper helper) {
        var level = helper.getLevel();
        var templateManager = level.getStructureManager();
        Holder.Reference<StructureProcessorList> processors = level.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolder(ResourceKey.create(
                        Registries.PROCESSOR_LIST,
                        VillageBuildingPools.EMPTY_PROCESSORS))
                .orElseThrow();
        List<String> biomes = List.of("plains", "desert", "savanna", "snowy", "taiga");
        int placedCount = 0;
        for (int biomeIndex = 0; biomeIndex < biomes.size(); biomeIndex++) {
            String biome = biomes.get(biomeIndex);
            ResourceLocation entryId = id("training_yard/" + biome);
            ResourceLocation templateId = id("village/" + biome + "/training_yard");
            StructureTemplate template = templateManager.get(templateId).orElse(null);
            helper.assertTrue(
                    template != null && template.getSize().equals(new Vec3i(11, 5, 10)),
                    "Training Yard lost its reviewed bounds: " + templateId);
            VillageBuildingPoolElement element = VillageBuildingPoolElement.create(
                    entryId,
                    VillageBuildingType.TRAINING_YARD,
                    templateId,
                    processors,
                    StructureTemplatePool.Projection.RIGID);
            List<StructureTemplate.StructureBlockInfo> jigsaws =
                    element.getShuffledJigsawBlocks(
                            templateManager,
                            BlockPos.ZERO,
                            Rotation.NONE,
                            RandomSource.create(1_201L + biomeIndex));
            helper.assertTrue(
                    jigsaws.size() == 1
                            && jigsaws.get(0).nbt() != null
                            && ("minecraft:village/" + biome + "/streets").equals(
                                    jigsaws.get(0).nbt().getString("pool")),
                    "Training Yard lost its biome road connector: " + templateId);

            for (Rotation rotation : Rotation.values()) {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
                BlockPos target = helper.absolutePos(new BlockPos(
                        8 + biomeIndex * 20,
                        2,
                        8 + rotation.ordinal() * 16));
                List<StructureTemplate.StructureBlockInfo> markers = element.getDataMarkers(
                        templateManager,
                        target,
                        rotation,
                        false);
                List<StructureTemplate.StructureBlockInfo> connectors =
                        element.getShuffledJigsawBlocks(
                                templateManager,
                                target,
                                rotation,
                                RandomSource.create(1_251L + placedCount));
                helper.assertTrue(markers.size() == 1 && connectors.size() == 1,
                        "Training Yard lost its guard anchor or entrance: "
                                + templateId + "/" + rotation);
                BlockPos anchor = StructureTemplate.calculateRelativePosition(
                        settings, markers.get(0).pos()).offset(target);
                boolean placed = element.place(
                        templateManager,
                        level,
                        level.structureManager(),
                        level.getChunkSource().getGenerator(),
                        target,
                        BlockPos.ZERO,
                        rotation,
                        BoundingBox.infinite(),
                        RandomSource.create(1_301L + placedCount),
                        false);
                helper.assertTrue(placed,
                        "Training Yard failed rotated placement: "
                                + templateId + "/" + rotation);
                assertNeighborSensitiveStatesSettled(
                        helper, template, settings, target,
                        "Training Yard " + templateId + "/" + rotation);

                BoundingBox guardBounds = new BoundingBox(
                        anchor.getX() - 1,
                        anchor.getY() - 1,
                        anchor.getZ() - 1,
                        anchor.getX() + 1,
                        anchor.getY() + 1,
                        anchor.getZ() + 1);
                for (int memberIndex = 0; memberIndex < 2; memberIndex++) {
                    BlockPos position = GuardPostSpawner.spawnPosition(
                            anchor, rotation, memberIndex);
                    helper.assertTrue(
                            GuardPostSpawner.isSafeSpawnCell(level, guardBounds, position),
                            "Training Yard lost safe guard cell " + memberIndex
                                    + ": " + templateId + "/" + rotation);
                }

                BlockPos entrance = connectors.get(0).pos();
                helper.assertTrue(
                        level.getBlockState(entrance).isAir()
                                && level.getBlockState(entrance.above())
                                        .getCollisionShape(level, entrance.above()).isEmpty(),
                        "Training Yard road connector did not finalize as a clear entrance: "
                                + templateId + "/" + rotation);
                Guard pathProbe = GuardEntityType.GUARD.get().create(level);
                helper.assertTrue(pathProbe != null,
                        "Could not create a Training Yard navigation probe");

                List<BlockPos> localTargets = List.of(
                        new BlockPos(7, 2, 7),
                        new BlockPos(3, 2, 7));
                for (int memberIndex = 0; memberIndex < localTargets.size(); memberIndex++) {
                    BlockPos guardCell = GuardPostSpawner.spawnPosition(
                            anchor, rotation, memberIndex);
                    pathProbe.moveTo(
                            guardCell.getX() + 0.5D,
                            guardCell.getY(),
                            guardCell.getZ() + 0.5D,
                            0.0F,
                            0.0F);
                    pathProbe.setOnGround(true);
                    Path exitPath = pathProbe.getNavigation().createPath(entrance, 0);
                    helper.assertTrue(
                            exitPath != null
                                    && exitPath.canReach()
                                    && exitPath.getNodeCount() > 1,
                            "A Guard Villager could not leave its Training Yard firing cell: "
                                    + templateId + "/" + rotation
                                    + ", member=" + memberIndex);

                    BlockPos localApproach = new BlockPos(
                            localTargets.get(memberIndex).getX(),
                            1,
                            6);
                    BlockPos targetApproach = StructureTemplate.calculateRelativePosition(
                            settings, localApproach).offset(target);
                    Path approachPath = pathProbe.getNavigation().createPath(
                            targetApproach, 0);
                    helper.assertTrue(
                            approachPath != null
                                    && approachPath.canReach()
                                    && approachPath.getNodeCount() > 1,
                            "A Guard Villager could not cross the Training Yard drill lane: "
                                    + templateId + "/" + rotation
                                    + ", member=" + memberIndex);

                    BlockPos targetBlock = StructureTemplate.calculateRelativePosition(
                            settings, localTargets.get(memberIndex)).offset(target);
                    Vec3 sightOrigin = new Vec3(
                            guardCell.getX() + 0.5D,
                            guardCell.getY() + 1.6D,
                            guardCell.getZ() + 0.5D);
                    BlockHitResult sightLine = level.clip(new ClipContext(
                            sightOrigin,
                            Vec3.atCenterOf(targetBlock),
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            pathProbe));
                    helper.assertTrue(
                            sightLine.getType() == HitResult.Type.BLOCK
                                    && sightLine.getBlockPos().equals(targetBlock),
                            "Training Yard obstructed a guard-to-target sight line: "
                                    + templateId + "/" + rotation
                                    + ", member=" + memberIndex
                                    + ", hit=" + sightLine.getBlockPos());
                }
                pathProbe.discard();

                if (!biome.equals("desert")) {
                    BlockPos frontBeam = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(5, 3, 2)).offset(target);
                    BlockPos sideBeam = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(2, 3, 3)).offset(target);
                    helper.assertTrue(
                            level.getBlockState(frontBeam).hasProperty(
                                    RotatedPillarBlock.AXIS)
                                    && level.getBlockState(frontBeam).getValue(
                                            RotatedPillarBlock.AXIS)
                                            == rotation.rotate(Direction.EAST).getAxis()
                                    && level.getBlockState(sideBeam).hasProperty(
                                            RotatedPillarBlock.AXIS)
                                    && level.getBlockState(sideBeam).getValue(
                                            RotatedPillarBlock.AXIS)
                                            == rotation.rotate(Direction.SOUTH).getAxis(),
                            "Training Yard shelter beams did not rotate with their spans: "
                                    + templateId + "/" + rotation);
                }

                int targets = 0;
                int hayBales = 0;
                int lanterns = 0;
                int doors = 0;
                int workstations = 0;
                Vec3i size = template.getSize();
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            BlockPos position = StructureTemplate.calculateRelativePosition(
                                    settings, new BlockPos(x, y, z)).offset(target);
                            if (level.getBlockState(position).is(Blocks.TARGET)) {
                                targets++;
                            } else if (level.getBlockState(position).is(Blocks.HAY_BLOCK)) {
                                hayBales++;
                            } else if (level.getBlockState(position).is(Blocks.LANTERN)) {
                                lanterns++;
                                helper.assertTrue(
                                        level.getBlockState(position).canSurvive(level, position),
                                        "Training Yard placed an unsupported lantern: "
                                                + templateId + "/" + rotation);
                            } else if (level.getBlockState(position).getBlock()
                                    instanceof DoorBlock) {
                                doors++;
                            } else if (level.getBlockState(position).is(Blocks.CRAFTING_TABLE)
                                    || level.getBlockState(position).is(Blocks.BARREL)
                                    || level.getBlockState(position).is(Blocks.CHEST)) {
                                workstations++;
                            }
                            helper.assertTrue(
                                    !level.getBlockState(position).is(ModBlocks.ARMORY.get()),
                                    "Training Yard unexpectedly placed an Armory: "
                                            + templateId + "/" + rotation);
                        }
                    }
                }
                helper.assertTrue(
                        targets == 2 && hayBales == 2 && lanterns == 1
                                && doors == 0 && workstations == 0,
                        "Training Yard decorative target layout changed: "
                                + templateId + "/" + rotation
                                + ", targets=" + targets
                                + ", hay=" + hayBales
                                + ", lanterns=" + lanterns
                                + ", doors=" + doors
                                + ", workstations=" + workstations);
                placedCount++;
            }
        }
        helper.assertTrue(placedCount == 20,
                "The complete Training Yard rotation matrix was not placed");
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

    public static void verifyDiagnostics(GameTestHelper helper) {
        var level = helper.getLevel();
        StructureTemplatePool pool = level.registryAccess()
                .registryOrThrow(Registries.TEMPLATE_POOL)
                .get(VillageBuildingPools.PLAINS_HOUSES);
        helper.assertTrue(pool != null, "The vanilla plains houses pool was missing");
        StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;
        List<Pair<StructurePoolElement, Integer>> rawBefore = new ArrayList<>(
                accessor.villagerGuardGunners$getRawTemplates());
        ObjectArrayList<StructurePoolElement> flattenedBefore = new ObjectArrayList<>(
                accessor.villagerGuardGunners$getTemplates());
        int maximumSizeBefore = accessor.villagerGuardGunners$getMaxSize();
        VillageBuildingDefinitionReloadSnapshot definitionsBefore =
                VillageBuildingDefinitions.reloadSnapshot();
        VillageBuildingInstallationSnapshot installationBefore =
                VillageBuildingPoolIntegration.installationSnapshot();

        var dispatcher = level.getServer().getCommands().getDispatcher();
        var administrator = level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(
                        helper.absolutePos(new BlockPos(1, 1, 1))))
                .withPermission(4);
        try {
            int status = dispatcher.execute("vgg villagebuildings status", administrator);
            int entries = dispatcher.execute("vgg villagebuildings entries 999", administrator);
            int pools = dispatcher.execute("vgg villagebuildings pools 999", administrator);
            int errors = dispatcher.execute("vgg villagebuildings errors 999", administrator);
            helper.assertTrue(
                    status == 1 && entries > 0 && pools > 0 && errors > 0,
                    "A registered village-building diagnostic command failed");
            boolean permissionDenied = false;
            try {
                dispatcher.execute(
                        "vgg villagebuildings status",
                        administrator.withPermission(1));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                permissionDenied = true;
            }
            helper.assertTrue(permissionDenied,
                    "Village-building diagnostics were available below permission level 2");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            helper.fail("Village-building diagnostic command failed: " + exception.getMessage());
        }

        helper.assertTrue(
                rawBefore.equals(accessor.villagerGuardGunners$getRawTemplates())
                        && flattenedBefore.equals(accessor.villagerGuardGunners$getTemplates())
                        && maximumSizeBefore == accessor.villagerGuardGunners$getMaxSize()
                        && definitionsBefore.equals(VillageBuildingDefinitions.reloadSnapshot())
                        && installationBefore.equals(
                                VillageBuildingPoolIntegration.installationSnapshot()),
                "Read-only village-building diagnostics mutated registry or publication state");
        helper.succeed();
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

    private static void assertNeighborSensitiveStatesSettled(
            GameTestHelper helper,
            StructureTemplate template,
            StructurePlaceSettings settings,
            BlockPos target,
            String context) {
        var level = helper.getLevel();
        Vec3i size = template.getSize();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos position = StructureTemplate.calculateRelativePosition(
                            settings, new BlockPos(x, y, z)).offset(target);
                    var state = level.getBlockState(position);
                    Block block = state.getBlock();
                    if (!(block instanceof FenceBlock)
                            && !(block instanceof IronBarsBlock)
                            && !(block instanceof WallBlock)
                            && !(block instanceof StairBlock)) {
                        continue;
                    }
                    var expected = Block.updateFromNeighbourShapes(state, level, position);
                    helper.assertTrue(
                            state.equals(expected),
                            context + " neighbor-sensitive state did not settle at "
                                    + new BlockPos(x, y, z)
                                    + ": actual=" + state + ", expected=" + expected);
                }
            }
        }
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
