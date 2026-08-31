package com.gamergaming.villagerguardgunners.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Complete immutable result of one natural-loadout datapack publication.
 *
 * <p>The pool map, deterministic publication summaries, and rejected-document
 * diagnostics are copied together so readers can retain one generation across
 * a concurrent {@code /reload}. TaCZ compatibility is intentionally absent:
 * gun indexes have an independent reload lifecycle and are analyzed only after
 * datapack publication.</p>
 */
public final class NaturalGuardLoadoutReloadSnapshot {
    private static final Comparator<ResourceLocation> ID_ORDER =
            Comparator.comparing(ResourceLocation::toString);
    private static final NaturalGuardLoadoutReloadSnapshot EMPTY = create(
            0L,
            Map.of(),
            List.of());

    private final long generation;
    private final Map<ResourceLocation, NaturalGuardLoadoutPool> pools;
    private final List<PublishedPool> publishedPools;
    private final List<RejectedPool> rejectedPools;

    private NaturalGuardLoadoutReloadSnapshot(
            long generation,
            Map<ResourceLocation, NaturalGuardLoadoutPool> pools,
            List<PublishedPool> publishedPools,
            List<RejectedPool> rejectedPools) {
        this.generation = generation;
        this.pools = pools;
        this.publishedPools = publishedPools;
        this.rejectedPools = rejectedPools;
    }

    /** Empty generation used before the first server datapack publication. */
    public static NaturalGuardLoadoutReloadSnapshot empty() {
        return EMPTY;
    }

    static NaturalGuardLoadoutReloadSnapshot create(
            long generation,
            Map<ResourceLocation, NaturalGuardLoadoutPool> pools,
            List<RejectedPool> rejectedPools) {
        if (generation < 0L) {
            throw new IllegalArgumentException("Reload generation cannot be negative");
        }
        Objects.requireNonNull(pools, "pools");
        Objects.requireNonNull(rejectedPools, "rejectedPools");

        List<Map.Entry<ResourceLocation, NaturalGuardLoadoutPool>> orderedPools =
                new ArrayList<>(pools.entrySet());
        orderedPools.sort(Map.Entry.comparingByKey(ID_ORDER));
        Map<ResourceLocation, NaturalGuardLoadoutPool> copiedPools = new LinkedHashMap<>();
        List<PublishedPool> publications = new ArrayList<>(orderedPools.size());
        for (Map.Entry<ResourceLocation, NaturalGuardLoadoutPool> entry : orderedPools) {
            ResourceLocation id = Objects.requireNonNull(entry.getKey(), "pool ID");
            NaturalGuardLoadoutPool pool =
                    Objects.requireNonNull(entry.getValue(), "pool");
            if (!id.equals(pool.id())) {
                throw new IllegalArgumentException(
                        "Published pool key does not match its pool ID: " + id);
            }
            copiedPools.put(id, pool);
            publications.add(PublishedPool.from(pool));
        }

        List<RejectedPool> copiedRejections = new ArrayList<>(rejectedPools.size());
        for (RejectedPool rejected : rejectedPools) {
            copiedRejections.add(Objects.requireNonNull(rejected, "rejected pool"));
        }
        copiedRejections.sort(Comparator.comparing(RejectedPool::id, ID_ORDER));
        for (int index = 1; index < copiedRejections.size(); index++) {
            if (copiedRejections.get(index - 1).id().equals(copiedRejections.get(index).id())) {
                throw new IllegalArgumentException(
                        "Reload snapshot contains duplicate rejected pool IDs");
            }
        }
        for (RejectedPool rejected : copiedRejections) {
            if (copiedPools.containsKey(rejected.id())) {
                throw new IllegalArgumentException(
                        "A pool cannot be both published and rejected: " + rejected.id());
            }
        }

        return new NaturalGuardLoadoutReloadSnapshot(
                generation,
                Collections.unmodifiableMap(copiedPools),
                List.copyOf(publications),
                List.copyOf(copiedRejections));
    }

    public long generation() {
        return generation;
    }

    /** Deterministically ordered immutable map of usable pools. */
    public Map<ResourceLocation, NaturalGuardLoadoutPool> pools() {
        return pools;
    }

    /** Compact deterministic metadata for every usable pool. */
    public List<PublishedPool> publishedPools() {
        return publishedPools;
    }

    /** Deterministic diagnostics for documents omitted from this generation. */
    public List<RejectedPool> rejectedPools() {
        return rejectedPools;
    }

    public @Nullable NaturalGuardLoadoutPool get(ResourceLocation poolId) {
        return pools.get(Objects.requireNonNull(poolId, "poolId"));
    }

    public int documentCount() {
        return publishedPools.size() + rejectedPools.size();
    }

    public boolean hasRejections() {
        return !rejectedPools.isEmpty();
    }

    /** Stable metadata that does not expose a pool's entry collection. */
    public record PublishedPool(
            ResourceLocation id,
            int format,
            int entryCount,
            int totalWeight) {
        public PublishedPool {
            id = Objects.requireNonNull(id, "id");
            if (format <= 0 || entryCount <= 0 || totalWeight <= 0) {
                throw new IllegalArgumentException("Published pool metadata must be positive");
            }
        }

        private static PublishedPool from(NaturalGuardLoadoutPool pool) {
            return new PublishedPool(
                    pool.id(),
                    pool.format(),
                    pool.entries().size(),
                    pool.totalWeight());
        }
    }

    public record RejectedPool(ResourceLocation id, String reason) {
        public RejectedPool {
            id = Objects.requireNonNull(id, "id");
            reason = Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()
                    || reason.length() > 512
                    || reason.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException(
                        "Rejected pool reason must be nonblank, single-line, and at most 512 characters");
            }
        }
    }
}
