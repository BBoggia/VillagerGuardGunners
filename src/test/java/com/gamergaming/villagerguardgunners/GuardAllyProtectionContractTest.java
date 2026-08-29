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

final class GuardAllyProtectionContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void everySafetyLayerUsesTheCentralModpackAwareResolver() throws IOException {
        String resolver = read(
                "src/main/java/com/gamergaming/villagerguardgunners/api/GuardAllyProtection.java");
        String safety = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireSafety.java");
        String events = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireEvents.java");

        assertAll(
                () -> assertTrue(safety.contains("GuardAllyProtection.isProtected(")),
                () -> assertTrue(safety.contains("GuardAllyProtection.classify(")),
                () -> assertTrue(events.contains("GuardFriendlyFireSafety.isProtected(")),
                () -> assertTrue(resolver.contains("ProtectionReason.EXPLICIT_EXCLUSION")),
                () -> assertTrue(resolver.contains("ProtectionReason.ENTITY_TYPE_TAG")),
                () -> assertTrue(resolver.contains("instanceof OwnableEntity")),
                () -> assertTrue(resolver.contains("ProtectionReason.SCOREBOARD_TEAM")),
                () -> assertTrue(resolver.contains("ProtectionReason.COMPATIBILITY_PROVIDER")),
                () -> assertTrue(resolver.contains("ProtectionReason.PROVIDER_FAILURE")));
    }

    @Test
    void datapackAndProviderExtensionsAddNoRecurringWorldWork() throws IOException {
        Path tagPath = projectDirectory.resolve(
                "src/main/resources/data/villagerguardgunners/tags/entity_types/guard_allies.json");
        String tag = Files.readString(tagPath, StandardCharsets.UTF_8);
        String resolver = read(
                "src/main/java/com/gamergaming/villagerguardgunners/api/GuardAllyProtection.java");

        assertAll(
                () -> assertTrue(Files.isRegularFile(tagPath)),
                () -> assertTrue(tag.contains("\"replace\": false")),
                () -> assertTrue(tag.contains("minecraft:snow_golem")),
                () -> assertTrue(resolver.contains("living.getType().is(GUARD_ALLIES)")),
                () -> assertTrue(resolver.contains("volatile List<RegisteredProvider>")),
                () -> assertTrue(resolver.contains("List.copyOf(updated)")),
                () -> assertTrue(resolver.contains("registerScopedProvider(")),
                () -> assertTrue(resolver.contains("updated.remove(registered)")),
                () -> assertFalse(resolver.contains("getEntitiesOfClass(")),
                () -> assertFalse(resolver.contains("TickEvent")),
                () -> assertFalse(resolver.contains("MinecraftForge.EVENT_BUS.post")),
                () -> assertFalse(resolver.contains("LogUtils")),
                () -> assertFalse(resolver.contains("Logger")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
