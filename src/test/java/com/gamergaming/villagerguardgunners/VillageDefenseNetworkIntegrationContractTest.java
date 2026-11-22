package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefenseNetworkIntegrationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void acceptedBuildingAnchorsRegisterOnTheServerThread() throws IOException {
        String lifecycle = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageBuildingAnchorLifecycle.java");
        int claim = lifecycle.indexOf("claimAnchor(identity, pending)");
        int register = lifecycle.indexOf(
                "VillageDefenseNetworkSavedData.get(liveLevel)");

        assertAll(
                () -> assertTrue(claim >= 0),
                () -> assertTrue(register > claim),
                () -> assertTrue(lifecycle.contains(
                        "anchorClaim == VillageBuildingSavedData.ClaimResult.CLAIMED")),
                () -> assertTrue(lifecycle.contains(
                        "VillageBuildingSavedData.ClaimResult.ALREADY_CLAIMED")),
                () -> assertTrue(lifecycle.contains(".claimBuilding(identity)")));
    }

    @Test
    void membershipUsesSavedIndexesWithoutWorldOrEntityQueries() throws IOException {
        String networks = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/network/"
                        + "VillageDefenseNetworkSavedData.java");
        String buildings = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageBuildingSavedData.java");

        assertAll(
                () -> assertTrue(networks.contains("buildingsByChunk")),
                () -> assertTrue(networks.contains("nearbyBuildingIds(")),
                () -> assertTrue(networks.contains("reconciledWithBuildingRegistry")),
                () -> assertTrue(buildings.contains("recordsSnapshot()")),
                () -> assertFalse(networks.contains("getEntities")),
                () -> assertFalse(networks.contains("getChunk(")),
                () -> assertFalse(networks.contains("hasChunk(")),
                () -> assertFalse(networks.contains("setChunkForced")),
                () -> assertFalse(networks.contains("forceChunk")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
