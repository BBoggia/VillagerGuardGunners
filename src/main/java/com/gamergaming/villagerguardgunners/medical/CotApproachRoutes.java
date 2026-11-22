package com.gamergaming.villagerguardgunners.medical;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

/** Bounded selection of complete, screened native routes to the four cot approaches. */
public final class CotApproachRoutes {
    public static final int MAX_APPROACHES = 4, MAX_NODES = 128;

    private CotApproachRoutes() {}

    public static Path select(List<BlockPos> approaches, Function<BlockPos, Path> paths,
                              Predicate<BlockPos> safeNode) {
        return select(approaches, paths, safeNode, null);
    }

    public static Path select(List<BlockPos> approaches, Function<BlockPos, Path> paths,
                              Predicate<BlockPos> safeNode, net.minecraft.world.phys.Vec3 origin) {
        if (approaches.size() > MAX_APPROACHES) throw new IllegalArgumentException("Too many cot approaches");
        Path best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (BlockPos approach : approaches) {
            Path path;
            try { path = paths.apply(approach); }
            catch (RuntimeException exception) { continue; }
            if (path == null || !path.canReach() || path.isDone()
                    || path.getNodeCount() > MAX_NODES
                    || !path.getNodePos(path.getNodeCount() - 1).equals(approach)) continue;
            int first = path.getNextNodeIndex();
            double cost = origin == null ? 0 : origin.distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(path.getNodePos(first)));
            boolean safe = true;
            for (int i = first; i < path.getNodeCount(); i++) {
                BlockPos node = path.getNodePos(i);
                if (!safeNode.test(node)) { safe = false; break; }
                if (i > first) cost += Math.sqrt(node.distSqr(path.getNodePos(i - 1)));
            }
            if (safe && cost < bestCost) { best = path; bestCost = cost; }
        }
        return best;
    }
    /** Native path queries mutate target state. Recreate and screen the winner before installing it. */
    public static Path prepare(net.minecraft.world.entity.ai.navigation.PathNavigation navigation,
                               net.minecraft.world.phys.Vec3 origin, List<BlockPos> approaches,
                               Predicate<BlockPos> safeNode) {
        Path selected = select(approaches, point -> {
            navigation.stop();
            return navigation.createPath(point, 0);
        }, safeNode, origin);
        navigation.stop();
        if (selected == null) return null;
        return select(List.of(selected.getTarget()), point -> navigation.createPath(point, 0), safeNode, origin);
    }

}
