package com.gamergaming.villagerguardgunners.worldgen.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.junit.jupiter.api.Test;

final class VillageBuildingInstallationSnapshotTest {

    @Test
    void startupSnapshotIsImmutableCountedAndSessionEmptyByDefault() {
        VillageBuildingPoolEntry entry = new VillageBuildingPoolEntry(
                id("entry"),
                VillageBuildingType.SENTRY_POST,
                id("pool"),
                id("template"),
                VillageBuildingPools.EMPTY_PROCESSORS,
                StructureTemplatePool.Projection.RIGID,
                2);
        List<VillageBuildingPoolEntry> mutable = new ArrayList<>(List.of(entry));
        VillagePoolInjectionReport report = new VillagePoolInjectionReport(
                true, 1, 1, 0, 0, 0, 0, 0, List.of());
        VillageBuildingInstallationSnapshot snapshot =
                new VillageBuildingInstallationSnapshot(true, mutable, report);
        mutable.clear();

        assertTrue(snapshot.attempted());
        assertEquals(List.of(entry), snapshot.requestedEntries());
        assertSame(entry, snapshot.requestedEntry(id("entry")));
        assertNull(snapshot.requestedEntry(id("missing")));
        assertEquals(report, snapshot.report());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.requestedEntries().clear());

        VillageBuildingInstallationSnapshot empty =
                VillageBuildingInstallationSnapshot.empty();
        assertFalse(empty.attempted());
        assertTrue(empty.requestedEntries().isEmpty());
        assertEquals(0, empty.report().requested());
    }

    @Test
    void inconsistentAttemptStateOrRequestCountIsRejected() {
        VillageBuildingPoolEntry entry = new VillageBuildingPoolEntry(
                id("entry"),
                VillageBuildingType.BARRACKS,
                id("pool"),
                id("template"),
                VillageBuildingPools.EMPTY_PROCESSORS,
                StructureTemplatePool.Projection.RIGID,
                1);

        assertThrows(IllegalArgumentException.class,
                () -> new VillageBuildingInstallationSnapshot(
                        false,
                        List.of(entry),
                        VillagePoolInjectionReport.disabled(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new VillageBuildingInstallationSnapshot(
                        true,
                        List.of(entry),
                        VillagePoolInjectionReport.disabled(0)));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("example", path);
    }
}
