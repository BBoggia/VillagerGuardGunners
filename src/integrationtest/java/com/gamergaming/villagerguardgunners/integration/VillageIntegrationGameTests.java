package com.gamergaming.villagerguardgunners.integration;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.integration.mixin.SectionStorageAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.configuration.GuardConfig;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class VillageIntegrationGameTests {
    public static final String NAMESPACE = "villagerguardgunners_integration";
    public static final String ARENA = "villagerguardgunners/gametest_arena";

    @GameTest(templateNamespace = NAMESPACE, template = ARENA, timeoutTicks = 200)
    public static void completeVillageLayoutsAndStartIdentityExistBeforeChunkPlacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(!ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.get(),
                "Probe requires ordinary villages with addon buildings disabled");
        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        var context = StructurePieceSerializationContext.fromLevel(level);
        try (ProbeScope probe = new ProbeScope()) {
            for (String style : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
                Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                        .get(id("village_" + style));
                ChunkPos origin = new ChunkPos(12_000 + style.length() * 100, 12_000);
                helper.assertTrue(chunkSource.getChunkNow(origin.x, origin.z) == null,
                        "Generation probe origin unexpectedly loaded");
                StructureStart start = structure.generate(level.registryAccess(), generator,
                        generator.getBiomeSource(), chunkSource.randomState(),
                        level.getStructureManager(), level.getSeed(), origin, 0, level, ignored -> true);
                helper.assertTrue(start.isValid() && start.getPieces().size() > 1,
                        "No complete jigsaw layout for " + style);
                var encoded = start.createTag(context, origin);
                var restored = StructureStart.loadStaticStart(context, encoded, level.getSeed());
                helper.assertTrue(restored != null && restored.getChunkPos().equals(origin)
                                && restored.getStructure() == structure
                                && restored.getPieces().size() == start.getPieces().size(),
                        "Structure identity/piece count did not survive native serialization");
                // Vanilla rebuilds piece boxes from templates when loading; generation can expand them.
                // Identity must use stable placement metadata, not a hash of the original boxes.
                var reencoded = restored.createTag(context, origin);
                int changedBoxes = 0;
                var beforeChildren = encoded.getList("Children", 10);
                var afterChildren = reencoded.getList("Children", 10);
                for (int i = 0; i < beforeChildren.size(); i++) {
                    var before = beforeChildren.getCompound(i);
                    var after = afterChildren.getCompound(i);
                    if (!java.util.Objects.equals(before.get("BB"), after.get("BB"))) changedBoxes++;
                    before.remove("BB");
                    after.remove("BB");
                }
                helper.assertTrue(encoded.equals(reencoded),
                        "Stable template/position/rotation metadata changed on reload for " + style);
                var adjacent = structure.generate(level.registryAccess(), generator,
                        generator.getBiomeSource(), chunkSource.randomState(),
                        level.getStructureManager(), level.getSeed(), new ChunkPos(origin.x + 16, origin.z),
                        0, level, ignored -> true);
                helper.assertTrue(adjacent.isValid() && !adjacent.getChunkPos().equals(start.getChunkPos()),
                        "Distinct structure starts were not distinguishable");
                System.out.println("VGG_INTEGRATION layout " + style + " pieces=" + start.getPieces().size()
                        + " start=" + origin + " serialized=true rebuiltBoxes=" + changedBoxes);
            }
            helper.assertTrue(probe.missingChunkRequests == 0 && probe.poiReads == 0,
                    "Metadata generation requested absent chunks or POI reads");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NAMESPACE, template = ARENA)
    public static void cachedPoiDiscoveryDefersUnknownSectionsWithoutDiskReads(GameTestHelper helper) {
        var level = helper.getLevel();
        var pois = level.getPoiManager();
        BlockPos absent = new BlockPos(800_000, 64, 800_000);
        try (ProbeScope probe = new ProbeScope()) {
            var cached = ((SectionStorageAccess) pois).integrationCachedSection(SectionPos.asLong(absent));
            helper.assertTrue(cached == null && probe.poiReads == 0 && probe.missingChunkRequests == 0,
                    "Unknown POI observation must remain unresolved without loading");
            // Contrast with the actual convenience API: radius does not bound disk access away.
            pois.getInRange(type -> true, absent, 1, PoiManager.Occupancy.ANY).count();
            helper.assertTrue(probe.poiReads > 0, "Expected native POI range query to request uncached columns");
            System.out.println("VGG_INTEGRATION POI cached reads=0; range-query column reads=" + probe.poiReads);
        }
        helper.assertTrue(level.getChunkSource().getChunkNow(absent.getX() >> 4, absent.getZ() >> 4) == null,
                "The POI inspection unexpectedly loaded a world chunk");
        helper.succeed();
    }

    @GameTest(templateNamespace = NAMESPACE, template = ARENA, batch = "integration_discovery")
    public static void loadedVillageDiscoveryFindsStartsAndDefersUnloadedReferences(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(!ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.get(),
                "Discovery must work without addon buildings");
        ChunkPos origin = new ChunkPos(helper.absolutePos(new BlockPos(1, 2, 1)));
        var chunk = level.getChunkSource().getChunkNow(origin.x, origin.z);
        helper.assertTrue(chunk != null, "Arena chunk is not loaded");
        var oldStarts = new java.util.HashMap<>(chunk.getAllStarts());
        var oldReferences = new java.util.HashMap<>(chunk.getAllReferences());
        var structure = village(level);
        var generator = level.getChunkSource().getGenerator();
        var start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
                origin, 0, level, ignored -> true);
        helper.assertTrue(start.isValid(), "Ordinary village layout fixture is invalid");
        BlockPos home = helper.absolutePos(new BlockPos(3, 3, 3));
        var pois = level.getPoiManager();
        var homeType = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME);
        try {
            chunk.setStartForStructure(structure, start);
            var references = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
            references.add(origin.toLong());
            for (int i = 0; i < 12; i++) references.add(ChunkPos.asLong(30_000 + i, 30_000));
            chunk.setAllReferences(java.util.Map.of(structure, references));
            // Seed a real cached POI record before measuring the read-only observer.
            pois.add(home, homeType);
            try (ProbeScope probe = new ProbeScope()) {
                var observed = observeLoadedVillage(level, origin, structure, 4);
                var expected = level.dimension().location() + "/minecraft:village_plains/" + origin.toLong();
                helper.assertTrue(observed.identities().equals(java.util.Set.of(expected))
                                && observed.unresolved() && observed.referencesVisited() == 4,
                        "Discovery lost the loaded village, loaded an unknown start, or exceeded its budget");
                var missing = observeLoadedVillage(level, new ChunkPos(30_000, 30_000), structure, 4);
                helper.assertTrue(missing.unresolved() && missing.identities().isEmpty(),
                        "Unloaded origin was incorrectly reported as confirmed absence");
                var section = ((SectionStorageAccess) pois).integrationCachedSection(SectionPos.asLong(home));
                helper.assertTrue(section != null && section.isPresent()
                                && ((net.minecraft.world.entity.ai.village.poi.PoiSection) section.get())
                                .getRecords(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                                        PoiManager.Occupancy.ANY).anyMatch(record -> record.getPos().equals(home)),
                        "Cached POI observation missed the existing home");
                helper.assertTrue(probe.poiReads == 0 && probe.missingChunkRequests == 0,
                        "Loaded-only discovery requested absent chunk or POI storage");
                System.out.println("VGG_INTEGRATION ordinary loaded village found; reference budget=4; "
                        + "unknown references deferred; cached home found; chunk/POI loads=0");
            }
        } finally {
            chunk.setAllStarts(oldStarts);
            chunk.setAllReferences(oldReferences);
            pois.remove(home);
        }
        helper.succeed();
    }

    /** Minimal integration experiment, not the persistent discovery service planned for gameplay. */
    private static LoadedObservation observeLoadedVillage(ServerLevel level, ChunkPos position,
                                                           Structure structure, int referenceBudget) {
        var chunk = level.getChunkSource().getChunkNow(position.x, position.z);
        if (chunk == null) return new LoadedObservation(java.util.Set.of(), true, 0);
        var identities = new java.util.HashSet<String>();
        recordIdentity(level, structure, chunk.getStartForStructure(structure), identities);
        int visited = 0;
        boolean unresolved = false;
        for (long reference : chunk.getReferencesForStructure(structure)) {
            if (visited == referenceBudget) {
                unresolved = true;
                break;
            }
            visited++;
            var origin = new ChunkPos(reference);
            var startChunk = level.getChunkSource().getChunkNow(origin.x, origin.z);
            if (startChunk == null) {
                unresolved = true;
                continue;
            }
            var start = startChunk.getStartForStructure(structure);
            if (start == null || !start.isValid()) unresolved = true;
            else recordIdentity(level, structure, start, identities);
        }
        return new LoadedObservation(java.util.Set.copyOf(identities), unresolved, visited);
    }

    private static void recordIdentity(ServerLevel level, Structure structure, StructureStart start,
                                       java.util.Set<String> identities) {
        if (start != null && start.isValid()) {
            identities.add(level.dimension().location() + "/"
                    + level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure)
                    + "/" + start.getChunkPos().toLong());
        }
    }

    private record LoadedObservation(java.util.Set<String> identities, boolean unresolved, int referencesVisited) {}

    @GameTest(templateNamespace = NAMESPACE, template = ARENA, batch = "integration_spawn", timeoutTicks = 100)
    public static void managedTemplateGuardRequestsCanBeDeferredBeforeProtoChunkInsertion(GameTestHelper helper) {
        var level = helper.getLevel();
        String selected = "village/plains/houses/plains_small_house_1";
        String other = "village/plains/houses/plains_small_house_2";
        var oldTemplates = GuardConfig.COMMON.structuresThatSpawnGuards.get();
        int oldCount = GuardConfig.COMMON.guardSpawnInVillage.get();
        try {
            GuardConfig.COMMON.structuresThatSpawnGuards.set(List.of("minecraft:" + selected, "minecraft:" + other));
            GuardConfig.COMMON.guardSpawnInVillage.set(2);
            try (ProbeScope probe = new ProbeScope()) {
                probe.defer = (start, template) -> start.getStructure() == village(level)
                        && template.equals(id(selected));
                int deferred = placeInProtoChunks(level, selected, village(level));
                int allowed = placeInProtoChunks(level, other, village(level));
                int unrelated = placeInProtoChunks(level, selected,
                        level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id("igloo")));
                helper.assertTrue(deferred == 0 && allowed == 2 && unrelated == 2,
                        "Scoped spawn admission failed: deferred=" + deferred + " other=" + allowed
                                + " unrelated=" + unrelated + " captured=" + probe.spawns);
                helper.assertTrue(probe.spawns.size() == 6 && ProbeScope.START.get() == null
                                && ProbeScope.TEMPLATE.get() == null,
                        "Placement scopes missed upstream spawns or leaked after placement");
                System.out.println("VGG_INTEGRATION scoped native proto-chunk guards=0/2/2; requests="
                        + probe.spawns.size());
            }
        } finally {
            GuardConfig.COMMON.structuresThatSpawnGuards.set(oldTemplates);
            GuardConfig.COMMON.guardSpawnInVillage.set(oldCount);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NAMESPACE, template = ARENA, batch = "integration_spawn_failure")
    public static void placementContextRestoresAfterAnUpstreamSpawnException(GameTestHelper helper) {
        var level = helper.getLevel();
        String selected = "village/plains/houses/plains_small_house_1";
        var oldTemplates = GuardConfig.COMMON.structuresThatSpawnGuards.get();
        int oldCount = GuardConfig.COMMON.guardSpawnInVillage.get();
        var previousStart = ProbeScope.START.get();
        var previousTemplate = ProbeScope.TEMPLATE.get();
        var outerStart = new StructureStart(village(level), new ChunkPos(0, 0), 0,
                new PiecesContainer(List.of()));
        var outerTemplate = id("integration_outer_scope");
        try {
            GuardConfig.COMMON.structuresThatSpawnGuards.set(List.of("minecraft:" + selected));
            GuardConfig.COMMON.guardSpawnInVillage.set(1);
            ProbeScope.START.set(outerStart);
            ProbeScope.TEMPLATE.set(outerTemplate);
            try (ProbeScope probe = new ProbeScope()) {
                var expected = new IllegalStateException("Injected upstream spawn failure");
                probe.defer = (start, template) -> { throw expected; };
                boolean failed = false;
                try {
                    placeInProtoChunks(level, selected, village(level));
                } catch (IllegalStateException exception) {
                    if (exception != expected) throw exception;
                    failed = true;
                }
                helper.assertTrue(failed && ProbeScope.START.get() == outerStart
                                && ProbeScope.TEMPLATE.get().equals(outerTemplate),
                        "Exceptional placement did not restore the enclosing context");
                probe.defer = (start, template) -> false;
                helper.assertTrue(placeInProtoChunks(level, selected, village(level)) == 1
                                && ProbeScope.START.get() == outerStart
                                && ProbeScope.TEMPLATE.get().equals(outerTemplate),
                        "The failed placement contaminated a subsequent spawn");
                System.out.println("VGG_INTEGRATION placement failure restored nested context; next spawn succeeded");
            }
        } finally {
            GuardConfig.COMMON.structuresThatSpawnGuards.set(oldTemplates);
            GuardConfig.COMMON.guardSpawnInVillage.set(oldCount);
            if (previousStart == null) ProbeScope.START.remove();
            else ProbeScope.START.set(previousStart);
            if (previousTemplate == null) ProbeScope.TEMPLATE.remove();
            else ProbeScope.TEMPLATE.set(previousTemplate);
        }
        helper.succeed();
    }

    private static int placeInProtoChunks(ServerLevel level, String template, Structure structure) {
        ChunkPos center = new ChunkPos(24_000, 24_000);
        List<ChunkAccess> chunks = new ArrayList<>();
        for (int z = center.z - 1; z <= center.z + 1; z++) {
            for (int x = center.x - 1; x <= center.x + 1; x++) {
                var chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, level,
                        level.registryAccess().registryOrThrow(Registries.BIOME), null);
                chunk.setStatus(ChunkStatus.BIOMES);
                chunks.add(chunk);
            }
        }
        var region = new WorldGenRegion(level, chunks, ChunkStatus.FEATURES, 1);
        var templates = level.getStructureManager();
        var element = StructurePoolElement.single("minecraft:" + template)
                .apply(StructureTemplatePool.Projection.RIGID);
        BlockPos position = center.getWorldPosition().offset(2, 64, 2);
        BoundingBox bounds = element.getBoundingBox(templates, position, Rotation.NONE);
        var piece = new PoolElementStructurePiece(templates, element, position, 0, Rotation.NONE, bounds);
        var start = new StructureStart(structure, center, 0, new PiecesContainer(List.of(piece)));
        start.placeInChunk(region, level.structureManager().forWorldGenRegion(region),
                level.getChunkSource().getGenerator(), RandomSource.create(42), bounds, center);
        long solid = BlockPos.betweenClosedStream(bounds).filter(p -> !region.getBlockState(p).isAir()).count();
        if (solid == 0) throw new AssertionError("Guard admission canceled the house placement itself");
        return chunks.stream().map(ProtoChunk.class::cast).flatMap(c -> c.getEntities().stream())
                .mapToInt(tag -> tag.getString("id").equals("guardvillagers:guard") ? 1 : 0).sum();
    }

    private static Structure village(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id("village_plains"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }
}
