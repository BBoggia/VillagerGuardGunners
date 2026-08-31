package com.gamergaming.villagerguardgunners.squad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-wide metadata for stable squad IDs.
 *
 * <p>The registry deliberately contains no guard IDs or spatial membership
 * index. It is read only during explicit squad operations and is stored in the
 * overworld data storage so the same identity can be used across dimensions.</p>
 */
public final class SquadRegistrySavedData extends SavedData {
    public static final int MAX_SQUADS = 4_096;

    private static final String DATA_NAME = "villagerguardgunners_squads";
    private static final int DATA_VERSION = 1;
    private static final String VERSION_TAG = "Version";
    private static final String ENTRIES_TAG = "Entries";
    private static final String ENTRY_VERSION_TAG = "Version";
    private static final String SQUAD_ID_TAG = "SquadId";
    private static final String DISPLAY_NAME_TAG = "DisplayName";
    private static final String CREATED_BY_TAG = "CreatedBy";
    private static final String CREATED_GAME_TIME_TAG = "CreatedGameTime";
    private static final String UPDATED_GAME_TIME_TAG = "UpdatedGameTime";
    private static final Set<String> REQUIRED_ENTRY_KEYS = Set.of(
            ENTRY_VERSION_TAG,
            SQUAD_ID_TAG,
            DISPLAY_NAME_TAG,
            CREATED_GAME_TIME_TAG,
            UPDATED_GAME_TIME_TAG);
    private static final Set<String> OWNED_ENTRY_KEYS = Set.of(
            ENTRY_VERSION_TAG,
            SQUAD_ID_TAG,
            DISPLAY_NAME_TAG,
            CREATED_BY_TAG,
            CREATED_GAME_TIME_TAG,
            UPDATED_GAME_TIME_TAG);
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private final Map<UUID, SquadMetadata> squads = new HashMap<>();

    /** Resolves the single world registry without creating a recurring service. */
    public static SquadRegistrySavedData get(MinecraftServer server) {
        return Objects.requireNonNull(server, "server")
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        SquadRegistrySavedData::load,
                        SquadRegistrySavedData::new,
                        DATA_NAME);
    }

    public Optional<SquadMetadata> find(UUID squadId) {
        return Optional.ofNullable(squads.get(squadId));
    }

    /** Returns a deterministic immutable snapshot for commands and diagnostics. */
    public List<SquadMetadata> snapshot() {
        List<SquadMetadata> snapshot = new ArrayList<>(squads.values());
        snapshot.sort(Comparator.comparing(SquadMetadata::squadId));
        return List.copyOf(snapshot);
    }

    public int size() {
        return squads.size();
    }

    /**
     * Registers a caller-generated stable ID. Duplicate IDs and the hard world
     * bound fail without changing or dirtying the registry.
     */
    public Optional<SquadMetadata> register(
            UUID squadId,
            @Nullable String requestedDisplayName,
            @Nullable UUID createdBy,
            long gameTime) {
        if (squads.containsKey(squadId) || squads.size() >= MAX_SQUADS) {
            return Optional.empty();
        }
        SquadMetadata metadata = SquadMetadata.create(
                squadId,
                requestedDisplayName,
                createdBy,
                gameTime);
        squads.put(squadId, metadata);
        setDirty();
        return Optional.of(metadata);
    }

    public Optional<SquadMetadata> rename(
            UUID squadId,
            @Nullable String requestedDisplayName,
            long gameTime) {
        SquadMetadata existing = squads.get(squadId);
        if (existing == null) {
            return Optional.empty();
        }
        SquadMetadata replacement = existing.renamed(requestedDisplayName, gameTime);
        if (!replacement.equals(existing)) {
            squads.put(squadId, replacement);
            setDirty();
        }
        return Optional.of(replacement);
    }

    public boolean remove(UUID squadId) {
        if (squads.remove(squadId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(VERSION_TAG, DATA_VERSION);
        ListTag entries = new ListTag();
        snapshot().forEach(metadata -> entries.add(encode(metadata)));
        tag.put(ENTRIES_TAG, entries);
        return tag;
    }

    static SquadRegistrySavedData load(CompoundTag tag) {
        SquadRegistrySavedData registry = new SquadRegistrySavedData();
        if (tag == null
                || !tag.contains(VERSION_TAG, Tag.TAG_INT)
                || tag.getInt(VERSION_TAG) != DATA_VERSION
                || !tag.contains(ENTRIES_TAG, Tag.TAG_LIST)) {
            return registry;
        }

        ListTag entries = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        int inspectedEntries = Math.min(entries.size(), MAX_SQUADS);
        for (int index = 0;
                index < inspectedEntries && registry.squads.size() < MAX_SQUADS;
                index++) {
            SquadMetadata metadata = decode(entries.getCompound(index));
            if (metadata != null) {
                registry.squads.putIfAbsent(metadata.squadId(), metadata);
            }
        }
        registry.setDirty(false);
        return registry;
    }

    private static CompoundTag encode(SquadMetadata metadata) {
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(ENTRY_VERSION_TAG, metadata.schemaVersion());
        encoded.putUUID(SQUAD_ID_TAG, metadata.squadId());
        encoded.putString(DISPLAY_NAME_TAG, metadata.displayName());
        if (metadata.createdBy() != null) {
            encoded.putUUID(CREATED_BY_TAG, metadata.createdBy());
        }
        encoded.putLong(CREATED_GAME_TIME_TAG, metadata.createdGameTime());
        encoded.putLong(UPDATED_GAME_TIME_TAG, metadata.updatedGameTime());
        return encoded;
    }

    @Nullable
    private static SquadMetadata decode(CompoundTag encoded) {
        Set<String> keys = encoded.getAllKeys();
        if ((!keys.equals(REQUIRED_ENTRY_KEYS) && !keys.equals(OWNED_ENTRY_KEYS))
                || !encoded.contains(ENTRY_VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(ENTRY_VERSION_TAG) != SquadMetadata.CURRENT_SCHEMA_VERSION
                || !encoded.hasUUID(SQUAD_ID_TAG)
                || !encoded.contains(DISPLAY_NAME_TAG, Tag.TAG_STRING)
                || !encoded.contains(CREATED_GAME_TIME_TAG, Tag.TAG_LONG)
                || !encoded.contains(UPDATED_GAME_TIME_TAG, Tag.TAG_LONG)
                || (encoded.contains(CREATED_BY_TAG) && !encoded.hasUUID(CREATED_BY_TAG))) {
            return null;
        }

        try {
            UUID squadId = encoded.getUUID(SQUAD_ID_TAG);
            UUID createdBy = encoded.hasUUID(CREATED_BY_TAG)
                    ? encoded.getUUID(CREATED_BY_TAG)
                    : null;
            String displayName = encoded.getString(DISPLAY_NAME_TAG);
            long createdGameTime = encoded.getLong(CREATED_GAME_TIME_TAG);
            long updatedGameTime = encoded.getLong(UPDATED_GAME_TIME_TAG);
            if (NIL_UUID.equals(squadId)
                    || NIL_UUID.equals(createdBy)
                    || !SquadMetadata.isCanonicalDisplayName(displayName)) {
                return null;
            }
            return new SquadMetadata(
                    SquadMetadata.CURRENT_SCHEMA_VERSION,
                    squadId,
                    displayName,
                    createdBy,
                    createdGameTime,
                    updatedGameTime);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
