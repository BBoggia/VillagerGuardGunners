package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutExpansionDocumentationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void versionTwoAndReadOnlyCommandContractsAreDocumented() throws IOException {
        String contract = Files.readString(
                PROJECT_DIRECTORY.resolve("docs/natural-loadout-v2-contract.md"),
                StandardCharsets.UTF_8);
        String guide = Files.readString(
                PROJECT_DIRECTORY.resolve("docs/natural-guard-loadouts.md"),
                StandardCharsets.UTF_8);
        String sidearms = Files.readString(
                PROJECT_DIRECTORY.resolve("docs/natural-loadout-v3-sidearms.md"),
                StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(contract.contains(
                        "accepted by the strict reload parser")),
                () -> assertTrue(contract.contains(
                        "eligible for contextual natural spawn selection")),
                () -> assertTrue(contract.contains(
                        "captured lazily after the chance and pool gates")),
                () -> assertTrue(contract.contains("Different condition categories use AND")),
                () -> assertTrue(contract.contains("Exclusions take precedence")),
                () -> assertTrue(contract.contains("format-1 pool remains valid")),
                () -> assertTrue(contract.contains("NO_MATCHING_ENTRIES")),
                () -> assertTrue(contract.contains("/vgg loadouts validate [pool]")),
                () -> assertTrue(contract.contains(
                        "/vgg loadouts inspect <pool> [page]")),
                () -> assertTrue(contract.contains(
                        "/vgg loadouts preview <difficulty> <spawn_source> [pool]")),
                () -> assertTrue(contract.contains("never consumes a random roll")),
                () -> assertTrue(contract.contains(
                        "immutable, generation-tagged snapshot")),
                () -> assertTrue(contract.contains(
                        "safe single-line reason of at most")),
                () -> assertTrue(contract.contains("queries TaCZ indexes")),
                () -> assertTrue(contract.contains("permission level 2")),
                () -> assertTrue(contract.contains("8 entries per page")),
                () -> assertTrue(contract.contains("20 issues")),
                () -> assertTrue(contract.contains("at most 256 entries")),
                () -> assertTrue(contract.contains("at most 64 values")),
                () -> assertTrue(contract.contains("at most 128 distinct")),
                () -> assertTrue(contract.contains("`contextDependent` count")),
                () -> assertTrue(contract.contains("example datapack")),
                () -> assertTrue(contract.contains("migration guide")),
                () -> assertTrue(guide.contains("Copy-ready examples and migration")),
                () -> assertTrue(guide.contains("vgg_example:migrated_normal")),
                () -> assertTrue(guide.contains("vgg_example:contextual_guards")),
                () -> assertTrue(guide.contains("vgg_example:rifle_sidearms")),
                () -> assertTrue(sidearms.contains("one complete compatible ammunition stack")),
                () -> assertTrue(sidearms.contains("bundled Easy, Normal, and Hard pools remain format 1")),
                () -> assertTrue(sidearms.contains("configured-sidearm slot")));
    }
}
