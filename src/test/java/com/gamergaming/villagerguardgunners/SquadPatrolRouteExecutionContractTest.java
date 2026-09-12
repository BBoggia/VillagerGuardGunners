package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SquadPatrolRouteExecutionContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void oneAutomaticDutyGoalOwnsRoutesBeforePosts() throws IOException {
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/"
                + "GuardAutomaticDutyMovementGoal.java");
        String mixin = read("src/main/java/com/gamergaming/villagerguardgunners/mixin/"
                + "GuardMixin.java");
        String checkpointMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/"
                        + "WalkBackToCheckPointGoalMixin.java");

        assertAll(
                () -> assertTrue(goal.contains("EnumSet.of(Goal.Flag.MOVE)")),
                () -> assertTrue(goal.contains("routeController.hasExecutableRoute()")),
                () -> assertTrue(goal.indexOf("routeController.hasExecutableRoute()")
                        < goal.indexOf("postController.canStart()")),
                () -> assertTrue(goal.contains("MovementOwner.ROUTE")),
                () -> assertTrue(goal.contains("MovementOwner.POST")),
                () -> assertTrue(mixin.contains(
                        "addGoal(5, new GuardAutomaticDutyMovementGoal")),
                () -> assertFalse(mixin.contains("GuardPatrolRouteMovementGoal")),
                () -> assertTrue(checkpointMixin.contains(
                        "SquadPatrolRuntimePolicy.executionEnabled()")),
                () -> assertTrue(checkpointMixin.contains(
                        "SquadPatrolRouteResolver.hasLoadedWaypoint(guard)")),
                () -> assertTrue(checkpointMixin.contains(
                        "method = {\"canUse\", \"canContinueToUse\"}")));
    }

    @Test
    void executionIsBoundedLoadedAndLeavesUpstreamStateUntouched() throws IOException {
        String controller = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/duty/"
                        + "GuardPatrolRouteMovementController.java");
        String resolver = read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + "SquadPatrolRouteResolver.java");

        assertAll(
                () -> assertTrue(controller.contains(
                        "settings.maximumFailuresPerWaypoint()")),
                () -> assertTrue(controller.contains("settings.waypointDwellTicks()")),
                () -> assertTrue(controller.contains("hasChunk(")),
                () -> assertTrue(controller.contains("nearestWaypoint(")),
                () -> assertTrue(controller.contains("SKIPPING_UNREACHABLE")),
                () -> assertTrue(resolver.contains("findPatrolRoute(squadId)")),
                () -> assertFalse(controller.contains("getEntities")),
                () -> assertFalse(controller.contains("getChunk(")),
                () -> assertFalse(controller.contains("setChunkForced")),
                () -> assertFalse(controller.contains("setPatrolling(")),
                () -> assertFalse(controller.contains("setPatrolPos(")),
                () -> assertFalse(controller.contains("setFollowing(")));
    }

    @Test
    void routedGuardsReleaseDefensePostReservations() throws IOException {
        String staffing = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/duty/"
                        + "VillageDefenseStaffingRuntime.java");

        assertAll(
                () -> assertTrue(staffing.contains(
                        "SquadPatrolRuntimePolicy.executionEnabled()")),
                () -> assertTrue(staffing.contains(
                        "SquadPatrolRouteResolver.hasLoadedWaypoint(guard)")),
                () -> assertTrue(staffing.contains("patrolRouteChanged")),
                () -> assertTrue(staffing.contains("releaseGuard(guardId)")),
                () -> assertTrue(staffing.contains("if (!patrolRouteAssigned)")));
    }

    @Test
    void diagnosticsAndArtifactContainExecutionRuntime() throws IOException {
        String commands = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/"
                        + "GuardGunnerCommands.java");
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(commands.contains("capturePatrolDiagnostics()")),
                () -> assertTrue(commands.contains("Squad patrol route")),
                () -> assertTrue(build.contains(
                        "GuardPatrolRouteMovementController.class")),
                () -> assertTrue(build.contains("SquadPatrolRouteResolver.class")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
