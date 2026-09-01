package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingDefinitionFormat;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageDefenseBuildingsStepThreeContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void formatIsStrictBoundedAndOwnsNoGameplayPayload() throws IOException {
        String loader = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageBuildingDefinitions.java");

        assertAll(
                () -> assertEquals(1, VillageBuildingDefinitionFormat.VERSION),
                () -> assertEquals(25, VillageBuildingDefinitionFormat.MAXIMUM_DOCUMENTS),
                () -> assertEquals(16_384,
                        VillageBuildingDefinitionFormat.MAXIMUM_DOCUMENT_CHARACTERS),
                () -> assertEquals(16, VillageBuildingDefinitionFormat.MAXIMUM_JSON_DEPTH),
                () -> assertTrue(loader.contains("rejectUnknownFields")),
                () -> assertTrue(loader.contains("repeats JSON field")),
                () -> assertTrue(loader.contains("last-known-good")
                        || loader.contains("previous.definitions()")),
                () -> assertFalse(loader.contains("CommandSource")),
                () -> assertFalse(loader.contains("EntityType")),
                () -> assertFalse(loader.contains("ItemStack")),
                () -> assertFalse(loader.contains("getEntitiesOfClass(")),
                () -> assertFalse(loader.contains("tick(")));
    }

    @Test
    void builtInProbeUsesTheReviewedFormatAndRemainsInert() throws IOException {
        JsonObject probe = JsonParser.parseString(read(
                "src/main/resources/data/villagerguardgunners/village_buildings/"
                        + "integration_probe/plains.json")).getAsJsonObject();

        assertAll(
                () -> assertEquals(
                        Set.of(
                                "format",
                                "building_type",
                                "target_pool",
                                "template",
                                "processors",
                                "projection",
                                "weight"),
                        probe.keySet()),
                () -> assertEquals(1, probe.get("format").getAsInt()),
                () -> assertEquals("barracks", probe.get("building_type").getAsString()),
                () -> assertEquals(
                        "minecraft:village/plains/houses",
                        probe.get("target_pool").getAsString()),
                () -> assertEquals(
                        "minecraft:village/plains/houses/plains_small_house_1",
                        probe.get("template").getAsString()),
                () -> assertFalse(probe.has("markers")),
                () -> assertFalse(probe.has("guards")),
                () -> assertFalse(probe.has("loot")),
                () -> assertFalse(probe.has("commands")));
    }

    @Test
    void reloadStartupConfigAndArtifactContractsStayConnected() throws IOException {
        String bootstrap = read(
                "src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");
        String integration = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageBuildingPoolIntegration.java");
        String config = read(
                "src/main/java/com/gamergaming/villagerguardgunners/config/"
                        + "VillagerGuardGunnersConfig.java");
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(bootstrap.contains(
                        "VillageBuildingDefinitions::registerReloadListener")),
                () -> assertTrue(bootstrap.contains(
                        "VillageBuildingPoolIntegration::onServerAboutToStart")),
                () -> assertTrue(integration.contains("VillageBuildingDefinitions.enabledEntries")),
                () -> assertTrue(config.contains("CONFIG_VERSION = 13")),
                () -> assertTrue(config.contains("restoreVersionThirteenDefaults")),
                () -> assertTrue(config.contains("integratedWatchtowers")),
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners/village_buildings/"
                                + "integration_probe/plains.json")),
                () -> assertTrue(build.contains("VillageBuildingDefinitions.class")));
    }

    @Test
    void authoringExampleAndOperationalDocumentationExist() throws IOException {
        assertAll(
                () -> assertTrue(Files.isRegularFile(projectDirectory.resolve(
                        "docs/examples/village-buildings-datapack/pack.mcmeta"))),
                () -> assertTrue(Files.isRegularFile(projectDirectory.resolve(
                        "docs/examples/village-buildings-datapack/data/vgg_example/"
                                + "village_buildings/plains_sentry_post.json"))),
                () -> assertTrue(read("docs/village-defense-buildings-step-3.md")
                        .contains("Restart required")),
                () -> assertTrue(read("docs/configuration.md")
                        .contains("Config version 13")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
