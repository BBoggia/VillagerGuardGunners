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
        String recovery = read("src/main/java/com/gamergaming/villagerguardgunners/quartermaster/QuartermasterRecovery.java");
        // Item-entity recovery does not implement custom gun projectiles.
        productionSourceWithoutNaturalConversionBridge = productionSourceWithoutNaturalConversionBridge.replace(
                recovery, recovery.replace("addFreshEntity(", "recoveredItemInsertion("));
        String medicalRecovery = read("src/main/java/com/gamergaming/villagerguardgunners/medical/MedicalRecovery.java");
        productionSourceWithoutNaturalConversionBridge = productionSourceWithoutNaturalConversionBridge.replace(
                medicalRecovery, medicalRecovery.replace("addFreshEntity(", "recoveredMedicalItemInsertion("));
        String shotBridge = read("src/main/java/com/gamergaming/villagerguardgunners/mixin/TaCZGuardShotMixin.java");
        assertTrue(shotBridge.contains("boolean added = level.addFreshEntity(projectile);"));
        assertFalse(shotBridge.contains("new EntityKineticBullet"));
        productionSourceWithoutNaturalConversionBridge = productionSourceWithoutNaturalConversionBridge.replace(
                shotBridge, shotBridge.replace("addFreshEntity(", "nativeProjectileInsertion("));
        for (String file : List.of("VillagePopulationRuntime.java", "VillagePopulationPlacement.java")) {
            String population = read("src/main/java/com/gamergaming/villagerguardgunners/village/population/" + file);
            productionSourceWithoutNaturalConversionBridge = productionSourceWithoutNaturalConversionBridge.replace(
                    population, population.replace("addFreshEntity(", "populationEntityInsertion("));
        }
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
                        "\"mixins\":[\"GuardLifeMixin\",\"CasualtyMobMixin\",\"CasualtyHealingMixin\",\"BellBlockMixin\",\"GuardMixin\",\"TacticalPathNavigationMixin\",\"GuardDetourPathNavigationMixin\",\"GuardVillageReturnGoalMixin\",\"ItemEntityMixin\",\"PopulationStructureStartMixin\",\"VillageBuildingSelectionMixin\",\"VillageBuildingTerrainMixin\",\"RaidMixin\",\"RaiseShieldGoalMixin\",\"TaCZCommonAssetsManagerMixin\",\"TaCZGuardShotMixin\",\"TaCZGuardProjectileMixin\",\"VillagerToGuardMixin\",\"WalkBackToCheckPointGoalMixin\",\"accessor.LootPoolAccessor\",\"accessor.LootPoolEntryAccessor\",\"accessor.LootPoolSingletonAccessor\",\"accessor.LootTableAccessor\",\"accessor.RaidsAccessor\",\"accessor.StructureTemplatePoolAccessor\",\"accessor.VillagePoiStorageAccessor\",\"accessor.VillagePoolElementAccessor\",\"IntegratedVillageTagLoaderMixin\",\"QuartermasterOfferMixin\",\"QuartermasterOffersMixin\",\"QuartermasterLevelTradesMixin\",\"QuartermasterResultSlotMixin\",\"QuartermasterMerchantMenuMixin\"]")),
                () -> assertTrue(mixins.contains(
                        "\"client\":[\"client.GuardModelMixin\",\"client.GuardSteveModelMixin\",\"client.CasualtyRendererMixin\",\"client.CotNameTagMixin\"]")),
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
        String villageBuildingState = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageBuildingSavedData.java");
        String villageDefenseNetworkState = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/network/"
                        + "VillageDefenseNetworkSavedData.java");
        String villageDefenseDutyState = read(
                "src/main/java/com/gamergaming/villagerguardgunners/defense/duty/"
                        + "VillageDefenseDutySavedData.java");
        String villageBuildingActivation = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/village/"
                        + "VillageBuildingAnchorLifecycle.java");
        String generatedSquadCapacity = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/post/"
                        + "GuardPostSquads.java");
        String squadRegistryState = read(
                "src/main/java/com/gamergaming/villagerguardgunners/squad/"
                        + "SquadRegistrySavedData.java");
        String villagePopulation = read("src/main/java/com/gamergaming/villagerguardgunners/village/population/VillagePopulationRuntime.java");
        String medicalReload = read("src/main/java/com/gamergaming/villagerguardgunners/medical/TreatmentProfiles.java");
        String tradeRuntime = read("src/main/java/com/gamergaming/villagerguardgunners/quartermaster/trade/QuartermasterTrades.java");
        assertTrue(tradeRuntime.contains("if(WARNED.add(id))"));
        String performanceSensitiveSource = productionSource
                .replace(tradeRuntime, "")
                .replace(read("src/main/java/com/gamergaming/villagerguardgunners/quartermaster/trade/TradeDefinitions.java"), "")
                .replace(medicalReload, "")
                .replace(read("src/main/java/com/gamergaming/villagerguardgunners/village/compat/SettlementDefinitions.java"), "")
                .replace(read("src/main/java/com/gamergaming/villagerguardgunners/quartermaster/service/EquipmentProfiles.java"), "")
                .replace(villagePopulation, "")
                .replace(legacyNotice, "")
                .replace(naturalLoadoutReload, "")
                .replace(guardRoleReload, "")
                .replace(guardPostGroupReload, "")
                .replace(largePackSync, "")
                .replace(largePackSyncClient, "")
                .replace(villagePoolStartup, "")
                .replace(villageDefinitionReload, "")
                .replace(villageBuildingState, "")
                .replace(villageDefenseNetworkState, "")
                .replace(villageDefenseDutyState, "")
                .replace(villageBuildingActivation, "")
                .replace(generatedSquadCapacity, "")
                .replace(squadRegistryState, "");

        assertAll(
                () -> assertTrue(villagePopulation.contains("if (state.rejected == 1) LOGGER.warn(")),
                () -> assertFalse(villagePopulation.contains("LOGGER.info(")),
                () -> assertFalse(villagePopulation.contains("LOGGER.debug(")),
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
                () -> assertFalse(naturalLoadoutReload.contains("getEntitiesOfClass(")),
                () -> assertTrue(squadRegistryState.contains("LOGGER.error(")),
                () -> assertFalse(squadRegistryState.contains("LOGGER.info(")),
                () -> assertFalse(squadRegistryState.contains("LOGGER.debug(")),
                () -> assertTrue(villageBuildingState.contains("LOGGER.warn(")),
                () -> assertTrue(villageBuildingState.contains("LOGGER.error(")),
                () -> assertFalse(villageBuildingState.contains("LOGGER.info(")),
                () -> assertFalse(villageBuildingState.contains("LOGGER.debug(")),
                () -> assertFalse(villageBuildingState.contains("tick(")),
                () -> assertTrue(villageDefenseNetworkState.contains("LOGGER.warn(")),
                () -> assertTrue(villageDefenseNetworkState.contains("LOGGER.error(")),
                () -> assertFalse(villageDefenseNetworkState.contains("LOGGER.info(")),
                () -> assertFalse(villageDefenseNetworkState.contains("LOGGER.debug(")),
                () -> assertFalse(villageDefenseNetworkState.contains("tick(")),
                () -> assertTrue(villageDefenseDutyState.contains("LOGGER.warn(")),
                () -> assertTrue(villageDefenseDutyState.contains("LOGGER.error(")),
                () -> assertFalse(villageDefenseDutyState.contains("LOGGER.info(")),
                () -> assertFalse(villageDefenseDutyState.contains("LOGGER.debug(")),
                () -> assertFalse(villageDefenseDutyState.contains("tick(")),
                () -> assertTrue(villageBuildingActivation.contains("LOGGER.warn(")),
                () -> assertFalse(villageBuildingActivation.contains("LOGGER.info(")),
                () -> assertFalse(villageBuildingActivation.contains("LOGGER.debug(")),
                () -> assertTrue(generatedSquadCapacity.contains("LOGGER.warn(")),
                () -> assertFalse(generatedSquadCapacity.contains("LOGGER.info(")),
                () -> assertFalse(generatedSquadCapacity.contains("LOGGER.debug(")));
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
