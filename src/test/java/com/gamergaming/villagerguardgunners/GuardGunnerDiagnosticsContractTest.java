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

final class GuardGunnerDiagnosticsContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void administratorCommandIsExplicitAndHasNoRecurringRuntimeHook() throws IOException {
        String main = read("src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");
        String command = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/GuardGunnerCommands.java");

        assertAll(
                () -> assertTrue(main.contains("GuardGunnerCommands::register")),
                () -> assertTrue(command.contains("Commands.literal(\"vgg\")")),
                () -> assertTrue(command.contains("Commands.literal(\"inspect\")")),
                () -> assertTrue(command.contains("source.hasPermission(REQUIRED_PERMISSION_LEVEL)")),
                () -> assertTrue(command.contains("EntityArgument.entity()")),
                () -> assertTrue(command.contains("gunGoal.captureDiagnostics()")),
                () -> assertFalse(command.contains("TickEvent")),
                () -> assertFalse(command.contains("addListener")),
                () -> assertFalse(command.contains("schedule")));
    }

    @Test
    void descriptiveSafetyQueryStaysOutOfTheHotCombatPath() throws IOException {
        String goal = read("src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");
        String safety = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireSafety.java");
        String controller = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");

        assertAll(
                () -> assertTrue(goal.contains("public Diagnostics captureDiagnostics()")),
                () -> assertTrue(goal.contains("GuardFriendlyFireSafety.diagnoseClearShot(")),
                () -> assertTrue(safety.contains("public static DiagnosticResult diagnoseClearShot(")),
                () -> assertTrue(controller.contains("public Diagnostics captureDiagnostics()")),
                () -> assertTrue(controller.contains("lastShootResult = result")),
                () -> assertFalse(controller.substring(
                        controller.indexOf("private void tickSafely("),
                        controller.indexOf("public float getSafetyPadding"))
                        .contains("captureDiagnostics")));
    }

    @Test
    void correctnessSuiteCoversThePreferredVersusMaximumRangeBoundary() throws IOException {
        String gameTests = read(
                "src/gametest/java/com/gamergaming/villagerguardgunners/gametest/GuardGunnerGameTests.java");

        assertAll(
                () -> assertTrue(gameTests.contains(
                        "guardGoalFiresBeyondPreferredRangeWithinConfiguredMaximum")),
                () -> assertTrue(gameTests.contains("initial.distance() > initial.preferredRange()")),
                () -> assertTrue(gameTests.contains("initial.distance() <= initial.maximumRange()")),
                () -> assertTrue(gameTests.contains("goal.tick()")),
                () -> assertTrue(gameTests.contains("shootTimestamp >= 0L")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
