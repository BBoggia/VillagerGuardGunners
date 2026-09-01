package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageDefenseBuildingsStepEightContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void performanceFixtureIsOptInBoundedAndArtifactBound() throws IOException {
        String harness = read(
                "src/perftest/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageDefenseBuildingsPerformanceGameTests.java");
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(harness.contains("CANDIDATE_VILLAGES = 100")),
                () -> assertTrue(harness.contains("IDLE_SAMPLE_TICKS = 200")),
                () -> assertTrue(harness.contains("VillageBuildingType.TRAINING_YARD")),
                () -> assertTrue(harness.contains("level.hasChunkAt(origin)")),
                () -> assertTrue(harness.contains("Set.copyOf(level.getForcedChunks())")),
                () -> assertTrue(harness.contains("recordsAfterPlacement - recordsBefore")),
                () -> assertTrue(harness.contains("productionArtifactSha256")),
                () -> assertTrue(harness.contains("absoluteTimingGateApplied")),
                () -> assertFalse(harness.contains("setChunkForced")),
                () -> assertFalse(harness.contains("ChunkTicket")),
                () -> assertTrue(build.contains("reports/village-building-performance")),
                () -> assertTrue(build.contains("gradleProperty(\n        'villageBuildingPerformance')")),
                () -> assertTrue(build.contains(
                        "${mod_id}_village_buildings_performance")),
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners_village_buildings_performance/structures/")),
                () -> assertTrue(build.contains(
                        "tasks.register('verifyVillageBuildingPerformanceReport')")),
                () -> assertTrue(build.contains(
                        "VillageDefenseBuildingsPerformanceGameTests.class")),
                () -> assertTrue(build.contains(
                        "a zero-test server run is not accepted")),
                () -> assertTrue(build.contains("executionSampleCount == 0L")),
                () -> assertTrue(build.contains("report.recordDelta != 100")),
                () -> assertTrue(build.contains(
                        "tasks.register('assembleVillageDefenseBuildingEvidence')")),
                () -> assertTrue(build.contains(
                        "tasks.register('verifyVillageDefenseBuildingsReleaseCandidate')")),
                () -> assertTrue(build.contains("required-before-public-release")));
    }

    @Test
    void documentationRetainsTheManualBoundary() throws IOException {
        String docs = read("docs/village-defense-buildings-step-8.md");
        String matrix = read("docs/village-defense-buildings-release-matrix.md");
        String plan = read("docs/village-defense-buildings-1.5-plan.md");
        String readme = read("README.md");

        assertAll(
                () -> assertTrue(docs.contains("100 simulated village candidates")),
                () -> assertTrue(docs.contains("not 100 naturally generated villages")),
                () -> assertTrue(docs.contains("absolute timing gate")),
                () -> assertTrue(docs.contains(
                        "verifyVillageDefenseBuildingsReleaseCandidate")),
                () -> assertTrue(matrix.contains("VBUILD-PERF-01")),
                () -> assertTrue(matrix.contains("Natural terrain and mixed-mod review")),
                () -> assertTrue(plan.contains("## Step 8 validation and release evidence")),
                () -> assertTrue(plan.contains("Steps 1 through 8 exit criteria")),
                () -> assertTrue(readme.contains("village-defense-buildings-step-8.md")),
                () -> assertFalse(readme.contains(
                        "Armories and Training Yards remain functionally dormant")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
