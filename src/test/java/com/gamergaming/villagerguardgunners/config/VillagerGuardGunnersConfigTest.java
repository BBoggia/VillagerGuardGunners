package com.gamergaming.villagerguardgunners.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.combat.tactics.ExplosiveStandoffPolicy;
import com.gamergaming.villagerguardgunners.combat.engagement.EngagementMode;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfile;
import com.gamergaming.villagerguardgunners.combat.tactics.TacticalProfileDefinition;
import com.gamergaming.villagerguardgunners.combat.tactics.WeaponTacticalResolver;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficultySettings;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutDefaults;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnSource;

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
                () -> assertEquals(7, VillagerGuardGunnersConfig.CONFIG_VERSION),
                () -> assertTrue(config.enabled.get()),
                () -> assertTrue(config.naturalLoadouts.enabled.get()),
                () -> assertEquals(
                        NaturalGuardLoadoutDefaults.SPAWN_RULES,
                        config.naturalLoadouts.spawnRules()),
                () -> assertAll(java.util.Arrays.stream(NaturalGuardDifficulty.values())
                        .map(difficulty -> () -> assertEquals(
                                NaturalGuardLoadoutDefaults.settingsFor(difficulty),
                                config.naturalLoadouts.settingsFor(difficulty)))),
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
    void currentVersionUpdateRetainsExplicitChoices() {
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

        config.update(7);

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
                        config.naturalLoadouts.settingsFor(NaturalGuardDifficulty.EASY)));
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
