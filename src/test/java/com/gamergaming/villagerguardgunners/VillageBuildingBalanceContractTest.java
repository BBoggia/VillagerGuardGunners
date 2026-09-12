package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutDefaults;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingGarrisonPolicy;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class VillageBuildingBalanceContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));
    private static final Map<String, Integer> BUILDING_WEIGHTS = Map.of(
            "barracks", 1,
            "armory", 1,
            "sentry_post", 2,
            "watchtower", 1,
            "training_yard", 1);
    private static final Map<String, PopulationRange> EXPECTED_POPULATION = Map.of(
            "easy", new PopulationRange(4, 6),
            "normal", new PopulationRange(6, 8),
            "hard", new PopulationRange(8, 9));

    @Test
    void builtInWeightsFavorTheSmallSentryWithoutDominatingVillagePools()
            throws IOException {
        JsonObject buildings = json(
                "src/main/templates/villagerguardgunners/village_buildings.json")
                .getAsJsonObject("buildings");
        int totalWeight = 0;
        for (Map.Entry<String, Integer> expected : BUILDING_WEIGHTS.entrySet()) {
            int actual = buildings.getAsJsonObject(expected.getKey())
                    .get("weight")
                    .getAsInt();
            assertEquals(expected.getValue(), actual, expected.getKey());
            totalWeight += actual;
        }
        int balancedTotalWeight = totalWeight;

        assertAll(
                () -> assertEquals(6, balancedTotalWeight),
                () -> assertEquals(2, buildings.getAsJsonObject("sentry_post")
                        .get("weight").getAsInt()),
                () -> assertTrue(BUILDING_WEIGHTS.entrySet().stream()
                        .filter(entry -> !entry.getKey().equals("sentry_post"))
                        .allMatch(entry -> entry.getValue() == 1)));
    }

    @Test
    void combinedDifficultyGroupsRetainReviewedPopulationEnvelope() throws IOException {
        Map<String, PopulationRange> totals = new LinkedHashMap<>();
        for (String difficulty : EXPECTED_POPULATION.keySet()) {
            totals.put(difficulty, new PopulationRange(0, 0));
        }
        for (String building : new String[] {
                "barracks", "sentry_post", "watchtower", "training_yard"
        }) {
            JsonArray variants = json(
                    "src/main/resources/data/villagerguardgunners/guard_post_groups/village_"
                            + building + ".json")
                    .getAsJsonArray("variants");
            for (JsonElement variantElement : variants) {
                JsonObject variant = variantElement.getAsJsonObject();
                JsonArray difficulties = variant.getAsJsonArray("difficulties");
                assertEquals(1, difficulties.size(), building);
                String difficulty = difficulties.get(0).getAsString();
                PopulationRange current = totals.get(difficulty);
                assertTrue(current != null, difficulty);
                JsonArray members = variant.getAsJsonArray("members");
                int minimum = 0;
                for (JsonElement memberElement : members) {
                    JsonObject member = memberElement.getAsJsonObject();
                    if (!member.has("chance") || member.get("chance").getAsInt() == 100) {
                        minimum++;
                    }
                }
                totals.put(difficulty,
                        new PopulationRange(
                                current.minimum() + minimum,
                                current.maximum() + members.size()));
            }
        }

        assertEquals(EXPECTED_POPULATION, totals);

        VillageBuildingGarrisonPolicy policy = new VillageBuildingGarrisonPolicy(true, 4, 3.0D);
        assertAll(
                () -> assertEquals(4,
                        policy.limitGuardCount(VillageBuildingType.BARRACKS, 10)),
                () -> assertEquals(0,
                        policy.limitGuardCount(VillageBuildingType.ARMORY, 10)),
                () -> assertEquals(1,
                        policy.limitGuardCount(VillageBuildingType.SENTRY_POST, 10)),
                () -> assertEquals(2,
                        policy.limitGuardCount(VillageBuildingType.WATCHTOWER, 10)),
                () -> assertEquals(2,
                        policy.limitGuardCount(VillageBuildingType.TRAINING_YARD, 10)));
    }

    @Test
    void armoryRewardAndNaturalGunnerCapsRemainBounded() throws IOException {
        JsonArray pools = json(
                "src/main/resources/data/villagerguardgunners/loot_tables/chests/"
                        + "village_armory.json")
                .getAsJsonArray("pools");
        int totalRounds = 0;
        for (JsonElement poolElement : pools) {
            JsonObject pool = poolElement.getAsJsonObject();
            assertEquals(1, pool.get("rolls").getAsInt());
            JsonArray entries = pool.getAsJsonArray("entries");
            assertEquals(1, entries.size());
            JsonObject entry = entries.get(0).getAsJsonObject();
            assertEquals("tacz:ammo", entry.get("name").getAsString());
            assertFalse(entry.toString().contains("tacz:modern_kinetic_gun"));
            for (JsonElement functionElement : entry.getAsJsonArray("functions")) {
                JsonObject function = functionElement.getAsJsonObject();
                if ("minecraft:set_count".equals(function.get("function").getAsString())) {
                    int rounds = function.get("count").getAsInt();
                    assertTrue(rounds > 0 && rounds <= 32);
                    totalRounds += rounds;
                }
            }
        }
        int boundedTotalRounds = totalRounds;

        assertAll(
                () -> assertEquals(7, pools.size()),
                () -> assertEquals(224, boundedTotalRounds),
                () -> assertEquals(1, NaturalGuardLoadoutDefaults.settingsFor(
                        NaturalGuardDifficulty.EASY).maximumNearby()),
                () -> assertEquals(2, NaturalGuardLoadoutDefaults.settingsFor(
                        NaturalGuardDifficulty.NORMAL).maximumNearby()),
                () -> assertEquals(2, NaturalGuardLoadoutDefaults.settingsFor(
                        NaturalGuardDifficulty.HARD).maximumNearby()));
    }

    private static JsonObject json(String relativePath) throws IOException {
        return JsonParser.parseString(Files.readString(
                PROJECT.resolve(relativePath), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private record PopulationRange(int minimum, int maximum) {
    }
}
