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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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
