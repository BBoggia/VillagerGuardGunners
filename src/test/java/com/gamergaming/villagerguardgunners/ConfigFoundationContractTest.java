package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class ConfigFoundationContractTest {
    private static Path projectDirectory;

    @BeforeAll
    static void locateProject() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
    }

    @Test
    void registersOneVersionedSynchronizedConfig() throws IOException {
        String config = read(
                "src/main/java/com/gamergaming/villagerguardgunners/config/VillagerGuardGunnersConfig.java");
        String registration = read(
                "src/main/java/com/gamergaming/villagerguardgunners/init/ModConfigs.java");
        String legacyNotice = read(
                "src/main/java/com/gamergaming/villagerguardgunners/config/LegacyConfigNotice.java");
        String mod = read(
                "src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");

        assertAll(
                () -> assertTrue(config.contains(
                        "@Version(version = VillagerGuardGunnersConfig.CONFIG_VERSION)")),
                () -> assertTrue(config.contains("public static final int CONFIG_VERSION = 1;")),
                () -> assertTrue(config.contains(
                        "super(ResourceLocation.fromNamespaceAndPath(VillagerGuardGunners.MODID, \"guard_gunners\"))")),
                () -> assertTrue(config.contains("public int defaultPermLevel()")),
                () -> assertTrue(config.contains("return 4;")),
                () -> assertTrue(registration.contains("RegisterType.BOTH")),
                () -> assertTrue(registration.contains("registerAndLoadConfig")),
                () -> assertTrue(registration.contains("LegacyConfigNotice.warnIfPresent()")),
                () -> assertTrue(legacyNotice.contains("guard_gunner.toml")),
                () -> assertTrue(legacyNotice.contains("general.toml")),
                () -> assertTrue(legacyNotice.contains("tacz.toml")),
                () -> assertTrue(legacyNotice.contains("villager.toml")),
                () -> assertTrue(legacyNotice.contains("blueprint.toml")),
                () -> assertTrue(!legacyNotice.contains("Files.delete")),
                () -> assertTrue(!legacyNotice.contains("Files.move")),
                () -> assertTrue(mod.contains("ModConfigs.init();")));
    }

    @Test
    void exposesOnlyGuardGunBehaviorAndSchedulingSections() throws IOException {
        String config = read(
                "src/main/java/com/gamergaming/villagerguardgunners/config/VillagerGuardGunnersConfig.java");

        assertAll(
                () -> assertTrue(config.contains("public ValidatedBoolean enabled")),
                () -> assertTrue(config.contains("public CombatSettings combat")),
                () -> assertTrue(config.contains("public MovementSettings movement")),
                () -> assertTrue(config.contains("public SafetySettings safety")),
                () -> assertTrue(config.contains("public PerformanceSettings performance")),
                () -> assertTrue(!config.contains("damage")),
                () -> assertTrue(!config.contains("infiniteGuardAmmo")),
                () -> assertTrue(!config.contains("reloadTimeMultiplier")),
                () -> assertTrue(!config.contains("accuracy")));
    }

    @Test
    void suppliesTranslationsForEveryConfigEntry() throws IOException {
        String translations = read("src/main/resources/assets/villagerguardgunners/lang/en_us.json");
        String[] entries = {
                "enabled",
                "combat.requiredVisibleTicks",
                "combat.maximumEngagementRange",
                "combat.closeRangeDistance",
                "combat.aimToleranceDegrees",
                "movement.approachSpeed",
                "movement.retreatSpeed",
                "movement.repositionSpeed",
                "movement.friendlyRepositionTicks",
                "safety.baseClearance",
                "performance.visibilitySampleIntervalTicks",
                "performance.movementDecisionIntervalTicks",
                "performance.pathRecalculationIntervalTicks",
                "performance.gunDataRefreshIntervalTicks",
                "performance.unsafeShotCacheTicks",
                "performance.repositionProgressCheckTicks"
        };

        assertAll(java.util.Arrays.stream(entries)
                .map(entry -> (Executable) () -> assertTrue(
                        translations.contains("config.villagerguardgunners.guard_gunners." + entry + "\""),
                        () -> "Missing translation for " + entry)));
    }

    @Test
    void everyConfigSectionIsConnectedToTheRuntimeDecisionLayer() throws IOException {
        String goal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");
        String movement = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardCombatMovement.java");
        String controller = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");

        assertAll(
                () -> assertTrue(goal.contains("config.enabled.get()")),
                () -> assertTrue(goal.contains("config.combat.requiredVisibleTicks.get()")),
                () -> assertTrue(goal.contains("config.combat.closeRangeDistance.get()")),
                () -> assertTrue(goal.contains("config.combat.aimToleranceDegrees.get()")),
                () -> assertTrue(goal.contains("config.movement.approachSpeed.get()")),
                () -> assertTrue(goal.contains("config.movement.retreatSpeed.get()")),
                () -> assertTrue(goal.contains("config.movement.repositionSpeed.get()")),
                () -> assertTrue(goal.contains("config.movement.friendlyRepositionTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.visibilitySampleIntervalTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.movementDecisionIntervalTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.pathRecalculationIntervalTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.unsafeShotCacheTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.repositionProgressCheckTicks.get()")),
                () -> assertTrue(movement.contains("double closeRangeSquared")),
                () -> assertTrue(controller.contains("config.combat.maximumEngagementRange.get()")),
                () -> assertTrue(controller.contains("config.safety.baseClearance.get()")),
                () -> assertTrue(controller.contains("config.performance.gunDataRefreshIntervalTicks.get()")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
