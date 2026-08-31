package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutContextCaptureContractTest {
    private static Path spawnDirectory;

    @BeforeAll
    static void locateSources() {
        Path project = Path.of(System.getProperty("villagerguardgunners.projectDir"));
        spawnDirectory = project.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn");
    }

    @Test
    void expensiveContextReadsArePoolGatedAndOneShot() throws IOException {
        String capture = source("NaturalGuardLoadoutContextCapture.java");
        String integration = source("NaturalGuardSpawnIntegration.java");

        assertAll(
                () -> assertTrue(capture.contains("pool.requiresStructureLookup()")),
                () -> assertTrue(capture.contains("pool.requiresBiomeLookup()")),
                () -> assertTrue(capture.contains("pool.requiresRaidLookup()")),
                () -> assertTrue(capture.contains("pool.requiresGuardTags()")),
                () -> assertTrue(capture.contains(
                        "pool.requiresGuardTags() ? guard.getTags() : Set.of()")),
                () -> assertTrue(capture.contains("pool.biomeSelectors()")),
                () -> assertTrue(capture.contains("pool.structureSelectors()")),
                () -> assertTrue(capture.contains("getStructureWithPieceAt(")),
                () -> assertTrue(capture.contains("start.isValid()")),
                () -> assertTrue(integration.contains(
                        "() -> NaturalGuardLoadoutContextCapture.capture(")),
                () -> assertFalse(capture.contains("getEntitiesOfClass")),
                () -> assertFalse(capture.contains("TickEvent")));
    }

    @Test
    void contextualSelectionUsesBoundedPassesAndStopsBeforePopulationScan()
            throws IOException {
        String selector = source("NaturalGuardLoadoutSelector.java");
        String pool = source("NaturalGuardLoadoutPool.java");

        assertAll(
                () -> assertTrue(selector.indexOf("pool.matchingWeight(capturedContext)")
                        < selector.indexOf("nearbyNaturalGunners.getAsInt()")),
                () -> assertTrue(selector.indexOf("DecisionStatus.NO_MATCHING_ENTRIES")
                        < selector.indexOf("nearbyNaturalGunners.getAsInt()")),
                () -> assertTrue(pool.contains("selectMatching(")),
                () -> assertTrue(pool.contains("for (Entry entry : entries)")),
                () -> assertFalse(selector.contains("Collectors.")),
                () -> assertFalse(selector.contains("new ArrayList")),
                () -> assertFalse(pool.contains("Collectors.")),
                () -> assertFalse(pool.contains("new ArrayList")));
    }

    private static String source(String name) throws IOException {
        return Files.readString(spawnDirectory.resolve(name), StandardCharsets.UTF_8);
    }
}
