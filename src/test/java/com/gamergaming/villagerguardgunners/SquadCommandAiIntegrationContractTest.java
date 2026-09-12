package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SquadCommandAiIntegrationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void runtimeValidationIsConstantTimeFailClosedAndSafetyAuthoritative()
            throws IOException {
        String runtime = squad("SquadCommandRuntime.java");
        String dispatcher = squad("SquadCommandDispatcher.java");

        assertAll(
                () -> assertTrue(runtime.contains(
                        "MAXIMUM_RUNTIME_COMMAND_DISTANCE = 256.0D")),
                () -> assertTrue(runtime.contains("command.isExpired(")),
                () -> assertTrue(runtime.contains("command.dimension().equals(")),
                () -> assertTrue(runtime.contains("level.isInWorldBounds(position)")),
                () -> assertTrue(runtime.contains(
                        "GuardRulesOfEngagement.classify(guard, candidate)")),
                () -> assertTrue(runtime.contains("level.getEntity(command.targetId())")),
                () -> assertTrue(runtime.contains("guard.setTarget(candidate)")),
                () -> assertTrue(runtime.contains("releaseOwnedFocusTarget")),
                () -> assertTrue(runtime.contains("replaceCommand(")),
                () -> assertTrue(dispatcher.contains(
                        "SquadCommandRuntime.replaceCommand(guard")),
                () -> assertTrue(runtime.contains("clearIfCurrent(guard, command)")),
                () -> assertFalse(runtime.contains("getEntitiesOfClass(")),
                () -> assertFalse(runtime.contains("forceChunk")),
                () -> assertFalse(runtime.contains("setChunkForced")));
    }

    @Test
    void outsideCombatGoalOwnsOnlyMovementAndLeavesPermanentStateUntouched()
            throws IOException {
        String goal = ai("SquadCommandMovementGoal.java");
        String controller = ai("SquadCommandMovementController.java");

        assertAll(
                () -> assertTrue(goal.contains("EnumSet.of(Goal.Flag.MOVE)")),
                () -> assertFalse(goal.contains("Goal.Flag.LOOK")),
                () -> assertTrue(goal.contains("target == null || !target.isAlive()")),
                () -> assertTrue(goal.contains("synchronizeSharedFocusTarget()")),
                () -> assertTrue(controller.contains(
                        "NativeGunController.isGun(guard.getMainHandItem())")),
                () -> assertTrue(controller.contains("case RALLY")),
                () -> assertTrue(controller.contains("case DEFEND")),
                () -> assertTrue(controller.contains("case HOLD")),
                () -> assertTrue(controller.contains("case FALL_BACK")),
                () -> assertTrue(controller.contains("level.hasChunk(")),
                () -> assertTrue(controller.contains(
                        "pathRecalculationIntervalTicks.get()")),
                () -> assertFalse(controller.contains("setPatrol")),
                () -> assertFalse(controller.contains("setFollowing")),
                () -> assertFalse(controller.contains("GuardOrdersAccess.set")),
                () -> assertFalse(controller.contains("getEntitiesOfClass(")));
    }

    @Test
    void gunGoalAppliesCommandsBeforeTacticsWithoutBypassingNativeSafety()
            throws IOException {
        String combatGoal = ai("GuardGunCombatGoal.java");
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");

        assertAll(
                () -> assertTrue(combatGoal.contains("commandMovement.synchronize(true)")),
                () -> assertTrue(combatGoal.contains("commandMovement.apply(")),
                () -> assertTrue(combatGoal.contains("moveTowardCombatTarget(")),
                () -> assertTrue(combatGoal.contains("defendOverride")),
                () -> assertTrue(combatGoal.contains("gunController.tick(")),
                () -> assertTrue(combatGoal.contains("GuardFriendlyFireSafety")),
                () -> assertTrue(mixin.contains(
                        "addGoal(1, new SquadCommandMovementGoal")),
                () -> assertTrue(mixin.contains(
                        "addGoal(2, villagerGuardGunners$coverageGunGoal")),
                () -> assertFalse(combatGoal.contains("setPatrolling(")),
                () -> assertFalse(combatGoal.contains("setFollowing(")));
    }

    @Test
    void releaseArtifactRequiresEveryStepFourRuntimeClass() throws IOException {
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(build.contains(
                        "ai/SquadCommandMovementController.class")),
                () -> assertTrue(build.contains("ai/SquadCommandMovementGoal.class")),
                () -> assertTrue(build.contains("squad/SquadCommandRuntime.class")));
    }

    @Test
    void userGuideAndManualMatrixCoverStepFourBehavior() throws IOException {
        String guide = read("docs/squad-commands.md");
        String readme = read("README.md");
        String checklist = read("docs/release-checklist.md");

        assertAll(
                () -> assertTrue(guide.contains("Rally |")),
                () -> assertTrue(guide.contains("Defend |")),
                () -> assertTrue(guide.contains("Focus Target |")),
                () -> assertTrue(guide.contains("Fall Back |")),
                () -> assertTrue(guide.contains("256-block runtime sanity limit")),
                () -> assertTrue(readme.contains("docs/squad-commands.md")),
                () -> assertTrue(checklist.contains("SQUAD-MOVE-01")),
                () -> assertTrue(checklist.contains("SQUAD-FOCUS-01")),
                () -> assertTrue(checklist.contains("SQUAD-RESUME-01")),
                () -> assertTrue(checklist.contains("SQUAD-PERF-01")));
    }

    private static String ai(String fileName) throws IOException {
        return read("src/main/java/com/gamergaming/villagerguardgunners/ai/" + fileName);
    }

    private static String squad(String fileName) throws IOException {
        return read("src/main/java/com/gamergaming/villagerguardgunners/squad/" + fileName);
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
