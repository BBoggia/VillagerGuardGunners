package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutFoundationContractTest {
    private static final Path SOURCE_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."),
            "src/main/java/com/gamergaming/villagerguardgunners/spawn");

    @Test
    void foundationKeepsSpawnPolicyPureBoundedAndDeferred() throws IOException {
        String defaults = source("NaturalGuardLoadoutDefaults.java");
        String pool = source("NaturalGuardLoadoutPool.java");
        String selector = source("NaturalGuardLoadoutSelector.java");

        assertAll(
                () -> assertTrue(defaults.contains(
                        "Set.of(NaturalGuardSpawnSource.STRUCTURE)")),
                () -> assertTrue(defaults.contains("new NaturalGuardDifficultySettings(0.0D, 0, null)")),
                () -> assertTrue(defaults.contains("new NaturalGuardDifficultySettings(0.35D, 1, EASY_POOL)")),
                () -> assertTrue(defaults.contains("new NaturalGuardDifficultySettings(0.50D, 2, NORMAL_POOL)")),
                () -> assertTrue(defaults.contains("new NaturalGuardDifficultySettings(0.65D, 2, HARD_POOL)")),
                () -> assertTrue(pool.contains("List.copyOf(")),
                () -> assertTrue(pool.contains("Integer.MAX_VALUE")),
                () -> assertTrue(selector.indexOf("chanceRoll.getAsDouble()")
                        < selector.indexOf("nearbyNaturalGunners.getAsInt()")),
                () -> assertFalse(selector.contains("getEntitiesOfClass(")),
                () -> assertFalse(selector.contains("TimelessAPI")),
                () -> assertFalse(selector.contains("GunItemBuilder")),
                () -> assertFalse(selector.contains("tick(")),
                () -> assertFalse(selector.contains("EntityJoinLevelEvent")),
                () -> assertFalse(selector.contains("@Mixin")));
    }

    private static String source(String fileName) throws IOException {
        return Files.readString(SOURCE_DIRECTORY.resolve(fileName), StandardCharsets.UTF_8);
    }
}
