package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class VillageDefenseBuildingsStepFourContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void markerGrammarAndPoolHookStayClosedAndBounded() throws IOException {
        String marker = read(village("VillageBuildingAnchorMarker.java"));
        String element = read(village("VillageBuildingPoolElement.java"));

        assertAll(
                () -> assertTrue(marker.contains("PREFIX = \"village_anchor:\"")),
                () -> assertTrue(marker.contains("MAXIMUM_METADATA_LENGTH = 64")),
                () -> assertTrue(marker.contains("anchors > 1")),
                () -> assertTrue(element.contains("validateTemplateMarkers")),
                () -> assertTrue(element.contains("Blocks.AIR.defaultBlockState()")),
                () -> assertTrue(element.contains("VillageBuildingAnchorLifecycle.record")),
                () -> assertFalse(element.contains("getEntitiesOfClass(")),
                () -> assertFalse(element.contains("forceChunk")));
    }

    @Test
    void persistedStateIsStrictVersionedAndAtMostOnce() throws IOException {
        String identity = read(village("VillageBuildingIdentity.java"));
        String record = read(village("VillageBuildingRecord.java"));
        String codec = read(village("VillageBuildingRecordCodec.java"));
        String data = read(village("VillageBuildingSavedData.java"));

        assertAll(
                () -> assertTrue(identity.contains("CURRENT_SCHEMA_VERSION = 1")),
                () -> assertTrue(codec.contains("BUILDING_TAG = \"VggVillageBuilding\"")),
                () -> assertTrue(record.contains("claimedActions")),
                () -> assertTrue(data.contains("DATA_VERSION = 1")),
                () -> assertTrue(data.contains("MAXIMUM_RECORDS = 65_536")),
                () -> assertTrue(data.contains("ALREADY_CLAIMED")),
                () -> assertTrue(data.contains("unreadableRoot.copy()")),
                () -> assertTrue(data.contains("new AnchorKey(")));
    }

    @Test
    void lifecycleUsesMainThreadHandoffWithoutRuntimeManagement() throws IOException {
        String lifecycle = read(village("VillageBuildingAnchorLifecycle.java"));
        String villageSources = readAll("src/main/java/com/gamergaming/"
                + "villagerguardgunners/worldgen/village");

        assertAll(
                () -> assertTrue(lifecycle.contains("server.isSameThread()")),
                () -> assertTrue(lifecycle.contains("server.execute(claim)")),
                () -> assertTrue(lifecycle.contains("server.getLevel(dimension)")),
                () -> assertFalse(lifecycle.contains("getChunk(")),
                () -> assertFalse(lifecycle.contains("getEntities")),
                () -> assertFalse(villageSources.contains("TickEvent")),
                () -> assertFalse(villageSources.contains("addRegionTicket")),
                () -> assertFalse(villageSources.contains("setChunkForced")));
    }

    @Test
    void releaseArtifactAndDocumentationRetainStepFour() throws IOException {
        String build = read("build.gradle");
        assertAll(
                () -> assertTrue(build.contains("VillageBuildingAnchorMarker.class")),
                () -> assertTrue(build.contains("VillageBuildingSavedData.class")),
                () -> assertTrue(read("docs/village-defense-buildings-step-4.md")
                        .contains("No functional building actions")),
                () -> assertTrue(read("docs/village-defense-buildings-release-matrix.md")
                        .contains("VBUILD-ANCHOR-01")));
    }

    private static String village(String filename) {
        return "src/main/java/com/gamergaming/villagerguardgunners/"
                + "worldgen/village/" + filename;
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static String readAll(String relativeDirectory) throws IOException {
        try (var paths = Files.walk(projectDirectory.resolve(relativeDirectory))) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    })
                    .collect(java.util.stream.Collectors.joining("\n"));
        }
    }
}
