package com.gamergaming.villagerguardgunners.worldgen.village;

import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.slf4j.Logger;

/** Passive per-dimension registry of placed village-building action claims. */
public final class VillageBuildingSavedData extends SavedData {
    public static final String DATA_NAME = "villagerguardgunners_village_buildings";
    public static final int DATA_VERSION = 1;
    public static final int MAXIMUM_RECORDS = 65_536;
    private static final Logger LOGGER = LogUtils.getLogger();

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
    @Nullable
    private final String disabledReason;
    private final EnumSet<ClaimResult> reportedClaimFailures =
            EnumSet.noneOf(ClaimResult.class);
    private boolean disabledStateReported;

    private VillageBuildingSavedData(
            boolean enabled,
            Map<UUID, VillageBuildingRecord> records,
            @Nullable CompoundTag unreadableRoot,
            @Nullable String disabledReason) {
        this.enabled = enabled;
        this.records = new LinkedHashMap<>(records);
        this.anchors = new HashMap<>();
        this.unreadableRoot = unreadableRoot == null ? null : unreadableRoot.copy();
        this.disabledReason = disabledReason;
        for (VillageBuildingRecord record : records.values()) {
            VillageBuildingIdentity identity = record.identity();
            anchors.put(
                    new AnchorKey(identity.dimensionId(), identity.anchorPosition()),
                    identity.buildingId());
        }
    }

    public VillageBuildingSavedData() {
        this(true, Map.of(), null, null);
    }

    public static VillageBuildingSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        VillageBuildingSavedData data = level.getDataStorage().computeIfAbsent(
                encoded -> loadForLevel(
                        encoded,
                        level.dimension().location(),
                        level.getSeed()),
                VillageBuildingSavedData::new,
                DATA_NAME);
        data.reportDisabledStateOnce(level.dimension().location());
        return data;
    }

    public static VillageBuildingSavedData load(CompoundTag encoded) {
        return load(encoded, null, null);
    }

    static VillageBuildingSavedData loadForLevel(
            CompoundTag encoded,
            ResourceLocation expectedDimension,
            long worldSeed) {
        return load(
                encoded,
                Objects.requireNonNull(expectedDimension, "expectedDimension"),
                worldSeed);
    }

    private static VillageBuildingSavedData load(
            CompoundTag encoded,
            @Nullable ResourceLocation expectedDimension,
            @Nullable Long worldSeed) {
        Objects.requireNonNull(encoded, "encoded");
        if (!encoded.getAllKeys().equals(ROOT_KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != DATA_VERSION
                || !encoded.contains(RECORDS_TAG, Tag.TAG_LIST)) {
            return disabled(encoded, "unsupported or malformed root schema");
        }
        Tag recordsTag = encoded.get(RECORDS_TAG);
        if (!(recordsTag instanceof ListTag encodedRecords)
                || (!encodedRecords.isEmpty()
                        && encodedRecords.getElementType() != Tag.TAG_COMPOUND)) {
            return disabled(encoded, "Records is not a compound list");
        }
        if (encodedRecords.size() > MAXIMUM_RECORDS) {
            return disabled(encoded, "record limit exceeded");
        }
        Map<UUID, VillageBuildingRecord> records = new LinkedHashMap<>();
        Map<AnchorKey, UUID> anchors = new HashMap<>();
        for (Tag tag : encodedRecords) {
            if (!(tag instanceof CompoundTag wrapper)) {
                return disabled(encoded, "Records contains a non-compound value");
            }
            VillageBuildingRecord record = VillageBuildingRecordCodec.decodeWrapped(wrapper);
            if (record == null) {
                return disabled(encoded, "a building record failed strict decoding");
            }
            VillageBuildingIdentity identity = record.identity();
            if (expectedDimension != null
                    && !expectedDimension.equals(identity.dimensionId())) {
                return disabled(encoded, "a record belongs to a different dimension");
            }
            if (worldSeed != null
                    && !VillageBuildingIds.buildingId(
                            worldSeed,
                            identity.dimensionId(),
                            identity.entryId(),
                            identity.buildingType(),
                            identity.anchorPosition()).equals(identity.buildingId())) {
                return disabled(encoded, "a record has a non-deterministic building ID");
            }
            AnchorKey anchor = new AnchorKey(
                    identity.dimensionId(), identity.anchorPosition());
            if (records.put(identity.buildingId(), record) != null
                    || anchors.put(anchor, identity.buildingId()) != null) {
                return disabled(encoded, "duplicate building ID or physical anchor");
            }
        }
        return new VillageBuildingSavedData(true, records, null, null);
    }

    private static VillageBuildingSavedData disabled(
            CompoundTag encoded,
            String reason) {
        return new VillageBuildingSavedData(false, Map.of(), encoded, reason);
    }

    public synchronized ClaimResult claimAnchor(VillageBuildingIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (!enabled) {
            reportClaimFailureOnce(ClaimResult.DISABLED, identity);
            return ClaimResult.DISABLED;
        }
        VillageBuildingRecord existing = records.get(identity.buildingId());
        if (existing != null) {
            ClaimResult result = existing.identity().equals(identity)
                    ? ClaimResult.ALREADY_CLAIMED
                    : ClaimResult.CONFLICT;
            reportClaimFailureOnce(result, identity);
            return result;
        }
        AnchorKey anchor = new AnchorKey(identity.dimensionId(), identity.anchorPosition());
        if (anchors.containsKey(anchor)) {
            reportClaimFailureOnce(ClaimResult.CONFLICT, identity);
            return ClaimResult.CONFLICT;
        }
        if (records.size() >= MAXIMUM_RECORDS) {
            reportClaimFailureOnce(ClaimResult.LIMIT_REACHED, identity);
            return ClaimResult.LIMIT_REACHED;
        }
        records.put(identity.buildingId(), VillageBuildingRecord.anchored(identity));
        anchors.put(anchor, identity.buildingId());
        setDirty();
        return ClaimResult.CLAIMED;
    }

    /**
     * Reserves one action in memory and marks this file dirty. Future callers
     * must still make the physical side effect deterministic and idempotent;
     * SavedData and entity/chunk saves are not one atomic crash transaction.
     */
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

    /** Atomically reserves placement and the evaluated initial-stock decision. */
    public synchronized ClaimResult claimArmorySetup(UUID buildingId) {
        Objects.requireNonNull(buildingId, "buildingId");
        if (!enabled) {
            return ClaimResult.DISABLED;
        }
        VillageBuildingRecord current = records.get(buildingId);
        if (current == null) {
            return ClaimResult.MISSING_RECORD;
        }
        if (current.identity().buildingType() != VillageBuildingType.ARMORY) {
            return ClaimResult.CONFLICT;
        }
        if (current.hasClaimed(VillageBuildingAction.INITIAL_STOCK)) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        VillageBuildingRecord claimed = current;
        if (!claimed.hasClaimed(VillageBuildingAction.ARMORY)) {
            claimed = claimed.claim(VillageBuildingAction.ARMORY);
        }
        claimed = claimed.claim(VillageBuildingAction.INITIAL_STOCK);
        records.put(buildingId, claimed);
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
        for (VillageBuildingRecord record : records.values()) {
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

    private synchronized void reportDisabledStateOnce(ResourceLocation dimensionId) {
        if (enabled || disabledStateReported) {
            return;
        }
        disabledStateReported = true;
        LOGGER.error(
                "Village Defense Building state is disabled in {}: {}. The original data will be preserved unchanged",
                dimensionId,
                disabledReason == null ? "unknown strict-decoding failure" : disabledReason);
    }

    private void reportClaimFailureOnce(
            ClaimResult result,
            VillageBuildingIdentity identity) {
        if (result == ClaimResult.ALREADY_CLAIMED
                || result == ClaimResult.CLAIMED
                || !reportedClaimFailures.add(result)) {
            return;
        }
        LOGGER.warn(
                "Village Defense Building anchor claim failed once in {}: result={}, entry={}, anchor={}",
                identity.dimensionId(),
                result,
                identity.entryId(),
                identity.anchorPosition());
    }

    private record AnchorKey(ResourceLocation dimensionId, BlockPos position) {
        private AnchorKey {
            Objects.requireNonNull(dimensionId, "dimensionId");
            position = Objects.requireNonNull(position, "position").immutable();
        }
    }
}
