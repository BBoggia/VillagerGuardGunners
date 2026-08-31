package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class VillageWatchtowerPhaseZeroContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void developmentArtifactCannotBeMistakenForTheThirteenBeta() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                PROJECT_DIRECTORY.resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        assertEquals("1.4-dev", properties.getProperty("mod_version"));
    }

    @Test
    void baselineAndWorldGenerationBoundariesAreRecordedBeforeRegistration()
            throws IOException {
        String baseline = read("docs/village-watchtowers-phase-0.md");
        String matrix = read("docs/village-watchtowers-release-matrix.md");
        String readme = read("README.md");

        assertAll(
                () -> assertTrue(baseline.contains("`346d953`")),
                () -> assertTrue(baseline.contains(
                        "d498dabe6e87de95034a70befc92997a6a9d8b931211362471606242cf480417")),
                () -> assertTrue(baseline.contains("at most four generated guards")),
                () -> assertTrue(baseline.contains("at most eight recognized data markers")),
                () -> assertTrue(baseline.contains("no retro-generation")),
                () -> assertTrue(baseline.contains("no vanilla village-pool mutation")),
                () -> assertTrue(baseline.contains("zero post-generation scans")),
                () -> assertTrue(baseline.contains("MobSpawnType.STRUCTURE")),
                () -> assertTrue(matrix.contains("TOWER-DATA-01")),
                () -> assertTrue(matrix.contains("TOWER-PLACE-01")),
                () -> assertTrue(matrix.contains("TOWER-MARKER-01")),
                () -> assertTrue(matrix.contains("TOWER-GROUP-01")),
                () -> assertTrue(matrix.contains("TOWER-INTEGRATE-01")),
                () -> assertTrue(matrix.contains("TOWER-CFG-01")),
                () -> assertTrue(matrix.contains("TOWER-COMPAT-01")),
                () -> assertTrue(matrix.contains("TOWER-PERF-01")),
                () -> assertTrue(readme.contains("docs/village-watchtowers-phase-0.md")),
                () -> assertTrue(readme.contains(
                        "docs/village-watchtowers-release-matrix.md")));
    }

    @Test
    void phaseZeroHasNoProductionWorldGenerationSurface() throws IOException {
        Path resources = PROJECT_DIRECTORY.resolve("src/main/resources/data/villagerguardgunners");
        Path sources = PROJECT_DIRECTORY.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners");
        String build = read("build.gradle");

        assertAll(
                () -> assertFalse(Files.exists(resources.resolve("worldgen/structure"))),
                () -> assertFalse(Files.exists(resources.resolve("worldgen/structure_set"))),
                () -> assertFalse(Files.exists(resources.resolve("worldgen/template_pool"))),
                () -> assertFalse(Files.exists(resources.resolve("structures/watchtower.nbt"))),
                () -> assertFalse(Files.exists(resources.resolve("loot_tables/chests/watchtower.json"))),
                () -> assertFalse(Files.exists(sources.resolve("worldgen"))),
                () -> assertFalse(build.contains("watchtower.nbt")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
