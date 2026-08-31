package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArmoryResupplyStepThreeContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void reservationsAndWithdrawalsStayTransientBoundedAndRuntimeDormant()
            throws IOException {
        String documentation = read("docs/armory-resupply-contracts.md");
        String book = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/ArmoryReservationBook.java");
        String withdrawal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/ArmoryAmmunitionWithdrawal.java");
        String guardMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String combatGoal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");

        assertAll(
                () -> assertTrue(documentation.contains("simulate-before-commit")),
                () -> assertTrue(documentation.contains("indivisible")),
                () -> assertTrue(book.contains("HARD_MAX_ACTIVE_RESERVATIONS")),
                () -> assertFalse(book.contains("BlockEntity")),
                () -> assertFalse(book.contains("Level")),
                () -> assertTrue(withdrawal.contains("GuardAmmoTransactions.transfer(")),
                () -> assertTrue(withdrawal.contains("ArmoryResupplyGate.evaluate(")),
                () -> assertTrue(withdrawal.contains("hasChunkAt")),
                () -> assertFalse(withdrawal.contains("getChunk(")),
                () -> assertFalse(guardMixin.contains("ArmoryReservationBook")),
                () -> assertFalse(combatGoal.contains("ArmoryReservationBook")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
