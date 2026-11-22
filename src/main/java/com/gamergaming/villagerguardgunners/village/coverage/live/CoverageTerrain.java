package com.gamergaming.villagerguardgunners.village.coverage.live;

import com.gamergaming.villagerguardgunners.village.VillageRecord;
import com.gamergaming.villagerguardgunners.village.compat.*;
import com.gamergaming.villagerguardgunners.village.VillageFootprint.Bounds;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import com.gamergaming.villagerguardgunners.ai.navigation.GuardGroundTraversal;

/** Incremental, conservative walking graph. Every block read is cache-only. */
public final class CoverageTerrain {
    public static final int MAX_NODES = 16_384;
    public static final int MAX_ANCHORS = 64;
    public static final int MAX_DOMAIN_CHUNKS = 256, MAX_DOMAIN_REFERENCES = 4_096;
    public static final int MAX_PATH_NODES = 256;
    private static final Comparator<BlockPos> ORDER = Comparator
            .comparingInt((BlockPos p) -> p.getX()).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
    private final ServerLevel level;
    private final VillageRecord record;
    private final Map<Long, List<Bounds>> domain = new HashMap<>();
    private final List<BlockPos> anchors;
    private final List<BlockPos> approaches = new ArrayList<>();
    private final Map<BlockPos, BlockPos> resolved = new HashMap<>();
    private final Map<BlockPos, BlockPos> entranceAnchors = new HashMap<>();
    private final Set<BlockPos> defenseAnchors = new HashSet<>();
    private final Map<BlockPos, List<BlockPos>> edges = new HashMap<>();
    private final Map<BlockPos, BlockPos> roots = new HashMap<>();
    private final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
    private int anchorIndex, probeIndex;
    private boolean complete, limited;
    private long probes, reusedNodes;
    private final Set<Long> invalidChunks = new HashSet<>();
    private CoverageTerrain reuse;
    private Set<Long> repairChunks = Set.of();

    /** Reuse clean observations, but rebuild connectivity from scratch under the normal tick budget. */
    public static CoverageTerrain repair(ServerLevel level, VillageRecord record, CoverageTerrain active) {
        CoverageTerrain next = new CoverageTerrain(level, record);
        next.reuse = active;
        next.repairChunks = Set.copyOf(active.invalidChunks);
        return next;
    }
    public void invalidateChunk(long chunk) { if (domain.containsKey(chunk)) invalidChunks.add(chunk); }
    public boolean repairing() { return !invalidChunks.isEmpty(); }
    public boolean affected(BlockPos p) { return affected(p, invalidChunks); }
    private static boolean affected(BlockPos p, Set<Long> chunks) {
        if (chunks.isEmpty()) return false;
        // One block of support/headroom and edge context across a chunk boundary.
        // Check each intersecting chunk once instead of allocating nine positions.
        for (int x = (p.getX() - 1) >> 4; x <= (p.getX() + 1) >> 4; x++)
            for (int z = (p.getZ() - 1) >> 4; z <= (p.getZ() + 1) >> 4; z++)
                if (chunks.contains(net.minecraft.world.level.ChunkPos.asLong(x, z))) return true;
        return false;
    }
    public long reusedNodes() { return reusedNodes; }

    public CoverageTerrain(ServerLevel level, VillageRecord record) {
        this.level = level;
        this.record = record;
        TreeSet<BlockPos> seeds = new TreeSet<>(ORDER);
        var definitions = com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingDefinitions.snapshot();
        Set<net.minecraft.resources.ResourceLocation> defenseTemplates = definitions.size() > 256 ? Set.of()
                : definitions.values().stream().map(e -> e.template()).collect(java.util.stream.Collectors.toSet());
        var classification = SettlementDefinitions.lookup(record.identity().structure());
        int references = 0;
        outer: for (var piece : record.footprint().pieces()) {
            Bounds b = piece.bounds();
            Bounds expanded = new Bounds(Math.max(-30_000_000, b.minX() - 4), b.minY(), Math.max(-30_000_000, b.minZ() - 4),
                    Math.min(30_000_000, b.maxX() + 4), b.maxY(), Math.min(30_000_000, b.maxZ() + 4));
            for (int x = expanded.minX() >> 4; x <= expanded.maxX() >> 4; x++) {
                for (int z = expanded.minZ() >> 4; z <= expanded.maxZ() >> 4; z++) {
                    long chunk = net.minecraft.world.level.ChunkPos.asLong(x, z);
                    if (++references > MAX_DOMAIN_REFERENCES || (!domain.containsKey(chunk) && domain.size() == MAX_DOMAIN_CHUNKS)) {
                        limited = true; break outer;
                    }
                    domain.computeIfAbsent(chunk, k -> new ArrayList<>()).add(expanded);
                }
            }
            boolean defense = defenseTemplates.contains(piece.template());
            if (classification.kind(piece.template().toString()) == SettlementProfile.Kind.RESIDENCE || defense) {
                var meaning = classification.meaning(piece.template().toString());
                if (meaning.supported()) {
                    for (BlockPos entrance : SettlementObservations.worldPoints(piece, meaning.entrances())) {
                        Direction outward = nearestSide(b, entrance);
                        BlockPos approach = entrance.relative(outward, 2);
                        // Fingerprinted metadata nominates a position; advance still checks the placed terrain.
                        seeds.add(approach);
                        entranceAnchors.put(approach, entrance);
                        if (defense) defenseAnchors.add(approach);
                    }
                }
                // Unknown/damaged entrances retain the conservative discovery fallback.
                BlockPos left = new BlockPos(b.minX() - 2, b.minY() + 1, (b.minZ() + b.maxZ()) / 2);
                BlockPos right = new BlockPos(b.maxX() + 2, b.minY() + 1, (b.minZ() + b.maxZ()) / 2);
                seeds.add(left); seeds.add(right);
                if (defense) { defenseAnchors.add(left); defenseAnchors.add(right); }
            }
        }
        // Cached homes also support pack structures without known template metadata.
        for (BlockPos home : record.homes().stream().sorted(ORDER).toList()) {
            if (seeds.size() > MAX_ANCHORS) { limited = true; break; }
            if (seeds.stream().noneMatch(p -> p.distSqr(home) < 144)) seeds.add(home);
        }
        // Keep verified local components usable; the limited flag reports incomplete village coverage.
        limited = limited || seeds.size() > MAX_ANCHORS;
        anchors = seeds.stream().sorted(Comparator.comparing((BlockPos p) -> !entranceAnchors.containsKey(p)).thenComparing(ORDER)).limit(MAX_ANCHORS).toList();
        complete = anchors.isEmpty();
    }

    /** One unit tests one seed position or expands one node (at most 12 step probes). */
    public int advance(int budget) {
        int used = 0;
        while (!complete && used < budget) {
            used++;
            if (anchorIndex < anchors.size()) {
                BlockPos anchor = anchors.get(anchorIndex);
                BlockPos old = reuse == null ? null : reuse.resolved.get(anchor);
                if (old != null && !affected(old, repairChunks)) {
                    approaches.add(old); resolved.put(anchor, old); add(old);
                    anchorIndex++; probeIndex = 0; continue;
                }
                BlockPos entrance = entranceAnchors.get(anchor);
                if (entrance != null) {
                    BlockPos midway = new BlockPos((anchor.getX() + entrance.getX()) / 2, anchor.getY(), (anchor.getZ() + entrance.getZ()) / 2);
                    if (inDomain(anchor) && walkable(anchor) && GuardGroundTraversal.planningStep(level, entrance, midway, true)
                            && GuardGroundTraversal.planningStep(level, midway, anchor, true)) {
                        approaches.add(anchor); resolved.put(anchor, anchor); add(anchor);
                    }
                    anchorIndex++; probeIndex = 0; continue;
                }
                BlockPos p = anchor.offset(PROBE_OFFSETS.get(probeIndex));
                probeIndex++;
                if (inDomain(p) && Math.floorMod(p.getX(), 32) >= 2 && Math.floorMod(p.getX(), 32) <= 29
                        && Math.floorMod(p.getZ(), 32) >= 2 && Math.floorMod(p.getZ(), 32) <= 29 && walkable(p)) {
                    approaches.add(p.immutable());
                    resolved.put(anchor, p.immutable());
                    add(p);
                    anchorIndex++;
                    probeIndex = 0;
                } else if (probeIndex == 245) {
                    anchorIndex++;
                    probeIndex = 0;
                }
                continue;
            }
            if (pending.isEmpty()) { complete = true; break; }
            BlockPos from = pending.removeFirst();
            if (reuse != null && !affected(from, repairChunks) && reuse.edges.containsKey(from)
                    && reuse.edges.get(from).stream().noneMatch(p -> affected(p, repairChunks))) {
                reusedNodes++;
                for (BlockPos to : reuse.edges.get(from)) {
                    if (!edges.containsKey(to) && edges.size() >= MAX_NODES) { limited = true; complete = true; pending.clear(); break; }
                    connect(from, to);
                }
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos to = from.relative(direction).offset(0, dy, 0);
                    if (!inDomain(to)) continue;
                    probes++;
                    if (!GuardGroundTraversal.planningStep(level, from, to, true)) continue;
                    if (!edges.containsKey(to) && edges.size() == MAX_NODES) {
                        limited = true;
                        complete = true;
                        pending.clear();
                        break;
                    }
                    connect(from, to);
                    break;
                }
                if (complete) break;
            }
        }
        if (complete) reuse = null;
        return used;
    }

    /** Exact completed navigation observations; object identity and survey counters are irrelevant. */
    public boolean sameNavigation(CoverageTerrain other) {
        return other != null && usable() && other.usable()
                && limited == other.limited && anchors.equals(other.anchors) && resolved.equals(other.resolved)
                && defenseAnchors.equals(other.defenseAnchors) && domain.equals(other.domain)
                && edges.keySet().equals(other.edges.keySet())
                // connect() keeps these short adjacency lists unique. Compare their
                // contents without allocating two hash sets for every surveyed node.
                && edges.entrySet().stream().allMatch(e -> e.getValue().size() == other.edges.get(e.getKey()).size()
                        && e.getValue().containsAll(other.edges.get(e.getKey())));
    }

    public boolean complete() { return complete; }
    public boolean usable() { return complete; }
    public boolean limited() { return limited; }
    public int nodeCount() { return edges.size(); }
    public long probes() { return probes; }
    public Set<Long> chunks() { return Collections.unmodifiableSet(domain.keySet()); }
    public List<BlockPos> approaches() { return List.copyOf(approaches); }
    public List<BlockPos> anchors() { return anchors; }
    public BlockPos approach(BlockPos anchor) { return resolved.get(anchor); }
    public com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageLayout.SiteKind kind(BlockPos anchor) {
        return defenseAnchors.contains(anchor) ? com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageLayout.SiteKind.DEFENSE
                : com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageLayout.SiteKind.HOME;
    }

    public UUID component(BlockPos pos) {
        BlockPos node = nodeNear(pos);
        return node == null ? null : UUID.nameUUIDFromBytes((record.identity().id() + ":walk:" + root(node))
                .getBytes(StandardCharsets.UTF_8));
    }

    public BlockPos nodeNear(BlockPos position) {
        for (int dy : new int[]{0, -1, 1}) {
            BlockPos p = position.offset(0, dy, 0);
            if (!affected(p) && edges.containsKey(p)) return p;
        }
        return null;
    }

    /** Bounded graph search; constructs no PathNavigationRegion and requests no chunks. */
    public List<BlockPos> path(BlockPos position, BlockPos target) {
        return path(position, target, MAX_NODES);
    }
    public List<BlockPos> path(BlockPos position, BlockPos target, int workLimit) {
        BlockPos start = nodeNear(position);
        if (!usable() || start == null || !edges.containsKey(target) || !root(start).equals(root(target))) return List.of();
        return com.gamergaming.villagerguardgunners.ai.navigation.BoundedRouteSearch.find(start, target,
                this::availableNeighbors,
                (from, to) -> Math.sqrt(from.distSqr(to)) + (from.getY() == to.getY() ? 0 : .25)
                        + (edges.getOrDefault(to, List.of()).size() <= 2 ? .15 : 0), Math.min(MAX_NODES, workLimit), MAX_PATH_NODES).positions();
    }

    private List<BlockPos> availableNeighbors(BlockPos p) {
        List<BlockPos> neighbors = edges.getOrDefault(p, List.of());
        if (invalidChunks.isEmpty()) return neighbors;
        return affected(p) ? List.of() : neighbors.stream().filter(n -> !affected(n)).toList();
    }

    public boolean walkable(BlockPos feet) {
        probes++;
        return !affected(feet) && GuardGroundTraversal.planningStanding(level, feet);
    }

    /** A cached route edge must still exist, and its real support/headroom must still be safe. */
    public boolean safeTransition(BlockPos from, BlockPos to) {
        return !affected(from) && !affected(to) && edges.getOrDefault(from, List.of()).contains(to)
                && GuardGroundTraversal.planningStep(level, from, to, true);
    }
    public static boolean safeStep(ServerLevel level, BlockPos from, BlockPos to) {
        return GuardGroundTraversal.safeStep(level, from, to);
    }
    public static boolean safeStandingPosition(ServerLevel level, BlockPos feet) {
        return GuardGroundTraversal.safeStandingPosition(level, feet);
    }
    private boolean inDomain(BlockPos pos) {
        var boxes = domain.get(net.minecraft.world.level.ChunkPos.asLong(pos));
        return boxes != null && boxes.stream().anyMatch(b -> b.contains(pos)
                && pos.getY() >= b.minY() - 8 && pos.getY() <= b.maxY() + 4);
    }
    private void connect(BlockPos from, BlockPos to) {
        add(to);
        if (!edges.get(from).contains(to)) edges.get(from).add(to);
        if (!edges.get(to).contains(from)) edges.get(to).add(from);
        union(from, to);
    }
    private static Direction nearestSide(Bounds b, BlockPos p) {
        return java.util.stream.Stream.of(Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH)
                .min(Comparator.comparingInt(d -> switch (d) {
                    case WEST -> Math.abs(p.getX() - b.minX()); case EAST -> Math.abs(p.getX() - b.maxX());
                    case NORTH -> Math.abs(p.getZ() - b.minZ()); default -> Math.abs(p.getZ() - b.maxZ());
                })).orElse(Direction.NORTH);
    }
    private static final List<BlockPos> PROBE_OFFSETS = probeOffsets();
    private static List<BlockPos> probeOffsets() {
        var positions = new ArrayList<BlockPos>();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = -2; y <= 2; y++) positions.add(new BlockPos(x, y, z));
        positions.sort(Comparator.comparingDouble((BlockPos p) -> p.distSqr(BlockPos.ZERO)).thenComparing(ORDER));
        return List.copyOf(positions);
    }
    private void add(BlockPos pos) {
        if (!edges.containsKey(pos)) {
            BlockPos immutable = pos.immutable();
            edges.put(immutable, new ArrayList<>());
            roots.put(immutable, immutable);
            pending.add(immutable);
        }
    }
    private BlockPos root(BlockPos p) {
        BlockPos first = p;
        while (!roots.get(p).equals(p)) p = roots.get(p);
        while (!roots.get(first).equals(p)) {
            BlockPos next = roots.put(first, p);
            first = next;
        }
        return p;
    }
    private void union(BlockPos a, BlockPos b) {
        a = root(a); b = root(b);
        if (ORDER.compare(a, b) < 0) roots.put(b, a); else roots.put(a, b);
    }
}
