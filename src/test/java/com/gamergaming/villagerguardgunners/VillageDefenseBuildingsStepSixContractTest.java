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

final class VillageDefenseBuildingsStepSixContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));
    private static final Set<String> GROUPS = Set.of(
            "village_barracks", "village_sentry_post", "village_watchtower");

    @Test
    void bundledGarrisonsCoverEveryCombatDifficultyWithinTheHardLimit()
            throws IOException {
        for (String group : GROUPS) {
            JsonObject root = JsonParser.parseString(read(
                    "src/main/resources/data/villagerguardgunners/guard_post_groups/"
                            + group + ".json")).getAsJsonObject();
            JsonArray variants = root.getAsJsonArray("variants");
            Set<String> difficulties = new HashSet<>();
            assertEquals(1, root.get("format").getAsInt());
            for (var variantElement : variants) {
                JsonObject variant = variantElement.getAsJsonObject();
                JsonArray members = variant.getAsJsonArray("members");
                assertTrue(!members.isEmpty() && members.size() <= 4);
                assertFalse(members.get(0).getAsJsonObject().has("chance"),
                        "Every variant needs one required occupant");
                variant.getAsJsonArray("difficulties")
                        .forEach(value -> difficulties.add(value.getAsString()));
            }
            assertEquals(Set.of("easy", "normal", "hard"), difficulties);
        }
    }

    @Test
    void runtimeReusesOneShotClaimsAndExistingGuardIntegration() throws IOException {
        String lifecycle = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingGarrisonLifecycle.java");
        String spawner = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/post/GuardPostSpawner.java");
        String poolElement = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingPoolElement.java");
        String integration = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "spawn/NaturalGuardSpawnIntegration.java");
        int spawnCall = lifecycle.indexOf("GuardPostSpawner.spawnMembersAtomically(");
        int successfulClaim = lifecycle.indexOf(
                "Status claimStatus = claimGarrison(data, identity);", spawnCall);

        assertAll(
                () -> assertTrue(lifecycle.contains(
                        "identity.buildingId(), VillageBuildingAction.GARRISON")),
                () -> assertTrue(lifecycle.contains("GuardPostGroups.get(plan.groupId())")),
                () -> assertTrue(spawnCall >= 0),
                () -> assertTrue(successfulClaim > spawnCall,
                        "Successful garrison actions must be claimed only after spawning"),
                () -> assertTrue(lifecycle.contains("level.hasChunkAt(position)")),
                () -> assertFalse(lifecycle.contains("getChunk(")),
                () -> assertFalse(lifecycle.contains("getEntitiesOfClass(")),
                () -> assertFalse(lifecycle.contains("TickEvent")),
                () -> assertTrue(spawner.contains("prepared.forEach(Guard::discard)")),
                () -> assertTrue(spawner.contains("level.getLevel().getEntity(guardId)")),
                () -> assertTrue(spawner.contains(
                        "expected.equals(GuardPostAccess.getIdentity(guard))")),
                () -> assertTrue(spawner.contains("GuardPostAssignment.pending(")),
                () -> assertTrue(poolElement.contains("recordAndPopulate(")),
                () -> assertTrue(integration.contains(
                        "VillageBuildingGarrisons.isVillageGroup(identity.groupId())")),
                () -> assertTrue(integration.contains(
                        "config.villageBuildings.guardGunUpgradeChance")),
                () -> assertTrue(integration.contains(
                        "villageGarrison || guard.getMainHandItem().is(Items.CROSSBOW)")));
    }

    @Test
    void configArtifactAndDocumentationExposeVillageGarrisons() throws IOException {
        String config = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "config/VillagerGuardGunnersConfig.java");
        String build = read("build.gradle");
        String docs = read("docs/village-defense-buildings.md");

        assertAll(
                () -> assertTrue(config.contains("CONFIG_VERSION = 21")),
                () -> assertTrue(config.contains("spawnGarrisons")),
                () -> assertTrue(config.contains("maximumGuardsPerBuilding")),
                () -> assertTrue(config.contains("guardGunChanceMultiplier")),
                () -> assertTrue(config.contains("restoreVersionFourteenDefaults")),
                () -> GROUPS.forEach(group -> assertTrue(build.contains(
                        "guard_post_groups/" + group + ".json"))),
                () -> assertTrue(docs.contains("no recurring")),
                () -> assertTrue(docs.contains("Peaceful")),
                () -> assertTrue(docs.contains("Village Armories")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
