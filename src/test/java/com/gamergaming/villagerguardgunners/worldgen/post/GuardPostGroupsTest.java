package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardPostGroupsTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void strictFormatResolvesDocumentVariantAndMemberLoadoutInheritance() {
        GuardPostGroupPool pool = GuardPostGroups.compile(Map.of(
                id("custom"), json("""
                        {
                          "format": 1,
                          "loadout_pool": "test:root_pool",
                          "variants": [{
                            "name": "mixed",
                            "weight": 7,
                            "difficulties": ["easy", "hard"],
                            "loadout_pool": "test:variant_pool",
                            "members": [
                              {"role":"test:rifleman"},
                              {"loadout_pool":"test:member_pool","chance":35}
                            ]
                          }]
                        }
                        """))).get(id("custom"));

        GuardPostGroupVariant variant = pool.variants().get(0);
        assertEquals("mixed", variant.name());
        assertEquals(7, variant.weight());
        assertEquals(id("variant_pool"), variant.members().get(0).loadoutPool());
        assertEquals(id("rifleman"), variant.members().get(0).role());
        assertTrue(variant.members().get(0).required());
        assertEquals(id("member_pool"), variant.members().get(1).loadoutPool());
        assertEquals(35, variant.members().get(1).chancePercent());
        assertNull(variant.members().get(1).role());
    }

    @Test
    void documentInheritanceIsOrderedBoundedAndSuppliesDefaultsToChildren() {
        Map<ResourceLocation, GuardPostGroupPool> pools = GuardPostGroups.compile(Map.of(
                id("base"), json(group("test:normal", "base", "normal")),
                id("child"), json("""
                        {
                          "format":1,
                          "inherits":["test:base"],
                          "variants":[{
                            "name":"child",
                            "weight":1,
                            "difficulties":["hard"],
                            "members":[{"role":"test:support"}]
                          }]
                        }
                        """)));

        GuardPostGroupPool child = pools.get(id("child"));
        assertEquals(List.of(id("base")), child.parents());
        assertEquals(List.of("base", "child"), child.variants().stream()
                .map(GuardPostGroupVariant::name)
                .toList());
        assertEquals(id("normal"), child.variants().get(1).members().get(0).loadoutPool());
    }

    @Test
    void identicalDiamondInheritanceMergesOnceButDifferentDefinitionsConflict() {
        GuardPostGroupPool diamond = GuardPostGroups.compile(Map.of(
                id("base"), json(group("test:easy", "base", "easy")),
                id("left"), json("""
                        {"format":1,"inherits":["test:base"],"variants":[{
                         "name":"left","weight":1,"difficulties":["normal"],
                         "members":[{"loadout_pool":"test:normal"}]}]}
                        """),
                id("right"), json("""
                        {"format":1,"inherits":["test:base"],"variants":[{
                         "name":"right","weight":1,"difficulties":["hard"],
                         "members":[{"loadout_pool":"test:hard"}]}]}
                        """),
                id("diamond"), json("""
                        {"format":1,"inherits":["test:left","test:right"]}
                        """))).get(id("diamond"));

        assertEquals(List.of("base", "left", "right"), diamond.variants().stream()
                .map(GuardPostGroupVariant::name)
                .toList());
    }

    @Test
    void selectionIsDeterministicDifficultyGatedAndNeverExceedsFourMembers() {
        GuardPostGroupPool pool = GuardPostGroups.compile(Map.of(
                id("weighted"), json("""
                        {
                          "format":1,
                          "loadout_pool":"test:normal",
                          "variants":[
                            {"name":"single","weight":1,"difficulties":["easy"],
                             "members":[{"role":"test:rifleman"}]},
                            {"name":"squad","weight":3,"difficulties":["easy"],
                             "members":[
                               {"role":"test:rifleman"},
                               {"role":"test:marksman","chance":50},
                               {"role":"test:support","chance":50},
                               {"role":"test:breacher","chance":50}
                             ]},
                            {"name":"hard_only","weight":1,"difficulties":["hard"],
                             "members":[{"role":"test:support"}]}
                          ]
                        }
                        """))).get(id("weighted"));

        assertTrue(pool.select(NaturalGuardDifficulty.PEACEFUL, 10L).isEmpty());
        assertTrue(pool.select(NaturalGuardDifficulty.NORMAL, 10L).isEmpty());
        assertEquals("hard_only", pool.select(NaturalGuardDifficulty.HARD, 10L)
                .orElseThrow().variantName());

        int singleCount = 0;
        int squadCount = 0;
        boolean sawOptionalMember = false;
        boolean sawSkippedOptionalMember = false;
        for (long seed = 0; seed < 1_000; seed++) {
            GuardPostGroupSelection first = pool.select(
                    NaturalGuardDifficulty.EASY,
                    seed).orElseThrow();
            GuardPostGroupSelection second = pool.select(
                    NaturalGuardDifficulty.EASY,
                    seed).orElseThrow();
            assertEquals(first, second);
            assertTrue(first.members().size() >= 1 && first.members().size() <= 4);
            singleCount += first.variantName().equals("single") ? 1 : 0;
            squadCount += first.variantName().equals("squad") ? 1 : 0;
            if (first.variantName().equals("squad")) {
                sawOptionalMember |= first.members().size() > 1;
                sawSkippedOptionalMember |= first.members().size() < 4;
            }
        }
        assertEquals(1_000, singleCount + squadCount);
        assertTrue(squadCount > singleCount * 2 && squadCount < singleCount * 4);
        assertTrue(sawOptionalMember && sawSkippedOptionalMember);
    }

    @Test
    void cyclesMissingParentsAndVariantOrDefaultConflictsRejectTheWholeGraph() {
        assertThrows(JsonParseException.class, () -> GuardPostGroups.compile(Map.of(
                id("a"), json(inheriting("test:b")),
                id("b"), json(inheriting("test:a")))));
        assertThrows(JsonParseException.class, () -> GuardPostGroups.compile(Map.of(
                id("missing"), json(inheriting("test:not_present")))));

        assertThrows(JsonParseException.class, () -> GuardPostGroups.compile(Map.of(
                id("left"), json(group("test:easy", "same", "easy")),
                id("right"), json(group("test:easy", "same", "hard")),
                id("child"), json("""
                        {"format":1,"inherits":["test:left","test:right"]}
                        """))));
        assertThrows(JsonParseException.class, () -> GuardPostGroups.compile(Map.of(
                id("left"), json(group("test:easy", "left", "easy")),
                id("right"), json(group("test:hard", "right", "hard")),
                id("child"), json("""
                        {"format":1,"inherits":["test:left","test:right"],
                         "variants":[{"name":"child","weight":1,"difficulties":["normal"],
                         "members":[{"role":"test:rifleman"}]}]}
                        """))));
    }

    @Test
    void strictSyntaxFutureVersionsAndMemberBoundsFailClosed() {
        assertRejected("""
                {"format":2,"loadout_pool":"test:easy","variants":[]}
                """);
        assertRejected("""
                {"format":1,"loadout_pool":"test:easy","variants":[],"surprise":true}
                """);
        assertRejected("""
                {"format":1,"loadout_pool":"test:easy","variants":[{
                 "name":"bad","weight":1.5,"difficulties":["easy"],"members":[{}]}]}
                """);
        assertRejected("""
                {"format":1,"loadout_pool":"test:easy","variants":[{
                 "name":"bad","weight":1,"difficulties":["peaceful"],"members":[{}]}]}
                """);
        assertRejected("""
                {"format":1,"loadout_pool":"test:easy","variants":[{
                 "name":"bad","weight":1,"difficulties":["easy"],
                 "members":[{"chance":50},{"chance":75}]}]}
                """);
        assertRejected("""
                {"format":1,"loadout_pool":"test:easy","variants":[{
                 "name":"bad","weight":1,"difficulties":["easy"],
                 "members":[{},{},{},{},{}]}]}
                """);
        assertRejected("""
                {"format":1,"variants":[{"name":"bad","weight":1,
                 "difficulties":["easy"],"members":[{}]}]}
                """);
    }

    @Test
    void boundedReaderRejectsOversizedDeepAndDuplicateKeyDocuments() {
        String oversized = " ".repeat(GuardPostGroupFormat.MAXIMUM_DOCUMENT_CHARACTERS + 1);
        assertThrows(JsonParseException.class, () -> GuardPostGroups.readBoundedJson(
                new StringReader(oversized),
                id("oversized")));

        String deep = "[".repeat(GuardPostGroupFormat.MAXIMUM_JSON_DEPTH + 1)
                + "0"
                + "]".repeat(GuardPostGroupFormat.MAXIMUM_JSON_DEPTH + 1);
        assertThrows(JsonParseException.class, () -> GuardPostGroups.readBoundedJson(
                new StringReader(deep),
                id("deep")));
        assertThrows(JsonParseException.class, () -> GuardPostGroups.readBoundedJson(
                new StringReader("{\"format\":1,\"format\":1}"),
                id("duplicate")));
    }

    @Test
    void documentParentDepthAndResolvedVariantLimitsAreEnforced() {
        Map<ResourceLocation, JsonElement> tooMany = new LinkedHashMap<>();
        for (int index = 0; index <= GuardPostGroupFormat.MAXIMUM_DOCUMENTS; index++) {
            tooMany.put(id("group_" + index), json(group("test:easy", "v" + index, "easy")));
        }
        assertThrows(JsonParseException.class, () -> GuardPostGroups.compile(tooMany));

        assertTrue(GuardPostGroups.compile(inheritanceChain(
                GuardPostGroupFormat.MAXIMUM_INHERITANCE_DEPTH)).containsKey(
                        id("depth_" + (GuardPostGroupFormat.MAXIMUM_INHERITANCE_DEPTH - 1))));
        assertThrows(JsonParseException.class, () -> GuardPostGroups.compile(inheritanceChain(
                GuardPostGroupFormat.MAXIMUM_INHERITANCE_DEPTH + 1)));

        Map<ResourceLocation, JsonElement> parentVariants = new LinkedHashMap<>();
        StringBuilder parents = new StringBuilder();
        for (int index = 0; index <= GuardPostGroupFormat.MAXIMUM_PARENTS; index++) {
            parentVariants.put(id("parent_" + index),
                    json(group("test:easy", "variant_" + index, "easy")));
            if (index > 0) {
                parents.append(',');
            }
            parents.append('"').append("test:parent_").append(index).append('"');
        }
        parentVariants.put(id("child"), json(
                "{\"format\":1,\"inherits\":[" + parents + "]}"));
        assertThrows(JsonParseException.class, () -> GuardPostGroups.compile(parentVariants));

        assertEquals(
                GuardPostGroupFormat.MAXIMUM_VARIANTS_PER_GROUP,
                GuardPostGroups.compile(Map.of(
                        id("maximum"), json(groupWithVariants(
                                "maximum",
                                GuardPostGroupFormat.MAXIMUM_VARIANTS_PER_GROUP))))
                        .get(id("maximum")).variants().size());
        assertThrows(JsonParseException.class, () -> GuardPostGroups.compile(Map.of(
                id("too_many"), json(groupWithVariants(
                        "too_many",
                        GuardPostGroupFormat.MAXIMUM_VARIANTS_PER_GROUP + 1)))));

        assertThrows(IllegalArgumentException.class, () -> new GuardPostGroupPool(
                id("weight_overflow"),
                List.of(),
                List.of(
                        new GuardPostGroupVariant(
                                "left",
                                Integer.MAX_VALUE,
                                java.util.Set.of(NaturalGuardDifficulty.HARD),
                                List.of(new GuardPostGroupMember(id("hard"), null, 100))),
                        new GuardPostGroupVariant(
                                "right",
                                1,
                                java.util.Set.of(NaturalGuardDifficulty.HARD),
                                List.of(new GuardPostGroupMember(id("hard"), null, 100))))));
    }

    @Test
    void failedPublicationRetainsLastKnownGoodDefinitionsAndGeneration() {
        GuardPostGroups registry = new GuardPostGroups();
        registry.publish(new GuardPostGroups.PreparedResources(
                Map.of(id("good"), json(group("test:easy", "good", "easy"))),
                List.of()));
        GuardPostGroupReloadSnapshot accepted = registry.currentSnapshot();

        registry.publish(new GuardPostGroups.PreparedResources(
                Map.of(id("future"), json("""
                        {"format":99,"loadout_pool":"test:easy","variants":[]}
                        """)),
                List.of()));
        GuardPostGroupReloadSnapshot rejected = registry.currentSnapshot();

        assertTrue(accepted.lastReloadAccepted());
        assertFalse(rejected.lastReloadAccepted());
        assertEquals(accepted.generation(), rejected.generation());
        assertEquals(accepted.groups(), rejected.groups());
        assertNotNull(rejected.get(id("good")));
        assertFalse(rejected.lastErrors().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> rejected.groups().clear());
    }

    @Test
    void bundledWatchtowerPoolCoversCombatDifficultiesAndNoMoreThanFourMembers()
            throws IOException {
        Path file = PROJECT.resolve(
                "src/main/resources/data/villagerguardgunners/guard_post_groups/watchtower.json");
        GuardPostGroupPool pool = GuardPostGroups.compile(Map.of(
                GuardPostGroupDefaults.WATCHTOWER,
                json(Files.readString(file, StandardCharsets.UTF_8))))
                .get(GuardPostGroupDefaults.WATCHTOWER);

        assertEquals(6, pool.variants().size());
        assertTrue(pool.select(NaturalGuardDifficulty.PEACEFUL, 42L).isEmpty());
        for (NaturalGuardDifficulty difficulty : List.of(
                NaturalGuardDifficulty.EASY,
                NaturalGuardDifficulty.NORMAL,
                NaturalGuardDifficulty.HARD)) {
            GuardPostGroupSelection selected = pool.select(difficulty, 42L).orElseThrow();
            assertTrue(selected.members().size() >= 1 && selected.members().size() <= 4);
            assertEquals(selected, pool.select(difficulty, 42L).orElseThrow());
        }
    }

    private static void assertRejected(String source) {
        assertThrows(JsonParseException.class,
                () -> GuardPostGroups.compile(Map.of(id("rejected"), json(source))));
    }

    private static Map<ResourceLocation, JsonElement> inheritanceChain(int count) {
        Map<ResourceLocation, JsonElement> groups = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            if (index == 0) {
                groups.put(id("depth_0"), json(group("test:easy", "base", "easy")));
            } else {
                groups.put(id("depth_" + index), json(
                        inheriting("test:depth_" + (index - 1))));
            }
        }
        return groups;
    }

    private static String group(String loadout, String name, String difficulty) {
        return """
                {"format":1,"loadout_pool":"%s","variants":[{
                 "name":"%s","weight":1,"difficulties":["%s"],"members":[{}]}]}
                """.formatted(loadout, name, difficulty);
    }

    private static String inheriting(String parent) {
        return "{\"format\":1,\"inherits\":[\"" + parent + "\"]}";
    }

    private static String groupWithVariants(String prefix, int count) {
        StringBuilder variants = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                variants.append(',');
            }
            variants.append("{\"name\":\"")
                    .append(prefix).append('_').append(index)
                    .append("\",\"weight\":1,\"difficulties\":[\"easy\"],")
                    .append("\"members\":[{}]}");
        }
        return "{\"format\":1,\"loadout_pool\":\"test:easy\",\"variants\":["
                + variants + "]}";
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static JsonElement json(String source) {
        return JsonParser.parseString(source);
    }
}
