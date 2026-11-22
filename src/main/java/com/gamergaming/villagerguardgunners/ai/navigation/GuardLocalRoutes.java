package com.gamergaming.villagerguardgunners.ai.navigation;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Loaded-only fallback for nearby ground travel, independent of automatic coverage. */
public final class GuardLocalRoutes {
    public static final int MAX_EXPANDED = 1024, MAX_ROUTES_PER_TICK = 4, MAX_ROUTE_NODES = 256;
    public static final int MAX_RANGE = 128;
    private static final Map<ServerLevel, RouteSearchBudget> BUDGETS = new WeakHashMap<>();
    private static final Map<Guard, String> STATUS = new WeakHashMap<>();
    private GuardLocalRoutes() { }

    public static boolean nativeRegionLoaded(Guard guard, int range) {
        if (!(guard.level() instanceof ServerLevel level) || range < 1 || range > MAX_RANGE) return false;
        int radius = range + 8;
        BlockPos origin = guard.blockPosition();
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++)
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    public static Path create(Guard guard, BlockPos target, int range) {
        if (target == null || range < 1 || range > MAX_RANGE) return reject(guard, "INVALID_SEARCH");
        if (nativeRegionLoaded(guard, range)) {
            Path nativePath = guard.getNavigation().createPath(target, 0, range);
            // Installing a synthetic Path does not update native targetPos. Its
            // createPath cache can consequently return a route to a different goal.
            if (nativePath != null && nativePath.canReach() && target.equals(nativePath.getTarget())
                    && nativePath.getEndNode() != null && target.equals(nativePath.getEndNode().asBlockPos())) {
                cancelRequest(guard); STATUS.put(guard, "NATIVE_ROUTE"); return nativePath;
            }
        }
        // Distant duty/service targets need a complete verified connection. A partial
        // native route is never relabeled as arrival or sent toward an unknown frontier.
        int radius = Math.min(MAX_RANGE, Math.max(range, (int) Math.ceil(guard.position().distanceTo(Vec3.atBottomCenterOf(target))) + 8));
        return search(guard, target, radius);
    }

    public static Path search(Guard guard, BlockPos target, int radius) {
        if (!(guard.level() instanceof ServerLevel level) || target == null || radius < 1 || radius > MAX_RANGE)
            return reject(guard, "INVALID_SEARCH");
        if (level.getChunkSource().getChunkNow(guard.getBlockX() >> 4, guard.getBlockZ() >> 4) == null
                || level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) {
            return reject(guard, "LOCAL_TERRAIN_UNAVAILABLE");
        }
        boolean doors = GuardGroundTraversal.canOpenDoors(guard);
        BlockPos start = GuardGroundTraversal.navigationNode(level, guard.position(), doors);
        if (start == null) return reject(guard, "UNSAFE_START");
        if (start.distSqr(target) > (double) radius * radius) return reject(guard, "OUTSIDE_LOCAL_RANGE");
        RouteSearchBudget budget = BUDGETS.computeIfAbsent(level, ignored -> new RouteSearchBudget(MAX_ROUTES_PER_TICK));
        if (!budget.request(guard.getUUID(), level.getGameTime())) { STATUS.put(guard, "WAITING_FOR_PATH_BUDGET"); return null; }
        var known = com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime.knownRoute(guard, target, MAX_EXPANDED / 4);
        if (!known.isEmpty() && known.stream().allMatch(p -> GuardGroundTraversal.safeStanding(guard, Vec3.atBottomCenterOf(p), true))) {
            STATUS.put(guard, "COVERAGE_ROUTE");
            return fromVerifiedGround(known, target);
        }
        if (Math.abs(target.getY() - start.getY()) > 8) return reject(guard, "OUTSIDE_LOCAL_RANGE");
        boolean[] unknown = {false};
        Map<BlockPos, List<BlockPos>> adjacency = new HashMap<>();
        var result = BoundedRouteSearch.find(start, target, from -> adjacency.computeIfAbsent(from, node -> {
            var neighbors = new ArrayList<BlockPos>(4);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos to = node.relative(direction).offset(0, dy, 0);
                    if (Math.abs(to.getX() - start.getX()) > radius || Math.abs(to.getZ() - start.getZ()) > radius
                            || Math.abs(to.getY() - start.getY()) > 8) continue;
                    if (level.getChunkSource().getChunkNow(to.getX() >> 4, to.getZ() >> 4) == null) {
                        unknown[0] = true; continue;
                    }
                    if (GuardGroundTraversal.planningStep(level, node, to, doors)
                            && GuardGroundTraversal.safeStanding(guard, Vec3.atBottomCenterOf(to), doors)) {
                        neighbors.add(to.immutable()); break;
                    }
                }
            }
            return List.copyOf(neighbors);
        }), (from, to) -> Math.sqrt(from.distSqr(to)) + (from.getY() == to.getY() ? 0 : .25), MAX_EXPANDED * 3 / 4, MAX_ROUTE_NODES);
        STATUS.put(guard, result.positions().isEmpty() && unknown[0] ? "LOCAL_TERRAIN_UNAVAILABLE"
                : result.status() + ", nodes=" + result.expanded());
        if (result.positions().isEmpty()) return null;
        return fromVerifiedGround(result.positions(), target);
    }
    /** Convert a complete ground route whose cells and transitions the caller has already screened. */
    public static Path fromVerifiedGround(List<BlockPos> positions, BlockPos target) {
        var nodes = new ArrayList<Node>(positions.size());
        for (BlockPos position : positions) {
            Node node = new Node(position.getX(), position.getY(), position.getZ());
            // Node defaults to BLOCKED, which tactical consumers correctly reject.
            // This marks the verified route; movement still rechecks live terrain.
            node.type = BlockPathTypes.WALKABLE;
            nodes.add(node);
        }
        return new Path(nodes, target, true);
    }
    private static Path reject(Guard guard, String reason) {
        cancelRequest(guard);
        STATUS.put(guard, reason);
        return null;
    }
    public static void cancelRequest(Guard guard) {
        if (guard.level() instanceof ServerLevel level) {
            RouteSearchBudget budget = BUDGETS.get(level);
            if (budget != null) budget.cancel(guard.getUUID());
        }
    }
    public static String status(Guard guard) { return STATUS.getOrDefault(guard, "IDLE"); }
    public static boolean deferred(Guard guard) {
        String status = status(guard);
        return status.equals("WAITING_FOR_PATH_BUDGET") || status.equals("LOCAL_TERRAIN_UNAVAILABLE");
    }
}
