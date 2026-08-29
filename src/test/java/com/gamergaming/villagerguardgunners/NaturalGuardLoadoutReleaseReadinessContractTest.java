package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class NaturalGuardLoadoutReleaseReadinessContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void manualMatrixAndGuideCoverBalanceLifecycleAndRollback() throws IOException {
        String checklist = read("docs/release-checklist.md");
        String guide = read("docs/natural-guard-loadouts.md");
        String[] manualCases = {
                "NATURAL-LIFE-01",
                "NATURAL-LIFE-02",
                "NATURAL-DROP-01",
                "NATURAL-DIFF-01",
                "NATURAL-POOL-01",
                "NATURAL-SOURCE-01",
                "NATURAL-AMMO-01",
                "NATURAL-EXPLOSIVE-01",
                "NATURAL-CAP-01"
        };

        assertAll(Stream.of(manualCases)
                .map(id -> (Executable) () -> assertTrue(
                        checklist.contains(id),
                        () -> "Missing natural-loadout manual case " + id)));
        assertAll(
                () -> assertTrue(guide.contains("Step 8 balance and release guidance")),
                () -> assertTrue(guide.contains("Easy | 35% | 1")),
                () -> assertTrue(guide.contains("Normal | 50% | 2")),
                () -> assertTrue(guide.contains("Hard | 65% | 2")),
                () -> assertTrue(guide.contains("one complete")),
                () -> assertTrue(guide.contains("naturalLoadouts.enabled = false")),
                () -> assertTrue(guide.contains("future spawn attempts")),
                () -> assertTrue(guide.contains("/vgg inspect <guard>")));
    }

    @Test
    void curseForgeHandoffDescribesTheShippedNaturalLoadoutContract() throws IOException {
        String description = read("docs/curseforge-description.md");
        String releaseNotes = read("docs/curseforge-release-notes-1.0.0.md");
        String upload = read("docs/curseforge-upload-1.0.0.md");

        assertAll(
                () -> assertTrue(description.contains(
                        "<strong>Difficulty-scaled natural guard loadouts</strong>")),
                () -> assertTrue(description.contains("one complete stack")),
                () -> assertTrue(description.contains("Reloadable datapack weapon pools")),
                () -> assertTrue(description.contains("guard/gun provenance mismatch")),
                () -> assertTrue(releaseNotes.contains(
                        "Difficulty-scaled natural guard loadouts")),
                () -> assertTrue(releaseNotes.contains("one full compatible ammunition stack")),
                () -> assertTrue(upload.contains("difficulty-scaled loadouts")),
                () -> assertTrue(upload.contains("datapack-defined pools")));
    }

    @Test
    void aggregateReleaseGateBindsNaturalCodeDocumentationAndManualCases()
            throws IOException {
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(build.contains("api/GuardNaturalLoadoutAccess.class")),
                () -> assertTrue(build.contains("spawn/NaturalGuardLoadoutIssuance.class")),
                () -> assertTrue(build.contains("spawn/NaturalGuardLoadoutProvenanceCodec.class")),
                () -> assertTrue(build.contains("spawn/NaturalGuardLoadoutDiagnostics.class")),
                () -> assertTrue(build.contains("naturalLoadoutGuide")),
                () -> assertTrue(build.contains("docs/natural-guard-loadouts.md")),
                () -> assertTrue(build.contains("requiredNaturalLoadoutCases")),
                () -> assertTrue(build.contains("NATURAL-CAP-01")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
