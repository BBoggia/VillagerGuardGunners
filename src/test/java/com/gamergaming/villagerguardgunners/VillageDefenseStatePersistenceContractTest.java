package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefenseStatePersistenceContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void stateLivesInsideTheExistingVersionedNetworkPayload() throws IOException {
        String network = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/network/"
                        + "VillageDefenseNetwork.java");
        String codec = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/network/"
                        + "VillageDefenseNetworkCodec.java");

        assertAll(
                () -> assertTrue(network.contains("VillageDefenseStatus defenseStatus")),
                () -> assertTrue(network.contains("LEGACY_SCHEMA_VERSION = 1")),
                () -> assertTrue(network.contains("CURRENT_SCHEMA_VERSION = 2")),
                () -> assertTrue(codec.contains("DEFENSE_STATUS_TAG")),
                () -> assertTrue(codec.contains("VillageDefenseStatus.peaceful()")),
                () -> assertTrue(codec.contains("LEGACY_NETWORK_KEYS")));
    }

    @Test
    void decayUsesTrackedDeadlinesAndBoundedBatches() throws IOException {
        String savedData = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/network/"
                        + "VillageDefenseNetworkSavedData.java");
        String ticker = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseStateTicker.java");
        String service = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseStateService.java");
        String bootstrap = read(
                "src/main/java/com/gamergaming/villagerguardgunners/"
                        + "VillagerGuardGunners.java");

        assertAll(
                () -> assertTrue(savedData.contains("reviewsByGameTime")),
                () -> assertTrue(savedData.contains("MAXIMUM_REVIEWS_PER_TICK")),
                () -> assertTrue(savedData.contains("reviewDue(")),
                () -> assertFalse(savedData.contains("getEntities")),
                () -> assertFalse(savedData.contains("getChunk(")),
                () -> assertTrue(ticker.contains("NEXT_REVIEWS")),
                () -> assertTrue(ticker.contains("TickEvent.Phase.END")),
                () -> assertTrue(ticker.contains("onServerStarted(")),
                () -> assertTrue(ticker.contains("onServerStopping(")),
                () -> assertFalse(ticker.contains("getEntities")),
                () -> assertTrue(service.contains("isSameThread()")),
                () -> assertTrue(bootstrap.contains(
                        "VillageDefenseStateTicker::onServerTick")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
