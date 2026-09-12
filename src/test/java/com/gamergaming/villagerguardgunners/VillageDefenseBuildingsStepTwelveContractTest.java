package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefenseBuildingsStepTwelveContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void diagnosticsArePermissionGatedPagedAndReadOnly() throws IOException {
        String root = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "command/GuardGunnerCommands.java");
        String commands = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "command/VillageBuildingCommands.java");

        assertAll(
                () -> assertTrue(root.contains("REQUIRED_PERMISSION_LEVEL = 2")),
                () -> assertTrue(root.contains("VillageBuildingCommands.build()")),
                () -> assertTrue(commands.contains("ITEMS_PER_PAGE = 8")),
                () -> assertTrue(commands.contains("Commands.literal(\"status\")")),
                () -> assertTrue(commands.contains("paged(\"entries\"")),
                () -> assertTrue(commands.contains("paged(\"pools\"")),
                () -> assertTrue(commands.contains("paged(\"errors\"")),
                () -> assertTrue(commands.contains("VillageBuildingDefinitions.reloadSnapshot()")),
                () -> assertTrue(commands.contains("installationSnapshot()")),
                () -> assertTrue(commands.contains("villagerGuardGunners$getRawTemplates()")),
                () -> assertFalse(commands.contains("getChunk(")),
                () -> assertFalse(commands.contains("getEntitiesOfClass(")),
                () -> assertFalse(commands.contains("setBlock(")),
                () -> assertFalse(commands.contains("addFreshEntity(")),
                () -> assertFalse(commands.toLowerCase().contains("locate")),
                () -> assertFalse(commands.contains("TickEvent")));
    }

    @Test
    void sessionSnapshotArtifactAndOperatorGuideAreComplete() throws IOException {
        String bootstrap = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "VillagerGuardGunners.java");
        String integration = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingPoolIntegration.java");
        String build = read("build.gradle");
        String guide = read("docs/village-defense-buildings.md");
        String readme = read("README.md");

        assertAll(
                () -> assertTrue(bootstrap.contains(
                        "VillageBuildingPoolIntegration::onServerStopped")),
                () -> assertTrue(integration.contains(
                        "VillageBuildingInstallationSnapshot.empty()")),
                () -> assertTrue(build.contains("VillageBuildingCommands.class")),
                () -> assertTrue(build.contains(
                        "VillageBuildingInstallationSnapshot.class")),
                () -> assertTrue(guide.contains("/vgg villagebuildings status")),
                () -> assertTrue(guide.contains("/vgg villagebuildings entries")),
                () -> assertTrue(guide.contains("/vgg villagebuildings pools")),
                () -> assertTrue(guide.contains("/vgg villagebuildings errors")),
                () -> assertTrue(guide.contains("does not locate villages")),
                () -> assertTrue(readme.contains("`/vgg villagebuildings`")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
