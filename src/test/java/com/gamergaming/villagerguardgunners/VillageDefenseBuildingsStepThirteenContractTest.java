package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefenseBuildingsStepThirteenContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void combinedPerformanceModeCoversVillageCombatAndLogisticsPressure() throws IOException {
        String build = read("build.gradle");
        String performance = read("src/perftest/java/com/gamergaming/"
                + "villagerguardgunners/worldgen/village/"
                + "VillageDefenseBuildingsPerformanceGameTests.java");
        String combatPerformance = read("src/perftest/java/com/gamergaming/"
                + "villagerguardgunners/gametest/PhaseEightPerformanceGameTests.java");

        assertAll(
                () -> assertTrue(build.contains("step13Performance")),
                () -> assertTrue(build.contains(
                        "${mod_id}_village_buildings_performance,minecraft")),
                () -> assertTrue(build.contains(
                        "VillageDefenseBuildingsPerformanceGameTests.class")),
                () -> assertTrue(build.contains("combatAndLogisticsScenarios: 20")),
                () -> assertTrue(build.contains("assembleReleaseCandidateEvidence")),
                () -> assertTrue(countOccurrences(
                        combatPerformance, "batch = \"phase8_") == 20),
                () -> assertTrue(combatPerformance.contains("\"default-v\" + VillagerGuardGunnersConfig.CONFIG_VERSION")),
                () -> assertTrue(combatPerformance.contains("resetArmoryServices(helper)")),
                () -> assertTrue(combatPerformance.contains("guardCount <= 10")),
                () -> assertTrue(combatPerformance.contains(
                        "Workload.MIXED_EXPLOSIVE_STANDOFF_SIDEARM")),
                () -> assertTrue(build.contains("report.configVersion != 21")),
                () -> assertTrue(performance.contains("noSelectionCandidates")),
                () -> assertTrue(performance.contains("noSelectionVggSelections")),
                () -> assertTrue(performance.contains(
                        "simultaneousInitializationCandidates")),
                () -> assertTrue(performance.contains("completedLoadedBuildings")),
                () -> assertTrue(performance.contains("preexistingEntriesPreserved")),
                () -> assertTrue(performance.contains("clearPlacedBuildings")),
                () -> assertTrue(performance.contains("schemaVersion\", 3")),
                () -> assertTrue(performance.contains("performanceHarnessSha256")));
    }

    @Test
    void correctnessMatrixUsesMinecraftAttachmentRulesAndIndependentRecords()
            throws IOException {
        String gameTests = read("src/gametest/java/com/gamergaming/"
                + "villagerguardgunners/worldgen/village/"
                + "VillageBuildingPoolGameTestAccess.java");
        String guide = read("docs/village-defense-buildings.md");
        String matrix = read("docs/village-defense-buildings-release-matrix.md");

        assertAll(
                () -> assertTrue(gameTests.contains("JigsawBlock.canAttach")),
                () -> assertTrue(gameTests.contains("attachmentChecks == 100")),
                () -> assertTrue(gameTests.contains("recordsBeforePlacementMatrix")),
                () -> assertTrue(guide.contains("combined Step 13 performance run")),
                () -> assertTrue(matrix.contains("VBUILD-AUTO-01")),
                () -> assertTrue(matrix.contains("VBUILD-PERF-01")),
                () -> assertTrue(matrix.contains("VBUILD-PERF-02")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }
}
