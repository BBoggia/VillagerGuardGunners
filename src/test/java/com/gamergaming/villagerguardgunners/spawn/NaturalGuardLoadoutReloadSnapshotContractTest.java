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

final class NaturalGuardLoadoutReloadSnapshotContractTest {
    private static String listener;
    private static String snapshot;

    @BeforeAll
    static void readSources() throws IOException {
        Path projectDirectory = Path.of(
                System.getProperty("villagerguardgunners.projectDir"));
        Path spawnDirectory = projectDirectory.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn");
        listener = Files.readString(
                spawnDirectory.resolve("NaturalGuardLoadoutPools.java"),
                StandardCharsets.UTF_8);
        snapshot = Files.readString(
                spawnDirectory.resolve("NaturalGuardLoadoutReloadSnapshot.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    void reloadUsesOneAtomicPublicationBoundary() {
        assertAll(
                () -> assertTrue(listener.contains(
                        "private volatile PublishedState publishedState")),
                () -> assertTrue(listener.contains(
                        "publishedState = PublishedState.create(snapshot);")),
                () -> assertTrue(listener.contains(
                        "extends SimplePreparableReloadListener<")),
                () -> assertTrue(listener.contains("PreparedResources prepare(")),
                () -> assertTrue(listener.contains("FileToIdConverter.json(DIRECTORY)")),
                () -> assertTrue(listener.contains("openAsReader()")),
                () -> assertTrue(listener.contains(
                        "public static NaturalGuardLoadoutReloadSnapshot reloadSnapshot()")),
                () -> assertFalse(listener.contains("SimpleJsonResourceReloadListener")),
                () -> assertFalse(listener.contains("volatile Map<")),
                () -> assertFalse(listener.contains("volatile Set<")));
    }

    @Test
    void reloadDoesNoTaCZOrEntityWork() {
        assertAll(
                () -> assertFalse(listener.contains("TimelessAPI")),
                () -> assertFalse(listener.contains("GunItemBuilder")),
                () -> assertFalse(listener.contains("AmmoItemBuilder")),
                () -> assertFalse(listener.contains(
                        "NaturalGuardLoadoutCompatibilityAnalyzer.analyze")),
                () -> assertFalse(listener.contains("getEntitiesOfClass(")),
                () -> assertFalse(listener.contains("TickEvent")));
    }

    @Test
    void snapshotCopiesEveryExposedCollection() {
        assertAll(
                () -> assertTrue(snapshot.contains(
                        "Collections.unmodifiableMap(copiedPools)")),
                () -> assertTrue(snapshot.contains("List.copyOf(publications)")),
                () -> assertTrue(snapshot.contains("List.copyOf(copiedRejections)")),
                () -> assertTrue(snapshot.contains("orderedPools.sort")),
                () -> assertTrue(snapshot.contains("copiedRejections.sort")));
    }
}
