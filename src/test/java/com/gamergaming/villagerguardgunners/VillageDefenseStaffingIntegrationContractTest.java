package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefenseStaffingIntegrationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void staffingIsEventDrivenBoundedAndTransient() throws IOException {
        String runtime = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/duty/"
                        + "VillageDefenseStaffingRuntime.java");
        String mod = read(
                "src/main/java/com/gamergaming/villagerguardgunners/"
                        + "VillagerGuardGunners.java");
        String mixin = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/GuardMixin.java");
        String suitability = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/duty/"
                        + "VillageDefenseWeaponSuitability.java");
        int staffingStart = mod.indexOf(
                "VillageDefenseStaffingRuntime::onServerStarted");
        int stateStart = mod.indexOf("VillageDefenseStateTicker::onServerStarted");
        int stateTick = mod.indexOf("VillageDefenseStateTicker::onServerTick");
        int staffingTick = mod.indexOf("VillageDefenseStaffingRuntime::onServerTick");
        int eligibilityFilter = runtime.indexOf("Double.isFinite(candidate.distanceSquared())");
        int candidateLimit = runtime.indexOf(
                ".limit(VillageDefenseStaffingPlanner.MAXIMUM_CANDIDATES)");

        assertAll(
                () -> assertTrue(runtime.contains("MAXIMUM_REVIEWS_PER_TICK = 32")),
                () -> assertTrue(runtime.contains("MAXIMUM_LOADED_GUARDS_PER_DIMENSION")),
                () -> assertTrue(runtime.contains("pendingNetworks")),
                () -> assertTrue(runtime.contains("postIdentityChanged")),
                () -> assertTrue(runtime.contains("if (identity != null)")),
                () -> assertTrue(runtime.contains("reservations.releaseGuard")),
                () -> assertTrue(eligibilityFilter >= 0
                        && eligibilityFilter < candidateLimit),
                () -> assertTrue(mod.contains(
                        "VillageDefenseStaffingRuntime::onEntityJoin")),
                () -> assertTrue(mod.contains(
                        "VillageDefenseStaffingRuntime::onEntityLeave")),
                () -> assertTrue(mod.contains(
                        "VillageDefenseStaffingRuntime::onServerTick")),
                () -> assertTrue(staffingStart >= 0 && staffingStart < stateStart),
                () -> assertTrue(stateTick >= 0 && stateTick < staffingTick),
                () -> assertTrue(mixin.contains("((tickCount + getId()) & 31) == 0")),
                () -> assertTrue(suitability.contains("NativeGunController.isGun(main)")),
                () -> assertTrue(suitability.contains("instanceof CrossbowItem")),
                () -> assertTrue(suitability.contains("instanceof SwordItem")),
                () -> assertFalse(runtime.contains("getEntities")),
                () -> assertFalse(runtime.contains("getChunk(")),
                () -> assertFalse(runtime.contains("setChunkForced")),
                () -> assertFalse(runtime.contains("extends SavedData")));
    }

    @Test
    void stateChangesAndDeadlineReviewsQueueRestaffing() throws IOException {
        String service = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseStateService.java");
        String ticker = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseStateTicker.java");
        String commands = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/"
                        + "VillageDefenseCommands.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "VillageDefenseStaffingRuntime.requestReview")),
                () -> assertTrue(ticker.contains(
                        "VillageDefenseStaffingRuntime.requestReview")),
                () -> assertTrue(commands.contains("Current staffing:")),
                () -> assertTrue(commands.contains("limitRejections=")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
