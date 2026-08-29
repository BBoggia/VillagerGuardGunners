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
import java.util.LinkedHashMap;
import java.util.Map;
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
                        () -> parse(poolId, "{\"format\": 2, \"entries\": []}")),
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
    void semanticFailuresAreIsolatedAndReportedDeterministically() {
        Map<ResourceLocation, JsonElement> prepared = new LinkedHashMap<>();
        prepared.put(id("test", "z_invalid"), json("{\"format\": 1, \"entries\": []}"));
        prepared.put(id("test", "valid"), json(document(
                "{\"gun\": \"tacz:glock_17\", \"weight\": 1}")));
        prepared.put(id("test", "a_invalid"), json("{\"format\": 9, \"entries\": []}"));

        NaturalGuardLoadoutPools.LoadResult result = NaturalGuardLoadoutPools.parseAll(prepared);

        assertAll(
                () -> assertEquals(1, result.pools().size()),
                () -> assertTrue(result.pools().containsKey(id("test", "valid"))),
                () -> assertEquals(2, result.rejected().size()),
                () -> assertEquals(id("test", "a_invalid"), result.rejected().get(0).id()),
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
        listener.apply(
                Map.of(first, json(document(
                        "{\"gun\": \"tacz:glock_17\", \"weight\": 1}"))),
                null,
                null);
        Map<ResourceLocation, NaturalGuardLoadoutPool> oldSnapshot = listener.loadedSnapshot();

        listener.apply(
                Map.of(second, json(document(
                        "{\"gun\": \"tacz:m1911\", \"weight\": 1}"))),
                null,
                null);

        assertAll(
                () -> assertEquals(1, oldSnapshot.size()),
                () -> assertTrue(oldSnapshot.containsKey(first)),
                () -> assertNull(listener.getLoaded(first)),
                () -> assertEquals(second, listener.getLoaded(second).id()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> listener.loadedSnapshot().clear()));
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
                        .sum()));
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
