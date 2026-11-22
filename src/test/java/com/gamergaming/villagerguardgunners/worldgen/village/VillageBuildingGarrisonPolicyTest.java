package com.gamergaming.villagerguardgunners.worldgen.village;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VillageBuildingGarrisonPolicyTest {
    @Test
    void buildingSpecificAndGlobalLimitsRemainBounded() {
        VillageBuildingGarrisonPolicy full =
                new VillageBuildingGarrisonPolicy(true, 4, 3.0D);
        VillageBuildingGarrisonPolicy reduced =
                new VillageBuildingGarrisonPolicy(true, 1, 1.0D);

        assertAll(
                () -> assertEquals(4,
                        full.limitGuardCount(VillageBuildingType.BARRACKS, 4)),
                () -> assertEquals(1,
                        full.limitGuardCount(VillageBuildingType.SENTRY_POST, 4)),
                () -> assertEquals(2,
                        full.limitGuardCount(VillageBuildingType.WATCHTOWER, 4)),
                () -> assertEquals(0,
                        full.limitGuardCount(VillageBuildingType.ARMORY, 4)),
                () -> assertEquals(2,
                        full.limitGuardCount(VillageBuildingType.TRAINING_YARD, 4)),
                () -> assertEquals(1,
                        reduced.limitGuardCount(VillageBuildingType.BARRACKS, 4)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> full.limitGuardCount(VillageBuildingType.BARRACKS, -1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new VillageBuildingGarrisonPolicy(true, 5, 1.0D)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new VillageBuildingGarrisonPolicy(true, 4, Double.NaN)));
    }

    @Test
    void chanceMultiplierClampsWithoutChangingEligibility() {
        VillageBuildingGarrisonPolicy policy =
                new VillageBuildingGarrisonPolicy(true, 4, 3.0D);

        assertAll(
                () -> assertEquals(0.0D, policy.guardGunUpgradeChance(0.0D)),
                () -> assertEquals(1.0D, policy.guardGunUpgradeChance(0.35D)),
                () -> assertEquals(1.0D, policy.guardGunUpgradeChance(1.0D)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> policy.guardGunUpgradeChance(-0.1D)));
    }

    @Test
    void onlyFourBuildingTypesOwnGarrisonPlansAndSeedsAreIdentityStable() {
        VillageBuildingIdentity barracks = identity(
                VillageBuildingType.BARRACKS, new BlockPos(2, 70, -4));
        VillageBuildingIdentity moved = identity(
                VillageBuildingType.BARRACKS, new BlockPos(3, 70, -4));

        assertAll(
                () -> assertEquals(
                        VillageBuildingGarrisons.BARRACKS_GROUP,
                        VillageBuildingGarrisons.planFor(VillageBuildingType.BARRACKS)
                                .orElseThrow().groupId()),
                () -> assertEquals(0,
                        VillageBuildingGarrisons.planFor(VillageBuildingType.SENTRY_POST)
                                .orElseThrow().verticalSpawnOffset()),
                () -> assertEquals(0,
                        VillageBuildingGarrisons.planFor(VillageBuildingType.WATCHTOWER)
                                .orElseThrow().verticalSpawnOffset()),
                () -> assertTrue(VillageBuildingGarrisons.planFor(
                        VillageBuildingType.ARMORY).isEmpty()),
                () -> assertEquals(
                        VillageBuildingGarrisons.TRAINING_YARD_GROUP,
                        VillageBuildingGarrisons.planFor(
                                VillageBuildingType.TRAINING_YARD)
                                .orElseThrow().groupId()),
                () -> assertEquals(0,
                        VillageBuildingGarrisons.planFor(
                                VillageBuildingType.TRAINING_YARD)
                                .orElseThrow().verticalSpawnOffset()),
                () -> assertTrue(VillageBuildingGarrisons.isVillageGroup(
                        VillageBuildingGarrisons.WATCHTOWER_GROUP)),
                () -> assertTrue(VillageBuildingGarrisons.isVillageGroup(
                        VillageBuildingGarrisons.TRAINING_YARD_GROUP)),
                () -> assertFalse(VillageBuildingGarrisons.isVillageGroup(
                        ResourceLocation.fromNamespaceAndPath(
                                "villagerguardgunners", "watchtower"))),
                () -> assertEquals(
                        VillageBuildingGarrisonLifecycle.selectionSeed(barracks),
                        VillageBuildingGarrisonLifecycle.selectionSeed(barracks)),
                () -> assertNotEquals(
                        VillageBuildingGarrisonLifecycle.selectionSeed(barracks),
                        VillageBuildingGarrisonLifecycle.selectionSeed(moved)));
    }

    private static VillageBuildingIdentity identity(
            VillageBuildingType type,
            BlockPos position) {
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath(
                "minecraft", "overworld");
        ResourceLocation entry = ResourceLocation.fromNamespaceAndPath(
                "example", type.serializedName());
        UUID id = VillageBuildingIds.buildingId(7L, dimension, entry, type, position);
        return new VillageBuildingIdentity(
                VillageBuildingIdentity.CURRENT_SCHEMA_VERSION,
                id,
                entry,
                type,
                dimension,
                position);
    }
}
