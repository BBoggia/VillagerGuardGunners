package com.gamergaming.villagerguardgunners.village.coverage.live;

import com.gamergaming.villagerguardgunners.village.VillageRecord;
import com.gamergaming.villagerguardgunners.village.compat.*;
import com.gamergaming.villagerguardgunners.village.VillageFootprint.Bounds;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

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
    private final CachedBlocks blocks;
    private final Map<Long, List<Bounds>> domain = new HashMap<>();
    private final List<BlockPos> anchors;
    private final List<BlockPos> approaches = new ArrayList<>();
    private final Map<BlockPos, BlockPos> resolved = new HashMap<>();
    private final Set<BlockPos> defenseAnchors = new HashSet<>();
    private final Map<BlockPos, List<BlockPos>> edges = new HashMap<>();
    private final Map<BlockPos, BlockPos> roots = new HashMap<>();
    private final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
    private int anchorIndex, probeIndex;
    private boolean complete, limited;
    private long probes;

    public CoverageTerrain(ServerLevel level, VillageRecord record) {
        this.level = level;
        this.record = record;
        this.blocks = new CachedBlocks(level);
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
                // Probe outside the building bounds near its foundation height.
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
        // Publish no silently truncated village. Oversized observations remain diagnostic.
        limited = limited || seeds.size() > MAX_ANCHORS;
        anchors = limited ? List.of() : List.copyOf(seeds);
        complete = limited || anchors.isEmpty();
    }

    /** One unit tests one seed position or expands one node (at most 12 step probes). */
    public int advance(int budget) {
        int used = 0;
        while (!complete && used < budget) {
            used++;
            if (anchorIndex < anchors.size()) {
                BlockPos anchor = anchors.get(anchorIndex);
                int dy = probeIndex % 5 - 2;
                int horizontal = probeIndex / 5;
                BlockPos p = anchor.offset(horizontal % 7 - 3, dy, horizontal / 7 - 3);
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
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos to = from.relative(direction).offset(0, dy, 0);
                    if (!inDomain(to)) continue;
                    probes++;
                    if (!safeStep(level, blocks, from, to)) continue;
                    if (!edges.containsKey(to) && edges.size() == MAX_NODES) {
                        limited = true;
                        complete = true;
                        pending.clear();
                        break;
                    }
                    add(to);
                    if (!edges.get(from).contains(to)) edges.get(from).add(to);
                    if (!edges.get(to).contains(from)) edges.get(to).add(from);
                    union(from, to);
                    break;
                }
                if (limited) break;
            }
        }
        return used;
    }

    /** Exact completed navigation observations; object identity and survey counters are irrelevant. */
    public boolean sameNavigation(CoverageTerrain other) {
        return other != null && usable() && other.usable()
                && anchors.equals(other.anchors) && resolved.equals(other.resolved)
                && defenseAnchors.equals(other.defenseAnchors) && domain.equals(other.domain)
                && edges.equals(other.edges);
    }

    public boolean complete() { return complete; }
    public boolean usable() { return complete && !limited; }
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
            if (edges.containsKey(p)) return p;
        }
        return null;
    }

    /** Bounded graph search; constructs no PathNavigationRegion and requests no chunks. */
    public List<BlockPos> path(BlockPos position, BlockPos target) {
        BlockPos start = nodeNear(position);
        if (!usable() || start == null || !edges.containsKey(target) || !root(start).equals(root(target))) return List.of();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        queue.add(start);
        previous.put(start, start);
        while (!queue.isEmpty() && previous.size() <= MAX_NODES) {
            BlockPos p = queue.removeFirst();
            if (p.equals(target)) {
                List<BlockPos> result = new ArrayList<>();
                while (!p.equals(start)) {
                    result.add(p);
                    if (result.size() == MAX_PATH_NODES) return List.of();
                    p = previous.get(p);
                }
                result.add(start);
                Collections.reverse(result);
                return List.copyOf(result);
            }
            for (BlockPos neighbor : edges.get(p)) {
                if (!previous.containsKey(neighbor)) { previous.put(neighbor, p); queue.add(neighbor); }
            }
        }
        return List.of();
    }

    public boolean walkable(BlockPos feet) {
        probes++;
        return walkable(level, blocks, feet);
    }

    /** A cached route edge must still exist, and its real support/headroom must still be safe. */
    public boolean safeTransition(BlockPos from, BlockPos to) {
        return edges.getOrDefault(from, List.of()).contains(to) && safeStep(level, blocks, from, to);
    }
    public static boolean safeStep(ServerLevel level, BlockPos from, BlockPos to) {
        return safeStep(level, new CachedBlocks(level), from, to);
    }
    private static boolean safeStep(ServerLevel level, CachedBlocks blocks, BlockPos from, BlockPos to) {
        long horizontal = Math.abs((long)from.getX() - to.getX()) + Math.abs((long)from.getZ() - to.getZ());
        long dy = (long)to.getY() - from.getY();
        if (horizontal != 1 || Math.abs(dy) > 1) return false;
        double fromHeight = standingHeight(level, blocks, from);
        double toHeight = standingHeight(level, blocks, to);
        if (!Double.isFinite(fromHeight) || !Double.isFinite(toHeight)
                || Math.abs(toHeight - fromHeight) > 1.0) return false;
        return dy == 0 || clear(blocks, (dy > 0 ? from : to).above(2));
    }

    public static boolean safeStandingPosition(ServerLevel level, BlockPos feet) {
        return walkable(level, new CachedBlocks(level), feet);
    }

    private static boolean walkable(ServerLevel level, CachedBlocks blocks, BlockPos feet) {
        return Double.isFinite(standingHeight(level, blocks, feet));
    }

    // Graph nodes remain the air cell above support. Native navigation resolves
    // its fractional floor height when executing the path.
    private static double standingHeight(ServerLevel level, CachedBlocks blocks, BlockPos feet) {
        if (!level.getWorldBorder().isWithinBounds(feet)
                || level.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4) == null
                || !clear(blocks, feet) || !clear(blocks, feet.above())) return Double.NaN;
        BlockState floor = blocks.getBlockState(feet.below());
        boolean campfire = floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE);
        if (floor.is(Blocks.MAGMA_BLOCK)
                || (campfire && floor.getValue(BlockStateProperties.LIT))
                || floor.is(Blocks.CACTUS) || floor.is(Blocks.POWDER_SNOW)) return Double.NaN;
        // Water beneath a full-height deck top does not immerse the guard.
        // Keep submerged bottom slabs/campfires and open trapdoors excluded.
        boolean dryWaterloggedTop = floor.getFluidState().is(net.minecraft.tags.FluidTags.WATER)
                && ((floor.getBlock() instanceof SlabBlock && floor.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP)
                || (floor.getBlock() instanceof TrapDoorBlock && floor.getValue(BlockStateProperties.HALF) == Half.TOP
                && !floor.getValue(BlockStateProperties.OPEN)));
        if (!floor.getFluidState().isEmpty() && !dryWaterloggedTop) return Double.NaN;
        VoxelShape shape = floor.getCollisionShape(blocks, feet.below());
        boolean supported = !shape.isEmpty() && shape.max(Direction.Axis.Y) >= (campfire ? 7.0 / 16.0 : 0.5)
                && shape.max(Direction.Axis.Y) <= 1.0
                && shape.min(Direction.Axis.X) <= 0.1 && shape.max(Direction.Axis.X) >= 0.9
                && shape.min(Direction.Axis.Z) <= 0.1 && shape.max(Direction.Axis.Z) >= 0.9;
        return supported ? feet.getY() - 1 + shape.max(Direction.Axis.Y) : Double.NaN;
    }

    private boolean clear(BlockPos p) { return clear(blocks, p); }
    private static boolean clear(CachedBlocks blocks, BlockPos p) {
        BlockState state = blocks.getBlockState(p);
        return state.getFluidState().isEmpty() && state.getCollisionShape(blocks, p).isEmpty()
                && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE) && !state.is(Blocks.SWEET_BERRY_BUSH)
                && !state.is(Blocks.COBWEB) && !state.is(Blocks.WITHER_ROSE) && !state.is(Blocks.POWDER_SNOW);
    }
    private boolean inDomain(BlockPos pos) {
        var boxes = domain.get(net.minecraft.world.level.ChunkPos.asLong(pos));
        return boxes != null && boxes.stream().anyMatch(b -> b.contains(pos)
                && pos.getY() >= b.minY() - 8 && pos.getY() <= b.maxY() + 4);
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
    private static final class CachedBlocks implements BlockGetter {
        private final ServerLevel level;
        CachedBlocks(ServerLevel level) { this.level = level; }
        public BlockState getBlockState(BlockPos p) {
            var chunk = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
            return chunk == null ? Blocks.BARRIER.defaultBlockState() : chunk.getBlockState(p);
        }
        public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
        public BlockEntity getBlockEntity(BlockPos p) { return null; }
        public int getHeight() { return level.getHeight(); }
        public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
}
