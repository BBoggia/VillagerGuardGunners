package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutConditionSpec.RegistrySelector;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutExampleDatapackTest {
    private static Path projectDirectory;
    private static Path examplePack;

    @BeforeAll
    static void locateExamplePack() {
        projectDirectory = Path.of(System.getProperty("villagerguardgunners.projectDir"));
        examplePack = projectDirectory.resolve("docs/examples/natural-loadout-datapack");
    }

    @Test
    void exampleIsAStandaloneMinecraftOneTwentyPointOneDatapack() throws IOException {
        JsonObject root = readJson(examplePack.resolve("pack.mcmeta")).getAsJsonObject();
        JsonObject pack = root.getAsJsonObject("pack");

        assertEquals(Set.of("pack"), root.keySet());
        assertEquals(Set.of("description", "pack_format"), pack.keySet());
        assertEquals(15, pack.get("pack_format").getAsInt());
        assertFalse(Files.exists(
                projectDirectory.resolve("src/main/resources/data/vgg_example")),
                "Documentation examples must remain inert until a modpack installs them");
    }

    @Test
    void everyDocumentedPoolPassesTheProductionStrictParser() throws IOException {
        Map<ResourceLocation, NaturalGuardLoadoutPool> pools = parseExamplePools();

        assertEquals(
                Set.of(
                        id("contextual_guards"),
                        id("migrated_normal"),
                        id("rifle_sidearms")),
                pools.keySet());
        assertEquals(NaturalGuardLoadoutFormat.CONDITIONAL_VERSION,
                pools.get(id("contextual_guards")).format());
        assertEquals(NaturalGuardLoadoutFormat.CONDITIONAL_VERSION,
                pools.get(id("migrated_normal")).format());
        assertEquals(NaturalGuardLoadoutFormat.SIDEARM_VERSION,
                pools.get(id("rifle_sidearms")).format());
    }

    @Test
    void migratedNormalPoolPreservesLegacyWeightsAndProbabilities() throws IOException {
        NaturalGuardLoadoutPool pool = parseExamplePools().get(id("migrated_normal"));
        Map<ResourceLocation, Integer> weights = new LinkedHashMap<>();
        pool.entries().forEach(entry -> weights.put(entry.gunId(), entry.weight()));

        assertEquals(8, pool.totalWeight());
        assertEquals(Map.of(
                        ResourceLocation.fromNamespaceAndPath("tacz", "glock_17"), 3,
                        ResourceLocation.fromNamespaceAndPath("tacz", "m1911"), 2,
                        ResourceLocation.fromNamespaceAndPath("tacz", "uzi"), 2,
                        ResourceLocation.fromNamespaceAndPath("tacz", "hk_mp5a5"), 1),
                weights);
        assertTrue(pool.entries().stream()
                .allMatch(entry -> entry.conditions().isUnconditional()));
    }

    @Test
    void contextualExampleExercisesEveryConditionCategoryWithAFallback()
            throws IOException {
        NaturalGuardLoadoutPool pool = parseExamplePools().get(id("contextual_guards"));
        List<NaturalGuardLoadoutConditionSpec> conditions = pool.entries().stream()
                .map(NaturalGuardLoadoutPool.Entry::conditions)
                .toList();

        assertEquals(12, pool.totalWeight());
        assertTrue(conditions.stream().anyMatch(
                NaturalGuardLoadoutConditionSpec::isUnconditional));
        assertTrue(conditions.stream().anyMatch(condition ->
                !condition.dimensions().include().isEmpty()));
        assertTrue(conditions.stream().anyMatch(condition ->
                !condition.dimensions().exclude().isEmpty()));
        assertTrue(conditions.stream().anyMatch(condition ->
                condition.biomes().include().stream().anyMatch(RegistrySelector::tag)));
        assertTrue(conditions.stream().anyMatch(condition ->
                condition.biomes().exclude().stream().anyMatch(selector -> !selector.tag())));
        assertTrue(conditions.stream().anyMatch(condition ->
                condition.structures().include().stream().anyMatch(RegistrySelector::tag)));
        assertTrue(conditions.stream().anyMatch(condition ->
                condition.structures().exclude().stream()
                        .anyMatch(selector -> !selector.tag())));
        assertTrue(conditions.stream().anyMatch(condition ->
                !condition.difficulties().isEmpty()));
        assertTrue(conditions.stream().anyMatch(condition ->
                !condition.spawnSources().isEmpty()));
        assertTrue(conditions.stream().anyMatch(condition ->
                condition.raid()
                        != NaturalGuardLoadoutConditionSpec.RaidRequirement.ANY));
        assertTrue(conditions.stream().anyMatch(condition ->
                !condition.requiredGuardTags().isEmpty()));
        assertTrue(conditions.stream().anyMatch(condition ->
                !condition.excludedGuardTags().isEmpty()));
    }

    @Test
    void formatThreeExampleUsesExplicitPistolSidearms() throws IOException {
        NaturalGuardLoadoutPool pool = parseExamplePools().get(id("rifle_sidearms"));

        assertEquals(NaturalGuardLoadoutFormat.SIDEARM_VERSION, pool.format());
        assertEquals(2, pool.totalWeight());
        assertTrue(pool.entries().stream().allMatch(entry ->
                entry.sidearmId() != null
                        && entry.sidearmId().getNamespace().equals("tacz")
                        && entry.conditions().difficulties()
                                .equals(Set.of(NaturalGuardDifficulty.HARD))));
    }

    @Test
    void supportingRegistryTagsUseCanonicalIdsAndExpectedDataPaths() throws IOException {
        Path tags = examplePack.resolve("data/vgg_example/tags/worldgen");
        List<Path> tagFiles;
        try (Stream<Path> files = Files.walk(tags)) {
            tagFiles = files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }

        assertEquals(List.of(
                        tags.resolve("biome/is_dry.json"),
                        tags.resolve("structure/villages.json")),
                tagFiles);
        for (Path tagFile : tagFiles) {
            JsonObject tag = readJson(tagFile).getAsJsonObject();
            assertEquals(Set.of("replace", "values"), tag.keySet());
            assertFalse(tag.get("replace").getAsBoolean());
            assertFalse(tag.getAsJsonArray("values").isEmpty());
            for (JsonElement valueElement : tag.getAsJsonArray("values")) {
                assertTrue(valueElement.isJsonPrimitive());
                String value = valueElement.getAsString();
                ResourceLocation parsed = ResourceLocation.tryParse(value);
                assertNotNull(parsed, () -> "Invalid example tag value " + value);
                assertEquals(value, parsed.toString(),
                        () -> "Non-canonical example tag value " + value);
            }
        }
    }

    @Test
    void migrationDocumentationCoversSafeActivationAndRollback() throws IOException {
        String migration = Files.readString(
                projectDirectory.resolve("docs/natural-loadout-migration.md"),
                StandardCharsets.UTF_8);
        String exampleReadme = Files.readString(
                examplePack.resolve("README.md"),
                StandardCharsets.UTF_8);

        assertTrue(migration.contains("Lossless format-1 to format-2 conversion"));
        assertTrue(migration.contains("NO_MATCHING_ENTRIES"));
        assertTrue(migration.contains("naturalLoadouts.normal.poolId"));
        assertTrue(migration.contains("Datapack priority replaces a whole pool"));
        assertTrue(migration.contains("naturalLoadouts.enabled = false"));
        assertTrue(migration.contains("Opt in to format-3 sidearms"));
        assertTrue(exampleReadme.contains("built-in pools or alter configuration"));
        assertTrue(exampleReadme.contains(
                "/vgg loadouts validate vgg_example:contextual_guards"));
        assertTrue(exampleReadme.contains(
                "/vgg loadouts validate vgg_example:rifle_sidearms"));
    }

    private static Map<ResourceLocation, NaturalGuardLoadoutPool> parseExamplePools()
            throws IOException {
        Path poolDirectory = examplePack.resolve(
                "data/vgg_example/natural_guard_loadouts");
        Map<ResourceLocation, NaturalGuardLoadoutPool> pools = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(poolDirectory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                String relative = poolDirectory.relativize(file).toString()
                        .replace(file.getFileSystem().getSeparator(), "/");
                String poolPath = relative.substring(0, relative.length() - ".json".length());
                ResourceLocation poolId = id(poolPath);
                pools.put(poolId, NaturalGuardLoadoutParser.parse(poolId, readJson(file)));
            }
        }
        return pools;
    }

    private static JsonElement readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("vgg_example", path);
    }
}
