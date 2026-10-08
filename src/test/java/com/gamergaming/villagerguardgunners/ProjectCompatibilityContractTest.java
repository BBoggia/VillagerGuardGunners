package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
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
        gradleProperties.forEach((key, value) ->
                modsToml = modsToml.replace("${" + key + "}", value.toString()));
    }

    @Test
    void targetsTheReviewedMinecraftAndForgeVersions() {
        assertAll(
                () -> assertEquals("1.20.1", property("minecraft_version")),
                () -> assertEquals("47.4.10", property("forge_version")),
                () -> assertEquals("[1.20.1]", property("minecraft_version_range")),
                () -> assertEquals("[47.4.10,48)", property("forge_version_range")),
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
                () -> assertDependency("tacz", property("tacz_version_range")),
                () -> assertDependency("fzzy_config", property("fzzy_config_version_range")),
                () -> assertDependency("kotlinforforge", property("kotlin_for_forge_version_range")));
    }

    @Test
    void acceptsTacz118AndNewerWithoutAnUpperBound() throws InvalidVersionSpecificationException {
        assertEquals("[1.1.8,)", property("tacz_version_range"));
        VersionRange range = VersionRange.createFromVersionSpec(property("tacz_version_range"));
        for (String version : new String[] {"1.1.8", "1.1.8-release", "1.1.8-hotfix",
                "1.1.8-hotfix2", "1.1.9", "1.2.0", "2.0.0"}) {
            assertTrue(range.containsVersion(new DefaultArtifactVersion(version)),
                    () -> "TaCZ " + version + " should be allowed by the loader");
        }
        for (String version : new String[] {"1.0.0", "1.1.7", "1.1.7-hotfix2", "1.1.8-beta"}) {
            assertFalse(range.containsVersion(new DefaultArtifactVersion(version)),
                    () -> "TaCZ " + version + " is below the minimum release");
        }
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
