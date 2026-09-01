package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class PhaseSevenCleanupContractTest {
    private static Path projectDirectory;
    private static String productionSource;
    private static String productionSourceWithoutNaturalConversionBridge;

    @BeforeAll
    static void loadProductionSource() throws IOException {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
        Path sourceRoot = projectDirectory.resolve("src/main/java");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            productionSource = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .map(PhaseSevenCleanupContractTest::readUnchecked)
                    .collect(Collectors.joining("\n"));
        }
        productionSourceWithoutNaturalConversionBridge = productionSource.replace(
                read("src/main/java/com/gamergaming/villagerguardgunners/mixin/VillagerToGuardMixin.java"),
                "").replace(
                        read("src/main/java/com/gamergaming/villagerguardgunners/worldgen/post/GuardPostSpawner.java"),
                        "");
    }

    @Test
    void everyLegacyRuntimeSystemStaysDeleted() {
        List<String> removedPaths = List.of(
                "ai/GuardBehaviorTree.java",
                "ai/GuardBehaviorTreeGoal.java",
                "ai/GuardMeleeCombatGoal.java",
                "ai/GuardReloadingGoal.java",
                "ai/NodeFactory.java",
                "ai/action_nodes/AttackAction.java",
                "ai/action_nodes/GuardAction.java",
                "ai/action_nodes/IdleAction.java",
                "ai/action_nodes/MoveToCoverAction.java",
                "ai/action_nodes/MoveTowardsTargetAction.java",
                "ai/action_nodes/PatrolAction.java",
                "ai/action_nodes/ReloadAction.java",
                "ai/action_nodes/RetreatAction.java",
                "ai/condition_nodes/CanAttackCondition.java",
                "ai/condition_nodes/GuardCondition.java",
                "ai/condition_nodes/IsHealthCriticalCondition.java",
                "ai/condition_nodes/IsPatrolTimeCondition.java",
                "ai/condition_nodes/IsTargetVisibleCondition.java",
                "ai/condition_nodes/NeedToReloadCondition.java",
                "ai/condition_nodes/RecentlyDamaged.java",
                "ai/selector_nodes/UtilitySelectorNode.java",
                "api/IGuardBTreeHelper.java",
                "api/IGuardReloading.java",
                "api/ISteeringBehavior.java",
                "api/MovementState.java",
                "api/ai/ActionNode.java",
                "api/ai/BTNode.java",
                "api/ai/ConditionNode.java",
                "api/ai/NodeStatus.java",
                "api/ai/SelectorNode.java",
                "api/ai/SequenceNode.java",
                "api/ai/UtilityNode.java",
                "client/renderer/entity/GuardBulletRenderer.java",
                "compat/fzzy_config/GeneralConfig.java",
                "compat/fzzy_config/GuardGunnerConfig.java",
                "entity/GuardBulletEntity.java",
                "entity/SlotAmmo.java",
                "handlers/GuardSpawnHandler.java",
                "handlers/GunMechanicsHandler.java",
                "handlers/MovementHandler.java",
                "handlers/firing_modes/BurstFireMode.java",
                "handlers/firing_modes/FireModeStrategy.java",
                "handlers/firing_modes/FullAutoFireMode.java",
                "handlers/firing_modes/SemiAutoFireMode.java",
                "init/ModEntities.java",
                "mixin/GuardRendererMixin.java",
                "util/AIUtils.java",
                "util/GuardGunHelper.java");
        Path packageRoot = projectDirectory.resolve(
                "src/main/java/com/gamergaming/villagerguardgunners");

        assertAll(removedPaths.stream()
                .map(relativePath -> () -> assertFalse(
                        Files.exists(packageRoot.resolve(relativePath)),
                        () -> "Legacy runtime path returned: " + relativePath)));
    }

    @Test
    void buildUsesOnlyThePluginsAndDependenciesStillNeededAtRuntime() throws IOException {
        String build = read("build.gradle");
        String runtimeBuild = build.substring(0, build.indexOf("tasks.register('verifyReleaseArtifact')"));
        String properties = read("gradle.properties");
        String metadata = read("src/main/resources/META-INF/mods.toml");

        assertAll(
                () -> assertTrue(build.contains("id 'net.minecraftforge.gradle' version '[6.0,6.2)'")),
                () -> assertTrue(build.contains("classpath 'org.spongepowered:mixingradle:0.7-SNAPSHOT'")),
                () -> assertFalse(build.contains("name: 'ForgeGradle'")),
                () -> assertFalse(build.contains("org.parchmentmc")),
                () -> assertFalse(build.contains("librarian")),
                () -> assertFalse(build.contains("maven-publish")),
                () -> assertFalse(build.contains("publishing {")),
                () -> assertFalse(build.contains("output.resourcesDir")),
                () -> assertFalse(runtimeBuild.contains("Implementation-Timestamp")),
                () -> assertTrue(runtimeBuild.contains("me.fzzyhmstrs:fzzy_config")),
                () -> assertTrue(runtimeBuild.contains("thedarkcolour:kotlinforforge")),
                () -> assertTrue(properties.contains("fzzy_config_version=0.7.6+1.20.1")),
                () -> assertTrue(metadata.contains("modId=\"fzzy_config\"")),
                () -> assertTrue(metadata.contains("modId=\"kotlinforforge\"")),
                () -> assertEquals(6, countOccurrences(metadata, "[[dependencies.${mod_id}]]")));
    }

    @Test
    void bootstrapAndMixinRegistrationRemainNarrow() throws IOException {
        String bootstrap = read(
                "src/main/java/com/gamergaming/villagerguardgunners/VillagerGuardGunners.java");
        String mixins = read("src/main/resources/mixins.villagerguardgunners.json")
                .replaceAll("\\s+", "");

        assertAll(
                () -> assertTrue(bootstrap.contains("ModMenus.register(modEventBus)")),
                () -> assertFalse(bootstrap.contains("Logger")),
                () -> assertFalse(bootstrap.contains("ResourceLocation")),
                () -> assertFalse(bootstrap.contains("ModEntities")),
                () -> assertTrue(bootstrap.contains("ModConfigs.init();")),
                () -> assertTrue(mixins.contains(
                        "\"mixins\":[\"GuardMixin\",\"ItemEntityMixin\",\"RaiseShieldGoalMixin\",\"TaCZCommonAssetsManagerMixin\",\"VillagerToGuardMixin\",\"accessor.LootPoolAccessor\",\"accessor.LootPoolEntryAccessor\",\"accessor.LootPoolSingletonAccessor\",\"accessor.LootTableAccessor\",\"accessor.StructureTemplatePoolAccessor\"]")),
                () -> assertTrue(mixins.contains(
                        "\"client\":[\"client.GuardModelMixin\",\"client.GuardSteveModelMixin\"]")),
                () -> assertFalse(mixins.contains("GuardRendererMixin")));
    }

    @Test
    void nativeCombatCannotRegressToTheCustomSimulationOrRebalance() {
        List<String> prohibitedRuntimeMarkers = List.of(
                "GuardBulletEntity",
                "GuardGunHelper",
                "GunMechanicsHandler",
                "FireModeStrategy",
                "RecoilState",
                "getDamageAmount()",
                "getRecoil()",
                "setDamage(",
                "addFreshEntity(",
                "SoundManager.sendSound",
                "setCurrentAmmoCount(",
                "reduceCurrentAmmoCount(",
                "infiniteGuardAmmo",
                "reloadTimeMultiplier",
                "minAttackInterval",
                "maxAttackInterval");

        assertAll(prohibitedRuntimeMarkers.stream()
                .map(marker -> () -> assertFalse(
                        productionSourceWithoutNaturalConversionBridge.contains(marker),
                        () -> "Custom firearm/rebalance marker returned: " + marker)));
    }

    @Test
    void productionCodeContainsNoPerShotOrDebugLogging() throws IOException {
        String legacyNotice = read(
                "src/main/java/com/gamergaming/villagerguardgunners/config/LegacyConfigNotice.java");
        String naturalLoadoutReload = read(
                "src/main/java/com/gamergaming/villagerguardgunners/spawn/NaturalGuardLoadoutPools.java");
        String guardRoleReload = read(
                "src/main/java/com/gamergaming/villagerguardgunners/role/GuardRoles.java");
        String guardPostGroupReload = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/post/GuardPostGroups.java");
        String largePackSync = read(
                "src/main/java/com/gamergaming/villagerguardgunners/compat/tacz/network/"
                        + "TaCZGunPackSync.java");
        String largePackSyncClient = read(
                "src/main/java/com/gamergaming/villagerguardgunners/compat/tacz/network/client/"
                        + "TaCZGunPackSyncClient.java");
        String villagePoolStartup = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageBuildingPoolIntegration.java");
        String villageDefinitionReload = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageBuildingDefinitions.java");
        String performanceSensitiveSource = productionSource
                .replace(legacyNotice, "")
                .replace(naturalLoadoutReload, "")
                .replace(guardRoleReload, "")
                .replace(guardPostGroupReload, "")
                .replace(largePackSync, "")
                .replace(largePackSyncClient, "")
                .replace(villagePoolStartup, "")
                .replace(villageDefinitionReload, "");

        assertAll(
                () -> assertFalse(performanceSensitiveSource.contains("LogUtils")),
                () -> assertFalse(performanceSensitiveSource.contains("Logger")),
                () -> assertFalse(performanceSensitiveSource.contains(".info(")),
                () -> assertFalse(performanceSensitiveSource.contains(".debug(")),
                () -> assertFalse(performanceSensitiveSource.contains(".warn(")),
                () -> assertFalse(performanceSensitiveSource.contains("System.out")),
                () -> assertFalse(performanceSensitiveSource.contains("System.err")),
                () -> assertFalse(performanceSensitiveSource.contains("printStackTrace(")),
                () -> assertTrue(legacyNotice.contains("LOGGER.warn(")),
                () -> assertFalse(legacyNotice.contains("LOGGER.info(")),
                () -> assertFalse(legacyNotice.contains("LOGGER.debug(")),
                () -> assertTrue(naturalLoadoutReload.contains("LOGGER.error(")),
                () -> assertTrue(naturalLoadoutReload.contains("LOGGER.info(")),
                () -> assertFalse(naturalLoadoutReload.contains("LOGGER.debug(")),
                () -> assertFalse(naturalLoadoutReload.contains("tick(")),
                () -> assertFalse(naturalLoadoutReload.contains("getEntitiesOfClass(")));
        assertAll(
                () -> assertTrue(guardRoleReload.contains("LOGGER.error(")),
                () -> assertTrue(guardRoleReload.contains("LOGGER.info(")),
                () -> assertFalse(guardRoleReload.contains("LOGGER.debug(")),
                () -> assertFalse(guardRoleReload.contains("tick(")),
                () -> assertFalse(guardRoleReload.contains("getEntitiesOfClass(")));
        assertAll(
                () -> assertTrue(guardPostGroupReload.contains("LOGGER.error(")),
                () -> assertTrue(guardPostGroupReload.contains("LOGGER.info(")),
                () -> assertFalse(guardPostGroupReload.contains("LOGGER.debug(")),
                () -> assertFalse(guardPostGroupReload.contains("tick(")),
                () -> assertFalse(guardPostGroupReload.contains("getEntitiesOfClass(")));
        assertAll(
                () -> assertTrue(largePackSync.contains("LOGGER.info(")),
                () -> assertTrue(largePackSync.contains("LOGGER.error(")),
                () -> assertFalse(largePackSync.contains("LOGGER.debug(")),
                () -> assertFalse(largePackSync.contains("tick(")),
                () -> assertTrue(largePackSyncClient.contains("LOGGER.info(")),
                () -> assertTrue(largePackSyncClient.contains("LOGGER.error(")),
                () -> assertFalse(largePackSyncClient.contains("LOGGER.debug(")),
                () -> assertFalse(largePackSyncClient.contains("tick(")));
        assertAll(
                () -> assertTrue(villagePoolStartup.contains("LOGGER.info(")),
                () -> assertTrue(villagePoolStartup.contains("LOGGER.warn(")),
                () -> assertFalse(villagePoolStartup.contains("LOGGER.debug(")),
                () -> assertFalse(villagePoolStartup.contains("tick(")),
                () -> assertFalse(villagePoolStartup.contains("getEntitiesOfClass(")));
        assertAll(
                () -> assertTrue(villageDefinitionReload.contains("LOGGER.info(")),
                () -> assertTrue(villageDefinitionReload.contains("LOGGER.error(")),
                () -> assertFalse(villageDefinitionReload.contains("LOGGER.debug(")),
                () -> assertFalse(villageDefinitionReload.contains("tick(")),
                () -> assertFalse(villageDefinitionReload.contains("getEntitiesOfClass(")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read production source " + path, exception);
        }
    }

    private static int countOccurrences(String source, String value) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
