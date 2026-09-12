package com.gamergaming.villagerguardgunners.village.population;

import com.gamergaming.villagerguardgunners.village.VillageFootprint;
import com.gamergaming.villagerguardgunners.village.VillageRecord;
import com.gamergaming.villagerguardgunners.village.VillageRecordCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict, versioned generation handoff shared by structure starts, tickets and saved data. */
public final class VillagePopulationCodec {
    public static CompoundTag encode(VillagePopulationPlan plan) {
        CompoundTag out = new CompoundTag();
        // Keep legacy-sized plans byte-compatible; larger frozen inputs require format 3.
        boolean expanded = plan.target() > VillagePopulationPlan.LEGACY_CAP
                || plan.sources().stream().anyMatch(s -> s.capacity() > VillagePopulationPlan.LEGACY_CAP);
        out.putInt("Version", expanded ? 3 : 2);
        out.putString("Dimension", plan.identity().dimension().toString());
        out.put("Layout", VillageRecordCodec.encode(new VillageRecord(plan.identity(),
                VillageFootprint.of(plan.sources().stream().map(VillagePopulationPlan.Source::piece).toList()), 1, Set.of())));
        out.putInt("Residences", plan.residences()); out.putInt("Areas", plan.areas());
        out.putInt("Defense", plan.defenseBuildings()); out.putInt("Target", plan.target());
        ListTag sources = new ListTag();
        for (var source : plan.sources()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", source.id()); tag.putBoolean("Building", source.building());
            tag.putBoolean("Residence", source.residence());
            tag.putInt("Capacity", source.capacity()); tag.putInt("Priority", source.priority());
            tag.putInt("Quota", plan.quotas().get(source.id())); sources.add(tag);
        }
        out.put("Sources", sources);
        return out;
    }
    public static VillagePopulationPlan decode(CompoundTag in, ResourceLocation dimension) {
        keys(in, "Version", "Dimension", "Layout", "Residences", "Areas", "Defense", "Target", "Sources");
        int version = integer(in, "Version");
        require((version == 1 || version == 2 || version == 3) && in.contains("Dimension", Tag.TAG_STRING)
                && dimension.toString().equals(in.getString("Dimension")) && in.contains("Layout", Tag.TAG_COMPOUND));
        if (version < 3) require(integer(in, "Target") <= VillagePopulationPlan.LEGACY_CAP);
        var record = VillageRecordCodec.decode(in.getCompound("Layout"), dimension);
        ListTag metadata = list(in, "Sources", VillagePopulationPlan.MAX_SOURCES);
        require(metadata.size() == record.footprint().pieces().size());
        var sources = new ArrayList<VillagePopulationPlan.Source>();
        var quotas = new HashMap<java.util.UUID, Integer>();
        for (int i = 0; i < metadata.size(); i++) {
            CompoundTag tag = metadata.getCompound(i);
            if (version == 1) keys(tag, "Id", "Building", "Capacity", "Priority", "Quota");
            else keys(tag, "Id", "Building", "Residence", "Capacity", "Priority", "Quota");
            require(tag.hasUUID("Id") && tag.contains("Building", Tag.TAG_BYTE)
                    && (version == 1 || tag.contains("Residence", Tag.TAG_BYTE)));
            if (version < 3) require(integer(tag, "Capacity") <= VillagePopulationPlan.LEGACY_CAP);
            var source = new VillagePopulationPlan.Source(tag.getUUID("Id"), record.footprint().pieces().get(i),
                    tag.getBoolean("Building"), version == 1 ? !tag.getBoolean("Building") : tag.getBoolean("Residence"),
                    integer(tag, "Capacity"), integer(tag, "Priority"));
            sources.add(source);
            require(quotas.put(source.id(), integer(tag, "Quota")) == null);
        }
        return new VillagePopulationPlan(record.identity(), sources, integer(in, "Residences"), integer(in, "Areas"),
                integer(in, "Defense"), integer(in, "Target"), quotas);
    }
    static int integer(CompoundTag tag, String key) { require(tag.contains(key, Tag.TAG_INT)); return tag.getInt(key); }
    static ListTag list(CompoundTag tag, String key, int limit) {
        require(tag.get(key) instanceof ListTag);
        ListTag list = (ListTag) tag.get(key);
        require(list.size() <= limit && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND));
        return list;
    }
    static void keys(CompoundTag tag, String... names) { require(tag.getAllKeys().equals(Set.of(names))); }
    static void require(boolean valid) { if (!valid) throw new IllegalArgumentException("Invalid population data"); }
    private VillagePopulationCodec() { }
}
