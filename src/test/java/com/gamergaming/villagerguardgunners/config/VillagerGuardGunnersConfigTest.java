package com.gamergaming.villagerguardgunners.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyLimits;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplySettings;
import com.gamergaming.villagerguardgunners.combat.tactics.ExplosiveStandoffPolicy;
import com.gamergaming.villagerguardgunners.combat.engagement.EngagementMode;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfile;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfileDefinition;
import com.gamergaming.villagerguardgunners.combat.tactics.WeaponTacticalResolver;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficultySettings;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutDefaults;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnSource;
import com.gamergaming.villagerguardgunners.worldgen.structure.WatchtowerGenerationPolicy;

import java.util.Map;
import java.util.Set;
import me.fzzyhmstrs.fzzy_config.entry.EntryValidator.ValidationType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VillagerGuardGunnersConfigTest {

    @Test
    void defaultsMatchTheRecommendedGuardProfile() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();

        assertAll(
                () -> assertEquals(11, VillagerGuardGunnersConfig.CONFIG_VERSION),
                () -> assertTrue(config.enabled.get()),
                () -> assertTrue(config.naturalLoadouts.enabled.get()),
                () -> assertEquals(
                        NaturalGuardLoadoutDefaults.SPAWN_RULES,
                        config.naturalLoadouts.spawnRules()),
                () -> assertAll(java.util.Arrays.stream(NaturalGuardDifficulty.values())
                        .map(difficulty -> () -> assertEquals(
                                NaturalGuardLoadoutDefaults.settingsFor(difficulty),
                                config.naturalLoadouts.settingsFor(difficulty)))),
                () -> assertEquals(48.0D, config.squads.commandDispatchRadius.get()),
                () -> assertEquals(32, config.squads.maximumAffectedGuards.get()),
                () -> assertEquals(1200, config.squads.positionalCommandDurationTicks.get()),
                () -> assertEquals(400, config.squads.focusCommandDurationTicks.get()),
                () -> assertEquals(12.0D, config.squads.defendRadius.get()),
                () -> assertEquals(32.0D, config.squads.airCommandRange.get()),
                () -> assertEquals(5, config.combat.requiredVisibleTicks.get()),
                () -> assertEquals(32.0D, config.combat.maximumEngagementRange.get()),
                () -> assertEquals(4.0D, config.combat.closeRangeDistance.get()),
                () -> assertEquals(10.0D, config.combat.aimToleranceDegrees.get()),
                () -> assertEquals(1.0D, config.movement.approachSpeed.get()),
                () -> assertEquals(0.75D, config.movement.retreatSpeed.get()),
                () -> assertEquals(0.75D, config.movement.repositionSpeed.get()),
                () -> assertEquals(20, config.movement.friendlyRepositionTicks.get()),
                () -> assertEquals(
                        WeaponTacticalResolver.DEFAULT_TYPE_PROFILES,
                        config.tactics.typeProfiles.get()),
                () -> assertTrue(config.tactics.gunOverrides.get().isEmpty()),
                () -> assertAll(java.util.Arrays.stream(TacticalProfile.values())
                        .map(profile -> () -> assertEquals(
                                TacticalProfileDefinition.defaultsFor(profile),
                                config.tactics.definition(profile)))),
                () -> assertEquals(EngagementMode.GUARD_TARGETS, config.engagement.mode.get()),
                () -> assertEquals(200, config.engagement.defensiveMemoryTicks.get()),
                () -> assertTrue(config.engagement.additionalTargets.get().isEmpty()),
                () -> assertTrue(config.engagement.excludedEntities.get().isEmpty()),
                () -> assertEquals(0.75D, config.safety.baseClearance.get()),
                () -> assertTrue(config.safety.preserveBlocksFromGuardExplosions.get()),
                () -> assertEquals(
                        ExplosiveStandoffPolicy.DEFAULT,
                        config.safety.explosiveStandoffPolicy()),
                () -> assertTrue(config.safety.allies.protectOwnedEntities.get()),
                () -> assertTrue(config.safety.allies.protectAlliedEntities.get()),
                () -> assertTrue(config.safety.allies.additionalProtectedEntities.get().isEmpty()),
                () -> assertTrue(config.safety.allies.excludedEntities.get().isEmpty()),
                () -> assertTrue(config.ammunition.pickupDroppedAmmo.get()),
                () -> assertEquals(1.5D, config.ammunition.droppedAmmoPickupRadius.get()),
                () -> assertTrue(config.ammunition.shareAmmoBetweenGuards.get()),
                () -> assertEquals(8.0D, config.ammunition.ammoSharingRadius.get()),
                () -> assertEquals(1, config.ammunition.donorReserveMagazines.get()),
                () -> assertTrue(config.ammunition.requireMatchingOwnerForSharing.get()),
                () -> assertEquals(
                        new ArmoryResupplySettings(
                                true,
                                40,
                                48.0D,
                                8,
                                1,
                                3,
                                0.8D,
                                1200,
                                400,
                                100,
                                200,
                                200),
                        config.armoryResupply.snapshot(true)),
                () -> assertEquals(
                        new WatchtowerGenerationPolicy(true, true, 4, true, true),
                        config.watchtowers.snapshot()),
                () -> assertEquals(1.5D, config.watchtowers.guardGunChanceMultiplier.get()),
                () -> assertEquals(0.525D,
                        config.watchtowers.guardGunUpgradeChance(0.35D),
                        1.0E-12D),
                () -> assertEquals(0.75D,
                        config.watchtowers.guardGunUpgradeChance(0.5D),
                        1.0E-12D),
                () -> assertEquals(0.975D,
                        config.watchtowers.guardGunUpgradeChance(0.65D),
                        1.0E-12D),
                () -> assertEquals(1.0D, config.watchtowers.guardGunUpgradeChance(1.0D)),
                () -> assertEquals(2, config.performance.visibilitySampleIntervalTicks.get()),
                () -> assertEquals(5, config.performance.movementDecisionIntervalTicks.get()),
                () -> assertEquals(20, config.performance.pathRecalculationIntervalTicks.get()),
                () -> assertEquals(20, config.performance.gunDataRefreshIntervalTicks.get()),
                () -> assertEquals(2, config.performance.unsafeShotCacheTicks.get()),
                () -> assertEquals(10, config.performance.repositionProgressCheckTicks.get()));
    }

    @Test
    void versionOneConfigMigratesToSafeExplosionPreservationDefault() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        config.safety.preserveBlocksFromGuardExplosions.accept(false);

        config.update(1);

        assertTrue(config.safety.preserveBlocksFromGuardExplosions.get());
    }

    @Test
    void versionNineUpdateRetainsEveryExistingChoiceAndInitializesWatchtowers() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        ResourceLocation protectedEntity = ResourceLocation.fromNamespaceAndPath(
                "example", "protected_guard");
        config.safety.preserveBlocksFromGuardExplosions.accept(false);
        config.tactics.typeProfiles.accept(Map.of("rifle", TacticalProfile.MARKSMAN));
        config.tactics.support.retreatDistanceMultiplier.accept(3.0D);
        config.safety.explosiveStandoffAdditionalBlocks.accept(12.0D);
        config.safety.allies.protectOwnedEntities.accept(false);
        config.safety.allies.additionalProtectedEntities.accept(Set.of(protectedEntity));
        config.engagement.mode.accept(EngagementMode.DEFENSIVE);
        config.engagement.defensiveMemoryTicks.accept(400);
        config.engagement.additionalTargets.accept(Set.of(protectedEntity));
        config.ammunition.pickupDroppedAmmo.accept(false);
        config.ammunition.droppedAmmoPickupRadius.accept(2.5D);
        config.ammunition.shareAmmoBetweenGuards.accept(false);
        config.ammunition.ammoSharingRadius.accept(12.0D);
        config.ammunition.donorReserveMagazines.accept(3);
        config.ammunition.requireMatchingOwnerForSharing.accept(false);
        config.naturalLoadouts.enabled.accept(false);
        config.naturalLoadouts.enabledSpawnSources.accept(Set.of(
                NaturalGuardSpawnSource.CONVERSION,
                NaturalGuardSpawnSource.COMMAND));
        config.naturalLoadouts.replaceCrossbowsOnly.accept(false);
        config.naturalLoadouts.nearbyCapRadius.accept(128.0D);
        config.naturalLoadouts.allowExplosiveWeapons.accept(true);
        config.naturalLoadouts.easy.upgradeChance.accept(0.75D);
        config.naturalLoadouts.easy.maximumNearby.accept(7);
        config.naturalLoadouts.easy.poolId.accept(ResourceLocation.fromNamespaceAndPath(
                "example", "custom_easy"));
        config.squads.commandDispatchRadius.accept(96.0D);
        config.squads.maximumAffectedGuards.accept(48);
        config.squads.positionalCommandDurationTicks.accept(2400);
        config.squads.focusCommandDurationTicks.accept(800);
        config.squads.defendRadius.accept(24.0D);
        config.squads.airCommandRange.accept(48.0D);
        config.armoryResupply.enabled.accept(false);
        config.armoryResupply.checkIntervalTicks.accept(80);
        config.armoryResupply.searchRadius.accept(32.0D);
        config.armoryResupply.maximumCandidateArmories.accept(16);
        config.armoryResupply.lowReserveMagazines.accept(2);
        config.armoryResupply.targetReserveMagazines.accept(6);
        config.armoryResupply.movementSpeed.accept(1.2D);
        config.armoryResupply.requestLifetimeTicks.accept(1800);
        config.armoryResupply.reservationLifetimeTicks.accept(800);
        config.armoryResupply.successCooldownTicks.accept(300);
        config.armoryResupply.noSupplyRetryTicks.accept(400);
        config.armoryResupply.unreachableRetryTicks.accept(600);
        config.watchtowers.enabled.accept(false);
        config.watchtowers.spawnGuards.accept(false);
        config.watchtowers.maximumGuardsPerPost.accept(2);
        config.watchtowers.placeArmory.accept(false);
        config.watchtowers.stockArmory.accept(false);
        config.watchtowers.guardGunChanceMultiplier.accept(0.25D);

        config.update(9);

        assertAll(
                () -> assertFalse(config.safety.preserveBlocksFromGuardExplosions.get()),
                () -> assertEquals(
                        Map.of("rifle", TacticalProfile.MARKSMAN),
                        config.tactics.typeProfiles.get()),
                () -> assertEquals(3.0D, config.tactics.support.retreatDistanceMultiplier.get()),
                () -> assertEquals(
                        12.0D,
                        config.safety.explosiveStandoffAdditionalBlocks.get()),
                () -> assertFalse(config.safety.allies.protectOwnedEntities.get()),
                () -> assertEquals(
                        Set.of(protectedEntity),
                        config.safety.allies.additionalProtectedEntities.get()),
                () -> assertEquals(EngagementMode.DEFENSIVE, config.engagement.mode.get()),
                () -> assertEquals(400, config.engagement.defensiveMemoryTicks.get()),
                () -> assertEquals(
                        Set.of(protectedEntity),
                        config.engagement.additionalTargets.get()),
                () -> assertFalse(config.ammunition.pickupDroppedAmmo.get()),
                () -> assertEquals(2.5D, config.ammunition.droppedAmmoPickupRadius.get()),
                () -> assertFalse(config.ammunition.shareAmmoBetweenGuards.get()),
                () -> assertEquals(12.0D, config.ammunition.ammoSharingRadius.get()),
                () -> assertEquals(3, config.ammunition.donorReserveMagazines.get()),
                () -> assertFalse(config.ammunition.requireMatchingOwnerForSharing.get()),
                () -> assertFalse(config.naturalLoadouts.enabled.get()),
                () -> assertEquals(
                        Set.of(
                                NaturalGuardSpawnSource.CONVERSION,
                                NaturalGuardSpawnSource.COMMAND),
                        config.naturalLoadouts.enabledSpawnSources.get()),
                () -> assertFalse(config.naturalLoadouts.replaceCrossbowsOnly.get()),
                () -> assertEquals(128.0D, config.naturalLoadouts.nearbyCapRadius.get()),
                () -> assertTrue(config.naturalLoadouts.allowExplosiveWeapons.get()),
                () -> assertEquals(
                        new NaturalGuardDifficultySettings(
                                0.75D,
                                7,
                                ResourceLocation.fromNamespaceAndPath(
                                        "example", "custom_easy")),
                        config.naturalLoadouts.settingsFor(NaturalGuardDifficulty.EASY)),
                () -> assertEquals(96.0D, config.squads.commandDispatchRadius.get()),
                () -> assertEquals(48, config.squads.maximumAffectedGuards.get()),
                () -> assertEquals(2400, config.squads.positionalCommandDurationTicks.get()),
                () -> assertEquals(800, config.squads.focusCommandDurationTicks.get()),
                () -> assertEquals(24.0D, config.squads.defendRadius.get()),
                () -> assertEquals(48.0D, config.squads.airCommandRange.get()),
                () -> assertEquals(
                        new ArmoryResupplySettings(
                                false,
                                80,
                                32.0D,
                                16,
                                2,
                                6,
                                1.2D,
                                1800,
                                800,
                                300,
                                400,
                                600),
                        config.armoryResupply.snapshot(true)),
                () -> assertEquals(
                        new WatchtowerGenerationPolicy(true, true, 4, true, true),
                        config.watchtowers.snapshot()),
                () -> assertEquals(1.5D, config.watchtowers.guardGunChanceMultiplier.get()));
    }

    @Test
    void currentVersionUpdateRetainsExplicitWatchtowerChoices() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        config.watchtowers.enabled.accept(false);
        config.watchtowers.spawnGuards.accept(false);
        config.watchtowers.maximumGuardsPerPost.accept(2);
        config.watchtowers.placeArmory.accept(false);
        config.watchtowers.stockArmory.accept(false);
        config.watchtowers.guardGunChanceMultiplier.accept(0.25D);

        config.update(11);

        assertAll(
                () -> assertEquals(
                        new WatchtowerGenerationPolicy(false, false, 2, false, false),
                        config.watchtowers.snapshot()),
                () -> assertEquals(0.25D,
                        config.watchtowers.guardGunChanceMultiplier.get()));
    }

    @Test
    void versionTenUpdateAddsOnlyTheTowerGunChanceDefault() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        config.combat.requiredVisibleTicks.accept(17);
        config.naturalLoadouts.normal.upgradeChance.accept(0.2D);
        config.watchtowers.enabled.accept(false);
        config.watchtowers.spawnGuards.accept(false);
        config.watchtowers.maximumGuardsPerPost.accept(2);
        config.watchtowers.placeArmory.accept(false);
        config.watchtowers.stockArmory.accept(false);
        config.watchtowers.guardGunChanceMultiplier.accept(0.25D);

        config.update(10);

        assertAll(
                () -> assertEquals(17, config.combat.requiredVisibleTicks.get()),
                () -> assertEquals(0.2D,
                        config.naturalLoadouts.normal.upgradeChance.get()),
                () -> assertEquals(
                        new WatchtowerGenerationPolicy(false, false, 2, false, false),
                        config.watchtowers.snapshot()),
                () -> assertEquals(1.5D,
                        config.watchtowers.guardGunChanceMultiplier.get()));
    }

    @Test
    void versionNineConfigAddsBoundedWatchtowerDefaultsWithoutChangingExistingSettings() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        config.combat.requiredVisibleTicks.accept(17);
        config.armoryResupply.searchRadius.accept(32.0D);
        config.watchtowers.enabled.accept(false);
        config.watchtowers.spawnGuards.accept(false);
        config.watchtowers.maximumGuardsPerPost.accept(1);
        config.watchtowers.placeArmory.accept(false);
        config.watchtowers.stockArmory.accept(false);
        config.watchtowers.guardGunChanceMultiplier.accept(0.25D);

        config.update(9);

        assertAll(
                () -> assertEquals(17, config.combat.requiredVisibleTicks.get()),
                () -> assertEquals(32.0D, config.armoryResupply.searchRadius.get()),
                () -> assertEquals(
                        new WatchtowerGenerationPolicy(true, true, 4, true, true),
                        config.watchtowers.snapshot()),
                () -> assertEquals(1.5D,
                        config.watchtowers.guardGunChanceMultiplier.get()));
    }

    @Test
    void versionEightConfigAddsBoundedArmoryDefaultsWithoutChangingExistingSettings() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        config.combat.requiredVisibleTicks.accept(17);
        config.ammunition.shareAmmoBetweenGuards.accept(false);
        config.squads.commandDispatchRadius.accept(96.0D);
        config.armoryResupply.enabled.accept(false);
        config.armoryResupply.checkIntervalTicks.accept(200);
        config.armoryResupply.searchRadius.accept(64.0D);
        config.armoryResupply.maximumCandidateArmories.accept(32);
        config.armoryResupply.lowReserveMagazines.accept(12);
        config.armoryResupply.targetReserveMagazines.accept(24);
        config.armoryResupply.movementSpeed.accept(2.0D);
        config.armoryResupply.requestLifetimeTicks.accept(2400);
        config.armoryResupply.reservationLifetimeTicks.accept(1200);
        config.armoryResupply.successCooldownTicks.accept(2400);
        config.armoryResupply.noSupplyRetryTicks.accept(2400);
        config.armoryResupply.unreachableRetryTicks.accept(2400);

        config.update(8);

        assertAll(
                () -> assertEquals(17, config.combat.requiredVisibleTicks.get()),
                () -> assertFalse(config.ammunition.shareAmmoBetweenGuards.get()),
                () -> assertEquals(96.0D, config.squads.commandDispatchRadius.get()),
                () -> assertEquals(
                        new ArmoryResupplySettings(
                                true,
                                40,
                                48.0D,
                                8,
                                1,
                                3,
                                0.8D,
                                1200,
                                400,
                                100,
                                200,
                                200),
                        config.armoryResupply.snapshot(true)));
    }

    @Test
    void versionSevenConfigAddsBoundedSquadCommandDefaults() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        config.combat.requiredVisibleTicks.accept(17);
        config.squads.commandDispatchRadius.accept(128.0D);
        config.squads.maximumAffectedGuards.accept(64);
        config.squads.positionalCommandDurationTicks.accept(72000);
        config.squads.focusCommandDurationTicks.accept(12000);
        config.squads.defendRadius.accept(64.0D);
        config.squads.airCommandRange.accept(64.0D);

        config.update(7);

        assertAll(
                () -> assertEquals(17, config.combat.requiredVisibleTicks.get()),
                () -> assertEquals(48.0D, config.squads.commandDispatchRadius.get()),
                () -> assertEquals(32, config.squads.maximumAffectedGuards.get()),
                () -> assertEquals(1200, config.squads.positionalCommandDurationTicks.get()),
                () -> assertEquals(400, config.squads.focusCommandDurationTicks.get()),
                () -> assertEquals(12.0D, config.squads.defendRadius.get()),
                () -> assertEquals(32.0D, config.squads.airCommandRange.get()));
    }

    @Test
    void versionSixConfigAddsConservativeNaturalLoadoutDefaults() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        config.combat.requiredVisibleTicks.accept(17);
        config.naturalLoadouts.enabled.accept(false);
        config.naturalLoadouts.enabledSpawnSources.accept(Set.of(
                NaturalGuardSpawnSource.CONVERSION,
                NaturalGuardSpawnSource.SPAWN_EGG,
                NaturalGuardSpawnSource.COMMAND,
                NaturalGuardSpawnSource.OTHER));
        config.naturalLoadouts.replaceCrossbowsOnly.accept(false);
        config.naturalLoadouts.nearbyCapRadius.accept(192.0D);
        config.naturalLoadouts.allowExplosiveWeapons.accept(true);
        config.naturalLoadouts.easy.upgradeChance.accept(1.0D);
        config.naturalLoadouts.normal.maximumNearby.accept(32);
        config.naturalLoadouts.hard.poolId.accept(ResourceLocation.fromNamespaceAndPath(
                "example", "old_hard"));

        config.update(6);

        assertAll(
                () -> assertEquals(17, config.combat.requiredVisibleTicks.get()),
                () -> assertTrue(config.naturalLoadouts.enabled.get()),
                () -> assertEquals(
                        NaturalGuardLoadoutDefaults.SPAWN_RULES,
                        config.naturalLoadouts.spawnRules()),
                () -> assertAll(java.util.Arrays.stream(NaturalGuardDifficulty.values())
                        .map(difficulty -> () -> assertEquals(
                                NaturalGuardLoadoutDefaults.settingsFor(difficulty),
                                config.naturalLoadouts.settingsFor(difficulty)))));
    }

    @Test
    void versionFiveConfigAddsSafeAmmunitionDefaultsWithoutChangingExistingSettings() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        config.combat.requiredVisibleTicks.accept(17);
        config.ammunition.pickupDroppedAmmo.accept(false);
        config.ammunition.droppedAmmoPickupRadius.accept(3.0D);
        config.ammunition.shareAmmoBetweenGuards.accept(false);
        config.ammunition.ammoSharingRadius.accept(16.0D);
        config.ammunition.donorReserveMagazines.accept(4);
        config.ammunition.requireMatchingOwnerForSharing.accept(false);

        config.update(5);

        assertAll(
                () -> assertEquals(17, config.combat.requiredVisibleTicks.get()),
                () -> assertTrue(config.ammunition.pickupDroppedAmmo.get()),
                () -> assertEquals(1.5D, config.ammunition.droppedAmmoPickupRadius.get()),
                () -> assertTrue(config.ammunition.shareAmmoBetweenGuards.get()),
                () -> assertEquals(8.0D, config.ammunition.ammoSharingRadius.get()),
                () -> assertEquals(1, config.ammunition.donorReserveMagazines.get()),
                () -> assertTrue(config.ammunition.requireMatchingOwnerForSharing.get()));
    }

    @Test
    void versionFourConfigAddsCompatibilityPreservingEngagementDefaults() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        ResourceLocation customEntity = ResourceLocation.fromNamespaceAndPath("example", "target");
        config.combat.requiredVisibleTicks.accept(17);
        config.engagement.mode.accept(EngagementMode.HOLD_FIRE);
        config.engagement.defensiveMemoryTicks.accept(800);
        config.engagement.additionalTargets.accept(Set.of(customEntity));
        config.engagement.excludedEntities.accept(Set.of(customEntity));

        config.update(4);

        assertAll(
                () -> assertEquals(17, config.combat.requiredVisibleTicks.get()),
                () -> assertEquals(EngagementMode.GUARD_TARGETS, config.engagement.mode.get()),
                () -> assertEquals(200, config.engagement.defensiveMemoryTicks.get()),
                () -> assertTrue(config.engagement.additionalTargets.get().isEmpty()),
                () -> assertTrue(config.engagement.excludedEntities.get().isEmpty()));
    }

    @Test
    void versionThreeConfigAddsSafeAllyDefaultsWithoutChangingExistingSettings() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        ResourceLocation customEntity = ResourceLocation.fromNamespaceAndPath("example", "companion");
        config.combat.requiredVisibleTicks.accept(13);
        config.safety.allies.protectOwnedEntities.accept(false);
        config.safety.allies.protectAlliedEntities.accept(false);
        config.safety.allies.additionalProtectedEntities.accept(Set.of(customEntity));
        config.safety.allies.excludedEntities.accept(Set.of(customEntity));

        config.update(3);

        assertAll(
                () -> assertEquals(13, config.combat.requiredVisibleTicks.get()),
                () -> assertTrue(config.safety.allies.protectOwnedEntities.get()),
                () -> assertTrue(config.safety.allies.protectAlliedEntities.get()),
                () -> assertTrue(config.safety.allies.additionalProtectedEntities.get().isEmpty()),
                () -> assertTrue(config.safety.allies.excludedEntities.get().isEmpty()));
    }

    @Test
    void versionTwoConfigAddsTacticalDefaultsWithoutChangingExistingSettings() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        ResourceLocation gunId = ResourceLocation.fromNamespaceAndPath("example", "test_gun");
        config.combat.requiredVisibleTicks.accept(11);
        config.safety.preserveBlocksFromGuardExplosions.accept(false);
        config.tactics.typeProfiles.accept(Map.of("rifle", TacticalProfile.MARKSMAN));
        config.tactics.gunOverrides.accept(Map.of(gunId, TacticalProfile.SUPPORT));
        config.tactics.breacher.preferredRangeMultiplier.accept(3.0D);
        config.safety.explosiveStandoffRadiusMultiplier.accept(7.0D);

        config.update(2);

        assertAll(
                () -> assertEquals(11, config.combat.requiredVisibleTicks.get()),
                () -> assertFalse(config.safety.preserveBlocksFromGuardExplosions.get()),
                () -> assertEquals(
                        WeaponTacticalResolver.DEFAULT_TYPE_PROFILES,
                        config.tactics.typeProfiles.get()),
                () -> assertTrue(config.tactics.gunOverrides.get().isEmpty()),
                () -> assertEquals(
                        TacticalProfileDefinition.defaultsFor(TacticalProfile.BREACHER),
                        config.tactics.definition(TacticalProfile.BREACHER)),
                () -> assertEquals(
                        ExplosiveStandoffPolicy.DEFAULT,
                        config.safety.explosiveStandoffPolicy()));
    }

    @Test
    void validatedFieldsCorrectValuesOutsideTheirSupportedRanges() {
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();

        assertAll(
                () -> assertEquals(0, config.combat.requiredVisibleTicks
                        .correctEntry(-1, ValidationType.STRONG).get()),
                () -> assertEquals(40, config.combat.requiredVisibleTicks
                        .correctEntry(41, ValidationType.STRONG).get()),
                () -> assertEquals(8.0D, config.combat.maximumEngagementRange
                        .correctEntry(7.0D, ValidationType.STRONG).get()),
                () -> assertEquals(64.0D, config.combat.maximumEngagementRange
                        .correctEntry(65.0D, ValidationType.STRONG).get()),
                () -> assertEquals(1.0D, config.combat.closeRangeDistance
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(8.0D, config.combat.closeRangeDistance
                        .correctEntry(9.0D, ValidationType.STRONG).get()),
                () -> assertEquals(0.1D, config.movement.approachSpeed
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(2.0D, config.movement.approachSpeed
                        .correctEntry(3.0D, ValidationType.STRONG).get()),
                () -> assertEquals(0.25D, config.tactics.generalist.preferredRangeMultiplier
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(4.0D, config.tactics.generalist.preferredRangeMultiplier
                        .correctEntry(5.0D, ValidationType.STRONG).get()),
                () -> assertEquals(-40, config.tactics.generalist.visibleTicksAdjustment
                        .correctEntry(-41, ValidationType.STRONG).get()),
                () -> assertEquals(40, config.tactics.generalist.visibleTicksAdjustment
                        .correctEntry(41, ValidationType.STRONG).get()),
                () -> assertEquals(0.25D, config.safety.baseClearance
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(4.0D, config.safety.baseClearance
                        .correctEntry(5.0D, ValidationType.STRONG).get()),
                () -> assertEquals(1.0D, config.safety.explosiveStandoffRadiusMultiplier
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(8.0D, config.safety.explosiveStandoffRadiusMultiplier
                        .correctEntry(9.0D, ValidationType.STRONG).get()),
                () -> assertEquals(0.0D, config.safety.explosiveStandoffAdditionalBlocks
                        .correctEntry(-1.0D, ValidationType.STRONG).get()),
                () -> assertEquals(32.0D, config.safety.explosiveStandoffAdditionalBlocks
                        .correctEntry(33.0D, ValidationType.STRONG).get()),
                () -> assertEquals(20, config.engagement.defensiveMemoryTicks
                        .correctEntry(19, ValidationType.STRONG).get()),
                () -> assertEquals(1200, config.engagement.defensiveMemoryTicks
                        .correctEntry(1201, ValidationType.STRONG).get()),
                () -> assertEquals(0.5D, config.ammunition.droppedAmmoPickupRadius
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(3.0D, config.ammunition.droppedAmmoPickupRadius
                        .correctEntry(4.0D, ValidationType.STRONG).get()),
                () -> assertEquals(1.0D, config.ammunition.ammoSharingRadius
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(16.0D, config.ammunition.ammoSharingRadius
                        .correctEntry(17.0D, ValidationType.STRONG).get()),
                () -> assertEquals(0, config.ammunition.donorReserveMagazines
                        .correctEntry(-1, ValidationType.STRONG).get()),
                () -> assertEquals(4, config.ammunition.donorReserveMagazines
                        .correctEntry(5, ValidationType.STRONG).get()),
                () -> assertEquals(20, config.armoryResupply.checkIntervalTicks
                        .correctEntry(19, ValidationType.STRONG).get()),
                () -> assertEquals(200, config.armoryResupply.checkIntervalTicks
                        .correctEntry(201, ValidationType.STRONG).get()),
                () -> assertEquals(ArmoryResupplyLimits.MIN_SEARCH_RADIUS,
                        config.armoryResupply.searchRadius
                                .correctEntry(7.0D, ValidationType.STRONG).get()),
                () -> assertEquals(ArmoryResupplyLimits.HARD_MAX_SEARCH_RADIUS,
                        config.armoryResupply.searchRadius
                                .correctEntry(65.0D, ValidationType.STRONG).get()),
                () -> assertEquals(1, config.armoryResupply.maximumCandidateArmories
                        .correctEntry(0, ValidationType.STRONG).get()),
                () -> assertEquals(ArmoryResupplyLimits.HARD_MAX_CANDIDATE_ARMORIES,
                        config.armoryResupply.maximumCandidateArmories
                                .correctEntry(33, ValidationType.STRONG).get()),
                () -> assertEquals(0, config.armoryResupply.lowReserveMagazines
                        .correctEntry(-1, ValidationType.STRONG).get()),
                () -> assertEquals(ArmoryResupplyLimits.HARD_MAX_RESERVE_MAGAZINES,
                        config.armoryResupply.targetReserveMagazines
                                .correctEntry(65, ValidationType.STRONG).get()),
                () -> assertEquals(0.1D, config.armoryResupply.movementSpeed
                        .correctEntry(0.0D, ValidationType.STRONG).get()),
                () -> assertEquals(ArmoryResupplyLimits.HARD_MAX_RESUPPLY_MOVEMENT_SPEED,
                        config.armoryResupply.movementSpeed
                                .correctEntry(3.0D, ValidationType.STRONG).get()),
                () -> assertEquals(20, config.armoryResupply.requestLifetimeTicks
                        .correctEntry(19, ValidationType.STRONG).get()),
                () -> assertEquals(ArmoryResupplyLimits.HARD_MAX_REQUEST_LIFETIME_TICKS,
                        config.armoryResupply.requestLifetimeTicks
                                .correctEntry(2401, ValidationType.STRONG).get()),
                () -> assertEquals(20, config.armoryResupply.reservationLifetimeTicks
                        .correctEntry(19, ValidationType.STRONG).get()),
                () -> assertEquals(ArmoryResupplyLimits.HARD_MAX_RESERVATION_LIFETIME_TICKS,
                        config.armoryResupply.reservationLifetimeTicks
                                .correctEntry(1201, ValidationType.STRONG).get()),
                () -> assertEquals(20, config.armoryResupply.successCooldownTicks
                        .correctEntry(19, ValidationType.STRONG).get()),
                () -> assertEquals(ArmoryResupplyLimits.HARD_MAX_RETRY_TICKS,
                        config.armoryResupply.noSupplyRetryTicks
                                .correctEntry(2401, ValidationType.STRONG).get()),
                () -> assertEquals(ArmoryResupplyLimits.HARD_MAX_RETRY_TICKS,
                        config.armoryResupply.unreachableRetryTicks
                                .correctEntry(2401, ValidationType.STRONG).get()),
                () -> assertEquals(0, config.watchtowers.maximumGuardsPerPost
                        .correctEntry(-1, ValidationType.STRONG).get()),
                () -> assertEquals(4, config.watchtowers.maximumGuardsPerPost
                        .correctEntry(5, ValidationType.STRONG).get()),
                () -> assertEquals(0.0D, config.watchtowers.guardGunChanceMultiplier
                        .correctEntry(-0.1D, ValidationType.STRONG).get()),
                () -> assertEquals(3.0D, config.watchtowers.guardGunChanceMultiplier
                        .correctEntry(3.1D, ValidationType.STRONG).get()),
                () -> assertEquals(8.0D, config.naturalLoadouts.nearbyCapRadius
                        .correctEntry(7.0D, ValidationType.STRONG).get()),
                () -> assertEquals(256.0D, config.naturalLoadouts.nearbyCapRadius
                        .correctEntry(257.0D, ValidationType.STRONG).get()),
                () -> assertEquals(0.0D, config.naturalLoadouts.easy.upgradeChance
                        .correctEntry(-0.1D, ValidationType.STRONG).get()),
                () -> assertEquals(1.0D, config.naturalLoadouts.easy.upgradeChance
                        .correctEntry(1.1D, ValidationType.STRONG).get()),
                () -> assertEquals(0, config.naturalLoadouts.easy.maximumNearby
                        .correctEntry(-1, ValidationType.STRONG).get()),
                () -> assertEquals(32, config.naturalLoadouts.easy.maximumNearby
                        .correctEntry(33, ValidationType.STRONG).get()),
                () -> assertEquals(8.0D, config.squads.commandDispatchRadius
                        .correctEntry(7.0D, ValidationType.STRONG).get()),
                () -> assertEquals(128.0D, config.squads.commandDispatchRadius
                        .correctEntry(129.0D, ValidationType.STRONG).get()),
                () -> assertEquals(1, config.squads.maximumAffectedGuards
                        .correctEntry(0, ValidationType.STRONG).get()),
                () -> assertEquals(64, config.squads.maximumAffectedGuards
                        .correctEntry(65, ValidationType.STRONG).get()),
                () -> assertEquals(20, config.squads.positionalCommandDurationTicks
                        .correctEntry(19, ValidationType.STRONG).get()),
                () -> assertEquals(72000, config.squads.positionalCommandDurationTicks
                        .correctEntry(72001, ValidationType.STRONG).get()),
                () -> assertEquals(20, config.squads.focusCommandDurationTicks
                        .correctEntry(19, ValidationType.STRONG).get()),
                () -> assertEquals(12000, config.squads.focusCommandDurationTicks
                        .correctEntry(12001, ValidationType.STRONG).get()),
                () -> assertEquals(2.0D, config.squads.defendRadius
                        .correctEntry(1.0D, ValidationType.STRONG).get()),
                () -> assertEquals(64.0D, config.squads.defendRadius
                        .correctEntry(65.0D, ValidationType.STRONG).get()),
                () -> assertEquals(4.0D, config.squads.airCommandRange
                        .correctEntry(3.0D, ValidationType.STRONG).get()),
                () -> assertEquals(64.0D, config.squads.airCommandRange
                        .correctEntry(65.0D, ValidationType.STRONG).get()),
                () -> assertEquals(1, config.performance.visibilitySampleIntervalTicks
                        .correctEntry(0, ValidationType.STRONG).get()),
                () -> assertEquals(200, config.performance.gunDataRefreshIntervalTicks
                        .correctEntry(201, ValidationType.STRONG).get()),
                () -> assertEquals(0, config.performance.unsafeShotCacheTicks
                        .correctEntry(-1, ValidationType.STRONG).get()),
                () -> assertEquals(5, config.performance.unsafeShotCacheTicks
                        .correctEntry(6, ValidationType.STRONG).get()));
    }

    @Test
    void tacticalProfileEnumSuppliesLocalizedFzzyKeys() {
        assertAll(
                () -> assertEquals(
                        "config.villagerguardgunners.tactical_profile.GENERALIST",
                        TacticalProfile.GENERALIST.translationKey()),
                () -> assertEquals(
                        "config.villagerguardgunners.tactical_profile.DEMOLITIONS.desc",
                        TacticalProfile.DEMOLITIONS.descriptionKey()));
    }

    @Test
    void engagementModeEnumSuppliesLocalizedFzzyKeys() {
        assertAll(
                () -> assertEquals(
                        "config.villagerguardgunners.engagement_mode.GUARD_TARGETS",
                        EngagementMode.GUARD_TARGETS.translationKey()),
                () -> assertEquals(
                        "config.villagerguardgunners.engagement_mode.HOLD_FIRE.desc",
                        EngagementMode.HOLD_FIRE.descriptionKey()));
    }

    @Test
    void naturalSpawnSourceEnumSuppliesLocalizedFzzyKeys() {
        assertAll(
                () -> assertEquals(
                        "config.villagerguardgunners.natural_spawn_source.STRUCTURE",
                        NaturalGuardSpawnSource.STRUCTURE.translationKey()),
                () -> assertEquals(
                        "config.villagerguardgunners.natural_spawn_source.OTHER.desc",
                        NaturalGuardSpawnSource.OTHER.descriptionKey()));
    }

}
