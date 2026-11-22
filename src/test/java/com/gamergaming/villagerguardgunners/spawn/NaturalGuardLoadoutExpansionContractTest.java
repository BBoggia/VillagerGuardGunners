package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.command.NaturalGuardLoadoutCommandContract;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RaidRequirement;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RegistrySelector;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.SelectorGroup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutExpansionContractTest {

    @Test
    void formatFieldNamesAndLegacyBoundariesAreExplicit() {
        assertAll(
                () -> assertEquals(1, NaturalGuardLoadoutFormat.LEGACY_VERSION),
                () -> assertEquals(2, NaturalGuardLoadoutFormat.CONDITIONAL_VERSION),
                () -> assertEquals(3, NaturalGuardLoadoutFormat.SIDEARM_VERSION),
                () -> assertEquals(4, NaturalGuardLoadoutFormat.ROLE_VERSION),
                () -> assertEquals(4, NaturalGuardLoadoutPools.FORMAT_VERSION),
                () -> assertEquals(Set.of("format", "entries"),
                        NaturalGuardLoadoutFormat.ROOT_FIELDS),
                () -> assertEquals(Set.of("gun", "weight"),
                        NaturalGuardLoadoutFormat.VERSION_1_ENTRY_FIELDS),
                () -> assertEquals(Set.of("gun", "weight", "conditions"),
                        NaturalGuardLoadoutFormat.VERSION_2_ENTRY_FIELDS),
                () -> assertEquals(Set.of("gun", "sidearm", "weight", "conditions"),
                        NaturalGuardLoadoutFormat.VERSION_3_ENTRY_FIELDS),
                () -> assertEquals(Set.of("include", "exclude"),
                        NaturalGuardLoadoutFormat.SELECTOR_GROUP_FIELDS),
                () -> assertEquals(Set.of(
                                "dimensions",
                                "biomes",
                                "structures",
                                "difficulties",
                                "spawn_sources",
                                "raid",
                                "required_guard_tags",
                                "excluded_guard_tags"),
                        NaturalGuardLoadoutFormat.CONDITION_FIELDS));
    }

    @Test
    void selectorGroupsAreImmutableAndRejectContradictions() {
        Set<ResourceLocation> included = new HashSet<>();
        included.add(id("overworld"));
        SelectorGroup<ResourceLocation> group = new SelectorGroup<>(included, Set.of());
        included.add(id("the_nether"));

        assertAll(
                () -> assertEquals(Set.of(id("overworld")), group.include()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> group.include().add(id("the_end"))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SelectorGroup<>(
                                Set.of(id("overworld")),
                                Set.of(id("overworld")))));
    }

    @Test
    void unconditionalAndContextualLookupRequirementsAreStable() {
        RegistrySelector desertTag = RegistrySelector.tag(id("is_desert"));
        RegistrySelector village = RegistrySelector.exact(id("village"));
        NaturalGuardLoadoutConditionSpec contextual = new NaturalGuardLoadoutConditionSpec(
                new SelectorGroup<>(Set.of(id("overworld")), Set.of(id("the_end"))),
                new SelectorGroup<>(Set.of(desertTag), Set.of()),
                new SelectorGroup<>(Set.of(village), Set.of()),
                Set.of(NaturalGuardDifficulty.HARD),
                Set.of(NaturalGuardSpawnSource.STRUCTURE),
                RaidRequirement.ACTIVE,
                Set.of("vgg_elite"),
                Set.of("vgg_unarmed"));

        assertAll(
                () -> assertTrue(NaturalGuardLoadoutConditionSpec.ANY.isUnconditional()),
                () -> assertFalse(contextual.isUnconditional()),
                () -> assertTrue(contextual.requiresStructureLookup()),
                () -> assertTrue(contextual.requiresRaidLookup()),
                () -> assertTrue(contextual.requiresGuardTags()),
                () -> assertEquals("#test:is_desert", desertTag.asDatapackValue()),
                () -> assertEquals("test:village", village.asDatapackValue()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> conditionsWithTags(Set.of("same"), Set.of("same"))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> conditionsWithTags(Set.of(" untrimmed"), Set.of())));
    }

    @Test
    void conditionCollectionsDefensivelyCopyTheirInputs() {
        Set<NaturalGuardDifficulty> difficulties = new HashSet<>();
        difficulties.add(NaturalGuardDifficulty.HARD);
        Set<String> requiredTags = new HashSet<>();
        requiredTags.add("vgg_elite");
        NaturalGuardLoadoutConditionSpec conditions = new NaturalGuardLoadoutConditionSpec(
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                difficulties,
                Set.of(NaturalGuardSpawnSource.STRUCTURE),
                RaidRequirement.ANY,
                requiredTags,
                Set.of());
        difficulties.add(NaturalGuardDifficulty.EASY);
        requiredTags.add("late_mutation");

        assertAll(
                () -> assertEquals(Set.of(NaturalGuardDifficulty.HARD),
                        conditions.difficulties()),
                () -> assertEquals(Set.of("vgg_elite"), conditions.requiredGuardTags()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> conditions.requiredGuardTags().add("mutation")));
    }

    @Test
    void commandPathsPermissionsAndOutputBoundsAreFrozen() {
        List<String> copiedPaths = new ArrayList<>(
                NaturalGuardLoadoutCommandContract.DOCUMENTED_PATHS);

        assertAll(
                () -> assertEquals(2,
                        NaturalGuardLoadoutCommandContract.REQUIRED_PERMISSION_LEVEL),
                () -> assertEquals(8,
                        NaturalGuardLoadoutCommandContract.ENTRIES_PER_PAGE),
                () -> assertEquals(20,
                        NaturalGuardLoadoutCommandContract.MAX_VALIDATION_ISSUES_IN_SUMMARY),
                () -> assertEquals(List.of(
                                "/vgg loadouts validate [pool]",
                                "/vgg loadouts inspect <pool> [page]",
                                "/vgg loadouts preview <difficulty> <spawn_source> [pool]"),
                        copiedPaths),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> NaturalGuardLoadoutCommandContract.DOCUMENTED_PATHS.add(
                                "/vgg loadouts mutate")));
    }

    @Test
    void diagnosticsUseOnlyTheThreeDocumentedSeverities() {
        assertEquals(
                List.of(
                        NaturalGuardLoadoutDiagnosticSeverity.INFO,
                        NaturalGuardLoadoutDiagnosticSeverity.WARNING,
                        NaturalGuardLoadoutDiagnosticSeverity.ERROR),
                List.of(NaturalGuardLoadoutDiagnosticSeverity.values()));
    }

    private static NaturalGuardLoadoutConditionSpec conditionsWithTags(
            Set<String> required,
            Set<String> excluded) {
        return new NaturalGuardLoadoutConditionSpec(
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                SelectorGroup.unrestricted(),
                Set.of(),
                Set.of(),
                RaidRequirement.ANY,
                required,
                excluded);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
