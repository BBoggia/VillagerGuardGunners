package com.gamergaming.villagerguardgunners.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension passive facts. No entity/chunk references, spawn credits, or movement ownership. */
public final class VillageRegistrySavedData extends SavedData {
    public static final String DATA_NAME = "villagerguardgunners_villages";
    public static final int VERSION = 1;
    public static final int MAX_RECORDS = 16_384;
    public static final int MAX_MEMBERS = 262_144;
    public static final int MAX_QUERY_CANDIDATES = 64;
    public static final int MAX_EVIDENCE_CHECKS = 4096;
    private static final int CELL_SHIFT = 8;
    private final ResourceLocation dimension;
    private final TreeMap<UUID, VillageRecord> records = new TreeMap<>();
    private final Map<Long, Set<UUID>> cells = new HashMap<>();
    private final Map<UUID, UUID> guards = new HashMap<>();
    private final Map<UUID, UUID> buildings = new HashMap<>();
    private final Map<UUID, Integer> guardCounts = new HashMap<>();
    private final Map<UUID, Integer> buildingCounts = new HashMap<>();
    private CompoundTag preserved;
    private String disabledReason;

    public VillageRegistrySavedData(ResourceLocation dimension) { this.dimension = java.util.Objects.requireNonNull(dimension); }

    public static VillageRegistrySavedData get(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        return level.getDataStorage().computeIfAbsent(tag -> load(tag, dimension),
                () -> new VillageRegistrySavedData(dimension), DATA_NAME);
    }

    public static VillageRegistrySavedData load(CompoundTag tag, ResourceLocation expectedDimension) {
        var data = new VillageRegistrySavedData(expectedDimension);
        try {
            VillageRecordCodec.keys(tag, Set.of("Version", "Dimension", "Records", "Guards", "Buildings"));
            VillageRecordCodec.require(tag.contains("Version", Tag.TAG_INT) && tag.getInt("Version") == VERSION
                    && tag.contains("Dimension", Tag.TAG_STRING)
                    && expectedDimension.equals(VillageRecordCodec.id(tag.getString("Dimension"))));
            for (Tag raw : VillageRecordCodec.list(tag, "Records", Tag.TAG_COMPOUND, MAX_RECORDS)) {
                VillageRecord record = VillageRecordCodec.decode((CompoundTag) raw, expectedDimension);
                VillageRecordCodec.require(data.records.put(record.identity().id(), record) == null);
                data.index(record, true);
            }
            data.readMembers(tag, "Guards", data.guards, data.guardCounts);
            data.readMembers(tag, "Buildings", data.buildings, data.buildingCounts);
            return data;
        } catch (IllegalArgumentException exception) {
            // Preserve the whole publication, including unknown fields/versions. Never partially migrate it.
            var disabled = new VillageRegistrySavedData(expectedDimension);
            disabled.preserved = tag.copy();
            disabled.disabledReason = "Unsupported, malformed, or conflicting village data";
            return disabled;
        }
    }

    private void readMembers(CompoundTag tag, String key, Map<UUID, UUID> members, Map<UUID, Integer> counts) {
        for (Tag raw : VillageRecordCodec.list(tag, key, Tag.TAG_COMPOUND, MAX_MEMBERS)) {
            CompoundTag entry = (CompoundTag) raw;
            VillageRecordCodec.keys(entry, Set.of("Member", "Village"));
            VillageRecordCodec.require(entry.hasUUID("Member") && entry.hasUUID("Village"));
            UUID member = entry.getUUID("Member"), village = entry.getUUID("Village");
            VillageRecordCodec.require(records.containsKey(village) && members.put(member, village) == null);
            counts.merge(village, 1, Integer::sum);
        }
    }

    public boolean enabled() { return preserved == null; }
    public String disabledReason() { return disabledReason == null ? "none" : disabledReason; }
    public int size() { return records.size(); }
    public VillageRecord get(UUID id) { return records.get(id); }
    public UUID guardVillage(UUID id) { return guards.get(id); }
    public UUID buildingVillage(UUID id) { return buildings.get(id); }
    public int guardCount(UUID village) { return guardCounts.getOrDefault(village, 0); }
    public int buildingCount(UUID village) { return buildingCounts.getOrDefault(village, 0); }

    /** Cursor paging avoids copying or walking all historical records for diagnostics. */
    public List<VillageRecord> page(UUID after, int limit) {
        if (limit < 1 || limit > 32) throw new IllegalArgumentException("Invalid page size");
        var source = after == null ? records : records.tailMap(after, false);
        return source.values().stream().limit(limit).toList();
    }

    public boolean observe(VillageIdentity identity, VillageFootprint footprint) {
        if (!enabled() || !dimension.equals(identity.dimension())) return false;
        VillageRecord old = records.get(identity.id());
        if (old != null && old.footprint().equals(footprint)) return true;
        if (old == null && records.size() >= MAX_RECORDS || old != null && old.revision() == Long.MAX_VALUE) return false;
        Set<BlockPos> homes = old == null ? Set.of() : old.homes().stream()
                .filter(footprint.bounds()::contains).collect(java.util.stream.Collectors.toSet());
        VillageRecord next = new VillageRecord(identity, footprint, old == null ? 1 : old.revision() + 1, homes);
        if (old != null) index(old, false);
        records.put(identity.id(), next);
        index(next, true);
        setDirty();
        return true;
    }

    /** Affiliation is sticky across unloading, following a player, or incomplete observations. */
    public boolean affiliateGuard(UUID guard, UUID village) { return affiliate(guards, guardCounts, guard, village); }
    public boolean associateBuilding(UUID building, UUID village) { return affiliate(buildings, buildingCounts, building, village); }
    private boolean affiliate(Map<UUID, UUID> members, Map<UUID, Integer> counts, UUID member, UUID village) {
        java.util.Objects.requireNonNull(member);
        java.util.Objects.requireNonNull(village);
        if (!enabled() || !records.containsKey(village)) return false;
        UUID old = members.get(member);
        if (old != null) return old.equals(village);
        if (members.size() >= MAX_MEMBERS) return false;
        members.put(member, village);
        counts.merge(village, 1, Integer::sum);
        setDirty();
        return true;
    }

    /** Horizontal planned extent; overlapping villages are returned separately. */
    public Query at(BlockPos position) {
        Set<UUID> candidates = cells.getOrDefault(cell(position.getX() >> CELL_SHIFT, position.getZ() >> CELL_SHIFT), Set.of());
        if (candidates.size() > MAX_QUERY_CANDIDATES) return new Query(List.of(), false);
        List<UUID> found = new ArrayList<>();
        for (UUID id : candidates) if (records.get(id).footprint().bounds().contains(position)) found.add(id);
        found.sort(UUID::compareTo);
        return new Query(List.copyOf(found), enabled());
    }
    /** Alarm scope is local and unambiguous; a guard's affiliation cannot alert a different village. */
    public UUID alarmVillage(BlockPos position, UUID affiliation) {
        Query query = membershipAt(position, 16, new VillageFootprint.EvidenceBudget(MAX_EVIDENCE_CHECKS));
        UUID local = query.unambiguous();
        return affiliation == null || affiliation.equals(local) ? local : null;
    }

    /** Initial association only. Existing durable members are never migrated by this query. */
    public Query membershipAt(BlockPos position) {
        return membershipAt(position, 0, new VillageFootprint.EvidenceBudget(MAX_EVIDENCE_CHECKS));
    }
    public Query membershipAt(BlockPos position, VillageFootprint.EvidenceBudget budget) {
        return membershipAt(position, 0, budget);
    }
    private Query membershipAt(BlockPos position, int margin, VillageFootprint.EvidenceBudget budget) {
        Query horizontal = at(position);
        if (!horizontal.complete()) return horizontal;
        List<UUID> found = new ArrayList<>();
        boolean complete = true;
        for (UUID id : horizontal.villages()) {
            var evidence = records.get(id).footprint().membershipEvidence(position, margin, budget);
            if (evidence == VillageFootprint.Evidence.PRESENT) found.add(id);
            if (evidence == VillageFootprint.Evidence.UNRESOLVED) complete = false;
            if (budget.remaining() == 0) return new Query(List.of(), false);
        }
        return new Query(List.copyOf(found), complete);
    }

    /** Read-only evidence diagnostic; absence can also mean a member is following a player. */
    public boolean membershipOutsideObservedRegion(UUID village, BlockPos position) {
        VillageRecord record = records.get(village);
        return record != null && !record.footprint().supportsMembership(position);
    }

    public record Query(List<UUID> villages, boolean complete) {
        public UUID unambiguous() { return complete && villages.size() == 1 ? villages.get(0) : null; }
    }

    /** Replace only a fully observed section. Missing/capped POI sections must never call this. */
    public boolean observeHomes(long section, List<BlockPos> positions) {
        if (!enabled() || positions.size() > 256) return false;
        for (BlockPos p : positions) if (SectionPos.asLong(p) != section) return false;
        var origin = SectionPos.of(section).origin();
        Set<UUID> candidates = cells.getOrDefault(cell(origin.getX() >> CELL_SHIFT, origin.getZ() >> CELL_SHIFT), Set.of());
        if (candidates.size() > MAX_QUERY_CANDIDATES) return false;
        Map<UUID, Set<BlockPos>> updates = new HashMap<>();
        for (UUID id : candidates) {
            VillageRecord old = records.get(id);
            Set<BlockPos> homes = new HashSet<>(old.homes());
            homes.removeIf(p -> SectionPos.asLong(p) == section);
            updates.put(id, homes);
        }
        var budget = new VillageFootprint.EvidenceBudget(MAX_EVIDENCE_CHECKS);
        for (BlockPos p : positions) {
            // Homes can be built in courtyards or gaps, but still need nearby elevation evidence.
            Query query = membershipAt(p, 16, budget);
            if (!query.complete()) return false; // Keep the entire previous section on uncertainty.
            UUID village = query.unambiguous();
            if (village != null) updates.get(village).add(p.immutable());
        }
        for (var entry : updates.entrySet()) {
            if (entry.getValue().size() > VillageRecord.MAX_HOMES || records.get(entry.getKey()).revision() == Long.MAX_VALUE) return false;
        }
        updates.forEach((id, homes) -> {
            VillageRecord old = records.get(id);
            if (!old.homes().equals(homes)) {
                records.put(id, new VillageRecord(old.identity(), old.footprint(), old.revision() + 1, homes));
                setDirty();
            }
        });
        return true;
    }

    private void index(VillageRecord record, boolean add) {
        UUID id = record.identity().id();
        var b = record.footprint().bounds();
        for (int x = b.minX() >> CELL_SHIFT; x <= b.maxX() >> CELL_SHIFT; x++) {
            for (int z = b.minZ() >> CELL_SHIFT; z <= b.maxZ() >> CELL_SHIFT; z++) {
                long cell = cell(x, z);
                if (add) cells.computeIfAbsent(cell, ignored -> new HashSet<>()).add(id);
                else {
                    Set<UUID> ids = cells.get(cell);
                    ids.remove(id);
                    if (ids.isEmpty()) cells.remove(cell);
                }
            }
        }
    }
    private static long cell(int x, int z) { return ChunkPos.asLong(x, z); }

    @Override
    public CompoundTag save(CompoundTag out) {
        if (!enabled()) return preserved.copy();
        out.putInt("Version", VERSION);
        out.putString("Dimension", dimension.toString());
        ListTag encoded = new ListTag();
        records.values().forEach(r -> encoded.add(VillageRecordCodec.encode(r)));
        out.put("Records", encoded);
        out.put("Guards", encodeMembers(guards));
        out.put("Buildings", encodeMembers(buildings));
        return out;
    }
    private static ListTag encodeMembers(Map<UUID, UUID> members) {
        ListTag list = new ListTag();
        members.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Member", entry.getKey());
            tag.putUUID("Village", entry.getValue());
            list.add(tag);
        });
        return list;
    }
}
