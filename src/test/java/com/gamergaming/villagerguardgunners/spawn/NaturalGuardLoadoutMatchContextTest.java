package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RaidRequirement;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RegistrySelector;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.SelectorGroup;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutMatchContextTest {

    @Test
    void allConditionCategoriesMatchWithOrIncludesAndRequiredTags() {
        RegistrySelector desert = RegistrySelector.exact(id("desert"));
        RegistrySelector hot = RegistrySelector.tag(id("hot"));
        RegistrySelector village = RegistrySelector.tag(id("villages"));
        NaturalGuardLoadoutMatchContext context = context(
                id("overworld"),
                Set.of(desert, hot),
                Set.of(village),
                NaturalGuardDifficulty.HARD,
                NaturalGuardSpawnSource.STRUCTURE,
                true,
                Set.of("elite", "veteran"));
        NaturalGuardLoadoutConditionSpec conditions = new NaturalGuardLoadoutConditionSpec(
                group(Set.of(id("overworld"), id("the_nether")), Set.of(id("the_end"))),
                group(Set.of(RegistrySelector.tag(id("hot"))),
                        Set.of(RegistrySelector.tag(id("frozen")))),
                group(Set.of(RegistrySelector.exact(id("outpost")), village), Set.of()),
                Set.of(NaturalGuardDifficulty.NORMAL, NaturalGuardDifficulty.HARD),
                Set.of(NaturalGuardSpawnSource.STRUCTURE),
                RaidRequirement.ACTIVE,
                Set.of("elite", "veteran"),
                Set.of("unarmed"));

        assertAll(
                () -> assertTrue(context.matches(NaturalGuardLoadoutConditionSpec.ANY)),
                () -> assertTrue(context.matches(conditions)));
    }

    @Test
    void everyExclusionVetoesAndEveryRequiredCategoryUsesAnd() {
        RegistrySelector hot = RegistrySelector.tag(id("hot"));
        RegistrySelector village = RegistrySelector.tag(id("villages"));
        NaturalGuardLoadoutMatchContext context = context(
                id("overworld"),
                Set.of(hot),
                Set.of(village),
                NaturalGuardDifficulty.HARD,
                NaturalGuardSpawnSource.STRUCTURE,
                true,
                Set.of("elite"));

        assertAll(
                () -> assertFalse(context.matches(conditions(
                        group(Set.of(), Set.of(id("overworld"))),
                        SelectorGroup.unrestricted(),
                        SelectorGroup.unrestricted(),
                        Set.of(), Set.of(), RaidRequirement.ANY, Set.of(), Set.of()))),
                () -> assertFalse(context.matches(conditions(
                        SelectorGroup.unrestricted(),
                        group(Set.of(), Set.of(hot)),
                        SelectorGroup.unrestricted(),
                        Set.of(), Set.of(), RaidRequirement.ANY, Set.of(), Set.of()))),
                () -> assertFalse(context.matches(conditions(
                        SelectorGroup.unrestricted(),
                        SelectorGroup.unrestricted(),
                        group(Set.of(), Set.of(village)),
                        Set.of(), Set.of(), RaidRequirement.ANY, Set.of(), Set.of()))),
                () -> assertFalse(context.matches(conditions(
                        SelectorGroup.unrestricted(), SelectorGroup.unrestricted(),
                        SelectorGroup.unrestricted(), Set.of(NaturalGuardDifficulty.EASY),
                        Set.of(), RaidRequirement.ANY, Set.of(), Set.of()))),
                () -> assertFalse(context.matches(conditions(
                        SelectorGroup.unrestricted(), SelectorGroup.unrestricted(),
                        SelectorGroup.unrestricted(), Set.of(),
                        Set.of(NaturalGuardSpawnSource.CONVERSION), RaidRequirement.ANY,
                        Set.of(), Set.of()))),
                () -> assertFalse(context.matches(conditions(
                        SelectorGroup.unrestricted(), SelectorGroup.unrestricted(),
                        SelectorGroup.unrestricted(), Set.of(), Set.of(),
                        RaidRequirement.INACTIVE, Set.of(), Set.of()))),
                () -> assertFalse(context.matches(conditions(
                        SelectorGroup.unrestricted(), SelectorGroup.unrestricted(),
                        SelectorGroup.unrestricted(), Set.of(), Set.of(),
                        RaidRequirement.ANY, Set.of("elite", "veteran"), Set.of()))),
                () -> assertFalse(context.matches(conditions(
                        SelectorGroup.unrestricted(), SelectorGroup.unrestricted(),
                        SelectorGroup.unrestricted(), Set.of(), Set.of(),
                        RaidRequirement.ANY, Set.of(), Set.of("elite")))));
    }

    @Test
    void capturedCollectionsAreImmutableDefensiveCopies() {
        Set<RegistrySelector> biomes = new HashSet<>();
        biomes.add(RegistrySelector.tag(id("hot")));
        Set<String> tags = new HashSet<>();
        tags.add("elite");
        NaturalGuardLoadoutMatchContext context = context(
                id("overworld"),
                biomes,
                Set.of(),
                NaturalGuardDifficulty.NORMAL,
                NaturalGuardSpawnSource.STRUCTURE,
                false,
                tags);
        biomes.clear();
        tags.clear();

        assertAll(
                () -> assertFalse(context.matchingBiomeSelectors().isEmpty()),
                () -> assertTrue(context.guardTags().contains("elite")),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> context.guardTags().add("mutation")));
    }

    private static NaturalGuardLoadoutConditionSpec conditions(
            SelectorGroup<ResourceLocation> dimensions,
            SelectorGroup<RegistrySelector> biomes,
            SelectorGroup<RegistrySelector> structures,
            Set<NaturalGuardDifficulty> difficulties,
            Set<NaturalGuardSpawnSource> sources,
            RaidRequirement raid,
            Set<String> requiredTags,
            Set<String> excludedTags) {
        return new NaturalGuardLoadoutConditionSpec(
                dimensions,
                biomes,
                structures,
                difficulties,
                sources,
                raid,
                requiredTags,
                excludedTags);
    }

    private static <T> SelectorGroup<T> group(Set<T> include, Set<T> exclude) {
        return new SelectorGroup<>(include, exclude);
    }

    private static NaturalGuardLoadoutMatchContext context(
            ResourceLocation dimension,
            Set<RegistrySelector> biomes,
            Set<RegistrySelector> structures,
            NaturalGuardDifficulty difficulty,
            NaturalGuardSpawnSource source,
            boolean raid,
            Set<String> tags) {
        return new NaturalGuardLoadoutMatchContext(
                dimension, biomes, structures, difficulty, source, raid, tags);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
