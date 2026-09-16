package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DefenseDutyPatrolConfigurationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void schemaSixteenAddsBoundedPolicyWithoutWeakeningHardLimits()
            throws IOException {
        String config = read("src/main/java/com/gamergaming/villagerguardgunners/config/"
                + "VillagerGuardGunnersConfig.java");
        String patrolSettings = read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + "PatrolRouteExecutionSettings.java");

        assertAll(
                () -> assertTrue(config.contains("CONFIG_VERSION = 22")),
                () -> assertTrue(config.contains("villageDefense.restoreVersionSixteenDefaults()")),
                () -> assertTrue(config.contains("squads.restoreVersionSixteenDefaults()")),
                () -> assertTrue(config.contains("patrolRouteExecution")),
                () -> assertTrue(config.contains("automaticDuties")),
                () -> assertTrue(patrolSettings.contains("MAXIMUM_DWELL_TICKS = 1_200")),
                () -> assertTrue(patrolSettings.contains(
                        "MAXIMUM_FAILURES_PER_WAYPOINT = 8")));
    }

    @Test
    void liveConsumersShareOneFailClosedPolicyBoundary() throws IOException {
        String stateService = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseStateService.java");
        String stateTicker = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseStateTicker.java");
        String staffing = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/duty/"
                        + "VillageDefenseStaffingRuntime.java");
        String routeController = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/duty/"
                        + "GuardPatrolRouteMovementController.java");
        String checkpointMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/"
                        + "WalkBackToCheckPointGoalMixin.java");

        assertAll(
                () -> assertTrue(stateService.contains("VillageDefenseRuntimePolicy.timings()")),
                () -> assertTrue(stateTicker.contains("VillageDefenseRuntimePolicy.timings()")),
                () -> assertTrue(staffing.contains(
                        "VillageDefenseRuntimePolicy.automaticDutiesEnabled()")),
                () -> assertTrue(staffing.contains(
                        "SquadPatrolRuntimePolicy.executionEnabled()")),
                () -> assertTrue(routeController.contains(
                        "SquadPatrolRuntimePolicy.settings()")),
                () -> assertTrue(checkpointMixin.contains(
                        "SquadPatrolRuntimePolicy.executionEnabled()")));
    }

    @Test
    void disablingExecutionDoesNotDisableRouteAuthoringOrPersistence()
            throws IOException {
        String editor = read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + "SquadPatrolRouteEditor.java");
        String registry = read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + "SquadRegistrySavedData.java");

        assertAll(
                () -> assertFalse(editor.contains("patrolRouteExecution")),
                () -> assertFalse(editor.contains("SquadPatrolRuntimePolicy")),
                () -> assertFalse(registry.contains("patrolRouteExecution")),
                () -> assertTrue(registry.contains("replacePatrolRoute")));
    }

    @Test
    void publicSurfacesAndReleaseArtifactCarryTheNewPolicy() throws IOException {
        String language = read(
                "src/main/resources/assets/villagerguardgunners/lang/en_us.json");
        String documentation = read("docs/configuration.md");
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(language.contains(
                        "squads.patrolRouteExecution.desc")),
                () -> assertTrue(language.contains(
                        "villageDefense.automaticDuties.desc")),
                () -> assertTrue(documentation.contains(
                        "`squads.patrolRouteExecution`")),
                () -> assertTrue(documentation.contains(
                        "`villageDefense.automaticDuties`")),
                () -> assertTrue(build.contains("PatrolRouteExecutionSettings.class")),
                () -> assertTrue(build.contains("SquadPatrolRuntimePolicy.class")),
                () -> assertTrue(build.contains("VillageDefenseRuntimePolicy.class")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
