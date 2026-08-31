package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArmoryResupplyStepOneContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void stepOneContractsRemainBoundedAfterStepSixActivation()
            throws IOException {
        String documentation = read("docs/armory-resupply-contracts.md");
        String combatGoal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");
        String guardMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String armory = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/ArmoryBlockEntity.java");

        assertAll(
                () -> assertTrue(documentation.contains("IDLE -> SEARCHING")),
                () -> assertTrue(documentation.contains("low-priority")),
                () -> assertTrue(documentation.contains("never authorizes a transfer")),
                () -> assertTrue(documentation.contains("HARD_MAX_REQUESTED_UNITS")),
                () -> assertFalse(combatGoal.contains("ArmoryResupply")),
                () -> assertTrue(guardMixin.contains("GuardArmoryResupplyGoal")),
                () -> assertFalse(guardMixin.contains("ArmoryReservationBook")),
                () -> assertTrue(armory.contains("ArmoryResupplyServerServices")),
                () -> assertFalse(armory.contains("static void tick(")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
