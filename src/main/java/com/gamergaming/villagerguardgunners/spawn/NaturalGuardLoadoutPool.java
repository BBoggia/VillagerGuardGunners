package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RegistrySelector;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Immutable, validated weighted pool of TaCZ gun identifiers. */
public final class NaturalGuardLoadoutPool {
    private final ResourceLocation id;
    private final int format;
    private final List<Entry> entries;
    private final int totalWeight;
    private final boolean conditional;
    private final boolean requiresStructureLookup;
    private final boolean requiresRaidLookup;
    private final boolean requiresGuardTags;
    private final Set<RegistrySelector> biomeSelectors;
    private final Set<RegistrySelector> structureSelectors;

    public NaturalGuardLoadoutPool(ResourceLocation id, List<Entry> entries) {
        this(id, NaturalGuardLoadoutFormat.LEGACY_VERSION, entries);
    }

    public NaturalGuardLoadoutPool(ResourceLocation id, int format, List<Entry> entries) {
        this.id = Objects.requireNonNull(id, "id");
        if (format != NaturalGuardLoadoutFormat.LEGACY_VERSION
                && format != NaturalGuardLoadoutFormat.CONDITIONAL_VERSION
                && format != NaturalGuardLoadoutFormat.SIDEARM_VERSION
                && format != NaturalGuardLoadoutFormat.ROLE_VERSION) {
            throw new IllegalArgumentException("Unsupported natural guard loadout format");
        }
        this.format = format;
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (this.entries.isEmpty()) {
            throw new IllegalArgumentException("A natural guard loadout pool cannot be empty");
        }
        if (this.entries.size() > NaturalGuardLoadoutFormat.MAX_ENTRIES_PER_POOL) {
            throw new IllegalArgumentException(
                    "Natural guard loadout pool exceeds the maximum of "
                            + NaturalGuardLoadoutFormat.MAX_ENTRIES_PER_POOL + " entries");
        }

        Set<Object> entryIdentities = new HashSet<>();
        long accumulatedWeight = 0L;
        boolean foundConditional = false;
        boolean foundStructureLookup = false;
        boolean foundRaidLookup = false;
        boolean foundGuardTags = false;
        Set<RegistrySelector> foundBiomeSelectors = new HashSet<>();
        Set<RegistrySelector> foundStructureSelectors = new HashSet<>();
        for (Entry entry : this.entries) {
            Objects.requireNonNull(entry, "entry");
            if (format == NaturalGuardLoadoutFormat.LEGACY_VERSION
                    && !entry.conditions().isUnconditional()) {
                throw new IllegalArgumentException(
                        "Format-1 natural loadouts cannot contain conditions");
            }
            if (format < NaturalGuardLoadoutFormat.SIDEARM_VERSION
                    && entry.sidearmId() != null) {
                throw new IllegalArgumentException(
                        "Natural loadout sidearms require format 3");
            }
            if (format < NaturalGuardLoadoutFormat.ROLE_VERSION
                    && entry.roleId() != null) {
                throw new IllegalArgumentException(
                        "Natural loadout roles require format 4");
            }
            Object identity = switch (format) {
                case NaturalGuardLoadoutFormat.LEGACY_VERSION -> entry.gunId();
                case NaturalGuardLoadoutFormat.CONDITIONAL_VERSION ->
                        new ConditionalEntryIdentity(entry.gunId(), entry.conditions());
                case NaturalGuardLoadoutFormat.SIDEARM_VERSION -> new SidearmEntryIdentity(
                        entry.gunId(), entry.sidearmId(), entry.conditions());
                default -> new RoleEntryIdentity(
                        entry.gunId(), entry.sidearmId(), entry.roleId(), entry.conditions());
            };
            if (!entryIdentities.add(identity)) {
                throw new IllegalArgumentException(
                        "Duplicate gun/condition entry in natural guard loadout pool: "
                                + entry.gunId());
            }
            foundConditional |= !entry.conditions().isUnconditional();
            foundStructureLookup |= entry.conditions().requiresStructureLookup();
            foundRaidLookup |= entry.conditions().requiresRaidLookup();
            foundGuardTags |= entry.conditions().requiresGuardTags();
            foundBiomeSelectors.addAll(entry.conditions().biomes().include());
            foundBiomeSelectors.addAll(entry.conditions().biomes().exclude());
            foundStructureSelectors.addAll(entry.conditions().structures().include());
            foundStructureSelectors.addAll(entry.conditions().structures().exclude());
            accumulatedWeight += entry.weight();
            if (accumulatedWeight > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Natural guard loadout pool weight exceeds the supported limit");
            }
        }
        int contextSelectorCount = foundBiomeSelectors.size() + foundStructureSelectors.size();
        if (contextSelectorCount > NaturalGuardLoadoutFormat.MAX_CONTEXT_SELECTORS_PER_POOL) {
            throw new IllegalArgumentException(
                    "Natural guard loadout pool contains " + contextSelectorCount
                            + " distinct biome/structure selectors; the maximum is "
                            + NaturalGuardLoadoutFormat.MAX_CONTEXT_SELECTORS_PER_POOL);
        }
        totalWeight = (int) accumulatedWeight;
        conditional = foundConditional;
        requiresStructureLookup = foundStructureLookup;
        requiresRaidLookup = foundRaidLookup;
        requiresGuardTags = foundGuardTags;
        biomeSelectors = Set.copyOf(foundBiomeSelectors);
        structureSelectors = Set.copyOf(foundStructureSelectors);
    }

    public ResourceLocation id() {
        return id;
    }

    public int format() {
        return format;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int totalWeight() {
        return totalWeight;
    }

    public boolean hasConditionalEntries() {
        return conditional;
    }

    public boolean requiresStructureLookup() {
        return requiresStructureLookup;
    }

    public boolean requiresBiomeLookup() {
        return !biomeSelectors.isEmpty();
    }

    public boolean requiresRaidLookup() {
        return requiresRaidLookup;
    }

    public boolean requiresGuardTags() {
        return requiresGuardTags;
    }

    public Set<RegistrySelector> biomeSelectors() {
        return biomeSelectors;
    }

    public Set<RegistrySelector> structureSelectors() {
        return structureSelectors;
    }

    /** Returns the total weight of entries matching one immutable spawn context. */
    public int matchingWeight(NaturalGuardLoadoutMatchContext context) {
        Objects.requireNonNull(context, "context");
        int matchingWeight = 0;
        for (Entry entry : entries) {
            if (context.matches(entry.conditions())) {
                matchingWeight += entry.weight();
            }
        }
        return matchingWeight;
    }

    /** Selects among matching entries using an index bounded by {@link #matchingWeight}. */
    public Entry selectMatching(NaturalGuardLoadoutMatchContext context, int weightedIndex) {
        Objects.requireNonNull(context, "context");
        if (weightedIndex < 0) {
            throw new IllegalArgumentException("Weighted index cannot be negative");
        }
        int remaining = weightedIndex;
        for (Entry entry : entries) {
            if (!context.matches(entry.conditions())) {
                continue;
            }
            if (remaining < entry.weight()) {
                return entry;
            }
            remaining -= entry.weight();
        }
        throw new IllegalArgumentException(
                "Weighted index must be within the matching natural loadout entries");
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

    public record Entry(
            ResourceLocation gunId,
            @Nullable ResourceLocation sidearmId,
            @Nullable ResourceLocation roleId,
            int weight,
            NaturalGuardLoadoutConditionSpec conditions) {
        public Entry(ResourceLocation gunId, int weight) {
            this(gunId, null, null, weight, NaturalGuardLoadoutConditionSpec.ANY);
        }

        public Entry(
                ResourceLocation gunId,
                int weight,
                NaturalGuardLoadoutConditionSpec conditions) {
            this(gunId, null, null, weight, conditions);
        }

        public Entry(ResourceLocation gunId, ResourceLocation sidearmId, int weight) {
            this(gunId, sidearmId, null, weight, NaturalGuardLoadoutConditionSpec.ANY);
        }

        public Entry(
                ResourceLocation gunId,
                @Nullable ResourceLocation sidearmId,
                int weight,
                NaturalGuardLoadoutConditionSpec conditions) {
            this(gunId, sidearmId, null, weight, conditions);
        }

        public Entry {
            gunId = Objects.requireNonNull(gunId, "gunId");
            conditions = Objects.requireNonNull(conditions, "conditions");
            if (weight <= 0) {
                throw new IllegalArgumentException("Natural loadout weight must be positive");
            }
        }
    }

    private record ConditionalEntryIdentity(
            ResourceLocation gunId,
            NaturalGuardLoadoutConditionSpec conditions) {
    }

    private record SidearmEntryIdentity(
            ResourceLocation gunId,
            @Nullable ResourceLocation sidearmId,
            NaturalGuardLoadoutConditionSpec conditions) {
    }

    private record RoleEntryIdentity(
            ResourceLocation gunId,
            @Nullable ResourceLocation sidearmId,
            @Nullable ResourceLocation roleId,
            NaturalGuardLoadoutConditionSpec conditions) {
    }
}
