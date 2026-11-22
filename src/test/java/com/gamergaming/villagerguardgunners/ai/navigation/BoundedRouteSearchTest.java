package com.gamergaming.villagerguardgunners.ai.navigation;

import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedRouteSearchTest {
    @Test void choosesLongerOpenRouteWhenNarrowShortcutCostsMore() {
        BlockPos a = new BlockPos(0, 0, 0), b = new BlockPos(4, 0, 0);
        Map<BlockPos, List<BlockPos>> edges = new HashMap<>();
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 1; z++) {
            BlockPos p = new BlockPos(x, 0, z);
            edges.put(p, java.util.stream.Stream.of(p.east(), p.west(), p.north(), p.south())
                    .filter(n -> n.getX() >= 0 && n.getX() <= 4 && n.getZ() >= 0 && n.getZ() <= 1).toList());
        }
        var result = BoundedRouteSearch.find(a, b, edges::get,
                (from, to) -> 1 + (to.getZ() == 0 && to.getX() > 0 && to.getX() < 4 ? 2 : 0), 32, 32);
        assertEquals("REACHED", result.status());
        assertTrue(result.positions().stream().anyMatch(p -> p.getZ() == 1));
        assertEquals(7, result.positions().size());
    }
    @Test void rejectsUnknownEdgesAndHonorsBothWorkAndLengthLimits() {
        var start = BlockPos.ZERO;
        assertTrue(BoundedRouteSearch.find(start, start.east(10), p -> List.of(p.east()), (a,b) -> 1, 4, 20).positions().isEmpty());
        var result = BoundedRouteSearch.find(start, start.east(10), p -> List.of(p.east()), (a,b) -> 1, 20, 4);
        assertEquals("ROUTE_LIMIT", result.status());
        assertTrue(result.expanded() <= 20);
        assertEquals("NO_CONNECTION", BoundedRouteSearch.find(start, start.east(), p -> List.of(), (a,b) -> 1, 20, 20).status());
    }
}
