package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GuardAmmoSharingContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void sharingIsDryTriggeredBoundedOwnerSafeAndTransactional() throws IOException {
        String sharing = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ammunition/GuardAmmoSharing.java");
        String controller = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");

        int requestReload = controller.indexOf("private void requestReload(");
        int shootInterval = controller.indexOf("private long resolveShootInterval(");
        String dryReloadPath = controller.substring(requestReload, shootInterval);

        assertAll(
                () -> assertTrue(sharing.contains("shareAmmoBetweenGuards.get()")),
                () -> assertTrue(sharing.contains("ammoSharingRadius.get()")),
                () -> assertTrue(sharing.contains("getBoundingBox().inflate(radius)")),
                () -> assertTrue(sharing.contains("getEntitiesOfClass(")),
                () -> assertTrue(sharing.contains("distanceToSqr(recipient)")),
                () -> assertTrue(sharing.contains("requireMatchingOwnerForSharing.get()")),
                () -> assertTrue(sharing.contains("ownersMayShare(")),
                () -> assertTrue(sharing.contains("donorReserveMagazines.get()")),
                () -> assertTrue(sharing.contains("GuardWeaponAmmunition.captureLoadout(donor)")),
                () -> assertTrue(sharing.contains("GuardWeaponAmmunition.assessDonorReserve(")),
                () -> assertTrue(sharing.contains("reserve.shareableUnits()")),
                () -> assertTrue(sharing.contains("ammunitionTemplate")),
                () -> assertTrue(sharing.contains("ItemStack.isSameItemSameTags")),
                () -> assertTrue(sharing.contains("instanceof IAmmo")),
                () -> assertTrue(sharing.contains("instanceof IAmmoBox")),
                () -> assertTrue(sharing.contains("AmmoCompatibility.assess(recipientGun, candidate)")),
                () -> assertTrue(sharing.contains("GuardAmmoTransactions.planInsertion")),
                () -> assertTrue(sharing.contains("GuardAmmoTransactions.transfer(")),
                () -> assertTrue(sharing.contains("GuardAmmoMaintenance.ejectEmptyFiniteBoxes(recipient)")),
                () -> assertFalse(sharing.contains("getNavigation()")),
                () -> assertFalse(sharing.contains("@SubscribeEvent")),
                () -> assertFalse(sharing.contains("TickEvent")),
                () -> assertTrue(dryReloadPath.contains(
                        "failedReloadHasUsableReserve(nativeCanReload, ammunition)")),
                () -> assertTrue(dryReloadPath.contains("GuardAmmoSharing.tryShare(guard)")),
                () -> assertTrue(dryReloadPath.indexOf("GuardAmmoSharing.tryShare(guard)")
                        > dryReloadPath.indexOf(
                                "failedReloadHasUsableReserve(nativeCanReload, ammunition)")),
                () -> assertTrue(dryReloadPath.indexOf("weaponState.markDry(")
                        > dryReloadPath.indexOf("GuardAmmoSharing.tryShare(guard)")),
                () -> assertTrue(controller.contains("lastAmmoSharingStatus")),
                () -> assertTrue(controller.contains("lastAmmoSharedItems")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
