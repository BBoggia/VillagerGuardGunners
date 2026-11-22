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

final class VillageDefenseBuildingsStepTenContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void bundledTrainingYardGroupIsBoundedVariedAndDifficultyComplete()
            throws IOException {
        JsonObject root = JsonParser.parseString(read(
                "src/main/resources/data/villagerguardgunners/guard_post_groups/"
                        + "village_training_yard.json")).getAsJsonObject();
        JsonArray variants = root.getAsJsonArray("variants");
        Set<String> difficulties = new HashSet<>();
        Set<String> roles = new HashSet<>();

        assertEquals(1, root.get("format").getAsInt());
        assertEquals(3, variants.size());
        for (var variantElement : variants) {
            JsonObject variant = variantElement.getAsJsonObject();
            JsonArray members = variant.getAsJsonArray("members");
            assertTrue(!members.isEmpty() && members.size() <= 2);
            assertFalse(members.get(0).getAsJsonObject().has("chance"),
                    "Every difficulty needs one guaranteed Training Yard guard");
            variant.getAsJsonArray("difficulties")
                    .forEach(value -> difficulties.add(value.getAsString()));
            members.forEach(member -> roles.add(
                    member.getAsJsonObject().get("role").getAsString()));
        }

        assertAll(
                () -> assertEquals(Set.of("easy", "normal", "hard"), difficulties),
                () -> assertEquals(Set.of(
                        "villagerguardgunners:rifleman",
                        "villagerguardgunners:marksman",
                        "villagerguardgunners:breacher"), roles));
    }

    @Test
    void trainingYardUsesTheSharedOneShotGarrisonWithoutPracticeCombat()
            throws IOException {
        String garrisons = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingGarrisons.java");
        String policy = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingGarrisonPolicy.java");
        String lifecycle = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingGarrisonLifecycle.java");
        String build = (read("build.gradle") + read("gradle/village-building-templates.gradle"));
        String gameTests = read("src/gametest/java/com/gamergaming/"
                + "villagerguardgunners/worldgen/village/"
                + "VillageBuildingPoolGameTestAccess.java");

        assertAll(
                () -> assertTrue(garrisons.contains(
                        "id(\"village_training_yard\")")),
                () -> assertTrue(garrisons.contains(
                        "new Plan(TRAINING_YARD_GROUP, 0, \"Training Yard\")")),
                () -> assertTrue(policy.contains(
                        "case WATCHTOWER, TRAINING_YARD -> 2")),
                () -> assertTrue(lifecycle.contains(
                        "GuardPostSpawner.spawnMembersAtomically(")),
                () -> assertFalse(lifecycle.contains("Projectile")),
                () -> assertFalse(lifecycle.contains("practice")),
                () -> assertFalse(lifecycle.contains("TickEvent")),
                () -> assertTrue(build.contains(
                        "guard_post_groups/village_training_yard.json")),
                () -> assertTrue(build.contains(
                        "The Training Yard lost safe guard cell")),
                () -> assertTrue(gameTests.contains("verifyTrainingYardVariants")),
                () -> assertTrue(gameTests.contains("placedCount == 20")),
                () -> assertTrue(gameTests.contains("Blocks.TARGET")),
                () -> assertTrue(gameTests.contains("Blocks.HAY_BLOCK")));
    }

    @Test
    void trainingYardBlueprintRetainsDecorativeTargetsAndTwoSafeCells()
            throws IOException {
        JsonObject root = JsonParser.parseString(read(
                "src/main/templates/villagerguardgunners/village_buildings.json"))
                .getAsJsonObject();
        JsonObject yard = root.getAsJsonObject("buildings")
                .getAsJsonObject("training_yard");
        JsonArray size = yard.getAsJsonArray("size");
        JsonArray layers = yard.getAsJsonArray("layers");
        String flattened = layers.toString();

        assertAll(
                () -> assertEquals(11, size.get(0).getAsInt()),
                () -> assertEquals(5, size.get(1).getAsInt()),
                () -> assertEquals(10, size.get(2).getAsInt()),
                () -> assertEquals(2, count(flattened, 'T')),
                () -> assertEquals(2, count(flattened, 'Y')),
                () -> assertEquals('P', row(layers, 0, 3).charAt(5)),
                () -> assertEquals('A', row(layers, 1, 3).charAt(5)),
                () -> assertEquals('.', row(layers, 2, 3).charAt(5)),
                () -> assertEquals('P', row(layers, 0, 3).charAt(4)),
                () -> assertEquals('.', row(layers, 1, 3).charAt(4)),
                () -> assertEquals('.', row(layers, 2, 3).charAt(4)));
    }

    private static String row(JsonArray layers, int y, int z) {
        return layers.get(y).getAsJsonArray().get(z).getAsString();
    }

    private static int count(String value, char expected) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == expected) {
                count++;
            }
        }
        return count;
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
