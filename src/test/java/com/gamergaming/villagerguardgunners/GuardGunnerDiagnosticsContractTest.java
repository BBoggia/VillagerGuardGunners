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
                () -> assertTrue(command.contains("\"Cached tactics\"")),
                () -> assertTrue(command.contains("\"Cached envelope\"")),
                () -> assertTrue(command.contains("tactical.metadataReadable()")),
                () -> assertTrue(command.contains("snapshot.retreatDistance()")),
                () -> assertTrue(command.contains("snapshot.minimumRange()")),
                () -> assertTrue(command.contains("snapshot.validFiringWindow()")),
                () -> assertTrue(command.contains("snapshot.withinFiringWindow()")),
                () -> assertTrue(command.contains("snapshot.requiredVisibleTicks()")),
                () -> assertTrue(command.contains("snapshot.aimToleranceDegrees()")),
                () -> assertTrue(command.contains("\"Rules of engagement\"")),
                () -> assertTrue(command.contains("snapshot.engagementMode()")),
                () -> assertTrue(command.contains("snapshot.engagementReason()")),
                () -> assertTrue(command.contains("Last shot safety")),
                () -> assertTrue(command.contains("safety.entityId()")),
                () -> assertTrue(command.contains("GuardWeaponAmmunition.captureSummary(")),
                () -> assertTrue(command.contains("GuardAmmoAccess.getTaCZInventoryHandler")),
                () -> assertTrue(command.contains("\"Primary ammo\"")),
                () -> assertTrue(command.contains("\"Sidearm ammo\"")),
                () -> assertTrue(command.contains("ammunition.primary()")),
                () -> assertTrue(command.contains("ammunition.sidearm()")),
                () -> assertTrue(command.contains("ammunition.reserveUnits()")),
                () -> assertTrue(command.contains("ammunition.incompatibleAmmoStacks()")),
                () -> assertTrue(command.contains("ammunition.unknownAmmoStacks()")),
                () -> assertTrue(command.contains("ammunition.reserveScanComplete()")),
                () -> assertTrue(command.contains("nativeState.lastAmmoSharingStatus()")),
                () -> assertTrue(command.contains("nativeState.lastAmmoSharedItems()")),
                () -> assertTrue(command.contains("\"Natural loadout\"")),
                () -> assertTrue(command.contains("NaturalGuardLoadoutDiagnostics.capture(guard)")),
                () -> assertTrue(command.contains("naturalLoadoutDetails(")),
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
                () -> assertTrue(goal.contains("gunController.lastShotSafety()")),
                () -> assertTrue(safety.contains("public static DiagnosticResult diagnoseClearShot(")),
                () -> assertTrue(controller.contains("public Diagnostics captureDiagnostics()")),
                () -> assertTrue(controller.contains("lastShootResult = result")),
                () -> assertFalse(controller.substring(
                        controller.indexOf("private void tickSafely("),
                        controller.indexOf("public float getProjectileSafetyPadding"))
                        .contains("captureDiagnostics")));
    }

    @Test
    void correctnessSuiteCoversEveryLiveTacticalRangeBoundary() throws IOException {
        String gameTests = read(
                "src/gametest/java/com/gamergaming/villagerguardgunners/gametest/GuardGunnerGameTests.java");

        assertAll(
                () -> assertTrue(gameTests.contains(
                        "guardGoalFiresBeyondPreferredRangeWithinConfiguredMaximum")),
                () -> assertTrue(gameTests.contains("initial.distance() > initial.preferredRange()")),
                () -> assertTrue(gameTests.contains("initial.distance() <= initial.maximumRange()")),
                () -> assertTrue(gameTests.contains(
                        "profileMaximumFiringRangeControlsLiveGoal")),
                () -> assertTrue(gameTests.contains(
                        "explosiveMinimumFiringDistanceControlsLiveGoal")),
                () -> assertTrue(gameTests.contains("FireDecision.TOO_CLOSE")),
                () -> assertTrue(gameTests.contains("FireDecision.OUT_OF_RANGE")),
                () -> assertTrue(gameTests.contains("goal.tick()")),
                () -> assertTrue(gameTests.contains("shootTimestamp >= 0L")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
