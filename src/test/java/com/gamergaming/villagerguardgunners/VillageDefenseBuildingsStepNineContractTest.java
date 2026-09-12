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
import org.junit.jupiter.api.Test;

final class VillageDefenseBuildingsStepNineContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void integratedWatchtowerKeepsSupportedStairRouteAndUpperDeck() throws IOException {
        JsonObject root = JsonParser.parseString(read(
                "src/main/templates/villagerguardgunners/village_buildings.json"))
                .getAsJsonObject();
        JsonObject tower = root.getAsJsonObject("buildings").getAsJsonObject("watchtower");
        JsonArray size = tower.getAsJsonArray("size");
        JsonArray layers = tower.getAsJsonArray("layers");

        assertAll(
                () -> assertEquals(9, size.get(0).getAsInt()),
                () -> assertEquals(13, size.get(1).getAsInt()),
                () -> assertEquals(10, size.get(2).getAsInt()),
                () -> assertEquals(13, layers.size()));
        Object[][] route = {
            {4, 0, 7, 'P', 'A'},
            {4, 1, 6, 'N', '.'},
            {4, 2, 5, 'N', '.'},
            {4, 3, 4, 'N', '.'},
            {4, 4, 3, 'N', '.'},
            {4, 5, 2, 'N', '.'}
        };
        for (Object[] step : route) {
            int x = (int) step[0];
            int y = (int) step[1];
            int z = (int) step[2];
            char floor = layers.get(y).getAsJsonArray().get(z).getAsString().charAt(x);
            assertAll(
                    () -> assertEquals((char) step[3], floor),
                    () -> assertEquals((char) step[4], layers.get(y + 1)
                            .getAsJsonArray().get(z).getAsString().charAt(x)),
                    () -> assertEquals('.', layers.get(y + 2)
                            .getAsJsonArray().get(z).getAsString().charAt(x)));
        }
        for (int x : new int[] {3, 4}) {
            assertAll(
                    () -> assertEquals('B', layers.get(5).getAsJsonArray().get(7)
                            .getAsString().charAt(x)),
                    () -> assertEquals('.', layers.get(6).getAsJsonArray().get(7)
                            .getAsString().charAt(x)),
                    () -> assertEquals('.', layers.get(7).getAsJsonArray().get(7)
                            .getAsString().charAt(x)),
                    () -> assertEquals('.', layers.get(8).getAsJsonArray().get(7)
                            .getAsString().charAt(x)));
        }
        assertAll(
                () -> assertEquals("VL..N..LV", layers.get(1)
                        .getAsJsonArray().get(6).getAsString()),
                () -> assertEquals("BBBBNBBBB", layers.get(5)
                        .getAsJsonArray().get(2).getAsString()),
                () -> assertEquals("LfffffffL", layers.get(6)
                        .getAsJsonArray().get(1).getAsString()),
                () -> assertEquals("f..I.I..f", layers.get(6)
                        .getAsJsonArray().get(4).getAsString()),
                () -> assertEquals("f..I.I..f", layers.get(6)
                        .getAsJsonArray().get(5).getAsString()),
                () -> assertEquals("f..III..f", layers.get(6)
                        .getAsJsonArray().get(6).getAsString()),
                () -> assertEquals("LfffffffL", layers.get(6)
                        .getAsJsonArray().get(9).getAsString()),
                () -> assertEquals(".........", layers.get(8)
                        .getAsJsonArray().get(4).getAsString()),
                () -> assertEquals('q', layers.get(9)
                        .getAsJsonArray().get(4).getAsString().charAt(4)));
    }

    @Test
    void runtimeAndValidationKeepIntegratedAndStandaloneTowersIndependent()
            throws IOException {
        String garrisons = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingGarrisons.java");
        String armory = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingArmoryLifecycle.java");
        String build = (read("build.gradle") + read("gradle/village-building-templates.gradle"));
        String gameTests = read("src/gametest/java/com/gamergaming/"
                + "villagerguardgunners/worldgen/village/VillageBuildingPoolGameTestAccess.java");
        String placement = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageWatchtowerGuardPlacement.java");
        String spawnIntegration = read("src/main/java/com/gamergaming/"
                + "villagerguardgunners/spawn/NaturalGuardSpawnIntegration.java");
        String poolElement = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingPoolElement.java");
        String docs = read("docs/village-defense-buildings.md");

        assertAll(
                () -> assertTrue(garrisons.contains(
                        "new Plan(WATCHTOWER_GROUP, 0, \"Village Watchtower\")")),
                () -> assertTrue(garrisons.contains("id(\"village_watchtower\")")),
                () -> assertFalse(garrisons.contains("GuardPostGroupDefaults.WATCHTOWER")),
                () -> assertTrue(armory.contains(
                        "identity.buildingType() != VillageBuildingType.ARMORY")),
                () -> assertTrue(build.contains(
                        "The integrated Village Watchtower stair route is blocked")),
                () -> assertTrue(build.contains(
                        "The integrated Village Watchtower lost a safe upper guard cell")),
                () -> assertTrue(gameTests.contains(
                        "verifyIntegratedWatchtowerVariants")),
                () -> assertTrue(gameTests.contains("placedCount == 20")),
                () -> assertTrue(gameTests.contains("new BlockPos(4, 5, 2)")),
                () -> assertTrue(gameTests.contains(
                        "Integrated Village Watchtower stair route is blocked at step")),
                () -> assertTrue(gameTests.contains(
                        "neighbor-sensitive state did not settle")),
                () -> assertTrue(gameTests.contains("ModBlocks.ARMORY.get()")),
                () -> assertTrue(poolElement.contains("setKnownShape(false)")),
                () -> assertTrue(placement.contains("UPPER_DECK_OFFSET = 5")),
                () -> assertTrue(placement.contains("NativeGunController.isGun")),
                () -> assertTrue(placement.contains("Items.CROSSBOW")),
                () -> assertTrue(placement.contains("level.hasChunkAt(upperPosition)")),
                () -> assertFalse(placement.contains("TickEvent")),
                () -> assertTrue(spawnIntegration.contains(
                        "VillageWatchtowerGuardPlacement.finalizePosition")),
                () -> assertTrue(docs.contains("no recurring")),
                () -> assertTrue(docs.contains("standalone")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
