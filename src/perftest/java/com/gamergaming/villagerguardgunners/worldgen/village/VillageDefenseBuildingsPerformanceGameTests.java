package com.gamergaming.villagerguardgunners.worldgen.village;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyServerServices;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.mixin.accessor.StructureTemplatePoolAccessor;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Opt-in marginal startup and placement workload for the 1.5 village buildings. */
@GameTestHolder(VillagerGuardGunners.MODID + "_village_buildings_performance")
@PrefixGameTestTemplate(false)
public final class VillageDefenseBuildingsPerformanceGameTests {
    private static final String EMPTY_TEMPLATE = "villagerguardgunners/gametest_arena";
    private static final int CANDIDATE_VILLAGES = 100;
    private static final int WARMUP_TICKS = 40;
    private static final int IDLE_SAMPLE_TICKS = 200;
    private static final List<ResourceLocation> TARGET_POOLS = List.of(
            VillageBuildingPools.PLAINS_HOUSES,
            VillageBuildingPools.DESERT_HOUSES,
            VillageBuildingPools.SAVANNA_HOUSES,
            VillageBuildingPools.SNOWY_HOUSES,
            VillageBuildingPools.TAIGA_HOUSES);
    private static final String REPORT_DIRECTORY_PROPERTY =
            "villagerguardgunners.villageBuildingPerformanceReportDir";
    private static final String ARTIFACT_SHA256_PROPERTY =
            "villagerguardgunners.phase8ProductionArtifactSha256";
    private static final String HARNESS_SHA256_PROPERTY =
            "villagerguardgunners.performanceHarnessSha256";

    private VillageDefenseBuildingsPerformanceGameTests() {
    }

    @GameTest(batch = "village_buildings_100",
            templateNamespace = "villagerguardgunners_village_buildings_performance",
            template = EMPTY_TEMPLATE, timeoutTicks = 500)
    public static void passiveBuildingPlacementsFor100VillageCandidates(GameTestHelper helper) {
        String artifactSha256 = System.getProperty(ARTIFACT_SHA256_PROPERTY);
        String harnessSha256 = System.getProperty(HARNESS_SHA256_PROPERTY);
        helper.assertTrue(
                artifactSha256 != null && artifactSha256.matches("[0-9a-f]{64}"),
                "The village-building workload was not bound to a production artifact");
        helper.assertTrue(
                harnessSha256 != null && harnessSha256.matches("[0-9a-f]{64}"),
                "The village-building workload was not bound to its performance harness");

        Recording recording;
        try {
            recording = new Recording(Configuration.getConfiguration("default"));
            recording.setName("villagerguardgunners-village-buildings-100");
            recording.setToDisk(true);
            recording.setMaxSize(128L * 1024L * 1024L);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5L));
        } catch (IOException | ParseException exception) {
            helper.fail("Could not configure the village-building JFR recording: "
                    + exception.getMessage());
            return;
        }

        long[] idleSamples = new long[IDLE_SAMPLE_TICKS];
        int[] idleSampleCount = {0};
        boolean[] workloadRan = {false};
        boolean[] completed = {false};
        WorkloadEvidence[] evidence = new WorkloadEvidence[1];

        helper.onEachTick(() -> {
            if (completed[0]) {
                return;
            }
            try {
                long testTick = helper.getTick();
                if (testTick == WARMUP_TICKS - 1L) {
                    recording.start();
                }
                if (testTick == WARMUP_TICKS) {
                    evidence[0] = runPlacementWorkload(helper);
                    workloadRan[0] = true;
                    return;
                }
                if (!workloadRan[0] || idleSampleCount[0] >= IDLE_SAMPLE_TICKS) {
                    return;
                }

                long[] serverTickTimes = helper.getLevel().getServer().tickTimes;
                int previousTickIndex = Math.floorMod(
                        helper.getLevel().getServer().getTickCount() - 1,
                        serverTickTimes.length);
                idleSamples[idleSampleCount[0]++] = serverTickTimes[previousTickIndex];
                if (idleSampleCount[0] != IDLE_SAMPLE_TICKS) {
                    return;
                }

                completed[0] = true;
                int recordsAfterIdle = VillageBuildingSavedData.get(helper.getLevel()).size();
                writeReport(helper, evidence[0], recordsAfterIdle, idleSamples, recording);
                clearPlacedBuildings(helper, evidence[0].placedBounds());
                helper.succeed();
            } catch (RuntimeException | Error exception) {
                if (evidence[0] != null) {
                    clearPlacedBuildings(helper, evidence[0].placedBounds());
                }
                try {
                    recording.close();
                } catch (RuntimeException ignored) {
                    // Preserve the workload failure.
                }
                throw exception;
            }
        });
    }

    private static WorkloadEvidence runPlacementWorkload(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(
                !ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.get(),
                "The structure-only performance fixture requires Village Defense Buildings "
                        + "to remain disabled");
        List<VillageBuildingPoolEntry> definitions = List.copyOf(
                VillageBuildingDefinitions.snapshot().values());
        helper.assertTrue(definitions.size() == 25,
                "The complete 25-entry village-building snapshot was not loaded");

        Set<Long> forcedChunksBefore = Set.copyOf(level.getForcedChunks());
        List<PoolSnapshot> originalPools = captureTargetPools(helper);
        long noSelectionStart = System.nanoTime();
        int noSelectionVggSelections = 0;
        for (int index = 0; index < CANDIDATE_VILLAGES; index++) {
            StructurePoolElement selected = originalPools.get(index % originalPools.size())
                    .pool()
                    .getRandomTemplate(RandomSource.create(0x4e4f4e45L + index));
            if (selected instanceof VillageBuildingPoolElement) {
                noSelectionVggSelections++;
            }
        }
        long noSelectionNanos = System.nanoTime() - noSelectionStart;
        helper.assertTrue(noSelectionVggSelections == 0,
                "The disabled 100-candidate baseline selected a VGG building");

        long startupStart = System.nanoTime();
        VillagePoolInjectionReport startup = VillageBuildingPoolIntegration.install(
                level.registryAccess(), level.getStructureManager(), true, definitions);
        VillagePoolInjectionReport repeated = VillageBuildingPoolIntegration.install(
                level.registryAccess(), level.getStructureManager(), true, definitions);
        long startupNanos = System.nanoTime() - startupStart;
        PoolPreservation preservation = verifyPoolPreservation(helper, originalPools);

        Holder.Reference<StructureProcessorList> processors = level.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolder(ResourceKey.create(
                        Registries.PROCESSOR_LIST,
                        VillageBuildingPools.EMPTY_PROCESSORS))
                .orElseThrow();
        List<VillageBuildingPoolEntry> trainingYards = definitions.stream()
                .filter(entry -> entry.buildingType() == VillageBuildingType.TRAINING_YARD)
                .toList();
        helper.assertTrue(trainingYards.size() == 5,
                "The five passive Training Yard templates were not available");

        int recordsBefore = VillageBuildingSavedData.get(level).size();
        List<Long> placementTimes = new ArrayList<>(CANDIDATE_VILLAGES);
        List<BoundingBox> placedBounds = new ArrayList<>(CANDIDATE_VILLAGES);
        long placementStart = System.nanoTime();
        for (int index = 0; index < CANDIDATE_VILLAGES; index++) {
            VillageBuildingPoolEntry entry = trainingYards.get(index % trainingYards.size());
            StructureTemplate template = level.getStructureManager().get(entry.template())
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing performance template " + entry.template()));
            Vec3i size = template.getSize();
            BlockPos origin = helper.absolutePos(new BlockPos(2, 2 + index * 2, 2));
            BlockPos farCorner = origin.offset(
                    Math.max(0, size.getX() - 1),
                    Math.max(0, size.getY() - 1),
                    Math.max(0, size.getZ() - 1));
            helper.assertTrue(
                    level.hasChunkAt(origin) && level.hasChunkAt(farCorner),
                    "The bounded fixture attempted to place into an unloaded chunk");

            VillageBuildingPoolElement element = VillageBuildingPoolElement.create(
                    entry.entryId(),
                    entry.buildingType(),
                    entry.template(),
                    processors,
                    entry.projection());
            long onePlacementStart = System.nanoTime();
            boolean placed = element.place(
                    level.getStructureManager(),
                    level,
                    level.structureManager(),
                    level.getChunkSource().getGenerator(),
                    origin,
                    BlockPos.ZERO,
                    Rotation.NONE,
                    BoundingBox.infinite(),
                    RandomSource.create(0x564747L + index),
                    false);
            placementTimes.add(System.nanoTime() - onePlacementStart);
            helper.assertTrue(placed, "Passive placement failed at candidate " + index);
            placedBounds.add(BoundingBox.fromCorners(origin, farCorner));
        }
        long placementNanos = System.nanoTime() - placementStart;
        int recordsAfterPlacement = VillageBuildingSavedData.get(level).size();
        Set<Long> forcedChunksAfter = Set.copyOf(level.getForcedChunks());

        helper.assertTrue(
                startup.enabled()
                        && startup.requested() == 25
                        && startup.injected() == 25
                        && !startup.hasWarnings()
                        && repeated.injected() == 0
                        && repeated.alreadyPresent() == 25,
                "The startup integration workload was incomplete or non-idempotent");
        helper.assertTrue(recordsAfterPlacement - recordsBefore == CANDIDATE_VILLAGES,
                "The 100 placements did not create exactly 100 passive records");
        helper.assertTrue(forcedChunksBefore.equals(forcedChunksAfter),
                "Village placement changed the forced-chunk set");

        return new WorkloadEvidence(
                startup,
                repeated,
                noSelectionNanos,
                noSelectionVggSelections,
                preservation,
                startupNanos,
                placementNanos,
                placementTimes.stream().mapToLong(Long::longValue).toArray(),
                List.copyOf(placedBounds),
                recordsBefore,
                recordsAfterPlacement,
                forcedChunksBefore.size(),
                forcedChunksAfter.size());
    }

    private static void clearPlacedBuildings(
            GameTestHelper helper,
            List<BoundingBox> placedBounds) {
        var level = helper.getLevel();
        for (BoundingBox bounds : placedBounds) {
            for (BlockPos position : BlockPos.betweenClosed(
                    bounds.minX(),
                    bounds.minY(),
                    bounds.minZ(),
                    bounds.maxX(),
                    bounds.maxY(),
                    bounds.maxZ())) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        ArmoryResupplyServerServices.Services services =
                ArmoryResupplyServerServices.forLevel(level);
        services.loadedArmories().clear();
        services.reservations().clear();
    }

    private static List<PoolSnapshot> captureTargetPools(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.TEMPLATE_POOL);
        List<PoolSnapshot> snapshots = new ArrayList<>(TARGET_POOLS.size());
        for (ResourceLocation id : TARGET_POOLS) {
            StructureTemplatePool pool = registry.get(id);
            helper.assertTrue(pool != null,
                    "A performance target pool was unavailable: " + id);
            List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(
                    ((StructureTemplatePoolAccessor) pool)
                            .villagerGuardGunners$getRawTemplates());
            helper.assertTrue(
                    raw.stream().noneMatch(pair ->
                            pair.getFirst() instanceof VillageBuildingPoolElement),
                    "The disabled performance baseline already contained a VGG entry: " + id);
            snapshots.add(new PoolSnapshot(id, pool, List.copyOf(raw)));
        }
        return List.copyOf(snapshots);
    }

    private static PoolPreservation verifyPoolPreservation(
            GameTestHelper helper,
            List<PoolSnapshot> snapshots) {
        int preexistingEntries = 0;
        long preexistingWeight = 0L;
        for (PoolSnapshot snapshot : snapshots) {
            List<Pair<StructurePoolElement, Integer>> current =
                    ((StructureTemplatePoolAccessor) snapshot.pool())
                            .villagerGuardGunners$getRawTemplates();
            helper.assertTrue(
                    current.size() == snapshot.raw().size() + 5,
                    "The complete VGG matrix was not appended to " + snapshot.id());
            for (int index = 0; index < snapshot.raw().size(); index++) {
                Pair<StructurePoolElement, Integer> before = snapshot.raw().get(index);
                Pair<StructurePoolElement, Integer> after = current.get(index);
                helper.assertTrue(
                        before.getFirst() == after.getFirst()
                                && before.getSecond().equals(after.getSecond()),
                        "A preexisting pool entry changed at " + snapshot.id()
                                + " index " + index);
                preexistingEntries++;
                if (before.getSecond() > 0) {
                    preexistingWeight += before.getSecond();
                }
            }
        }
        return new PoolPreservation(preexistingEntries, preexistingWeight, true);
    }

    private static void writeReport(
            GameTestHelper helper,
            WorkloadEvidence evidence,
            int recordsAfterIdle,
            long[] idleSamples,
            Recording recording) {
        String reportDirectory = System.getProperty(REPORT_DIRECTORY_PROPERTY);
        helper.assertTrue(reportDirectory != null && !reportDirectory.isBlank(),
                "The village-building performance report directory was not configured");
        Path outputDirectory = Path.of(reportDirectory);
        String jfrName = "village-buildings-100.jfr";
        try {
            recording.stop();
            Files.createDirectories(outputDirectory);
            recording.dump(outputDirectory.resolve(jfrName));
        } catch (IOException | RuntimeException exception) {
            helper.fail("Could not write the village-building JFR recording: "
                    + exception.getMessage());
        } finally {
            recording.close();
        }

        long[] sortedPlacements = evidence.placementTimes().clone();
        long[] sortedIdle = idleSamples.clone();
        Arrays.sort(sortedPlacements);
        Arrays.sort(sortedIdle);
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", 3);
        report.addProperty("scenario", "village-buildings-100");
        report.addProperty("candidateVillages", CANDIDATE_VILLAGES);
        report.addProperty("noSelectionCandidates", CANDIDATE_VILLAGES);
        report.addProperty("noSelectionVggSelections", evidence.noSelectionVggSelections());
        report.addProperty("noSelectionTotalMillis",
                evidence.noSelectionNanos() / 1_000_000.0D);
        report.addProperty("selectedBuildingCandidates", CANDIDATE_VILLAGES);
        report.addProperty("simultaneousInitializationCandidates", CANDIDATE_VILLAGES);
        report.addProperty("passiveTemplates", 5);
        report.addProperty("targetPools", TARGET_POOLS.size());
        report.addProperty("preexistingPoolEntries",
                evidence.preservation().preexistingEntries());
        report.addProperty("preexistingPoolWeight",
                evidence.preservation().preexistingWeight());
        report.addProperty("preexistingEntriesPreserved",
                evidence.preservation().preserved());
        report.addProperty("startupRequestedEntries", evidence.startup().requested());
        report.addProperty("startupInjectedEntries", evidence.startup().injected());
        report.addProperty("startupWarnings", evidence.startup().hasWarnings());
        report.addProperty("repeatedInjectedEntries", evidence.repeated().injected());
        report.addProperty("repeatedAlreadyPresentEntries", evidence.repeated().alreadyPresent());
        report.addProperty("startupIntegrationMillis", evidence.startupNanos() / 1_000_000.0D);
        report.addProperty("successfulPlacements", CANDIDATE_VILLAGES);
        report.addProperty("placementTotalMillis", evidence.placementNanos() / 1_000_000.0D);
        report.addProperty("placementP50Millis", percentileMillis(sortedPlacements, 0.50D));
        report.addProperty("placementP95Millis", percentileMillis(sortedPlacements, 0.95D));
        report.addProperty("placementP99Millis", percentileMillis(sortedPlacements, 0.99D));
        report.addProperty("placementMaxMillis", sortedPlacements[sortedPlacements.length - 1]
                / 1_000_000.0D);
        report.addProperty("recordsBefore", evidence.recordsBefore());
        report.addProperty("recordsAfterPlacement", evidence.recordsAfterPlacement());
        report.addProperty("recordDelta", evidence.recordsAfterPlacement() - evidence.recordsBefore());
        report.addProperty("completedLoadedBuildings", evidence.recordsAfterPlacement());
        report.addProperty("recordsAfterIdle", recordsAfterIdle);
        report.addProperty("idleSampleTicks", idleSamples.length);
        report.addProperty("idleP50Mspt", percentileMillis(sortedIdle, 0.50D));
        report.addProperty("idleP95Mspt", percentileMillis(sortedIdle, 0.95D));
        report.addProperty("idleP99Mspt", percentileMillis(sortedIdle, 0.99D));
        report.addProperty("idleMaxMspt", sortedIdle[sortedIdle.length - 1] / 1_000_000.0D);
        report.addProperty("forcedChunksBefore", evidence.forcedChunksBefore());
        report.addProperty("forcedChunksAfter", evidence.forcedChunksAfter());
        report.addProperty("jfrProfile", jfrName);
        report.addProperty("productionArtifactSha256",
                System.getProperty(ARTIFACT_SHA256_PROPERTY));
        report.addProperty("performanceHarnessSha256",
                System.getProperty(HARNESS_SHA256_PROPERTY));
        report.addProperty("absoluteTimingGateApplied", false);
        report.addProperty("timingNote",
                "Marginal same-run evidence; compare timing only on identical hardware and JVM settings.");
        try {
            Files.writeString(
                    outputDirectory.resolve("village-buildings-100.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report)
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            helper.fail("Could not write the village-building performance report: "
                    + exception.getMessage());
        }
    }

    private static double percentileMillis(long[] sorted, double percentile) {
        int index = Math.min(
                sorted.length - 1,
                Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1));
        return sorted[index] / 1_000_000.0D;
    }

    private record WorkloadEvidence(
            VillagePoolInjectionReport startup,
            VillagePoolInjectionReport repeated,
            long noSelectionNanos,
            int noSelectionVggSelections,
            PoolPreservation preservation,
            long startupNanos,
            long placementNanos,
            long[] placementTimes,
            List<BoundingBox> placedBounds,
            int recordsBefore,
            int recordsAfterPlacement,
            int forcedChunksBefore,
            int forcedChunksAfter) {
    }

    private record PoolSnapshot(
            ResourceLocation id,
            StructureTemplatePool pool,
            List<Pair<StructurePoolElement, Integer>> raw) {
    }

    private record PoolPreservation(
            int preexistingEntries,
            long preexistingWeight,
            boolean preserved) {
    }
}
