package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Passive per-dimension registry of placed village-building action claims. */
public final class VillageBuildingSavedData extends SavedData {
    public static final String DATA_NAME = "villagerguardgunners_village_buildings";
    public static final int DATA_VERSION = 1;
    public static final int MAXIMUM_RECORDS = 262_144;

    private static final String VERSION_TAG = "Version";
    private static final String RECORDS_TAG = "Records";
    private static final java.util.Set<String> ROOT_KEYS = java.util.Set.of(
            VERSION_TAG,
            RECORDS_TAG);

    private final boolean enabled;
    private final LinkedHashMap<UUID, VillageBuildingRecord> records;
    private final Map<AnchorKey, UUID> anchors;
    @Nullable
    private final CompoundTag unreadableRoot;

    private VillageBuildingSavedData(
            boolean enabled,
            Map<UUID, VillageBuildingRecord> records,
            @Nullable CompoundTag unreadableRoot) {
        this.enabled = enabled;
        this.records = new LinkedHashMap<>(records);
        this.anchors = new HashMap<>();
        this.unreadableRoot = unreadableRoot == null ? null : unreadableRoot.copy();
        for (VillageBuildingRecord record : records.values()) {
            VillageBuildingIdentity identity = record.identity();
            anchors.put(
                    new AnchorKey(identity.dimensionId(), identity.anchorPosition()),
                    identity.buildingId());
        }
    }

    public VillageBuildingSavedData() {
        this(true, Map.of(), null);
    }

    public static VillageBuildingSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(
                VillageBuildingSavedData::load,
                VillageBuildingSavedData::new,
                DATA_NAME);
    }

    public static VillageBuildingSavedData load(CompoundTag encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (!encoded.getAllKeys().equals(ROOT_KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != DATA_VERSION
                || !encoded.contains(RECORDS_TAG, Tag.TAG_LIST)) {
            return disabled(encoded);
        }
        Tag recordsTag = encoded.get(RECORDS_TAG);
        if (!(recordsTag instanceof ListTag encodedRecords)
                || (!encodedRecords.isEmpty()
                        && encodedRecords.getElementType() != Tag.TAG_COMPOUND)) {
            return disabled(encoded);
        }
        if (encodedRecords.size() > MAXIMUM_RECORDS) {
            return disabled(encoded);
        }
        Map<UUID, VillageBuildingRecord> records = new LinkedHashMap<>();
        Map<AnchorKey, UUID> anchors = new HashMap<>();
        for (Tag tag : encodedRecords) {
            if (!(tag instanceof CompoundTag wrapper)) {
                return disabled(encoded);
            }
            VillageBuildingRecord record = VillageBuildingRecordCodec.decodeWrapped(wrapper);
            if (record == null) {
                return disabled(encoded);
            }
            VillageBuildingIdentity identity = record.identity();
            AnchorKey anchor = new AnchorKey(
                    identity.dimensionId(), identity.anchorPosition());
            if (records.put(identity.buildingId(), record) != null
                    || anchors.put(anchor, identity.buildingId()) != null) {
                return disabled(encoded);
            }
        }
        return new VillageBuildingSavedData(true, records, null);
    }

    private static VillageBuildingSavedData disabled(CompoundTag encoded) {
        return new VillageBuildingSavedData(false, Map.of(), encoded);
    }

    public synchronized ClaimResult claimAnchor(VillageBuildingIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (!enabled) {
            return ClaimResult.DISABLED;
        }
        VillageBuildingRecord existing = records.get(identity.buildingId());
        if (existing != null) {
            return existing.identity().equals(identity)
                    ? ClaimResult.ALREADY_CLAIMED
                    : ClaimResult.CONFLICT;
        }
        AnchorKey anchor = new AnchorKey(identity.dimensionId(), identity.anchorPosition());
        if (anchors.containsKey(anchor)) {
            return ClaimResult.CONFLICT;
        }
        if (records.size() >= MAXIMUM_RECORDS) {
            return ClaimResult.LIMIT_REACHED;
        }
        records.put(identity.buildingId(), VillageBuildingRecord.anchored(identity));
        anchors.put(anchor, identity.buildingId());
        setDirty();
        return ClaimResult.CLAIMED;
    }

    public synchronized ClaimResult claimAction(
            UUID buildingId,
            VillageBuildingAction action) {
        Objects.requireNonNull(buildingId, "buildingId");
        Objects.requireNonNull(action, "action");
        if (!enabled) {
            return ClaimResult.DISABLED;
        }
        VillageBuildingRecord current = records.get(buildingId);
        if (current == null) {
            return ClaimResult.MISSING_RECORD;
        }
        if (current.hasClaimed(action)) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        if (!current.canClaim(action)) {
            return ClaimResult.PREREQUISITE_MISSING;
        }
        records.put(buildingId, current.claim(action));
        setDirty();
        return ClaimResult.CLAIMED;
    }

    public synchronized @Nullable VillageBuildingRecord get(UUID buildingId) {
        return records.get(Objects.requireNonNull(buildingId, "buildingId"));
    }

    public synchronized @Nullable VillageBuildingRecord getAt(
            ResourceLocation dimensionId,
            BlockPos anchorPosition) {
        UUID buildingId = anchors.get(new AnchorKey(dimensionId, anchorPosition));
        return buildingId == null ? null : records.get(buildingId);
    }

    public boolean enabled() {
        return enabled;
    }

    public synchronized int size() {
        return records.size();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag output) {
        if (!enabled) {
            return unreadableRoot == null ? new CompoundTag() : unreadableRoot.copy();
        }
        output.putInt(VERSION_TAG, DATA_VERSION);
        ListTag encodedRecords = new ListTag();
        List<VillageBuildingRecord> sorted = new ArrayList<>(records.values());
        sorted.sort(Comparator.comparing(record -> record.identity().buildingId().toString()));
        for (VillageBuildingRecord record : sorted) {
            encodedRecords.add(VillageBuildingRecordCodec.encodeWrapped(record));
        }
        output.put(RECORDS_TAG, encodedRecords);
        return output;
    }

    public enum ClaimResult {
        CLAIMED,
        ALREADY_CLAIMED,
        PREREQUISITE_MISSING,
        MISSING_RECORD,
        CONFLICT,
        LIMIT_REACHED,
        DISABLED
    }

    private record AnchorKey(ResourceLocation dimensionId, BlockPos position) {
        private AnchorKey {
            Objects.requireNonNull(dimensionId, "dimensionId");
            position = Objects.requireNonNull(position, "position").immutable();
        }
    }
}
