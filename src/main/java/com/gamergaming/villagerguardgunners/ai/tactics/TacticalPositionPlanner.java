package com.gamergaming.villagerguardgunners.ai.tactics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Bounded local search using native paths. No terrain lookup may request an unloaded chunk. */
public final class TacticalPositionPlanner {
    public static final int CANDIDATE_LIMIT = 48;
    public static final int PATH_RANGE = 24;
    public static final int MAX_NODES = 48;
    private final Guard guard;
    private int candidatesChecked;
    private int pathsBuilt;
    private int sightRays;
    private List<Vec3> supportPositions = List.of();
    private TacticalFiringLane lane;

    public record Plan(Path path, BlockPos destination, double score, String reason) { }
    private record Candidate(BlockPos position, double score) { }

    public TacticalPositionPlanner(Guard guard) { this.guard = guard; }

    public Plan plan(List<ThreatMemory.Observation> threats, TacticalAssignmentBounds bounds,
            Set<BlockPos> failed, boolean reposition, List<Vec3> usefulAllies) {
        return plan(threats, bounds, failed, reposition, usefulAllies, null);
    }

    public Plan plan(List<ThreatMemory.Observation> threats, TacticalAssignmentBounds bounds,
            Set<BlockPos> failed, boolean reposition, List<Vec3> usefulAllies, TacticalFiringLane.Request firing) {
        supportPositions = List.copyOf(usefulAllies);
        sightRays = 0;
        candidatesChecked = 0;
        pathsBuilt = 0;
        lane = reposition ? TacticalFiringLane.capture(guard, firing) : null;
        if (reposition && lane == null) return new Plan(null, null, 0, "NO_FIRING_CONTEXT");
        var reserved = reposition ? FiringPositionReservations.positions(guard) : List.<Vec3>of();
        Vec3 start = guard.position();
        if (!(guard.level() instanceof ServerLevel) ) return new Plan(null, null, 0, "UNLOADED_REGION");
        // REPOSITION is requested only after the firing adapter observed a blocked lane.
        double baseline = score(start, start, threats, bounds, reposition) - (reposition ? 4 : 0);
        double baselineExposure = (reposition ? 0 : exposureScore(start, threats, false)) + roomScore(start);
        boolean hazardousStart = !safeStanding(start);
        Vec3 away = Vec3.ZERO;
        for (var threat : threats) {
            Vec3 delta = start.subtract(threat.position()).multiply(1, 0, 1);
            away = away.add(delta.scale(1 / Math.max(1, delta.lengthSqr())));
        }
        double heading = away.horizontalDistanceSqr() < .001 ? guard.getYRot() * Math.PI / 180
                : Math.atan2(away.z, away.x);
        List<Candidate> candidates = new ArrayList<>(CANDIDATE_LIMIT);
        for (int ring = 0; ring < 3; ring++) {
            double radius = reposition ? new int[]{3, 6, 10}[ring] : 6 + ring * 4;
            for (int angle = 0; angle < 16; angle++) {
                candidatesChecked++;
                double direction = heading + angle * Math.PI / 8;
                BlockPos horizontal = BlockPos.containing(start.add(Math.cos(direction) * radius, 0, Math.sin(direction) * radius));
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    BlockPos position = horizontal.offset(0, dy, 0);
                    Vec3 point = standingPosition(position);
                    if (point == null) continue;
                    if (nearFailedDestination(position, failed) || !bounds.contains(point) || !safeStanding(point)) continue;
                    if (reposition && (!lane.plausibleFrom(point)
                            || reserved.stream().anyMatch(other -> other.distanceToSqr(point) < 4))) continue;
                    double score = score(point, start, threats, bounds, reposition);
                    if (hazardousStart || score > baseline + .5) candidates.add(new Candidate(position, score));
                    break;
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparingLong(c -> c.position().asLong()));
        if (candidates.isEmpty()) return new Plan(null, null, baseline, hazardousStart ? "HAZARD_NO_EXIT" : "NO_IMPROVEMENT");
        List<Candidate> finalists = new ArrayList<>();
        for (int i = 0; i < Math.min(reposition ? 8 : 4, candidates.size()); i++) {
            Candidate candidate = candidates.get(i);
            Vec3 point = standingPosition(candidate.position());
            if (point == null) continue;
            if (reposition && !lane.clearFrom(point)) continue;
            double value = candidate.score() + (reposition ? 2 : exposureScore(point, threats, false)) + roomScore(point);
            if (hazardousStart || value > baseline + baselineExposure + .5) finalists.add(new Candidate(candidate.position(), value));
        }
        finalists.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparingLong(c -> c.position().asLong()));
        if (finalists.isEmpty()) return new Plan(null, null, baseline, "NO_IMPROVEMENT");
        Candidate candidate = finalists.get(0);
        // One native construction per shared slot. Rejected destinations let the next slot try another.
        pathsBuilt++;
        Path path = com.gamergaming.villagerguardgunners.ai.navigation.GuardLocalRoutes.create(guard, candidate.position(), PATH_RANGE);
        if (path == null && com.gamergaming.villagerguardgunners.ai.navigation.GuardLocalRoutes.deferred(guard)) return new Plan(null, null, 0, "WAITING_FOR_TERRAIN_OR_BUDGET");
        if (path == null || !path.canReach() || path.getNodeCount() > MAX_NODES
                || path.getEndNode() == null
                || path.getEndNode().asBlockPos().distSqr(candidate.position()) > 1) {
            return new Plan(null, candidate.position(), candidate.score(), "UNREACHABLE");
        }
        if (!safeRoute(path, 0, threats, bounds, hazardousStart)) {
            return new Plan(null, candidate.position(), candidate.score(), "UNSAFE_ROUTE");
        }
        return new Plan(path, candidate.position(), candidate.score(), "ACCEPTED");
    }

    /** Fixed sighting geometry only: this method never receives the hidden target entity. */
    public Plan planPeek(Vec3 sighting, Vec3 origin, List<ThreatMemory.Observation> threats,
                         TacticalAssignmentBounds bounds, Set<BlockPos> failed) {
        candidatesChecked = 0; pathsBuilt = 0; sightRays = 0;
        if (!(guard.level() instanceof ServerLevel level) )
            return new Plan(null, null, 0, "UNLOADED_REGION");
        Vec3 start = guard.position();
        var rings = new ArrayList<List<Candidate>>(3);
        for (int radius : new int[]{3, 6, 10}) {
            var ring = new ArrayList<Candidate>(16);
            for (int angle = 0; angle < 16; angle++) {
                candidatesChecked++;
                double direction = angle * Math.PI / 8;
                BlockPos column = BlockPos.containing(start.add(Math.cos(direction) * radius, 0, Math.sin(direction) * radius));
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    BlockPos node = column.offset(0, dy, 0);
                    Vec3 point = standingPosition(node);
                    if (point == null || origin.distanceToSqr(point) > 100 || !bounds.contains(point)
                            || nearFailedDestination(node, failed) || !safeStanding(point)
                            || point.distanceToSqr(sighting) < 16) continue;
                    double score = -point.distanceTo(start) * .5 - point.distanceTo(sighting) * .15;
                    ring.add(new Candidate(node, score)); break;
                }
            }
            ring.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparingLong(c -> c.position().asLong()));
            rings.add(ring);
        }
        // Preserve the eight-check budget while giving farther rings a turn.
        // Otherwise nearby blind spots can exclude every useful wider peek.
        var finalists = new ArrayList<Candidate>(8);
        for (int rank = 0; rank < 16 && finalists.size() < 8; rank++) {
            for (var ring : rings) {
                if (rank < ring.size()) finalists.add(ring.get(rank));
                if (finalists.size() == 8) break;
            }
        }
        for (Candidate candidate : finalists) {
            Vec3 point = standingPosition(candidate.position());
            Vec3 eye = point.add(0, guard.getEyeHeight(), 0), known = sighting.add(0, 1.3, 0);
            if (!TacticalFiringLane.loadedCorridor(level, eye, known)) continue;
            sightRays++;
            if (level.clip(new net.minecraft.world.level.ClipContext(eye, known,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, guard)).getType()
                    != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            pathsBuilt++;
            Path path = com.gamergaming.villagerguardgunners.ai.navigation.GuardLocalRoutes.create(guard, candidate.position(), PATH_RANGE);
            if (path == null && com.gamergaming.villagerguardgunners.ai.navigation.GuardLocalRoutes.deferred(guard)) return new Plan(null, null, 0, "WAITING_FOR_TERRAIN_OR_BUDGET");
            if (path == null || !path.canReach() || path.getEndNode() == null
                    || path.getEndNode().asBlockPos().distSqr(candidate.position()) > 1
                    || !safePeekRoute(path, threats, bounds, origin))
                return new Plan(null, candidate.position(), candidate.score(), "UNSAFE_PEEK_ROUTE");
            return new Plan(path, candidate.position(), candidate.score(), "ACCEPTED");
        }
        return new Plan(null, null, 0, "NO_PEEK");
    }

    public boolean safePeekRoute(Path path, List<ThreatMemory.Observation> threats,
                                 TacticalAssignmentBounds bounds, Vec3 origin) {
        return path != null && TacticalNavigationController.remainingDistance(path, path.getNextNodeIndex(), guard.position()) <= 16
                && safeRoute(path, path.getNextNodeIndex(), threats, bounds, false)
                && safeRoute(path, path.getNextNodeIndex(), threats, new TacticalAssignmentBounds(origin, 10), false);
    }

    static boolean nearFailedDestination(BlockPos position, Set<BlockPos> failed) {
        // Knockback and moving threats shift candidate rings. Preserve a small failed area,
        // rather than immediately retrying the adjacent block on the same blocked approach.
        for (BlockPos previous : failed) {
            double dx = position.getX() - previous.getX(), dz = position.getZ() - previous.getZ();
            if (dx * dx + dz * dz <= 9 && Math.abs(position.getY() - previous.getY()) <= 2) return true;
        }
        return false;
    }

    private double score(Vec3 point, Vec3 start, List<ThreatMemory.Observation> threats,
            TacticalAssignmentBounds bounds, boolean reposition) {
        double nearest = 20, pressure = 0;
        long now = guard.level().getGameTime();
        for (var threat : threats) {
            double distance = point.distanceTo(threat.position());
            nearest = Math.min(nearest, distance);
            pressure += threat.confidence(now) * (threat.explosive() ? 12 : 6) / Math.max(1, distance);
        }
        double travel = start.distanceTo(point);
        double assignmentCost = Double.isFinite(bounds.radius()) ? point.distanceTo(bounds.center()) * .03 : 0;
        double desired = reposition ? lane.rangeScore(point) : Math.min(nearest, 20);
        double support = 0;
        for (Vec3 ally : supportPositions) support += Math.max(0, 1 - point.distanceTo(ally) / 16) * .4;
        return desired - pressure + support - travel * .12 - assignmentCost;
    }

    private double roomScore(Vec3 point) {
        // Full clearance queries are reserved for the baseline and four finalists.
        int exits = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (safeStanding(point.relative(direction, 1))) exits++;
        }
        return exits * .2;
    }

    private double exposureScore(Vec3 point, List<ThreatMemory.Observation> threats, boolean reposition) {
        double value = 0;
        for (int i = 0; i < Math.min(2, threats.size()) && sightRays < 10; i++) {
            var threat = threats.get(i);
            Vec3 from = point.add(0, guard.getEyeHeight(), 0), to = threat.position().add(0, 1, 0);
            boolean regionLoaded = true;
            for (int x = ((int) Math.floor(Math.min(from.x, to.x))) >> 4; x <= ((int) Math.floor(Math.max(from.x, to.x))) >> 4; x++) {
                for (int z = ((int) Math.floor(Math.min(from.z, to.z))) >> 4; z <= ((int) Math.floor(Math.max(from.z, to.z))) >> 4; z++) {
                    if (!((ServerLevel) guard.level()).hasChunk(x, z)) regionLoaded = false;
                }
            }
            if (!regionLoaded) continue;
            sightRays++;
            boolean clear = guard.level().clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, guard)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
            if (reposition) value += clear ? 2 : 0;
            else value += clear ? 0 : (threat.ranged() ? 2 : .5);
        }
        return value;
    }

    public boolean safeRoute(Path path, int from, List<ThreatMemory.Observation> threats,
            TacticalAssignmentBounds bounds, boolean hazardousStart) {
        if (path == null || path.getNodeCount() > MAX_NODES) return false;
        Vec3 previous = guard.position();
        double length = 0;
        for (int i = from; i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            if (node.type != null && dangerousPathType(node.type) && !(hazardousStart && length <= 1.5)) return false;
            Vec3 point = Vec3.atBottomCenterOf(path.getNodePos(i));
            double segment = previous.distanceTo(point);
            length += segment;
            if (length > 48 || segment > 3) return false;
            int steps = Math.max(1, (int) Math.ceil(segment * 2));
            for (int j = 1; j <= steps; j++) {
                Vec3 sample = previous.lerp(point, (double) j / steps);
                if (!bounds.contains(sample)) return false;
                // The starting hazard can be left, but never crossed again later in the route.
                boolean leavingStart = hazardousStart && length <= 1.5;
                if (!leavingStart && !com.gamergaming.villagerguardgunners.ai.navigation.GuardGroundTraversal.safeStanding(guard, sample, true)) return false;
                for (var threat : threats) {
                    double original = guard.position().distanceTo(threat.position());
                    double distance = sample.distanceTo(threat.position());
                    double clearance = threat.explosive() ? 8 : 4;
                    if (distance < Math.min(clearance, original) - .35) return false;
                }
            }
            previous = point;
        }
        return true;
    }

    public Vec3 standingPosition(BlockPos node) {
        return guard.level() instanceof ServerLevel level
                ? com.gamergaming.villagerguardgunners.ai.navigation.GuardGroundTraversal.surface(level, Vec3.atBottomCenterOf(node)) : null;
    }

    public boolean safeStanding(Vec3 point) {
        return com.gamergaming.villagerguardgunners.ai.navigation.GuardGroundTraversal.safeStanding(guard, point);
    }

    private boolean dangerousPathType(net.minecraft.world.level.pathfinder.BlockPathTypes type) {
        return guard.getPathfindingMalus(type) < 0 || type.name().contains("DAMAGE") || type.name().contains("DANGER");
    }

    public boolean pathRegionLoaded() {
        // The explicit-range native overload builds a PathNavigationRegion with range + 8 padding.
        int radius = PATH_RANGE + 8;
        BlockPos origin = guard.blockPosition();
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) {
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
                if (!((ServerLevel) guard.level()).hasChunk(x, z)) return false;
            }
        }
        return true;
    }
    private boolean loaded(BlockPos pos) {
        return guard.level() instanceof ServerLevel level && level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
    public int candidatesChecked() { return candidatesChecked; }
    public int sightRays() { return sightRays; }
    public int pathsBuilt() { return pathsBuilt; }
}
