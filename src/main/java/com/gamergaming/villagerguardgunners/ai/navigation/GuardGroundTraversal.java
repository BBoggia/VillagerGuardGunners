package com.gamergaming.villagerguardgunners.ai.navigation;

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

import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Loaded-only ground traversal shared by combat and village coverage. */
public final class GuardGroundTraversal {
    private GuardGroundTraversal() { }

    public static double standingHeight(ServerLevel level, BlockPos node) {
        return standingHeight(level, new CachedBlocks(level), node);
    }

    /** Accept both native air-cell nodes and actual fractional slab/campfire positions. */
    public static Vec3 surface(ServerLevel level, Vec3 point) {
        return surface(level, point, false);
    }

    public static Vec3 surface(ServerLevel level, Vec3 point, boolean planDoors) {
        return surface(level, new CachedBlocks(level, planDoors), point);
    }

    private static Vec3 surface(ServerLevel level, CachedBlocks blocks, Vec3 point) {
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return null;
        BlockPos cell = BlockPos.containing(point);
        for (int dy = 0; dy <= 1; dy++) {
            double height = standingHeight(level, blocks, cell.offset(0, dy, 0));
            if (Double.isFinite(height) && Math.abs(height - point.y) <= 1.01)
                return new Vec3(point.x, height, point.z);
        }
        return null;
    }

    /** Native navigation addresses the air cell above a fractional supporting block. */
    public static BlockPos navigationNode(ServerLevel level, Vec3 point) {
        return navigationNode(level, point, false);
    }

    public static BlockPos navigationNode(ServerLevel level, Vec3 point, boolean planDoors) {
        Vec3 floor = surface(level, point, planDoors);
        return floor == null ? null : BlockPos.containing(floor.x, Math.ceil(floor.y), floor.z);
    }

    public static boolean safeStanding(Guard guard, Vec3 point) {
        return safeStanding(guard, point, false);
    }

    /** Door transitions are hypothetical during planning; execution must open and recheck them. */
    public static boolean safeStanding(Guard guard, Vec3 point, boolean planDoors) {
        if (!(guard.level() instanceof ServerLevel level)) return false;
        boolean doors = planDoors && canOpenDoors(guard);
        var blocks = new CachedBlocks(level, doors);
        Vec3 floor = surface(level, blocks, point);
        if (floor == null) return false;
        var body = guard.getBoundingBox().move(floor.subtract(guard.position())).deflate(.01);
        var border = level.getWorldBorder();
        if (body.minY < level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()
                || body.minX < border.getMinX() || body.maxX > border.getMaxX()
                || body.minZ < border.getMinZ() || body.maxZ > border.getMaxZ()) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, Math.floor(floor.y) - 1, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (!blocks.loaded(pos)) return false;
            BlockState state = blocks.getBlockState(pos);
            if (hazard(state)) return false;
            if (pos.getY() + 1 > floor.y + .01 && !state.getFluidState().isEmpty()) return false;
            var type = state.getBlockPathType(level, pos, guard);
            if (type != null && (guard.getPathfindingMalus(type) < 0
                    || type.name().contains("DAMAGE") || type.name().contains("DANGER"))) return false;
            var collision = state.getCollisionShape(blocks, pos);
            for (var box : collision.toAabbs()) if (box.move(pos).intersects(body)) return false;
        }
        return true;
    }

    private static boolean hazard(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.COBWEB)
                || ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))
                    && state.getValue(BlockStateProperties.LIT));
    }

    public static boolean canOpenDoors(Guard guard) {
        return guard.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground
                // In 1.20.1 GroundPathNavigation.canOpenDoors() reads the pass-door flag.
                && ground.getNodeEvaluator().canOpenDoors() && ground.canPassDoors();
    }

    public static boolean planningStep(ServerLevel level, BlockPos from, BlockPos to, boolean doors) {
        return safeStep(level, new CachedBlocks(level, doors), from, to);
    }

    public static boolean planningStanding(ServerLevel level, BlockPos feet) {
        return Double.isFinite(standingHeight(level, new CachedBlocks(level, true), feet));
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
        if (level.isOutsideBuildHeight(feet.below()) || level.isOutsideBuildHeight(feet.above())
                || !level.getWorldBorder().isWithinBounds(feet)
                || !blocks.loaded(feet)
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

    private static boolean clear(CachedBlocks blocks, BlockPos p) {
        BlockState state = blocks.getBlockState(p);
        return state.getFluidState().isEmpty() && (state.getCollisionShape(blocks, p).isEmpty()
                || state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    && state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN))
                && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE) && !state.is(Blocks.SWEET_BERRY_BUSH)
                && !state.is(Blocks.COBWEB) && !state.is(Blocks.WITHER_ROSE) && !state.is(Blocks.POWDER_SNOW);
    }
    private static final class CachedBlocks implements BlockGetter {
        private final ServerLevel level;
        private final boolean planDoors;
        private int chunkX, chunkZ;
        private boolean resolvedChunk;
        private net.minecraft.world.level.chunk.LevelChunk chunk;
        CachedBlocks(ServerLevel level) { this(level, false); }
        CachedBlocks(ServerLevel level, boolean planDoors) { this.level = level; this.planDoors = planDoors; }
        // This reader lives for one synchronous traversal check. Reuse only the
        // chunk reference, never a safety result or block state across checks.
        private net.minecraft.world.level.chunk.LevelChunk chunkAt(BlockPos p) {
            int x = p.getX() >> 4, z = p.getZ() >> 4;
            if (!resolvedChunk || chunkX != x || chunkZ != z) {
                chunkX = x; chunkZ = z; resolvedChunk = true;
                chunk = level.getChunkSource().getChunkNow(x, z);
            }
            return chunk;
        }
        boolean loaded(BlockPos p) { return chunkAt(p) != null; }
        public BlockState getBlockState(BlockPos p) {
            var chunk = chunkAt(p);
            BlockState state = chunk == null ? Blocks.BARRIER.defaultBlockState() : chunk.getBlockState(p);
            return planDoors && net.minecraft.world.level.block.DoorBlock.isWoodenDoor(state)
                    ? state.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true) : state;
        }
        public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
        public BlockEntity getBlockEntity(BlockPos p) { return null; }
        public int getHeight() { return level.getHeight(); }
        public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
}
