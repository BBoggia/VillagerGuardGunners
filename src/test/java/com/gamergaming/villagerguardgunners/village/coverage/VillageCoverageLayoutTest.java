package com.gamergaming.villagerguardgunners.village.coverage;

import static org.junit.jupiter.api.Assertions.*;

import com.gamergaming.villagerguardgunners.village.VillageFootprint;
import com.gamergaming.villagerguardgunners.village.VillageIdentity;
import com.gamergaming.villagerguardgunners.village.VillageRecord;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageLayout.Site;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageLayout.SiteKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VillageCoverageLayoutTest {
    private static final UUID VILLAGE = new UUID(0, 1);

    @Test void ordinaryVillageUsesHomesWithoutDefenseBuildingsOrTemplateHeuristics() {
        var house = piece("pack:dwelling", 0);
        var decoration = piece("minecraft:village/plains/decor/fake_house", 512);
        var footprint = VillageFootprint.of(List.of(house, decoration));
        var record = new VillageRecord(new VillageIdentity(ResourceLocation.parse("minecraft:overworld"),
                ResourceLocation.parse("minecraft:village_plains"), 0), footprint, 1,
                Set.of(new BlockPos(2, 64, 2)));
        var homesOnly = VillageCoverageLayout.fromRecord(record, Map.of(), List.of());
        assertEquals(1, homesOnly.areas().size());
        assertEquals(List.of(new BlockPos(2, 64, 2)), homesOnly.areas().get(0).anchors());
        var classified = VillageCoverageLayout.fromRecord(record,
                Map.of(house.template(), SiteKind.RESIDENTIAL, decoration.template(), SiteKind.DECORATION), List.of());
        assertEquals(1, classified.areas().size());
        assertEquals(2, classified.areas().get(0).anchors().size());
        assertTrue(classified.areas().get(0).anchors().stream().allMatch(pos -> pos.getX() < 32));
        assertThrows(IllegalArgumentException.class, () -> VillageCoverageLayout.fromRecord(record, Map.of(),
                List.of(new Site(new BlockPos(1_000, 64, 0), SiteKind.SERVICE))));
    }

    @Test void inputOrderDuplicatesAndRevisionDoNotChangeAreaIdentity() {
        List<Site> sites = new ArrayList<>(List.of(home(0, 64, 0), home(32, 64, 0), home(128, 64, 0)));
        var first = VillageCoverageLayout.partition(VILLAGE, 1, sites);
        sites.add(sites.get(0));
        Collections.reverse(sites);
        var second = VillageCoverageLayout.partition(VILLAGE, 2, sites);
        assertEquals(first.areas(), second.areas());
        assertEquals(2, second.revision());
        assertNotEquals(first.areas(), VillageCoverageLayout.partition(new UUID(0, 2), 1, sites).areas());
    }

    @Test void sixtyFourOccupiedCellsBecomeSixteenBoundedAreasWithoutInventingOccupancy() {
        List<Site> sites = IntStream.range(0, 64)
                .mapToObj(i -> home((i % 8) * 32, 64, (i / 8) * 32)).toList();
        var layout = VillageCoverageLayout.partition(VILLAGE, 1, sites);
        assertEquals(VillageCoverageLayout.Status.READY, layout.status());
        assertEquals(16, layout.areas().size());
        assertTrue(layout.areas().stream().allMatch(area -> area.cells().size() <= 4));
        assertEquals(64, layout.areas().stream().flatMap(area -> area.cells().stream()).distinct().count());
        assertEquals(64, layout.areas().stream().flatMap(area -> area.anchors().stream()).distinct().count());
    }

    @Test void negativeCoordinatesAndVerticalLayersAreExplicit() {
        assertEquals(new VillageCoverageLayout.Cell(-1, 1, -1),
                VillageCoverageLayout.Cell.at(new BlockPos(-1, 63, -32)));
        var layout = VillageCoverageLayout.partition(VILLAGE, 1, List.of(home(-1, 63, -1), home(-1, 64, -1)));
        assertEquals(2, layout.areas().size());
        assertFalse(layout.areas().get(0).cells().get(0).equals(layout.areas().get(1).cells().get(0)));
    }

    @Test void provenDisconnectedSitesStaySeparateEvenInsideTheSameCell() {
        var left = new Site(new BlockPos(0, 64, 0), SiteKind.HOME, new UUID(0, 10));
        var right = new Site(new BlockPos(4, 64, 0), SiteKind.HOME, new UUID(0, 20));
        var layout = VillageCoverageLayout.partition(VILLAGE, 1, List.of(left, right));
        assertEquals(2, layout.areas().size());
        assertEquals(2, layout.areas().stream().map(VillageCoverageLayout.Area::component).distinct().count());
    }

    @Test void distantDecorationsAndRoadsDoNotCreateAreasOrBridgeOccupiedCells() {
        var layout = VillageCoverageLayout.partition(VILLAGE, 1, List.of(home(0, 64, 0),
                new Site(new BlockPos(32, 64, 0), SiteKind.ROAD), home(64, 64, 0),
                new Site(new BlockPos(100_000, 64, 0), SiteKind.DECORATION)));
        assertEquals(2, layout.areas().size());
        assertEquals(2, layout.areas().stream().mapToInt(area -> area.anchors().size()).sum());
    }

    @Test void excessiveDisconnectedAreasRetainBoundedIndependentCoverage() {
        var layout = VillageCoverageLayout.partition(VILLAGE, 1,
                IntStream.range(0, 17).mapToObj(i -> home(i * 64, 64, 0)).toList());
        assertEquals(VillageCoverageLayout.Status.AREA_LIMIT, layout.status());
        assertEquals(VillageCoverageLayout.MAX_AREAS, layout.areas().size());
        assertEquals(16, layout.areas().stream().flatMap(area -> area.anchors().stream()).distinct().count());
        assertThrows(IllegalArgumentException.class, () -> VillageCoverageLayout.partition(VILLAGE, 1,
                Collections.nCopies(VillageCoverageLayout.MAX_SITES + 1, home(0, 64, 0))));
    }

    @Test void emptyLayoutsAndImmutablePositionsRemainSafe() {
        assertEquals(VillageCoverageLayout.Status.NO_OCCUPIED_SITES,
                VillageCoverageLayout.partition(VILLAGE, 1, List.of()).status());
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 64, 1);
        Site site = new Site(mutable, SiteKind.HOME);
        mutable.set(100, 100, 100);
        assertEquals(new BlockPos(1, 64, 1), site.position());
        var layout = VillageCoverageLayout.partition(VILLAGE, 1, List.of(site));
        assertThrows(UnsupportedOperationException.class, () -> layout.areas().clear());
        assertThrows(UnsupportedOperationException.class, () -> layout.areas().get(0).anchors().clear());
        assertThrows(IllegalArgumentException.class, () -> home(Integer.MIN_VALUE, 0, 0));
    }

    private static Site home(int x, int y, int z) { return new Site(new BlockPos(x, y, z), SiteKind.HOME); }
    private static VillageFootprint.Piece piece(String template, int x) {
        return new VillageFootprint.Piece(new BlockPos(x, 64, 0), "NONE", ResourceLocation.parse(template),
                new VillageFootprint.Bounds(x, 64, 0, x + 15, 80, 15));
    }
}
