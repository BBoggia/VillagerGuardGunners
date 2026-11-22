package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import tallestegg.guardvillagers.entities.Guard;

/** Bounded last-seen metadata, never an equipment/policy authority or a queued edit. */
public final class ServiceRoster extends SavedData {
    public static final int CAPACITY = 4096;
    private static final String KEY = "villagerguardgunners_service_roster";
    public record Entry(UUID guard, ServiceStationAssignment assignment, ResourceLocation dimension,
            BlockPos position, String name, long seen) {}
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private CompoundTag preserved;
    public static ServiceRoster get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(ServiceRoster::load, ServiceRoster::new, KEY);
    }
    public boolean readable() { return preserved == null; }
    public boolean full() { return entries.size() >= CAPACITY; }
    public List<Entry> entries() { return List.copyOf(entries.values()); }
    public Entry find(UUID id) { return entries.get(id); }
    boolean record(Entry entry) {
        if (!readable() || (!entries.containsKey(entry.guard()) && entries.size() >= CAPACITY)) return false;
        if (!entry.equals(entries.put(entry.guard(), entry))) setDirty();
        return true;
    }
    public void observe(Guard guard) {
        if (!(guard.level() instanceof ServerLevel level) || !guard.isAlive() || guard.isRemoved()) return;
        var state = GuardEquipmentServiceAccess.get(guard);
        if (!state.readable()) return; // Unreadable live settings are not proof of unassignment.
        if (state.assignment() == null) { forget(guard.getUUID()); return; }
        var previous = entries.get(guard.getUUID()); long now = level.getServer().overworld().getGameTime();
        String name = guard.getName().getString(); name = name.substring(0, Math.min(64, name.length()));
        if (previous != null && previous.assignment().equals(state.assignment())
                && previous.dimension().equals(level.dimension().location()) && previous.name().equals(name)
                && now >= previous.seen() && now - previous.seen() < 200) return;
        record(new Entry(guard.getUUID(), state.assignment(), level.dimension().location(), guard.blockPosition().immutable(), name, now));
    }
    public void forget(UUID id) { if (readable() && entries.remove(id) != null) setDirty(); }
    public static ServiceRoster load(CompoundTag tag) {
        var roster = new ServiceRoster();
        try {
            if (!tag.getAllKeys().equals(Set.of("Version", "Entries")) || !tag.contains("Version", Tag.TAG_INT)
                    || tag.getInt("Version") != 1 || !(tag.get("Entries") instanceof ListTag list)
                    || list.size() > CAPACITY) throw new IllegalArgumentException();
            for (Tag raw : list) {
                if (!(raw instanceof CompoundTag item) || !item.getAllKeys().equals(Set.of("Guard", "Assignment", "Dimension", "Position", "Name", "Seen"))
                        || !item.hasUUID("Guard") || !item.contains("Assignment", Tag.TAG_COMPOUND)
                        || !item.contains("Dimension", Tag.TAG_STRING) || !item.contains("Position", Tag.TAG_LONG)
                        || !item.contains("Name", Tag.TAG_STRING) || item.getString("Name").length() > 64
                        || !item.contains("Seen", Tag.TAG_LONG) || item.getLong("Seen") < 0) throw new IllegalArgumentException();
                String rawDimension = item.getString("Dimension"); var dimension = ResourceLocation.tryParse(rawDimension);
                if (dimension == null || !dimension.toString().equals(rawDimension) || rawDimension.length() > 96) throw new IllegalArgumentException();
                UUID id = item.getUUID("Guard");
                if (roster.entries.putIfAbsent(id, new Entry(id, ServiceStationAssignment.load(item.getCompound("Assignment")),
                        dimension, BlockPos.of(item.getLong("Position")), item.getString("Name"), item.getLong("Seen"))) != null)
                    throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException failure) { roster.entries.clear(); roster.preserved = tag.copy(); }
        return roster;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        if (preserved != null) return preserved.copy();
        tag.putInt("Version", 1); var list = new ListTag();
        for (var entry : entries.values()) {
            var item = new CompoundTag(); item.putUUID("Guard", entry.guard()); item.put("Assignment", entry.assignment().save());
            item.putString("Dimension", entry.dimension().toString()); item.putLong("Position", entry.position().asLong());
            item.putString("Name", entry.name()); item.putLong("Seen", entry.seen()); list.add(item);
        }
        tag.put("Entries", list); return tag;
    }
}
