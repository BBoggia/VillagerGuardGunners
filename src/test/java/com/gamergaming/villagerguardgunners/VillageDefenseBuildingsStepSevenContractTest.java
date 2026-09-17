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

final class VillageDefenseBuildingsStepSevenContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void runtimeReusesPublicArmoryLootAndOneShotContracts() throws IOException {
        String lifecycle = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingArmoryLifecycle.java");
        String savedData = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingSavedData.java");
        String anchor = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingAnchorLifecycle.java");
        int placement = lifecycle.indexOf("GuardPostArmoryPlacer.place(");
        int claim = lifecycle.indexOf("Status claimStatus = claimSetup(data, identity);", placement);

        assertAll(
                () -> assertTrue(placement >= 0),
                () -> assertTrue(claim > placement,
                        "Armory setup must be claimed only after successful placement"),
                () -> assertTrue(lifecycle.contains("GuardPostMarker.ArmoryAccess.PUBLIC")),
                () -> assertTrue(lifecycle.contains("level.hasChunkAt(position)")),
                () -> assertTrue(lifecycle.contains("level.getBlockState(position).isAir()")),
                () -> assertFalse(lifecycle.contains("getChunk(")),
                () -> assertFalse(lifecycle.contains("getEntitiesOfClass(")),
                () -> assertFalse(lifecycle.contains("TickEvent")),
                () -> assertTrue(savedData.contains("claimArmorySetup(")),
                () -> assertTrue(savedData.contains(
                        "claimed.claim(VillageBuildingAction.INITIAL_STOCK)")),
                () -> assertTrue(anchor.contains(
                        "VillageBuildingArmoryLifecycle.activate(liveLevel, identity, rotation)")),
                () -> assertTrue(anchor.contains("MAXIMUM_FUNCTIONAL_LOAD_RETRIES = 10")),
                () -> assertTrue(anchor.contains("MAXIMUM_RETRY_DELAY_TICKS = 40")));
    }

    @Test
    void initialStockTableIsAmmoOnlyConstantAndBounded() throws IOException {
        JsonObject root = JsonParser.parseString(read(
                "src/main/resources/data/villagerguardgunners/loot_tables/chests/"
                        + "village_armory.json")).getAsJsonObject();
        JsonArray pools = root.getAsJsonArray("pools");

        assertEquals(7, pools.size());
        for (var poolElement : pools) {
            JsonObject pool = poolElement.getAsJsonObject();
            assertEquals(1, pool.get("rolls").getAsInt());
            JsonArray entries = pool.getAsJsonArray("entries");
            assertEquals(1, entries.size());
            assertEquals("minecraft:item",
                    entries.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals("tacz:ammo",
                    entries.get(0).getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    void configArtifactAndDocumentationExposeIndependentArmoryControls()
            throws IOException {
        String config = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "config/VillagerGuardGunnersConfig.java");
        String build = read("build.gradle");
        String docs = read("docs/village-defense-buildings.md");

        assertAll(
                () -> assertTrue(config.contains("CONFIG_VERSION = 23")),
                () -> assertTrue(config.contains("placeArmoryBlocks")),
                () -> assertTrue(config.contains("stockArmories")),
                () -> assertTrue(config.contains("restoreVersionFifteenDefaults")),
                () -> assertTrue(build.contains(
                        "loot_tables/chests/village_armory.json")),
                () -> assertTrue(build.contains(
                        "VillageBuildingArmoryLifecycle.class")),
                () -> assertTrue(docs.contains("no recurring")),
                () -> assertTrue(docs.contains("public")),
                () -> assertTrue(docs.contains("Training Yard")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
