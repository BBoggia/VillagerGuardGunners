package com.gamergaming.villagerguardgunners.integration;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.village.VillageDiscoveryRuntime;
import com.gamergaming.villagerguardgunners.village.VillageIdentity;
import com.gamergaming.villagerguardgunners.village.VillageRegistrySavedData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class VillageRegistryIntegrationGameTests {
    private static final String NS = VillageIntegrationGameTests.NAMESPACE;
    private static final String ARENA = VillageIntegrationGameTests.ARENA;

    @GameTest(templateNamespace = NS, template = ARENA, batch = "village_records_reload")
    public static void ordinaryVillagesAndAffiliationSurviveNativeSaveAndLifecycleReload(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            helper.assertTrue(!ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.get(), "Ordinary-village fixture requires buildings off");
            var a = f.install(f.left, 1);
            var b = f.install(f.right, 1);
            BlockPos home = a.getPieces().get(0).getBoundingBox().getCenter();
            f.level.getPoiManager().add(home, f.level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                    .getHolderOrThrow(PoiTypes.HOME));
            try {
                VillageDiscoveryRuntime.trackChunk(f.level, f.right.getPos().toLong());
                VillageDiscoveryRuntime.trackChunk(f.level, f.left.getPos().toLong());
                try (ProbeScope scope = new ProbeScope()) {
                    f.drain();
                    helper.assertTrue(scope.missingChunkRequests == 0 && scope.poiReads == 0,
                            "Production discovery requested uncached chunk/POI storage");
                }
                var data = VillageRegistrySavedData.get(f.level);
                helper.assertTrue(data.size() == 2 && data.get(f.id(f.left).id()).homes().contains(home),
                        "Ordinary layouts or cached homes were not published");
                var guard = MedicalIntegrationGameTests.guard(helper);
                guard.setPos(home.getX(), home.getY(), home.getZ());
                guard.setFollowing(true);
                guard.setPatrolling(true);
                try {
                    VillageDiscoveryRuntime.onEntityJoin(new EntityJoinLevelEvent(guard, f.level));
                    // Affiliation shares a four-entry queue with other loaded residents.
                    f.drain();
                    helper.assertTrue(f.id(f.left).id().equals(data.guardVillage(guard.getUUID()))
                                    && followingFlag(guard) && guard.isPatrolling() && guard.isNoAi(),
                            "Affiliation failed or changed the guard's orders/AI: village=" + data.guardVillage(guard.getUUID())
                                    + ", following=" + guard.isFollowing() + ", patrolling=" + guard.isPatrolling());
                    data.setDirty(false);
                    f.drain();
                    helper.assertTrue(!data.isDirty(), "Unchanged observations repeatedly dirty saved data");
                    var directory = java.nio.file.Files.createTempDirectory("vgg-village-registry-");
                    VillageRegistrySavedData restored;
                    try {
                        data.setDirty();
                        data.save(directory.resolve(VillageRegistrySavedData.DATA_NAME + ".dat").toFile());
                        var storage = new net.minecraft.world.level.storage.DimensionDataStorage(
                                directory.toFile(), f.level.getServer().getFixerUpper());
                        restored = storage.computeIfAbsent(tag -> VillageRegistrySavedData.load(tag, f.level.dimension().location()),
                                () -> { throw new AssertionError("Saved village file was not loaded"); }, VillageRegistrySavedData.DATA_NAME);
                    } finally {
                        java.nio.file.Files.deleteIfExists(directory.resolve(VillageRegistrySavedData.DATA_NAME + ".dat"));
                        java.nio.file.Files.deleteIfExists(directory);
                    }
                    VillageDiscoveryRuntime.onEntityLeave(new EntityLeaveLevelEvent(guard, f.level));
                    VillageDiscoveryRuntime.onChunkUnload(new ChunkEvent.Unload(f.left));
                    helper.assertTrue(!VillageDiscoveryRuntime.chunkStatus(f.level, f.left.getPos().toLong()).tracked()
                                    && data.guardCount(f.id(f.left).id()) == 1,
                            "Unloading removed durable affiliation or retained chunk work");
                    VillageDiscoveryRuntime.onLevelUnload(new LevelEvent.Unload(f.level));
                    var context = StructurePieceSerializationContext.fromLevel(f.level);
                    f.left.setStartForStructure(f.structure, StructureStart.loadStaticStart(context,
                            a.createTag(context, f.left.getPos()), f.level.getSeed()));
                    f.right.setStartForStructure(f.structure, StructureStart.loadStaticStart(context,
                            b.createTag(context, f.right.getPos()), f.level.getSeed()));
                    CompoundTag guardTag = new CompoundTag();
                    guard.saveWithoutId(guardTag);
                    var restoredGuard = tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(f.level);
                    restoredGuard.load(guardTag);
                    helper.assertTrue(restored.guardVillage(restoredGuard.getUUID()).equals(f.id(f.left).id()),
                            "Native entity NBT reload lost its saved affiliation");
                    f.level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, restored);
                    VillageDiscoveryRuntime.trackChunk(f.level, f.left.getPos().toLong());
                    VillageDiscoveryRuntime.trackChunk(f.level, f.right.getPos().toLong());
                    f.drain();
                    helper.assertTrue(restored.size() == 2 && restored.get(f.id(f.right).id()) != null
                                    && f.id(f.left).id().equals(restored.guardVillage(guard.getUUID())),
                            "Reload/order change duplicated identity or lost guard ownership");
                    System.out.println("VGG_VILLAGES native SavedData file, guard NBT and lifecycle reload: two identities, home, affiliation preserved");
                } finally { guard.discard(); }
            } finally { f.level.getPoiManager().remove(home); }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "village_records_early_join", timeoutTicks = 80)
    public static void earlyJoinWaitsForNativeEntityTracking(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        var start = f.install(f.left, 1);
        VillageDiscoveryRuntime.trackChunk(f.level, f.left.getPos().toLong());
        f.drain();
        var guard = tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(f.level);
        var position = start.getPieces().get(0).getBoundingBox().getCenter();
        guard.setNoAi(true); guard.setPos(position.getX(), position.getY(), position.getZ());
        try {
            VillageDiscoveryRuntime.onEntityJoin(new EntityJoinLevelEvent(guard, f.level));
            f.drain();
            helper.assertTrue(VillageDiscoveryRuntime.diagnostics(f.level).pendingGuards() > 0,
                    "Join request was dropped before native entity tracking became available");
            helper.assertTrue(f.level.addFreshEntity(guard), "Could not insert delayed guard");
        } catch (RuntimeException | Error exception) { guard.discard(); f.close(); throw exception; }
        helper.runAfterDelay(40, () -> {
            try {
                f.drain();
                helper.assertTrue(f.id(f.left).id().equals(VillageRegistrySavedData.get(f.level).guardVillage(guard.getUUID())),
                        "Delayed native tracking did not recover affiliation");
            } finally { guard.discard(); f.close(); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "village_records_provisional")
    public static void provisionalPopulationFootprintCanBeRefinedFromAStartReference(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var start = f.install(f.left, 4);
            var nativePiece = (PoolElementStructurePiece) start.getPieces().get(0);
            var b = nativePiece.getBoundingBox();
            var piece = new com.gamergaming.villagerguardgunners.village.VillageFootprint.Piece(nativePiece.getPosition(), "NONE",
                    ResourceLocation.parse("minecraft:village/plains/houses/plains_small_house_1"),
                    new com.gamergaming.villagerguardgunners.village.VillageFootprint.Bounds(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
            var data = VillageRegistrySavedData.get(f.level);
            data.observe(f.id(f.left), com.gamergaming.villagerguardgunners.village.VillageFootprint.of(List.of(piece)));
            f.right.setAllReferences(Map.of(f.structure,
                    new it.unimi.dsi.fastutil.longs.LongOpenHashSet(new long[]{f.left.getPos().toLong()})));
            VillageDiscoveryRuntime.trackChunk(f.level, f.right.getPos().toLong());
            try (ProbeScope scope = new ProbeScope()) {
                f.drain();
                helper.assertTrue(data.get(f.id(f.left).id()).footprint().pieces().size() == 4,
                        "A provisional population footprint prevented discovery of the full referenced layout");
                helper.assertTrue(scope.missingChunkRequests == 0 && scope.poiReads == 0, "Refinement loaded uncached world data");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "village_records_unknown")
    public static void missingStartReferencesDeferWithoutLoadingAndResumeFromLoadedMetadata(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            long missing = ChunkPos.asLong(40_000, 40_000);
            f.left.setAllReferences(Map.of(f.structure, new it.unimi.dsi.fastutil.longs.LongOpenHashSet(new long[]{missing})));
            VillageDiscoveryRuntime.trackChunk(f.level, f.left.getPos().toLong());
            try (ProbeScope scope = new ProbeScope()) {
                f.drain();
                var status = VillageDiscoveryRuntime.chunkStatus(f.level, f.left.getPos().toLong());
                helper.assertTrue(status.complete() && status.unresolvedStarts() == 1
                                && VillageRegistrySavedData.get(f.level).size() == 0
                                && scope.missingChunkRequests == 0 && scope.poiReads == 0,
                        "Unknown start was treated as absence or caused loading");
            }
            f.install(f.right, 1);
            f.left.setAllReferences(Map.of(f.structure, new it.unimi.dsi.fastutil.longs.LongOpenHashSet(
                    new long[]{f.right.getPos().toLong()})));
            VillageDiscoveryRuntime.onChunkUnload(new ChunkEvent.Unload(f.left));
            VillageDiscoveryRuntime.trackChunk(f.level, f.left.getPos().toLong());
            f.drain();
            helper.assertTrue(VillageRegistrySavedData.get(f.level).get(f.id(f.right).id()) != null,
                    "Loaded referenced metadata did not resolve the village");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "village_records_budget")
    public static void interruptedLayoutNeverPublishesAPartialVillageAndResumesWithinBudget(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.install(f.left, 128);
            f.install(f.right, 1);
            VillageDiscoveryRuntime.trackChunk(f.level, f.left.getPos().toLong());
            VillageDiscoveryRuntime.trackChunk(f.level, f.right.getPos().toLong());
            VillageDiscoveryRuntime.tick(f.level);
            helper.assertTrue(VillageRegistrySavedData.get(f.level).get(f.id(f.left).id()) == null,
                    "Partial piece cursor was published as a complete footprint");
            VillageDiscoveryRuntime.onChunkUnload(new ChunkEvent.Unload(f.left));
            f.drain();
            helper.assertTrue(VillageRegistrySavedData.get(f.level).get(f.id(f.right).id()) != null,
                    "Large/interrupted village starved an adjacent observation");
            VillageDiscoveryRuntime.trackChunk(f.level, f.left.getPos().toLong());
            f.drain();
            helper.assertTrue(VillageRegistrySavedData.get(f.level).get(f.id(f.left).id()).footprint().pieces().size() == 128,
                    "Reload did not rebuild a complete layout");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "village_records_shared")
    public static void referencingChunksShareOneBoundedLayoutCursor(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.install(f.left, 128);
            f.right.setAllReferences(Map.of(f.structure,
                    new it.unimi.dsi.fastutil.longs.LongOpenHashSet(new long[]{f.left.getPos().toLong()})));
            VillageDiscoveryRuntime.trackChunk(f.level, f.left.getPos().toLong());
            VillageDiscoveryRuntime.trackChunk(f.level, f.right.getPos().toLong());
            try (ProbeScope scope = new ProbeScope()) {
                for (int i = 0; i < 9; i++) {
                    VillageDiscoveryRuntime.tick(f.level);
                    helper.assertTrue(VillageDiscoveryRuntime.diagnostics(f.level).lastWorkUnits() <= 32,
                            "Shared discovery exceeded the tick budget");
                    if (i < 8) helper.assertTrue(VillageRegistrySavedData.get(f.level).size() == 0,
                            "Published a partial shared layout");
                }
                var record = VillageRegistrySavedData.get(f.level).get(f.id(f.left).id());
                helper.assertTrue(record != null && record.footprint().pieces().size() == 128,
                        "Reference chunks duplicated work instead of advancing the same layout");
                helper.assertTrue(scope.missingChunkRequests == 0 && scope.poiReads == 0,
                        "Shared discovery requested uncached data");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "village_records_shared_unload")
    public static void originUnloadInvalidatesSharedReferencesAndTagReloadResetsReview(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper); ProbeScope scope = new ProbeScope()) {
            f.install(f.left, 128);
            f.right.setAllReferences(Map.of(f.structure,
                    new it.unimi.dsi.fastutil.longs.LongOpenHashSet(new long[]{f.left.getPos().toLong()})));
            VillageDiscoveryRuntime.trackChunk(f.level, f.right.getPos().toLong());
            VillageDiscoveryRuntime.tick(f.level);
            // The unload notification can precede removal from the native chunk cache.
            VillageDiscoveryRuntime.onChunkUnload(new ChunkEvent.Unload(f.left));
            f.drain();
            helper.assertTrue(VillageRegistrySavedData.get(f.level).size() == 0
                            && VillageDiscoveryRuntime.chunkStatus(f.level, f.right.getPos().toLong()).unresolvedStarts() == 1,
                    "Origin unload left a referencing chunk's shared scan active");
            VillageDiscoveryRuntime.onTagsUpdated(null);
            helper.assertTrue(!VillageDiscoveryRuntime.chunkStatus(f.level, f.right.getPos().toLong()).complete(),
                    "Tag reload exposed stale completed review status");
            f.drain();
            helper.assertTrue(VillageRegistrySavedData.get(f.level).get(f.id(f.left).id()).footprint().pieces().size() == 128,
                    "Fresh tag review did not recover the currently loaded layout");
            helper.assertTrue(scope.missingChunkRequests == 0 && scope.poiReads == 0,
                    "Lifecycle recovery requested uncached world data");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "village_records_commands")
    public static void diagnosticsRequireOperatorPermissionAndDoNotLoadOrDirtyWorldData(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.install(f.left, 1);
            VillageDiscoveryRuntime.trackChunk(f.level, f.left.getPos().toLong());
            f.drain();
            var data = VillageRegistrySavedData.get(f.level);
            data.setDirty(false);
            var source = f.level.getServer().createCommandSourceStack().withLevel(f.level)
                    .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(f.left.getPos().getWorldPosition())).withPermission(2);
            var commands = f.level.getServer().getCommands();
            try (ProbeScope scope = new ProbeScope()) {
                helper.assertTrue(commands.performPrefixedCommand(source, "vgg villages status") == 1
                                && commands.performPrefixedCommand(source, "vgg villages here") == 1
                                && commands.performPrefixedCommand(source, "vgg villages list") == 1
                                && commands.performPrefixedCommand(source, "vgg villages inspect " + f.id(f.left).id()) == 1
                                && commands.performPrefixedCommand(source.withPermission(0), "vgg villages status") == 0,
                        "Diagnostics or permissions did not behave as specified");
                helper.assertTrue(!data.isDirty() && scope.missingChunkRequests == 0 && scope.poiReads == 0,
                        "Read-only diagnostics changed storage or requested chunks");
            }
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static boolean followingFlag(tallestegg.guardvillagers.entities.Guard guard) throws Exception {
        // isFollowing() also requires a connected owner; inspect the user's flag without a fake network connection.
        var field = tallestegg.guardvillagers.entities.Guard.class.getDeclaredField("FOLLOWING");
        field.setAccessible(true);
        var key = (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) field.get(null);
        return guard.getEntityData().get(key);
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final LevelChunk left, right;
        final Structure structure;
        final VillageRegistrySavedData original;
        final Map<Structure, StructureStart> leftStarts, rightStarts;
        final Map<Structure, it.unimi.dsi.fastutil.longs.LongSet> leftReferences, rightReferences;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            ChunkPos pos = new ChunkPos(helper.absolutePos(new BlockPos(8, 2, 8)));
            left = level.getChunkSource().getChunkNow(pos.x, pos.z);
            right = level.getChunkSource().getChunkNow(pos.x + 1, pos.z);
            helper.assertTrue(left != null && right != null, "Native fixture chunks are not loaded");
            structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("minecraft:village_plains"));
            leftStarts = new HashMap<>(left.getAllStarts()); rightStarts = new HashMap<>(right.getAllStarts());
            leftReferences = new HashMap<>(left.getAllReferences()); rightReferences = new HashMap<>(right.getAllReferences());
            original = VillageRegistrySavedData.get(level);
            level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, new VillageRegistrySavedData(level.dimension().location()));
            left.setAllStarts(Map.of()); right.setAllStarts(Map.of());
            left.setAllReferences(Map.of()); right.setAllReferences(Map.of());
            VillageDiscoveryRuntime.onLevelUnload(new LevelEvent.Unload(level));
        }
        VillageIdentity id(LevelChunk chunk) {
            return new VillageIdentity(level.dimension().location(), ResourceLocation.parse("minecraft:village_plains"), chunk.getPos().toLong());
        }
        StructureStart install(LevelChunk chunk, int pieces) {
            var element = StructurePoolElement.single("minecraft:village/plains/houses/plains_small_house_1")
                    .apply(StructureTemplatePool.Projection.RIGID);
            BlockPos pos = chunk.getPos().getWorldPosition().offset(2, 64, 2);
            var piece = new PoolElementStructurePiece(level.getStructureManager(), element, pos, 0, Rotation.NONE,
                    element.getBoundingBox(level.getStructureManager(), pos, Rotation.NONE));
            var start = new StructureStart(structure, chunk.getPos(), 0,
                    new PiecesContainer(java.util.Collections.nCopies(pieces, piece)));
            chunk.setStartForStructure(structure, start);
            return start;
        }
        void drain() {
            for (int i = 0; i < 200; i++) {
                VillageDiscoveryRuntime.tick(level);
                helper.assertTrue(VillageDiscoveryRuntime.diagnostics(level).lastWorkUnits() <= 32,
                        "Discovery exceeded its per-dimension work budget");
            }
        }
        @Override public void close() {
            left.setAllStarts(leftStarts); right.setAllStarts(rightStarts);
            left.setAllReferences(leftReferences); right.setAllReferences(rightReferences);
            level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, original);
            VillageDiscoveryRuntime.onLevelUnload(new LevelEvent.Unload(level));
        }
    }
}
