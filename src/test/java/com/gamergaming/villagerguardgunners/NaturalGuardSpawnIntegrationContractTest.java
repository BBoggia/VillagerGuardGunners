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

final class NaturalGuardSpawnIntegrationContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void spawnIntegrationIsOneTimeDeferredAndBounded() throws IOException {
        String integration = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/NaturalGuardSpawnIntegration.java");
        String guardMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String conversionMixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/VillagerToGuardMixin.java");
        String mod = read(
                "src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");
        String populationIndex = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/"
                        + "NaturalGuardPopulationIndex.java");

        assertAll(
                () -> assertTrue(mod.contains("NaturalGuardSpawnIntegration::onEntityJoin")),
                () -> assertTrue(guardMixin.contains("method = \"finalizeSpawn\"")),
                () -> assertTrue(guardMixin.contains("method = \"aiStep\"")),
                () -> assertTrue(guardMixin.contains("getItemsFromLootTable")),
                () -> assertTrue(guardMixin.contains("shift = At.Shift.AFTER")),
                () -> assertTrue(guardMixin.contains(
                        "NaturalGuardSpawnIntegration.onInitialEquipmentReady")),
                () -> assertTrue(guardMixin.contains(
                        "VILLAGER_GUARD_GUNNERS_PENDING_NATURAL_SPAWN_TAG")),
                () -> assertTrue(conversionMixin.contains("NaturalGuardSpawnSource.CONVERSION")),
                () -> assertTrue(integration.contains(
                        "if (!guard.getMainHandItem().isEmpty())")),
                () -> assertTrue(integration.contains("level.getServer().tell(new TickTask(")),
                () -> assertTrue(integration.contains("getEntitiesOfClass(")),
                () -> assertTrue(integration.contains("nearby.distanceToSqr(origin)")),
                () -> assertTrue(integration.contains("countNearbyCandidates")),
                () -> assertTrue(integration.contains("hasActiveNaturalLoadout(nearby)")),
                () -> assertTrue(integration.contains("countUnloadedWithin(")),
                () -> assertTrue(populationIndex.contains("extends SavedData")),
                () -> assertTrue(populationIndex.contains("Map<Long, Map<UUID, Entry>> byChunk")),
                () -> assertTrue(mod.contains("NaturalGuardPopulationIndex::onEntityLeave")),
                () -> assertFalse(populationIndex.contains("TickEvent")),
                () -> assertFalse(populationIndex.contains("ChunkEvent")),
                () -> assertFalse(integration.contains("TickEvent")),
                () -> assertFalse(integration.contains("TimelessAPI")),
                () -> assertFalse(integration.contains("GunItemBuilder")));
    }

    @Test
    void transformedSuiteCoversReloadedCapAccountingEndToEnd() throws IOException {
        String gameTests = read(
                "src/gametest/java/com/gamergaming/villagerguardgunners/gametest/"
                        + "GuardGunnerGameTests.java");

        assertAll(
                () -> assertTrue(gameTests.contains(
                        "reloadedNaturalGunnerEnforcesLiveNearbyPopulationCap")),
                () -> assertTrue(gameTests.contains(
                        "NaturalGuardLoadoutSelector.DecisionStatus")),
                () -> assertTrue(gameTests.contains("NEARBY_CAP_REACHED")),
                () -> assertTrue(gameTests.contains("restored.load(serialized)")),
                () -> assertTrue(gameTests.contains(
                        "NaturalGuardLoadoutDiagnostics.capture(restored).active()")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
