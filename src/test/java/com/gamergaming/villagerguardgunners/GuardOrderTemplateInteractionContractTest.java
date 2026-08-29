package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GuardOrderTemplateInteractionContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void guardInteractionRoutesLedgersBeforeAmmunitionWithoutAddingPackets()
            throws IOException {
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String interaction = read(
                "src/main/java/com/gamergaming/villagerguardgunners/orders/GuardOrderTemplateInteractions.java");

        int ledgerRoute = mixin.indexOf("GuardOrderTemplateInteractions.findLedgerHand");
        int ammunitionRoute = mixin.indexOf("GuardAmmoHandler.isAmmo(heldStack)");
        assertAll(
                () -> assertTrue(ledgerRoute >= 0 && ledgerRoute < ammunitionRoute),
                () -> assertTrue(mixin.contains("method = \"mobInteract\"")),
                () -> assertTrue(mixin.contains("GuardOrderTemplateInteractions.interact(")),
                () -> assertTrue(interaction.contains("player.level().isClientSide")),
                () -> assertTrue(interaction.contains("InteractionResult.SUCCESS")),
                () -> assertTrue(interaction.contains("InteractionResult.CONSUME")),
                () -> assertTrue(interaction.contains("player.isSecondaryUseActive()")),
                () -> assertTrue(interaction.contains("DecodeStatus.BLANK")),
                () -> assertTrue(interaction.contains("GuardOrderTemplateService.capture(")),
                () -> assertTrue(interaction.contains("GuardOrderTemplateService.apply(")),
                () -> assertFalse(interaction.contains("Packet")),
                () -> assertFalse(interaction.contains("getEntitiesOfClass(")),
                () -> assertFalse(interaction.contains("tick(")));
    }

    @Test
    void offhandFallbackAndEveryOperationMessageRemainExplicit() throws IOException {
        String interaction = read(
                "src/main/java/com/gamergaming/villagerguardgunners/orders/GuardOrderTemplateInteractions.java");
        String language = read(
                "src/main/resources/assets/villagerguardgunners/lang/en_us.json");

        assertAll(
                () -> assertTrue(interaction.contains("player.getMainHandItem().isEmpty()")),
                () -> assertTrue(interaction.contains("player.getOffhandItem()")),
                () -> assertTrue(interaction.contains("InteractionHand.OFF_HAND")),
                () -> assertTrue(language.contains("orders_ledger.captured")),
                () -> assertTrue(language.contains("orders_ledger.overwritten")),
                () -> assertTrue(language.contains("orders_ledger.applied")),
                () -> assertTrue(language.contains("orders_ledger.already_matches")),
                () -> assertTrue(language.contains("orders_ledger.already_written")),
                () -> assertTrue(language.contains("orders_ledger.corrupt")),
                () -> assertTrue(language.contains("orders_ledger.unsupported")),
                () -> assertTrue(language.contains("orders_ledger.unauthorized")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
