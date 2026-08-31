package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutParserContractTest {
    private static String parser;
    private static String selector;

    @BeforeAll
    static void readSources() throws IOException {
        Path projectDirectory = Path.of(
                System.getProperty("villagerguardgunners.projectDir"));
        Path spawnDirectory = projectDirectory.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn");
        parser = Files.readString(
                spawnDirectory.resolve("NaturalGuardLoadoutParser.java"),
                StandardCharsets.UTF_8);
        selector = Files.readString(
                spawnDirectory.resolve("NaturalGuardLoadoutSelector.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    void parserAcceptsOnlyTheFrozenThreeVersionSurface() {
        assertAll(
                () -> assertTrue(parser.contains(
                        "NaturalGuardLoadoutFormat.LEGACY_VERSION")),
                () -> assertTrue(parser.contains(
                        "NaturalGuardLoadoutFormat.CONDITIONAL_VERSION")),
                () -> assertTrue(parser.contains(
                        "NaturalGuardLoadoutFormat.SIDEARM_VERSION")),
                () -> assertTrue(parser.contains("VERSION_1_ENTRY_FIELDS")),
                () -> assertTrue(parser.contains("VERSION_2_ENTRY_FIELDS")),
                () -> assertTrue(parser.contains("VERSION_3_ENTRY_FIELDS")),
                () -> assertTrue(parser.contains("CONDITION_FIELDS")),
                () -> assertTrue(parser.contains("SELECTOR_GROUP_FIELDS")),
                () -> assertTrue(parser.contains("contains a duplicate value")));
    }

    @Test
    void parserPerformsNoRegistryWorldOrTaCZResolution() {
        assertAll(
                () -> assertFalse(parser.contains("TimelessAPI")),
                () -> assertFalse(parser.contains("ItemStack")),
                () -> assertFalse(parser.contains("ServerLevel")),
                () -> assertFalse(parser.contains("RegistryAccess")),
                () -> assertFalse(parser.contains("BuiltInRegistries")),
                () -> assertFalse(parser.contains("getEntitiesOfClass(")));
    }

    @Test
    void formatTwoUsesContextualSelectionWithoutWeakeningPoolAvailability() {
        assertAll(
                () -> assertTrue(selector.contains("pool.hasConditionalEntries()")),
                () -> assertTrue(selector.contains("pool.matchingWeight(capturedContext)")),
                () -> assertTrue(selector.contains("DecisionStatus.NO_MATCHING_ENTRIES")),
                () -> assertTrue(selector.contains("!pool.id().equals(difficulty.poolId())")));
    }
}
