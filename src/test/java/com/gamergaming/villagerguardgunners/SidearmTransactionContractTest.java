package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SidearmTransactionContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void activationAndReturnMoveCompleteStacksThroughOneTransactionLayer()
            throws IOException {
        String transactions = read(
                "src/main/java/com/gamergaming/villagerguardgunners/sidearm/"
                        + "GuardSidearmTransactions.java");

        assertAll(
                () -> assertTrue(transactions.contains(
                        "public static SidearmTransitionResult activate(")),
                () -> assertTrue(transactions.contains(
                        "public static SidearmTransitionResult returnPrimary(")),
                () -> assertTrue(transactions.contains(
                        "guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY)")),
                () -> assertTrue(transactions.contains("storage.commitActivation(")),
                () -> assertTrue(transactions.contains("storage.commitReturn(")),
                () -> assertTrue(transactions.contains("ItemStack.isSameItemSameTags")),
                () -> assertTrue(transactions.contains(
                        "SidearmTransitionResult.activated()")),
                () -> assertTrue(transactions.contains(
                        "SidearmTransitionResult.returned()")));
    }

    @Test
    void failedCommitsRestoreSnapshotsOrRetainBothWeaponsForRecovery()
            throws IOException {
        String transactions = read(
                "src/main/java/com/gamergaming/villagerguardgunners/sidearm/"
                        + "GuardSidearmTransactions.java");
        String storage = read(
                "src/main/java/com/gamergaming/villagerguardgunners/sidearm/"
                        + "GuardSidearmStorage.java");

        assertAll(
                () -> assertTrue(transactions.contains("captureTransactionSnapshot()")),
                () -> assertTrue(transactions.contains("restoreTransactionSnapshot(")),
                () -> assertTrue(transactions.contains("storageBefore.matches(storage)")),
                () -> assertTrue(transactions.contains("retainForRecovery(sidearm, primary)")),
                () -> assertTrue(transactions.contains(
                        "SidearmTransitionResult.rollbackFailed(before)")),
                () -> assertTrue(storage.contains("StorageSnapshot")),
                () -> assertTrue(storage.contains(
                        "replaceStacks(withoutActiveToken(sidearm), primary)")),
                () -> assertTrue(storage.contains("clearActiveContext()")));
    }

    @Test
    void transactionMutationsRemainServerBoundAndOutsideCombatTicks()
            throws IOException {
        String transactions = read(
                "src/main/java/com/gamergaming/villagerguardgunners/sidearm/"
                        + "GuardSidearmTransactions.java");
        String combatGoal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");

        assertAll(
                () -> assertTrue(transactions.contains("!guard.level().isClientSide")),
                () -> assertTrue(transactions.contains("guard.getServer().isSameThread()")),
                () -> assertTrue(transactions.contains("nativeSwapSafe")),
                () -> assertFalse(transactions.contains("getEntitiesOfClass(")),
                () -> assertFalse(transactions.contains("for (")),
                () -> assertFalse(transactions.contains("while (")),
                () -> assertFalse(combatGoal.contains("GuardSidearmTransactions")));
    }

    @Test
    void naturalLoadoutAuthenticationUsesTheValidatedEffectivePrimaryView()
            throws IOException {
        String codec = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                        + "NaturalGuardLoadoutProvenanceCodec.java");
        String access = read(
                "src/main/java/com/gamergaming/villagerguardgunners/api/"
                        + "GuardSidearmAccess.java");
        String guardMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/"
                        + "GuardMixin.java");
        String population = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                        + "NaturalGuardPopulationIndex.java");

        assertAll(
                () -> assertTrue(codec.contains("matchesIssuedGun(\n            Guard guard,")),
                () -> assertTrue(codec.contains(
                        "GuardSidearmAccess.getEffectivePrimaryCopy(guard)")),
                () -> assertTrue(access.contains("getEffectivePrimaryCopy(Guard guard)")),
                () -> assertTrue(guardMixin.contains(
                        "state == SidearmState.SIDEARM_ACTIVE")),
                () -> assertTrue(guardMixin.contains("STOWED_PRIMARY_SLOT")),
                () -> assertTrue(guardMixin.contains(
                        "state == SidearmState.RECOVERY_REQUIRED")),
                () -> assertTrue(population.contains(
                        "NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(\n                        guard,")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
