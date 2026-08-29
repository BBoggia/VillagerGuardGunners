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

final class NaturalGuardLoadoutDatapackContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void reloadListenerIsRegisteredOnceOnTheCommonForgeBus() throws IOException {
        String mod = read(
                "src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");
        String listener = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/NaturalGuardLoadoutPools.java");

        assertAll(
                () -> assertTrue(mod.contains(
                        "NaturalGuardLoadoutPools::registerReloadListener")),
                () -> assertTrue(listener.contains("AddReloadListenerEvent")),
                () -> assertTrue(listener.contains("SimpleJsonResourceReloadListener")),
                () -> assertTrue(listener.contains("private volatile Map<")),
                () -> assertTrue(listener.contains("pools = result.pools()")),
                () -> assertFalse(listener.contains("getEntitiesOfClass(")),
                () -> assertFalse(listener.contains("TimelessAPI")),
                () -> assertFalse(listener.contains("GunItemBuilder")),
                () -> assertFalse(listener.contains("tick(")));
    }

    @Test
    void builtInPoolsArePackagedAtTheDocumentedServerDataPath() {
        Path pools = projectDirectory.resolve(
                "src/main/resources/data/villagerguardgunners/natural_guard_loadouts");

        assertAll(
                () -> assertTrue(Files.isRegularFile(pools.resolve("easy.json"))),
                () -> assertTrue(Files.isRegularFile(pools.resolve("normal.json"))),
                () -> assertTrue(Files.isRegularFile(pools.resolve("hard.json"))));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
