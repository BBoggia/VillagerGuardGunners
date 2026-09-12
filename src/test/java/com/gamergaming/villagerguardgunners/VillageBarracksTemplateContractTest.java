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

final class VillageBarracksTemplateContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));
    private static final Set<String> BIOMES =
            Set.of("plains", "desert", "savanna", "snowy", "taiga");
    private static final Set<String> UNWANTED_FUNCTIONAL_BLOCKS = Set.of(
            "minecraft:blast_furnace",
            "minecraft:brewing_stand",
            "minecraft:cartography_table",
            "minecraft:composter",
            "minecraft:fletching_table",
            "minecraft:grindstone",
            "minecraft:lectern",
            "minecraft:loom",
            "minecraft:smithing_table",
            "minecraft:smoker",
            "minecraft:stonecutter");

    @Test
    void barracksUsesAStagedLodgePlanWithAProtectedCentralMusterArea()
            throws IOException {
        JsonObject root = blueprint();
        JsonObject barracks = root.getAsJsonObject("buildings")
                .getAsJsonObject("barracks");
        JsonArray layers = barracks.getAsJsonArray("layers");

        assertAll(
                () -> assertEquals("[13,10,11]", barracks.getAsJsonArray("size").toString()),
                () -> assertEquals(10, layers.size()),
                () -> assertEquals('J', cell(layers, 1, 6, 0)),
                () -> assertEquals('d', cell(layers, 1, 6, 2)),
                () -> assertEquals('D', cell(layers, 2, 6, 2)),
                () -> assertEquals('A', cell(layers, 1, 6, 6)),
                () -> assertEquals('q', cell(layers, 4, 6, 3)),
                () -> assertEquals('S', cell(layers, 4, 0, 5)),
                () -> assertEquals('R', cell(layers, 5, 2, 5)),
                () -> assertEquals('r', cell(layers, 5, 10, 5)),
                () -> assertEquals('P', cell(layers, 0, 6, 4)),
                () -> assertEquals('X', cell(layers, 4, 6, 2)),
                () -> assertEquals('X', cell(layers, 4, 6, 9)),
                () -> assertEquals('X', cell(layers, 5, 6, 3)),
                () -> assertEquals('Z', cell(layers, 8, 6, 5)),
                () -> assertEquals('S', cell(layers, 9, 6, 5)),
                () -> assertEquals(".............",
                        layers.get(1).getAsJsonArray().get(10).getAsString()),
                () -> assertEquals(".............",
                        layers.get(2).getAsJsonArray().get(10).getAsString()),
                () -> assertEquals(".............",
                        layers.get(3).getAsJsonArray().get(10).getAsString()));

        for (int y = 5; y <= 9; y++) {
            for (int z = 1; z <= 10; z++) {
                assertFalse(
                        layers.get(y).getAsJsonArray().get(z).getAsString().contains("V"),
                        "Roof clearance must replace terrain at y=" + y + ", z=" + z);
            }
        }
        for (int z = 1; z <= 10; z++) {
            assertEquals('Z', cell(layers, 8, 6, z),
                    "The ridge beam must close and support the roof at z=" + z);
        }

        for (int z = 3; z <= 5; z++) {
            int routeZ = z;
            assertAll(
                    () -> assertEquals('.', cell(layers, 1, 6, routeZ)),
                    () -> assertEquals('.', cell(layers, 2, 6, routeZ)));
        }
        for (int[] guardCell : new int[][] {
                {6, 6}, {5, 6}, {7, 6}, {6, 5}
        }) {
            int x = guardCell[0];
            int z = guardCell[1];
            assertAll(
                    () -> assertEquals('P', cell(layers, 0, x, z)),
                    () -> assertTrue(".A".indexOf(cell(layers, 1, x, z)) >= 0),
                    () -> assertEquals('.', cell(layers, 2, x, z)));
        }

        String flattened = layers.toString();
        assertAll(
                () -> assertEquals(8, count(flattened, 'K')),
                () -> assertEquals(1, count(flattened, 'C')),
                () -> assertEquals(1, count(flattened, 'A')),
                () -> assertEquals(1, count(flattened, 'J')));
    }

    @Test
    void everyBiomeProvidesRotatableBarracksRoofStatesWithoutVillageJobsOrBeds()
            throws IOException {
        JsonObject biomes = blueprint().getAsJsonObject("biomes");
        assertEquals(BIOMES, biomes.keySet());

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
                    () -> assertTrue(ascendingEast.get("name").getAsString().endsWith("_stairs")),
                    () -> assertEquals("east", ascendingEast.getAsJsonObject("properties")
                            .get("facing").getAsString()),
                    () -> assertTrue(ascendingWest.get("name").getAsString().endsWith("_stairs")),
                    () -> assertEquals("west", ascendingWest.getAsJsonObject("properties")
                            .get("facing").getAsString()),
                    () -> assertEquals(paletteEntry(palette, "L").get("name").getAsString(),
                            crossbeam.get("name").getAsString()),
                    () -> assertEquals(paletteEntry(palette, "L").get("name").getAsString(),
                            ridgeBeam.get("name").getAsString()),
                    () -> assertTrue(names.stream().noneMatch(name -> name.endsWith("_bed"))),
                    () -> assertTrue(names.stream().noneMatch(UNWANTED_FUNCTIONAL_BLOCKS::contains)));

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
    void barracksContainsNoDetachedPlacedBlocks() throws IOException {
        JsonArray layers = blueprint().getAsJsonObject("buildings")
                .getAsJsonObject("barracks")
                .getAsJsonArray("layers");
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
                "Every placed Barracks block must connect to the structure");
    }

    @Test
    void barracksFurnitureHasSupportAndUsableClearance() throws IOException {
        JsonArray layers = blueprint().getAsJsonObject("buildings")
                .getAsJsonObject("barracks")
                .getAsJsonArray("layers");
        int carpetCells = 0;
        for (int y = 0; y < layers.size(); y++) {
            JsonArray layer = layers.get(y).getAsJsonArray();
            for (int z = 0; z < layer.size(); z++) {
                String row = layer.get(z).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    if (row.charAt(x) == 'K') {
                        assertTrue(y > 0, "Carpet cannot occupy the foundation layer");
                        assertEquals('P', cell(layers, y - 1, x, z),
                                "Every bedroll carpet cell needs a finished floor");
                        assertEquals('.', cell(layers, y + 1, x, z),
                                "Every bedroll carpet cell needs head clearance");
                        carpetCells++;
                    }
                }
            }
        }

        assertEquals(8, carpetCells);
        assertAll(
                () -> assertEquals('P', cell(layers, 0, 2, 7)),
                () -> assertEquals('C', cell(layers, 1, 2, 7)),
                () -> assertEquals('.', cell(layers, 2, 2, 7)),
                () -> assertEquals('.', cell(layers, 3, 6, 3)),
                () -> assertEquals('q', cell(layers, 4, 6, 3)),
                () -> assertEquals('X', cell(layers, 5, 6, 3)));
    }

    private static JsonObject blueprint() throws IOException {
        return JsonParser.parseString(Files.readString(
                PROJECT.resolve("src/main/templates/villagerguardgunners/village_buildings.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
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
