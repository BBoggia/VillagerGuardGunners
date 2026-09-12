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

final class VillageSentryPostTemplateContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));
    private static final Set<String> BIOMES =
            Set.of("plains", "desert", "savanna", "snowy", "taiga");

    @Test
    void sentryPostUsesACompactOpenCheckpointPlan() throws IOException {
        JsonObject sentryPost = blueprint().getAsJsonObject("buildings")
                .getAsJsonObject("sentry_post");
        JsonArray layers = sentryPost.getAsJsonArray("layers");

        assertAll(
                () -> assertEquals("[7,7,8]", sentryPost.getAsJsonArray("size").toString()),
                () -> assertEquals(2, sentryPost.get("weight").getAsInt()),
                () -> assertEquals(7, layers.size()),
                () -> assertEquals('J', cell(layers, 1, 3, 0)),
                () -> assertEquals('.', cell(layers, 1, 3, 3)),
                () -> assertEquals('A', cell(layers, 1, 3, 4)),
                () -> assertEquals('P', cell(layers, 0, 3, 4)),
                () -> assertEquals("LH...HL",
                        layers.get(1).getAsJsonArray().get(2).getAsString()),
                () -> assertEquals("W.....W",
                        layers.get(1).getAsJsonArray().get(3).getAsString()),
                () -> assertEquals("H..A..H",
                        layers.get(1).getAsJsonArray().get(4).getAsString()),
                () -> assertEquals("WC...nW",
                        layers.get(1).getAsJsonArray().get(5).getAsString()),
                () -> assertEquals(".......",
                        layers.get(2).getAsJsonArray().get(3).getAsString()),
                () -> assertEquals(".......",
                        layers.get(2).getAsJsonArray().get(4).getAsString()),
                () -> assertEquals("L.....L",
                        layers.get(3).getAsJsonArray().get(2).getAsString()),
                () -> assertEquals(".......",
                        layers.get(3).getAsJsonArray().get(3).getAsString()),
                () -> assertEquals('q', cell(layers, 3, 3, 5)),
                () -> assertEquals('B', cell(layers, 4, 3, 5)),
                () -> assertEquals("LWGWGWL",
                        layers.get(2).getAsJsonArray().get(6).getAsString()),
                () -> assertEquals(".......",
                        layers.get(1).getAsJsonArray().get(7).getAsString()),
                () -> assertEquals(".......",
                        layers.get(2).getAsJsonArray().get(7).getAsString()),
                () -> assertEquals(".......",
                        layers.get(3).getAsJsonArray().get(7).getAsString()));

        for (int z = 1; z <= 7; z++) {
            assertEquals('Z', cell(layers, 6, 3, z),
                    "The Sentry Post ridge must remain continuous at z=" + z);
        }
        for (int y = 4; y <= 6; y++) {
            for (int z = 1; z <= 7; z++) {
                assertFalse(
                        layers.get(y).getAsJsonArray().get(z).getAsString().contains("V"),
                        "Roof clearance must replace terrain at y=" + y + ", z=" + z);
            }
        }
        assertAll(
                () -> assertEquals('.', cell(layers, 1, 3, 1)),
                () -> assertEquals('.', cell(layers, 2, 3, 1)),
                () -> assertEquals('.', cell(layers, 1, 3, 2)),
                () -> assertEquals('.', cell(layers, 2, 3, 2)),
                () -> assertEquals('.', cell(layers, 1, 3, 3)),
                () -> assertEquals('.', cell(layers, 2, 3, 3)));

        String flattened = layers.toString();
        assertAll(
                () -> assertEquals(2, count(flattened, 'G')),
                () -> assertEquals(1, count(flattened, 'q')),
                () -> assertEquals(4, count(flattened, 'H')),
                () -> assertEquals(1, count(flattened, 'A')),
                () -> assertEquals(1, count(flattened, 'J')),
                () -> assertEquals(1, count(flattened, 'n')),
                () -> assertEquals(0, count(flattened, 'd')),
                () -> assertEquals(0, count(flattened, 'D')),
                () -> assertEquals(1, count(flattened, 'C')),
                () -> assertEquals(0, count(flattened, 'T')));
    }

    @Test
    void everyBiomeProvidesCorrectlyOrientedSentryPostRoofBeams() throws IOException {
        JsonObject biomes = blueprint().getAsJsonObject("biomes");
        assertEquals(BIOMES, biomes.keySet());

        for (String biome : BIOMES) {
            JsonArray palette = biomes.getAsJsonObject(biome).getAsJsonArray("palette");
            JsonObject ascendingEast = paletteEntry(palette, "R");
            JsonObject ascendingWest = paletteEntry(palette, "r");
            JsonObject seat = paletteEntry(palette, "n");
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
                    () -> assertTrue(seat.get("name").getAsString().endsWith("_stairs")),
                    () -> assertEquals("south", seat.getAsJsonObject("properties")
                            .get("facing").getAsString()),
                    () -> assertEquals(paletteEntry(palette, "L").get("name").getAsString(),
                            crossbeam.get("name").getAsString()),
                    () -> assertEquals(paletteEntry(palette, "L").get("name").getAsString(),
                            ridgeBeam.get("name").getAsString()),
                    () -> assertTrue(names.stream().noneMatch(name -> name.endsWith("_bed"))));

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
    void sentryPostContainsNoDetachedPlacedBlocks() throws IOException {
        JsonArray layers = sentryLayers();
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
                "Every placed Sentry Post block must connect to the structure");
    }

    @Test
    void sentryCellHasCoverVisibilityAndLanternSupport() throws IOException {
        JsonArray layers = sentryLayers();
        assertAll(
                () -> assertEquals('P', cell(layers, 0, 3, 4)),
                () -> assertEquals('A', cell(layers, 1, 3, 4)),
                () -> assertEquals('.', cell(layers, 2, 3, 4)),
                () -> assertEquals('W', cell(layers, 1, 0, 3)),
                () -> assertEquals('.', cell(layers, 2, 0, 3)),
                () -> assertEquals('.', cell(layers, 3, 0, 3)),
                () -> assertEquals('W', cell(layers, 1, 6, 3)),
                () -> assertEquals('.', cell(layers, 2, 6, 3)),
                () -> assertEquals('.', cell(layers, 3, 6, 3)),
                () -> assertEquals('H', cell(layers, 1, 0, 4)),
                () -> assertEquals('H', cell(layers, 1, 6, 4)),
                () -> assertEquals('.', cell(layers, 2, 3, 3)),
                () -> assertEquals('.', cell(layers, 3, 3, 3)),
                () -> assertEquals('q', cell(layers, 3, 3, 5)),
                () -> assertEquals('B', cell(layers, 4, 3, 5)),
                () -> assertEquals('C', cell(layers, 1, 1, 5)),
                () -> assertEquals('n', cell(layers, 1, 5, 5)),
                () -> assertEquals('W', cell(layers, 1, 3, 6)),
                () -> assertEquals('G', cell(layers, 2, 2, 6)),
                () -> assertEquals('G', cell(layers, 2, 4, 6)));
    }

    @Test
    void sentryGarrisonUsesItsPositionBoundRole() throws IOException {
        JsonObject group = JsonParser.parseString(Files.readString(
                PROJECT.resolve("src/main/resources/data/villagerguardgunners/"
                        + "guard_post_groups/village_sentry_post.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        for (JsonElement variantElement : group.getAsJsonArray("variants")) {
            JsonObject member = variantElement.getAsJsonObject()
                    .getAsJsonArray("members").get(0).getAsJsonObject();
            assertEquals("villagerguardgunners:sentry", member.get("role").getAsString());
        }

        JsonObject role = JsonParser.parseString(Files.readString(
                PROJECT.resolve("src/main/resources/data/villagerguardgunners/"
                        + "guard_roles/sentry.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertAll(
                () -> assertEquals("villagerguardgunners:rifleman",
                        role.getAsJsonArray("inherits").get(0).getAsString()),
                () -> assertEquals("patrol_bound",
                        role.getAsJsonObject("orders").get("movement").getAsString()));
    }

    private static JsonObject blueprint() throws IOException {
        return JsonParser.parseString(Files.readString(
                PROJECT.resolve("src/main/templates/villagerguardgunners/village_buildings.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonArray sentryLayers() throws IOException {
        return blueprint().getAsJsonObject("buildings")
                .getAsJsonObject("sentry_post")
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
