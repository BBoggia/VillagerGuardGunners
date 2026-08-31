package com.gamergaming.villagerguardgunners.spawn;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable, normalized contract for a format-2 entry's optional conditions.
 *
 * <p>Different condition categories are combined with AND. Values inside an
 * include set are combined with OR, while any matching exclusion vetoes the
 * entry. Empty include and exclusion sets leave that category unrestricted.</p>
 */
public record NaturalGuardLoadoutConditionSpec(
        SelectorGroup<ResourceLocation> dimensions,
        SelectorGroup<RegistrySelector> biomes,
        SelectorGroup<RegistrySelector> structures,
        Set<NaturalGuardDifficulty> difficulties,
        Set<NaturalGuardSpawnSource> spawnSources,
        RaidRequirement raid,
        Set<String> requiredGuardTags,
        Set<String> excludedGuardTags) {
    public static final NaturalGuardLoadoutConditionSpec ANY =
            new NaturalGuardLoadoutConditionSpec(
                    SelectorGroup.unrestricted(),
                    SelectorGroup.unrestricted(),
                    SelectorGroup.unrestricted(),
                    Set.of(),
                    Set.of(),
                    RaidRequirement.ANY,
                    Set.of(),
                    Set.of());

    public NaturalGuardLoadoutConditionSpec {
        dimensions = Objects.requireNonNull(dimensions, "dimensions");
        biomes = Objects.requireNonNull(biomes, "biomes");
        structures = Objects.requireNonNull(structures, "structures");
        difficulties = Set.copyOf(Objects.requireNonNull(difficulties, "difficulties"));
        spawnSources = Set.copyOf(Objects.requireNonNull(spawnSources, "spawnSources"));
        raid = Objects.requireNonNull(raid, "raid");
        requiredGuardTags = normalizedGuardTags(requiredGuardTags, "requiredGuardTags");
        excludedGuardTags = normalizedGuardTags(excludedGuardTags, "excludedGuardTags");

        Set<String> contradictoryTags = new HashSet<>(requiredGuardTags);
        contradictoryTags.retainAll(excludedGuardTags);
        if (!contradictoryTags.isEmpty()) {
            throw new IllegalArgumentException(
                    "Guard tags cannot be both required and excluded");
        }
    }

    public boolean isUnconditional() {
        return dimensions.isUnrestricted()
                && biomes.isUnrestricted()
                && structures.isUnrestricted()
                && difficulties.isEmpty()
                && spawnSources.isEmpty()
                && raid == RaidRequirement.ANY
                && requiredGuardTags.isEmpty()
                && excludedGuardTags.isEmpty();
    }

    public boolean requiresStructureLookup() {
        return !structures.isUnrestricted();
    }

    public boolean requiresRaidLookup() {
        return raid != RaidRequirement.ANY;
    }

    public boolean requiresGuardTags() {
        return !requiredGuardTags.isEmpty() || !excludedGuardTags.isEmpty();
    }

    private static Set<String> normalizedGuardTags(Set<String> tags, String label) {
        Set<String> copied = Set.copyOf(Objects.requireNonNull(tags, label));
        requireBounded(copied, label);
        for (String tag : copied) {
            if (tag.isBlank() || !tag.equals(tag.trim())) {
                throw new IllegalArgumentException(
                        label + " must contain nonblank, trimmed scoreboard tags");
            }
        }
        return copied;
    }

    private static void requireBounded(Set<?> values, String label) {
        if (values.size() > NaturalGuardLoadoutFormat.MAX_VALUES_PER_CONDITION_COLLECTION) {
            throw new IllegalArgumentException(
                    label + " exceeds the maximum of "
                            + NaturalGuardLoadoutFormat.MAX_VALUES_PER_CONDITION_COLLECTION
                            + " values");
        }
    }

    /** Exact registry ID or registry tag selector used by biome and structure conditions. */
    public record RegistrySelector(ResourceLocation id, boolean tag) {
        public RegistrySelector {
            id = Objects.requireNonNull(id, "id");
        }

        public static RegistrySelector exact(ResourceLocation id) {
            return new RegistrySelector(id, false);
        }

        public static RegistrySelector tag(ResourceLocation id) {
            return new RegistrySelector(id, true);
        }

        public String asDatapackValue() {
            return tag ? "#" + id : id.toString();
        }
    }

    /** Include/exclude selector group with deterministic exclusion precedence. */
    public record SelectorGroup<T>(Set<T> include, Set<T> exclude) {
        public SelectorGroup {
            include = Set.copyOf(Objects.requireNonNull(include, "include"));
            exclude = Set.copyOf(Objects.requireNonNull(exclude, "exclude"));
            requireBounded(include, "include");
            requireBounded(exclude, "exclude");
            Set<T> contradictory = new HashSet<>(include);
            contradictory.retainAll(exclude);
            if (!contradictory.isEmpty()) {
                throw new IllegalArgumentException(
                        "A condition selector cannot be included and excluded");
            }
        }

        public boolean isUnrestricted() {
            return include.isEmpty() && exclude.isEmpty();
        }

        public static <T> SelectorGroup<T> unrestricted() {
            return new SelectorGroup<>(Set.of(), Set.of());
        }
    }

    public enum RaidRequirement {
        ANY,
        ACTIVE,
        INACTIVE
    }
}
