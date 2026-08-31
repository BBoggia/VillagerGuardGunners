package com.gamergaming.villagerguardgunners.spawn;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RegistrySelector;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.SelectorGroup;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable one-shot world and guard context used to match format-2 entries. */
public record NaturalGuardLoadoutMatchContext(
        ResourceLocation dimensionId,
        Set<RegistrySelector> matchingBiomeSelectors,
        Set<RegistrySelector> matchingStructureSelectors,
        NaturalGuardDifficulty difficulty,
        NaturalGuardSpawnSource spawnSource,
        boolean raidActive,
        Set<String> guardTags) {
    public NaturalGuardLoadoutMatchContext {
        dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        matchingBiomeSelectors = Set.copyOf(Objects.requireNonNull(
                matchingBiomeSelectors, "matchingBiomeSelectors"));
        matchingStructureSelectors = Set.copyOf(Objects.requireNonNull(
                matchingStructureSelectors, "matchingStructureSelectors"));
        difficulty = Objects.requireNonNull(difficulty, "difficulty");
        spawnSource = Objects.requireNonNull(spawnSource, "spawnSource");
        guardTags = Set.copyOf(Objects.requireNonNull(guardTags, "guardTags"));
    }

    /** Applies the format-2 AND/OR/exclusion contract without allocating. */
    public boolean matches(NaturalGuardLoadoutConditionSpec conditions) {
        return matchesEnvironment(conditions) && matchesGuardTags(conditions);
    }

    /** Matches every condition category that does not require a concrete guard. */
    public boolean matchesEnvironment(NaturalGuardLoadoutConditionSpec conditions) {
        Objects.requireNonNull(conditions, "conditions");
        if (!matchesExact(conditions.dimensions(), dimensionId)
                || !matchesSelectors(conditions.biomes(), matchingBiomeSelectors)
                || !matchesSelectors(conditions.structures(), matchingStructureSelectors)
                || (!conditions.difficulties().isEmpty()
                        && !conditions.difficulties().contains(difficulty))
                || (!conditions.spawnSources().isEmpty()
                        && !conditions.spawnSources().contains(spawnSource))) {
            return false;
        }

        if (conditions.raid() == NaturalGuardLoadoutConditionSpec.RaidRequirement.ACTIVE
                && !raidActive) {
            return false;
        }
        if (conditions.raid() == NaturalGuardLoadoutConditionSpec.RaidRequirement.INACTIVE
                && raidActive) {
            return false;
        }
        return true;
    }

    /** Matches required and excluded scoreboard tags against the captured guard. */
    public boolean matchesGuardTags(NaturalGuardLoadoutConditionSpec conditions) {
        Objects.requireNonNull(conditions, "conditions");
        return guardTags.containsAll(conditions.requiredGuardTags())
                && Collections.disjoint(guardTags, conditions.excludedGuardTags());
    }

    private static <T> boolean matchesExact(SelectorGroup<T> group, T value) {
        if (group.exclude().contains(value)) {
            return false;
        }
        return group.include().isEmpty() || group.include().contains(value);
    }

    private static <T> boolean matchesSelectors(SelectorGroup<T> group, Set<T> matching) {
        if (!Collections.disjoint(group.exclude(), matching)) {
            return false;
        }
        return group.include().isEmpty() || !Collections.disjoint(group.include(), matching);
    }
}
