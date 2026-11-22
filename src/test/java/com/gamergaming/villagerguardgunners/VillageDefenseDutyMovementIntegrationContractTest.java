package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefenseDutyMovementIntegrationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void goalIsLowestVggMovementOwnerAndPreservesGuardVillagersState()
            throws IOException {
        String goal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/"
                        + "GuardAutomaticDutyMovementGoal.java");
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String controller = duty("GuardAutomaticDutyMovementController.java");

        assertAll(
                () -> assertTrue(goal.contains("EnumSet.of(Goal.Flag.MOVE)")),
                () -> assertFalse(goal.contains("Goal.Flag.LOOK")),
                () -> assertTrue(mixin.contains(
                        "addGoal(5, new GuardAutomaticDutyMovementGoal")),
                () -> assertTrue(mixin.indexOf("new GuardArmoryResupplyGoal")
                        < mixin.indexOf("new GuardAutomaticDutyMovementGoal")),
                () -> assertFalse(controller.contains("setPatrolling(")),
                () -> assertFalse(controller.contains("setFollowing(")),
                () -> assertFalse(controller.contains("setPatrolPos(")),
                () -> assertFalse(controller.contains("GuardOrdersAccess.set")));
    }

    @Test
    void navigationIsLoadedChunkBoundedAndBacksOffWithoutScanning() throws IOException {
        String controller = duty("GuardAutomaticDutyMovementController.java");
        String destination = duty("VillageDefenseDutyDestination.java");

        assertAll(
                () -> assertTrue(controller.contains(
                        "VillageDefenseDutyDestination.evaluate(")),
                () -> assertTrue(destination.contains("level.hasChunk(")),
                () -> assertTrue(destination.contains("GuardGroundTraversal.safeStandingPosition(")),
                () -> assertTrue(controller.contains("com.gamergaming.villagerguardgunners.ai.navigation.GuardLocalRoutes.create(guard, destination, 32)")),
                () -> assertTrue(controller.contains("MAXIMUM_RETRY_TICKS = 200")),
                () -> assertTrue(controller.contains("PROGRESS_CHECK_TICKS = 40")),
                () -> assertTrue(controller.contains(
                        "GateStatus.STATE_UNAVAILABLE")),
                () -> assertTrue(controller.contains("requestRestaffReview(current)")),
                () -> assertTrue(controller.contains("navigation.stop()")),
                () -> assertFalse(controller.contains("getEntities")),
                () -> assertFalse(controller.contains("getChunk(")),
                () -> assertFalse(controller.contains("forceChunk")),
                () -> assertFalse(controller.contains("setChunkForced")));
    }

    @Test
    void liveGateAndDiagnosticsExposeEveryInterruptionAndMovementState()
            throws IOException {
        String gate = duty("GuardAutomaticDutyLiveGate.java");
        String commands = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/"
                        + "GuardGunnerCommands.java");
        String armory = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/"
                        + "GuardArmoryResupplyController.java");

        assertAll(
                () -> assertTrue(gate.contains("GuardSidearmAccess.getState(guard)")),
                () -> assertTrue(gate.contains("GuardSquadCommandAccess.getCommand(guard)")),
                () -> assertTrue(gate.contains("guard.isFollowing()")),
                () -> assertTrue(gate.contains("resupply.hasActiveRequest()")),
                () -> assertTrue(gate.contains("GuardRoleResolver.effectiveOrders(guard)")),
                () -> assertTrue(armory.contains("public boolean hasActiveRequest()")),
                () -> assertTrue(commands.contains("Automatic duty")),
                () -> assertTrue(commands.contains("automaticDutyDetails")));
    }

    @Test
    void releaseArtifactRequiresTheCompleteMovementRuntime() throws IOException {
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(build.contains(
                        "ai/GuardAutomaticDutyMovementGoal.class")),
                () -> assertTrue(build.contains(
                        "defense/duty/GuardAutomaticDutyLiveGate.class")),
                () -> assertTrue(build.contains(
                        "defense/duty/GuardAutomaticDutyMovementController.class")),
                () -> assertTrue(build.contains(
                        "defense/duty/VillageDefenseDutyDestination.class")));
    }

    private static String duty(String fileName) throws IOException {
        return read("src/main/java/com/gamergaming/villagerguardgunners/defense/duty/"
                + fileName);
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
