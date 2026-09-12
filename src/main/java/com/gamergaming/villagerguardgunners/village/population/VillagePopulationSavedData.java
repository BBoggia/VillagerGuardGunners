package com.gamergaming.villagerguardgunners.village.population;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Population, immutable initial allowances and consumed credits are separate quantities. */
public final class VillagePopulationSavedData extends SavedData {
    public static final String DATA_NAME = "villagerguardgunners_population";
    public static final int MAX_VILLAGES = 16_384, MAX_RESIDENTS = 262_144;
    private final ResourceLocation dimension;
    private final Map<UUID, VillagePopulationPlan> plans = new HashMap<>();
    private final Map<UUID, Resident> residents = new HashMap<>();
    private final Map<UUID, Integer> observed = new HashMap<>();
    private final Map<UUID, Integer> living = new HashMap<>();
    private final Map<UUID, Credit> credits = new HashMap<>();
    private final Map<UUID, Integer> spent = new HashMap<>();
    private CompoundTag preserved;
    public VillagePopulationSavedData(ResourceLocation dimension) { this.dimension = dimension; }
    public static VillagePopulationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(tag, level.dimension().location()),
                () -> new VillagePopulationSavedData(level.dimension().location()), DATA_NAME);
    }
    public boolean enabled() { return preserved == null; }
    /** Validate before the runtime seals any durable journal files. */
    public boolean canFreeze(VillagePopulationPlan plan) {
        if (!enabled() || !dimension.equals(plan.identity().dimension())) return false;
        var old = plans.get(plan.identity().id());
        return old != null ? old.equals(plan) : plans.size() < MAX_VILLAGES;
    }
    public boolean freeze(VillagePopulationPlan plan) {
        if (!canFreeze(plan)) return false;
        if (!plans.containsKey(plan.identity().id())) { plans.put(plan.identity().id(), plan); setDirty(); }
        return true;
    }
    public VillagePopulationPlan plan(UUID village) { return plans.get(village); }
    public boolean observe(UUID guard, UUID village) {
        java.util.Objects.requireNonNull(guard); java.util.Objects.requireNonNull(village);
        if (!enabled()) return false;
        Resident old = residents.get(guard);
        if (old != null) return old.village().equals(village) && !old.dead();
        if (residents.size() >= MAX_RESIDENTS) return false;
        residents.put(guard, new Resident(village, false)); observed.merge(village, 1, Integer::sum); living.merge(village, 1, Integer::sum); setDirty(); return true;
    }
    public void confirmedDeath(UUID guard) {
        Resident resident = residents.get(guard);
        if (enabled() && resident != null && !resident.dead()) {
            residents.put(guard, new Resident(resident.village(), true));
            living.computeIfPresent(resident.village(), (k, count) -> Math.max(0, count - 1)); setDirty();
        }
    }
    public Resident resident(UUID guard) { return residents.get(guard); }
    public int observed(UUID village) { return observed.getOrDefault(village, 0); }
    public int living(UUID village) { return living.getOrDefault(village, 0); }
    public int spent(UUID village) { return spent.getOrDefault(village, 0); }
    public Credit credit(UUID slot) { return credits.get(slot); }
    public boolean consume(UUID village, UUID source, int ordinal, UUID guard) {
        java.util.Objects.requireNonNull(guard);
        VillagePopulationPlan plan = plans.get(village);
        if (!enabled() || plan == null) return false;
        UUID slot;
        try { slot = plan.slot(source, ordinal); } catch (IllegalArgumentException exception) { return false; }
        Credit old = credits.get(slot);
        if (old != null) return old.guard().equals(guard);
        if (credits.size() >= MAX_VILLAGES * VillagePopulationPlan.HARD_CAP) return false;
        credits.put(slot, new Credit(village, source, ordinal, guard));
        spent.merge(village, 1, Integer::sum); setDirty(); return true;
    }
    @Override public CompoundTag save(CompoundTag out) {
        if (preserved != null) return preserved.copy();
        out.putInt("Version", 1); out.putString("Dimension", dimension.toString());
        ListTag p = new ListTag(); plans.values().stream().sorted(java.util.Comparator.comparing(v -> v.identity().id()))
                .forEach(plan -> p.add(VillagePopulationCodec.encode(plan))); out.put("Plans", p);
        ListTag r = new ListTag(); residents.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Guard", e.getKey()); tag.putUUID("Village", e.getValue().village());
            tag.putBoolean("Dead", e.getValue().dead()); r.add(tag);
        }); out.put("Residents", r);
        ListTag c = new ListTag(); credits.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            Credit credit = e.getValue(); CompoundTag tag = new CompoundTag(); tag.putUUID("Village", credit.village());
            tag.putUUID("Source", credit.source()); tag.putInt("Ordinal", credit.ordinal()); tag.putUUID("Guard", credit.guard()); c.add(tag);
        }); out.put("Credits", c);
        return out;
    }
    public static VillagePopulationSavedData load(CompoundTag in, ResourceLocation dimension) {
        var data = new VillagePopulationSavedData(dimension);
        try {
            VillagePopulationCodec.keys(in, "Version", "Dimension", "Plans", "Residents", "Credits");
            VillagePopulationCodec.require(VillagePopulationCodec.integer(in, "Version") == 1
                    && in.contains("Dimension", Tag.TAG_STRING) && dimension.toString().equals(in.getString("Dimension")));
            for (Tag raw : VillagePopulationCodec.list(in, "Plans", MAX_VILLAGES)) {
                var plan = VillagePopulationCodec.decode((CompoundTag) raw, dimension);
                VillagePopulationCodec.require(!data.plans.containsKey(plan.identity().id()) && data.freeze(plan));
            }
            for (Tag raw : VillagePopulationCodec.list(in, "Residents", MAX_RESIDENTS)) {
                CompoundTag tag = (CompoundTag) raw; VillagePopulationCodec.keys(tag, "Guard", "Village", "Dead");
                VillagePopulationCodec.require(tag.hasUUID("Guard") && tag.hasUUID("Village") && tag.contains("Dead", Tag.TAG_BYTE)
                        && !data.residents.containsKey(tag.getUUID("Guard")) && data.observe(tag.getUUID("Guard"), tag.getUUID("Village")));
                if (tag.getBoolean("Dead")) data.confirmedDeath(tag.getUUID("Guard"));
            }
            for (Tag raw : VillagePopulationCodec.list(in, "Credits", MAX_VILLAGES * VillagePopulationPlan.HARD_CAP)) {
                CompoundTag tag = (CompoundTag) raw; VillagePopulationCodec.keys(tag, "Village", "Source", "Ordinal", "Guard");
                VillagePopulationCodec.require(tag.hasUUID("Village") && tag.hasUUID("Source") && tag.hasUUID("Guard"));
                var plan = data.plans.get(tag.getUUID("Village"));
                int ordinal = VillagePopulationCodec.integer(tag, "Ordinal");
                VillagePopulationCodec.require(plan != null && !data.credits.containsKey(plan.slot(tag.getUUID("Source"), ordinal))
                        && data.consume(tag.getUUID("Village"), tag.getUUID("Source"), ordinal, tag.getUUID("Guard")));
            }
        } catch (RuntimeException exception) {
            data.plans.clear(); data.residents.clear(); data.observed.clear(); data.living.clear(); data.credits.clear(); data.spent.clear();
            data.preserved = in.copy();
        }
        data.setDirty(false); return data;
    }
    public record Resident(UUID village, boolean dead) { }
    public record Credit(UUID village, UUID source, int ordinal, UUID guard) { }
}
