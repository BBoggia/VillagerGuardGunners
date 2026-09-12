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

final class SquadPatrolRoutePersistenceContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void routePersistenceIsBoundedVersionedAndOwnedByTheSquadRegistry()
            throws IOException {
        String route = read("src/main/java/com/gamergaming/villagerguardgunners/squad/"
                + "SquadPatrolRoute.java");
        String codec = read("src/main/java/com/gamergaming/villagerguardgunners/squad/"
                + "SquadPatrolRouteCodec.java");
        String registry = read("src/main/java/com/gamergaming/villagerguardgunners/squad/"
                + "SquadRegistrySavedData.java");

        assertAll(
                () -> assertTrue(route.contains("CURRENT_SCHEMA_VERSION = 1")),
                () -> assertTrue(route.contains("MAXIMUM_WAYPOINTS = 64")),
                () -> assertTrue(route.contains("MAXIMUM_SEGMENT_DISTANCE = 256")),
                () -> assertTrue(route.contains("waypoints = List.copyOf")),
                () -> assertTrue(route.contains("boolean executable()")),
                () -> assertTrue(codec.contains("ROUTE_KEYS")),
                () -> assertTrue(codec.contains("future, or malformed")),
                () -> assertTrue(registry.contains("public static final int DATA_VERSION = 3")),
                () -> assertTrue(registry.contains("LEGACY_DATA_VERSION = 1")),
                () -> assertTrue(registry.contains("Map<UUID, SquadPatrolRoute> routes")),
                () -> assertTrue(registry.contains("long routeRevision")),
                () -> assertTrue(registry.contains("unreadableRoot")),
                () -> assertTrue(registry.contains("replacePatrolRoute(")),
                () -> assertTrue(registry.contains("routes.remove(squadId)")));
    }

    @Test
    void persistenceAndDiagnosticsAddNoIdleWorldSearch() throws IOException {
        String registry = read("src/main/java/com/gamergaming/villagerguardgunners/squad/"
                + "SquadRegistrySavedData.java");
        String commands = read("src/main/java/com/gamergaming/villagerguardgunners/command/"
                + "SquadRegistryCommands.java");
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(commands.contains("Commands.literal(\"routes\")")),
                () -> assertTrue(commands.contains("patrolRouteSnapshot()")),
                () -> assertTrue(build.contains("SquadPatrolRoute.class")),
                () -> assertTrue(build.contains("SquadPatrolRouteCodec.class")),
                () -> assertFalse(registry.contains("getEntitiesOfClass(")),
                () -> assertFalse(registry.contains("getChunk(")),
                () -> assertFalse(registry.contains("TickEvent")),
                () -> assertFalse(registry.contains("EntityJoinLevelEvent")));
    }

    @Test
    void everyRegistryConsumerDistinguishesUnavailableData() throws IOException {
        String dispatcher = read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + "SquadCommandDispatcher.java");
        String armory = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/"
                        + "ArmoryLockInteractions.java");
        String ledger = read(
                "src/main/java/com/gamergaming/villagerguardgunners/item/"
                        + "SquadCommandLedgerItem.java");
        String language = read(
                "src/main/resources/assets/villagerguardgunners/lang/en_us.json");

        assertAll(
                () -> assertTrue(dispatcher.contains(
                        "DispatchStatus.REGISTRY_DISABLED")),
                () -> assertTrue(armory.contains(
                        "InteractionResult.REGISTRY_DISABLED")),
                () -> assertTrue(ledger.contains(
                        "squad_command.registry_disabled")),
                () -> assertTrue(language.contains(
                        "armory.registry_disabled")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
