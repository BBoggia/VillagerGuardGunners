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
    void integratedWatchtowerKeepsCompactSupportedGroundAccess() throws IOException {
        JsonObject root = JsonParser.parseString(read(
                "src/main/templates/villagerguardgunners/village_buildings.json"))
                .getAsJsonObject();
        JsonObject tower = root.getAsJsonObject("buildings").getAsJsonObject("watchtower");
        JsonArray size = tower.getAsJsonArray("size");
        JsonArray layers = tower.getAsJsonArray("layers");

        assertAll(
                () -> assertEquals(7, size.get(0).getAsInt()),
                () -> assertEquals(9, size.get(1).getAsInt()),
                () -> assertEquals(7, size.get(2).getAsInt()),
                () -> assertEquals(9, layers.size()));
        for (int y = 1; y <= 6; y++) {
            String ladderRow = layers.get(y).getAsJsonArray().get(5).getAsString();
            String backingRow = layers.get(y).getAsJsonArray().get(6).getAsString();
            assertAll(
                    () -> assertEquals('H', ladderRow.charAt(3)),
                    () -> assertFalse(".VHJA".contains(
                            String.valueOf(backingRow.charAt(3)))));
        }
        for (int x : new int[] {2, 3}) {
            assertAll(
                    () -> assertTrue(layers.get(5).getAsJsonArray().get(3)
                            .getAsString().charAt(x) != '.'),
                    () -> assertEquals('.', layers.get(6).getAsJsonArray().get(3)
                            .getAsString().charAt(x)),
                    () -> assertEquals('.', layers.get(7).getAsJsonArray().get(3)
                            .getAsString().charAt(x)));
        }
    }

    @Test
    void runtimeAndValidationKeepIntegratedAndStandaloneTowersIndependent()
            throws IOException {
        String garrisons = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingGarrisons.java");
        String armory = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingArmoryLifecycle.java");
        String build = read("build.gradle");
        String gameTests = read("src/gametest/java/com/gamergaming/"
                + "villagerguardgunners/worldgen/village/VillageBuildingPoolGameTestAccess.java");
        String docs = read("docs/village-defense-buildings-step-9.md");

        assertAll(
                () -> assertTrue(garrisons.contains(
                        "new Plan(WATCHTOWER_GROUP, 5, \"Village Watchtower\")")),
                () -> assertTrue(garrisons.contains("id(\"village_watchtower\")")),
                () -> assertFalse(garrisons.contains("GuardPostGroupDefaults.WATCHTOWER")),
                () -> assertTrue(armory.contains(
                        "identity.buildingType() != VillageBuildingType.ARMORY")),
                () -> assertTrue(build.contains(
                        "The integrated Village Watchtower ladder is discontinuous")),
                () -> assertTrue(build.contains(
                        "The integrated Village Watchtower lost a safe upper guard cell")),
                () -> assertTrue(gameTests.contains(
                        "verifyIntegratedWatchtowerVariants")),
                () -> assertTrue(gameTests.contains("placedCount == 20")),
                () -> assertTrue(gameTests.contains("canSurvive(level, ladder)")),
                () -> assertTrue(gameTests.contains("ModBlocks.ARMORY.get()")),
                () -> assertTrue(docs.contains("zero recurring")),
                () -> assertTrue(docs.contains("standalone")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
