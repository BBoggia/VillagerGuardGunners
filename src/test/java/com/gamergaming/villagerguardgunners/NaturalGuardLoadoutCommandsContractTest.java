package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutCommandsContractTest {
    private static String commands;
    private static String service;

    @BeforeAll
    static void readSources() throws IOException {
        Path project = Path.of(System.getProperty("villagerguardgunners.projectDir"));
        Path directory = project.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners/command");
        commands = Files.readString(
                directory.resolve("NaturalGuardLoadoutCommands.java"),
                StandardCharsets.UTF_8);
        service = Files.readString(
                directory.resolve("NaturalGuardLoadoutCommandService.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    void frozenSubtreeIsPermissionedSuggestedAndSilentToOtherOperators() {
        assertAll(
                () -> assertTrue(commands.contains("REQUIRED_PERMISSION_LEVEL")),
                () -> assertTrue(commands.contains("VALIDATE_LITERAL")),
                () -> assertTrue(commands.contains("INSPECT_LITERAL")),
                () -> assertTrue(commands.contains("PREVIEW_LITERAL")),
                () -> assertTrue(commands.contains("suggestPools(builder)")),
                () -> assertTrue(commands.contains("IntegerArgumentType.integer(1)")),
                () -> assertTrue(commands.contains("source.sendSuccess(() -> message, false)")),
                () -> assertFalse(commands.contains("broadcastToAdmins")));
    }

    @Test
    void diagnosticsAreBoundedAndCannotInvokeMutationPaths() {
        assertAll(
                () -> assertTrue(service.contains("MAX_VALIDATION_ISSUES_IN_SUMMARY")),
                () -> assertTrue(service.contains("ENTRIES_PER_PAGE")),
                () -> assertTrue(service.contains("MAX_PREVIEW_ENTRIES")),
                () -> assertTrue(commands.contains("MAX_DETAIL_LENGTH")),
                () -> assertTrue(commands.contains("NaturalGuardLoadoutContextCapture.capture(")),
                () -> assertTrue(commands.contains("NaturalGuardLoadoutPools.reloadSnapshot()")),
                () -> assertFalse(commands.contains("NaturalGuardLoadoutIssuance")),
                () -> assertFalse(commands.contains("NaturalGuardPopulationIndex")),
                () -> assertFalse(commands.contains("nextDouble")),
                () -> assertFalse(commands.contains("nextInt")),
                () -> assertFalse(service.contains("setItemSlot")),
                () -> assertFalse(service.contains("getEntitiesOfClass")));
    }
}
