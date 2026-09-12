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

final class ArmoryResupplyStepFiveContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void schemaNinePublishesEveryBoundedArmoryPolicyInput() throws IOException {
        String config = read(
                "src/main/java/com/gamergaming/villagerguardgunners/config/VillagerGuardGunnersConfig.java");
        String settings = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/ArmoryResupplySettings.java");

        assertAll(
                () -> assertTrue(config.contains("CONFIG_VERSION = 21")),
                () -> assertTrue(config.contains("previousVersion < 9")),
                () -> assertTrue(config.contains("restoreVersionNineDefaults()")),
                () -> assertTrue(config.contains("public ArmorySettings armoryResupply")),
                () -> assertTrue(config.contains("ValidatedBoolean enabled")),
                () -> assertTrue(config.contains("ValidatedInt checkIntervalTicks")),
                () -> assertTrue(config.contains("ValidatedDouble searchRadius")),
                () -> assertTrue(config.contains("ValidatedInt maximumCandidateArmories")),
                () -> assertTrue(config.contains("RelatedIntSetting lowReserveMagazines")),
                () -> assertTrue(config.contains("RelatedIntSetting targetReserveMagazines")),
                () -> assertTrue(config.contains("ValidatedDouble movementSpeed")),
                () -> assertTrue(config.contains("ValidatedInt requestLifetimeTicks")),
                () -> assertTrue(config.contains("ValidatedInt reservationLifetimeTicks")),
                () -> assertTrue(config.contains("ValidatedInt successCooldownTicks")),
                () -> assertTrue(config.contains("ValidatedInt noSupplyRetryTicks")),
                () -> assertTrue(config.contains("ValidatedInt unreachableRetryTicks")),
                () -> assertTrue(config.contains("parentEnabled && enabled.get()")),
                () -> assertTrue(settings.contains("validReserveThresholds()")),
                () -> assertTrue(settings.contains("HARD_MAX_CANDIDATE_ARMORIES")),
                () -> assertTrue(settings.contains("HARD_MAX_REQUEST_LIFETIME_TICKS")),
                () -> assertTrue(settings.contains("HARD_MAX_RESERVATION_LIFETIME_TICKS")),
                () -> assertTrue(settings.contains("HARD_MAX_RETRY_TICKS")));
    }

    @Test
    void diagnosticsRemainExplicitOnDemandAfterRuntimeRegistration()
            throws IOException {
        String diagnostic = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/ArmoryResupplyDiagnostics.java");
        String command = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/GuardGunnerCommands.java");
        String mod = read(
                "src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");

        assertAll(
                () -> assertTrue(diagnostic.contains("captureDormant(")),
                () -> assertTrue(diagnostic.contains("NOT_REGISTERED")),
                () -> assertTrue(diagnostic.contains("REGISTERED")),
                () -> assertTrue(diagnostic.contains("ArmoryResupplyLiveGate.evaluate(")),
                () -> assertTrue(diagnostic.indexOf("ArmoryResupplyLiveGate.evaluate(")
                        < diagnostic.indexOf("GuardWeaponAmmunition.captureSummary(")),
                () -> assertTrue(diagnostic.contains("HARD_MAX_CANDIDATE_ARMORIES")),
                () -> assertTrue(diagnostic.contains("HARD_MAX_PATH_ATTEMPTS")),
                () -> assertFalse(diagnostic.contains("getChunk(")),
                () -> assertFalse(diagnostic.contains("getEntities")),
                () -> assertFalse(diagnostic.contains("moveTo(")),
                () -> assertFalse(diagnostic.contains("reserve(")),
                () -> assertTrue(command.contains("\"Armory resupply\"")),
                () -> assertTrue(command.contains("\"Armory policy\"")),
                () -> assertTrue(command.contains("ArmoryResupplyDiagnostics.capture(")),
                () -> assertFalse(mod.contains("ArmoryResupplyDiagnostics")),
                () -> assertFalse(mod.contains("ArmoryResupplyMovementController")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
