package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SidearmRuntimeContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void combatGoalEvaluatesActivationAfterNativeReloadAndReturnBeforeFire()
            throws IOException {
        String goal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");
        int tickStart = goal.indexOf("public void tick()");
        int activeReturn = goal.indexOf(
                "if (sidearmController.isSidearmActive())",
                tickStart);
        int ordinaryNativeTick = goal.lastIndexOf("gunController.tick(");
        int activationEvaluation = goal.lastIndexOf(
                "evaluatePrimarySidearm(currentTarget");

        assertAll(
                () -> assertTrue(goal.contains("new GuardSidearmRuntimeController(guard)")),
                () -> assertTrue(activeReturn >= 0 && activeReturn < ordinaryNativeTick),
                () -> assertTrue(
                        ordinaryNativeTick >= 0 && ordinaryNativeTick < activationEvaluation),
                () -> assertTrue(goal.contains("suppressSidearmFireForReturn()")),
                () -> assertTrue(goal.contains("requiresLifecycleCleanup(")));
    }

    @Test
    void runtimeAssessmentIsTriggeredAndRateLimitedWithoutWorldScanning()
            throws IOException {
        String runtime = read(
                "src/main/java/com/gamergaming/villagerguardgunners/sidearm/"
                        + "GuardSidearmRuntimeController.java");
        int reasonGate = runtime.indexOf(
                "if (reason == SidearmActivationReason.NONE || target == null)");
        int assessment = runtime.indexOf("assessStoredSidearm(", reasonGate);

        assertAll(
                () -> assertTrue(reasonGate >= 0 && reasonGate < assessment),
                () -> assertTrue(runtime.contains("lastEvaluationGameTick")),
                () -> assertTrue(runtime.contains("lastEvaluationOutcome")),
                () -> assertTrue(runtime.contains("nextAssessmentGameTick")),
                () -> assertTrue(runtime.contains("getContentRevision()")),
                () -> assertFalse(runtime.contains("getEntitiesOfClass(")),
                () -> assertFalse(runtime.contains("getNearest")),
                () -> assertFalse(runtime.contains("createPath(")),
                () -> assertFalse(runtime.contains("for (")),
                () -> assertFalse(runtime.contains("while (")));
    }

    @Test
    void nativeSwapGateUsesOnlyBoundActionStateAndTransactionsRefreshCaches()
            throws IOException {
        String nativeController = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/"
                        + "NativeGunController.java");
        String runtime = read(
                "src/main/java/com/gamergaming/villagerguardgunners/sidearm/"
                        + "GuardSidearmRuntimeController.java");

        assertAll(
                () -> assertTrue(nativeController.contains("isSidearmSwapSafe()")),
                () -> assertTrue(nativeController.contains("isNativeBurstActive(")),
                () -> assertTrue(nativeController.contains("getSynReloadState()")),
                () -> assertTrue(nativeController.contains("getSynIsBolting()")),
                () -> assertTrue(nativeController.contains("getSynMeleeCoolDown()")),
                () -> assertTrue(nativeController.contains("meleePrepTickCount")),
                () -> assertTrue(nativeController.contains("getSynDrawCoolDown()")),
                () -> assertTrue(nativeController.contains("getSynShootCoolDown()")),
                () -> assertTrue(runtime.contains("refreshAfterSidearmTransaction()")),
                () -> assertTrue(runtime.contains("isCurrentWeaponSidearmCompatible()")),
                () -> assertTrue(runtime.contains("DEFAULT_MINIMUM_ACTIVE_TICKS")),
                () -> assertTrue(runtime.contains("DEFAULT_RETURN_MARGIN_BLOCKS")));
    }

    @Test
    void positionalSquadMovementYieldsForARequiredLifecycleReturn()
            throws IOException {
        String squadGoal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/"
                        + "SquadCommandMovementGoal.java");

        assertAll(
                () -> assertTrue(squadGoal.contains("GuardSidearmAccess.getState(guard)")),
                () -> assertTrue(squadGoal.contains("!= SidearmState.PRIMARY_ACTIVE")));
    }

    @Test
    void recoveryRequiredStateSuppressesCombatAndCommandMovement()
            throws IOException {
        String combatGoal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/"
                        + "GuardGunCombatGoal.java");
        String runtime = read(
                "src/main/java/com/gamergaming/villagerguardgunners/sidearm/"
                        + "GuardSidearmRuntimeController.java");
        String squadGoal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/"
                        + "SquadCommandMovementGoal.java");

        assertAll(
                () -> assertTrue(runtime.contains(
                        "public boolean requiresRecoverySuppression()")),
                () -> assertTrue(runtime.contains("SidearmState.RECOVERY_REQUIRED")),
                () -> assertTrue(combatGoal.contains(
                        "sidearmController.requiresRecoverySuppression()")),
                () -> assertTrue(combatGoal.contains(
                        "gunController.prepareForSidearmTransition()")),
                () -> assertTrue(squadGoal.contains(
                        "!= SidearmState.PRIMARY_ACTIVE")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
