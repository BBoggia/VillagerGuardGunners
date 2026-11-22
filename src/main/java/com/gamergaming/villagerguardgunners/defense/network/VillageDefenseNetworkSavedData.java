package com.gamergaming.villagerguardgunners.defense.network;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseSignal;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseReviewFacts;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseStateLifecycle;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseStatus;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseTimings;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIds;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingIdentity;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingSavedData;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * Per-dimension, spatially indexed defense-network membership. Mutations occur
 * only when a building is registered or existing building data is reconciled.
 */
public final class VillageDefenseNetworkSavedData extends SavedData {
    public static final String DATA_NAME = "villagerguardgunners_defense_networks";
    public static final int DATA_VERSION = 1;
    public static final int MAXIMUM_NETWORKS = 65_536;
    public static final int MAXIMUM_ALIASES = 65_536;
    public static final int MAXIMUM_BUILDINGS = VillageBuildingSavedData.MAXIMUM_RECORDS;
    public static final double DEFAULT_LINK_RADIUS = 128.0D;
    public static final double ABSOLUTE_MAXIMUM_LINK_RADIUS = 256.0D;
    public static final int MAXIMUM_REVIEWS_PER_TICK = 256;

    private static final String VERSION_TAG = "Version";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String NETWORKS_TAG = "Networks";
    private static final String ALIASES_TAG = "Aliases";
    private static final String ALIAS_FROM_TAG = "From";
    private static final String ALIAS_TO_TAG = "To";
    private static final Set<String> ROOT_KEYS = Set.of(
            VERSION_TAG, DIMENSION_TAG, NETWORKS_TAG, ALIASES_TAG);
    private static final Set<String> ALIAS_KEYS = Set.of(
            ALIAS_FROM_TAG, ALIAS_TO_TAG);
    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Logger LOGGER = LogUtils.getLogger();

    private final boolean enabled;
    @Nullable
    private final ResourceLocation dimensionId;
    @Nullable
    private final Long worldSeed;
    private final LinkedHashMap<UUID, VillageDefenseNetwork> networks;
    private final Map<UUID, UUID> networkByBuilding;
    private final Map<UUID, VillageBuildingIdentity> identityByBuilding;
    private final Map<BlockPos, UUID> buildingByAnchor;
    private final Map<Long, LinkedHashSet<UUID>> buildingsByChunk;
    private final NavigableMap<Long, LinkedHashSet<UUID>> reviewsByGameTime;
    private final LinkedHashMap<UUID, UUID> aliases;
    @Nullable
    private final CompoundTag unreadableRoot;
    @Nullable
    private final String disabledReason;
    private final EnumSet<ClaimStatus> reportedClaimFailures =
            EnumSet.noneOf(ClaimStatus.class);
    private boolean reconciledWithBuildingRegistry;
    private boolean disabledStateReported;

    VillageDefenseNetworkSavedData(ResourceLocation dimensionId) {
        this(dimensionId, null);
    }

    VillageDefenseNetworkSavedData(
            ResourceLocation dimensionId,
            @Nullable Long worldSeed) {
        this(true, Objects.requireNonNull(dimensionId, "dimensionId"), worldSeed,
                Map.of(), Map.of(), null, null);
    }

    private VillageDefenseNetworkSavedData(
            boolean enabled,
            @Nullable ResourceLocation dimensionId,
            @Nullable Long worldSeed,
            Map<UUID, VillageDefenseNetwork> networks,
            Map<UUID, UUID> aliases,
            @Nullable CompoundTag unreadableRoot,
            @Nullable String disabledReason) {
        this.enabled = enabled;
        this.dimensionId = dimensionId;
        this.worldSeed = worldSeed;
        this.networks = new LinkedHashMap<>(networks);
        this.networkByBuilding = new HashMap<>();
        this.identityByBuilding = new HashMap<>();
        this.buildingByAnchor = new HashMap<>();
        this.buildingsByChunk = new HashMap<>();
        this.reviewsByGameTime = new TreeMap<>();
        this.aliases = new LinkedHashMap<>(aliases);
        this.unreadableRoot = unreadableRoot == null ? null : unreadableRoot.copy();
        this.disabledReason = disabledReason;
        for (VillageDefenseNetwork network : networks.values()) {
            indexNetwork(network);
        }
    }

    public static VillageDefenseNetworkSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ResourceLocation dimension = level.dimension().location();
        VillageDefenseNetworkSavedData data = level.getDataStorage().computeIfAbsent(
                encoded -> loadForLevel(encoded, dimension, level.getSeed()),
                () -> new VillageDefenseNetworkSavedData(dimension, level.getSeed()),
                DATA_NAME);
        VillageBuildingSavedData buildings = VillageBuildingSavedData.get(level);
        if (buildings.enabled()) {
            data.reconcileBuildingRegistry(buildings::recordsSnapshot);
        }
        data.reportDisabledStateOnce();
        return data;
    }

    public static VillageDefenseNetworkSavedData load(CompoundTag encoded) {
        return load(encoded, null, null);
    }

    static VillageDefenseNetworkSavedData loadForLevel(
            CompoundTag encoded,
            ResourceLocation expectedDimension,
            long worldSeed) {
        return load(
                encoded,
                Objects.requireNonNull(expectedDimension, "expectedDimension"),
                worldSeed);
    }

    private static VillageDefenseNetworkSavedData load(
            CompoundTag encoded,
            @Nullable ResourceLocation expectedDimension,
            @Nullable Long worldSeed) {
        Objects.requireNonNull(encoded, "encoded");
        ResourceLocation storedDimension = encoded.contains(DIMENSION_TAG, Tag.TAG_STRING)
                ? ResourceLocation.tryParse(encoded.getString(DIMENSION_TAG))
                : null;
        if (!encoded.getAllKeys().equals(ROOT_KEYS)
                || !encoded.contains(VERSION_TAG, Tag.TAG_INT)
                || encoded.getInt(VERSION_TAG) != DATA_VERSION
                || storedDimension == null
                || (expectedDimension != null
                        && !expectedDimension.equals(storedDimension))
                || !encoded.contains(NETWORKS_TAG, Tag.TAG_LIST)
                || !encoded.contains(ALIASES_TAG, Tag.TAG_LIST)) {
            return disabled(encoded, expectedDimension != null
                    ? expectedDimension
                    : storedDimension, "unsupported or malformed root schema");
        }

        Tag rawNetworks = encoded.get(NETWORKS_TAG);
        Tag rawAliases = encoded.get(ALIASES_TAG);
        if (!(rawNetworks instanceof ListTag networkList)
                || !(rawAliases instanceof ListTag aliasList)
                || (!networkList.isEmpty()
                        && networkList.getElementType() != Tag.TAG_COMPOUND)
                || (!aliasList.isEmpty()
                        && aliasList.getElementType() != Tag.TAG_COMPOUND)
                || networkList.size() > MAXIMUM_NETWORKS
                || aliasList.size() > MAXIMUM_ALIASES) {
            return disabled(encoded, storedDimension, "network or alias list is malformed");
        }

        Map<UUID, VillageDefenseNetwork> networks = new LinkedHashMap<>();
        Map<UUID, UUID> networkByBuilding = new HashMap<>();
        Map<BlockPos, UUID> buildingByAnchor = new HashMap<>();
        Map<UUID, UUID> derivedNetworkOwners = new HashMap<>();
        boolean migratedLegacyNetwork = false;
        for (Tag rawNetwork : networkList) {
            if (!(rawNetwork instanceof CompoundTag networkTag)) {
                return disabled(encoded, storedDimension,
                        "network list contains a non-compound value");
            }
            VillageDefenseNetwork network = VillageDefenseNetworkCodec.decode(networkTag);
            migratedLegacyNetwork |= networkTag.getInt(VERSION_TAG)
                    == VillageDefenseNetwork.LEGACY_SCHEMA_VERSION;
            if (network == null
                    || !storedDimension.equals(network.dimensionId())
                    || networks.put(network.networkId(), network) != null) {
                return disabled(encoded, storedDimension,
                        "a network failed strict decoding or conflicts");
            }
            for (VillageBuildingIdentity identity : network.buildings()) {
                if ((long) networkByBuilding.size() + 1L > MAXIMUM_BUILDINGS) {
                    return disabled(encoded, storedDimension,
                            "global building membership limit exceeded");
                }
                if (worldSeed != null
                        && !VillageBuildingIds.buildingId(
                                worldSeed,
                                identity.dimensionId(),
                                identity.entryId(),
                                identity.buildingType(),
                                identity.anchorPosition()).equals(identity.buildingId())) {
                    return disabled(encoded, storedDimension,
                            "a network building has a non-deterministic ID");
                }
                if (networkByBuilding.put(
                                identity.buildingId(), network.networkId()) != null
                        || buildingByAnchor.put(
                                identity.anchorPosition(), identity.buildingId()) != null) {
                    return disabled(encoded, storedDimension,
                            "a building belongs to multiple networks or anchors");
                }
                UUID derivedNetworkId = VillageDefenseNetworkIds.forBuilding(
                        identity.buildingId());
                UUID previousOwner = derivedNetworkOwners.put(
                        derivedNetworkId, network.networkId());
                if (previousOwner != null
                        && !previousOwner.equals(network.networkId())) {
                    return disabled(encoded, storedDimension,
                            "derived network identities collide across networks");
                }
            }
        }

        Map<UUID, UUID> aliases = new LinkedHashMap<>();
        for (Tag rawAlias : aliasList) {
            if (!(rawAlias instanceof CompoundTag alias)
                    || !alias.getAllKeys().equals(ALIAS_KEYS)
                    || !alias.hasUUID(ALIAS_FROM_TAG)
                    || !alias.hasUUID(ALIAS_TO_TAG)) {
                return disabled(encoded, storedDimension, "an alias failed strict decoding");
            }
            UUID from = alias.getUUID(ALIAS_FROM_TAG);
            UUID to = alias.getUUID(ALIAS_TO_TAG);
            if (NIL_UUID.equals(from)
                    || NIL_UUID.equals(to)
                    || from.equals(to)
                    || networks.containsKey(from)
                    || !networks.containsKey(to)
                    || !to.equals(derivedNetworkOwners.get(from))
                    || aliases.put(from, to) != null) {
                return disabled(encoded, storedDimension,
                        "an alias is invalid, chained, or conflicting");
            }
        }
        VillageDefenseNetworkSavedData loaded = new VillageDefenseNetworkSavedData(
                true, storedDimension, worldSeed, networks, aliases, null, null);
        if (migratedLegacyNetwork) {
            loaded.setDirty();
        }
        return loaded;
    }

    private static VillageDefenseNetworkSavedData disabled(
            CompoundTag encoded,
            @Nullable ResourceLocation dimensionId,
            String reason) {
        return new VillageDefenseNetworkSavedData(
                false, dimensionId, null, Map.of(), Map.of(), encoded, reason);
    }

    public synchronized MembershipResult claimBuilding(
            VillageBuildingIdentity identity) {
        return claimBuilding(identity, DEFAULT_LINK_RADIUS);
    }

    synchronized MembershipResult claimBuilding(
            VillageBuildingIdentity identity,
            double linkRadius) {
        VillageBuildingIdentity candidate = Objects.requireNonNull(identity, "identity");
        if (!enabled) {
            return reportClaimFailure(MembershipResult.failed(ClaimStatus.DISABLED));
        }
        if (!Objects.equals(dimensionId, candidate.dimensionId())) {
            return reportClaimFailure(MembershipResult.failed(ClaimStatus.WRONG_DIMENSION));
        }
        if (!Double.isFinite(linkRadius)
                || linkRadius < 0.0D
                || linkRadius > ABSOLUTE_MAXIMUM_LINK_RADIUS) {
            return reportClaimFailure(MembershipResult.failed(ClaimStatus.INVALID_RADIUS));
        }

        VillageBuildingIdentity existing = identityByBuilding.get(candidate.buildingId());
        if (existing != null) {
            if (!existing.equals(candidate)) {
                return reportClaimFailure(MembershipResult.failed(ClaimStatus.CONFLICT));
            }
            UUID existingNetwork = networkByBuilding.get(candidate.buildingId());
            return MembershipResult.success(
                    ClaimStatus.ALREADY_CLAIMED, existingNetwork, 0);
        }
        UUID anchoredBuilding = buildingByAnchor.get(candidate.anchorPosition());
        if (anchoredBuilding != null) {
            return reportClaimFailure(MembershipResult.failed(ClaimStatus.CONFLICT));
        }
        if (worldSeed != null
                && !VillageBuildingIds.buildingId(
                        worldSeed,
                        candidate.dimensionId(),
                        candidate.entryId(),
                        candidate.buildingType(),
                        candidate.anchorPosition()).equals(candidate.buildingId())) {
            return reportClaimFailure(MembershipResult.failed(
                    ClaimStatus.NON_DETERMINISTIC_BUILDING));
        }
        if (identityByBuilding.size() >= MAXIMUM_BUILDINGS) {
            return reportClaimFailure(MembershipResult.failed(
                    ClaimStatus.BUILDING_LIMIT_REACHED));
        }

        Set<UUID> nearbyNetworks = nearbyNetworkIds(
                candidate.anchorPosition(), linkRadius);
        if (nearbyNetworks.isEmpty()) {
            if (networks.size() >= MAXIMUM_NETWORKS) {
                return reportClaimFailure(MembershipResult.failed(
                        ClaimStatus.NETWORK_LIMIT_REACHED));
            }
            UUID networkId = VillageDefenseNetworkIds.forBuilding(candidate.buildingId());
            if (networks.containsKey(networkId) || aliases.containsKey(networkId)) {
                return reportClaimFailure(MembershipResult.failed(ClaimStatus.CONFLICT));
            }
            VillageDefenseNetwork network = VillageDefenseNetwork.forBuilding(candidate);
            networks.put(networkId, network);
            indexNetwork(network);
            setDirty();
            return MembershipResult.success(ClaimStatus.CLAIMED, networkId, 0);
        }

        List<VillageDefenseNetwork> matching = nearbyNetworks.stream()
                .map(networks::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(VillageDefenseNetwork::networkId))
                .toList();
        if (matching.isEmpty()) {
            return reportClaimFailure(MembershipResult.failed(
                    ClaimStatus.STATE_UNAVAILABLE));
        }
        int combinedBuildings = 1;
        for (VillageDefenseNetwork network : matching) {
            combinedBuildings += network.buildings().size();
        }
        if (combinedBuildings > VillageDefenseNetwork.MAXIMUM_BUILDINGS) {
            return reportClaimFailure(MembershipResult.failed(
                    ClaimStatus.MEMBER_LIMIT_REACHED));
        }

        VillageDefenseNetwork canonical = matching.get(0);
        if (matching.size() == 1) {
            VillageDefenseNetwork replacement = canonical.withBuilding(candidate);
            networks.put(replacement.networkId(), replacement);
            indexBuilding(candidate, replacement.networkId());
            setDirty();
            return MembershipResult.success(
                    ClaimStatus.CLAIMED, replacement.networkId(), 0);
        }

        int requiredNewAliases = matching.size() - 1;
        long prospectiveAliasCount = (long) aliases.size() + requiredNewAliases;
        if (prospectiveAliasCount > MAXIMUM_ALIASES) {
            return reportClaimFailure(MembershipResult.failed(
                    ClaimStatus.ALIAS_LIMIT_REACHED));
        }

        UUID canonicalId = canonical.networkId();
        ArrayList<VillageBuildingIdentity> combined = new ArrayList<>(combinedBuildings);
        Set<UUID> mergedIds = new HashSet<>();
        for (VillageDefenseNetwork network : matching) {
            mergedIds.add(network.networkId());
            combined.addAll(network.buildings());
        }
        combined.add(candidate);
        VillageDefenseStatus mergedStatus = VillageDefenseStatus.merge(
                matching.stream()
                        .map(VillageDefenseNetwork::defenseStatus)
                        .toList());
        VillageDefenseNetwork merged = new VillageDefenseNetwork(
                VillageDefenseNetwork.CURRENT_SCHEMA_VERSION,
                canonicalId,
                Objects.requireNonNull(dimensionId, "dimensionId"),
                combined,
                mergedStatus);

        redirectAliases(mergedIds, canonicalId);
        matching.forEach(this::unindexReview);
        for (UUID mergedId : mergedIds) {
            networks.remove(mergedId);
            if (!mergedId.equals(canonicalId)) {
                aliases.put(mergedId, canonicalId);
            }
        }
        networks.put(canonicalId, merged);
        indexReview(merged);
        for (VillageBuildingIdentity member : merged.buildings()) {
            networkByBuilding.put(member.buildingId(), canonicalId);
        }
        indexBuilding(candidate, canonicalId);
        setDirty();
        return MembershipResult.success(
                ClaimStatus.MERGED, canonicalId, matching.size() - 1);
    }

    public synchronized StateUpdateResult applySignal(
            UUID networkId,
            VillageDefenseSignal signal,
            long gameTime,
            VillageDefenseTimings timings) {
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(timings, "timings");
        if (!enabled) {
            return StateUpdateResult.failed(StateUpdateStatus.DISABLED);
        }
        VillageDefenseNetwork network = resolvedNetwork(networkId);
        if (network == null) {
            return StateUpdateResult.failed(StateUpdateStatus.MISSING_NETWORK);
        }
        try {
            VillageDefenseStatus replacement = VillageDefenseStateLifecycle.onSignal(
                    network.defenseStatus(), signal, gameTime, timings);
            return replaceDefenseStatus(network, replacement);
        } catch (IllegalArgumentException exception) {
            return StateUpdateResult.failed(StateUpdateStatus.INVALID_TIME);
        }
    }

    public synchronized StateUpdateResult raidEnded(
            UUID networkId,
            long gameTime,
            boolean confirmedThreatActive,
            VillageDefenseTimings timings) {
        Objects.requireNonNull(timings, "timings");
        if (!enabled) {
            return StateUpdateResult.failed(StateUpdateStatus.DISABLED);
        }
        VillageDefenseNetwork network = resolvedNetwork(networkId);
        if (network == null) {
            return StateUpdateResult.failed(StateUpdateStatus.MISSING_NETWORK);
        }
        try {
            VillageDefenseStatus replacement = VillageDefenseStateLifecycle.onRaidEnded(
                    network.defenseStatus(),
                    gameTime,
                    confirmedThreatActive,
                    timings);
            return replaceDefenseStatus(network, replacement);
        } catch (IllegalArgumentException exception) {
            return StateUpdateResult.failed(StateUpdateStatus.INVALID_TIME);
        }
    }

    /**
     * Reviews only deadline-indexed networks that are due. Work is hard
     * bounded so a large restart catch-up can continue over later ticks.
     */
    public synchronized ReviewBatch reviewDue(
            long gameTime,
            VillageDefenseTimings timings,
            int maximumReviews) {
        return reviewDue(
                gameTime,
                timings,
                maximumReviews,
                (networkId, status) -> VillageDefenseReviewFacts.QUIET);
    }

    public synchronized ReviewBatch reviewDue(
            long gameTime,
            VillageDefenseTimings timings,
            int maximumReviews,
            BiFunction<UUID, VillageDefenseStatus, VillageDefenseReviewFacts>
                    factsResolver) {
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Game time cannot be negative");
        }
        Objects.requireNonNull(timings, "timings");
        Objects.requireNonNull(factsResolver, "factsResolver");
        if (maximumReviews <= 0
                || maximumReviews > MAXIMUM_REVIEWS_PER_TICK) {
            throw new IllegalArgumentException(
                    "Defense review batch exceeds its hard bound");
        }
        if (!enabled) {
            return new ReviewBatch(0, 0, false, VillageDefenseStatus.NO_REVIEW);
        }

        int reviewed = 0;
        int changed = 0;
        while (reviewed < maximumReviews) {
            Map.Entry<Long, LinkedHashSet<UUID>> first = reviewsByGameTime.firstEntry();
            if (first == null || first.getKey() > gameTime) {
                break;
            }
            UUID networkId = first.getValue().iterator().next();
            first.getValue().remove(networkId);
            if (first.getValue().isEmpty()) {
                reviewsByGameTime.pollFirstEntry();
            }
            reviewed++;
            VillageDefenseNetwork network = networks.get(networkId);
            if (network == null
                    || network.defenseStatus().nextReviewGameTime()
                            != first.getKey()) {
                continue;
            }
            VillageDefenseReviewFacts facts = Objects.requireNonNull(
                    factsResolver.apply(networkId, network.defenseStatus()),
                    "defense review facts");
            VillageDefenseStatus replacement = VillageDefenseStateLifecycle.onReview(
                    network.defenseStatus(), facts, gameTime, timings);
            if (!replacement.equals(network.defenseStatus())) {
                networks.put(networkId, network.withDefenseStatus(replacement));
                indexReview(networks.get(networkId));
                changed++;
            } else {
                indexReview(network);
            }
        }
        if (changed > 0) {
            setDirty();
        }
        long nextReview = nextReviewGameTime();
        return new ReviewBatch(
                reviewed,
                changed,
                nextReview <= gameTime,
                nextReview);
    }

    public synchronized long nextReviewGameTime() {
        return reviewsByGameTime.isEmpty()
                ? VillageDefenseStatus.NO_REVIEW
                : reviewsByGameTime.firstKey();
    }

    public synchronized @Nullable VillageDefenseNetwork networkForBuilding(
            UUID buildingId) {
        UUID networkId = networkByBuilding.get(
                Objects.requireNonNull(buildingId, "buildingId"));
        return networkId == null ? null : networks.get(networkId);
    }

    public synchronized @Nullable VillageDefenseNetwork getNetwork(UUID networkId) {
        UUID resolved = resolveNetworkId(Objects.requireNonNull(networkId, "networkId"));
        return resolved == null ? null : networks.get(resolved);
    }

    private @Nullable VillageDefenseNetwork resolvedNetwork(UUID networkId) {
        UUID resolved = resolveNetworkId(Objects.requireNonNull(networkId, "networkId"));
        return resolved == null ? null : networks.get(resolved);
    }

    public synchronized @Nullable UUID resolveNetworkId(UUID networkId) {
        Objects.requireNonNull(networkId, "networkId");
        if (networks.containsKey(networkId)) {
            return networkId;
        }
        UUID target = aliases.get(networkId);
        return target != null && networks.containsKey(target) ? target : null;
    }

    /** Indexed nearest-network query; it performs no world access or chunk load. */
    public synchronized @Nullable VillageDefenseNetwork findNearest(
            BlockPos position,
            double radius) {
        Objects.requireNonNull(position, "position");
        if (!enabled
                || !Double.isFinite(radius)
                || radius < 0.0D
                || radius > ABSOLUTE_MAXIMUM_LINK_RADIUS) {
            return null;
        }
        double radiusSquared = radius * radius;
        VillageDefenseNetwork bestNetwork = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        UUID bestNetworkId = null;
        for (UUID buildingId : nearbyBuildingIds(position, radius)) {
            VillageBuildingIdentity identity = identityByBuilding.get(buildingId);
            UUID networkId = networkByBuilding.get(buildingId);
            if (identity == null || networkId == null) {
                continue;
            }
            double distanceSquared = distanceSquared(
                    position, identity.anchorPosition());
            if (distanceSquared > radiusSquared) {
                continue;
            }
            if (distanceSquared < bestDistanceSquared
                    || (Double.compare(distanceSquared, bestDistanceSquared) == 0
                            && (bestNetworkId == null
                                    || networkId.compareTo(bestNetworkId) < 0))) {
                bestDistanceSquared = distanceSquared;
                bestNetworkId = networkId;
                bestNetwork = networks.get(networkId);
            }
        }
        return bestNetwork;
    }

    public boolean enabled() {
        return enabled;
    }

    public synchronized int networkCount() {
        return networks.size();
    }

    public synchronized int buildingCount() {
        return identityByBuilding.size();
    }

    public synchronized int aliasCount() {
        return aliases.size();
    }

    public synchronized List<VillageDefenseNetwork> snapshot() {
        return networks.values().stream()
                .sorted(Comparator.comparing(VillageDefenseNetwork::networkId))
                .toList();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag output) {
        if (!enabled) {
            return unreadableRoot == null ? new CompoundTag() : unreadableRoot.copy();
        }
        output.putInt(VERSION_TAG, DATA_VERSION);
        output.putString(DIMENSION_TAG,
                Objects.requireNonNull(dimensionId, "dimensionId").toString());
        ListTag encodedNetworks = new ListTag();
        networks.values().stream()
                .sorted(Comparator.comparing(VillageDefenseNetwork::networkId))
                .map(VillageDefenseNetworkCodec::encode)
                .forEach(encodedNetworks::add);
        output.put(NETWORKS_TAG, encodedNetworks);
        ListTag encodedAliases = new ListTag();
        aliases.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag alias = new CompoundTag();
                    alias.putUUID(ALIAS_FROM_TAG, entry.getKey());
                    alias.putUUID(ALIAS_TO_TAG, entry.getValue());
                    encodedAliases.add(alias);
                });
        output.put(ALIASES_TAG, encodedAliases);
        return output;
    }

    synchronized void reconcileBuildingRegistry(
            Supplier<? extends Collection<VillageBuildingIdentity>> buildingIdentities) {
        if (reconciledWithBuildingRegistry || !enabled) {
            return;
        }
        reconciledWithBuildingRegistry = true;
        buildingIdentities.get().stream()
                .filter(identity -> Objects.equals(dimensionId, identity.dimensionId()))
                .sorted(Comparator.comparing(VillageBuildingIdentity::buildingId))
                .forEach(this::claimBuilding);
    }

    private Set<UUID> nearbyNetworkIds(BlockPos position, double radius) {
        Set<UUID> networkIds = new HashSet<>();
        double radiusSquared = radius * radius;
        for (UUID buildingId : nearbyBuildingIds(position, radius)) {
            VillageBuildingIdentity identity = identityByBuilding.get(buildingId);
            UUID networkId = networkByBuilding.get(buildingId);
            if (identity != null
                    && networkId != null
                    && distanceSquared(position, identity.anchorPosition())
                            <= radiusSquared) {
                networkIds.add(networkId);
            }
        }
        return networkIds;
    }

    private Set<UUID> nearbyBuildingIds(BlockPos position, double radius) {
        int minimumChunkX = Mth.floor(position.getX() - radius) >> 4;
        int maximumChunkX = Mth.floor(position.getX() + radius) >> 4;
        int minimumChunkZ = Mth.floor(position.getZ() - radius) >> 4;
        int maximumChunkZ = Mth.floor(position.getZ() + radius) >> 4;
        LinkedHashSet<UUID> buildingIds = new LinkedHashSet<>();
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                Set<UUID> bucket = buildingsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
                if (bucket != null) {
                    buildingIds.addAll(bucket);
                }
            }
        }
        return buildingIds;
    }

    private void redirectAliases(Set<UUID> mergedIds, UUID canonicalId) {
        aliases.replaceAll((source, target) -> mergedIds.contains(target)
                ? canonicalId
                : target);
    }

    private void indexNetwork(VillageDefenseNetwork network) {
        for (VillageBuildingIdentity identity : network.buildings()) {
            indexBuilding(identity, network.networkId());
        }
        indexReview(network);
    }

    private void indexReview(VillageDefenseNetwork network) {
        VillageDefenseStatus status = network.defenseStatus();
        if (!status.hasScheduledReview()) {
            return;
        }
        reviewsByGameTime.computeIfAbsent(
                        status.nextReviewGameTime(),
                        ignored -> new LinkedHashSet<>())
                .add(network.networkId());
    }

    private void unindexReview(VillageDefenseNetwork network) {
        VillageDefenseStatus status = network.defenseStatus();
        if (!status.hasScheduledReview()) {
            return;
        }
        LinkedHashSet<UUID> bucket = reviewsByGameTime.get(
                status.nextReviewGameTime());
        if (bucket == null) {
            return;
        }
        bucket.remove(network.networkId());
        if (bucket.isEmpty()) {
            reviewsByGameTime.remove(status.nextReviewGameTime());
        }
    }

    private StateUpdateResult replaceDefenseStatus(
            VillageDefenseNetwork current,
            VillageDefenseStatus replacement) {
        if (current.defenseStatus().equals(replacement)) {
            return StateUpdateResult.success(
                    StateUpdateStatus.UNCHANGED,
                    current.networkId(),
                    replacement);
        }
        unindexReview(current);
        VillageDefenseNetwork updated = current.withDefenseStatus(replacement);
        networks.put(updated.networkId(), updated);
        indexReview(updated);
        setDirty();
        return StateUpdateResult.success(
                StateUpdateStatus.APPLIED,
                updated.networkId(),
                replacement);
    }

    private void indexBuilding(VillageBuildingIdentity identity, UUID networkId) {
        identityByBuilding.put(identity.buildingId(), identity);
        networkByBuilding.put(identity.buildingId(), networkId);
        buildingByAnchor.put(identity.anchorPosition(), identity.buildingId());
        buildingsByChunk.computeIfAbsent(
                        ChunkPos.asLong(identity.anchorPosition()),
                        ignored -> new LinkedHashSet<>())
                .add(identity.buildingId());
    }

    private MembershipResult reportClaimFailure(MembershipResult result) {
        if (result.status().successful()
                || !reportedClaimFailures.add(result.status())) {
            return result;
        }
        LOGGER.warn(
                "Village defense-network membership failed once in {}: status={}",
                dimensionId,
                result.status());
        return result;
    }

    private synchronized void reportDisabledStateOnce() {
        if (enabled || disabledStateReported) {
            return;
        }
        disabledStateReported = true;
        LOGGER.error(
                "Village defense-network state is disabled in {}: {}. The original data will be preserved unchanged",
                dimensionId,
                disabledReason == null ? "unknown strict-decoding failure" : disabledReason);
    }

    private static double distanceSquared(BlockPos left, BlockPos right) {
        double deltaX = (double) left.getX() - right.getX();
        double deltaY = (double) left.getY() - right.getY();
        double deltaZ = (double) left.getZ() - right.getZ();
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    public enum ClaimStatus {
        CLAIMED(true),
        ALREADY_CLAIMED(true),
        MERGED(true),
        WRONG_DIMENSION(false),
        NON_DETERMINISTIC_BUILDING(false),
        INVALID_RADIUS(false),
        CONFLICT(false),
        NETWORK_LIMIT_REACHED(false),
        BUILDING_LIMIT_REACHED(false),
        MEMBER_LIMIT_REACHED(false),
        ALIAS_LIMIT_REACHED(false),
        STATE_UNAVAILABLE(false),
        DISABLED(false);

        private final boolean successful;

        ClaimStatus(boolean successful) {
            this.successful = successful;
        }

        public boolean successful() {
            return successful;
        }
    }

    public enum StateUpdateStatus {
        APPLIED(true),
        UNCHANGED(true),
        MISSING_NETWORK(false),
        INVALID_TIME(false),
        WRONG_THREAD(false),
        DISABLED(false);

        private final boolean successful;

        StateUpdateStatus(boolean successful) {
            this.successful = successful;
        }

        public boolean successful() {
            return successful;
        }
    }

    public record StateUpdateResult(
            StateUpdateStatus status,
            @Nullable UUID networkId,
            @Nullable VillageDefenseStatus defenseStatus) {
        public StateUpdateResult {
            Objects.requireNonNull(status, "status");
            if (status.successful()
                    != (networkId != null && defenseStatus != null)) {
                throw new IllegalArgumentException(
                        "Defense-state update result is inconsistent");
            }
        }

        public static StateUpdateResult success(
                StateUpdateStatus status,
                UUID networkId,
                VillageDefenseStatus defenseStatus) {
            return new StateUpdateResult(status, networkId, defenseStatus);
        }

        public static StateUpdateResult failed(StateUpdateStatus status) {
            return new StateUpdateResult(status, null, null);
        }
    }

    public record ReviewBatch(
            int reviewedNetworks,
            int changedNetworks,
            boolean hasMoreDue,
            long nextReviewGameTime) {
        public ReviewBatch {
            if (reviewedNetworks < 0
                    || changedNetworks < 0
                    || changedNetworks > reviewedNetworks
                    || nextReviewGameTime < 0L) {
                throw new IllegalArgumentException(
                        "Defense-state review batch is inconsistent");
            }
        }
    }

    public record MembershipResult(
            ClaimStatus status,
            @Nullable UUID networkId,
            int mergedNetworks) {
        public MembershipResult {
            Objects.requireNonNull(status, "status");
            if (status.successful() != (networkId != null)
                    || mergedNetworks < 0
                    || (status != ClaimStatus.MERGED && mergedNetworks != 0)
                    || (status == ClaimStatus.MERGED && mergedNetworks == 0)) {
                throw new IllegalArgumentException(
                        "Defense-network membership result is inconsistent");
            }
        }

        public static MembershipResult success(
                ClaimStatus status,
                UUID networkId,
                int mergedNetworks) {
            return new MembershipResult(status, networkId, mergedNetworks);
        }

        public static MembershipResult failed(ClaimStatus status) {
            return new MembershipResult(status, null, 0);
        }
    }
}
