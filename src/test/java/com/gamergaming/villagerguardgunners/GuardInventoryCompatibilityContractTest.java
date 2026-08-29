package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GuardInventoryCompatibilityContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void gunnerInventoryTracksTheUpstreamClientStatusPresentation() throws IOException {
        String screen = Files.readString(
                PROJECT_DIRECTORY.resolve(
                        "src/main/java/com/gamergaming/villagerguardgunners/client/GuardGunnerInventoryScreen.java"),
                StandardCharsets.UTF_8);
        String container = Files.readString(
                PROJECT_DIRECTORY.resolve(
                        "src/main/java/com/gamergaming/villagerguardgunners/entity/GuardGunnerContainer.java"),
                StandardCharsets.UTF_8);
        String translations = Files.readString(
                PROJECT_DIRECTORY.resolve(
                        "src/main/resources/assets/villagerguardgunners/lang/en_us.json"),
                StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(screen.contains("GuardConfig.CLIENT.guardInventoryNumbers.get()")),
                () -> assertTrue(screen.contains("EMPTY_HEART_TEXTURE_X = 16")),
                () -> assertTrue(screen.contains("FULL_HEART_TEXTURE_X = 34")),
                () -> assertTrue(screen.contains("HALF_HEART_TEXTURE_X = 43")),
                () -> assertTrue(screen.contains("textures/gui/icons.png")),
                () -> assertTrue(screen.contains("armorPoint < armor ? 34")),
                () -> assertTrue(screen.contains("int buttonY = this.topPos + 1")),
                () -> assertTrue(screen.contains("GuardAmmoSummary.capture(")),
                () -> assertTrue(screen.contains("GuardAmmoAccess.getTaCZInventoryHandler")),
                () -> assertTrue(screen.contains("AMMO_STATUS_REFRESH_TICKS = 20")),
                () -> assertTrue(screen.contains("lastAmmoFingerprintTick")),
                () -> assertTrue(screen.contains("inventoryFingerprintChanged()")),
                () -> assertTrue(screen.contains("renderAmmunitionTooltip(")),
                () -> assertTrue(screen.contains("renderCombatStatusTooltip(")),
                () -> assertTrue(screen.contains("menu.getCombatStatus()")),
                () -> assertTrue(screen.contains("handleInventoryButtonClick(")),
                () -> assertTrue(screen.contains("for (OrderField field : OrderField.values())")),
                () -> assertTrue(screen.contains("menu.getOrders()")),
                () -> assertTrue(screen.contains("incompatibleAmmoStacks()")),
                () -> assertTrue(screen.contains("reserveScanComplete()")),
                () -> assertTrue(container.contains("addDataSlot(new DataSlot()")),
                () -> assertTrue(container.contains("statusGoal.getPlayerFacingStatus()")),
                () -> assertTrue(container.contains("GuardCombatStatus.fromNetworkId(")),
                () -> assertTrue(container.contains("synchronizedOrders.withNetworkValue(")),
                () -> assertTrue(container.contains("public boolean clickMenuButton(")),
                () -> assertTrue(container.contains(
                        "!GuardOrderPermissions.mayManage(player, guard)")),
                () -> assertTrue(container.contains("current.cycle(field)")),
                () -> assertFalse(container.contains("captureDiagnostics()")),
                () -> assertTrue(translations.contains(
                        "container.villagerguardgunners.ammo.tooltip.title")),
                () -> assertTrue(translations.contains(
                        "container.villagerguardgunners.ammo.tooltip.incompatible")),
                () -> assertTrue(translations.contains(
                        "container.villagerguardgunners.ammo.tooltip.incomplete")),
                () -> assertTrue(translations.contains(
                        "container.villagerguardgunners.combat_status.ready.description")),
                () -> assertTrue(translations.contains(
                        "container.villagerguardgunners.combat_status.repositioning.description")),
                () -> assertTrue(translations.contains(
                        "container.villagerguardgunners.combat_status.weapon_restricted.description")));
    }
}
