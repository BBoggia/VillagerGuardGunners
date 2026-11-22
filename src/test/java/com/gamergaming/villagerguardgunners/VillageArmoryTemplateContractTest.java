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
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

final class VillageArmoryTemplateContractTest {
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
            "minecraft:fletching_table",
            "minecraft:grindstone",
            "minecraft:lectern",
            "minecraft:loom",
            "minecraft:smithing_table",
            "minecraft:smoker",
            "minecraft:stonecutter",
            "minecraft:trapped_chest");

    @Test
    void armoryUsesAReinforcedDepotPlanWithAReachableRearSupplyCounter()
            throws IOException {
        JsonObject root = blueprint();
        JsonObject armory = root.getAsJsonObject("buildings")
                .getAsJsonObject("armory");
        JsonArray layers = armory.getAsJsonArray("layers");

        assertAll(
                () -> assertEquals("[9,8,10]", armory.getAsJsonArray("size").toString()),
                () -> assertEquals(8, layers.size()),
                () -> assertEquals('J', cell(layers, 1, 4, 0)),
                () -> assertEquals('d', cell(layers, 1, 4, 2)),
                () -> assertEquals('D', cell(layers, 2, 4, 2)),
                () -> assertEquals('A', cell(layers, 1, 4, 7)),
                () -> assertEquals('P', cell(layers, 0, 4, 7)),
                () -> assertEquals('X', cell(layers, 4, 4, 1)),
                () -> assertEquals('X', cell(layers, 4, 4, 5)),
                () -> assertEquals('Z', cell(layers, 7, 4, 5)),
                () -> assertEquals('q', cell(layers, 3, 4, 5)),
                () -> assertEquals(".........",
                        layers.get(1).getAsJsonArray().get(9).getAsString()),
                () -> assertEquals(".........",
                        layers.get(2).getAsJsonArray().get(9).getAsString()),
                () -> assertEquals(".........",
                        layers.get(3).getAsJsonArray().get(9).getAsString()));

        for (int z = 1; z <= 9; z++) {
            assertEquals('Z', cell(layers, 7, 4, z),
                    "The Armory ridge must remain continuous at z=" + z);
        }
        for (int y = 4; y <= 7; y++) {
            for (int z = 1; z <= 9; z++) {
                assertFalse(
                        layers.get(y).getAsJsonArray().get(z).getAsString().contains("V"),
                        "Roof clearance must replace terrain at y=" + y + ", z=" + z);
            }
        }
        assertAll(
                () -> assertEquals('.', cell(layers, 1, 4, 1)),
                () -> assertEquals('.', cell(layers, 2, 4, 1)));
        for (int z = 3; z <= 6; z++) {
            int routeZ = z;
            assertAll(
                    () -> assertEquals('.', cell(layers, 1, 4, routeZ)),
                    () -> assertEquals('.', cell(layers, 2, 4, routeZ)));
        }

        String flattened = layers.toString();
        assertAll(
                () -> assertEquals(8, count(flattened, 'I')),
                () -> assertEquals(1, count(flattened, 'C')),
                () -> assertEquals(2, count(flattened, 'T')),
                () -> assertEquals(1, count(flattened, 'A')),
                () -> assertEquals(1, count(flattened, 'J')));
    }

    @Test
    void everyBiomeProvidesRotatableArmoryRoofBeamsWithoutMisleadingStorageOrJobs()
            throws IOException {
        JsonObject root = blueprint();
        JsonObject biomes = root.getAsJsonObject("biomes");
        assertEquals(BIOMES, biomes.keySet());
        Set<String> commonNames = StreamSupport.stream(
                        root.getAsJsonArray("commonPalette").spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .map(entry -> entry.get("name").getAsString())
                .collect(Collectors.toSet());
        assertAll(
                () -> assertTrue(commonNames.contains("minecraft:iron_bars")),
                () -> assertTrue(commonNames.stream()
                        .noneMatch(UNWANTED_FUNCTIONAL_BLOCKS::contains)));

        for (String biome : BIOMES) {
            JsonArray palette = biomes.getAsJsonObject(biome).getAsJsonArray("palette");
            JsonObject ascendingEast = paletteEntry(palette, "R");
            JsonObject ascendingWest = paletteEntry(palette, "r");
            JsonObject crossbeam = paletteEntry(palette, "X");
            JsonObject ridgeBeam = paletteEntry(palette, "Z");
            Set<String> names = StreamSupport.stream(palette.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .map(entry -> entry.get("name").getAsString())
                    .collect(Collectors.toSet());

            assertAll(
                    () -> assertTrue(ascendingEast.get("name").getAsString()
                            .endsWith("_stairs")),
                    () -> assertEquals("east", ascendingEast.getAsJsonObject("properties")
                            .get("facing").getAsString()),
                    () -> assertTrue(ascendingWest.get("name").getAsString()
                            .endsWith("_stairs")),
                    () -> assertEquals("west", ascendingWest.getAsJsonObject("properties")
                            .get("facing").getAsString()),
                    () -> assertEquals(paletteEntry(palette, "L").get("name").getAsString(),
                            crossbeam.get("name").getAsString()),
                    () -> assertEquals(paletteEntry(palette, "L").get("name").getAsString(),
                            ridgeBeam.get("name").getAsString()),
                    () -> assertTrue(names.stream().noneMatch(name -> name.endsWith("_bed"))),
                    () -> assertTrue(names.stream()
                            .noneMatch(UNWANTED_FUNCTIONAL_BLOCKS::contains)));

            if (!biome.equals("desert")) {
                assertAll(
                        () -> assertEquals("x", crossbeam.getAsJsonObject("properties")
                                .get("axis").getAsString()),
                        () -> assertEquals("z", ridgeBeam.getAsJsonObject("properties")
                                .get("axis").getAsString()));
            }
        }
    }

    @Test
    void armoryContainsNoDetachedPlacedBlocks() throws IOException {
        JsonArray layers = armoryLayers();
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
                "Every placed Armory block must connect to the structure");
    }

    @Test
    void armoryServiceFixturesHaveSupportAndPlayerClearance() throws IOException {
        JsonArray layers = armoryLayers();
        for (int[] fixture : new int[][] {{1, 1, 3}, {2, 1, 7}, {4, 1, 7}, {6, 1, 7}}) {
            int x = fixture[0];
            int y = fixture[1];
            int z = fixture[2];
            assertAll(
                    () -> assertEquals('P', cell(layers, y - 1, x, z)),
                    () -> assertEquals('.', cell(layers, y + 1, x, z)));
        }
        assertAll(
                () -> assertEquals('.', cell(layers, 1, 3, 7)),
                () -> assertEquals('.', cell(layers, 2, 3, 7)),
                () -> assertEquals('.', cell(layers, 1, 4, 6)),
                () -> assertEquals('.', cell(layers, 2, 4, 6)),
                () -> assertEquals('.', cell(layers, 1, 5, 7)),
                () -> assertEquals('.', cell(layers, 2, 5, 7)),
                () -> assertEquals('.', cell(layers, 2, 4, 5)),
                () -> assertEquals('q', cell(layers, 3, 4, 5)),
                () -> assertEquals('X', cell(layers, 4, 4, 5)));
    }

    private static JsonObject blueprint() throws IOException {
        return JsonParser.parseString(Files.readString(
                PROJECT.resolve("src/main/templates/villagerguardgunners/village_buildings.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonArray armoryLayers() throws IOException {
        return blueprint().getAsJsonObject("buildings")
                .getAsJsonObject("armory")
                .getAsJsonArray("layers");
    }

    private static JsonObject paletteEntry(JsonArray palette, String symbol) {
        return StreamSupport.stream(palette.spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .filter(entry -> symbol.equals(entry.get("symbol").getAsString()))
                .findFirst()
                .orElseThrow();
    }

    private static char cell(JsonArray layers, int y, int x, int z) {
        return layers.get(y).getAsJsonArray().get(z).getAsString().charAt(x);
    }

    private static int count(String value, char expected) {
        return (int) value.chars().filter(character -> character == expected).count();
    }

    private record Cell(int x, int y, int z) {}
}
