package com.gamergaming.villagerguardgunners.squad;

import com.mojang.logging.LogUtils;
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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * World-wide metadata for stable squad IDs.
 *
 * <p>The registry deliberately contains no guard IDs or spatial membership
 * index. It is read only during explicit squad operations and is stored in the
 * overworld data storage so the same identity can be used across dimensions.</p>
 */
public final class SquadRegistrySavedData extends SavedData {
    public static final int MAX_SQUADS = 4_096;
    public static final int LEGACY_DATA_VERSION = 1;
    public static final int ROUTES_DATA_VERSION = 2;
    public static final int DATA_VERSION = 3;

    private static final String DATA_NAME = "villagerguardgunners_squads";
    private static final String VERSION_TAG = "Version";
    private static final String ENTRIES_TAG = "Entries";
    private static final String ROUTES_TAG = "Routes";
    private static final String ROUTE_REVISION_TAG = "RouteRevision";
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
    private static final Set<String> LEGACY_ROOT_KEYS = Set.of(
            VERSION_TAG, ENTRIES_TAG);
    private static final Set<String> ROOT_KEYS = Set.of(
            VERSION_TAG, ENTRIES_TAG, ROUTES_TAG, ROUTE_REVISION_TAG);
    private static final Set<String> ROUTES_ROOT_KEYS = Set.of(
            VERSION_TAG, ENTRIES_TAG, ROUTES_TAG);
    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Logger LOGGER = LogUtils.getLogger();

    private final boolean enabled;
    private final Map<UUID, SquadMetadata> squads = new HashMap<>();
    private final Map<UUID, SquadPatrolRoute> routes = new HashMap<>();
    private long routeRevision;
    @Nullable
    private final CompoundTag unreadableRoot;
    @Nullable
    private final String disabledReason;
    private boolean disabledStateReported;

    public SquadRegistrySavedData() {
        this(true, Map.of(), Map.of(), 0L, null, null);
    }

    private SquadRegistrySavedData(
            boolean enabled,
            Map<UUID, SquadMetadata> squads,
            Map<UUID, SquadPatrolRoute> routes,
            long routeRevision,
            @Nullable CompoundTag unreadableRoot,
            @Nullable String disabledReason) {
        this.enabled = enabled;
        this.squads.putAll(squads);
        this.routes.putAll(routes);
        this.routeRevision = routeRevision;
        this.unreadableRoot = unreadableRoot == null ? null : unreadableRoot.copy();
        this.disabledReason = disabledReason;
    }

    /** Resolves the single world registry without creating a recurring service. */
    public static SquadRegistrySavedData get(MinecraftServer server) {
        SquadRegistrySavedData data = Objects.requireNonNull(server, "server")
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        SquadRegistrySavedData::load,
                        SquadRegistrySavedData::new,
                        DATA_NAME);
        data.reportDisabledStateOnce();
        return data;
    }

    public synchronized Optional<SquadMetadata> find(UUID squadId) {
        if (!enabled) {
            return Optional.empty();
        }
        return Optional.ofNullable(squads.get(squadId));
    }

    public synchronized Optional<SquadPatrolRoute> findPatrolRoute(UUID squadId) {
        if (!enabled) {
            return Optional.empty();
        }
        return Optional.ofNullable(routes.get(squadId));
    }

    /** Returns a deterministic immutable snapshot for commands and diagnostics. */
    public synchronized List<SquadMetadata> snapshot() {
        List<SquadMetadata> snapshot = new ArrayList<>(squads.values());
        snapshot.sort(Comparator.comparing(SquadMetadata::squadId));
        return List.copyOf(snapshot);
    }

    /** Returns a deterministic immutable route snapshot for diagnostics. */
    public synchronized List<SquadPatrolRoute> patrolRouteSnapshot() {
        List<SquadPatrolRoute> snapshot = new ArrayList<>(routes.values());
        snapshot.sort(Comparator.comparing(SquadPatrolRoute::squadId));
        return List.copyOf(snapshot);
    }

    public synchronized int size() {
        return squads.size();
    }

    public synchronized int patrolRouteCount() {
        return routes.size();
    }

    public boolean enabled() {
        return enabled;
    }

    public @Nullable String disabledReason() {
        return disabledReason;
    }

    /**
     * Registers a caller-generated stable ID. Duplicate IDs and the hard world
     * bound fail without changing or dirtying the registry.
     */
    public synchronized Optional<SquadMetadata> register(
            UUID squadId,
            @Nullable String requestedDisplayName,
            @Nullable UUID createdBy,
            long gameTime) {
        if (!enabled || squads.containsKey(squadId) || squads.size() >= MAX_SQUADS) {
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

    public synchronized Optional<SquadMetadata> rename(
            UUID squadId,
            @Nullable String requestedDisplayName,
            long gameTime) {
        SquadMetadata existing = enabled ? squads.get(squadId) : null;
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

    public synchronized boolean remove(UUID squadId) {
        if (!enabled) {
            return false;
        }
        if (squads.remove(squadId) == null) {
            return false;
        }
        routes.remove(squadId);
        setDirty();
        return true;
    }

    /**
     * Atomically replaces one squad route. An empty waypoint list clears it.
     * Existing routes cannot silently cross dimensions.
     */
    public synchronized RouteUpdateResult replacePatrolRoute(
            UUID squadId,
            ResourceLocation dimensionId,
            List<BlockPos> waypoints,
            long gameTime) {
        if (!enabled) {
            return RouteUpdateResult.of(RouteUpdateStatus.DISABLED, null);
        }
        if (squadId == null || dimensionId == null || waypoints == null || gameTime < 0L) {
            return RouteUpdateResult.of(RouteUpdateStatus.INVALID_ROUTE, null);
        }
        if (!squads.containsKey(squadId)) {
            return RouteUpdateResult.of(RouteUpdateStatus.SQUAD_NOT_FOUND, null);
        }
        SquadPatrolRoute existing = routes.get(squadId);
        List<BlockPos> replacement;
        try {
            replacement = List.copyOf(waypoints);
        } catch (NullPointerException exception) {
            return RouteUpdateResult.of(RouteUpdateStatus.INVALID_ROUTE, existing);
        }
        if (existing != null && gameTime < existing.updatedGameTime()) {
            return RouteUpdateResult.of(RouteUpdateStatus.INVALID_ROUTE, existing);
        }
        if (replacement.isEmpty()) {
            if (existing == null) {
                return RouteUpdateResult.of(RouteUpdateStatus.UNCHANGED, null);
            }
            routes.remove(squadId);
            setDirty();
            return RouteUpdateResult.of(RouteUpdateStatus.CLEARED, null);
        }
        if (existing != null && !existing.dimensionId().equals(dimensionId)) {
            return RouteUpdateResult.of(RouteUpdateStatus.WRONG_DIMENSION, existing);
        }
        if (existing != null && existing.waypoints().equals(replacement)) {
            return RouteUpdateResult.of(RouteUpdateStatus.UNCHANGED, existing);
        }
        if (routeRevision == Long.MAX_VALUE) {
            return RouteUpdateResult.of(RouteUpdateStatus.REVISION_EXHAUSTED, existing);
        }
        try {
            long nextRevision = routeRevision + 1L;
            SquadPatrolRoute updated = existing == null
                    ? SquadPatrolRoute.createAtRevision(
                            squadId,
                            dimensionId,
                            replacement,
                            nextRevision,
                            gameTime)
                    : existing.revisedAtRevision(
                            replacement, nextRevision, gameTime);
            routes.put(squadId, updated);
            routeRevision = nextRevision;
            setDirty();
            return RouteUpdateResult.of(RouteUpdateStatus.UPDATED, updated);
        } catch (IllegalArgumentException exception) {
            return RouteUpdateResult.of(RouteUpdateStatus.INVALID_ROUTE, existing);
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        if (!enabled) {
            return unreadableRoot == null ? new CompoundTag() : unreadableRoot.copy();
        }
        tag.putInt(VERSION_TAG, DATA_VERSION);
        ListTag entries = new ListTag();
        snapshot().forEach(metadata -> entries.add(encode(metadata)));
        tag.put(ENTRIES_TAG, entries);
        ListTag encodedRoutes = new ListTag();
        patrolRouteSnapshot().stream()
                .map(SquadPatrolRouteCodec::encode)
                .forEach(encodedRoutes::add);
        tag.put(ROUTES_TAG, encodedRoutes);
        tag.putLong(ROUTE_REVISION_TAG, routeRevision);
        return tag;
    }

    static SquadRegistrySavedData load(CompoundTag tag) {
        if (tag == null) {
            return disabled(new CompoundTag(), "missing root payload");
        }
        if (!tag.contains(VERSION_TAG, Tag.TAG_INT)) {
            return disabled(tag, "missing root version");
        }
        int version = tag.getInt(VERSION_TAG);
        Set<String> expectedKeys = switch (version) {
            case LEGACY_DATA_VERSION -> LEGACY_ROOT_KEYS;
            case ROUTES_DATA_VERSION -> ROUTES_ROOT_KEYS;
            case DATA_VERSION -> ROOT_KEYS;
            default -> Set.of();
        };
        if (expectedKeys.isEmpty()
                || !tag.getAllKeys().equals(expectedKeys)
                || !(tag.get(ENTRIES_TAG) instanceof ListTag entries)
                || (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND)
                || entries.size() > MAX_SQUADS) {
            return disabled(tag, "unsupported or malformed root schema");
        }

        Map<UUID, SquadMetadata> squads = new HashMap<>();
        int inspectedEntries = Math.min(entries.size(), MAX_SQUADS);
        for (int index = 0;
                index < inspectedEntries && squads.size() < MAX_SQUADS;
                index++) {
            SquadMetadata metadata = decode(entries.getCompound(index));
            if (metadata != null) {
                squads.putIfAbsent(metadata.squadId(), metadata);
            }
        }

        Map<UUID, SquadPatrolRoute> routes = new HashMap<>();
        if (version >= ROUTES_DATA_VERSION) {
            Tag rawRoutes = tag.get(ROUTES_TAG);
            if (!(rawRoutes instanceof ListTag encodedRoutes)
                    || (!encodedRoutes.isEmpty()
                            && encodedRoutes.getElementType() != Tag.TAG_COMPOUND)
                    || encodedRoutes.size() > MAX_SQUADS) {
                return disabled(tag, "patrol-route list is malformed or unbounded");
            }
            for (Tag rawRoute : encodedRoutes) {
                if (!(rawRoute instanceof CompoundTag routeTag)) {
                    return disabled(tag, "patrol-route list contains a non-compound value");
                }
                SquadPatrolRoute route = SquadPatrolRouteCodec.decode(routeTag);
                if (route == null
                        || !squads.containsKey(route.squadId())
                        || routes.put(route.squadId(), route) != null) {
                    return disabled(tag,
                            "a patrol route failed strict decoding or has no unique squad owner");
                }
            }
        }
        long routeRevision;
        if (version == DATA_VERSION) {
            if (!tag.contains(ROUTE_REVISION_TAG, Tag.TAG_LONG)) {
                return disabled(tag, "the patrol-route revision counter is missing");
            }
            routeRevision = tag.getLong(ROUTE_REVISION_TAG);
            if (routeRevision < 0L
                    || routes.values().stream().anyMatch(
                            route -> route.revision() > routeRevision)) {
                return disabled(tag, "the patrol-route revision counter is invalid");
            }
        } else {
            routeRevision = routes.values().stream()
                    .mapToLong(SquadPatrolRoute::revision)
                    .max()
                    .orElse(0L);
        }
        SquadRegistrySavedData registry = new SquadRegistrySavedData(
                true, squads, routes, routeRevision, null, null);
        if (version != DATA_VERSION) {
            registry.setDirty();
        } else {
            registry.setDirty(false);
        }
        return registry;
    }

    private static SquadRegistrySavedData disabled(CompoundTag root, String reason) {
        return new SquadRegistrySavedData(
                false, Map.of(), Map.of(), 0L, root, reason);
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

    private synchronized void reportDisabledStateOnce() {
        if (enabled || disabledStateReported) {
            return;
        }
        disabledStateReported = true;
        LOGGER.error(
                "Squad registry state is disabled: {}. The original data will be preserved unchanged",
                disabledReason == null ? "unknown strict-decoding failure" : disabledReason);
    }

    public enum RouteUpdateStatus {
        UPDATED,
        CLEARED,
        UNCHANGED,
        SQUAD_NOT_FOUND,
        WRONG_DIMENSION,
        INVALID_ROUTE,
        REVISION_EXHAUSTED,
        DISABLED
    }

    public record RouteUpdateResult(
            RouteUpdateStatus status,
            @Nullable SquadPatrolRoute route) {
        public RouteUpdateResult {
            Objects.requireNonNull(status, "status");
            if (status == RouteUpdateStatus.UPDATED && route == null
                    || (status == RouteUpdateStatus.CLEARED
                                    || status == RouteUpdateStatus.SQUAD_NOT_FOUND
                                    || status == RouteUpdateStatus.DISABLED)
                            && route != null) {
                throw new IllegalArgumentException(
                        "Patrol-route update result is contradictory");
            }
        }

        private static RouteUpdateResult of(
                RouteUpdateStatus status,
                @Nullable SquadPatrolRoute route) {
            return new RouteUpdateResult(status, route);
        }

        public boolean changed() {
            return status == RouteUpdateStatus.UPDATED
                    || status == RouteUpdateStatus.CLEARED;
        }
    }
}
