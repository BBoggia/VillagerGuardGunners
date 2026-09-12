package com.gamergaming.villagerguardgunners.ai.tactics;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Bounded value-only memory. Neither observations nor this store retain live entities. */
public final class ThreatMemory {
    public static final int CAPACITY = 16;
    public static final int LIFETIME_TICKS = 40;
    private final Map<UUID, Observation> entries = new LinkedHashMap<>();

    public boolean observe(Observation observation) { return observe(observation, false); }

    public boolean observe(Observation observation, boolean priority) {
        if (priority && !entries.containsKey(observation.id()) && entries.size() >= CAPACITY) {
            Observation oldest = null;
            for (Observation entry : entries.values()) {
                if (oldest == null || entry.observedAt() < oldest.observedAt()) oldest = entry;
            }
            if (oldest != null) entries.remove(oldest.id());
        }
        if (!entries.containsKey(observation.id()) && entries.size() >= CAPACITY) return false;
        entries.put(observation.id(), observation);
        return true;
    }

    public void expire(long now, String dimension) {
        entries.values().removeIf(o -> !o.dimension().equals(dimension)
                || now < o.observedAt() || now - o.observedAt() >= LIFETIME_TICKS);
    }

    public void remove(UUID id) { entries.remove(id); }
    public Observation get(UUID id) { return entries.get(id); }
    public Collection<Observation> observations() { return java.util.List.copyOf(entries.values()); }
    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }

    public record Observation(UUID id, String dimension, Vec3 position, Vec3 velocity,
            long observedAt, long attackEvidenceAt, boolean ranged, boolean explosive,
            boolean unknownAttack) {
        public double confidence(long now) {
            long age = now - observedAt;
            return age < 0 ? 0 : Math.max(0, 1.0 - (double) age / LIFETIME_TICKS);
        }
    }
}
