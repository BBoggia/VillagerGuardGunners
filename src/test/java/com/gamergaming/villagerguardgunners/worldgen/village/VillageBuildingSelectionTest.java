package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VillageBuildingSelectionTest {
    @Test void defaultsPreserveCandidateOrderAndRandomStream() {
        var candidates = List.of(new Object(), new Object());
        assertSame(candidates, VillageBuildingSelection.filter(candidates, e -> 1.0,
                () -> { throw new AssertionError("Default admission consumed randomness"); }));
    }

    @Test void failedAdmissionRemovesAllWeightedCopiesWithoutBlockingOtherCandidates() {
        Object building = new Object(), road = new Object(), empty = new Object();
        var candidates = List.of(building, road, building, empty, building);
        var draws = new AtomicInteger();
        var selected = VillageBuildingSelection.filter(candidates, e -> e == building ? 0.25 : 1.0,
                () -> { draws.incrementAndGet(); return 0.25; });
        assertEquals(List.of(road, empty), selected);
        assertEquals(1, draws.get());
        assertEquals(5, candidates.size());
    }

    @Test void admittedBuildingRetainsWeightAndEachDistinctElementGetsItsOwnDecision() {
        Object compact = new Object(), courtyard = new Object(), road = new Object();
        var draws = new AtomicInteger();
        var selected = VillageBuildingSelection.filter(List.of(compact, courtyard, compact, road),
                e -> e == road ? 1.0 : 0.25, () -> draws.getAndIncrement() == 0 ? 0.249 : 0.9);
        assertEquals(List.of(compact, compact, road), selected);
        assertEquals(2, draws.get());
    }

    @Test void zeroChanceUsesNoRandomnessAndAllowsFallback() {
        Object building = new Object();
        assertTrue(VillageBuildingSelection.filter(List.of(building, building), e -> 0.0,
                () -> { throw new AssertionError("Zero admission consumed randomness"); }).isEmpty());
    }
}
