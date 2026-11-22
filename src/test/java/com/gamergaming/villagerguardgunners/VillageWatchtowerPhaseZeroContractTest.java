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
    void candidateVersionHasMatchingReleaseNotes() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                PROJECT_DIRECTORY.resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        String version = properties.getProperty("mod_version");
        assertTrue(Files.isRegularFile(PROJECT_DIRECTORY.resolve("docs/releases/" + version + ".md")),
                "The candidate needs release notes matching its version");
        assertTrue(read("CHANGELOG.md").contains("## " + version + " - "),
                "The candidate needs a matching changelog section");
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
                () -> assertTrue(build.contains("watchtower.nbt")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
