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

final class ArmoryResupplyStepSixContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void loadedIndexIsLifecycleDrivenSpatialBoundedAndNeverLoadsChunks()
            throws IOException {
        String index = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/LoadedArmoryIndex.java");
        String armory = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/ArmoryBlockEntity.java");
        String services = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/ArmoryResupplyServerServices.java");

        assertAll(
                () -> assertTrue(index.contains("Map<ResourceKey<Level>, DimensionIndex>")),
                () -> assertTrue(index.contains("SectionPos.asLong(")),
                () -> assertTrue(index.contains("visitedRecords >= maximumRecords")),
                () -> assertTrue(index.contains("level.hasChunkAt(position)")),
                () -> assertFalse(index.contains("getChunk(")),
                () -> assertFalse(index.contains("getEntities")),
                () -> assertFalse(index.contains("BlockPos.betweenClosed")),
                () -> assertTrue(armory.contains("loadedArmories()")),
                () -> assertTrue(armory.contains("public void setRemoved()")),
                () -> assertTrue(services.contains("onLevelUnload(")),
                () -> assertTrue(services.contains("onServerStopping(")));
    }

    @Test
    void lowPriorityRuntimeJoinsEveryDormantContractWithBoundedWork()
            throws IOException {
        String runtime = read(
                "src/main/java/com/gamergaming/villagerguardgunners/armory/resupply/GuardArmoryResupplyController.java");
        String goal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardArmoryResupplyGoal.java");
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");

        assertAll(
                () -> assertTrue(runtime.contains("ArmoryResupplyLiveGate.evaluate(")),
                () -> assertTrue(runtime.indexOf("ArmoryResupplyLiveGate.evaluate(")
                        < runtime.indexOf("GuardWeaponAmmunition.captureSummary(")),
                () -> assertTrue(runtime.contains("loadedArmories().query(")),
                () -> assertTrue(runtime.contains("ArmorySupplyCandidate.inspect(")),
                () -> assertTrue(runtime.contains("reservations.reserve(")),
                () -> assertTrue(runtime.contains("movement.begin(")),
                () -> assertTrue(runtime.contains("ArmoryAmmunitionWithdrawal.withdraw(")),
                () -> assertTrue(runtime.contains("nextStaggeredTick(")),
                () -> assertTrue(runtime.contains("unreachableRetryDelay(")),
                () -> assertFalse(runtime.contains("getEntities")),
                () -> assertFalse(runtime.contains("getChunk(")),
                () -> assertFalse(runtime.contains("BlockPos.betweenClosed")),
                () -> assertTrue(goal.contains("EnumSet.of(Flag.MOVE)")),
                () -> assertTrue(mixin.contains("addGoal(\n                3,")),
                () -> assertFalse(mixin.contains("new ArmoryReservationBook(")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
