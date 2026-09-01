package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict schema-1 codec reusable by SavedData and future generated objects. */
public final class VillageBuildingRecordCodec {
    public static final String BUILDING_TAG = "VggVillageBuilding";

    private static final String VERSION_TAG = "Version";
    private static final String BUILDING_ID_TAG = "BuildingId";
    private static final String ENTRY_ID_TAG = "EntryId";
    private static final String BUILDING_TYPE_TAG = "BuildingType";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String ANCHOR_TAG = "Anchor";
    private static final String CLAIMED_ACTIONS_TAG = "ClaimedActions";
    private static final Set<String> RECORD_KEYS = Set.of(
            VERSION_TAG,
            BUILDING_ID_TAG,
            ENTRY_ID_TAG,
            BUILDING_TYPE_TAG,
            DIMENSION_TAG,
            ANCHOR_TAG,
            CLAIMED_ACTIONS_TAG);

    public static void writeRecord(
            CompoundTag owner,
            @Nullable VillageBuildingRecord record) {
        if (record == null) {
            owner.remove(BUILDING_TAG);
            return;
        }
        owner.put(BUILDING_TAG, encodePayload(record));
    }

    public static @Nullable VillageBuildingRecord readRecord(@Nullable CompoundTag owner) {
        if (owner == null || !owner.contains(BUILDING_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        return decodePayload(owner.getCompound(BUILDING_TAG));
    }

    /** Copies an encoded record without interpreting or normalizing it. */
    public static @Nullable Tag copyEncodedRecord(@Nullable CompoundTag owner) {
        if (owner == null) {
            return null;
        }
        Tag encoded = owner.get(BUILDING_TAG);
        return encoded == null ? null : encoded.copy();
    }

    /** Writes recognized state or preserves an unreadable/future record exactly. */
    public static void writeRecordPreservingUnreadable(
            CompoundTag owner,
            @Nullable VillageBuildingRecord record,
            @Nullable Tag unreadableRecord) {
        if (record != null || unreadableRecord == null) {
            writeRecord(owner, record);
            return;
        }
        owner.put(BUILDING_TAG, unreadableRecord.copy());
    }

    static CompoundTag encodeWrapped(VillageBuildingRecord record) {
        CompoundTag wrapper = new CompoundTag();
        writeRecord(wrapper, record);
        return wrapper;
    }

    static @Nullable VillageBuildingRecord decodeWrapped(CompoundTag wrapper) {
        if (!wrapper.getAllKeys().equals(Set.of(BUILDING_TAG))) {
            return null;
        }
        return readRecord(wrapper);
    }

    private static CompoundTag encodePayload(VillageBuildingRecord record) {
        VillageBuildingIdentity identity = record.identity();
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, identity.schemaVersion());
        encoded.putUUID(BUILDING_ID_TAG, identity.buildingId());
        encoded.putString(ENTRY_ID_TAG, identity.entryId().toString());
        encoded.putString(BUILDING_TYPE_TAG, identity.buildingType().serializedName());
        encoded.putString(DIMENSION_TAG, identity.dimensionId().toString());
        encoded.putLong(ANCHOR_TAG, identity.anchorPosition().asLong());
        encoded.putInt(CLAIMED_ACTIONS_TAG, record.claimedActions());
        return encoded;
    }

    private static @Nullable VillageBuildingRecord decodePayload(CompoundTag encoded) {
        if (!encoded.getAllKeys().equals(RECORD_KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != VillageBuildingIdentity.CURRENT_SCHEMA_VERSION
                || !encoded.hasUUID(BUILDING_ID_TAG)
                || !encoded.contains(ENTRY_ID_TAG, Tag.TAG_STRING)
                || !encoded.contains(BUILDING_TYPE_TAG, Tag.TAG_STRING)
                || !encoded.contains(DIMENSION_TAG, Tag.TAG_STRING)
                || !encoded.contains(ANCHOR_TAG, Tag.TAG_LONG)
                || !encoded.contains(CLAIMED_ACTIONS_TAG, Tag.TAG_INT)) {
            return null;
        }
        ResourceLocation entryId = canonicalId(encoded.getString(ENTRY_ID_TAG));
        ResourceLocation dimensionId = canonicalId(encoded.getString(DIMENSION_TAG));
        VillageBuildingType buildingType = VillageBuildingType.byName(
                encoded.getString(BUILDING_TYPE_TAG)).orElse(null);
        if (entryId == null || dimensionId == null || buildingType == null) {
            return null;
        }
        try {
            UUID buildingId = encoded.getUUID(BUILDING_ID_TAG);
            VillageBuildingIdentity identity = new VillageBuildingIdentity(
                    encoded.getInt(VERSION_TAG),
                    buildingId,
                    entryId,
                    buildingType,
                    dimensionId,
                    BlockPos.of(encoded.getLong(ANCHOR_TAG)));
            return new VillageBuildingRecord(
                    identity,
                    encoded.getInt(CLAIMED_ACTIONS_TAG));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static @Nullable ResourceLocation canonicalId(String encoded) {
        ResourceLocation id = ResourceLocation.tryParse(encoded);
        return id != null && id.toString().equals(encoded) ? id : null;
    }

    private VillageBuildingRecordCodec() {
    }
}
