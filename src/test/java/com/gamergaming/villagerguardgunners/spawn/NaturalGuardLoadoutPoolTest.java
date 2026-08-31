package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RegistrySelector;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.SelectorGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutPoolTest {

    @Test
    void weightedSelectionUsesExactBoundaries() {
        NaturalGuardLoadoutPool.Entry pistol = entry("pistol", 2);
        NaturalGuardLoadoutPool.Entry smg = entry("smg", 3);
        NaturalGuardLoadoutPool.Entry rifle = entry("rifle", 1);
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(
                id("test_pool"),
                List.of(pistol, smg, rifle));

        assertAll(
                () -> assertEquals(6, pool.totalWeight()),
                () -> assertEquals(pistol, pool.select(0)),
                () -> assertEquals(pistol, pool.select(1)),
                () -> assertEquals(smg, pool.select(2)),
                () -> assertEquals(smg, pool.select(4)),
                () -> assertEquals(rifle, pool.select(5)),
                () -> assertThrows(IllegalArgumentException.class, () -> pool.select(-1)),
                () -> assertThrows(IllegalArgumentException.class, () -> pool.select(6)));
    }

    @Test
    void poolDefensivelyCopiesEntriesAndExposesNoMutableCollection() {
        List<NaturalGuardLoadoutPool.Entry> source = new ArrayList<>();
        source.add(entry("pistol", 1));
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(id("immutable"), source);
        source.add(entry("rifle", 1));

        assertAll(
                () -> assertEquals(1, pool.entries().size()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> pool.entries().add(entry("smg", 1))));
    }

    @Test
    void malformedPoolsFailBeforeTheyCanReachSpawnSelection() {
        NaturalGuardLoadoutPool.Entry maximum = entry("maximum", Integer.MAX_VALUE);
        NaturalGuardLoadoutPool.Entry overflow = entry("overflow", 1);

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool(id("empty"), List.of())),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool(
                                id("duplicate"),
                                List.of(entry("same", 1), entry("same", 2)))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool(
                                id("overflow"),
                                List.of(maximum, overflow))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool.Entry(id("zero"), 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool.Entry(id("negative"), -1)));
    }

    @Test
    void formatTwoUsesNormalizedGunAndConditionIdentityAndPrecomputesLookups() {
        RegistrySelector hotBiome = RegistrySelector.tag(id("hot"));
        RegistrySelector villageStructure = RegistrySelector.tag(id("villages"));
        NaturalGuardLoadoutConditionSpec structureCondition = conditions(
                new SelectorGroup<>(Set.of(hotBiome), Set.of()),
                new SelectorGroup<>(Set.of(villageStructure), Set.of()),
                NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                Set.of());
        NaturalGuardLoadoutConditionSpec raidAndTags = conditions(
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                NaturalGuardLoadoutConditionSpec.RaidRequirement.ACTIVE,
                Set.of("elite"));
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(
                id("conditional"),
                NaturalGuardLoadoutFormat.CONDITIONAL_VERSION,
                List.of(
                        new NaturalGuardLoadoutPool.Entry(id("same"), 1),
                        new NaturalGuardLoadoutPool.Entry(id("same"), 2, structureCondition),
                        new NaturalGuardLoadoutPool.Entry(id("other"), 3, raidAndTags)));

        assertAll(
                () -> assertEquals(2, pool.format()),
                () -> assertEquals(6, pool.totalWeight()),
                () -> assertTrue(pool.hasConditionalEntries()),
                () -> assertTrue(pool.requiresBiomeLookup()),
                () -> assertTrue(pool.requiresStructureLookup()),
                () -> assertTrue(pool.requiresRaidLookup()),
                () -> assertTrue(pool.requiresGuardTags()),
                () -> assertEquals(Set.of(hotBiome), pool.biomeSelectors()),
                () -> assertEquals(Set.of(villageStructure), pool.structureSelectors()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool(
                                id("duplicate_conditions"),
                                NaturalGuardLoadoutFormat.CONDITIONAL_VERSION,
                                List.of(
                                        new NaturalGuardLoadoutPool.Entry(
                                                id("same"), 1, structureCondition),
                                        new NaturalGuardLoadoutPool.Entry(
                                                id("same"), 9, structureCondition)))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool(
                                id("legacy_conditions"),
                                NaturalGuardLoadoutFormat.LEGACY_VERSION,
                                List.of(new NaturalGuardLoadoutPool.Entry(
                                        id("same"), 1, structureCondition)))));
    }

    @Test
    void rejectsCombinedContextSelectorCountBeyondPoolBound() {
        Set<RegistrySelector> biomeSelectors = selectors("biome", 64);
        Set<RegistrySelector> structureIncludes = selectors("structure_include", 64);
        Set<RegistrySelector> structureExcludes = selectors("structure_exclude", 1);
        NaturalGuardLoadoutConditionSpec conditions = new NaturalGuardLoadoutConditionSpec(
                SelectorGroup.unrestricted(),
                new SelectorGroup<>(biomeSelectors, Set.of()),
                new SelectorGroup<>(structureIncludes, structureExcludes),
                Set.of(),
                Set.of(),
                NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                Set.of(),
                Set.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> new NaturalGuardLoadoutPool(
                        id("too_many_context_selectors"),
                        NaturalGuardLoadoutFormat.CONDITIONAL_VERSION,
                        List.of(new NaturalGuardLoadoutPool.Entry(
                                id("bounded_gun"), 1, conditions))));
    }

    @Test
    void contextualWeightingUsesTwoBoundedPassesWithoutAFilteredList() {
        NaturalGuardLoadoutConditionSpec hard = new NaturalGuardLoadoutConditionSpec(
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                Set.of(NaturalGuardDifficulty.HARD),
                Set.of(),
                NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                Set.of(),
                Set.of());
        NaturalGuardLoadoutConditionSpec easy = new NaturalGuardLoadoutConditionSpec(
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                Set.of(NaturalGuardDifficulty.EASY),
                Set.of(),
                NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY,
                Set.of(),
                Set.of());
        NaturalGuardLoadoutPool.Entry unconditional = entry("same", 2);
        NaturalGuardLoadoutPool.Entry hardSameGun =
                new NaturalGuardLoadoutPool.Entry(id("same"), 3, hard);
        NaturalGuardLoadoutPool.Entry hardRifle =
                new NaturalGuardLoadoutPool.Entry(id("rifle"), 1, hard);
        NaturalGuardLoadoutPool.Entry easyOnly =
                new NaturalGuardLoadoutPool.Entry(id("easy"), 9, easy);
        NaturalGuardLoadoutPool pool = new NaturalGuardLoadoutPool(
                id("contextual_weight"),
                NaturalGuardLoadoutFormat.CONDITIONAL_VERSION,
                List.of(unconditional, hardSameGun, hardRifle, easyOnly));
        NaturalGuardLoadoutMatchContext context = new NaturalGuardLoadoutMatchContext(
                id("overworld"),
                Set.of(),
                Set.of(),
                NaturalGuardDifficulty.HARD,
                NaturalGuardSpawnSource.STRUCTURE,
                false,
                Set.of());

        assertAll(
                () -> assertEquals(6, pool.matchingWeight(context)),
                () -> assertEquals(unconditional, pool.selectMatching(context, 0)),
                () -> assertEquals(unconditional, pool.selectMatching(context, 1)),
                () -> assertEquals(hardSameGun, pool.selectMatching(context, 2)),
                () -> assertEquals(hardSameGun, pool.selectMatching(context, 4)),
                () -> assertEquals(hardRifle, pool.selectMatching(context, 5)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> pool.selectMatching(context, -1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> pool.selectMatching(context, 6)));
    }

    private static NaturalGuardLoadoutConditionSpec conditions(
            SelectorGroup<RegistrySelector> biomes,
            SelectorGroup<RegistrySelector> structures,
            NaturalGuardLoadoutConditionSpec.RaidRequirement raid,
            Set<String> tags) {
        return new NaturalGuardLoadoutConditionSpec(
                NaturalGuardLoadoutConditionSpec.SelectorGroup.unrestricted(),
                biomes,
                structures,
                Set.of(),
                Set.of(),
                raid,
                tags,
                Set.of());
    }

    private static NaturalGuardLoadoutPool.Entry entry(String path, int weight) {
        return new NaturalGuardLoadoutPool.Entry(id(path), weight);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static Set<RegistrySelector> selectors(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> RegistrySelector.exact(id(prefix + "_" + index)))
                .collect(Collectors.toUnmodifiableSet());
    }
}
