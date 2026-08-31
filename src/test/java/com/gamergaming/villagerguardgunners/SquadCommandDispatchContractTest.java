package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SquadCommandDispatchContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void dispatchIsOneShotLoadedBoundedDeterministicAndAuthorized()
            throws IOException {
        String dispatcher = squadSource("SquadCommandDispatcher.java");

        assertAll(
                () -> assertTrue(dispatcher.contains(
                        "ABSOLUTE_MAXIMUM_AFFECTED_GUARDS = 64")),
                () -> assertTrue(dispatcher.contains(
                        "ABSOLUTE_MAXIMUM_QUERIED_GUARDS = 128")),
                () -> assertTrue(dispatcher.contains(
                        "ABSOLUTE_MAXIMUM_DISPATCH_RADIUS = 128.0D")),
                () -> assertTrue(dispatcher.contains(
                        "ModConfigs.GUARD_GUNNERS.squads.commandDispatchRadius.get()")),
                () -> assertTrue(dispatcher.contains(
                        "ModConfigs.GUARD_GUNNERS.squads.maximumAffectedGuards.get()")),
                () -> assertTrue(dispatcher.contains("level.getEntities(")),
                () -> assertTrue(dispatcher.contains(
                        "EntityTypeTest.forClass(Guard.class)")),
                () -> assertTrue(dispatcher.contains(
                        "members,\n                ABSOLUTE_MAXIMUM_QUERIED_GUARDS")),
                () -> assertTrue(dispatcher.contains(
                        "GuardSquadAccess.isMemberOf(guard, squadId)")),
                () -> assertTrue(dispatcher.contains(
                        "GuardOrderPermissions.mayManage(player, guard)")),
                () -> assertTrue(dispatcher.contains(
                        ".comparingDouble((Guard guard) -> guard.distanceToSqr(player))")),
                () -> assertTrue(dispatcher.contains(
                        ".thenComparing(Guard::getUUID)")),
                () -> assertTrue(dispatcher.contains("unauthorized++")),
                () -> assertTrue(dispatcher.contains("capped++")),
                () -> assertFalse(dispatcher.contains("TickEvent")),
                () -> assertFalse(dispatcher.contains("EntityJoinLevelEvent")),
                () -> assertFalse(dispatcher.contains("forceChunk")),
                () -> assertFalse(dispatcher.contains("setChunkForced")),
                () -> assertFalse(dispatcher.contains("GuardOrdersAccess")),
                () -> assertFalse(dispatcher.contains("setItemSlot(")),
                () -> assertFalse(dispatcher.contains("GuardAmmo")));
    }

    @Test
    void ledgerExposesAirBlockAndEntityCommandsWithoutPacketsOrTicks()
            throws IOException {
        String item = read(
                "src/main/java/com/gamergaming/villagerguardgunners/item/"
                        + "SquadCommandLedgerItem.java");
        String mode = squadSource("SquadLedgerModeCodec.java");

        assertAll(
                () -> assertTrue(item.contains(
                        "InteractionResultHolder<ItemStack> use(")),
                () -> assertTrue(item.contains("player.isSecondaryUseActive()")),
                () -> assertTrue(item.contains("cycleMode(player, ledger)")),
                () -> assertTrue(item.contains("InteractionResult useOn(UseOnContext")),
                () -> assertTrue(item.contains("interactLivingEntity(")),
                () -> assertTrue(item.contains("projectedPosition(player)")),
                () -> assertTrue(item.contains("mode.requiresTarget() ? target : null")),
                () -> assertTrue(item.contains("result.applied()")),
                () -> assertTrue(item.contains("result.unauthorized()")),
                () -> assertTrue(item.contains("result.capped()")),
                () -> assertTrue(mode.contains("DEFAULT_MODE = SquadCommandType.RALLY")),
                () -> assertFalse(item.contains("Packet")),
                () -> assertFalse(item.contains("getEntitiesOfClass(")),
                () -> assertFalse(item.contains("tick(")));
    }

    @Test
    void temporaryCommandsPersistIndependentlyAndRemainVersioned()
            throws IOException {
        String command = squadSource("SquadCommand.java");
        String codec = squadSource("SquadCommandCodec.java");
        String access = read(
                "src/main/java/com/gamergaming/villagerguardgunners/api/"
                        + "GuardSquadCommandAccess.java");
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");

        assertAll(
                () -> assertTrue(command.contains("CURRENT_SCHEMA_VERSION")),
                () -> assertTrue(command.contains("issuedGameTime")),
                () -> assertTrue(command.contains("expiresGameTime")),
                () -> assertTrue(command.contains("ResourceLocation dimension")),
                () -> assertTrue(command.contains("BlockPos position")),
                () -> assertTrue(command.contains("UUID targetId")),
                () -> assertTrue(codec.contains(
                        "villagerguardgunners:squad_command")),
                () -> assertTrue(access.contains("getSquadCommandRevision")),
                () -> assertTrue(mixin.contains(
                        "SquadCommandCodec.readGuardRecord(compound)")),
                () -> assertTrue(mixin.contains(
                        "SquadCommandCodec.writeGuardRecord(")),
                () -> assertFalse(command.contains("GuardOrders")),
                () -> assertFalse(codec.contains("SquadMembershipCodec")));
    }

    @Test
    void releaseArtifactRequiresEveryStepThreeRuntimeClass() throws IOException {
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(build.contains(
                        "api/GuardSquadCommandAccess.class")),
                () -> assertTrue(build.contains("squad/SquadCommand.class")),
                () -> assertTrue(build.contains("squad/SquadCommandCodec.class")),
                () -> assertTrue(build.contains("squad/SquadCommandDispatcher.class")),
                () -> assertTrue(build.contains("squad/SquadCommandType.class")),
                () -> assertTrue(build.contains("squad/SquadLedgerModeCodec.class")));
    }

    private static String squadSource(String fileName) throws IOException {
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
