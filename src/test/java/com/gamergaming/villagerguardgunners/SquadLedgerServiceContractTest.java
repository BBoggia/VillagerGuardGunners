package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SquadLedgerServiceContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void membershipWorkflowIsServerAuthoritativeAuthorizedAndInteractionOnly()
            throws IOException {
        String service = source("SquadLedgerService.java");

        assertAll(
                () -> assertTrue(service.contains("player.level().isClientSide")),
                () -> assertTrue(service.contains("player.level() != guard.level()")),
                () -> assertTrue(service.contains("!player.isAlive()")),
                () -> assertTrue(service.contains("guard.isRemoved()")),
                () -> assertTrue(service.contains("player.distanceToSqr(guard)")),
                () -> assertTrue(service.contains("player.isSpectator()")),
                () -> assertTrue(service.contains("guard.getTarget() == player")),
                () -> assertTrue(service.contains(
                        "GuardOrderPermissions.mayManage(player, guard)")),
                () -> assertTrue(service.contains("heldStack.getCount() != 1")),
                () -> assertTrue(service.contains("ModItems.SQUAD_COMMAND_LEDGER.get()")),
                () -> assertTrue(service.contains("player.isSecondaryUseActive()")),
                () -> assertFalse(service.contains("getEntitiesOfClass(")),
                () -> assertFalse(service.contains("TickEvent")),
                () -> assertFalse(service.contains("Packet")),
                () -> assertFalse(service.contains("shrink(")),
                () -> assertFalse(service.contains("grow(")));
    }

    @Test
    void stableIdentityRecoveryRenameAndExactReassignmentAreExplicit()
            throws IOException {
        String service = source("SquadLedgerService.java");

        assertAll(
                () -> assertTrue(service.contains("SquadRegistrySavedData.get(server)")),
                () -> assertTrue(service.contains("SquadMembership.assigned(")),
                () -> assertTrue(service.contains("registryEntryExisted")),
                () -> assertTrue(service.contains("RECOVERED_AND_BOUND")),
                () -> assertTrue(service.contains("requestedRename(ledger, binding)")),
                () -> assertTrue(service.contains("registry.rename(")),
                () -> assertTrue(service.contains("MAXIMUM_ID_GENERATION_ATTEMPTS")),
                () -> assertTrue(service.contains("PendingReassignment")),
                () -> assertTrue(service.contains("guardId.equals(requestedGuardId)")),
                () -> assertTrue(service.contains(
                        "sourceSquadId.equals(requestedSourceSquadId)")),
                () -> assertTrue(service.contains(
                        "destinationSquadId.equals(requestedDestinationSquadId)")),
                () -> assertTrue(service.contains("gameTime <= expiresAtGameTime")),
                () -> assertTrue(service.contains("onServerStopping(ServerStoppingEvent")),
                () -> assertTrue(service.contains(
                        "onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent")),
                () -> assertTrue(service.contains(
                        "removeIf(pending -> pending.isExpired(gameTime))")),
                () -> assertFalse(service.contains("GuardOrdersAccess")),
                () -> assertFalse(service.contains("setItemSlot(")));
    }

    @Test
    void deliberateSneakUseRoutesBeforeOtherGuardInteractions() throws IOException {
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String interaction = source("SquadLedgerInteractions.java");
        String bootstrap = read(
                "src/main/java/com/gamergaming/villagerguardgunners/"
                        + "VillagerGuardGunners.java");

        int squadRoute = mixin.indexOf("SquadLedgerInteractions.findLedgerHand");
        int ordersRoute = mixin.indexOf("GuardOrderTemplateInteractions.findLedgerHand");
        int ammunitionRoute = mixin.indexOf("GuardAmmoHandler.isAmmo(heldStack)");
        assertAll(
                () -> assertTrue(squadRoute >= 0 && squadRoute < ordersRoute),
                () -> assertTrue(ordersRoute < ammunitionRoute),
                () -> assertTrue(interaction.contains("player.isSecondaryUseActive()")),
                () -> assertTrue(interaction.contains("player.getMainHandItem().isEmpty()")),
                () -> assertTrue(interaction.contains("InteractionHand.OFF_HAND")),
                () -> assertTrue(interaction.contains("InteractionResult.SUCCESS")),
                () -> assertTrue(interaction.contains("InteractionResult.CONSUME")),
                () -> assertTrue(bootstrap.contains(
                        "SquadLedgerService::onServerStopping")),
                () -> assertTrue(bootstrap.contains(
                        "SquadLedgerService::onPlayerLoggedOut")),
                () -> assertFalse(interaction.contains("getEntitiesOfClass(")),
                () -> assertFalse(interaction.contains("tick(")));
    }

    private static String source(String fileName) throws IOException {
        return read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + fileName);
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
