package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefenseBuildingsStepOneContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void baselineBoundariesAndValidationCasesAreRecorded() throws IOException {
        String guide = read("docs/village-defense-buildings.md");
        String matrix = read("docs/village-defense-buildings-release-matrix.md");
        String readme = read("README.md");

        assertAll(
                () -> assertTrue(guide.contains("vanilla village pools")),
                () -> assertTrue(guide.contains("Barracks")),
                () -> assertTrue(guide.contains("Integrated Watchtowers")),
                () -> assertTrue(guide.contains("no recurring village scan")),
                () -> assertTrue(guide.contains("server restart")),
                () -> assertTrue(matrix.contains("VBUILD-POOL-01")),
                () -> assertTrue(matrix.contains("VBUILD-CODEC-01")),
                () -> assertTrue(matrix.contains("VBUILD-COMPAT-01")),
                () -> assertTrue(matrix.contains("VBUILD-PERF-01")),
                () -> assertTrue(readme.contains("docs/village-defense-buildings.md")),
                () -> assertTrue(guide.contains(
                        "village-defense-buildings-release-matrix.md")));
    }

    @Test
    void stepTwoProbeIsInertDefaultOffAndAdditive() throws IOException {
        String integration = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingPoolIntegration.java");
        String element = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/VillageBuildingPoolElement.java");
        String config = read("src/main/java/com/gamergaming/villagerguardgunners/"
                + "config/VillagerGuardGunnersConfig.java");

        assertAll(
                () -> assertTrue(element.contains("extends SinglePoolElement")),
                () -> assertTrue(element.contains("entry_id")),
                () -> assertTrue(integration.contains("new ArrayList<>(")),
                () -> assertTrue(integration.contains("alreadyPresent++")),
                () -> assertTrue(integration.contains("setMaxSize(SIZE_UNSET)")),
                () -> assertTrue(config.contains("public static final int CONFIG_VERSION = 24")),
                () -> assertTrue(config.contains("villageBuildings = new VillageBuildingSettings()")),
                () -> assertTrue(config.contains("enabled = new ValidatedBoolean(false)")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
