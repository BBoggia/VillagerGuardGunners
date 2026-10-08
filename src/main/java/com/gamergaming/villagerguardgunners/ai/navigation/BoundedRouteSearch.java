package com.gamergaming.villagerguardgunners.ai.navigation;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import net.minecraft.core.BlockPos;

/** Deterministic, bounded A* over already-authorized edges. Unknown edges are never invented. */
public final class BoundedRouteSearch {
    private BoundedRouteSearch() { }
    public record Result(List<BlockPos> positions, int expanded, String status) { }
    private record Visit(BlockPos position, double cost, double estimate) { }

    public static Result find(BlockPos start, BlockPos target, Function<BlockPos, List<BlockPos>> neighbors,
            ToDoubleBiFunction<BlockPos, BlockPos> cost, int nodeLimit, int routeLimit) {
        if (nodeLimit < 1 || nodeLimit > 16_384 || routeLimit < 1 || routeLimit > RouteProgress.MAX_POINTS)
            throw new IllegalArgumentException("Invalid route search budget");
        var queue = new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::estimate)
                .thenComparingLong(v -> v.position().asLong()));
        Map<BlockPos, Double> distances = new HashMap<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        queue.add(new Visit(start, 0, distance(start, target)));
        distances.put(start, 0.0);
        previous.put(start, start);
        int expanded = 0;
        while (!queue.isEmpty() && expanded < nodeLimit) {
            Visit next = queue.remove();
            if (next.cost() != distances.getOrDefault(next.position(), Double.POSITIVE_INFINITY)) continue;
            expanded++;
            if (next.position().equals(target)) {
                List<BlockPos> result = new ArrayList<>();
                BlockPos node = target;
                while (!node.equals(start)) {
                    if (result.size() >= routeLimit - 1) return new Result(List.of(), expanded, "ROUTE_LIMIT");
                    result.add(node); node = previous.get(node);
                }
                result.add(start); Collections.reverse(result);
                return new Result(List.copyOf(result), expanded, "REACHED");
            }
            for (BlockPos neighbor : neighbors.apply(next.position())) {
                double step = cost.applyAsDouble(next.position(), neighbor);
                if (!Double.isFinite(step) || step < distance(next.position(), neighbor)) continue;
                double value = next.cost() + step;
                if (value >= distances.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) continue;
                // Bound frontier storage as well as expansion work.
                if (!distances.containsKey(neighbor) && distances.size() >= nodeLimit) continue;
                distances.put(neighbor, value); previous.put(neighbor, next.position());
                if (queue.size() >= nodeLimit * 4) return new Result(List.of(), expanded, "SEARCH_LIMIT");
                queue.add(new Visit(neighbor, value, value + distance(neighbor, target)));
            }
        }
        return new Result(List.of(), expanded, expanded >= nodeLimit || distances.size() >= nodeLimit ? "SEARCH_LIMIT" : "NO_CONNECTION");
    }
    private static double distance(BlockPos a, BlockPos b) { return Math.sqrt(a.distSqr(b)); }
}
