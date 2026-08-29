package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ProjectCompatibilityContractTest {
    private static Path projectDirectory;
    private static Properties gradleProperties;
    private static String modsToml;

    @BeforeAll
    static void loadProjectMetadata() throws IOException {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
        gradleProperties = new Properties();

        try (Reader reader = Files.newBufferedReader(
                projectDirectory.resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            gradleProperties.load(reader);
        }

        modsToml = Files.readString(
                projectDirectory.resolve("src/main/resources/META-INF/mods.toml"), StandardCharsets.UTF_8);
    }

    @Test
    void targetsTheReviewedMinecraftAndForgeVersions() {
        assertAll(
                () -> assertEquals("1.20.1", property("minecraft_version")),
                () -> assertEquals("47.4.23", property("forge_version")),
                () -> assertEquals("[1.20.1]", property("minecraft_version_range")),
                () -> assertEquals("[47.4.23]", property("forge_version_range")),
                () -> assertEquals("official", property("mapping_channel")),
                () -> assertEquals("1.20.1", property("mapping_version")));
    }

    @Test
    void targetsTheReviewedCompatibilityModVersions() {
        assertAll(
                () -> assertEquals("1.6.19", property("guard_villager_version")),
                () -> assertEquals("1.1.8-hotfix", property("tacz_version")),
                () -> assertEquals("0.7.6+1.20.1", property("fzzy_config_version")),
                () -> assertEquals("4.12.0", property("kotlin_for_forge_version")));
    }

    @Test
    void declaresGuardVillagersAndTaczAsRequiredRuntimeDependencies() {
        assertAll(
                () -> assertDependency("guardvillagers", "[1.20.1-1.6.19]"),
                () -> assertDependency("tacz", "[1.1.8-hotfix]"),
                () -> assertDependency("fzzy_config", property("fzzy_config_version_range")),
                () -> assertDependency("kotlinforforge", property("kotlin_for_forge_version_range")));
    }

    private static String property(String key) {
        return gradleProperties.getProperty(key);
    }

    private static void assertDependency(String modId, String versionRange) {
        int dependencyStart = modsToml.indexOf("modId=\"" + modId + "\"");
        assertTrue(dependencyStart >= 0, () -> "Missing required dependency metadata for " + modId);

        int dependencyEnd = modsToml.indexOf("[[dependencies.", dependencyStart);
        String dependencyBlock = dependencyEnd >= 0
                ? modsToml.substring(dependencyStart, dependencyEnd)
                : modsToml.substring(dependencyStart);

        assertAll(
                () -> assertTrue(dependencyBlock.contains("mandatory=true"),
                        () -> modId + " must remain mandatory"),
                () -> assertTrue(dependencyBlock.contains("versionRange=\"" + versionRange + "\""),
                        () -> modId + " must use the reviewed version range " + versionRange),
                () -> assertTrue(dependencyBlock.contains("side=\"BOTH\""),
                        () -> modId + " must load on both logical sides"));
    }
}
