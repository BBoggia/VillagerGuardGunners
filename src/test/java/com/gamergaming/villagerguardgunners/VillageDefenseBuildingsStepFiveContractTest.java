package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class VillageDefenseBuildingsStepFiveContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));
    private static final Set<String> BIOMES =
            Set.of("plains", "desert", "savanna", "snowy", "taiga");
    private static final Set<String> BUILDINGS = Set.of(
            "barracks", "armory", "sentry_post", "watchtower", "training_yard");
    private static final Map<String, Integer> BUILDING_WEIGHTS = Map.of(
            "barracks", 1,
            "armory", 1,
            "sentry_post", 2,
            "watchtower", 1,
            "training_yard", 1);

    @Test
    void blueprintIsBoundedCompleteAndHasOneConnectorAndAnchorPerLayout()
            throws IOException {
        JsonObject root = JsonParser.parseString(read(
                "src/main/templates/villagerguardgunners/village_buildings.json"))
                .getAsJsonObject();
        JsonObject biomes = root.getAsJsonObject("biomes");
        JsonObject buildings = root.getAsJsonObject("buildings");

        assertAll(
                () -> assertEquals(3465, root.get("dataVersion").getAsInt()),
                () -> assertEquals(BIOMES, biomes.keySet()),
                () -> assertEquals(BUILDINGS, buildings.keySet()),
                () -> assertEquals(5, biomes.size()),
                () -> assertEquals(5, buildings.size()));

        for (String biome : BIOMES) {
            JsonObject definition = biomes.getAsJsonObject(biome);
            assertAll(
                    () -> assertEquals(
                            "minecraft:village/" + biome + "/houses",
                            definition.get("targetPool").getAsString()),
                    () -> assertEquals(
                            "minecraft:village/" + biome + "/streets",
                            definition.get("streetPool").getAsString()),
                    () -> assertTrue(definition.getAsJsonArray("palette").size() >= 8));
        }

        for (String building : BUILDINGS) {
            JsonObject definition = buildings.getAsJsonObject(building);
            JsonArray size = definition.getAsJsonArray("size");
            int width = size.get(0).getAsInt();
            int height = size.get(1).getAsInt();
            int depth = size.get(2).getAsInt();
            JsonArray layers = definition.getAsJsonArray("layers");
            int connectors = 0;
            int anchors = 0;
            Set<Character> usedSymbols = new HashSet<>();
            assertAll(
                    () -> assertTrue(width > 0 && width <= 16),
                    () -> assertTrue(height > 0 && height <= 16),
                    () -> assertTrue(depth > 0 && depth <= 16),
                    () -> assertTrue(width * height * depth <= 4096),
                    () -> assertEquals(
                            BUILDING_WEIGHTS.get(building),
                            definition.get("weight").getAsInt()),
                    () -> assertEquals(height, layers.size()));
            for (JsonElement layerElement : layers) {
                JsonArray layer = layerElement.getAsJsonArray();
                assertEquals(depth, layer.size());
                for (JsonElement rowElement : layer) {
                    String row = rowElement.getAsString();
                    assertEquals(width, row.length());
                    for (char symbol : row.toCharArray()) {
                        usedSymbols.add(symbol);
                        connectors += symbol == 'J' ? 1 : 0;
                        anchors += symbol == 'A' ? 1 : 0;
                    }
                }
            }
            assertEquals(1, connectors);
            assertEquals(1, anchors);
            assertFalse(usedSymbols.isEmpty());
        }
    }

    @Test
    void buildPipelineGeneratesAndPackagesTheCompleteMatrix() throws IOException {
        String build = read("build.gradle") + read("gradle/village-building-templates.gradle");
        String poolIds = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageBuildingPools.java");

        assertAll(
                () -> assertTrue(build.contains("tasks.register('generateVillageBuildingTemplates')")),
                () -> assertTrue(build.contains("generatedVillageBuildingTemplateDirectory")),
                () -> assertTrue(build.contains("generated != jobs.size()")),
                () -> assertTrue(build.contains("symbols.count { it == 'J' } != 1")),
                () -> assertTrue(build.contains("symbols.count { it == 'A' } != 1")),
                () -> assertTrue(build.contains("project.delete(outputDirectory)")),
                () -> assertTrue(poolIds.contains("PLAINS_BARRACKS_ID")),
                () -> assertTrue(poolIds.contains("PLAINS_INTEGRATION_PROBE_ID")),
                () -> assertFalse(Files.exists(PROJECT.resolve(
                        "src/main/resources/data/villagerguardgunners/village_buildings/"
                                + "integration_probe/plains.json"))));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
