package com.gamergaming.villagerguardgunners.worldgen.village;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyLayouts;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutySlotDefinition;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutySlotKind;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyWeaponRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Test;

final class VillageBuildingAnchorTest {
    private static final ResourceLocation DIMENSION = id("minecraft", "overworld");
    private static final ResourceLocation ENTRY = id("example", "plains/barracks");
    private static final BlockPos ANCHOR = new BlockPos(12, 70, -8);

    @Test
    void markerGrammarIsClosedAndTemplateAllowsAtMostOneMatchingAnchor() {
        StructureTemplate.StructureBlockInfo valid = marker("village_anchor:barracks", ANCHOR);
        StructureTemplate.StructureBlockInfo unrelated = marker("future_action:data", ANCHOR.above());

        assertAll(
                () -> assertEquals(
                        VillageBuildingType.BARRACKS,
                        VillageBuildingAnchorMarker.parse("village_anchor:barracks")
                                .orElseThrow().buildingType()),
                () -> assertTrue(VillageBuildingAnchorMarker.validateTemplateMarkers(
                        List.of(), VillageBuildingType.BARRACKS)),
                () -> assertTrue(VillageBuildingAnchorMarker.validateTemplateMarkers(
                        List.of(unrelated, valid), VillageBuildingType.BARRACKS)),
                () -> assertFalse(VillageBuildingAnchorMarker.validateTemplateMarkers(
                        List.of(valid), VillageBuildingType.ARMORY)),
                () -> assertFalse(VillageBuildingAnchorMarker.validateTemplateMarkers(
                        List.of(valid, marker("village_anchor:barracks", ANCHOR.east())),
                        VillageBuildingType.BARRACKS)),
                () -> assertFalse(VillageBuildingAnchorMarker.validateTemplateMarkers(
                        List.of(marker("village_anchor:", ANCHOR)),
                        VillageBuildingType.BARRACKS)),
                () -> assertFalse(VillageBuildingAnchorMarker.validateTemplateMarkers(
                        List.of(marker("village_anchor:BARRACKS", ANCHOR)),
                        VillageBuildingType.BARRACKS)),
                () -> assertTrue(VillageBuildingAnchorMarker.parse(
                        "village_anchor:" + "x".repeat(65)).isEmpty()));
    }

    @Test
    void deterministicIdentityChangesAcrossEveryOwnershipInput() {
        UUID baseline = VillageBuildingIds.buildingId(
                42L, DIMENSION, ENTRY, VillageBuildingType.BARRACKS, ANCHOR);

        assertAll(
                () -> assertEquals(
                        baseline,
                        VillageBuildingIds.buildingId(
                                42L,
                                DIMENSION,
                                ENTRY,
                                VillageBuildingType.BARRACKS,
                                ANCHOR)),
                () -> assertNotEquals(baseline, VillageBuildingIds.buildingId(
                        43L, DIMENSION, ENTRY, VillageBuildingType.BARRACKS, ANCHOR)),
                () -> assertNotEquals(baseline, VillageBuildingIds.buildingId(
                        42L, id("minecraft", "the_nether"), ENTRY,
                        VillageBuildingType.BARRACKS, ANCHOR)),
                () -> assertNotEquals(baseline, VillageBuildingIds.buildingId(
                        42L, DIMENSION, id("example", "other"),
                        VillageBuildingType.BARRACKS, ANCHOR)),
                () -> assertNotEquals(baseline, VillageBuildingIds.buildingId(
                        42L, DIMENSION, ENTRY, VillageBuildingType.ARMORY, ANCHOR)),
                () -> assertNotEquals(baseline, VillageBuildingIds.buildingId(
                        42L, DIMENSION, ENTRY, VillageBuildingType.BARRACKS, ANCHOR.east())));
    }

    @Test
    void identityAndRecordEnforceSchemaUuidAndActionBounds() {
        VillageBuildingIdentity identity = identity();
        VillageBuildingRecord record = VillageBuildingRecord.anchored(identity);

        assertAll(
                () -> assertTrue(record.hasClaimed(VillageBuildingAction.ANCHOR)),
                () -> assertFalse(record.hasClaimed(VillageBuildingAction.GARRISON)),
                () -> assertSame(record, record.claim(VillageBuildingAction.ANCHOR)),
                () -> assertTrue(record.claim(VillageBuildingAction.GARRISON)
                        .hasClaimed(VillageBuildingAction.GARRISON)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageBuildingIdentity(
                                2,
                                identity.buildingId(),
                                ENTRY,
                                VillageBuildingType.BARRACKS,
                                DIMENSION,
                                ANCHOR)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageBuildingIdentity(
                                1,
                                new UUID(0L, 0L),
                                ENTRY,
                                VillageBuildingType.BARRACKS,
                                DIMENSION,
                                ANCHOR)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageBuildingRecord(identity, 0)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageBuildingRecord(identity, 1 << 20)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new VillageBuildingRecord(
                                identity,
                                VillageBuildingAction.ANCHOR.mask()
                                        | VillageBuildingAction.INITIAL_STOCK.mask())));
    }

    @Test
    void strictRecordCodecRoundTripsAndPreservesUnreadableFutureData() {
        VillageBuildingRecord expected = VillageBuildingRecord.anchored(identity())
                .claim(VillageBuildingAction.GARRISON)
                .claim(VillageBuildingAction.ARMORY);
        CompoundTag owner = new CompoundTag();
        VillageBuildingRecordCodec.writeRecord(owner, expected);
        VillageBuildingRecord decoded = VillageBuildingRecordCodec.readRecord(owner);

        CompoundTag futureOwner = owner.copy();
        futureOwner.getCompound(VillageBuildingRecordCodec.BUILDING_TAG)
                .putInt("Version", VillageBuildingIdentity.CURRENT_SCHEMA_VERSION + 1);
        var opaque = VillageBuildingRecordCodec.copyEncodedRecord(futureOwner);
        CompoundTag resaved = new CompoundTag();
        VillageBuildingRecordCodec.writeRecordPreservingUnreadable(resaved, null, opaque);

        assertAll(
                () -> assertEquals(expected, decoded),
                () -> assertNull(VillageBuildingRecordCodec.readRecord(futureOwner)),
                () -> assertNotNull(opaque),
                () -> assertEquals(
                        futureOwner.get(VillageBuildingRecordCodec.BUILDING_TAG),
                        resaved.get(VillageBuildingRecordCodec.BUILDING_TAG)),
                () -> {
                    CompoundTag unknown = owner.copy();
                    unknown.getCompound(VillageBuildingRecordCodec.BUILDING_TAG)
                            .putString("Unexpected", "value");
                    assertNull(VillageBuildingRecordCodec.readRecord(unknown));
                },
                () -> {
                    CompoundTag badActions = owner.copy();
                    badActions.getCompound(VillageBuildingRecordCodec.BUILDING_TAG)
                            .putInt("ClaimedActions", 1 << 20);
                    assertNull(VillageBuildingRecordCodec.readRecord(badActions));
                });
    }

    @Test
    void pendingActivationRoundTripsIndexesOnlyRelevantChunksAndClearsOnClaim() {
        VillageBuildingIdentity borderIdentity = VillageBuildingIdentity.anchored(
                42L,
                DIMENSION,
                id("example", "border_barracks"),
                VillageBuildingType.BARRACKS,
                new BlockPos(15, 70, 15));
        VillageBuildingPendingActivation pending =
                VillageBuildingPendingActivation.forBuilding(
                        borderIdentity,
                        Rotation.CLOCKWISE_180,
                        NaturalGuardDifficulty.HARD);
        VillageBuildingSavedData source = new VillageBuildingSavedData();
        assertEquals(
                VillageBuildingSavedData.ClaimResult.CLAIMED,
                source.claimAnchor(borderIdentity, pending));
        CompoundTag encoded = source.save(new CompoundTag());
        VillageBuildingSavedData loaded = VillageBuildingSavedData.loadForLevel(
                encoded, DIMENSION, 42L);
        VillageBuildingRecord loadedRecord = loaded.get(borderIdentity.buildingId());

        assertAll(
                () -> assertEquals(2, encoded.getInt("Version")),
                () -> assertNotNull(loadedRecord),
                () -> assertEquals(java.util.Optional.of(pending),
                        loadedRecord.pendingActivation()),
                () -> assertEquals(1, loaded.pendingActivationCount()),
                () -> assertTrue(loaded.pendingActivationsIn(new ChunkPos(0, 0))
                        .contains(loadedRecord)),
                () -> assertTrue(loaded.pendingActivationsIn(new ChunkPos(1, 0))
                        .contains(loadedRecord)),
                () -> assertTrue(loaded.pendingActivationsIn(new ChunkPos(0, 1))
                        .contains(loadedRecord)),
                () -> assertTrue(loaded.pendingActivationsIn(new ChunkPos(1, 1))
                        .isEmpty()));

        assertEquals(
                VillageBuildingSavedData.ClaimResult.CLAIMED,
                loaded.claimAction(
                        borderIdentity.buildingId(), VillageBuildingAction.GARRISON));
        assertAll(
                () -> assertEquals(0, loaded.pendingActivationCount()),
                () -> assertTrue(loaded.pendingActivationsIn(new ChunkPos(0, 0)).isEmpty()),
                () -> assertTrue(loaded.get(borderIdentity.buildingId())
                        .pendingActivation().isEmpty()));
    }

    @Test
    void pendingActivationPersistsExactPackAuthoredLayoutAndMigratesLegacyContext() {
        VillageBuildingIdentity identity = VillageBuildingIdentity.anchored(
                42L,
                DIMENSION,
                id("example", "custom_sentry"),
                VillageBuildingType.SENTRY_POST,
                ANCHOR);
        var customLayout = VillageDefenseDutyLayouts.forBuilding(
                identity,
                Rotation.CLOCKWISE_90,
                List.of(new VillageDefenseDutySlotDefinition(
                        "roof_overwatch",
                        new BlockPos(3, 5, -2),
                        Direction.NORTH,
                        VillageDefenseDutySlotKind.OVERWATCH,
                        VillageDefenseDutyWeaponRequirement.RANGED)));
        VillageBuildingPendingActivation pending =
                VillageBuildingPendingActivation.forBuilding(
                        identity,
                        Rotation.CLOCKWISE_90,
                        NaturalGuardDifficulty.NORMAL,
                        customLayout);
        CompoundTag owner = VillageBuildingRecordCodec.encodeWrapped(
                VillageBuildingRecord.anchored(identity, pending));
        VillageBuildingRecord decoded = VillageBuildingRecordCodec.decodeWrapped(owner);

        CompoundTag legacyOwner = owner.copy();
        CompoundTag legacyPending = legacyOwner
                .getCompound(VillageBuildingRecordCodec.BUILDING_TAG)
                .getCompound("PendingActivation");
        legacyPending.putInt("Version", 1);
        legacyPending.remove("DutyLayout");
        VillageBuildingRecord migrated = VillageBuildingRecordCodec.decodeWrapped(legacyOwner);

        assertAll(
                () -> assertNotNull(decoded),
                () -> assertEquals(customLayout,
                        decoded.pendingActivation().orElseThrow().dutyLayout()),
                () -> assertNotNull(migrated),
                () -> assertEquals(
                        VillageDefenseDutyLayouts.forBuilding(
                                identity, Rotation.CLOCKWISE_90),
                        migrated.pendingActivation().orElseThrow().dutyLayout()));
    }

    @Test
    void legacySavedDataLoadsWithoutInventingActivationContext() {
        VillageBuildingSavedData source = new VillageBuildingSavedData();
        VillageBuildingIdentity identity = identity();
        source.claimAnchor(identity);
        CompoundTag legacy = source.save(new CompoundTag());
        legacy.putInt("Version", VillageBuildingSavedData.LEGACY_DATA_VERSION);

        VillageBuildingSavedData migrated = VillageBuildingSavedData.loadForLevel(
                legacy, DIMENSION, 42L);

        assertAll(
                () -> assertTrue(migrated.enabled()),
                () -> assertEquals(0, migrated.pendingActivationCount()),
                () -> assertTrue(migrated.get(identity.buildingId())
                        .pendingActivation().isEmpty()),
                () -> assertEquals(
                        VillageBuildingSavedData.DATA_VERSION,
                        migrated.save(new CompoundTag()).getInt("Version")));
    }

    @Test
    void savedDataClaimsAnchorsAndActionsAtMostOnce() {
        VillageBuildingSavedData data = new VillageBuildingSavedData();
        VillageBuildingIdentity identity = identity();

        assertAll(
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.CLAIMED,
                        data.claimAnchor(identity)),
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.ALREADY_CLAIMED,
                        data.claimAnchor(identity)),
                () -> assertEquals(1, data.size()),
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.CLAIMED,
                        data.claimAction(identity.buildingId(), VillageBuildingAction.GARRISON)),
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.ALREADY_CLAIMED,
                        data.claimAction(identity.buildingId(), VillageBuildingAction.GARRISON)),
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.MISSING_RECORD,
                        data.claimAction(UUID.randomUUID(), VillageBuildingAction.ARMORY)),
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.PREREQUISITE_MISSING,
                        data.claimAction(
                                identity.buildingId(),
                                VillageBuildingAction.INITIAL_STOCK)),
                () -> assertTrue(data.get(identity.buildingId())
                        .hasClaimed(VillageBuildingAction.GARRISON)),
                () -> assertEquals(
                        data.get(identity.buildingId()),
                        data.getAt(DIMENSION, ANCHOR)));
    }

    @Test
    void armorySetupClaimsPlacementAndStockAtomically() {
        VillageBuildingSavedData data = new VillageBuildingSavedData();
        VillageBuildingIdentity identity = VillageBuildingIdentity.anchored(
                42L,
                DIMENSION,
                id("example", "plains/armory"),
                VillageBuildingType.ARMORY,
                ANCHOR);
        data.claimAnchor(identity);

        assertAll(
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.CLAIMED,
                        data.claimArmorySetup(identity.buildingId())),
                () -> assertTrue(data.get(identity.buildingId())
                        .hasClaimed(VillageBuildingAction.ARMORY)),
                () -> assertTrue(data.get(identity.buildingId())
                        .hasClaimed(VillageBuildingAction.INITIAL_STOCK)),
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.ALREADY_CLAIMED,
                        data.claimArmorySetup(identity.buildingId())),
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.MISSING_RECORD,
                        data.claimArmorySetup(UUID.randomUUID())));

        VillageBuildingSavedData wrongType = new VillageBuildingSavedData();
        VillageBuildingIdentity barracks = identity();
        wrongType.claimAnchor(barracks);
        assertEquals(
                VillageBuildingSavedData.ClaimResult.CONFLICT,
                wrongType.claimArmorySetup(barracks.buildingId()));
    }

    @Test
    void activationRetriesUseBoundedExponentialBackoff() {
        assertAll(
                () -> assertEquals(1, VillageBuildingAnchorLifecycle.retryDelayTicks(0)),
                () -> assertEquals(2, VillageBuildingAnchorLifecycle.retryDelayTicks(1)),
                () -> assertEquals(4, VillageBuildingAnchorLifecycle.retryDelayTicks(2)),
                () -> assertEquals(32, VillageBuildingAnchorLifecycle.retryDelayTicks(5)),
                () -> assertEquals(40, VillageBuildingAnchorLifecycle.retryDelayTicks(6)),
                () -> assertEquals(40, VillageBuildingAnchorLifecycle.retryDelayTicks(100)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> VillageBuildingAnchorLifecycle.retryDelayTicks(-1)));
    }

    @Test
    void savedDataRejectsIdAndPhysicalAnchorConflicts() {
        VillageBuildingSavedData data = new VillageBuildingSavedData();
        VillageBuildingIdentity original = identity();
        assertEquals(VillageBuildingSavedData.ClaimResult.CLAIMED,
                data.claimAnchor(original));

        VillageBuildingIdentity sameIdDifferentPosition = new VillageBuildingIdentity(
                1,
                original.buildingId(),
                original.entryId(),
                original.buildingType(),
                original.dimensionId(),
                ANCHOR.east());
        VillageBuildingIdentity samePositionDifferentId = VillageBuildingIdentity.anchored(
                99L,
                DIMENSION,
                id("example", "changed_entry"),
                VillageBuildingType.ARMORY,
                ANCHOR);

        assertAll(
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.CONFLICT,
                        data.claimAnchor(sameIdDifferentPosition)),
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.CONFLICT,
                        data.claimAnchor(samePositionDifferentId)),
                () -> assertEquals(1, data.size()));
    }

    @Test
    void savedDataRoundTripsAndMalformedOrFutureRootsStayDisabledAndOpaque() {
        VillageBuildingSavedData source = new VillageBuildingSavedData();
        VillageBuildingIdentity identity = identity();
        source.claimAnchor(identity);
        source.claimAction(identity.buildingId(), VillageBuildingAction.ARMORY);
        source.claimAction(identity.buildingId(), VillageBuildingAction.INITIAL_STOCK);
        CompoundTag encoded = source.save(new CompoundTag());

        VillageBuildingSavedData loaded = VillageBuildingSavedData.load(encoded);
        VillageBuildingSavedData levelValidated = VillageBuildingSavedData.loadForLevel(
                encoded, DIMENSION, 42L);
        VillageBuildingSavedData wrongDimension = VillageBuildingSavedData.loadForLevel(
                encoded, id("minecraft", "the_nether"), 42L);
        VillageBuildingSavedData wrongSeed = VillageBuildingSavedData.loadForLevel(
                encoded, DIMENSION, 43L);
        CompoundTag future = encoded.copy();
        future.putInt("Version", VillageBuildingSavedData.DATA_VERSION + 1);
        VillageBuildingSavedData disabledFuture = VillageBuildingSavedData.load(future);
        CompoundTag wrongListType = encoded.copy();
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("not a record"));
        wrongListType.put("Records", strings);
        VillageBuildingSavedData disabledWrongList =
                VillageBuildingSavedData.load(wrongListType);

        CompoundTag duplicated = encoded.copy();
        ListTag records = duplicated.getList("Records", Tag.TAG_COMPOUND);
        records.add(records.get(0).copy());
        VillageBuildingSavedData disabledDuplicate =
                VillageBuildingSavedData.load(duplicated);

        assertAll(
                () -> assertTrue(loaded.enabled()),
                () -> assertTrue(levelValidated.enabled()),
                () -> assertFalse(wrongDimension.enabled()),
                () -> assertFalse(wrongSeed.enabled()),
                () -> assertEquals(source.get(identity.buildingId()),
                        loaded.get(identity.buildingId())),
                () -> assertFalse(disabledFuture.enabled()),
                () -> assertEquals(future, disabledFuture.save(new CompoundTag())),
                () -> assertEquals(
                        VillageBuildingSavedData.ClaimResult.DISABLED,
                        disabledFuture.claimAnchor(identity)),
                () -> assertFalse(disabledWrongList.enabled()),
                () -> assertEquals(wrongListType,
                        disabledWrongList.save(new CompoundTag())),
                () -> assertFalse(disabledDuplicate.enabled()),
                () -> assertEquals(duplicated,
                        disabledDuplicate.save(new CompoundTag())));
    }

    private static VillageBuildingIdentity identity() {
        return VillageBuildingIdentity.anchored(
                42L,
                DIMENSION,
                ENTRY,
                VillageBuildingType.BARRACKS,
                ANCHOR);
    }

    private static StructureTemplate.StructureBlockInfo marker(
            String metadata,
            BlockPos position) {
        CompoundTag tag = new CompoundTag();
        tag.putString("metadata", metadata);
        return new StructureTemplate.StructureBlockInfo(
                position,
                null,
                tag);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
