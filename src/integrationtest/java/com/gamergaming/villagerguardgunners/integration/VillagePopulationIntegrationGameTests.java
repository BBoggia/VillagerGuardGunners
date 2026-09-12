package com.gamergaming.villagerguardgunners.integration;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.village.*;
import com.gamergaming.villagerguardgunners.village.population.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class VillagePopulationIntegrationGameTests {
    private static final String NS = VillageIntegrationGameTests.NAMESPACE, ARENA = VillageIntegrationGameTests.ARENA;
    private static final ResourceLocation HOUSE = ResourceLocation.parse("minecraft:village/plains/houses/plains_small_house_1");

    @GameTest(templateNamespace = NS, template = ARENA, batch = "population_generation")
    public static void nativeGenerationUsesSizeAllowanceAndPersistsTheBasisWithoutLiveWorldAccess(GameTestHelper helper) {
        var settings = ModConfigs.GUARD_GUNNERS.villagePopulation;
        boolean enabled = settings.managedGeneration.get();
        boolean buildingsEnabled = ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.get();
        boolean garrisonsEnabled = ModConfigs.GUARD_GUNNERS.villageBuildings.spawnGarrisons.get();
        var templates = GuardConfig.COMMON.structuresThatSpawnGuards.get(); int count = GuardConfig.COMMON.guardSpawnInVillage.get();
        try {
            settings.managedGeneration.accept(true);
            GuardConfig.COMMON.structuresThatSpawnGuards.set(List.of(HOUSE.toString())); GuardConfig.COMMON.guardSpawnInVillage.set(2);
            for (int homes : new int[]{1, 4, 12}) {
                var fixture = new Generation(helper.getLevel(), homes, false);
                // A new start saved before any placement must retain enrollment eligibility.
                fixture.start = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(helper.getLevel()),
                        fixture.start.createTag(StructurePieceSerializationContext.fromLevel(helper.getLevel()), fixture.center), helper.getLevel().getSeed());
                try (ProbeScope policyScope = new ProbeScope()) {
                    VillagePopulationPlacement.initialize(fixture.region, fixture.start);
                    helper.assertTrue(policyScope.missingChunkRequests == 0 && policyScope.poiReads == 0,
                            "Population planning queried live chunks or POIs");
                }
                try (ProbeScope scope = new ProbeScope()) {
                    fixture.place();
                    CompoundTag saved = fixture.start.createTag(StructurePieceSerializationContext.fromLevel(helper.getLevel()), fixture.center);
                    var basis = VillagePopulationCodec.decode(saved.getCompound(VillagePopulationPlacement.START_TAG), helper.getLevel().dimension().location());
                    helper.assertTrue(fixture.entities("guardvillagers:guard") == 0 && fixture.entities("minecraft:marker") == basis.allowance(),
                            "Native guards bypassed the allowance: homes=" + homes + ", target=" + basis.target() + ", tickets=" + fixture.entities("minecraft:marker"));
                    helper.assertTrue(basis.residences() == homes && basis.target() <= 16 && basis.allowance() <= basis.target(), "Invalid generation basis");
                    int oldMaximum = settings.maximumGuards.get(); settings.maximumGuards.accept(32);
                    var reloaded = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(helper.getLevel()), saved, helper.getLevel().getSeed());
                    settings.maximumGuards.accept(oldMaximum);
                    helper.assertTrue(reloaded != null && reloaded.createTag(StructurePieceSerializationContext.fromLevel(helper.getLevel()), fixture.center)
                            .getCompound(VillagePopulationPlacement.START_TAG).equals(saved.getCompound(VillagePopulationPlacement.START_TAG)),
                            "Reload changed the frozen generation basis");
                    helper.assertTrue(scope.missingChunkRequests == 0, "Generation forced a live chunk: " + scope.firstMissingChunkTrace);
                    System.out.println("VGG_POPULATION homes=" + homes + " areas=" + basis.areas() + " target=" + basis.target() + " tickets=" + basis.allowance() + " nativePlacementPoiReads=" + scope.poiReads);
                }
            }
            ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.villageBuildings.spawnGarrisons.accept(true);
            var garrison = new Generation(helper.getLevel(), 1, false, true);
            garrison.place();
            var buildingBasis = VillagePopulationCodec.decode(garrison.start.createTag(StructurePieceSerializationContext.fromLevel(helper.getLevel()),
                    garrison.center).getCompound(VillagePopulationPlacement.START_TAG), helper.getLevel().dimension().location());
            helper.assertTrue(buildingBasis.sources().get(0).building() && buildingBasis.allowance() > 0 && garrison.entities("guardvillagers:guard") == 0
                    && garrison.entities("minecraft:marker") == 0, "Native requests in a building bypassed the garrison adapter");
            var unrelated = new Generation(helper.getLevel(), 1, true); unrelated.place();
            helper.assertTrue(unrelated.entities("guardvillagers:guard") == 2 && unrelated.entities("minecraft:marker") == 0,
                    "Unrelated structures were changed");
            var legacy = new Generation(helper.getLevel(), 1, false);
            CompoundTag old = legacy.start.createTag(StructurePieceSerializationContext.fromLevel(helper.getLevel()), legacy.center);
            old.remove(VillagePopulationPlacement.START_TAG); old.remove("villagerguardgunners:population_new");
            legacy.start = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(helper.getLevel()), old, helper.getLevel().getSeed());
            legacy.place();
            helper.assertTrue(legacy.entities("guardvillagers:guard") == 2 && legacy.entities("minecraft:marker") == 0,
                    "An existing village was enrolled retroactively");
        } finally {
            settings.managedGeneration.accept(enabled);
            ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.accept(buildingsEnabled);
            ModConfigs.GUARD_GUNNERS.villageBuildings.spawnGarrisons.accept(garrisonsEnabled);
            GuardConfig.COMMON.structuresThatSpawnGuards.set(templates); GuardConfig.COMMON.guardSpawnInVillage.set(count);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "population_default_source")
    public static void defaultIronGolemSourceKeepsNativeRequestsWithinVillageCap(GameTestHelper helper) {
        var settings = ModConfigs.GUARD_GUNNERS.villagePopulation;
        boolean enabled = settings.managedGeneration.get(); int maximum = settings.maximumGuards.get();
        var templates = GuardConfig.COMMON.structuresThatSpawnGuards.get();
        int count = GuardConfig.COMMON.guardSpawnInVillage.get();
        try {
            settings.managedGeneration.accept(true);
            GuardConfig.COMMON.structuresThatSpawnGuards.set(List.of("minecraft:village/common/iron_golem"));
            GuardConfig.COMMON.guardSpawnInVillage.set(6);
            for (int cap : new int[]{2, 16}) {
                settings.maximumGuards.accept(cap);
                var fixture = new Generation(helper.getLevel(), 13, false, false, true);
                var basis = VillagePopulationCodec.decode(VillagePopulationPlacement.initialize(fixture.region, fixture.start),
                        helper.getLevel().dimension().location());
                helper.assertTrue(basis.residences() == 13 && basis.allowance() == Math.min(cap, 6),
                        "Default spawn marker lost its allowance or inflated residential demand");
                fixture.place();
                helper.assertTrue(fixture.entities("minecraft:marker") == Math.min(cap, 6)
                                && fixture.entities("guardvillagers:guard") == 0,
                        "Default native requests were lost or bypassed the village cap");
            }
        } finally {
            settings.managedGeneration.accept(enabled); settings.maximumGuards.accept(maximum);
            GuardConfig.COMMON.structuresThatSpawnGuards.set(templates); GuardConfig.COMMON.guardSpawnInVillage.set(count);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "population_recovery", timeoutTicks = 300)
    public static void canceledInsertionRetriesOnceAndDeathDoesNotReopenCredit(GameTestHelper helper) throws Exception {
        Fixture f = new Fixture(helper);
        CancelJoin cancel = new CancelJoin(); MinecraftForge.EVENT_BUS.register(cancel);
        Marker marker = f.ticket();
        helper.runAfterDelay(290, () -> { MinecraftForge.EVENT_BUS.unregister(cancel); f.close(); });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(cancel.canceled > 0 && !marker.isRemoved() && f.data.spent(f.plan.identity().id()) == 0,
                    "Canceled insertion consumed a credit or lost its request");
            cancel.cancel = false;
        });
        helper.runAfterDelay(200, () -> {
            try {
                Guard guard = (Guard) f.level.getEntity(f.slot);
                helper.assertTrue(guard != null && marker.isRemoved() && f.data.spent(f.plan.identity().id()) == 1,
                        "Canceled request did not resume exactly once: " + VillagePopulationRuntime.pending(f.level));
                f.guards.add(guard); guard.setNoAi(true);
                VillagePopulationRuntime.synchronizeGuard(guard);
                helper.assertTrue(f.data.living(f.plan.identity().id()) == 1, "Managed resident was not counted");
                f.level.getDataStorage().set(VillagePopulationSavedData.DATA_NAME,
                        VillagePopulationSavedData.load(f.data.save(new CompoundTag()), f.level.dimension().location()));
                guard.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                var restored = VillagePopulationSavedData.get(f.level);
                helper.assertTrue(restored.living(f.plan.identity().id()) == 0 && restored.spent(f.plan.identity().id()) == 1,
                        "Confirmed death refunded a generation credit");
                f.ticket();
            } catch (Exception exception) { throw new RuntimeException(exception); }
        });
        helper.runAfterDelay(260, () -> {
            try {
                helper.assertTrue(f.level.getEntity(f.slot) == null && VillagePopulationSavedData.get(f.level).spent(f.plan.identity().id()) == 1,
                        "Repeated ticket replaced a dead guard");
            } finally { MinecraftForge.EVENT_BUS.unregister(cancel); f.close(); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "population_uncertain", timeoutTicks = 160)
    public static void durableUncertainIntentDoesNotReplaceAnUnloadedGuard(GameTestHelper helper) throws Exception {
        Fixture f = new Fixture(helper);
        VillagePopulationRuntime.register(f.level, f.plan);
        VillagePopulationJournal.get(f.level).reserve(f.slot, f.slot);
        Marker marker = f.ticket();
        helper.runAfterDelay(150, f::close);
        helper.runAfterDelay(120, () -> {
            try {
                helper.assertTrue(f.level.getEntity(f.slot) == null && !marker.isRemoved() && f.data.spent(f.plan.identity().id()) == 0,
                        "Uncertain insertion was assumed absent and replaced");
                helper.assertTrue(f.data.enabled(), "An uncertain slot disabled unrelated village accounting");
            } finally { f.close(); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "population_garrison", timeoutTicks = 120)
    public static void garrisonHonorsItsSharedShareAndSpentMembersAreNeverReplaced(GameTestHelper helper) throws Exception {
        Fixture f = new Fixture(helper);
        ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.accept(true);
        ModConfigs.GUARD_GUNNERS.villageBuildings.spawnGarrisons.accept(true);
        var piece = new VillageFootprint.Piece(f.pos, "NONE", ResourceLocation.parse("test:population_barracks"),
                new VillageFootprint.Bounds(f.pos.getX()-3, f.pos.getY()-1, f.pos.getZ()-3, f.pos.getX()+3, f.pos.getY()+3, f.pos.getZ()+3));
        UUID source = VillagePopulationPlan.sourceId(piece);
        var plan = VillagePopulationPlan.create(f.plan.identity(), List.of(f.plan.sources().get(0),
                new VillagePopulationPlan.Source(source, piece, true, 4, 0)), new VillagePopulationPlan.Settings(1, 1, 3, 2, 2), 0);
        var identity = com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity.anchored(
                UUID.randomUUID().getMostSignificantBits(), f.level.dimension().location(), ResourceLocation.parse("test:population_barracks"),
                com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType.BARRACKS, f.pos);
        var buildingData = com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingSavedData.get(f.level);
        buildingData.claimAnchor(identity);
        helper.assertTrue(VillagePopulationRuntime.bind(f.level, identity, new VillagePopulationPlacement.Binding(plan, source)), "Could not bind garrison allowance");
        helper.assertTrue(plan.quotas().get(f.source) == 0 && plan.quotas().get(source) == 1, "Generic requests took the garrison share");
        helper.runAfterDelay(110, f::close);
        helper.runAfterDelay(40, () -> {
            try {
                var result = com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingGarrisonLifecycle.populate(f.level, identity, Rotation.NONE);
                UUID guardId = com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIds.guardId(identity.buildingId(), 0);
                var entity = f.level.getEntity(guardId);
                if (entity instanceof Guard guard) f.guards.add(guard);
                helper.assertTrue(result.spawnedGuards() == 1 && entity instanceof Guard && f.data.spent(plan.identity().id()) == 1,
                        "Garrison did not respect its one-slot share: " + result);
                helper.assertTrue(f.level.getEntity(com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIds.guardId(identity.buildingId(), 1)) == null,
                        "Garrison exceeded village allowance");
                helper.assertTrue(VillagePopulationRuntime.takeSpawnTurn(f.level) && !VillagePopulationRuntime.takeSpawnTurn(f.level),
                        "Garrison and ordinary guards did not share the two-attempt tick budget");
                entity.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                var repeat = com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingGarrisonLifecycle.populate(f.level, identity, Rotation.NONE);
                helper.assertTrue(repeat.spawnedGuards() == 0 && f.level.getEntity(guardId) == null && f.data.spent(plan.identity().id()) == 1,
                        "Garrison death reopened a consumed slot");
            } finally { f.close(); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "population_existing", timeoutTicks = 120)
    public static void existingUnarmedResidentsSurviveUnloadAndRestartWithoutGenerationCredits(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        var registry = VillageRegistrySavedData.get(f.level);
        registry.observe(f.plan.identity(), VillageFootprint.of(f.plan.sources().stream().map(VillagePopulationPlan.Source::piece).toList()));
        for (int i = 0; i < 3; i++) {
            Guard guard = tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(f.level);
            guard.moveTo(f.pos.getX()+i, f.pos.getY(), f.pos.getZ()); guard.setNoAi(true); guard.setFollowing(true);
            f.level.addFreshEntity(guard); registry.affiliateGuard(guard.getUUID(), f.plan.identity().id()); f.guards.add(guard);
        }
        helper.runAfterDelay(110, f::close);
        helper.runAfterDelay(30, () -> {
            f.guards.forEach(VillagePopulationRuntime::synchronizeGuard);
            helper.assertTrue(f.data.living(f.plan.identity().id()) == 3 && f.data.plan(f.plan.identity().id()) == null,
                    "Unavailable or unarmed old residents were omitted or enrolled for generation");
            Guard original = f.guards.get(0); CompoundTag saved = new CompoundTag(); original.saveWithoutId(saved);
            original.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var restored = VillagePopulationSavedData.load(f.data.save(new CompoundTag()), f.level.dimension().location());
            f.level.getDataStorage().set(VillagePopulationSavedData.DATA_NAME, restored);
            helper.assertTrue(restored.living(f.plan.identity().id()) == 3, "Unload reduced living population");
            Guard returned = tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(f.level); returned.load(saved);
            f.level.addFreshEntity(returned); f.guards.add(returned);
        });
        helper.runAfterDelay(90, () -> {
            try {
                f.guards.stream().filter(g -> !g.isRemoved()).forEach(VillagePopulationRuntime::synchronizeGuard);
                var restored = VillagePopulationSavedData.get(f.level);
                helper.assertTrue(restored.living(f.plan.identity().id()) == 3 && restored.plan(f.plan.identity().id()) == null
                        && restored.spent(f.plan.identity().id()) == 0, "Reload duplicated a resident or issued an initial allowance");
            } finally { f.close(); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "population_conflicting_basis")
    public static void rejectedBasisCannotSealAConflictingJournal(GameTestHelper helper) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            f.data.freeze(f.plan);
            var conflicting = VillagePopulationPlan.create(f.plan.identity(), f.plan.sources(),
                    new VillagePopulationPlan.Settings(2, 2, 3, 2, 2), 0);
            helper.assertTrue(!VillagePopulationRuntime.register(f.level, conflicting), "Conflicting saved basis was accepted");
            helper.assertTrue(!java.nio.file.Files.exists(journalDirectory(f.level).resolve("village_" + f.plan.identity().id() + ".dat")),
                    "Rejected basis permanently sealed a conflicting journal");
            helper.assertTrue(VillagePopulationRuntime.register(f.level, f.plan), "Original saved basis could not recover its journal");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "population_corrupt_plan")
    public static void savedBasisCannotBypassCorruptJournal(GameTestHelper helper) throws Exception {
        checkBrokenBinding(helper, false);
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "population_missing_binding")
    public static void missingManagedBindingCannotFallBackToLegacySpawning(GameTestHelper helper) throws Exception {
        checkBrokenBinding(helper, true);
    }

    private static void checkBrokenBinding(GameTestHelper helper, boolean missing) throws Exception {
        try (Fixture f = new Fixture(helper)) {
            var buildingSource = new VillagePopulationPlan.Source(f.source, f.plan.sources().get(0).piece(), true, 1, 0);
            var plan = VillagePopulationPlan.create(f.plan.identity(), List.of(buildingSource),
                    new VillagePopulationPlan.Settings(1, 1, 3, 2, 2), 0);
            var identity = com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity.anchored(
                    UUID.randomUUID().getMostSignificantBits(), f.level.dimension().location(), ResourceLocation.parse("test:review_barracks"),
                    com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType.BARRACKS, f.pos);
            helper.assertTrue(VillagePopulationRuntime.bind(f.level, identity, new VillagePopulationPlacement.Binding(plan, f.source)),
                    "Could not establish managed building binding");
            VillagePopulationRuntime.onLevelUnload(new net.minecraftforge.event.level.LevelEvent.Unload(f.level));
            if (missing) java.nio.file.Files.delete(journalDirectory(f.level).resolve("building_" + identity.buildingId() + ".dat"));
            else java.nio.file.Files.writeString(journalDirectory(f.level).resolve("village_" + plan.identity().id() + ".dat"), "interrupted");
            boolean rejected = false;
            try { VillagePopulationRuntime.binding(f.level, identity.buildingId()); }
            catch (java.io.IOException exception) { rejected = true; }
            helper.assertTrue(rejected, "Damaged managed metadata bypassed journal validation or enabled legacy spawning");
        }
        helper.succeed();
    }

    private static java.nio.file.Path journalDirectory(ServerLevel level) {
        return net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT))
                .resolve("data/villagerguardgunners_population_journal");
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "population_invalid_claim", timeoutTicks = 70)
    public static void copiedOrTruncatedClaimCannotConsumeAnInitialCredit(GameTestHelper helper) throws Exception {
        Fixture f = new Fixture(helper);
        VillagePopulationRuntime.register(f.level, f.plan);
        for (boolean truncated : new boolean[]{false, true}) {
            Guard guard = tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(f.level);
            guard.setNoAi(true); guard.moveTo(f.pos.getX(), f.pos.getY(), f.pos.getZ());
            if (truncated) guard.setUUID(f.slot);
            VillagePopulationRuntime.mark(guard, f.plan, f.source, 0);
            if (truncated) guard.getPersistentData().getCompound(VillagePopulationRuntime.CLAIM_TAG).remove("Ordinal");
            f.guards.add(guard); f.level.addFreshEntity(guard);
        }
        helper.runAfterDelay(35, () -> {
            try {
                f.guards.forEach(VillagePopulationRuntime::synchronizeGuard);
                helper.assertTrue(f.data.spent(f.plan.identity().id()) == 0 && !VillagePopulationJournal.get(f.level).attempted(f.slot),
                        "A copied UUID or missing ordinal consumed the valid initial slot");
            } finally { f.close(); }
            helper.succeed();
        });
    }

    public static final class CancelJoin {
        boolean cancel = true; int canceled;
        @SubscribeEvent public void join(EntityJoinLevelEvent event) {
            if (cancel && event.getEntity() instanceof Guard guard && guard.getPersistentData().contains(VillagePopulationRuntime.CLAIM_TAG)) {
                event.setCanceled(true); canceled++;
            }
        }
    }
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level; final BlockPos pos; final VillagePopulationPlan plan; final UUID source, slot;
        final VillageRegistrySavedData oldRegistry; final VillagePopulationSavedData oldData, data;
        final List<Marker> markers = new ArrayList<>(); final List<Guard> guards = new ArrayList<>();
        final boolean oldBuildings = ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.get();
        final boolean oldGarrisons = ModConfigs.GUARD_GUNNERS.villageBuildings.spawnGarrisons.get();
        final boolean oldEnabled = ModConfigs.GUARD_GUNNERS.villagePopulation.managedGeneration.get();
        boolean closed;
        Fixture(GameTestHelper helper) {
            level = helper.getLevel(); pos = helper.absolutePos(new BlockPos(8, 2, 8));
            oldRegistry = VillageRegistrySavedData.get(level); oldData = VillagePopulationSavedData.get(level);
            level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, new VillageRegistrySavedData(level.dimension().location()));
            data = new VillagePopulationSavedData(level.dimension().location()); level.getDataStorage().set(VillagePopulationSavedData.DATA_NAME, data);
            VillagePopulationRuntime.onLevelUnload(new net.minecraftforge.event.level.LevelEvent.Unload(level));
            ModConfigs.GUARD_GUNNERS.villagePopulation.managedGeneration.accept(true);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                level.setBlock(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
            var piece = new VillageFootprint.Piece(pos, "NONE", HOUSE,
                    new VillageFootprint.Bounds(pos.getX()-3, pos.getY()-1, pos.getZ()-3, pos.getX()+3, pos.getY()+3, pos.getZ()+3));
            source = VillagePopulationPlan.sourceId(piece);
            var identity = new VillageIdentity(level.dimension().location(), ResourceLocation.parse("test:population_" + UUID.randomUUID()), ChunkPos.asLong(pos));
            plan = VillagePopulationPlan.create(identity, List.of(new VillagePopulationPlan.Source(source, piece, false, 2, 10)),
                    new VillagePopulationPlan.Settings(1, 1, 3, 2, 2), 0);
            slot = plan.slot(source, 0);
        }
        Marker ticket() {
            Marker marker = EntityType.MARKER.create(level);
            CompoundTag tag = new CompoundTag(); tag.put("Plan", VillagePopulationCodec.encode(plan)); tag.putUUID("Source", source); tag.putInt("Ordinal", 0);
            marker.getPersistentData().put(VillagePopulationPlacement.TICKET_TAG, tag); marker.moveTo(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5);
            markers.add(marker); level.addFreshEntity(marker); return marker;
        }
        public void close() {
            if (closed) return; closed = true;
            var live = level.getEntity(slot); if (live != null) live.discard(); guards.forEach(Guard::discard); markers.forEach(Marker::discard);
            VillagePopulationRuntime.onLevelUnload(new net.minecraftforge.event.level.LevelEvent.Unload(level));
            level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, oldRegistry); level.getDataStorage().set(VillagePopulationSavedData.DATA_NAME, oldData);
            ModConfigs.GUARD_GUNNERS.villagePopulation.managedGeneration.accept(oldEnabled);
            ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.accept(oldBuildings);
            ModConfigs.GUARD_GUNNERS.villageBuildings.spawnGarrisons.accept(oldGarrisons);
        }
    }
    private static final class Generation {
        final ServerLevel level; final ChunkPos center = new ChunkPos(24_000, 24_000);
        final List<ChunkAccess> chunks = new ArrayList<>(); final WorldGenRegion region; final BoundingBox bounds;
        StructureStart start;
        Generation(ServerLevel level, int homes, boolean unrelated) { this(level, homes, unrelated, false); }
        Generation(ServerLevel level, int homes, boolean unrelated, boolean building) {
            this(level, homes, unrelated, building, false);
        }
        Generation(ServerLevel level, int homes, boolean unrelated, boolean building, boolean nativeMarker) {
            this.level = level;
            for (int z = center.z-3; z <= center.z+3; z++) for (int x = center.x-3; x <= center.x+3; x++) {
                var chunk = new ProtoChunk(new ChunkPos(x,z), UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
                chunk.setStatus(ChunkStatus.BIOMES); chunks.add(chunk);
            }
            region = new WorldGenRegion(level, chunks, ChunkStatus.FEATURES, 3);
            var pieces = new ArrayList<StructurePiece>();
            var element = building ? com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolElement.create(
                    ResourceLocation.parse("test:population_building"), com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType.BARRACKS, HOUSE,
                    net.minecraft.core.Holder.direct(new net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList(List.of())),
                    StructureTemplatePool.Projection.RIGID)
                    : StructurePoolElement.single(HOUSE.toString()).apply(StructureTemplatePool.Projection.RIGID);
            for (int i=0; i<homes; i++) {
                BlockPos pos = center.getWorldPosition().offset(2+(i%4)*10, 64, 2+(i/4)*10);
                pieces.add(new PoolElementStructurePiece(level.getStructureManager(), element, pos, 0, Rotation.NONE,
                        element.getBoundingBox(level.getStructureManager(), pos, Rotation.NONE)));
            }
            if (nativeMarker) {
                var marker = StructurePoolElement.single("minecraft:village/common/iron_golem")
                        .apply(StructureTemplatePool.Projection.RIGID);
                BlockPos pos = center.getWorldPosition().offset(44, 64, 2);
                pieces.add(new PoolElementStructurePiece(level.getStructureManager(), marker, pos, 0, Rotation.NONE,
                        marker.getBoundingBox(level.getStructureManager(), pos, Rotation.NONE)));
            }
            bounds = BoundingBox.encapsulatingBoxes(pieces.stream().map(StructurePiece::getBoundingBox).toList()).orElseThrow();
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse(unrelated ? "minecraft:igloo" : "minecraft:village_plains"));
            start = new StructureStart(structure, center, 0, new PiecesContainer(pieces));
        }
        void place() { start.placeInChunk(region, level.structureManager().forWorldGenRegion(region), level.getChunkSource().getGenerator(), RandomSource.create(42), bounds, center); }
        long entities(String id) { return chunks.stream().map(ProtoChunk.class::cast).flatMap(c -> c.getEntities().stream()).filter(t -> t.getString("id").equals(id)).count(); }
    }
}
