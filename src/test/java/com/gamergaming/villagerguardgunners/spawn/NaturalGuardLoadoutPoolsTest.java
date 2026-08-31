package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutPoolsTest {

    @Test
    void parsesStrictWeightedPoolFormat() {
        ResourceLocation poolId = id("example", "guards/standard");
        NaturalGuardLoadoutPool pool = NaturalGuardLoadoutPools.parsePool(
                poolId,
                json("""
                        {
                          "format": 1,
                          "entries": [
                            {"gun": "tacz:glock_17", "weight": 3},
                            {"gun": "example:service_rifle", "weight": 2}
                          ]
                        }
                        """));

        assertAll(
                () -> assertEquals(poolId, pool.id()),
                () -> assertEquals(NaturalGuardLoadoutFormat.LEGACY_VERSION, pool.format()),
                () -> assertEquals(2, pool.entries().size()),
                () -> assertEquals(5, pool.totalWeight()),
                () -> assertEquals(id("tacz", "glock_17"), pool.select(0).gunId()),
                () -> assertEquals(id("tacz", "glock_17"), pool.select(2).gunId()),
                () -> assertEquals(id("example", "service_rifle"), pool.select(3).gunId()));
    }

    @Test
    void rejectsAmbiguousOrMalformedPoolDocuments() {
        ResourceLocation poolId = id("test", "invalid");

        assertAll(
                () -> assertThrows(
                        JsonParseException.class,
                        () -> NaturalGuardLoadoutPools.parsePool(poolId, json("[]"))),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, "{\"entries\": []}")),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, "{\"format\": 5, \"entries\": []}")),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId,
                                "{\"format\": 1, \"entries\": [], \"replace\": true}")),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, "{\"format\": 1, \"entries\": {}}")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> parse(poolId, "{\"format\": 1, \"entries\": []}")));
    }

    @Test
    void parsesEveryFormatTwoConditionAndNormalizesRepeatedGunEntries() {
        ResourceLocation poolId = id("example", "conditional");
        NaturalGuardLoadoutPool pool = parse(poolId, """
                {
                  "format": 2,
                  "entries": [
                    {"gun": "tacz:glock_17", "weight": 4},
                    {
                      "gun": "tacz:glock_17",
                      "weight": 2,
                      "conditions": {
                        "dimensions": {
                          "include": ["minecraft:overworld"],
                          "exclude": ["minecraft:the_nether"]
                        },
                        "biomes": {
                          "include": ["#example:is_desert"],
                          "exclude": ["example:desert_oasis"]
                        },
                        "structures": {
                          "include": ["#example:villages"]
                        },
                        "difficulties": ["hard"],
                        "spawn_sources": ["structure"],
                        "raid": "active",
                        "required_guard_tags": ["vgg_elite"],
                        "excluded_guard_tags": ["vgg_unarmed"]
                      }
                    }
                  ]
                }
                """);
        NaturalGuardLoadoutConditionSpec conditions = pool.entries().get(1).conditions();

        assertAll(
                () -> assertEquals(2, pool.format()),
                () -> assertEquals(6, pool.totalWeight()),
                () -> assertEquals(2, pool.entries().size()),
                () -> assertTrue(pool.entries().get(0).conditions().isUnconditional()),
                () -> assertEquals(
                        Set.of(id("minecraft", "overworld")),
                        conditions.dimensions().include()),
                () -> assertEquals(
                        Set.of(id("minecraft", "the_nether")),
                        conditions.dimensions().exclude()),
                () -> assertEquals(
                        Set.of(NaturalGuardLoadoutConditionSpec.RegistrySelector.tag(
                                id("example", "is_desert"))),
                        conditions.biomes().include()),
                () -> assertEquals(
                        Set.of(NaturalGuardLoadoutConditionSpec.RegistrySelector.exact(
                                id("example", "desert_oasis"))),
                        conditions.biomes().exclude()),
                () -> assertEquals(
                        Set.of(NaturalGuardLoadoutConditionSpec.RegistrySelector.tag(
                                id("example", "villages"))),
                        conditions.structures().include()),
                () -> assertEquals(Set.of(NaturalGuardDifficulty.HARD),
                        conditions.difficulties()),
                () -> assertEquals(Set.of(NaturalGuardSpawnSource.STRUCTURE),
                        conditions.spawnSources()),
                () -> assertEquals(
                        NaturalGuardLoadoutConditionSpec.RaidRequirement.ACTIVE,
                        conditions.raid()),
                () -> assertEquals(Set.of("vgg_elite"), conditions.requiredGuardTags()),
                () -> assertEquals(Set.of("vgg_unarmed"), conditions.excludedGuardTags()),
                () -> assertTrue(pool.hasConditionalEntries()),
                () -> assertTrue(pool.requiresStructureLookup()),
                () -> assertTrue(pool.requiresRaidLookup()),
                () -> assertTrue(pool.requiresGuardTags()));
    }

    @Test
    void formatThreeAddsOnlyOptionalStrictSidearms() {
        ResourceLocation poolId = id("example", "sidearms");
        NaturalGuardLoadoutPool pool = parse(poolId, """
                {
                  "format": 3,
                  "entries": [
                    {
                      "gun": "tacz:m4a1",
                      "sidearm": "tacz:glock_17",
                      "weight": 2,
                      "conditions": {"difficulties": ["hard"]}
                    },
                    {
                      "gun": "tacz:m4a1",
                      "sidearm": "tacz:m1911",
                      "weight": 1,
                      "conditions": {"difficulties": ["hard"]}
                    },
                    {"gun": "tacz:ak47", "weight": 1}
                  ]
                }
                """);

        assertAll(
                () -> assertEquals(NaturalGuardLoadoutFormat.SIDEARM_VERSION, pool.format()),
                () -> assertEquals(id("tacz", "glock_17"),
                        pool.entries().get(0).sidearmId()),
                () -> assertEquals(id("tacz", "m1911"),
                        pool.entries().get(1).sidearmId()),
                () -> assertNull(pool.entries().get(2).sidearmId()),
                () -> assertTrue(pool.hasConditionalEntries()),
                () -> assertThrows(JsonParseException.class,
                        () -> parse(poolId, """
                                {"format": 2, "entries": [
                                  {"gun":"tacz:m4a1","sidearm":"tacz:glock_17","weight":1}
                                ]}
                                """)),
                () -> assertThrows(JsonParseException.class,
                        () -> parse(poolId, """
                                {"format": 3, "entries": [
                                  {"gun":"tacz:m4a1","sidearm":"GLOCK","weight":1}
                                ]}
                                """)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> parse(poolId, """
                                {"format": 3, "entries": [
                                  {"gun":"tacz:m4a1","sidearm":"tacz:glock_17","weight":1},
                                  {"gun":"tacz:m4a1","sidearm":"tacz:glock_17","weight":2}
                                ]}
                                """)));
    }

    @Test
    void formatTwoRejectsMalformedConditionsAndAmbiguousDuplicates() {
        ResourceLocation poolId = id("test", "bad_conditions");

        assertAll(
                () -> assertFormatTwoRejected(poolId, "\"conditions\": []"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"unknown\": true}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"dimensions\": {}}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"dimensions\": {\"include\": []}}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"dimensions\": {\"include\": [\"#minecraft:overworld\"]}}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"biomes\": {\"include\": [\"#\"]}}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"biomes\": {\"include\": [\"example:a\", \"example:a\"]}}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"biomes\": {\"include\": [\"example:a\"], \"exclude\": [\"example:a\"]}}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"difficulties\": [\"HARD\"]}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"spawn_sources\": [\"village\"]}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"raid\": true}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"raid\": \"sometimes\"}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"required_guard_tags\": [\" tag\"]}"),
                () -> assertFormatTwoRejected(poolId,
                        "\"conditions\": {\"required_guard_tags\": [\"same\"], \"excluded_guard_tags\": [\"same\"]}"),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> parse(poolId, """
                                {"format": 2, "entries": [
                                  {"gun": "tacz:glock_17", "weight": 1},
                                  {"gun": "tacz:glock_17", "weight": 2, "conditions": {}}
                                ]}
                                """)),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, """
                                {"format": 1, "entries": [{
                                  "gun": "tacz:glock_17",
                                  "weight": 1,
                                  "conditions": {}
                                }]}
                                """)));
    }

    @Test
    void rejectsMalformedEntriesBeforePublication() {
        ResourceLocation poolId = id("test", "bad_entries");

        assertAll(
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, document("1"))),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, document("{\"gun\": \"tacz:glock_17\", \"weight\": 1, \"chance\": 1}"))),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, document("{\"weight\": 1}"))),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, document("{\"gun\": \"glock_17\", \"weight\": 1}"))),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, document("{\"gun\": \"TaCZ:glock_17\", \"weight\": 1}"))),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, document("{\"gun\": \"tacz:glock_17\", \"weight\": 0}"))),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, document("{\"gun\": \"tacz:glock_17\", \"weight\": 1.5}"))),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, document("{\"gun\": \"tacz:glock_17\", \"weight\": \"1\"}"))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> parse(poolId, """
                                {"format": 1, "entries": [
                                  {"gun": "tacz:glock_17", "weight": 1},
                                  {"gun": "tacz:glock_17", "weight": 2}
                                ]}
                                """)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> parse(poolId, """
                                {"format": 1, "entries": [
                                  {"gun": "tacz:glock_17", "weight": 2147483647},
                                  {"gun": "tacz:m1911", "weight": 1}
                                ]}
                                """)));
    }

    @Test
    void rejectsPoolsAndConditionCollectionsBeyondRuntimeBounds() {
        ResourceLocation poolId = id("test", "oversized");
        String oversizedEntries = IntStream.range(
                        0,
                        NaturalGuardLoadoutFormat.MAX_ENTRIES_PER_POOL + 1)
                .mapToObj(index -> "{\"gun\":\"test:gun_" + index
                        + "\",\"weight\":1}")
                .collect(Collectors.joining(","));
        String oversizedDimensions = IntStream.range(
                        0,
                        NaturalGuardLoadoutFormat.MAX_VALUES_PER_CONDITION_COLLECTION + 1)
                .mapToObj(index -> "\"test:dimension_" + index + "\"")
                .collect(Collectors.joining(","));
        List<NaturalGuardLoadoutPool.Entry> programmaticEntries = IntStream.range(
                        0,
                        NaturalGuardLoadoutFormat.MAX_ENTRIES_PER_POOL + 1)
                .mapToObj(index -> new NaturalGuardLoadoutPool.Entry(
                        id("test", "programmatic_" + index), 1))
                .toList();

        assertAll(
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, "{\"format\":1,\"entries\":["
                                + oversizedEntries + "]}")),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> parse(poolId, "{\"format\":2,\"entries\":[{"
                                + "\"gun\":\"tacz:glock_17\",\"weight\":1,"
                                + "\"conditions\":{\"dimensions\":{\"include\":["
                                + oversizedDimensions + "]}}}]}")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutPool(poolId, programmaticEntries)));
    }

    @Test
    void semanticFailuresAreIsolatedAndReportedDeterministically() {
        Map<ResourceLocation, JsonElement> prepared = new LinkedHashMap<>();
        prepared.put(id("test", "z_invalid"), json("{\"format\": 1, \"entries\": []}"));
        prepared.put(id("test", "valid"), json(document(
                "{\"gun\": \"tacz:glock_17\", \"weight\": 1}")));
        prepared.put(id("test", "a_invalid"),
                json("{\"format\": 9, \"entries\": [], \"unsafe\\u0085field\": true}"));

        NaturalGuardLoadoutPools.LoadResult result = NaturalGuardLoadoutPools.parseAll(prepared);

        assertAll(
                () -> assertEquals(1, result.pools().size()),
                () -> assertTrue(result.pools().containsKey(id("test", "valid"))),
                () -> assertEquals(2, result.rejected().size()),
                () -> assertEquals(id("test", "a_invalid"), result.rejected().get(0).id()),
                () -> assertTrue(result.rejected().get(0).reason().chars()
                        .noneMatch(Character::isISOControl)),
                () -> assertEquals(id("test", "z_invalid"), result.rejected().get(1).id()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> result.pools().clear()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> result.rejected().clear()));
    }

    @Test
    void reloadPublishesOneCompleteImmutableReplacementSnapshot() {
        NaturalGuardLoadoutPools listener = new NaturalGuardLoadoutPools();
        ResourceLocation first = id("test", "first");
        ResourceLocation second = id("test", "second");
        ResourceLocation rejected = id("test", "rejected");
        listener.apply(
                prepared(Map.of(first, json(document(
                        "{\"gun\": \"tacz:glock_17\", \"weight\": 1}")))),
                null,
                null);
        Map<ResourceLocation, NaturalGuardLoadoutPool> oldSnapshot = listener.loadedSnapshot();
        NaturalGuardLoadoutReloadSnapshot oldPublication =
                listener.currentReloadSnapshot();

        listener.apply(
                prepared(Map.of(
                        second,
                        json(document(
                                "{\"gun\": \"tacz:m1911\", \"weight\": 2}")),
                        rejected,
                        json("{\"format\": 1, \"entries\": []}"))),
                null,
                null);
        NaturalGuardLoadoutReloadSnapshot current = listener.currentReloadSnapshot();

        assertAll(
                () -> assertEquals(1, oldSnapshot.size()),
                () -> assertTrue(oldSnapshot.containsKey(first)),
                () -> assertEquals(1L, oldPublication.generation()),
                () -> assertTrue(oldPublication.pools().containsKey(first)),
                () -> assertEquals(1, oldPublication.documentCount()),
                () -> assertNull(listener.getLoaded(first)),
                () -> assertEquals(second, listener.getLoaded(second).id()),
                () -> assertEquals(2L, current.generation()),
                () -> assertEquals(2, current.documentCount()),
                () -> assertEquals(
                        new NaturalGuardLoadoutReloadSnapshot.PublishedPool(
                                second,
                                1,
                                1,
                                2),
                        current.publishedPools().get(0)),
                () -> assertEquals(rejected, current.rejectedPools().get(0).id()),
                () -> assertTrue(current.hasRejections()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> listener.loadedSnapshot().clear()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> current.rejectedPools().clear()));
    }

    @Test
    void publicationSnapshotDefensivelyCopiesAndDeterministicallyOrdersMetadata() {
        ResourceLocation alpha = id("test", "alpha");
        ResourceLocation zulu = id("test", "zulu");
        ResourceLocation rejectedAlpha = id("bad", "alpha");
        ResourceLocation rejectedZulu = id("bad", "zulu");
        Map<ResourceLocation, NaturalGuardLoadoutPool> mutablePools = new LinkedHashMap<>();
        mutablePools.put(zulu, pool(zulu, "tacz:m1911", 3));
        mutablePools.put(alpha, pool(alpha, "tacz:glock_17", 2));
        List<NaturalGuardLoadoutReloadSnapshot.RejectedPool> mutableRejections =
                new ArrayList<>();
        mutableRejections.add(new NaturalGuardLoadoutReloadSnapshot.RejectedPool(
                rejectedZulu,
                "zulu reason"));
        mutableRejections.add(new NaturalGuardLoadoutReloadSnapshot.RejectedPool(
                rejectedAlpha,
                "alpha reason"));

        NaturalGuardLoadoutReloadSnapshot snapshot =
                NaturalGuardLoadoutReloadSnapshot.create(
                        7L,
                        mutablePools,
                        mutableRejections);
        mutablePools.clear();
        mutableRejections.clear();

        assertAll(
                () -> assertEquals(7L, snapshot.generation()),
                () -> assertEquals(List.of(alpha, zulu),
                        snapshot.publishedPools().stream()
                                .map(NaturalGuardLoadoutReloadSnapshot.PublishedPool::id)
                                .toList()),
                () -> assertEquals(List.of(rejectedAlpha, rejectedZulu),
                        snapshot.rejectedPools().stream()
                                .map(NaturalGuardLoadoutReloadSnapshot.RejectedPool::id)
                                .toList()),
                () -> assertEquals(4, snapshot.documentCount()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> snapshot.pools().clear()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> snapshot.publishedPools().clear()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> NaturalGuardLoadoutReloadSnapshot.create(
                                -1L,
                                Map.of(),
                                List.of())),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new NaturalGuardLoadoutReloadSnapshot.RejectedPool(
                                rejectedAlpha,
                                "unsafe\nreason")));
    }

    @Test
    void preparedSyntaxFailuresAreRetainedWithSemanticFailures() {
        NaturalGuardLoadoutPools listener = new NaturalGuardLoadoutPools();
        ResourceLocation valid = id("test", "valid");
        ResourceLocation syntaxFailure = id("test", "syntax_failure");
        ResourceLocation semanticFailure = id("test", "semantic_failure");
        NaturalGuardLoadoutPools.PreparedResources prepared =
                new NaturalGuardLoadoutPools.PreparedResources(
                        Map.of(
                                valid,
                                json(document(
                                        "{\"gun\": \"tacz:glock_17\", \"weight\": 1}")),
                                semanticFailure,
                                json("{\"format\": 1, \"entries\": []}")),
                        List.of(new NaturalGuardLoadoutReloadSnapshot.RejectedPool(
                                syntaxFailure,
                                "Malformed JSON")));

        listener.apply(prepared, null, null);
        NaturalGuardLoadoutReloadSnapshot snapshot = listener.currentReloadSnapshot();

        assertAll(
                () -> assertEquals(3, snapshot.documentCount()),
                () -> assertEquals(1, snapshot.publishedPools().size()),
                () -> assertEquals(List.of(semanticFailure, syntaxFailure),
                        snapshot.rejectedPools().stream()
                                .map(NaturalGuardLoadoutReloadSnapshot.RejectedPool::id)
                                .toList()));
    }

    @Test
    void bundledDifficultyPoolsMatchTheConservativeProgression() throws IOException {
        NaturalGuardLoadoutPool easy = bundled("easy");
        NaturalGuardLoadoutPool normal = bundled("normal");
        NaturalGuardLoadoutPool hard = bundled("hard");

        assertAll(
                () -> assertEquals(NaturalGuardLoadoutDefaults.EASY_POOL, easy.id()),
                () -> assertEquals(10, easy.totalWeight()),
                () -> assertEquals(3, easy.entries().size()),
                () -> assertTrue(easy.entries().stream()
                        .allMatch(entry -> entry.gunId().getPath().matches(
                                "glock_17|m1911|p320"))),
                () -> assertEquals(NaturalGuardLoadoutDefaults.NORMAL_POOL, normal.id()),
                () -> assertEquals(8, normal.totalWeight()),
                () -> assertEquals(NaturalGuardLoadoutDefaults.HARD_POOL, hard.id()),
                () -> assertEquals(10, hard.totalWeight()),
                () -> assertEquals(2, hard.entries().stream()
                        .filter(entry -> entry.gunId().getPath().matches("ak47|m4a1"))
                        .mapToInt(NaturalGuardLoadoutPool.Entry::weight)
                        .sum()),
                () -> assertTrue(List.of(easy, normal, hard).stream()
                        .flatMap(pool -> pool.entries().stream())
                        .allMatch(entry -> entry.sidearmId() == null),
                        "Bundled formats 1 pools must remain sidearm-free"));
    }

    private static NaturalGuardLoadoutPool bundled(String name) throws IOException {
        Path resource = Path.of(
                System.getProperty("villagerguardgunners.projectDir"),
                "src/main/resources/data/villagerguardgunners/natural_guard_loadouts",
                name + ".json");
        return NaturalGuardLoadoutPools.parsePool(
                id("villagerguardgunners", name),
                json(Files.readString(resource, StandardCharsets.UTF_8)));
    }

    private static NaturalGuardLoadoutPool parse(ResourceLocation id, String json) {
        return NaturalGuardLoadoutPools.parsePool(id, json(json));
    }

    private static void assertFormatTwoRejected(
            ResourceLocation poolId,
            String conditionMember) {
        assertThrows(
                RuntimeException.class,
                () -> parse(poolId, "{\"format\": 2, \"entries\": [{"
                        + "\"gun\": \"tacz:glock_17\", \"weight\": 1, "
                        + conditionMember
                        + "}]}"));
    }

    private static NaturalGuardLoadoutPool pool(
            ResourceLocation poolId,
            String gunId,
            int weight) {
        return new NaturalGuardLoadoutPool(
                poolId,
                List.of(new NaturalGuardLoadoutPool.Entry(
                        ResourceLocation.parse(gunId),
                        weight)));
    }

    private static NaturalGuardLoadoutPools.PreparedResources prepared(
            Map<ResourceLocation, JsonElement> parsed) {
        return new NaturalGuardLoadoutPools.PreparedResources(parsed, List.of());
    }

    private static String document(String entry) {
        return "{\"format\": 1, \"entries\": [" + entry + "]}";
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
