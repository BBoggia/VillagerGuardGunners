package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class VillageWatchtowerTemplateContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void blueprintIsExactBoundedAndContainsOneGuardAndOneArmoryMarker() throws IOException {
        JsonObject blueprint = JsonParser.parseString(Files.readString(
                PROJECT_DIRECTORY.resolve(
                        "src/main/templates/villagerguardgunners/watchtower.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray size = blueprint.getAsJsonArray("size");
        JsonArray palette = blueprint.getAsJsonArray("palette");
        JsonArray layers = blueprint.getAsJsonArray("layers");
        Set<Character> symbols = new HashSet<>();
        Set<String> blockNames = new HashSet<>();
        JsonObject guardMarker = null;
        JsonObject armoryMarker = null;

        for (var element : palette) {
            JsonObject state = element.getAsJsonObject();
            symbols.add(state.get("symbol").getAsString().charAt(0));
            blockNames.add(state.get("name").getAsString());
            if ("G".equals(state.get("symbol").getAsString())) {
                guardMarker = state;
            } else if ("A".equals(state.get("symbol").getAsString())) {
                armoryMarker = state;
            }
        }
        JsonObject exactGuardMarker = guardMarker;
        JsonObject exactArmoryMarker = armoryMarker;

        assertAll(
                () -> assertEquals(3465, blueprint.get("dataVersion").getAsInt()),
                () -> assertEquals(9, size.get(0).getAsInt()),
                () -> assertEquals(14, size.get(1).getAsInt()),
                () -> assertEquals(9, size.get(2).getAsInt()),
                () -> assertEquals(palette.size(), symbols.size()),
                () -> assertEquals(14, layers.size()),
                () -> assertTrue(blockNames.contains("minecraft:air")),
                () -> assertTrue(blockNames.contains("minecraft:structure_void")),
                () -> assertTrue(blockNames.contains("minecraft:ladder")),
                () -> assertTrue(blockNames.contains("minecraft:stone_brick_stairs")),
                () -> assertTrue(blockNames.contains("minecraft:structure_block")),
                () -> assertTrue(exactGuardMarker != null),
                () -> assertEquals("minecraft:structure_block",
                        exactGuardMarker.get("name").getAsString()),
                () -> assertEquals("data",
                        exactGuardMarker.getAsJsonObject("properties")
                                .get("mode").getAsString()),
                () -> assertEquals("DATA",
                        exactGuardMarker.getAsJsonObject("nbt").get("mode").getAsString()),
                () -> assertEquals("guard_post:villagerguardgunners:watchtower",
                        exactGuardMarker.getAsJsonObject("nbt")
                                .get("metadata").getAsString()),
                () -> assertTrue(exactArmoryMarker != null),
                () -> assertEquals("armory:squad:villagerguardgunners:chests/watchtower_armory",
                        exactArmoryMarker.getAsJsonObject("nbt")
                                .get("metadata").getAsString()),
                () -> assertFalse(blueprint.has("entities")));

        int guardMarkerCount = 0;
        int armoryMarkerCount = 0;
        for (var layer : layers) {
            JsonArray rows = layer.getAsJsonArray();
            assertEquals(9, rows.size());
            for (var rowElement : rows) {
                String row = rowElement.getAsString();
                assertEquals(9, row.length());
                for (char symbol : row.toCharArray()) {
                    assertTrue(symbols.contains(symbol));
                    if (symbol == 'G') {
                        guardMarkerCount++;
                    } else if (symbol == 'A') {
                        armoryMarkerCount++;
                    }
                }
            }
        }
        assertEquals(1, guardMarkerCount);
        assertEquals(1, armoryMarkerCount);
        assertEquals(".SA....S.", layers.get(1).getAsJsonArray().get(2).getAsString());
        assertEquals('.', layers.get(1).getAsJsonArray().get(2).getAsString().charAt(4));
        assertEquals(".S..G..S.", layers.get(1).getAsJsonArray().get(4).getAsString());
    }

    @Test
    void generationTaskValidatesAndPackagesTheBlueprint() throws IOException {
        String build = Files.readString(
                PROJECT_DIRECTORY.resolve("build.gradle"), StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(build.contains("tasks.register('generateWatchtowerTemplate')")),
                () -> assertTrue(build.contains("inputs.file blueprint")),
                () -> assertTrue(build.contains("outputs.file template")),
                () -> assertTrue(build.contains("markerCount > 8")),
                () -> assertTrue(build.contains("blockEntityData.keySet()")),
                () -> assertTrue(build.contains("namedTag(10, 'nbt')")),
                () -> assertTrue(build.contains("processResources")),
                () -> assertTrue(build.contains("dependsOn tasks.named('generateWatchtowerTemplate')")),
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners/structures/watchtower.nbt")));
    }
}
