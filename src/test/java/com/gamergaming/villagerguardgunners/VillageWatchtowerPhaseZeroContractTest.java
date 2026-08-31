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
                        "docs/village-watchtowers-release-matrix.md")),
                () -> assertTrue(readme.contains(
                        "docs/village-watchtowers-step-11.md")),
                () -> assertTrue(readme.contains(
                        "docs/village-watchtowers-step-12.md")));
    }

    @Test
    void stepElevenActivatesOnlyTemplatePlacement() throws IOException {
        Path resources = PROJECT_DIRECTORY.resolve("src/main/resources/data/villagerguardgunners");
        Path sources = PROJECT_DIRECTORY.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners");
        String build = read("build.gradle");
        String structure = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/structure/"
                        + "VillageWatchtowerStructure.java");
        String placement = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/structure/"
                        + "WatchtowerPlacement.java");
        String piece = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/structure/"
                        + "VillageWatchtowerPiece.java");
        String stepEleven = read("docs/village-watchtowers-step-11.md");

        assertAll(
                () -> assertTrue(Files.isRegularFile(
                        resources.resolve("worldgen/structure/watchtower.json"))),
                () -> assertTrue(Files.isRegularFile(
                        resources.resolve("worldgen/structure_set/watchtowers.json"))),
                () -> assertTrue(Files.isRegularFile(resources.resolve(
                        "tags/worldgen/biome/has_structure/watchtower.json"))),
                () -> assertFalse(Files.exists(resources.resolve("worldgen/template_pool"))),
                () -> assertTrue(Files.isRegularFile(PROJECT_DIRECTORY.resolve(
                        "src/main/templates/villagerguardgunners/watchtower.json"))),
                () -> assertFalse(Files.exists(resources.resolve("loot_tables/chests/watchtower.json"))),
                () -> assertTrue(Files.isRegularFile(sources.resolve(
                        "worldgen/structure/VillageWatchtowerStructure.java"))),
                () -> assertTrue(Files.isRegularFile(sources.resolve(
                        "worldgen/structure/VillageWatchtowerPiece.java"))),
                () -> assertTrue(Files.isRegularFile(sources.resolve(
                        "worldgen/structure/WatchtowerPlacement.java"))),
                () -> assertTrue(structure.contains("WatchtowerPlacement.findGroundY")),
                () -> assertFalse(structure.contains("afterPlace(")),
                () -> assertFalse(structure.contains("getChunk(")),
                () -> assertFalse(structure.contains("addFreshEntity")),
                () -> assertTrue(placement.contains("SAMPLE_COUNT = 9")),
                () -> assertTrue(placement.contains("MAX_SURFACE_VARIANCE = 2")),
                () -> assertTrue(placement.contains("getBaseColumn(")),
                () -> assertFalse(placement.contains("getChunk(")),
                () -> assertTrue(piece.contains("Blocks.STRUCTURE_VOID")),
                () -> assertFalse(piece.contains("addFreshEntity")),
                () -> assertTrue(build.contains("generateWatchtowerTemplate")),
                () -> assertTrue(build.contains("watchtower.nbt")),
                () -> assertTrue(stepEleven.contains("400 plain JUnit")),
                () -> assertTrue(stepEleven.contains("88 required Forge")),
                () -> assertTrue(stepEleven.contains(
                        "53dc78cf6107dc7f15d8019218d560e813153e15d4baad94408505438bd9b953")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
