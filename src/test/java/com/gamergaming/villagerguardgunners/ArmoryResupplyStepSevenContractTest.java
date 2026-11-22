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

final class ArmoryResupplyStepSevenContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void productionEvidenceIsPrimitiveBoundedAndOnDemand() throws IOException {
        String index = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/LoadedArmoryIndex.java");
        String controller = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/GuardArmoryResupplyController.java");

        assertAll(
                () -> assertTrue(index.contains("public synchronized Metrics metrics()")),
                () -> assertTrue(index.contains("incrementSaturated(queryCount)")),
                () -> assertTrue(index.contains("addSaturated(visitedRecordCount")),
                () -> assertFalse(index.contains("getAllEntities")),
                () -> assertFalse(index.contains("getChunk(")),
                () -> assertTrue(controller.contains("public RuntimeMetrics performanceMetrics()")),
                () -> assertTrue(controller.contains("accountPathAttempts(")),
                () -> assertTrue(controller.contains("maximumPathAttemptsPerTrip")),
                () -> assertTrue(controller.contains("HARD_MAX_PATH_ATTEMPTS")),
                () -> assertFalse(controller.contains("getAllEntities")),
                () -> assertFalse(controller.contains("BlockPos.betweenClosed")));
    }

    @Test
    void performanceMatrixProvesIdleFastPathAndPressureBounds() throws IOException {
        String harness = read(
                "src/perftest/java/com/gamergaming/villagerguardgunners/gametest/"
                        + "PhaseEightPerformanceGameTests.java");
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(harness.contains("\"armory-idle-100\", 0")),
                () -> assertTrue(harness.contains("\"armory-satisfied-100\", 100")),
                () -> assertTrue(harness.contains("\"armory-request-pressure-50\", 50")),
                () -> assertTrue(harness.contains("case ARMORY_REQUEST_PRESSURE -> 5")),
                () -> assertTrue(harness.contains("armorySourceSlotInspections")),
                () -> assertTrue(harness.contains("armoryForcedChunkCountDelta")),
                () -> assertTrue(harness.contains("activeArmoryReservationsAfterCleanup")),
                () -> assertTrue(build.contains("performanceJsonReports != 20")),
                () -> assertTrue(build.contains("performanceJfrProfiles != 20")),
                () -> assertTrue(build.contains("armoryCandidateLimit")),
                () -> assertTrue(build.contains("armorySlotLimit")),
                () -> assertTrue(build.contains("changed the forced-chunk set")),
                () -> assertTrue(build.contains("did not preserve the no-query fast path")),
                () -> assertTrue(build.contains("lacks bounded search/path/backoff evidence")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
