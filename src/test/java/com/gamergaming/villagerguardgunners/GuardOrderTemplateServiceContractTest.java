package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GuardOrderTemplateServiceContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void serviceIsServerAuthoritativePermissionCheckedAndInteractionOnly()
            throws IOException {
        String service = source();
        String permissions = read(
                "src/main/java/com/gamergaming/villagerguardgunners/orders/GuardOrderPermissions.java");

        assertAll(
                () -> assertTrue(service.contains("player.level().isClientSide")),
                () -> assertTrue(service.contains("player.level() != guard.level()")),
                () -> assertTrue(service.contains("!player.isAlive()")),
                () -> assertTrue(service.contains("guard.isRemoved()")),
                () -> assertTrue(service.contains("player.distanceToSqr(guard)")),
                () -> assertTrue(service.contains("player.isSpectator()")),
                () -> assertTrue(service.contains("guard.getTarget() == player")),
                () -> assertTrue(service.contains("GuardOrderPermissions.mayManage(player, guard)")),
                () -> assertFalse(service.contains("GuardVillagers.hotvChecker")),
                () -> assertTrue(permissions.contains("player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)")),
                () -> assertTrue(permissions.contains("guard.getPlayerReputation(player)")),
                () -> assertTrue(permissions.contains(">= GuardConfig.reputationRequirement")),
                () -> assertTrue(permissions.contains("ownerId.equals(player.getUUID())")),
                () -> assertFalse(permissions.contains("GuardVillagers.hotvChecker")),
                () -> assertTrue(service.contains("ModItems.ORDERS_LEDGER.get()")),
                () -> assertTrue(service.contains("player.getInventory().setChanged()")),
                () -> assertFalse(service.contains("getEntitiesOfClass(")),
                () -> assertFalse(service.contains("Packet")),
                () -> assertFalse(service.contains("tick(")),
                () -> assertFalse(service.contains("setItemInHand(")),
                () -> assertFalse(service.contains("setItemSlot(")),
                () -> assertFalse(service.contains("shrink(")),
                () -> assertFalse(service.contains("grow(")));
    }

    @Test
    void captureAndApplyAreCompleteAtomicAndNoOpAware() throws IOException {
        String service = source();

        assertAll(
                () -> assertTrue(service.contains("GuardOrderTemplate.writeTo(")),
                () -> assertTrue(service.contains("GuardOrdersAccess.getOrders(guard)")),
                () -> assertTrue(service.contains("templateOrders.equals(currentOrders)")),
                () -> assertTrue(service.contains("GuardOrdersAccess.setOrders(guard, templateOrders)")),
                () -> assertTrue(service.contains("OperationStatus.TEMPLATE_ALREADY_WRITTEN")),
                () -> assertTrue(service.contains("OperationStatus.ALREADY_MATCHES")),
                () -> assertTrue(service.contains("OperationStatus.CORRUPT_TEMPLATE")),
                () -> assertTrue(service.contains("OperationStatus.UNSUPPORTED_VERSION")));
    }

    private static String source() throws IOException {
        return read(
                "src/main/java/com/gamergaming/villagerguardgunners/orders/GuardOrderTemplateService.java");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
