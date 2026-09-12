package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

final class VillageTrainingYardTemplateContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));
    private static final Set<String> BIOMES =
            Set.of("plains", "desert", "savanna", "snowy", "taiga");
    private static final Set<String> UNWANTED_FUNCTIONAL_BLOCKS = Set.of(
            "minecraft:barrel",
            "minecraft:blast_furnace",
            "minecraft:brewing_stand",
            "minecraft:cartography_table",
            "minecraft:chest",
            "minecraft:composter",
            "minecraft:crafting_table",
            "minecraft:fletching_table",
            "minecraft:grindstone",
            "minecraft:lectern",
            "minecraft:loom",
            "minecraft:smithing_table",
            "minecraft:smoker",
            "minecraft:stonecutter",
            "minecraft:trapped_chest");

    @Test
    void trainingYardUsesOnlyReviewedUniqueTokenKeys() throws IOException {
        JsonObject root = blueprint();
        JsonArray commonPalette = root.getAsJsonArray("commonPalette");
        JsonObject biomes = root.getAsJsonObject("biomes");
        Set<Character> expectedTokens = Set.of(
                '.', 'A', 'F', 'J', 'L', 'P', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z', 'q');

        assertEquals(expectedTokens, usedSymbols(trainingLayers()),
                "The Training Yard token vocabulary changed without review");
        assertAll(
                () -> assertEquals("minecraft:air",
                        paletteEntry(commonPalette, ".").get("name").getAsString()),
                () -> assertEquals("minecraft:structure_void",
                        paletteEntry(commonPalette, "V").get("name").getAsString()),
                () -> assertEquals("minecraft:target",
                        paletteEntry(commonPalette, "T").get("name").getAsString()),
                () -> assertEquals("minecraft:hay_block",
                        paletteEntry(commonPalette, "Y").get("name").getAsString()),
                () -> assertEquals("y", paletteEntry(commonPalette, "Y")
                        .getAsJsonObject("properties").get("axis").getAsString()),
                () -> assertEquals("minecraft:lantern",
                        paletteEntry(commonPalette, "q").get("name").getAsString()),
                () -> assertEquals("true", paletteEntry(commonPalette, "q")
                        .getAsJsonObject("properties").get("hanging").getAsString()));

        for (String biome : BIOMES) {
            JsonArray biomePalette = biomes.getAsJsonObject(biome).getAsJsonArray("palette");
            Set<Character> definedTokens = new HashSet<>();
            for (JsonArray palette : new JsonArray[] {commonPalette, biomePalette}) {
                for (JsonElement element : palette) {
                    String token = element.getAsJsonObject().get("symbol").getAsString();
                    assertEquals(1, token.length(),
                            "Palette tokens must remain single characters in " + biome);
                    assertTrue(token.charAt(0) < 128,
                            "Palette tokens must remain plain ASCII in " + biome);
                    assertTrue(definedTokens.add(token.charAt(0)),
                            "Duplicate palette token " + token + " in " + biome);
                }
            }

            assertTrue(!definedTokens.contains('J') && !definedTokens.contains('A'),
                    "Jigsaw and anchor tokens must remain generator-owned in " + biome);
            definedTokens.add('J');
            definedTokens.add('A');
            assertTrue(definedTokens.containsAll(expectedTokens),
                    "The Training Yard uses an undefined token in " + biome);
        }
    }

    @Test
    void trainingYardUsesAnOpenDrillCourtWithCoveredFiringLineAndBackstop()
            throws IOException {
        JsonObject yard = blueprint().getAsJsonObject("buildings")
                .getAsJsonObject("training_yard");
        JsonArray layers = yard.getAsJsonArray("layers");

        assertAll(
                () -> assertEquals("[11,5,10]", yard.getAsJsonArray("size").toString()),
                () -> assertEquals(1, yard.get("weight").getAsInt()),
                () -> assertEquals(5, layers.size()),
                () -> assertEquals('J', cell(layers, 1, 5, 0)),
                () -> assertEquals('A', cell(layers, 1, 5, 3)),
                () -> assertEquals('P', cell(layers, 0, 5, 3)),
                () -> assertEquals("SSSS...SSSS", row(layers, 1, 2)),
                () -> assertEquals("..XXXXXXX..", row(layers, 3, 2)),
                () -> assertEquals("..XXXXXXX..", row(layers, 3, 4)),
                () -> assertEquals('q', cell(layers, 3, 7, 3)),
                () -> assertEquals("LWWLWWWLWWL", row(layers, 1, 9)),
                () -> assertEquals("LWWLWWWLWWL", row(layers, 2, 9)),
                () -> assertEquals("XXXXXXXXXXX", row(layers, 3, 9)),
                () -> assertEquals("SSSSSSSSSSS", row(layers, 4, 9)));

        for (int z : new int[] {1, 2, 4, 5, 6, 7, 8}) {
            assertAll(
                    () -> assertEquals('.', cell(layers, 1, 5, z)),
                    () -> assertEquals('.', cell(layers, 2, 5, z)));
        }
        for (int[] target : new int[][] {{3, 7}, {7, 7}}) {
            int x = target[0];
            int z = target[1];
            assertAll(
                    () -> assertEquals('Y', cell(layers, 1, x, z)),
                    () -> assertEquals('T', cell(layers, 2, x, z)),
                    () -> assertEquals('.', cell(layers, 3, x, z)));
        }
        for (int x : new int[] {3, 7}) {
            assertAll(
                    () -> assertEquals('F', cell(layers, 0, x, 6)),
                    () -> assertEquals('.', cell(layers, 1, x, 6)),
                    () -> assertEquals('.', cell(layers, 2, x, 6)));
        }

        String flattened = layers.toString();
        assertAll(
                () -> assertEquals(2, count(flattened, 'T')),
                () -> assertEquals(2, count(flattened, 'Y')),
                () -> assertEquals(1, count(flattened, 'q')),
                () -> assertEquals(1, count(flattened, 'A')),
                () -> assertEquals(1, count(flattened, 'J')),
                () -> assertEquals(0, count(flattened, 'C')),
                () -> assertEquals(0, count(flattened, 'd')),
                () -> assertEquals(0, count(flattened, 'D')));
    }

    @Test
    void everyBiomeProvidesRotatableShelterBeamsWithoutStorageOrVillageJobs()
            throws IOException {
        JsonObject root = blueprint();
        JsonObject biomes = root.getAsJsonObject("biomes");
        JsonArray commonPalette = root.getAsJsonArray("commonPalette");
        Set<Character> usedSymbols = usedSymbols(trainingLayers());
        assertEquals(BIOMES, biomes.keySet());

        for (String biome : BIOMES) {
            JsonArray palette = biomes.getAsJsonObject(biome).getAsJsonArray("palette");
            JsonObject crossbeam = paletteEntry(palette, "X");
            JsonObject sideBeam = paletteEntry(palette, "Z");
            JsonObject slab = paletteEntry(palette, "S");
            Map<Character, String> paletteNames = paletteNames(commonPalette, palette);

            assertAll(
                    () -> assertEquals(paletteEntry(palette, "L").get("name").getAsString(),
                            crossbeam.get("name").getAsString()),
                    () -> assertEquals(paletteEntry(palette, "L").get("name").getAsString(),
                            sideBeam.get("name").getAsString()),
                    () -> assertEquals("bottom", slab.getAsJsonObject("properties")
                            .get("type").getAsString()),
                    () -> assertTrue(usedSymbols.stream()
                            .map(paletteNames::get)
                            .filter(java.util.Objects::nonNull)
                            .noneMatch(name -> name.endsWith("_bed"))),
                    () -> assertTrue(usedSymbols.stream()
                            .map(paletteNames::get)
                            .filter(java.util.Objects::nonNull)
                            .noneMatch(UNWANTED_FUNCTIONAL_BLOCKS::contains)));

            if (!biome.equals("desert")) {
                assertAll(
                        () -> assertEquals("x", crossbeam.getAsJsonObject("properties")
                                .get("axis").getAsString()),
                        () -> assertEquals("z", sideBeam.getAsJsonObject("properties")
                                .get("axis").getAsString()));
            }
        }

        JsonObject lantern = paletteEntry(commonPalette, "q");
        assertEquals("true", lantern.getAsJsonObject("properties")
                .get("hanging").getAsString());
    }

    @Test
    void trainingYardContainsNoDetachedPlacedBlocks() throws IOException {
        JsonArray layers = trainingLayers();
        Set<Cell> occupied = new HashSet<>();
        for (int y = 0; y < layers.size(); y++) {
            JsonArray layer = layers.get(y).getAsJsonArray();
            for (int z = 0; z < layer.size(); z++) {
                String row = layer.get(z).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    if (row.charAt(x) != '.' && row.charAt(x) != 'V') {
                        occupied.add(new Cell(x, y, z));
                    }
                }
            }
        }

        Set<Cell> connected = new HashSet<>();
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        pending.add(occupied.iterator().next());
        while (!pending.isEmpty()) {
            Cell current = pending.removeFirst();
            if (!connected.add(current)) {
                continue;
            }
            for (int[] direction : new int[][] {
                    {1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
                    {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
            }) {
                Cell neighbor = new Cell(
                        current.x() + direction[0],
                        current.y() + direction[1],
                        current.z() + direction[2]);
                if (occupied.contains(neighbor) && !connected.contains(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
        }

        assertEquals(occupied, connected,
                "Every placed Training Yard block must connect to the structure");
    }

    @Test
    void trainingFixturesHaveSupportClearanceAndUnobstructedUseCells()
            throws IOException {
        JsonArray layers = trainingLayers();
        assertAll(
                () -> assertEquals('P', cell(layers, 0, 5, 3)),
                () -> assertEquals('A', cell(layers, 1, 5, 3)),
                () -> assertEquals('.', cell(layers, 2, 5, 3)),
                () -> assertEquals('P', cell(layers, 0, 4, 3)),
                () -> assertEquals('.', cell(layers, 1, 4, 3)),
                () -> assertEquals('.', cell(layers, 2, 4, 3)),
                () -> assertEquals('S', cell(layers, 4, 7, 3)),
                () -> assertEquals('q', cell(layers, 3, 7, 3)),
                () -> assertEquals('.', cell(layers, 2, 7, 3)),
                () -> assertEquals('L', cell(layers, 1, 2, 3)),
                () -> assertEquals('L', cell(layers, 2, 2, 3)),
                () -> assertEquals('Z', cell(layers, 3, 2, 3)),
                () -> assertEquals('L', cell(layers, 1, 8, 3)),
                () -> assertEquals('L', cell(layers, 2, 8, 3)),
                () -> assertEquals('Z', cell(layers, 3, 8, 3)));

        for (int z = 4; z <= 8; z++) {
            int laneZ = z;
            assertAll(
                    () -> assertEquals('.', cell(layers, 1, 5, laneZ)),
                    () -> assertEquals('.', cell(layers, 2, 5, laneZ)));
        }
    }

    private static JsonObject blueprint() throws IOException {
        return JsonParser.parseString(Files.readString(
                PROJECT.resolve("src/main/templates/villagerguardgunners/village_buildings.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonArray trainingLayers() throws IOException {
        return blueprint().getAsJsonObject("buildings")
                .getAsJsonObject("training_yard")
                .getAsJsonArray("layers");
    }

    private static JsonObject paletteEntry(JsonArray palette, String symbol) {
        return StreamSupport.stream(palette.spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .filter(entry -> symbol.equals(entry.get("symbol").getAsString()))
                .findFirst()
                .orElseThrow();
    }

    private static Map<Character, String> paletteNames(
            JsonArray commonPalette,
            JsonArray biomePalette) {
        Map<Character, String> names = new HashMap<>();
        for (JsonArray palette : new JsonArray[] {commonPalette, biomePalette}) {
            for (JsonElement element : palette) {
                JsonObject entry = element.getAsJsonObject();
                names.put(
                        entry.get("symbol").getAsString().charAt(0),
                        entry.get("name").getAsString());
            }
        }
        return names;
    }

    private static Set<Character> usedSymbols(JsonArray layers) {
        Set<Character> symbols = new HashSet<>();
        for (JsonElement layer : layers) {
            for (JsonElement row : layer.getAsJsonArray()) {
                row.getAsString().chars().forEach(value -> symbols.add((char) value));
            }
        }
        return Set.copyOf(symbols);
    }

    private static String row(JsonArray layers, int y, int z) {
        return layers.get(y).getAsJsonArray().get(z).getAsString();
    }

    private static char cell(JsonArray layers, int y, int x, int z) {
        return row(layers, y, z).charAt(x);
    }

    private static int count(String value, char expected) {
        return (int) value.chars().filter(character -> character == expected).count();
    }

    private record Cell(int x, int y, int z) {}
}
