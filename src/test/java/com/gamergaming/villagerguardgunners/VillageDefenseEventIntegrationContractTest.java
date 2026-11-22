package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VillageDefenseEventIntegrationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void bellAndRaidMixinsEmitOnlyBoundedServerObservations() throws IOException {
        String bell = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/"
                        + "BellBlockMixin.java");
        String raid = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/"
                        + "RaidMixin.java");
        String accessor = read(
                "src/main/java/com/gamergaming/villagerguardgunners/mixin/accessor/"
                        + "RaidsAccessor.java");
        String mixins = read("src/main/resources/mixins.villagerguardgunners.json");

        assertAll(
                () -> assertTrue(bell.contains("@Mixin(BellBlock.class)")),
                () -> assertTrue(bell.contains("afterSuccessfulRing")),
                () -> assertTrue(bell.contains("Boolean.TRUE.equals")),
                () -> assertTrue(bell.contains("instanceof ServerLevel")),
                () -> assertTrue(bell.contains("VillageDefenseSignal.BELL_RUNG")),
                () -> assertTrue(raid.contains("@Mixin(Raid.class)")),
                () -> assertTrue(raid.contains("observedState")),
                () -> assertTrue(raid.contains("current ==")),
                () -> assertTrue(raid.contains("VillageDefenseEventRuntime.observeRaid")),
                () -> assertTrue(raid.contains("method = \"stop\"")),
                () -> assertTrue(accessor.contains("@Accessor(\"raidMap\")")),
                () -> assertTrue(mixins.contains("BellBlockMixin")),
                () -> assertTrue(mixins.contains("RaidMixin")),
                () -> assertTrue(mixins.contains("accessor.RaidsAccessor")));
    }

    @Test
    void livingEventsUseExistingEntitiesAndIndexedNetworkResolution()
            throws IOException {
        String sources = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseEventSources.java");
        String runtime = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseEventRuntime.java");
        String bootstrap = read(
                "src/main/java/com/gamergaming/villagerguardgunners/"
                        + "VillagerGuardGunners.java");
        int cooldownGate = runtime.indexOf("signalGate.shouldRoute(");
        int threatLease = runtime.indexOf(
                "signalGate.confirmThreat(", cooldownGate);

        assertAll(
                () -> assertTrue(sources.contains("LivingChangeTargetEvent")),
                () -> assertTrue(sources.contains("LivingDamageEvent")),
                () -> assertTrue(sources.contains("LivingDeathEvent")),
                () -> assertTrue(sources.contains("GuardAllyProtection.GUARD_ALLIES")),
                () -> assertTrue(sources.contains("additionalProtectedEntities")),
                () -> assertTrue(sources.contains("excludedEntities")),
                () -> assertFalse(sources.contains("getEntities")),
                () -> assertFalse(sources.contains("getChunk(")),
                () -> assertTrue(runtime.contains("findNearest(")),
                () -> assertTrue(runtime.contains("EVENT_LINK_RADIUS")),
                () -> assertTrue(runtime.contains("RAID_LINK_RADIUS")),
                () -> assertTrue(runtime.contains("VillageDefenseSignalGate")),
                () -> assertTrue(cooldownGate >= 0),
                () -> assertTrue(threatLease > cooldownGate),
                () -> assertFalse(runtime.contains("getEntities")),
                () -> assertFalse(runtime.contains("getChunk(")),
                () -> assertFalse(runtime.contains("setChunkForced")),
                () -> assertTrue(bootstrap.contains("EventPriority.LOWEST")),
                () -> assertTrue(bootstrap.contains(
                        "VillageDefenseEventSources::onGuardTargetChanged")),
                () -> assertTrue(bootstrap.contains(
                        "VillageDefenseEventSources::onDefendedEntityDamaged")),
                () -> assertTrue(bootstrap.contains(
                        "VillageDefenseEventSources::onDefendedEntityKilled")));
    }

    @Test
    void restartAndDeadlineReviewRetainLiveRaidAndThreatFacts()
            throws IOException {
        String runtime = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseEventRuntime.java");
        String ticker = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseStateTicker.java");
        String lifecycle = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/"
                        + "VillageDefenseStateLifecycle.java");

        assertAll(
                () -> assertTrue(runtime.contains("onServerStarted(")),
                () -> assertTrue(runtime.contains("getRaidMap()")),
                () -> assertTrue(runtime.contains("restartReconciliations")),
                () -> assertTrue(runtime.contains("anotherRaidActive")),
                () -> assertTrue(runtime.contains("confirmedThreatActive")),
                () -> assertTrue(runtime.contains(
                        "MAXIMUM_ACTIVE_RAID_OBSERVATIONS")),
                () -> assertTrue(runtime.contains("raidTrackingOverflowed")),
                () -> assertTrue(ticker.contains(
                        "VillageDefenseEventRuntime.reviewFacts")),
                () -> assertTrue(lifecycle.contains("onReview(")),
                () -> assertTrue(lifecycle.contains("normalizedFacts.raidActive()")),
                () -> assertTrue(lifecycle.contains(
                        "normalizedFacts.confirmedThreatActive()")));
    }

    @Test
    void readOnlyOperatorCommandsExposeStateNetworksAndEventCounters()
            throws IOException {
        String root = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/"
                        + "GuardGunnerCommands.java");
        String commands = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/"
                        + "VillageDefenseCommands.java");

        assertAll(
                () -> assertTrue(root.contains("VillageDefenseCommands.build()")),
                () -> assertTrue(commands.contains("literal(\"status\")")),
                () -> assertTrue(commands.contains("literal(\"nearest\")")),
                () -> assertTrue(commands.contains("literal(\"inspect\")")),
                () -> assertTrue(commands.contains("literal(\"networks\")")),
                () -> assertTrue(commands.contains("literal(\"events\")")),
                () -> assertTrue(commands.contains("UuidArgument.uuid()")),
                () -> assertFalse(commands.contains("VillageDefenseStateService.signal")),
                () -> assertFalse(commands.contains("TickEvent")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
