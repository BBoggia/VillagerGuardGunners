package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class VillageWatchtowerRegistrationContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
            System.getProperty("villagerguardgunners.projectDir", "."));

    @Test
    void customStructureAndPieceTypesAreRegisteredExactlyOnce() throws IOException {
        String bootstrap = read(
                "src/main/java/com/gamergaming/villagerguardgunners/"
                        + "VillagerGuardGunners.java");
        String registry = read(
                "src/main/java/com/gamergaming/villagerguardgunners/init/"
                        + "ModStructures.java");
        String structure = read(
                "src/main/java/com/gamergaming/villagerguardgunners/worldgen/structure/"
                        + "VillageWatchtowerStructure.java");

        assertAll(
                () -> assertTrue(bootstrap.contains("ModStructures.register(modEventBus)")),
                () -> assertEquals(1, occurrences(
                        bootstrap,
                        "ModStructures.register(modEventBus)")),
                () -> assertTrue(registry.contains(
                        "DeferredRegister.create(\n                    Registries.STRUCTURE_TYPE")),
                () -> assertTrue(registry.contains("Registries.STRUCTURE_PIECE")),
                () -> assertTrue(registry.contains("\"watchtower\"")),
                () -> assertTrue(registry.contains("VillageWatchtowerStructure.CODEC")),
                () -> assertTrue(registry.contains("VillageWatchtowerPiece::new")),
                () -> assertTrue(structure.contains(
                        "simpleCodec(VillageWatchtowerStructure::new)")),
                () -> assertTrue(structure.contains("WatchtowerPlacement.findGroundY")),
                () -> assertTrue(structure.contains("Rotation.getRandom(context.random())")),
                () -> assertTrue(structure.contains("new VillageWatchtowerPiece(")),
                () -> assertFalse(structure.contains("StructureTemplate")),
                () -> assertFalse(structure.contains("WorldGenLevel")),
                () -> assertFalse(structure.contains("Entity")));
    }

    @Test
    void builtInStructureSettingsUseVillageCompatibleOverworldBiomes() throws IOException {
        JsonObject structure = json(
                "src/main/resources/data/villagerguardgunners/worldgen/structure/"
                        + "watchtower.json");

        assertAll(
                () -> assertEquals(Set.of(
                                "type",
                                "biomes",
                                "spawn_overrides",
                                "step",
                                "terrain_adaptation"),
                        structure.keySet()),
                () -> assertEquals(
                        "villagerguardgunners:watchtower",
                        structure.get("type").getAsString()),
                () -> assertEquals(
                        "#villagerguardgunners:has_structure/watchtower",
                        structure.get("biomes").getAsString()),
                () -> assertEquals(
                        0,
                        structure.getAsJsonObject("spawn_overrides").size()),
                () -> assertEquals(
                        "surface_structures",
                        structure.get("step").getAsString()),
                () -> assertEquals(
                        "beard_thin",
                        structure.get("terrain_adaptation").getAsString()));

        JsonObject biomeTag = json(
                "src/main/resources/data/villagerguardgunners/tags/worldgen/biome/"
                        + "has_structure/watchtower.json");
        assertEquals(Set.of("replace", "values"), biomeTag.keySet());
        assertFalse(biomeTag.get("replace").getAsBoolean());
        assertEquals(Set.of(
                        "#minecraft:has_structure/village_desert",
                        "#minecraft:has_structure/village_plains",
                        "#minecraft:has_structure/village_savanna",
                        "#minecraft:has_structure/village_snowy",
                        "#minecraft:has_structure/village_taiga"),
                biomeTag.getAsJsonArray("values").asList().stream()
                        .map(element -> element.getAsString())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void structureSetIsRareDeterministicAndInternallyValid() throws IOException {
        JsonObject root = json(
                "src/main/resources/data/villagerguardgunners/worldgen/structure_set/"
                        + "watchtowers.json");
        JsonObject placement = root.getAsJsonObject("placement");
        JsonArray structures = root.getAsJsonArray("structures");
        JsonObject entry = structures.get(0).getAsJsonObject();

        assertAll(
                () -> assertEquals(Set.of("placement", "structures"), root.keySet()),
                () -> assertEquals(Set.of(
                                "type",
                                "salt",
                                "separation",
                                "spacing",
                                "spread_type"),
                        placement.keySet()),
                () -> assertEquals("minecraft:random_spread",
                        placement.get("type").getAsString()),
                () -> assertEquals(17290527, placement.get("salt").getAsInt()),
                () -> assertEquals(12, placement.get("separation").getAsInt()),
                () -> assertEquals(40, placement.get("spacing").getAsInt()),
                () -> assertTrue(placement.get("spacing").getAsInt()
                        > placement.get("separation").getAsInt()),
                () -> assertEquals("linear", placement.get("spread_type").getAsString()),
                () -> assertEquals(1, structures.size()),
                () -> assertEquals(Set.of("structure", "weight"), entry.keySet()),
                () -> assertEquals("villagerguardgunners:watchtower",
                        entry.get("structure").getAsString()),
                () -> assertEquals(1, entry.get("weight").getAsInt()));
    }

    @Test
    void releaseArtifactRequiresTheCompleteStepElevenSurface() throws IOException {
        String build = read("build.gradle");

        assertAll(
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners/worldgen/structure/watchtower.json")),
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners/worldgen/structure_set/watchtowers.json")),
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners/tags/worldgen/biome/has_structure/"
                                + "watchtower.json")),
                () -> assertTrue(build.contains(
                        "data/villagerguardgunners/structures/watchtower.nbt")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/init/ModStructures.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/worldgen/structure/"
                                + "VillageWatchtowerPiece.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/worldgen/structure/"
                                + "WatchtowerPlacement.class")),
                () -> assertTrue(build.contains(
                        "com/gamergaming/villagerguardgunners/worldgen/structure/"
                                + "VillageWatchtowerStructure.class")));
    }

    private static JsonObject json(String relativePath) throws IOException {
        return JsonParser.parseString(read(relativePath)).getAsJsonObject();
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(
                PROJECT_DIRECTORY.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static int occurrences(String input, String needle) {
        int count = 0;
        int index = 0;
        while ((index = input.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
