package com.gamergaming.villagerguardgunners.defense.network;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseSignal;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseReviewFacts;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseState;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseTimings;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingType;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VillageDefenseNetworkSavedDataTest {
    private static final long WORLD_SEED = 42L;
    private static final ResourceLocation DIMENSION = id("minecraft", "overworld");
    private static final VillageDefenseTimings TIMINGS =
            new VillageDefenseTimings(10L, 20L, 5L);

    @Test
    void nearbyBuildingsShareANetworkWhileDistantBuildingsRemainSeparate() {
        VillageDefenseNetworkSavedData data =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        VillageBuildingIdentity first = building(0, "first");
        VillageBuildingIdentity nearby = building(64, "nearby");
        VillageBuildingIdentity far = building(400, "far");

        var firstResult = data.claimBuilding(first, 96.0D);
        var nearbyResult = data.claimBuilding(nearby, 96.0D);
        var farResult = data.claimBuilding(far, 96.0D);

        assertAll(
                () -> assertEquals(
                        VillageDefenseNetworkSavedData.ClaimStatus.CLAIMED,
                        firstResult.status()),
                () -> assertEquals(firstResult.networkId(), nearbyResult.networkId()),
                () -> assertFalse(firstResult.networkId().equals(farResult.networkId())),
                () -> assertEquals(2, data.networkCount()),
                () -> assertEquals(3, data.buildingCount()),
                () -> assertEquals(2,
                        data.networkForBuilding(first.buildingId()).buildings().size()),
                () -> assertEquals(
                        firstResult.networkId(),
                        data.findNearest(new BlockPos(20, 64, 0), 96.0D).networkId()),
                () -> assertNull(data.findNearest(new BlockPos(200, 64, 0), 32.0D)));
    }

    @Test
    void bridgeBuildingMergesNetworksAndPreservesTheRetiredAlias() {
        VillageDefenseNetworkSavedData data =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        VillageBuildingIdentity left = building(0, "left");
        VillageBuildingIdentity right = building(200, "right");
        VillageBuildingIdentity bridge = building(100, "bridge");
        UUID leftNetwork = data.claimBuilding(left, 100.0D).networkId();
        UUID rightNetwork = data.claimBuilding(right, 100.0D).networkId();
        UUID expectedCanonical = leftNetwork.compareTo(rightNetwork) <= 0
                ? leftNetwork
                : rightNetwork;
        UUID retired = expectedCanonical.equals(leftNetwork)
                ? rightNetwork
                : leftNetwork;

        var merge = data.claimBuilding(bridge, 100.0D);

        assertAll(
                () -> assertEquals(
                        VillageDefenseNetworkSavedData.ClaimStatus.MERGED,
                        merge.status()),
                () -> assertEquals(1, merge.mergedNetworks()),
                () -> assertEquals(expectedCanonical, merge.networkId()),
                () -> assertEquals(expectedCanonical, data.resolveNetworkId(retired)),
                () -> assertEquals(1, data.networkCount()),
                () -> assertEquals(1, data.aliasCount()),
                () -> assertEquals(3, data.getNetwork(retired).buildings().size()));
    }

    @Test
    void laterMergesFlattenEveryEarlierAliasToTheActiveNetwork() {
        VillageDefenseNetworkSavedData data =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        UUID first = data.claimBuilding(building(0, "first"), 100.0D).networkId();
        UUID second = data.claimBuilding(building(200, "second"), 100.0D).networkId();
        data.claimBuilding(building(100, "first_bridge"), 100.0D);
        UUID third = data.claimBuilding(building(400, "third"), 100.0D).networkId();
        data.claimBuilding(building(300, "second_bridge"), 100.0D);

        UUID active = data.resolveNetworkId(first);
        CompoundTag encoded = data.save(new CompoundTag());
        VillageDefenseNetworkSavedData restored =
                VillageDefenseNetworkSavedData.loadForLevel(
                        encoded, DIMENSION, WORLD_SEED);

        assertAll(
                () -> assertEquals(1, data.networkCount()),
                () -> assertEquals(5, data.buildingCount()),
                () -> assertEquals(active, data.resolveNetworkId(second)),
                () -> assertEquals(active, data.resolveNetworkId(third)),
                () -> assertEquals(active, restored.resolveNetworkId(first)),
                () -> assertEquals(active, restored.resolveNetworkId(second)),
                () -> assertEquals(active, restored.resolveNetworkId(third)),
                () -> assertEquals(5, restored.getNetwork(active).buildings().size()));
    }

    @Test
    void claimsAreIdempotentAndConflictsOrInvalidInputsFailClosed() {
        VillageDefenseNetworkSavedData data =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        VillageBuildingIdentity original = building(0, "original");
        data.claimBuilding(original, 96.0D);
        VillageBuildingIdentity conflictingId = new VillageBuildingIdentity(
                original.schemaVersion(),
                original.buildingId(),
                original.entryId(),
                original.buildingType(),
                original.dimensionId(),
                original.anchorPosition().east());
        VillageBuildingIdentity wrongDimension = VillageBuildingIdentity.anchored(
                WORLD_SEED,
                id("minecraft", "the_nether"),
                id("example", "nether"),
                VillageBuildingType.SENTRY_POST,
                BlockPos.ZERO);
        VillageBuildingIdentity nonDeterministic = new VillageBuildingIdentity(
                original.schemaVersion(),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                id("example", "forged"),
                VillageBuildingType.SENTRY_POST,
                DIMENSION,
                new BlockPos(80, 64, 0));

        assertAll(
                () -> assertEquals(
                        VillageDefenseNetworkSavedData.ClaimStatus.ALREADY_CLAIMED,
                        data.claimBuilding(original, 96.0D).status()),
                () -> assertEquals(
                        VillageDefenseNetworkSavedData.ClaimStatus.CONFLICT,
                        data.claimBuilding(conflictingId, 96.0D).status()),
                () -> assertEquals(
                        VillageDefenseNetworkSavedData.ClaimStatus.WRONG_DIMENSION,
                        data.claimBuilding(wrongDimension, 96.0D).status()),
                () -> assertEquals(
                        VillageDefenseNetworkSavedData.ClaimStatus.NON_DETERMINISTIC_BUILDING,
                        data.claimBuilding(nonDeterministic, 96.0D).status()),
                () -> assertEquals(
                        VillageDefenseNetworkSavedData.ClaimStatus.INVALID_RADIUS,
                        data.claimBuilding(building(40, "invalid"), Double.NaN).status()),
                () -> assertNull(data.findNearest(BlockPos.ZERO, Double.POSITIVE_INFINITY)),
                () -> assertEquals(1, data.buildingCount()));
    }

    @Test
    void roundTripRetainsNetworksAliasesAndCleanStrictData() {
        VillageDefenseNetworkSavedData source =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        VillageBuildingIdentity left = building(0, "left");
        VillageBuildingIdentity right = building(200, "right");
        UUID leftNetwork = source.claimBuilding(left, 100.0D).networkId();
        UUID rightNetwork = source.claimBuilding(right, 100.0D).networkId();
        source.claimBuilding(building(100, "bridge"), 100.0D);
        UUID retired = source.resolveNetworkId(leftNetwork).equals(leftNetwork)
                ? rightNetwork
                : leftNetwork;
        CompoundTag encoded = source.save(new CompoundTag());

        VillageDefenseNetworkSavedData restored =
                VillageDefenseNetworkSavedData.loadForLevel(
                        encoded, DIMENSION, WORLD_SEED);

        assertAll(
                () -> assertTrue(restored.enabled()),
                () -> assertEquals(1, restored.networkCount()),
                () -> assertEquals(3, restored.buildingCount()),
                () -> assertEquals(1, restored.aliasCount()),
                () -> assertNotNull(restored.getNetwork(retired)),
                () -> assertFalse(restored.isDirty()),
                () -> assertEquals(encoded, restored.save(new CompoundTag())));
    }

    @Test
    void malformedFutureWrongDimensionAndWrongSeedRootsRemainOpaque() {
        VillageDefenseNetworkSavedData source =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        source.claimBuilding(building(0, "first"), 96.0D);
        CompoundTag encoded = source.save(new CompoundTag());
        CompoundTag future = encoded.copy();
        future.putInt("Version", VillageDefenseNetworkSavedData.DATA_VERSION + 1);
        CompoundTag wrongList = encoded.copy();
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("not a network"));
        wrongList.put("Networks", strings);

        VillageDefenseNetworkSavedData disabledFuture =
                VillageDefenseNetworkSavedData.load(future);
        VillageDefenseNetworkSavedData disabledList =
                VillageDefenseNetworkSavedData.load(wrongList);
        VillageDefenseNetworkSavedData wrongDimension =
                VillageDefenseNetworkSavedData.loadForLevel(
                        encoded, id("minecraft", "the_nether"), WORLD_SEED);
        VillageDefenseNetworkSavedData wrongSeed =
                VillageDefenseNetworkSavedData.loadForLevel(
                        encoded, DIMENSION, WORLD_SEED + 1L);

        assertAll(
                () -> assertFalse(disabledFuture.enabled()),
                () -> assertEquals(future, disabledFuture.save(new CompoundTag())),
                () -> assertFalse(disabledList.enabled()),
                () -> assertEquals(wrongList, disabledList.save(new CompoundTag())),
                () -> assertFalse(wrongDimension.enabled()),
                () -> assertFalse(wrongSeed.enabled()),
                () -> assertEquals(
                        VillageDefenseNetworkSavedData.ClaimStatus.DISABLED,
                        disabledFuture.claimBuilding(building(40, "blocked")).status()));
    }

    @Test
    void malformedNestedDefenseStatusDisablesAndPreservesTheWholeRoot() {
        VillageDefenseNetworkSavedData source =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        source.claimBuilding(building(0, "first"), 96.0D);
        CompoundTag malformed = source.save(new CompoundTag());
        CompoundTag network = malformed.getList(
                "Networks", Tag.TAG_COMPOUND).getCompound(0);
        network.getCompound("DefenseStatus").putString("State", "future_state");

        VillageDefenseNetworkSavedData disabled =
                VillageDefenseNetworkSavedData.loadForLevel(
                        malformed, DIMENSION, WORLD_SEED);

        assertAll(
                () -> assertFalse(disabled.enabled()),
                () -> assertEquals(malformed, disabled.save(new CompoundTag())),
                () -> assertEquals(
                        VillageDefenseNetworkSavedData.StateUpdateStatus.DISABLED,
                        disabled.applySignal(
                                UUID.randomUUID(),
                                VillageDefenseSignal.BELL_RUNG,
                                100L,
                                TIMINGS).status()));
    }

    @Test
    void strictCodecRejectsDuplicateBuildingMembershipAcrossNetworks() {
        VillageDefenseNetworkSavedData source =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        source.claimBuilding(building(0, "first"), 96.0D);
        source.claimBuilding(building(400, "second"), 96.0D);
        CompoundTag duplicated = source.save(new CompoundTag());
        ListTag networks = duplicated.getList("Networks", Tag.TAG_COMPOUND);
        CompoundTag firstBuilding = networks.getCompound(0)
                .getList("Buildings", Tag.TAG_COMPOUND)
                .getCompound(0)
                .copy();
        networks.getCompound(1)
                .getList("Buildings", Tag.TAG_COMPOUND)
                .add(firstBuilding);

        VillageDefenseNetworkSavedData disabled =
                VillageDefenseNetworkSavedData.load(duplicated);

        assertFalse(disabled.enabled());
        assertEquals(duplicated, disabled.save(new CompoundTag()));
    }

    @Test
    void strictCodecRejectsAnAliasOwnedByAnotherNetwork() {
        VillageDefenseNetworkSavedData source =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        VillageBuildingIdentity canonicalMember = building(0, "canonical_member");
        VillageBuildingIdentity nonCanonicalMember = building(40, "noncanonical_member");
        source.claimBuilding(canonicalMember, 96.0D);
        source.claimBuilding(nonCanonicalMember, 96.0D);
        UUID otherNetwork = source.claimBuilding(
                building(400, "other_network"), 96.0D).networkId();
        UUID forgedAlias = VillageDefenseNetworkIds.forBuilding(
                nonCanonicalMember.buildingId());
        CompoundTag malformed = source.save(new CompoundTag());
        CompoundTag alias = new CompoundTag();
        alias.putUUID("From", forgedAlias);
        alias.putUUID("To", otherNetwork);
        malformed.getList("Aliases", Tag.TAG_COMPOUND).add(alias);

        VillageDefenseNetworkSavedData disabled =
                VillageDefenseNetworkSavedData.loadForLevel(
                        malformed, DIMENSION, WORLD_SEED);

        assertFalse(disabled.enabled());
        assertEquals(malformed, disabled.save(new CompoundTag()));
    }

    @Test
    void existingBuildingRegistryReconciliationIsDeterministicAndRunsOnce() {
        VillageDefenseNetworkSavedData data =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        VillageBuildingIdentity first = building(0, "first");
        VillageBuildingIdentity nearby = building(64, "nearby");

        int[] snapshots = {0};
        data.reconcileBuildingRegistry(() -> {
            snapshots[0]++;
            return List.of(nearby, first);
        });
        for (int call = 0; call < 1_000; call++) {
            data.reconcileBuildingRegistry(() -> {
                snapshots[0]++;
                return List.of(building(400, "ignored_after_first_pass"));
            });
        }
        assertEquals(1, snapshots[0], "Completed reconciliation must not request another snapshot");

        assertAll(
                () -> assertEquals(1, data.networkCount()),
                () -> assertEquals(2, data.buildingCount()),
                () -> assertEquals(
                        data.networkForBuilding(first.buildingId()).networkId(),
                        data.networkForBuilding(nearby.buildingId()).networkId()),
                () -> assertNull(data.networkForBuilding(
                        building(400, "ignored_after_first_pass").buildingId())));
    }

    @Test
    void stateUpdatesResolveAliasesAndPersistTheirDeadline() {
        VillageDefenseNetworkSavedData source =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        UUID left = source.claimBuilding(building(0, "left"), 100.0D).networkId();
        UUID right = source.claimBuilding(building(200, "right"), 100.0D).networkId();
        source.claimBuilding(building(100, "bridge"), 100.0D);
        UUID retired = source.resolveNetworkId(left).equals(left) ? right : left;

        var update = source.applySignal(
                retired,
                VillageDefenseSignal.HOSTILE_CONFIRMED,
                100L,
                TIMINGS);
        CompoundTag encoded = source.save(new CompoundTag());
        VillageDefenseNetworkSavedData restored =
                VillageDefenseNetworkSavedData.loadForLevel(
                        encoded, DIMENSION, WORLD_SEED);

        assertAll(
                () -> assertEquals(
                        VillageDefenseNetworkSavedData.StateUpdateStatus.APPLIED,
                        update.status()),
                () -> assertEquals(VillageDefenseState.ALERT,
                        restored.getNetwork(retired).defenseStatus().state()),
                () -> assertEquals(120L, restored.nextReviewGameTime()),
                () -> assertFalse(restored.isDirty()));
    }

    @Test
    void dueReviewsAreDeadlineIndexedAndHardBounded() {
        VillageDefenseNetworkSavedData data =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        UUID first = data.claimBuilding(building(0, "first"), 32.0D).networkId();
        UUID second = data.claimBuilding(building(100, "second"), 32.0D).networkId();
        UUID third = data.claimBuilding(building(200, "third"), 32.0D).networkId();
        data.applySignal(first, VillageDefenseSignal.BELL_RUNG, 100L, TIMINGS);
        data.applySignal(second, VillageDefenseSignal.BELL_RUNG, 100L, TIMINGS);
        data.applySignal(third, VillageDefenseSignal.BELL_RUNG, 100L, TIMINGS);

        var firstBatch = data.reviewDue(110L, TIMINGS, 2);
        var secondBatch = data.reviewDue(110L, TIMINGS, 2);

        assertAll(
                () -> assertEquals(2, firstBatch.reviewedNetworks()),
                () -> assertEquals(2, firstBatch.changedNetworks()),
                () -> assertTrue(firstBatch.hasMoreDue()),
                () -> assertEquals(110L, firstBatch.nextReviewGameTime()),
                () -> assertEquals(1, secondBatch.reviewedNetworks()),
                () -> assertEquals(1, secondBatch.changedNetworks()),
                () -> assertFalse(secondBatch.hasMoreDue()),
                () -> assertEquals(Long.MAX_VALUE,
                        secondBatch.nextReviewGameTime()),
                () -> assertTrue(data.snapshot().stream().allMatch(
                        network -> network.defenseStatus().state()
                                == VillageDefenseState.PEACE)));
    }

    @Test
    void dueReviewFactsCanMaintainAlertOrEscalateToSiege() {
        VillageDefenseNetworkSavedData data =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        UUID first = data.claimBuilding(building(0, "first"), 32.0D).networkId();
        UUID second = data.claimBuilding(building(100, "second"), 32.0D).networkId();
        data.applySignal(first, VillageDefenseSignal.HOSTILE_CONFIRMED, 100L, TIMINGS);
        data.applySignal(second, VillageDefenseSignal.BELL_RUNG, 110L, TIMINGS);

        var batch = data.reviewDue(
                120L,
                TIMINGS,
                2,
                (networkId, status) -> networkId.equals(first)
                        ? new VillageDefenseReviewFacts(false, true)
                        : new VillageDefenseReviewFacts(true, false));

        assertAll(
                () -> assertEquals(2, batch.reviewedNetworks()),
                () -> assertEquals(VillageDefenseState.ALERT,
                        data.getNetwork(first).defenseStatus().state()),
                () -> assertEquals(140L,
                        data.getNetwork(first).defenseStatus().nextReviewGameTime()),
                () -> assertEquals(VillageDefenseState.SIEGE,
                        data.getNetwork(second).defenseStatus().state()),
                () -> assertEquals(Long.MAX_VALUE,
                        data.getNetwork(second).defenseStatus().nextReviewGameTime()));
    }

    @Test
    void mergingNetworksPreservesTheStrongestActiveDefenseState() {
        VillageDefenseNetworkSavedData data =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        UUID left = data.claimBuilding(building(0, "left"), 100.0D).networkId();
        UUID right = data.claimBuilding(building(200, "right"), 100.0D).networkId();
        data.applySignal(left, VillageDefenseSignal.HOSTILE_CONFIRMED, 100L, TIMINGS);
        data.applySignal(right, VillageDefenseSignal.BELL_RUNG, 100L, TIMINGS);

        UUID merged = data.claimBuilding(
                building(100, "bridge"), 100.0D).networkId();

        assertAll(
                () -> assertEquals(VillageDefenseState.ALERT,
                        data.getNetwork(merged).defenseStatus().state()),
                () -> assertEquals(120L, data.nextReviewGameTime()),
                () -> assertEquals(1, data.networkCount()));
    }

    @Test
    void legacyNetworkPayloadMigratesToPeaceAndRewritesCurrentSchema() {
        VillageDefenseNetworkSavedData source =
                new VillageDefenseNetworkSavedData(DIMENSION, WORLD_SEED);
        UUID networkId = source.claimBuilding(
                building(0, "legacy"), 96.0D).networkId();
        CompoundTag legacy = source.save(new CompoundTag());
        CompoundTag legacyNetwork = legacy.getList(
                "Networks", Tag.TAG_COMPOUND).getCompound(0);
        legacyNetwork.putInt("Version", VillageDefenseNetwork.LEGACY_SCHEMA_VERSION);
        legacyNetwork.remove("DefenseStatus");

        VillageDefenseNetworkSavedData migrated =
                VillageDefenseNetworkSavedData.loadForLevel(
                        legacy, DIMENSION, WORLD_SEED);
        CompoundTag rewritten = migrated.save(new CompoundTag());
        CompoundTag rewrittenNetwork = rewritten.getList(
                "Networks", Tag.TAG_COMPOUND).getCompound(0);

        assertAll(
                () -> assertTrue(migrated.enabled()),
                () -> assertTrue(migrated.isDirty()),
                () -> assertEquals(VillageDefenseState.PEACE,
                        migrated.getNetwork(networkId).defenseStatus().state()),
                () -> assertEquals(VillageDefenseNetwork.CURRENT_SCHEMA_VERSION,
                        rewrittenNetwork.getInt("Version")),
                () -> assertTrue(rewrittenNetwork.contains(
                        "DefenseStatus", Tag.TAG_COMPOUND)));
    }

    private static VillageBuildingIdentity building(int x, String name) {
        return VillageBuildingIdentity.anchored(
                WORLD_SEED,
                DIMENSION,
                id("example", name),
                VillageBuildingType.SENTRY_POST,
                new BlockPos(x, 64, 0));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
