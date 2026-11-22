package com.gamergaming.villagerguardgunners.worldgen.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.junit.jupiter.api.Test;

final class VillageBuildingPoolIntegrationTest {

    @Test
    void disabledIntegrationDoesNotRequireDynamicRegistries() {
        VillagePoolInjectionReport report = VillageBuildingPoolIntegration.install(
                RegistryAccess.EMPTY,
                null,
                false,
                List.of(probe()));

        assertEquals(
                new VillagePoolInjectionReport(
                        false, 1, 0, 0, 0, 0, 0, 0, List.of()),
                report);
        assertFalse(report.hasWarnings());
    }

    @Test
    void entryValidationSupportsPackOwnedIdsAndRejectsOutOfBoundsWeights() {
        ResourceLocation pool = id("minecraft", "village/plains/houses");
        ResourceLocation template = id("minecraft", "village/plains/houses/plains_small_house_1");
        ResourceLocation processors = id("minecraft", "empty");

        assertEquals(
                id("example", "foreign"),
                new VillageBuildingPoolEntry(
                        id("example", "foreign"),
                        VillageBuildingType.SENTRY_POST,
                        pool,
                        template,
                        processors,
                        StructureTemplatePool.Projection.RIGID,
                        1).entryId());
        assertThrows(IllegalArgumentException.class, () -> new VillageBuildingPoolEntry(
                id("villagerguardgunners", "zero"), VillageBuildingType.BARRACKS,
                pool, template, processors, StructureTemplatePool.Projection.RIGID, 0));
        assertThrows(IllegalArgumentException.class, () -> new VillageBuildingPoolEntry(
                id("villagerguardgunners", "heavy"), VillageBuildingType.BARRACKS,
                pool, template, processors, StructureTemplatePool.Projection.RIGID, 6));
    }

    @Test
    void batchValidationRejectsDuplicatesAndBothHardLimits() {
        VillageBuildingPoolEntry probe = probe();
        assertThrows(
                IllegalArgumentException.class,
                () -> VillageBuildingPoolIntegration.validateBatch(List.of(probe, probe)));

        List<VillageBuildingPoolEntry> tooManyPools = new ArrayList<>();
        for (int index = 0; index <= VillageBuildingPools.MAXIMUM_TARGET_POOLS; index++) {
            tooManyPools.add(entry(index, "pool_" + index));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> VillageBuildingPoolIntegration.validateBatch(tooManyPools));

        List<VillageBuildingPoolEntry> tooManyEntries = new ArrayList<>();
        for (int index = 0; index <= VillageBuildingPools.MAXIMUM_INJECTED_ENTRIES; index++) {
            tooManyEntries.add(entry(index, "shared_pool"));
        }
        // Regional families, provider adapters, and built-ins must fit, but the new
        // bound still rejects overflow before mutating any live pool.
        assertDoesNotThrow(() -> VillageBuildingPoolIntegration.validateBatch(
                tooManyEntries.subList(0, 71)));
        assertDoesNotThrow(() -> VillageBuildingPoolIntegration.validateBatch(
                tooManyEntries.subList(0, 103)));
        assertDoesNotThrow(() -> VillageBuildingPoolIntegration.validateBatch(
                tooManyEntries.subList(0, 131)));
        assertDoesNotThrow(() -> VillageBuildingPoolIntegration.validateBatch(
                tooManyEntries.subList(0, 192)));
        assertThrows(
                IllegalArgumentException.class,
                () -> VillageBuildingPoolIntegration.validateBatch(tooManyEntries));
    }

    @Test
    void batchSupportsHousePoolsAndRegionalStreetEndsTogether() {
        List<VillageBuildingPoolEntry> entries = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            entries.add(entry(index, "regional_pool_" + index));
        }
        VillageBuildingPoolIntegration.validateBatch(entries);
        entries.add(entry(24, "twenty_fifth_pool"));
        assertThrows(IllegalArgumentException.class,
                () -> VillageBuildingPoolIntegration.validateBatch(entries));
    }

    @Test
    void stableBuildingTypeSurfaceContainsExactlyTheFivePlannedBuildings() {
        assertEquals(
                List.of("barracks", "armory", "sentry_post", "watchtower", "training_yard"),
                java.util.Arrays.stream(VillageBuildingType.values())
                        .map(VillageBuildingType::serializedName)
                        .toList());
    }

    private static VillageBuildingPoolEntry entry(int index, String poolPath) {
        return new VillageBuildingPoolEntry(
                id("villagerguardgunners", "test_" + index),
                VillageBuildingType.BARRACKS,
                id("example", poolPath),
                id("example", "template_" + index),
                id("minecraft", "empty"),
                StructureTemplatePool.Projection.RIGID,
                1);
    }

    private static VillageBuildingPoolEntry probe() {
        return new VillageBuildingPoolEntry(
                VillageBuildingPools.PLAINS_INTEGRATION_PROBE_ID,
                VillageBuildingType.BARRACKS,
                VillageBuildingPools.PLAINS_HOUSES,
                id("minecraft", "village/plains/houses/plains_small_house_1"),
                VillageBuildingPools.EMPTY_PROCESSORS,
                StructureTemplatePool.Projection.RIGID,
                1);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
