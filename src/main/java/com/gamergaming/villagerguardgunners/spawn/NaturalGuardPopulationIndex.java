package com.gamergaming.villagerguardgunners.spawn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.gamergaming.villagerguardgunners.api.GuardNaturalLoadoutAccess;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Dimension-local persistent spatial index for authentic natural gunners.
 *
 * <p>Loaded guards are still counted from the live entity index. This data only
 * supplies cap reservations for unloaded chunks, avoiding both duplicate
 * counting and recurring entity scans. Records are bucketed by chunk so the
 * one-time spawn query scales with the configured radius rather than the total
 * number of natural guards in a long-running world.</p>
 */
public final class NaturalGuardPopulationIndex extends SavedData {
    private static final String DATA_NAME = "villagerguardgunners_natural_gunners";
    private static final int DATA_VERSION = 1;
    private static final String VERSION_TAG = "Version";
    private static final String ENTRIES_TAG = "Entries";
    private static final String GUARD_ID_TAG = "GuardId";
    private static final String ISSUANCE_ID_TAG = "IssuanceId";
    private static final String X_TAG = "X";
    private static final String Y_TAG = "Y";
    private static final String Z_TAG = "Z";

    private final Map<UUID, Entry> byGuard = new HashMap<>();
    private final Map<Long, Map<UUID, Entry>> byChunk = new HashMap<>();

    public static NaturalGuardPopulationIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                NaturalGuardPopulationIndex::load,
                NaturalGuardPopulationIndex::new,
                DATA_NAME);
    }

    public static void synchronize(Guard guard) {
        if (!(guard.level() instanceof ServerLevel level)) {
            return;
        }
        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        NaturalGuardLoadoutProvenance provenance =
                access.villagerGuardGunners$getNaturalLoadoutProvenance();
        NaturalGuardPopulationIndex index = get(level);
        if (guard.isAlive()
                && !guard.isRemoved()
                && NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                        guard.getMainHandItem(),
                        provenance)) {
            index.put(
                    guard.getUUID(),
                    provenance.issuanceId(),
                    guard.getX(),
                    guard.getY(),
                    guard.getZ());
        } else {
            index.remove(guard.getUUID());
        }
    }

    public static void remove(Guard guard) {
        if (guard.level() instanceof ServerLevel level) {
            get(level).remove(guard.getUUID());
        }
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Guard guard)) {
            return;
        }

        Entity.RemovalReason reason = guard.getRemovalReason();
        GuardNaturalLoadoutAccess access = (GuardNaturalLoadoutAccess) guard;
        NaturalGuardLoadoutProvenance provenance =
                access.villagerGuardGunners$getNaturalLoadoutProvenance();
        NaturalGuardPopulationIndex index = get(level);
        if (reason != null
                && reason.shouldSave()
                && NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                        guard.getMainHandItem(),
                        provenance)) {
            index.put(
                    guard.getUUID(),
                    provenance.issuanceId(),
                    guard.getX(),
                    guard.getY(),
                    guard.getZ());
        } else {
            index.remove(guard.getUUID());
        }
    }

    public int countUnloadedWithin(
            ServerLevel level,
            double x,
            double y,
            double z,
            double radius) {
        return countWithin(x, y, z, radius, guardId -> level.getEntity(guardId) != null);
    }

    int countWithin(
            double x,
            double y,
            double z,
            double radius,
            Predicate<UUID> isLoaded) {
        if (!Double.isFinite(radius) || radius < 0.0D) {
            return 0;
        }
        double radiusSquared = radius * radius;
        int minimumChunkX = Mth.floor(x - radius) >> 4;
        int maximumChunkX = Mth.floor(x + radius) >> 4;
        int minimumChunkZ = Mth.floor(z - radius) >> 4;
        int maximumChunkZ = Mth.floor(z + radius) >> 4;
        int count = 0;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                Map<UUID, Entry> entries = byChunk.get(ChunkPos.asLong(chunkX, chunkZ));
                if (entries == null) {
                    continue;
                }
                for (Entry entry : entries.values()) {
                    if (isLoaded.test(entry.guardId())) {
                        continue;
                    }
                    double deltaX = entry.x() - x;
                    double deltaY = entry.y() - y;
                    double deltaZ = entry.z() - z;
                    if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
                            <= radiusSquared) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    void put(
            UUID guardId,
            UUID issuanceId,
            double x,
            double y,
            double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            remove(guardId);
            return;
        }
        Entry replacement = new Entry(guardId, issuanceId, x, y, z);
        Entry previous = byGuard.get(guardId);
        if (replacement.equals(previous)) {
            return;
        }
        if (previous != null) {
            removeFromChunk(previous);
        }
        byGuard.put(guardId, replacement);
        byChunk.computeIfAbsent(replacement.chunkKey(), ignored -> new HashMap<>())
                .put(guardId, replacement);
        setDirty();
    }

    void remove(UUID guardId) {
        Entry removed = byGuard.remove(guardId);
        if (removed != null) {
            removeFromChunk(removed);
            setDirty();
        }
    }

    private void removeFromChunk(Entry entry) {
        Map<UUID, Entry> entries = byChunk.get(entry.chunkKey());
        if (entries == null) {
            return;
        }
        entries.remove(entry.guardId());
        if (entries.isEmpty()) {
            byChunk.remove(entry.chunkKey());
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(VERSION_TAG, DATA_VERSION);
        ListTag entries = new ListTag();
        byGuard.values().stream()
                .sorted((left, right) -> left.guardId().compareTo(right.guardId()))
                .forEach(entry -> entries.add(entry.save()));
        tag.put(ENTRIES_TAG, entries);
        return tag;
    }

    static NaturalGuardPopulationIndex load(CompoundTag tag) {
        NaturalGuardPopulationIndex index = new NaturalGuardPopulationIndex();
        if (tag.getInt(VERSION_TAG) != DATA_VERSION
                || !tag.contains(ENTRIES_TAG, Tag.TAG_LIST)) {
            return index;
        }
        ListTag entries = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (int position = 0; position < entries.size(); position++) {
            Entry entry = Entry.load(entries.getCompound(position));
            if (entry != null) {
                index.putLoaded(entry);
            }
        }
        index.setDirty(false);
        return index;
    }

    private void putLoaded(Entry entry) {
        Entry previous = byGuard.put(entry.guardId(), entry);
        if (previous != null) {
            removeFromChunk(previous);
        }
        byChunk.computeIfAbsent(entry.chunkKey(), ignored -> new HashMap<>())
                .put(entry.guardId(), entry);
    }

    private record Entry(
            UUID guardId,
            UUID issuanceId,
            double x,
            double y,
            double z) {
        private long chunkKey() {
            return ChunkPos.asLong(chunkX(), chunkZ());
        }

        private int chunkX() {
            return Mth.floor(x) >> 4;
        }

        private int chunkZ() {
            return Mth.floor(z) >> 4;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(GUARD_ID_TAG, guardId);
            tag.putUUID(ISSUANCE_ID_TAG, issuanceId);
            tag.putDouble(X_TAG, x);
            tag.putDouble(Y_TAG, y);
            tag.putDouble(Z_TAG, z);
            return tag;
        }

        private static Entry load(CompoundTag tag) {
            if (!tag.hasUUID(GUARD_ID_TAG)
                    || !tag.hasUUID(ISSUANCE_ID_TAG)
                    || !tag.contains(X_TAG, Tag.TAG_DOUBLE)
                    || !tag.contains(Y_TAG, Tag.TAG_DOUBLE)
                    || !tag.contains(Z_TAG, Tag.TAG_DOUBLE)) {
                return null;
            }
            double x = tag.getDouble(X_TAG);
            double y = tag.getDouble(Y_TAG);
            double z = tag.getDouble(Z_TAG);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return null;
            }
            return new Entry(
                    tag.getUUID(GUARD_ID_TAG),
                    tag.getUUID(ISSUANCE_ID_TAG),
                    x,
                    y,
                    z);
        }
    }
}
