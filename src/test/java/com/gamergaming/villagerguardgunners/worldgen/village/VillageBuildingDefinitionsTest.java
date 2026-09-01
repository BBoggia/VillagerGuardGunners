package com.gamergaming.villagerguardgunners.worldgen.village;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.junit.jupiter.api.Test;

final class VillageBuildingDefinitionsTest {

    @Test
    void validDocumentsCompileDeterministicallyWithPackOwnedIdsAndDefaults() {
        Map<ResourceLocation, JsonElement> documents = new LinkedHashMap<>();
        documents.put(id("example", "z_armory"), json("""
                {
                  "format": 1,
                  "building_type": "armory",
                  "target_pool": "minecraft:village/desert/houses",
                  "template": "example:village/desert/armory",
                  "projection": "terrain_matching",
                  "weight": 2
                }
                """));
        documents.put(id("example", "a_post"), json("""
                {
                  "format": 1,
                  "building_type": "sentry_post",
                  "target_pool": "minecraft:village/plains/houses",
                  "template": "example:village/plains/sentry_post",
                  "weight": 1
                }
                """));

        Map<ResourceLocation, VillageBuildingPoolEntry> compiled =
                VillageBuildingDefinitions.compile(documents);
        VillageBuildingPoolEntry first = compiled.values().iterator().next();
        VillageBuildingPoolEntry armory = compiled.get(id("example", "z_armory"));

        assertAll(
                () -> assertEquals(
                        List.of(id("example", "a_post"), id("example", "z_armory")),
                        List.copyOf(compiled.keySet())),
                () -> assertEquals(VillageBuildingType.SENTRY_POST, first.buildingType()),
                () -> assertEquals(VillageBuildingPools.EMPTY_PROCESSORS, first.processors()),
                () -> assertEquals(StructureTemplatePool.Projection.RIGID, first.projection()),
                () -> assertEquals(VillageBuildingType.ARMORY, armory.buildingType()),
                () -> assertEquals(
                        StructureTemplatePool.Projection.TERRAIN_MATCHING,
                        armory.projection()),
                () -> assertEquals(2, armory.weight()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> compiled.clear()));
    }

    @Test
    void allFiveStableBuildingTypesAreAccepted() {
        Map<ResourceLocation, JsonElement> documents = new LinkedHashMap<>();
        int index = 0;
        for (VillageBuildingType type : VillageBuildingType.values()) {
            documents.put(
                    id("example", "building_" + index),
                    definition(type.serializedName(), "pool", "template_" + index, 1));
            index++;
        }

        assertEquals(5, VillageBuildingDefinitions.compile(documents).size());
    }

    @Test
    void schemaRejectsUnknownOrMalformedValues() {
        assertAll(
                () -> assertRejected("""
                        {"format":2,"building_type":"barracks","target_pool":"example:pool","template":"example:a","weight":1}
                        """),
                () -> assertRejected("""
                        {"format":1,"building_type":"BARRACKS","target_pool":"example:pool","template":"example:a","weight":1}
                        """),
                () -> assertRejected("""
                        {"format":1,"building_type":"barracks","target_pool":"example:pool","template":"example:a","projection":"floating","weight":1}
                        """),
                () -> assertRejected("""
                        {"format":1,"building_type":"barracks","target_pool":"example:pool","template":"example:a","weight":1.5}
                        """),
                () -> assertRejected("""
                        {"format":1,"building_type":"barracks","target_pool":"example:pool","template":"example:a","weight":6}
                        """),
                () -> assertRejected("""
                        {"format":1,"building_type":"barracks","target_pool":"Example:pool","template":"example:a","weight":1}
                        """),
                () -> assertRejected("""
                        {"format":1,"building_type":"barracks","target_pool":"example:pool","template":"example:a","weight":1,"command":"say unsafe"}
                        """),
                () -> assertRejected("[]"));
    }

    @Test
    void strictReaderRejectsDuplicateTrailingOversizedAndDeepDocuments() {
        String duplicate = "{\"format\":1,\"format\":1}";
        String trailing = "{} {}";
        String oversized = " ".repeat(
                VillageBuildingDefinitionFormat.MAXIMUM_DOCUMENT_CHARACTERS + 1);
        String deep = "[".repeat(VillageBuildingDefinitionFormat.MAXIMUM_JSON_DEPTH + 1)
                + "0"
                + "]".repeat(VillageBuildingDefinitionFormat.MAXIMUM_JSON_DEPTH + 1);

        assertAll(
                () -> assertThrows(
                        JsonParseException.class,
                        () -> read(duplicate)),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> read(trailing)),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> read(oversized)),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> read(deep)));
    }

    @Test
    void publicationRejectsDuplicatePlacementAndBothBatchLimits() {
        Map<ResourceLocation, JsonElement> duplicatePlacement = Map.of(
                id("example", "one"), definition("barracks", "pool", "same", 1),
                id("example", "two"), definition("barracks", "pool", "same", 2));

        Map<ResourceLocation, JsonElement> tooManyPools = new LinkedHashMap<>();
        for (int index = 0; index <= VillageBuildingPools.MAXIMUM_TARGET_POOLS; index++) {
            tooManyPools.put(
                    id("example", "pool_entry_" + index),
                    definition("barracks", "pool_" + index, "template_" + index, 1));
        }

        Map<ResourceLocation, JsonElement> tooManyDocuments = new LinkedHashMap<>();
        for (int index = 0; index <= VillageBuildingDefinitionFormat.MAXIMUM_DOCUMENTS; index++) {
            tooManyDocuments.put(
                    id("example", "entry_" + index),
                    definition("training_yard", "pool", "template_" + index, 1));
        }

        assertAll(
                () -> assertThrows(
                        JsonParseException.class,
                        () -> VillageBuildingDefinitions.compile(duplicatePlacement)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> VillageBuildingDefinitions.compile(tooManyPools)),
                () -> assertThrows(
                        JsonParseException.class,
                        () -> VillageBuildingDefinitions.compile(tooManyDocuments)));
    }

    @Test
    void failedReloadRetainsLastKnownGoodSnapshotAtomically() {
        VillageBuildingDefinitions loader = new VillageBuildingDefinitions();
        ResourceLocation acceptedId = id("example", "accepted");
        loader.publish(prepared(Map.of(
                acceptedId,
                definition("watchtower", "pool", "accepted", 1))));
        VillageBuildingDefinitionReloadSnapshot accepted = loader.currentSnapshot();

        loader.publish(prepared(Map.of(
                id("example", "rejected"),
                definition("watchtower", "pool", "rejected", 9))));
        VillageBuildingDefinitionReloadSnapshot rejected = loader.currentSnapshot();

        loader.publish(prepared(Map.of(
                id("example", "replacement"),
                definition("training_yard", "pool", "replacement", 1))));
        VillageBuildingDefinitionReloadSnapshot replacement = loader.currentSnapshot();

        assertAll(
                () -> assertTrue(accepted.lastReloadAccepted()),
                () -> assertEquals(1L, accepted.generation()),
                () -> assertEquals(List.of(acceptedId), List.copyOf(accepted.definitions().keySet())),
                () -> assertFalse(rejected.lastReloadAccepted()),
                () -> assertEquals(accepted.generation(), rejected.generation()),
                () -> assertEquals(accepted.definitions(), rejected.definitions()),
                () -> assertEquals(1, rejected.lastErrors().size()),
                () -> assertTrue(replacement.lastReloadAccepted()),
                () -> assertEquals(2L, replacement.generation()),
                () -> assertEquals(
                        List.of(id("example", "replacement")),
                        List.copyOf(replacement.definitions().keySet())));
    }

    @Test
    void stoppedServerSessionCannotSupplyAnotherServersLastKnownGoodDefinitions() {
        VillageBuildingDefinitions loader = new VillageBuildingDefinitions();
        ResourceLocation acceptedId = id("example", "accepted");
        loader.publish(prepared(Map.of(
                acceptedId,
                definition("watchtower", "pool", "accepted", 1))));

        loader.resetSession();
        loader.publish(prepared(Map.of(
                id("example", "rejected"),
                definition("watchtower", "pool", "rejected", 9))));

        VillageBuildingDefinitionReloadSnapshot rejected = loader.currentSnapshot();
        assertAll(
                () -> assertFalse(rejected.lastReloadAccepted()),
                () -> assertEquals(0L, rejected.generation()),
                () -> assertTrue(rejected.definitions().isEmpty()),
                () -> assertEquals(1, rejected.lastErrors().size()));
    }

    @Test
    void typeSettingsOnlySuppressMatchingAcceptedEntries() {
        Map<ResourceLocation, VillageBuildingPoolEntry> compiled =
                VillageBuildingDefinitions.compile(Map.of(
                        id("example", "barracks"),
                        definition("barracks", "pool", "barracks", 1),
                        id("example", "armory"),
                        definition("armory", "pool", "armory", 1)));
        VillagerGuardGunnersConfig config = new VillagerGuardGunnersConfig();
        config.villageBuildings.barracks.accept(false);

        List<VillageBuildingPoolEntry> enabled = VillageBuildingDefinitions.enabledEntries(
                compiled, config.villageBuildings);

        assertAll(
                () -> assertEquals(1, enabled.size()),
                () -> assertEquals(VillageBuildingType.ARMORY, enabled.get(0).buildingType()),
                () -> assertEquals(2, compiled.size()));
    }

    private static VillageBuildingDefinitions.PreparedResources prepared(
            Map<ResourceLocation, JsonElement> documents) {
        return new VillageBuildingDefinitions.PreparedResources(documents, List.of());
    }

    private static JsonElement read(String source) throws Exception {
        return VillageBuildingDefinitions.readBoundedJson(
                new StringReader(source), id("example", "strict"));
    }

    private static void assertRejected(String source) {
        assertThrows(
                RuntimeException.class,
                () -> VillageBuildingDefinitions.compile(Map.of(
                        id("example", "invalid"), json(source))));
    }

    private static JsonElement definition(
            String type,
            String poolPath,
            String templatePath,
            int weight) {
        return json("""
                {
                  "format": 1,
                  "building_type": "%s",
                  "target_pool": "example:%s",
                  "template": "example:%s",
                  "weight": %d
                }
                """.formatted(type, poolPath, templatePath, weight));
    }

    private static JsonElement json(String source) {
        return JsonParser.parseString(source);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
