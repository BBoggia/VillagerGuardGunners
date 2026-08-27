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
                () -> assertTrue(build.contains("${mod_id}_performance,minecraft")),
                () -> assertTrue(build.contains("run-gametest/world")),
                () -> assertTrue(build.contains("run-phase8-performance/world")),
                () -> assertTrue(build.contains(
                        "run-phase8-performance/config/villagerguardgunners/guard_gunners.toml")),
                () -> assertTrue(build.contains("reports/phase8-performance")),
                () -> assertTrue(build.contains("tasks.register('verifyPhase8PerformanceReports')")));
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
                () -> assertTrue(gate.contains("'GuardBullet'")),
                () -> assertTrue(gate.contains("'GuardBehaviorTree'")),
                () -> assertTrue(gate.contains("'Implementation-Timestamp'")),
                () -> assertTrue(gate.contains("metadata.count('[[dependencies.villagerguardgunners]]')")),
                () -> assertTrue(gate.contains("command/GuardGunnerCommands.class")),
                () -> assertTrue(gate.contains("mixin/GuardMixin")),
                () -> assertTrue(gate.contains("mixin/RaiseShieldGoalMixin")),
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
                () -> assertTrue(workflowText.contains("java-version: 17")),
                () -> assertTrue(workflowText.contains("./gradlew --no-daemon check")),
                () -> assertTrue(workflowText.contains("actions/upload-artifact@v4")));
    }

    @Test
    void performanceHarnessPreservesThePhaseEightWorkloadAndEvidenceContract() throws IOException {
        String harness = read(
                "src/perftest/java/com/gamergaming/villagerguardgunners/gametest/PhaseEightPerformanceGameTests.java");

        assertAll(
                () -> assertTrue(harness.contains("WARMUP_TICKS = 100")),
                () -> assertTrue(harness.contains("SAMPLE_TICKS = 1_000")),
                () -> assertTrue(harness.contains("Duration.ofMillis(5L)")),
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
                () -> assertTrue(harness.contains("moveProtectedFriendlies(")),
                () -> assertTrue(harness.contains("nativeTaCZShootCount")),
                () -> assertTrue(harness.contains("nativeAmmoConsumed")),
                () -> assertTrue(harness.contains("ammoRemainingAtStart")),
                () -> assertTrue(harness.contains("tacz:rpg_rocket")),
                () -> assertTrue(harness.contains("return guardCount * 2")),
                () -> assertTrue(harness.contains("minimumAcceptedShotEvidence")),
                () -> assertTrue(harness.contains("workload == Workload.PROTECTED_LANES")),
                () -> assertTrue(harness.contains("absoluteTimingGateApplied")),
                () -> assertTrue(harness.contains("configProfile")),
                () -> assertTrue(harness.contains("guardGunnerConfig")),
                () -> assertTrue(harness.contains("configSnapshot()")),
                () -> assertTrue(harness.contains("recording.dump(")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
