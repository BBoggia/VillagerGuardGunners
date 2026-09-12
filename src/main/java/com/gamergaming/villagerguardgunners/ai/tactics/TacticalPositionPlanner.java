package com.gamergaming.villagerguardgunners.ai.tactics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
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

    public record Plan(Path path, BlockPos destination, double score, String reason) { }
    private record Candidate(BlockPos position, double score) { }

    public TacticalPositionPlanner(Guard guard) { this.guard = guard; }

    public Plan plan(List<ThreatMemory.Observation> threats, TacticalAssignmentBounds bounds,
            Set<BlockPos> failed, boolean reposition, List<Vec3> usefulAllies) {
        supportPositions = List.copyOf(usefulAllies);
        sightRays = 0;
        candidatesChecked = 0;
        pathsBuilt = 0;
        Vec3 start = guard.position();
        if (!(guard.level() instanceof ServerLevel) || !pathRegionLoaded()) return new Plan(null, null, 0, "UNLOADED_REGION");
        // REPOSITION is requested only after the firing adapter observed a blocked lane.
        double baseline = score(start, start, threats, bounds, reposition) - (reposition ? 4 : 0);
        double baselineExposure = exposureScore(start, threats, reposition) + roomScore(start);
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
            double radius = 6 + ring * 4;
            for (int angle = 0; angle < 16; angle++) {
                candidatesChecked++;
                double direction = heading + angle * Math.PI / 8;
                BlockPos horizontal = BlockPos.containing(start.add(Math.cos(direction) * radius, 0, Math.sin(direction) * radius));
                for (int dy : new int[]{0, 1, -1, 2, -2}) {
                    BlockPos position = horizontal.offset(0, dy, 0);
                    Vec3 point = Vec3.atBottomCenterOf(position);
                    if (nearFailedDestination(position, failed) || !bounds.contains(point) || !safeStanding(point)) continue;
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
        for (int i = 0; i < Math.min(4, candidates.size()); i++) {
            Candidate candidate = candidates.get(i);
            Vec3 point = Vec3.atBottomCenterOf(candidate.position());
            double value = candidate.score() + exposureScore(point, threats, reposition) + roomScore(point);
            if (hazardousStart || value > baseline + baselineExposure + .5) finalists.add(new Candidate(candidate.position(), value));
        }
        finalists.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparingLong(c -> c.position().asLong()));
        if (finalists.isEmpty()) return new Plan(null, null, baseline, "NO_IMPROVEMENT");
        Candidate candidate = finalists.get(0);
        // One native construction per shared slot. Rejected destinations let the next slot try another.
        pathsBuilt++;
        Path path = guard.getNavigation().createPath(candidate.position(), 0, PATH_RANGE);
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
        double desired = reposition ? -Math.abs(nearest - 10) : Math.min(nearest, 20);
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
                if (!leavingStart && !safeStanding(sample)) return false;
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

    public boolean safeStanding(Vec3 point) {
        BlockPos feet = BlockPos.containing(point);
        if (!loaded(feet) || !loaded(feet.below()) || !loaded(feet.above())
                || guard.level().isOutsideBuildHeight(feet) || guard.level().isOutsideBuildHeight(feet.above())
                || !guard.level().getWorldBorder().isWithinBounds(feet)) return false;
        if (!guard.level().getBlockState(feet.below()).isFaceSturdy(guard.level(), feet.below(), Direction.UP)) return false;
        var body = guard.getBoundingBox().move(point.subtract(guard.position())).deflate(.01);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, feet.getY() - 1, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (!loaded(pos)) return false;
            var state = guard.level().getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                    || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) return false;
            var type = state.getBlockPathType(guard.level(), pos, guard);
            if (type != null && dangerousPathType(type)) return false;
        }
        return guard.level().noCollision(guard, body);
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
    public int pathsBuilt() { return pathsBuilt; }
}
