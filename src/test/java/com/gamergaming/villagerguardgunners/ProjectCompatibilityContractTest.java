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
                projectDirectory.resolve("src/main/resources/META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8);
        gradleProperties.forEach((key, value) ->
                modsToml = modsToml.replace("${" + key + "}", value.toString()));
    }

    @Test
    void retainsTheMinecraftVersionAndNeoForgeCompilationBaseline() {
        assertAll(
                () -> assertEquals("1.21.1", property("minecraft_version")),
                () -> assertEquals("21.1.233", property("neo_version")),
                () -> assertEquals("[1.21.1]", property("minecraft_version_range")),
                () -> assertEquals("[21.1.233,21.2)", property("neo_version_range")));
    }

    @Test
    void allowsNewerNeoForgePatchesWithinTheSupportedMinecraftLine() throws InvalidVersionSpecificationException {
        String specification = property("neo_version_range");
        assertDependency("neoforge", specification);
        VersionRange range = VersionRange.createFromVersionSpec(specification);
        for (String version : new String[]{"21.1.233", "21.1.247", "21.1.264", "21.1.9999"})
            assertTrue(range.containsVersion(new DefaultArtifactVersion(version)), version);
        for (String version : new String[]{"21.0.167", "21.1.0", "21.1.232", "21.2", "21.2.1", "21.3.0"})
            assertFalse(range.containsVersion(new DefaultArtifactVersion(version)), version);
    }

    @Test
    void targetsTheReviewedCompatibilityModVersions() {
        assertAll(
                () -> assertEquals("2.4.12", property("guard_villager_version")),
                () -> assertEquals("1.2.0", property("tacz_version")),
                () -> assertEquals("0.7.7+fix1+1.21+neoforge", property("fzzy_config_version")),
                () -> assertEquals("5.12.0", property("kotlin_for_forge_version")));
    }

    @Test
    void declaresGuardVillagersAndTaczAsRequiredRuntimeDependencies() {
        assertAll(
                () -> assertDependency("guardvillagers", "[2.4.12]"),
                () -> assertDependency("tacz", property("tacz_version_range")),
                () -> assertDependency("fzzy_config", property("fzzy_config_version_range")),
                () -> assertDependency("kotlinforforge", property("kotlin_for_forge_version_range")));
    }

    @Test
    void requiresTheOfficialTaczMigrationTarget() throws InvalidVersionSpecificationException {
        assertEquals("[1.2.0]", property("tacz_version_range"));
        VersionRange range = VersionRange.createFromVersionSpec(property("tacz_version_range"));
        assertTrue(range.containsVersion(new DefaultArtifactVersion("1.2.0")));
        for (String version : new String[]{"1.1.8", "1.1.8-hotfix", "1.1.8-hotfix-r7", "1.1.8-hotfix-r8", "1.1.9", "1.2.1", "2.0.0"})
            assertFalse(range.containsVersion(new DefaultArtifactVersion(version)), version);
    }

    @Test
    void acceptsBothReviewedFzzyBuildsWithoutAcceptingOtherMinecraftOrLoaderVariants() throws Exception {
        var range = VersionRange.createFromVersionSpec(property("fzzy_config_version_range"));
        for (String version : new String[]{"0.7.7+1.21+neoforge", "0.7.7+fix1+1.21+neoforge"})
            assertTrue(range.containsVersion(new DefaultArtifactVersion(version)), version);
        for (String version : new String[]{"0.7.6+1.21+neoforge", "0.7.7+1.20.1+forge", "0.7.8+1.21+neoforge", "0.8.0"})
            assertFalse(range.containsVersion(new DefaultArtifactVersion(version)), version);
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
                () -> assertTrue(dependencyBlock.contains("type=\"required\""),
                        () -> modId + " must remain mandatory"),
                () -> assertTrue(dependencyBlock.contains("versionRange=\"" + versionRange + "\""),
                        () -> modId + " must use the reviewed version range " + versionRange),
                () -> assertTrue(dependencyBlock.contains("side=\"BOTH\""),
                        () -> modId + " must load on both logical sides"));
    }
}
