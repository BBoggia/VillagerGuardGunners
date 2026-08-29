package com.gamergaming.villagerguardgunners.spawn;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable, validated weighted pool of TaCZ gun identifiers. */
public final class NaturalGuardLoadoutPool {
    private final ResourceLocation id;
    private final List<Entry> entries;
    private final int totalWeight;

    public NaturalGuardLoadoutPool(ResourceLocation id, List<Entry> entries) {
        this.id = Objects.requireNonNull(id, "id");
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (this.entries.isEmpty()) {
            throw new IllegalArgumentException("A natural guard loadout pool cannot be empty");
        }

        Set<ResourceLocation> gunIds = new HashSet<>();
        long accumulatedWeight = 0L;
        for (Entry entry : this.entries) {
            Objects.requireNonNull(entry, "entry");
            if (!gunIds.add(entry.gunId())) {
                throw new IllegalArgumentException(
                        "Duplicate gun in natural guard loadout pool: " + entry.gunId());
            }
            accumulatedWeight += entry.weight();
            if (accumulatedWeight > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Natural guard loadout pool weight exceeds the supported limit");
            }
        }
        totalWeight = (int) accumulatedWeight;
    }

    public ResourceLocation id() {
        return id;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int totalWeight() {
        return totalWeight;
    }

    /** Selects the entry covering a pre-bounded index in {@code [0, totalWeight)}. */
    public Entry select(int weightedIndex) {
        if (weightedIndex < 0 || weightedIndex >= totalWeight) {
            throw new IllegalArgumentException(
                    "Weighted index must be within the natural loadout pool");
        }

        int remaining = weightedIndex;
        for (Entry entry : entries) {
            if (remaining < entry.weight()) {
                return entry;
            }
            remaining -= entry.weight();
        }
        throw new IllegalStateException("Validated natural loadout pool had no selected entry");
    }

    public record Entry(ResourceLocation gunId, int weight) {
        public Entry {
            gunId = Objects.requireNonNull(gunId, "gunId");
            if (weight <= 0) {
                throw new IllegalArgumentException("Natural loadout weight must be positive");
            }
        }
    }
}
