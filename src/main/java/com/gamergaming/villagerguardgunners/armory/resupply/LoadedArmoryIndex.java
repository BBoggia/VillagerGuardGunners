package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Transient, dimension-local spatial index containing only loaded Armories.
 *
 * <p>Registration follows block-entity load/unload events. Queries touch a
 * fixed number of section buckets and at most the requested number of index
 * records; they never scan blocks, entities, chunks, or the global index and
 * never create a chunk ticket.</p>
 */
public final class LoadedArmoryIndex {
    private final Map<ResourceKey<Level>, DimensionIndex> dimensions = new HashMap<>();
    private long queryCount;
    private long visitedRecordCount;
    private long returnedArmoryCount;

    public synchronized boolean register(
            ServerLevel level,
            ArmoryBlockEntity armory) {
        if (!isLiveIn(level, armory)) {
            return false;
        }
        BlockPos position = armory.getBlockPos().immutable();
        DimensionIndex dimension = dimensions.computeIfAbsent(
                level.dimension(),
                ignored -> new DimensionIndex());
        return dimension.put(position, armory);
    }

    public synchronized boolean unregister(
            ServerLevel level,
            BlockPos position,
            ArmoryBlockEntity armory) {
        if (level == null || position == null || armory == null) {
            return false;
        }
        DimensionIndex dimension = dimensions.get(level.dimension());
        if (dimension == null) {
            return false;
        }
        boolean removed = dimension.remove(position, armory);
        if (dimension.isEmpty()) {
            dimensions.remove(level.dimension());
        }
        return removed;
    }

    /**
     * Returns a deterministic, bounded nearest-section snapshot. The returned
     * block entities are revalidated as loaded before publication.
     */
    public synchronized QueryResult query(
            ServerLevel level,
            double originX,
            double originY,
            double originZ,
            double radius,
            int maximumRecords) {
        if (level == null
                || !finite(originX, originY, originZ)
                || !Double.isFinite(radius)
                || radius < ArmoryResupplyLimits.MIN_SEARCH_RADIUS
                || radius > ArmoryResupplyLimits.HARD_MAX_SEARCH_RADIUS
                || maximumRecords <= 0
                || maximumRecords > ArmoryResupplyLimits.HARD_MAX_CANDIDATE_ARMORIES) {
            return QueryResult.empty();
        }
        queryCount = incrementSaturated(queryCount);
        DimensionIndex dimension = dimensions.get(level.dimension());
        if (dimension == null) {
            return QueryResult.empty();
        }

        double radiusSquared = radius * radius;
        List<SectionBucket> buckets = dimension.bucketsWithin(
                originX,
                originY,
                originZ,
                radius,
                radiusSquared);
        List<IndexedArmory> found = new ArrayList<>(maximumRecords);
        int visitedRecords = 0;
        boolean visitLimitReached = false;
        bucketLoop:
        for (SectionBucket bucket : buckets) {
            Iterator<Map.Entry<Long, ArmoryBlockEntity>> entries =
                    bucket.armories().entrySet().iterator();
            while (entries.hasNext()) {
                if (visitedRecords >= maximumRecords) {
                    visitLimitReached = true;
                    break bucketLoop;
                }
                visitedRecords++;
                Map.Entry<Long, ArmoryBlockEntity> entry = entries.next();
                ArmoryBlockEntity armory = entry.getValue();
                BlockPos position = BlockPos.of(entry.getKey());
                if (!position.equals(armory.getBlockPos())
                        || !isCurrentlyLoaded(level, armory)) {
                    entries.remove();
                    dimension.removeIdentity(position, armory);
                    continue;
                }
                double distanceSquared = distanceSquared(
                        originX,
                        originY,
                        originZ,
                        position.getX() + 0.5D,
                        position.getY() + 0.5D,
                        position.getZ() + 0.5D);
                if (distanceSquared <= radiusSquared) {
                    found.add(new IndexedArmory(armory, position, distanceSquared));
                }
            }
        }
        dimension.removeEmptySections();
        if (dimension.isEmpty()) {
            dimensions.remove(level.dimension());
        }
        found.sort(Comparator
                .comparingDouble(IndexedArmory::distanceSquared)
                .thenComparingLong(candidate -> candidate.position().asLong()));
        visitedRecordCount = addSaturated(visitedRecordCount, visitedRecords);
        returnedArmoryCount = addSaturated(returnedArmoryCount, found.size());
        return new QueryResult(
                found.stream().map(IndexedArmory::armory).toList(),
                visitedRecords,
                visitLimitReached);
    }

    public synchronized int removeLevel(ResourceKey<Level> dimension) {
        if (dimension == null) {
            return 0;
        }
        DimensionIndex removed = dimensions.remove(dimension);
        return removed == null ? 0 : removed.size();
    }

    public synchronized int loadedCount(ResourceKey<Level> dimension) {
        DimensionIndex index = dimension == null ? null : dimensions.get(dimension);
        return index == null ? 0 : index.size();
    }

    /** Monotonic counters used by the opt-in performance evidence harness. */
    public synchronized Metrics metrics() {
        return new Metrics(queryCount, visitedRecordCount, returnedArmoryCount);
    }

    public synchronized void clear() {
        dimensions.clear();
    }

    private static boolean isLiveIn(ServerLevel level, ArmoryBlockEntity armory) {
        return level != null
                && armory != null
                && !armory.isRemoved()
                && armory.getLevel() == level;
    }

    private static boolean isCurrentlyLoaded(
            ServerLevel level,
            ArmoryBlockEntity armory) {
        if (!isLiveIn(level, armory)) {
            return false;
        }
        BlockPos position = armory.getBlockPos();
        return level.hasChunkAt(position)
                && level.getBlockEntity(position) == armory;
    }

    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    private static double distanceSquared(
            double firstX,
            double firstY,
            double firstZ,
            double secondX,
            double secondY,
            double secondZ) {
        double x = firstX - secondX;
        double y = firstY - secondY;
        double z = firstZ - secondZ;
        return x * x + y * y + z * z;
    }

    private static double sectionDistanceSquared(
            double x,
            double y,
            double z,
            int sectionX,
            int sectionY,
            int sectionZ) {
        return axisDistanceSquared(x, SectionPos.sectionToBlockCoord(sectionX))
                + axisDistanceSquared(y, SectionPos.sectionToBlockCoord(sectionY))
                + axisDistanceSquared(z, SectionPos.sectionToBlockCoord(sectionZ));
    }

    private static double axisDistanceSquared(double coordinate, int sectionMinimum) {
        double sectionMaximum = sectionMinimum + 16.0D;
        double distance = coordinate < sectionMinimum
                ? sectionMinimum - coordinate
                : coordinate > sectionMaximum ? coordinate - sectionMaximum : 0.0D;
        return distance * distance;
    }

    private static long incrementSaturated(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static long addSaturated(long value, long increment) {
        if (increment <= 0L) {
            return value;
        }
        return value > Long.MAX_VALUE - increment
                ? Long.MAX_VALUE
                : value + increment;
    }

    public record Metrics(
            long queries,
            long visitedRecords,
            long returnedArmories) {
        public Metrics {
            if (queries < 0L || visitedRecords < 0L || returnedArmories < 0L
                    || returnedArmories > visitedRecords) {
                throw new IllegalArgumentException("Loaded Armory metrics are contradictory");
            }
        }
    }

    public record QueryResult(
            List<ArmoryBlockEntity> armories,
            int visitedRecords,
            boolean visitLimitReached) {
        public QueryResult {
            armories = List.copyOf(Objects.requireNonNull(armories, "armories"));
            if (visitedRecords < armories.size()
                    || visitedRecords > ArmoryResupplyLimits.HARD_MAX_CANDIDATE_ARMORIES) {
                throw new IllegalArgumentException("Loaded Armory query exceeded its bounds");
            }
        }

        private static QueryResult empty() {
            return new QueryResult(List.of(), 0, false);
        }
    }

    private static final class DimensionIndex {
        private final Map<Long, NavigableMap<Long, ArmoryBlockEntity>> bySection =
                new HashMap<>();
        private final Map<Long, Long> sectionByPosition = new HashMap<>();

        private boolean put(BlockPos position, ArmoryBlockEntity armory) {
            long positionKey = position.asLong();
            long sectionKey = sectionKey(position);
            Long previousSection = sectionByPosition.put(positionKey, sectionKey);
            ArmoryBlockEntity previous = null;
            if (previousSection != null) {
                NavigableMap<Long, ArmoryBlockEntity> previousEntries =
                        bySection.get(previousSection);
                if (previousEntries != null) {
                    previous = previousEntries.remove(positionKey);
                    if (previousEntries.isEmpty()) {
                        bySection.remove(previousSection);
                    }
                }
            }
            bySection.computeIfAbsent(sectionKey, ignored -> new TreeMap<>())
                    .put(positionKey, armory);
            return previous != armory;
        }

        private boolean remove(BlockPos position, ArmoryBlockEntity armory) {
            long positionKey = position.asLong();
            Long sectionKey = sectionByPosition.get(positionKey);
            if (sectionKey == null) {
                return false;
            }
            NavigableMap<Long, ArmoryBlockEntity> entries = bySection.get(sectionKey);
            if (entries == null || entries.get(positionKey) != armory) {
                return false;
            }
            entries.remove(positionKey);
            sectionByPosition.remove(positionKey);
            if (entries.isEmpty()) {
                bySection.remove(sectionKey);
            }
            return true;
        }

        private void removeIdentity(BlockPos position, ArmoryBlockEntity armory) {
            long positionKey = position.asLong();
            if (sectionByPosition.containsKey(positionKey)) {
                Long sectionKey = sectionByPosition.get(positionKey);
                NavigableMap<Long, ArmoryBlockEntity> entries = bySection.get(sectionKey);
                if (entries == null || entries.get(positionKey) == armory) {
                    sectionByPosition.remove(positionKey);
                }
            }
        }

        private List<SectionBucket> bucketsWithin(
                double x,
                double y,
                double z,
                double radius,
                double radiusSquared) {
            int minimumX = Mth.floor(x - radius) >> 4;
            int maximumX = Mth.floor(x + radius) >> 4;
            int minimumY = Mth.floor(y - radius) >> 4;
            int maximumY = Mth.floor(y + radius) >> 4;
            int minimumZ = Mth.floor(z - radius) >> 4;
            int maximumZ = Mth.floor(z + radius) >> 4;
            List<SectionBucket> buckets = new ArrayList<>();
            for (int sectionX = minimumX; sectionX <= maximumX; sectionX++) {
                for (int sectionY = minimumY; sectionY <= maximumY; sectionY++) {
                    for (int sectionZ = minimumZ; sectionZ <= maximumZ; sectionZ++) {
                        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
                        NavigableMap<Long, ArmoryBlockEntity> entries = bySection.get(key);
                        if (entries == null || entries.isEmpty()) {
                            continue;
                        }
                        double minimumDistance = sectionDistanceSquared(
                                x, y, z, sectionX, sectionY, sectionZ);
                        if (minimumDistance <= radiusSquared) {
                            buckets.add(new SectionBucket(
                                    sectionX,
                                    sectionY,
                                    sectionZ,
                                    minimumDistance,
                                    entries));
                        }
                    }
                }
            }
            buckets.sort(Comparator
                    .comparingDouble(SectionBucket::minimumDistanceSquared)
                    .thenComparingInt(SectionBucket::sectionX)
                    .thenComparingInt(SectionBucket::sectionY)
                    .thenComparingInt(SectionBucket::sectionZ));
            return buckets;
        }

        private void removeEmptySections() {
            bySection.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }

        private int size() {
            return sectionByPosition.size();
        }

        private boolean isEmpty() {
            return sectionByPosition.isEmpty();
        }

        private static long sectionKey(BlockPos position) {
            return SectionPos.asLong(
                    SectionPos.blockToSectionCoord(position.getX()),
                    SectionPos.blockToSectionCoord(position.getY()),
                    SectionPos.blockToSectionCoord(position.getZ()));
        }
    }

    private record SectionBucket(
            int sectionX,
            int sectionY,
            int sectionZ,
            double minimumDistanceSquared,
            NavigableMap<Long, ArmoryBlockEntity> armories) {
    }

    private record IndexedArmory(
            ArmoryBlockEntity armory,
            BlockPos position,
            double distanceSquared) {
    }
}
