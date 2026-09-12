package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefenseBuildingsStepFourteenContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void manualHandoffAndPublicPackagingRetainTheReleaseBoundary()
            throws IOException {
        String build = read("build.gradle");
        String guide = read(
                "docs/village-defense-buildings-manual-validation.md");
        String matrix = read(
                "docs/village-defense-buildings-release-matrix.md");
        String checklist = read("docs/release-checklist.md");

        assertAll(
                () -> assertTrue(build.contains(
                        "tasks.register('verifyVillageDefenseBuildingManualCandidateIdentity'")),
                () -> assertTrue(build.contains(
                        "tasks.register('prepareVillageDefenseBuildingManualValidation'")),
                () -> assertTrue(build.contains(
                        "tasks.register('verifyVillageDefenseBuildingManualChecklist'")),
                () -> assertTrue(build.contains(
                        "tasks.register('verifyPublicReleaseIdentity'")),
                () -> assertTrue(build.contains(
                        "tasks.named('verifyVillageDefenseBuildingsReleaseCandidate')")),
                () -> assertTrue(build.contains(
                        "tasks.named('verifyVillageDefenseBuildingManualCandidateIdentity')")),
                () -> assertTrue(build.contains(
                        "tasks.named('verifyVillageDefenseBuildingManualChecklist')")),
                () -> assertTrue(build.contains("mod_version.endsWith('-dev')")),
                () -> assertTrue(guide.contains(
                        "./gradlew prepareVillageDefenseBuildingManualValidation")),
                () -> assertTrue(guide.contains("newly generated chunks")),
                () -> assertTrue(guide.contains("mixed-mod")),
                () -> assertTrue(matrix.contains("Step 14 candidate version")),
                () -> assertTrue(countOccurrences(
                        matrix, "| VBUILD-MANUAL-") == 11),
                () -> assertTrue(checklist.contains(
                        "verifyVillageDefenseBuildingManualChecklist")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length())
                / needle.length();
    }
}
