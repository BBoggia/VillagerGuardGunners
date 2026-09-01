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
        String plan = read("docs/village-defense-buildings-1.5-plan.md");
        String matrix = read("docs/village-defense-buildings-release-matrix.md");
        String readme = read("README.md");

        assertAll(
                () -> assertTrue(plan.contains("`be63f09fc9697666aa67a8eb66055214fac2d568`")),
                () -> assertTrue(plan.contains(
                        "2ebf9920e7916bc9d7e81e85a55c522020e7149eaabf74d2354bfe6c838e59ac")),
                () -> assertTrue(plan.contains("at most five target village pools")),
                () -> assertTrue(plan.contains("at most 25 injected entries")),
                () -> assertTrue(plan.contains("zero recurring ticks")),
                () -> assertTrue(plan.contains("no retro-generation")),
                () -> assertTrue(plan.contains("distinct from the existing")),
                () -> assertTrue(matrix.contains("VBUILD-POOL-01")),
                () -> assertTrue(matrix.contains("VBUILD-CODEC-01")),
                () -> assertTrue(matrix.contains("VBUILD-COMPAT-01")),
                () -> assertTrue(matrix.contains("VBUILD-PERF-01")),
                () -> assertTrue(readme.contains("docs/village-defense-buildings-1.5-plan.md")),
                () -> assertTrue(readme.contains(
                        "docs/village-defense-buildings-release-matrix.md")));
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
                () -> assertTrue(config.contains("public static final int CONFIG_VERSION = 12")),
                () -> assertTrue(config.contains("villageBuildings = new VillageBuildingSettings()")),
                () -> assertTrue(config.contains("enabled = new ValidatedBoolean(false)")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
