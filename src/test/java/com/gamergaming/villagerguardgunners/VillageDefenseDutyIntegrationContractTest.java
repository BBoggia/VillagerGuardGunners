package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefenseDutyIntegrationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void acceptedAnchorsAuthorSlotsAndPendingRecoveryReassertsThem() throws IOException {
        String lifecycle = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageBuildingAnchorLifecycle.java");
        int anchorClaim = lifecycle.indexOf("claimAnchor(identity, pending)");
        int firstDutyClaim = lifecycle.indexOf("VillageDefenseDutySavedData.get(liveLevel)");
        int schedule = lifecycle.indexOf("VillageBuildingActivationRecovery.schedule(");
        int pendingRotation = lifecycle.indexOf("Rotation rotation = pending.rotation()");
        int recoveryNetworkClaim = lifecycle.indexOf(
                "VillageDefenseNetworkSavedData.get(liveLevel).claimBuilding(identity)",
                pendingRotation);
        int recoveryDutyClaim = lifecycle.indexOf(
                "VillageDefenseDutySavedData.get(liveLevel)", firstDutyClaim + 1);

        assertAll(
                () -> assertTrue(anchorClaim >= 0),
                () -> assertTrue(firstDutyClaim > anchorClaim),
                () -> assertTrue(schedule > firstDutyClaim),
                () -> assertTrue(pendingRotation >= 0),
                () -> assertTrue(recoveryNetworkClaim > pendingRotation),
                () -> assertTrue(recoveryDutyClaim > recoveryNetworkClaim),
                () -> assertTrue(recoveryDutyClaim > pendingRotation));
    }

    @Test
    void slotQueriesRemainIndexOnlyAndDoNotInspectWorldState() throws IOException {
        String savedData = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/duty/"
                        + "VillageDefenseDutySavedData.java");
        String layouts = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/duty/"
                        + "VillageDefenseDutyLayouts.java");
        String commands = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/"
                        + "VillageDefenseCommands.java");

        assertAll(
                () -> assertTrue(savedData.contains("layouts.get(buildingId)")),
                () -> assertTrue(savedData.contains("slotsById.get")),
                () -> assertTrue(savedData.contains("maximumResults")),
                () -> assertTrue(savedData.contains("MAXIMUM_QUERY_BUILDINGS")),
                () -> assertTrue(savedData.contains("coverageForBuildings")),
                () -> assertTrue(layouts.contains("GuardPostSpawner.spawnPosition")),
                () -> assertTrue(commands.contains("Authored duties:")),
                () -> assertFalse(savedData.contains("getEntities")),
                () -> assertFalse(savedData.contains("getChunk(")),
                () -> assertFalse(savedData.contains("hasChunkAt")),
                () -> assertFalse(savedData.contains("setChunkForced")),
                () -> assertFalse(layouts.contains("getBlockState")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
