package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageWatchtowerStepFifteenContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void currentSchemaRetainsTheBoundedWatchtowerControls() throws IOException {
        String config = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "config/VillagerGuardGunnersConfig.java");
        String policy = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/structure/WatchtowerGenerationPolicy.java");
        String structure = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/structure/VillageWatchtowerStructure.java");
        String piece = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/structure/VillageWatchtowerPiece.java");

        assertAll(
                () -> assertTrue(config.contains("CONFIG_VERSION = 13")),
                () -> assertTrue(config.contains("previousVersion < 10")),
                () -> assertTrue(config.contains("restoreVersionTenDefaults()")),
                () -> assertTrue(config.contains("previousVersion < 11")),
                () -> assertTrue(config.contains("restoreVersionElevenDefaults()")),
                () -> assertTrue(config.contains("ValidatedBoolean spawnGuards")),
                () -> assertTrue(config.contains("ValidatedInt maximumGuardsPerPost")),
                () -> assertTrue(config.contains("ValidatedBoolean placeArmory")),
                () -> assertTrue(config.contains("ValidatedBoolean stockArmory")),
                () -> assertTrue(config.contains("ValidatedDouble guardGunChanceMultiplier")),
                () -> assertTrue(policy.contains("MAXIMUM_GUARDS_PER_POST = 4")),
                () -> assertTrue(policy.contains("throw new IllegalArgumentException")),
                () -> assertTrue(structure.indexOf("watchtowers.enabled.get()")
                        < structure.indexOf("WatchtowerPlacement.findGroundY")),
                () -> assertTrue(piece.contains("watchtowers.snapshot()")),
                () -> assertTrue(piece.contains("policy.limitGuardCount")),
                () -> assertTrue(piece.contains("policy.armoryPlacementEnabled()")),
                () -> assertTrue(piece.contains("policy.armoryStockEnabled()")));
    }

    @Test
    void operatorDiagnosticsArePermissionGatedBoundedAndReadOnly() throws IOException {
        String root = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "command/GuardGunnerCommands.java");
        String commands = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "command/WatchtowerCommands.java");

        assertAll(
                () -> assertTrue(root.contains("REQUIRED_PERMISSION_LEVEL = 2")),
                () -> assertTrue(root.contains("WatchtowerCommands.build()")),
                () -> assertTrue(commands.contains("GROUPS_PER_PAGE = 8")),
                () -> assertTrue(commands.contains("GuardPostGroups.reloadSnapshot()")),
                () -> assertTrue(commands.contains("GuardPostAccess.getIdentity(guard)")),
                () -> assertTrue(commands.contains("IntegerArgumentType.integer(1)")),
                () -> assertFalse(commands.contains("getChunk(")),
                () -> assertFalse(commands.contains("setBlock(")),
                () -> assertFalse(commands.contains("addFreshEntity(")),
                () -> assertFalse(commands.contains("StructureManager")),
                () -> assertFalse(commands.toLowerCase().contains("locate")));
    }

    @Test
    void modpackPrecedenceAndNoRetrofitBoundaryAreDocumented() throws IOException {
        String documentation = read("docs/village-watchtowers-step-15.md");
        String readme = read("README.md");
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(documentation.contains("Datapack > configuration")),
                () -> assertTrue(documentation.contains("does not retrofit")),
                () -> assertTrue(documentation.contains("/vgg watchtowers status")),
                () -> assertTrue(readme.contains("village-watchtowers-step-15.md")),
                () -> assertTrue(build.contains("WatchtowerGenerationPolicy.class")),
                () -> assertTrue(build.contains("WatchtowerCommands.class")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
