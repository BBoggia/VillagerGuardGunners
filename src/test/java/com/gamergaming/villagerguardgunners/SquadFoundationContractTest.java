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

final class SquadFoundationContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void guardMembershipIsVersionedIndependentAndFailClosed() throws IOException {
        String membership = read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + "SquadMembership.java");
        String codec = read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + "SquadMembershipCodec.java");
        String access = read(
                "src/main/java/com/gamergaming/villagerguardgunners/api/"
                        + "GuardSquadAccess.java");
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");

        assertAll(
                () -> assertTrue(membership.contains("CURRENT_SCHEMA_VERSION")),
                () -> assertTrue(codec.contains(
                        "villagerguardgunners:squad_membership")),
                () -> assertTrue(codec.contains("Unknown, future, or malformed")),
                () -> assertTrue(access.contains("getSquadRevision")),
                () -> assertTrue(access.contains(
                        "SquadCommandRuntime.replaceCommand(guard, null)")),
                () -> assertTrue(mixin.contains("GuardSquadAccess")),
                () -> assertTrue(mixin.contains(
                        "SquadMembershipCodec.readGuardRecord(compound)")),
                () -> assertTrue(mixin.contains(
                        "SquadMembershipCodec.writeGuardRecord(")),
                () -> assertFalse(codec.contains("GuardOrders")),
                () -> assertFalse(access.contains("GuardOrders")));
    }

    @Test
    void worldRegistryContainsMetadataButNoMembershipOrTickIndex() throws IOException {
        String registry = read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + "SquadRegistrySavedData.java");
        String gameTests = read(
                "src/gametest/java/com/gamergaming/villagerguardgunners/gametest/"
                        + "GuardGunnerGameTests.java");

        assertAll(
                () -> assertTrue(registry.contains("extends SavedData")),
                () -> assertTrue(registry.contains(".overworld()")),
                () -> assertTrue(registry.contains("MAX_SQUADS")),
                () -> assertTrue(registry.contains("Map<UUID, SquadMetadata> squads")),
                () -> assertFalse(registry.contains("GuardSquadAccess")),
                () -> assertFalse(registry.contains("getEntitiesOfClass(")),
                () -> assertFalse(registry.contains("TickEvent")),
                () -> assertFalse(registry.contains("EntityJoinLevelEvent")),
                () -> assertTrue(gameTests.contains(
                        "squadMembershipPersistsIndependentlyThroughGuardSaveAndLoad")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
