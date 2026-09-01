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
                () -> assertTrue(config.contains("public static final int CONFIG_VERSION = 12;")),
                () -> assertTrue(config.contains("public void update(int previousVersion)")),
                () -> assertTrue(config.contains("previousVersion < 2")),
                () -> assertTrue(config.contains("previousVersion < 3")),
                () -> assertTrue(config.contains("previousVersion < 8")),
                () -> assertTrue(config.contains("previousVersion < 9")),
                () -> assertTrue(config.contains("previousVersion < 10")),
                () -> assertTrue(config.contains("previousVersion < 11")),
                () -> assertTrue(config.contains("previousVersion < 4")),
                () -> assertTrue(config.contains("previousVersion < 5")),
                () -> assertTrue(config.contains("previousVersion < 6")),
                () -> assertTrue(config.contains("previousVersion < 7")),
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
                () -> assertTrue(config.contains("public NaturalLoadoutSettings naturalLoadouts")),
                () -> assertTrue(config.contains("public SquadSettings squads")),
                () -> assertTrue(config.contains("public CombatSettings combat")),
                () -> assertTrue(config.contains("public MovementSettings movement")),
                () -> assertTrue(config.contains("public TacticalSettings tactics")),
                () -> assertTrue(config.contains("public EngagementSettings engagement")),
                () -> assertTrue(config.contains("public SafetySettings safety")),
                () -> assertTrue(config.contains("public AmmunitionSettings ammunition")),
                () -> assertTrue(config.contains("public ArmorySettings armoryResupply")),
                () -> assertTrue(config.contains("public WatchtowerSettings watchtowers")),
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
                "naturalLoadouts.enabled",
                "naturalLoadouts.enabledSpawnSources",
                "naturalLoadouts.replaceCrossbowsOnly",
                "naturalLoadouts.nearbyCapRadius",
                "naturalLoadouts.allowExplosiveWeapons",
                "naturalLoadouts.easy.upgradeChance",
                "naturalLoadouts.easy.maximumNearby",
                "naturalLoadouts.easy.poolId",
                "naturalLoadouts.normal.upgradeChance",
                "naturalLoadouts.normal.maximumNearby",
                "naturalLoadouts.normal.poolId",
                "naturalLoadouts.hard.upgradeChance",
                "naturalLoadouts.hard.maximumNearby",
                "naturalLoadouts.hard.poolId",
                "squads.commandDispatchRadius",
                "squads.maximumAffectedGuards",
                "squads.positionalCommandDurationTicks",
                "squads.focusCommandDurationTicks",
                "squads.defendRadius",
                "squads.airCommandRange",
                "combat.requiredVisibleTicks",
                "combat.maximumEngagementRange",
                "combat.closeRangeDistance",
                "combat.aimToleranceDegrees",
                "movement.approachSpeed",
                "movement.retreatSpeed",
                "movement.repositionSpeed",
                "movement.friendlyRepositionTicks",
                "tactics.typeProfiles",
                "tactics.gunOverrides",
                "tactics.generalist.preferredRangeMultiplier",
                "tactics.generalist.retreatDistanceMultiplier",
                "tactics.generalist.visibleTicksAdjustment",
                "tactics.generalist.aimToleranceMultiplier",
                "tactics.generalist.limitFiringToEffectiveRange",
                "tactics.generalist.maximumFiringRangeMultiplier",
                "tactics.skirmisher.preferredRangeMultiplier",
                "tactics.skirmisher.retreatDistanceMultiplier",
                "tactics.skirmisher.visibleTicksAdjustment",
                "tactics.skirmisher.aimToleranceMultiplier",
                "tactics.skirmisher.limitFiringToEffectiveRange",
                "tactics.skirmisher.maximumFiringRangeMultiplier",
                "tactics.breacher.preferredRangeMultiplier",
                "tactics.breacher.retreatDistanceMultiplier",
                "tactics.breacher.visibleTicksAdjustment",
                "tactics.breacher.aimToleranceMultiplier",
                "tactics.breacher.limitFiringToEffectiveRange",
                "tactics.breacher.maximumFiringRangeMultiplier",
                "tactics.marksman.preferredRangeMultiplier",
                "tactics.marksman.retreatDistanceMultiplier",
                "tactics.marksman.visibleTicksAdjustment",
                "tactics.marksman.aimToleranceMultiplier",
                "tactics.marksman.limitFiringToEffectiveRange",
                "tactics.marksman.maximumFiringRangeMultiplier",
                "tactics.support.preferredRangeMultiplier",
                "tactics.support.retreatDistanceMultiplier",
                "tactics.support.visibleTicksAdjustment",
                "tactics.support.aimToleranceMultiplier",
                "tactics.support.limitFiringToEffectiveRange",
                "tactics.support.maximumFiringRangeMultiplier",
                "tactics.demolitions.preferredRangeMultiplier",
                "tactics.demolitions.retreatDistanceMultiplier",
                "tactics.demolitions.visibleTicksAdjustment",
                "tactics.demolitions.aimToleranceMultiplier",
                "tactics.demolitions.limitFiringToEffectiveRange",
                "tactics.demolitions.maximumFiringRangeMultiplier",
                "engagement.mode",
                "engagement.defensiveMemoryTicks",
                "engagement.additionalTargets",
                "engagement.excludedEntities",
                "safety.baseClearance",
                "safety.preserveBlocksFromGuardExplosions",
                "safety.explosiveStandoffRadiusMultiplier",
                "safety.explosiveStandoffAdditionalBlocks",
                "safety.allies.protectOwnedEntities",
                "safety.allies.protectAlliedEntities",
                "safety.allies.additionalProtectedEntities",
                "safety.allies.excludedEntities",
                "ammunition.pickupDroppedAmmo",
                "ammunition.droppedAmmoPickupRadius",
                "ammunition.shareAmmoBetweenGuards",
                "ammunition.ammoSharingRadius",
                "ammunition.donorReserveMagazines",
                "ammunition.requireMatchingOwnerForSharing",
                "armoryResupply.enabled",
                "armoryResupply.checkIntervalTicks",
                "armoryResupply.searchRadius",
                "armoryResupply.maximumCandidateArmories",
                "armoryResupply.lowReserveMagazines",
                "armoryResupply.targetReserveMagazines",
                "armoryResupply.movementSpeed",
                "armoryResupply.requestLifetimeTicks",
                "armoryResupply.reservationLifetimeTicks",
                "armoryResupply.successCooldownTicks",
                "armoryResupply.noSupplyRetryTicks",
                "armoryResupply.unreachableRetryTicks",
                "watchtowers.enabled",
                "watchtowers.spawnGuards",
                "watchtowers.maximumGuardsPerPost",
                "watchtowers.placeArmory",
                "watchtowers.stockArmory",
                "watchtowers.guardGunChanceMultiplier",
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

        String[] profiles = {
                "GENERALIST",
                "SKIRMISHER",
                "BREACHER",
                "MARKSMAN",
                "SUPPORT",
                "DEMOLITIONS"
        };
        assertAll(java.util.Arrays.stream(profiles)
                .map(profile -> (Executable) () -> assertTrue(
                        translations.contains(
                                "config.villagerguardgunners.tactical_profile."
                                        + profile
                                        + "\""),
                        () -> "Missing tactical profile translation for " + profile)));

        String[] engagementModes = {
                "GUARD_TARGETS",
                "HOSTILE_MOBS_ONLY",
                "DEFENSIVE",
                "HOLD_FIRE"
        };
        assertAll(java.util.Arrays.stream(engagementModes)
                .map(mode -> (Executable) () -> assertTrue(
                        translations.contains(
                                "config.villagerguardgunners.engagement_mode."
                                        + mode
                                        + "\""),
                        () -> "Missing engagement mode translation for " + mode)));

        String[] naturalSpawnSources = {
                "STRUCTURE",
                "CONVERSION",
                "SPAWN_EGG",
                "COMMAND",
                "OTHER"
        };
        assertAll(java.util.Arrays.stream(naturalSpawnSources)
                .map(source -> (Executable) () -> assertTrue(
                        translations.contains(
                                "config.villagerguardgunners.natural_spawn_source."
                                        + source
                                        + "\""),
                        () -> "Missing natural spawn source translation for " + source)));
    }

    @Test
    void establishedConfigSectionsRemainConnectedToTheRuntimeDecisionLayer() throws IOException {
        String goal = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardGunCombatGoal.java");
        String movement = read(
                "src/main/java/com/gamergaming/villagerguardgunners/ai/GuardCombatMovement.java");
        String controller = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/NativeGunController.java");
        String safetyEvents = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/GuardFriendlyFireEvents.java");
        String allyProtection = read(
                "src/main/java/com/gamergaming/villagerguardgunners/api/GuardAllyProtection.java");
        String engagement = read(
                "src/main/java/com/gamergaming/villagerguardgunners/combat/engagement/GuardRulesOfEngagement.java");
        String diagnostics = read(
                "src/main/java/com/gamergaming/villagerguardgunners/command/GuardGunnerCommands.java");

        assertAll(
                () -> assertTrue(goal.contains("config.enabled.get()")),
                () -> assertTrue(goal.contains("gunController.getRequiredVisibleTicks()")),
                () -> assertTrue(goal.contains("gunController.getRetreatDistance()")),
                () -> assertTrue(goal.contains("gunController.getAimToleranceDegrees()")),
                () -> assertTrue(goal.contains("config.movement.approachSpeed.get()")),
                () -> assertTrue(goal.contains("config.movement.retreatSpeed.get()")),
                () -> assertTrue(goal.contains("config.movement.repositionSpeed.get()")),
                () -> assertTrue(goal.contains("config.movement.friendlyRepositionTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.visibilitySampleIntervalTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.movementDecisionIntervalTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.pathRecalculationIntervalTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.unsafeShotCacheTicks.get()")),
                () -> assertTrue(goal.contains("config.performance.repositionProgressCheckTicks.get()")),
                () -> assertTrue(movement.contains("double retreatRangeSquared")),
                () -> assertTrue(controller.contains("config.combat.requiredVisibleTicks.get()")),
                () -> assertTrue(controller.contains("config.combat.closeRangeDistance.get()")),
                () -> assertTrue(controller.contains("config.combat.maximumEngagementRange.get()")),
                () -> assertTrue(controller.contains("config.combat.aimToleranceDegrees.get()")),
                () -> assertTrue(controller.contains("config.safety.baseClearance.get()")),
                () -> assertTrue(controller.contains("config.performance.gunDataRefreshIntervalTicks.get()")),
                () -> assertTrue(safetyEvents.contains(
                        "safety.preserveBlocksFromGuardExplosions.get()")),
                () -> assertTrue(allyProtection.contains("settings.excludedEntities.get()")),
                () -> assertTrue(allyProtection.contains(
                        "settings.additionalProtectedEntities.get()")),
                () -> assertTrue(allyProtection.contains("settings.protectOwnedEntities.get()")),
                () -> assertTrue(allyProtection.contains("settings.protectAlliedEntities.get()")),
                () -> assertTrue(engagement.contains("settings.mode.get()")),
                () -> assertTrue(engagement.contains("settings.defensiveMemoryTicks.get()")),
                () -> assertTrue(engagement.contains("settings.additionalTargets.get()")),
                () -> assertTrue(engagement.contains("settings.excludedEntities.get()")),
                () -> assertTrue(diagnostics.contains("armoryResupply.snapshot(")),
                () -> assertTrue(diagnostics.contains("ArmoryResupplyDiagnostics.capture(")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectDirectory.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
