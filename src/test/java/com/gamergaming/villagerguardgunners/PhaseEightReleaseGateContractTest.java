package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class PhaseEightReleaseGateContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void developmentGameTestsStayOutOfProductionSources() {
        Path productionTests = projectDirectory.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners/gametest/GuardGunnerGameTests.java");
        Path correctnessTests = projectDirectory.resolve(
                "src/gametest/java/com/gamergaming/villagerguardgunners/gametest/GuardGunnerGameTests.java");
        Path performanceTests = projectDirectory.resolve(
                "src/perftest/java/com/gamergaming/villagerguardgunners/gametest/PhaseEightPerformanceGameTests.java");

        assertAll(
                () -> assertFalse(Files.isRegularFile(productionTests)),
                () -> assertTrue(Files.isRegularFile(correctnessTests)),
                () -> assertTrue(Files.isRegularFile(performanceTests)));
    }

    @Test
    void buildSeparatesCorrectnessAndPerformanceSuites() throws IOException {
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(build.contains("gameTest {")),
                () -> assertTrue(build.contains("performanceGameTest {")),
                () -> assertTrue(build.contains("gradleProperty('phase8Performance')")),
                () -> assertTrue(build.contains("sourceSets.performanceGameTest")),
                () -> assertTrue(build.contains("generateIsolatedGameTestArena")),
                () -> assertTrue(build.contains("generatedGameTestArenaDirectory")),
                () -> assertTrue(build.contains("${mod_id}_performance,minecraft")),
                () -> assertTrue(build.contains("run-gametest/world")),
                () -> assertTrue(build.contains("run-phase8-performance/world")),
                () -> assertTrue(build.contains(
                        "run-phase8-performance/config/villagerguardgunners/guard_gunners.toml")),
                () -> assertTrue(build.contains("reports/phase8-performance")),
                () -> assertTrue(build.contains("tasks.register('verifyPhase8PerformanceReports')")),
                () -> assertTrue(build.contains("report.guards != expectedScenario.guards")),
                () -> assertTrue(build.contains("report.weapon != expectedScenario.weapon")),
                () -> assertTrue(build.contains("report.workload != expectedScenario.workload")),
                () -> assertTrue(build.contains("report.sidearmLoadoutsObserved")),
                () -> assertTrue(build.contains("report.sidearmActiveGuardsAtEnd")),
                () -> assertTrue(build.contains("validFinalSidearmStates")),
                () -> assertTrue(build.contains("finalRecoveryRequired != 0")),
                () -> assertTrue(build.contains("nonNegativeEvidenceFields")),
                () -> assertTrue(build.contains("jsonScenarios != requiredScenarios.keySet()")),
                () -> assertTrue(build.contains("jfrScenarios != requiredScenarios.keySet()")),
                () -> assertTrue(build.contains("jdk.jfr.consumer.RecordingFile")),
                () -> assertTrue(build.contains("executionSampleCount == 0L")),
                () -> assertTrue(build.contains("report.productionArtifactSha256")),
                () -> assertTrue(build.contains("relativeTimingBudgets")),
                () -> assertTrue(build.contains("baseline.p95Mspt")),
                () -> assertTrue(build.contains("baseline.p99Mspt")));

        String performanceHarness = read(
                "src/perftest/java/com/gamergaming/villagerguardgunners/gametest/"
                        + "PhaseEightPerformanceGameTests.java");
        assertAll(
                () -> assertTrue(performanceHarness.contains(
                        "protectedFriendlyOffset(testTick, index)")),
                () -> assertTrue(performanceHarness.contains("phase < 30L")),
                () -> assertTrue(performanceHarness.contains("phase < 90L")),
                () -> assertTrue(performanceHarness.contains("return -6.0D")),
                () -> assertTrue(performanceHarness.contains("return 6.0D")),
                () -> assertTrue(performanceHarness.contains("retainOnlyAddOnGoals(guard)")),
                () -> assertTrue(performanceHarness.contains(
                        "workload == Workload.CLEAR_LANES")));
    }

    @Test
    void releaseCandidateEvidenceBindsTheArtifactAndPerformanceMatrix() throws IOException {
        String build = read("build.gradle");
        String evidenceTask = build.substring(
                build.indexOf("tasks.register('assembleReleaseCandidateEvidence')"));

        assertAll(
                () -> assertTrue(evidenceTask.contains("verifyReleaseArtifact")),
                () -> assertTrue(evidenceTask.contains("verifyPhase8PerformanceReports")),
                () -> assertTrue(build.contains("MessageDigest.getInstance('SHA-256')")),
                () -> assertTrue(evidenceTask.contains("release-candidate.json")),
                () -> assertTrue(evidenceTask.contains("performanceJsonReports")),
                () -> assertTrue(evidenceTask.contains("performanceJfrProfiles")),
                () -> assertTrue(evidenceTask.contains("performanceEvidenceFiles")),
                () -> assertTrue(evidenceTask.contains("performanceEvidence:")),
                () -> assertTrue(evidenceTask.contains("sha256OfFile(file)")),
                () -> assertTrue(evidenceTask.contains("schemaVersion  : 4")),
                () -> assertTrue(evidenceTask.contains("naturalLoadoutAuthoringPaths")),
                () -> assertTrue(evidenceTask.contains("naturalLoadoutAuthoring:")),
                () -> assertTrue(evidenceTask.contains("Natural-loadout authoring evidence is stale")),
                () -> assertTrue(evidenceTask.contains("correctnessGameTests")),
                () -> assertTrue(evidenceTask.contains("manualCompatibility")),
                () -> assertTrue(evidenceTask.contains("ammunitionGuide")),
                () -> assertTrue(evidenceTask.contains("naturalLoadoutGuide")),
                () -> assertTrue(evidenceTask.contains("automatedAmmunitionGate")),
                () -> assertFalse(evidenceTask.contains("generatedAt")),
                () -> assertFalse(evidenceTask.contains("Instant.now")));
    }

    @Test
    void releaseArtifactGateRejectsDevelopmentAndLegacySurface() throws IOException {
        String build = read("build.gradle");
        String gate = build.substring(build.indexOf("tasks.register('verifyReleaseArtifact')"));

        assertAll(
                () -> assertTrue(gate.contains("compileGameTestJava")),
                () -> assertTrue(gate.contains("compilePerformanceGameTestJava")),
                () -> assertTrue(gate.contains("reobfJar")),
                () -> assertTrue(gate.contains("'/gametest/'")),
                () -> assertTrue(gate.contains("'villagerguardgunners_gametest'")),
                () -> assertTrue(gate.contains("'GuardBullet'")),
                () -> assertTrue(gate.contains("'GuardBehaviorTree'")),
                () -> assertTrue(gate.contains("'Implementation-Timestamp'")),
                () -> assertTrue(gate.contains("metadata.count('[[dependencies.villagerguardgunners]]')")),
                () -> assertTrue(gate.contains("command/GuardGunnerCommands.class")),
                () -> assertTrue(gate.contains(
                        "command/NaturalGuardLoadoutCommandService.class")),
                () -> assertTrue(gate.contains(
                        "command/NaturalGuardLoadoutCommands.class")),
                () -> assertTrue(gate.contains(
                        "command/SquadRegistryCommands.class")),
                () -> assertTrue(gate.contains("ammunition/GuardAmmoSharing.class")),
                () -> assertTrue(gate.contains("ammunition/GuardDroppedAmmoPickup.class")),
                () -> assertTrue(gate.contains("ammunition/GuardWeaponAmmunition.class")),
                () -> assertTrue(gate.contains("mixin/GuardMixin")),
                () -> assertTrue(gate.contains("mixin/ItemEntityMixin")),
                () -> assertTrue(gate.contains("mixin/RaiseShieldGoalMixin")),
                () -> assertTrue(gate.contains("mixin/VillagerToGuardMixin")),
                () -> assertTrue(gate.contains("spawn/NaturalGuardSpawnIntegration.class")),
                () -> assertTrue(gate.contains("api/GuardNaturalLoadoutAccess.class")),
                () -> assertTrue(gate.contains(
                        "spawn/NaturalGuardLoadoutConditionSpec.class")),
                () -> assertTrue(gate.contains(
                        "spawn/NaturalGuardLoadoutContextCapture.class")),
                () -> assertTrue(gate.contains("spawn/NaturalGuardLoadoutFormat.class")),
                () -> assertTrue(gate.contains(
                        "spawn/NaturalGuardLoadoutMatchContext.class")),
                () -> assertTrue(gate.contains(
                        "spawn/NaturalGuardLoadoutReloadSnapshot.class")),
                () -> assertTrue(gate.contains("spawn/NaturalGuardLoadoutParser.class")),
                () -> assertTrue(gate.contains(
                        "spawn/NaturalGuardLoadoutCompatibilityAnalyzer.class")),
                () -> assertTrue(gate.contains("spawn/NaturalGuardLoadoutIssuance.class")),
                () -> assertTrue(gate.contains("spawn/NaturalGuardLoadoutProvenanceCodec.class")),
                () -> assertTrue(gate.contains("spawn/NaturalGuardLoadoutDiagnostics.class")),
                () -> assertTrue(gate.contains("commonClassesWithClientReferences")),
                () -> assertTrue(gate.contains("net/minecraft/client/")),
                () -> assertTrue(gate.contains("mixinMetadata.client")),
                () -> assertTrue(build.contains("dependsOn tasks.named('verifyReleaseArtifact')")));
    }

    @Test
    void releaseDocumentationAndCiReplaceTheMdkBoilerplate() throws IOException {
        Path readme = projectDirectory.resolve("README.md");
        Path legacyReadme = projectDirectory.resolve("README.txt");
        Path checklist = projectDirectory.resolve("docs/release-checklist.md");
        Path changelog = projectDirectory.resolve("CHANGELOG.md");
        Path workflow = projectDirectory.resolve(".github/workflows/ci.yml");
        String readmeText = Files.readString(readme, StandardCharsets.UTF_8);
        String checklistText = Files.readString(checklist, StandardCharsets.UTF_8);
        String workflowText = Files.readString(workflow, StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(Files.isRegularFile(readme)),
                () -> assertFalse(Files.exists(legacyReadme)),
                () -> assertTrue(Files.isRegularFile(checklist)),
                () -> assertTrue(Files.isRegularFile(changelog)),
                () -> assertTrue(Files.isRegularFile(workflow)),
                () -> assertFalse(readmeText.contains("Source installation information for modders")),
                () -> assertTrue(readmeText.contains("/vgg inspect <guard>")),
                () -> assertTrue(readmeText.contains("docs/release-checklist.md")),
                () -> assertTrue(checklistText.contains("Result | Gun/config and notes")),
                () -> assertTrue(checklistText.contains("Multiple observing clients")),
                () -> assertTrue(checklistText.contains("SIDEARM-VISUAL-01")),
                () -> assertTrue(checklistText.contains("SIDEARM-RELOAD-01")),
                () -> assertTrue(checklistText.contains("SIDEARM-SHIELD-01")),
                () -> assertTrue(checklistText.contains("SIDEARM-LIFE-01")),
                () -> assertTrue(checklistText.contains("SIDEARM-AMMO-01")),
                () -> assertTrue(checklistText.contains("SIDEARM-AMMO-02")),
                () -> assertTrue(checklistText.contains("SIDEARM-PACK-01")),
                () -> assertTrue(checklistText.contains("SIDEARM-CONFIG-01")),
                () -> assertTrue(checklistText.contains("SIDEARM-RECOVERY-01")),
                () -> assertTrue(buildContainsRequiredSidearmCases()),
                () -> assertTrue(workflowText.contains("java-version: 17")),
                () -> assertTrue(workflowText.contains(
                        "./gradlew --no-daemon clean check --warning-mode all")),
                () -> assertTrue(workflowText.contains("actions/upload-artifact@v4")));
    }

    private static boolean buildContainsRequiredSidearmCases() throws IOException {
        String build = read("build.gradle");
        return build.contains("requiredSidearmCases")
                && build.contains("requiredSidearmCases.any");
    }

    @Test
    void performanceHarnessPreservesThePhaseEightWorkloadAndEvidenceContract() throws IOException {
        String harness = read(
                "src/perftest/java/com/gamergaming/villagerguardgunners/gametest/PhaseEightPerformanceGameTests.java");

        assertAll(
                () -> assertTrue(harness.contains("WARMUP_TICKS = 200")),
                () -> assertTrue(harness.contains("SAMPLE_TICKS = 1_000")),
                () -> assertTrue(harness.contains("Duration.ofMillis(5L)")),
                () -> assertTrue(harness.contains("Configuration.getConfiguration(\"default\")")),
                () -> assertFalse(harness.contains("Configuration.getConfiguration(\"profile\")")),
                () -> assertTrue(harness.contains("unarmed-idle-baseline-100")),
                () -> assertTrue(harness.contains("idle-armed-100")),
                () -> assertTrue(harness.contains("semi-10")),
                () -> assertTrue(harness.contains("semi-25")),
                () -> assertTrue(harness.contains("semi-50")),
                () -> assertTrue(harness.contains("automatic-10")),
                () -> assertTrue(harness.contains("automatic-25")),
                () -> assertTrue(harness.contains("automatic-50")),
                () -> assertTrue(harness.contains("automatic-shotgun-10")),
                () -> assertTrue(harness.contains("explosive-10")),
                () -> assertTrue(harness.contains("obstacle-navigation-25")),
                () -> assertTrue(harness.contains("protected-crossing-25")),
                () -> assertTrue(harness.contains("dropped-ammo-pressure-100")),
                () -> assertTrue(harness.contains("dry-sharing-pressure-50")),
                () -> assertTrue(harness.contains("dry-primary-sidearm-switch-50")),
                () -> assertTrue(harness.contains("mixed-explosive-standoff-sidearm-20")),
                () -> assertTrue(harness.contains("idle-armed-sidearms-100")),
                () -> assertTrue(harness.contains("Workload.DRY_PRIMARY_SIDEARM_SWITCH")),
                () -> assertTrue(harness.contains(
                        "Workload.MIXED_EXPLOSIVE_STANDOFF_SIDEARM")),
                () -> assertTrue(harness.contains("Workload.IDLE_WITH_SIDEARMS")),
                () -> assertTrue(harness.contains("delaysTargetUntilSample()")),
                () -> assertTrue(harness.contains("testTick < WARMUP_TICKS")),
                () -> assertTrue(harness.contains("WARMUP_TICKS - 5L")),
                () -> assertTrue(harness.contains("weapon == Weapon.EXPLOSIVE")),
                () -> assertTrue(harness.contains(
                        "GuardSidearmAccess.getConfiguredSidearmInventory(guard)")),
                () -> assertTrue(harness.contains("sidearmLoadoutsObserved")),
                () -> assertTrue(harness.contains("sidearmActiveGuardsAtEnd")),
                () -> assertTrue(harness.contains("minimumSidearmActiveGuards")),
                () -> assertTrue(harness.contains("maximumSidearmActiveGuards")),
                () -> assertTrue(harness.contains("sidearmActivationTriggerTick")),
                () -> assertTrue(harness.contains("firstObservedSidearmActivationTick")),
                () -> assertTrue(harness.contains("perGuardFirstSidearmActivationTick")),
                () -> assertTrue(harness.contains("expectedSidearmActive")),
                () -> assertTrue(harness.contains("activeGunId")),
                () -> assertTrue(harness.contains("SidearmRoleEvidence")),
                () -> assertTrue(harness.contains("moveProtectedFriendlies(")),
                () -> assertTrue(harness.contains("maintainScenarioTargets(")),
                () -> assertTrue(harness.contains("targetAnchors")),
                () -> assertTrue(harness.contains("target.setNoGravity(true)")),
                () -> assertTrue(harness.contains("Attributes.KNOCKBACK_RESISTANCE")),
                () -> assertTrue(harness.contains("discardScenarioBullets(helper, guards)")),
                () -> assertTrue(harness.contains("wrapped.isRunning()")),
                () -> assertTrue(harness.contains("scenarioZOffset(name)")),
                () -> assertTrue(harness.contains("scenarioIndex * 512.0D")),
                () -> assertTrue(harness.contains("nativeTaCZShootCount")),
                () -> assertTrue(harness.contains("acceptedServerGunShootEvents")),
                () -> assertTrue(harness.contains("AcceptedShotRecorder")),
                () -> assertTrue(harness.contains("GunShootEvent event")),
                () -> assertTrue(harness.contains("NominalShotClock")),
                () -> assertTrue(harness.contains("countsAtOpen")),
                () -> assertFalse(harness.contains("getDataHolder().shootCount")),
                () -> assertTrue(harness.contains("nativeAmmoConsumed")),
                () -> assertTrue(harness.contains("ammoRemainingAtStart")),
                () -> assertTrue(harness.contains("tacz:rpg_rocket")),
                () -> assertTrue(harness.contains("return guardCount * 2")),
                () -> assertTrue(harness.contains("return guardCount")),
                () -> assertTrue(harness.contains("minimumAcceptedShotEvidence")),
                () -> assertTrue(harness.contains("minimumAcceptedPerActiveGuardShotEvidence")),
                () -> assertTrue(harness.contains("perGuardAcceptedShotEvidence")),
                () -> assertTrue(harness.contains("perGuardFinalState")),
                () -> assertTrue(harness.contains("ammoSharingAttemptsObserved")),
                () -> assertTrue(harness.contains("persistentDroppedAmmoEntities")),
                () -> assertTrue(harness.contains("minimumAmmoSharingAttempts(")),
                () -> assertTrue(harness.contains("minimumPersistentDroppedAmmo(")),
                () -> assertTrue(harness.contains("discardScenarioBullets(")),
                () -> assertTrue(harness.contains("bullet.discard()")),
                () -> assertTrue(harness.contains("workload == Workload.PROTECTED_LANES")),
                () -> assertTrue(harness.contains("absoluteTimingGateApplied")),
                () -> assertTrue(harness.contains("relativeTimingGateApplied")),
                () -> assertTrue(harness.contains("productionArtifactSha256")),
                () -> assertTrue(harness.contains("configProfile")),
                () -> assertTrue(harness.contains("default-v9")),
                () -> assertTrue(harness.contains("guardGunnerConfig")),
                () -> assertTrue(harness.contains("configSnapshot()")),
                () -> assertTrue(harness.contains("profileSnapshot(")),
                () -> assertTrue(harness.contains("config.tactics.typeProfiles.get()")),
                () -> assertTrue(harness.contains("config.tactics.gunOverrides.get()")),
                () -> assertTrue(harness.contains("config.engagement.mode.get()")),
                () -> assertTrue(harness.contains("config.engagement.additionalTargets.get()")),
                () -> assertTrue(harness.contains("config.ammunition.pickupDroppedAmmo.get()")),
                () -> assertTrue(harness.contains("config.ammunition.shareAmmoBetweenGuards.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.enabled.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.checkIntervalTicks.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.searchRadius.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.maximumCandidateArmories.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.lowReserveMagazines.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.targetReserveMagazines.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.movementSpeed.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.requestLifetimeTicks.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.reservationLifetimeTicks.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.successCooldownTicks.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.noSupplyRetryTicks.get()")),
                () -> assertTrue(harness.contains("config.armoryResupply.unreachableRetryTicks.get()")),
                () -> assertTrue(harness.contains("config.naturalLoadouts.enabled.get()")),
                () -> assertTrue(harness.contains("config.naturalLoadouts.enabledSpawnSources.get()")),
                () -> assertTrue(harness.contains("config.squads.commandDispatchRadius.get()")),
                () -> assertTrue(harness.contains("config.squads.maximumAffectedGuards.get()")),
                () -> assertTrue(harness.contains("config.squads.positionalCommandDurationTicks.get()")),
                () -> assertTrue(harness.contains("config.squads.focusCommandDurationTicks.get()")),
                () -> assertTrue(harness.contains("config.squads.defendRadius.get()")),
                () -> assertTrue(harness.contains("config.squads.airCommandRange.get()")),
                () -> assertTrue(harness.contains("naturalDifficultySnapshot(")),
                () -> assertTrue(harness.contains("explosiveStandoffRadiusMultiplier")),
                () -> assertTrue(harness.contains("recording.dump(")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
