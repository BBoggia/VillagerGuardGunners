package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArmoryResupplyStepFourContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void movementIsBoundedInterruptibleLoadedOnlyAndRuntimeDormant()
            throws IOException {
        String documentation = read("docs/armory-resupply-contracts.md");
        String limits = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/ArmoryResupplyLimits.java");
        String controller = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/ArmoryResupplyMovementController.java");
        String reservationBook = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/ArmoryReservationBook.java");
        String guardMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String combatGoal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");

        assertAll(
                () -> assertTrue(documentation.contains("at most eight approaches")),
                () -> assertTrue(documentation.contains("getChunkNow")),
                () -> assertTrue(limits.contains("HARD_MAX_PATH_ATTEMPTS = 8")),
                () -> assertTrue(limits.contains("HARD_MAX_NATIVE_PATH_RESUMES = 4")),
                () -> assertTrue(limits.contains("HARD_MAX_TRAVEL_DISTANCE_SQUARED")),
                () -> assertTrue(controller.contains("ArmoryResupplyLiveGate.evaluate(")),
                () -> assertTrue(controller.contains("level.hasChunkAt(armoryPosition)")),
                () -> assertTrue(controller.contains("level.hasChunkAt(approach)")),
                () -> assertTrue(controller.contains("navigation.getTargetPos()")),
                () -> assertTrue(controller.contains("DEFAULT_PROGRESS_CHECK_TICKS")),
                () -> assertTrue(controller.contains("DEFAULT_TRIP_REVALIDATION_TICKS")),
                () -> assertTrue(controller.contains("validateForTravel(")),
                () -> assertTrue(controller.contains("requestedKey.equals(requestKey)")),
                () -> assertTrue(controller.contains("pauseOwnedNavigation()")),
                () -> assertTrue(controller.contains("HARD_MAX_NATIVE_PATH_RESUMES")),
                () -> assertTrue(reservationBook.contains("movement hot path")),
                () -> assertFalse(controller.contains("getChunk(")),
                () -> assertFalse(controller.contains("getEntities")),
                () -> assertFalse(controller.contains("setPatrolling")),
                () -> assertFalse(controller.contains("setFollowing")),
                () -> assertFalse(guardMixin.contains("ArmoryResupplyMovementController")),
                () -> assertFalse(combatGoal.contains("ArmoryResupplyMovementController")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
