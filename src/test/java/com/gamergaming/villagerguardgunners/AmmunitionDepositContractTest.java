package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AmmunitionDepositContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void quickDepositIsAuthorizedCompatibleTransactionalAndInteractionDriven()
            throws IOException {
        String deposit = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ammunition/GuardAmmoDeposits.java");
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String translations = read(
                "src/main/resources/assets/villagerguardgunners/lang/en_us.json");

        assertAll(
                () -> assertTrue(deposit.contains("player.level().isClientSide")),
                () -> assertTrue(deposit.contains("player.isSecondaryUseActive()")),
                () -> assertTrue(deposit.contains("guard.getTarget() == player")),
                () -> assertTrue(deposit.contains("GuardVillagers.hotvChecker(player, guard)")),
                () -> assertTrue(deposit.contains("!guard.isEffectiveAi()")),
                () -> assertTrue(deposit.contains("gunStack.getItem() instanceof IGun")),
                () -> assertTrue(deposit.contains("GuardAmmoSummary.capture(gunStack.copy(), null)")),
                () -> assertTrue(deposit.contains("case DUMMY, NATIVE_INFINITE")),
                () -> assertTrue(deposit.contains("case NONE")),
                () -> assertTrue(deposit.contains("GUN_DOES_NOT_USE_PHYSICAL_AMMO")),
                () -> assertTrue(deposit.contains("AmmoCompatibility.assess(gunStack, offered)")),
                () -> assertTrue(deposit.contains("GuardAmmoAccess.getAmmoHandler(guard)")),
                () -> assertTrue(deposit.contains("GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard)")),
                () -> assertTrue(deposit.contains("GuardAmmoTransactions.insert")),
                () -> assertTrue(deposit.contains("player.getAbilities().instabuild")),
                () -> assertFalse(deposit.contains("getEntities")),
                () -> assertFalse(deposit.contains("@SubscribeEvent")),
                () -> assertTrue(mixin.contains("method = \"mobInteract\"")),
                () -> assertTrue(mixin.contains("player.isSecondaryUseActive()")),
                () -> assertTrue(mixin.contains("GuardAmmoHandler.isAmmo(heldStack)")),
                () -> assertTrue(mixin.contains("InteractionResult.CONSUME")),
                () -> assertTrue(translations.contains(
                        "message.villagerguardgunners.ammo_deposit.success")),
                () -> assertTrue(translations.contains(
                        "message.villagerguardgunners.ammo_deposit.incompatible")),
                () -> assertTrue(translations.contains(
                        "message.villagerguardgunners.ammo_deposit.inactive_guard")),
                () -> assertTrue(translations.contains(
                        "message.villagerguardgunners.ammo_deposit.physical_ammo_unused")),
                () -> assertTrue(translations.contains(
                        "message.villagerguardgunners.ammo_deposit.failed")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
