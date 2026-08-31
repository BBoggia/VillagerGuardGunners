package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Test;

final class AmmunitionReleaseReadinessContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void manualMatrixCoversTheStepEightLifecycleAndMultiplayerBoundary()
            throws IOException {
        String checklist = read("docs/release-checklist.md");
        String guide = read("docs/ammunition-quality-of-life.md");

        String[] manualCases = {
                "AMMO-ENV-01",
                "AMMO-ENV-02",
                "AMMO-ENV-03",
                "AMMO-OWN-01",
                "AMMO-FEED-01",
                "AMMO-FEED-02",
                "AMMO-GIVE-01",
                "AMMO-PICK-01",
                "AMMO-SHARE-01",
                "AMMO-LIFE-01",
                "AMMO-LIFE-02",
                "AMMO-CFG-01"
        };
        assertAll(Stream.of(manualCases)
                .map(id -> (Executable) () -> assertTrue(
                        checklist.contains(id),
                        () -> "Missing Step 8 manual case " + id)));
        assertAll(
                () -> assertTrue(checklist.contains("Integrated server")),
                () -> assertTrue(checklist.contains("Dedicated server")),
                () -> assertTrue(checklist.contains("simultaneous combat")),
                () -> assertTrue(checklist.contains("unload/reload the chunk")),
                () -> assertTrue(checklist.contains("Fzzy Config UI")),
                () -> assertTrue(guide.contains("Intentionally deferred")),
                () -> assertTrue(guide.contains("reload animation")),
                () -> assertTrue(guide.contains("continuous redistribution")),
                () -> assertTrue(guide.contains("ammo-box sharing")),
                () -> assertTrue(guide.contains("verifyAmmunitionReleaseCandidate")));
    }

    @Test
    void pickupAndSharingRemainIndependentServerRollbackControls()
            throws IOException {
        String config = read(
                "src/main/java/com/gamergaming/villagerguardgunners/config/VillagerGuardGunnersConfig.java");
        String pickup = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ammunition/GuardDroppedAmmoPickup.java");
        String sharing = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ammunition/GuardAmmoSharing.java");
        String gameTests = read(
                "src/gametest/java/com/gamergaming/villagerguardgunners/gametest/GuardGunnerGameTests.java");

        assertAll(
                () -> assertTrue(config.contains("ValidatedBoolean pickupDroppedAmmo")),
                () -> assertTrue(config.contains("ValidatedBoolean shareAmmoBetweenGuards")),
                () -> assertTrue(pickup.contains("pickupDroppedAmmo.get()")),
                () -> assertFalse(pickup.contains("shareAmmoBetweenGuards.get()")),
                () -> assertTrue(sharing.contains("shareAmmoBetweenGuards.get()")),
                () -> assertFalse(sharing.contains("pickupDroppedAmmo.get()")),
                () -> assertTrue(gameTests.contains("PickupStatus.DISABLED")),
                () -> assertTrue(gameTests.contains("SharingStatus.DISABLED")));
    }

    @Test
    void commonProductionSourcesDoNotReferenceMinecraftClientClasses()
            throws IOException {
        Path productionRoot = PROJECT_DIRECTORY.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners");
        List<Path> violations;
        try (Stream<Path> paths = Files.walk(productionRoot)) {
            violations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(productionRoot.resolve("client")))
                    .filter(path -> !path.startsWith(productionRoot.resolve("mixin/client")))
                    .filter(path -> contains(path, "net.minecraft.client"))
                    .toList();
        }
        assertTrue(violations.isEmpty(),
                () -> "Common-server sources reference client-only Minecraft classes: "
                        + violations);

        String clientEvents = read(
                "src/main/java/com/gamergaming/villagerguardgunners/client/ClientEventHandler.java");
        String mixins = read("src/main/resources/mixins.villagerguardgunners.json");
        assertAll(
                () -> assertTrue(clientEvents.contains("value = Dist.CLIENT")),
                () -> assertTrue(mixins.contains("\"mixins\"")),
                () -> assertTrue(mixins.contains("\"client\"")),
                () -> assertTrue(mixins.contains("client.GuardModelMixin")),
                () -> assertTrue(mixins.contains("client.GuardSteveModelMixin")));
    }

    @Test
    void aggregateGateRequiresCorrectnessArtifactAndPerformanceEvidence()
            throws IOException {
        String build = read("build.gradle");
        int gateStart = build.indexOf(
                "tasks.register('verifyAmmunitionReleaseCandidate')");
        assertTrue(gateStart >= 0, "Missing aggregate ammunition release task");
        String gate = build.substring(gateStart);

        assertAll(
                () -> assertTrue(gate.contains("runGameTestServer")),
                () -> assertTrue(gate.contains("assembleReleaseCandidateEvidence")),
                () -> assertTrue(gate.contains("performanceJsonReports != 17")),
                () -> assertTrue(gate.contains("performanceJfrProfiles != 17")),
                () -> assertTrue(gate.contains("sha256OfFile(archive)")),
                () -> assertTrue(gate.contains("docs/release-checklist.md")),
                () -> assertTrue(gate.contains("docs/ammunition-quality-of-life.md")),
                () -> assertTrue(gate.contains("without -Pphase8Performance")));
    }

    @Test
    void publicPackagingRequiresCompletedManualEvidence() throws IOException {
        String build = read("build.gradle");
        int manualGateStart = build.indexOf(
                "tasks.register('verifyManualReleaseChecklist')");
        int packagingStart = build.indexOf(
                "tasks.register('prepareCurseForgeRelease'");
        assertTrue(manualGateStart >= 0, "Missing manual release gate");
        assertTrue(packagingStart >= 0, "Missing CurseForge packaging task");
        String manualGate = build.substring(manualGateStart, packagingStart);
        String packaging = build.substring(packagingStart);

        assertAll(
                () -> assertTrue(manualGate.contains("| Not run |")),
                () -> assertTrue(manualGate.contains("| Failed |")),
                () -> assertTrue(manualGate.contains("- [ ]")),
                () -> assertTrue(manualGate.contains("throw new GradleException")),
                () -> assertTrue(packaging.contains(
                        "tasks.named('verifyManualReleaseChecklist')")));
    }

    private static boolean contains(Path path, String marker) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains(marker);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect " + path, exception);
        }
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
