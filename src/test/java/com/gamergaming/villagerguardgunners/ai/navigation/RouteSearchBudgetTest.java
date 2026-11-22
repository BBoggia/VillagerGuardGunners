package com.gamergaming.villagerguardgunners.ai.navigation;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouteSearchBudgetTest {
    @Test void queuedGuardsGetNextTickPriorityDespiteFixedEntityTickOrder() {
        var budget = new RouteSearchBudget(2);
        var guards = java.util.stream.IntStream.range(0, 6).mapToObj(i -> UUID.randomUUID()).toList();
        Set<UUID> served = new HashSet<>();
        for (int tick = 0; tick < 6; tick++) {
            int admitted = 0;
            for (UUID guard : guards) if (budget.request(guard, tick * 20)) { admitted++; served.add(guard); }
            assertEquals(2, admitted);
        }
        assertEquals(new HashSet<>(guards), served);
    }

    @Test void abandonedHeadLeavesOtherSlotsUsableUntilExpiry() {
        var budget = new RouteSearchBudget(2);
        UUID busy = UUID.randomUUID(), absent = UUID.randomUUID(), active = UUID.randomUUID(), later = UUID.randomUUID();
        assertTrue(budget.request(busy, 0));
        assertTrue(budget.request(busy, 0));
        assertFalse(budget.request(absent, 0));
        assertFalse(budget.request(active, 0));
        assertTrue(budget.request(active, 1));
        assertFalse(budget.request(later, 1));
        assertTrue(budget.request(later, 2));
        assertFalse(budget.request(busy, 2));
        assertTrue(budget.request(busy, 100));
        assertTrue(budget.request(later, 100));
    }

    @Test void cancellationFreesReservedSlotAndExpiredRequestsLosePriority() {
        var budget = new RouteSearchBudget(2);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        assertTrue(budget.request(a, 0));
        assertTrue(budget.request(a, 0));
        assertFalse(budget.request(b, 0));
        assertTrue(budget.request(c, 1));
        assertFalse(budget.request(a, 1));
        budget.cancel(b);
        assertTrue(budget.request(a, 1));
        assertFalse(budget.request(b, 1));
        assertTrue(budget.request(a, 101));
        assertTrue(budget.request(c, 101));
    }

    @Test void slowRequesterRetainsPriorityWhileFastRequestersConsumeEveryOtherSlot() {
        var budget = new RouteSearchBudget(4);
        var fast = java.util.stream.IntStream.range(0, 4).mapToObj(i -> UUID.randomUUID()).toList();
        UUID slow = UUID.randomUUID();
        for (int tick = 0; tick <= 20; tick++) {
            int admitted = 0;
            for (UUID guard : fast) if (budget.request(guard, tick)) admitted++;
            if (tick == 0) assertFalse(budget.request(slow, tick));
            if (tick == 20) { assertTrue(budget.request(slow, tick)); admitted++; }
            assertTrue(admitted >= 3 && admitted <= 4);
        }
    }

    @Test void boundedQueueNeverExceedsThePerTickWorkLimitUnderLoad() {
        var budget = new RouteSearchBudget(4);
        var guards = java.util.stream.IntStream.range(0, 1000).mapToObj(i -> UUID.randomUUID()).toList();
        for (int tick = 0; tick < 10; tick++) {
            int admitted = 0;
            for (UUID guard : guards) if (budget.request(guard, tick)) admitted++;
            assertEquals(4, admitted);
        }
    }
}
