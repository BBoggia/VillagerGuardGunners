package com.gamergaming.villagerguardgunners.worldgen.village;

import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/** Passive per-dimension registry of placed village-building action claims. */
public final class VillageBuildingSavedData extends SavedData {
    public static final String DATA_NAME = "villagerguardgunners_village_buildings";
    public static final int LEGACY_DATA_VERSION = 1;
    public static final int DATA_VERSION = 2;
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
    private final Map<Long, LinkedHashSet<UUID>> buildingsByChunk = new HashMap<>();
    private final Map<Long, LinkedHashSet<UUID>> pendingByChunk;
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
        this.pendingByChunk = new HashMap<>();
        this.unreadableRoot = unreadableRoot == null ? null : unreadableRoot.copy();
        this.disabledReason = disabledReason;
        for (VillageBuildingRecord record : records.values()) {
            VillageBuildingIdentity identity = record.identity();
            anchors.put(
                    new AnchorKey(identity.dimensionId(), identity.anchorPosition()),
                    identity.buildingId());
            indexPending(record);
            indexBuilding(record.identity());
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
                || !encoded.contains(RECORDS_TAG, Tag.TAG_LIST)) {
            return disabled(encoded, "unsupported or malformed root schema");
        }
        int dataVersion = encoded.getInt(VERSION_TAG);
        if (dataVersion != LEGACY_DATA_VERSION && dataVersion != DATA_VERSION) {
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
            if (dataVersion == LEGACY_DATA_VERSION
                    && record.pendingActivation().isPresent()) {
                return disabled(encoded, "legacy data contains future pending state");
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
        return claimAnchor(identity, null);
    }

    public synchronized ClaimResult claimAnchor(
            VillageBuildingIdentity identity,
            @Nullable VillageBuildingPendingActivation pendingActivation) {
        Objects.requireNonNull(identity, "identity");
        if (!enabled) {
            reportClaimFailureOnce(ClaimResult.DISABLED, identity);
            return ClaimResult.DISABLED;
        }
        VillageBuildingRecord existing = records.get(identity.buildingId());
        if (existing != null) {
            ClaimResult result;
            if (!existing.identity().equals(identity)) {
                result = ClaimResult.CONFLICT;
            } else {
                if (pendingActivation != null
                        && existing.pendingActivation().isEmpty()
                        && !existing.hasClaimed(pendingActivation.requiredAction())) {
                    replaceRecord(existing, existing.withPendingActivation(pendingActivation));
                    setDirty();
                }
                result = ClaimResult.ALREADY_CLAIMED;
            }
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
        VillageBuildingRecord record = pendingActivation == null
                ? VillageBuildingRecord.anchored(identity)
                : VillageBuildingRecord.anchored(identity, pendingActivation);
        records.put(identity.buildingId(), record);
        anchors.put(anchor, identity.buildingId());
        indexBuilding(identity);
        indexPending(record);
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
        replaceRecord(current, current.claim(action));
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
        replaceRecord(current, claimed);
        setDirty();
        return ClaimResult.CLAIMED;
    }

    /** Clears durable retry context without claiming a disabled or inapplicable action. */
    public synchronized boolean completePendingActivation(UUID buildingId) {
        Objects.requireNonNull(buildingId, "buildingId");
        if (!enabled) {
            return false;
        }
        VillageBuildingRecord current = records.get(buildingId);
        if (current == null || current.pendingActivation().isEmpty()) {
            return false;
        }
        replaceRecord(current, current.withoutPendingActivation());
        setDirty();
        return true;
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

    private void indexBuilding(VillageBuildingIdentity identity) {
        long chunk = new ChunkPos(identity.anchorPosition()).toLong();
        buildingsByChunk.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>()).add(identity.buildingId());
    }

    /** Bounded cached anchor lookup; includes completed buildings in existing saves. */
    public synchronized List<VillageBuildingIdentity> identitiesInChunk(ChunkPos chunk, int limit) {
        if (limit < 1 || limit > 256) throw new IllegalArgumentException("Invalid anchor query limit");
        return buildingsByChunk.getOrDefault(chunk.toLong(), new LinkedHashSet<>()).stream()
                .limit(limit).map(records::get).map(VillageBuildingRecord::identity).toList();
    }

    public boolean enabled() {
        return enabled;
    }

    public synchronized int size() {
        return records.size();
    }

    public synchronized int pendingActivationCount() {
        return (int) records.values().stream()
                .filter(record -> record.pendingActivation().isPresent())
                .count();
    }

    /** Immutable identity snapshot for one-time defense-network reconciliation. */
    public synchronized List<VillageBuildingIdentity> recordsSnapshot() {
        return records.values().stream()
                .map(VillageBuildingRecord::identity)
                .toList();
    }

    /** O(1) chunk-key lookup with a small immutable result for event-driven recovery. */
    public synchronized List<VillageBuildingRecord> pendingActivationsIn(
            ChunkPos chunkPosition) {
        Objects.requireNonNull(chunkPosition, "chunkPosition");
        Set<UUID> buildingIds = pendingByChunk.get(chunkPosition.toLong());
        if (buildingIds == null || buildingIds.isEmpty()) {
            return List.of();
        }
        return buildingIds.stream()
                .map(records::get)
                .filter(Objects::nonNull)
                .filter(record -> record.pendingActivation().isPresent())
                .toList();
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

    private void replaceRecord(
            VillageBuildingRecord previous,
            VillageBuildingRecord replacement) {
        unindexPending(previous);
        records.put(replacement.identity().buildingId(), replacement);
        indexPending(replacement);
    }

    private void indexPending(VillageBuildingRecord record) {
        record.pendingActivation().ifPresent(pending -> {
            for (long chunk : pending.relevantChunks(record.identity())) {
                pendingByChunk.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>())
                        .add(record.identity().buildingId());
            }
        });
    }

    private void unindexPending(VillageBuildingRecord record) {
        record.pendingActivation().ifPresent(pending -> {
            for (long chunk : pending.relevantChunks(record.identity())) {
                LinkedHashSet<UUID> buildingIds = pendingByChunk.get(chunk);
                if (buildingIds != null) {
                    buildingIds.remove(record.identity().buildingId());
                    if (buildingIds.isEmpty()) {
                        pendingByChunk.remove(chunk);
                    }
                }
            }
        });
    }

    private record AnchorKey(ResourceLocation dimensionId, BlockPos position) {
        private AnchorKey {
            Objects.requireNonNull(dimensionId, "dimensionId");
            position = Objects.requireNonNull(position, "position").immutable();
        }
    }
}
