package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SquadRegistryCommandsContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void maintenanceIsExplicitPermissionGatedBoundedAndNonScanning()
            throws IOException {
        String commands = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/"
                        + "SquadRegistryCommands.java");
        String root = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/"
                        + "GuardGunnerCommands.java");

        assertAll(
                () -> assertTrue(root.contains("hasPermission(REQUIRED_PERMISSION_LEVEL)")),
                () -> assertTrue(root.contains("SquadRegistryCommands.build()")),
                () -> assertTrue(commands.contains("ENTRIES_PER_PAGE = 10")),
                () -> assertTrue(commands.contains("Commands.literal(\"list\")")),
                () -> assertTrue(commands.contains("Commands.literal(\"unregister\")")),
                () -> assertTrue(commands.contains("UuidArgument.uuid()")),
                () -> assertTrue(commands.contains("registry.remove(squadId)")),
                () -> assertFalse(commands.contains("getEntities")),
                () -> assertFalse(commands.contains("forceChunk")),
                () -> assertFalse(commands.contains("TickEvent")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
