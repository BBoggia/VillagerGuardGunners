package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PerGuardOrdersContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void persistentOrdersReachEveryRelevantRuntimePolicy() throws IOException {
        String mixin = source("mixin/GuardMixin.java");
        String goal = source("ai/GuardGunCombatGoal.java");
        String controller = source("combat/NativeGunController.java");
        String engagement = source("combat/engagement/GuardRulesOfEngagement.java");
        String pickup = source("ammunition/GuardDroppedAmmoPickup.java");
        String sharing = source("ammunition/GuardAmmoSharing.java");
        String command = source("command/GuardGunnerCommands.java");
        String permissions = source("orders/GuardOrderPermissions.java");
        String menu = source("entity/GuardGunnerContainer.java");

        assertAll(
                () -> assertTrue(mixin.contains("implements GuardAmmoAccess")),
                () -> assertTrue(mixin.contains("GuardBurstSafetyAccess")),
                () -> assertTrue(mixin.contains("GuardOrdersAccess")),
                () -> assertTrue(mixin.contains("GuardNaturalLoadoutAccess")),
                () -> assertTrue(mixin.contains("villagerguardgunners:orders")),
                () -> assertTrue(mixin.contains("GuardOrders.load(")),
                () -> assertTrue(mixin.contains("orders.save()")),
                () -> assertTrue(goal.contains("movementOrder.holdsPosition()")),
                () -> assertTrue(goal.contains("isWeaponAllowedByOrders()")),
                () -> assertTrue(controller.contains("trackedOrdersRevision")),
                () -> assertTrue(controller.contains("ordersRevisionIsCurrent()")),
                () -> assertTrue(controller.contains("effectiveMaximumRange()")),
                () -> assertTrue(controller.contains("tacticalProfileOverride()")),
                () -> assertTrue(engagement.contains("resolveEngagementMode(")),
                () -> assertTrue(pickup.contains("permitsAmmoPickup(true)")),
                () -> assertTrue(sharing.contains("permitsAmmoSharing(true)")),
                () -> assertTrue(command.contains("Per-guard orders")),
                () -> assertTrue(menu.contains("GuardOrderPermissions.mayManage(player, guard)")),
                () -> assertTrue(permissions.contains("ownerId.equals(player.getUUID())")),
                () -> assertFalse(permissions.contains("GuardVillagers.hotvChecker")));
    }

    @Test
    void ordersUseExistingMenuSynchronizationWithoutAddingTickScansOrPackets() throws IOException {
        String menu = source("entity/GuardGunnerContainer.java");
        String screen = source("client/GuardGunnerInventoryScreen.java");
        String orders = source("orders/GuardOrders.java");

        assertAll(
                () -> assertTrue(menu.contains("for (OrderField field : OrderField.values())")),
                () -> assertTrue(menu.contains("addDataSlot(new DataSlot()")),
                () -> assertTrue(menu.contains("public boolean clickMenuButton(")),
                () -> assertTrue(screen.contains("handleInventoryButtonClick(")),
                () -> assertTrue(orders.contains("public static final GuardOrders DEFAULT")),
                () -> assertFalse(orders.contains("new Thread(")),
                () -> assertFalse(orders.contains("getEntitiesOfClass(")),
                () -> assertFalse(orders.contains("Packet")));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve("src/main/java/com/gamergaming/villagerguardgunners")
                        .resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
