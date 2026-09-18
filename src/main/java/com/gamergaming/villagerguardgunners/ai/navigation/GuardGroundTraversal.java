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
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return null;
        BlockPos cell = BlockPos.containing(point);
        for (int dy : new int[]{0, 1}) {
            double height = standingHeight(level, cell.offset(0, dy, 0));
            if (Double.isFinite(height) && Math.abs(height - point.y) <= 1.01)
                return new Vec3(point.x, height, point.z);
        }
        return null;
    }

    /** Native navigation addresses the air cell above a fractional supporting block. */
    public static BlockPos navigationNode(ServerLevel level, Vec3 point) {
        Vec3 floor = surface(level, point);
        return floor == null ? null : BlockPos.containing(floor.x, Math.ceil(floor.y), floor.z);
    }

    public static boolean safeStanding(Guard guard, Vec3 point) {
        if (!(guard.level() instanceof ServerLevel level)) return false;
        Vec3 floor = surface(level, point);
        if (floor == null) return false;
        var blocks = new CachedBlocks(level);
        var body = guard.getBoundingBox().move(floor.subtract(guard.position())).deflate(.01);
        var border = level.getWorldBorder();
        if (body.minY < level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()
                || body.minX < border.getMinX() || body.maxX > border.getMaxX()
                || body.minZ < border.getMinZ() || body.maxZ > border.getMaxZ()) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, Math.floor(floor.y) - 1, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return false;
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

    private static boolean clear(CachedBlocks blocks, BlockPos p) {
        BlockState state = blocks.getBlockState(p);
        return state.getFluidState().isEmpty() && state.getCollisionShape(blocks, p).isEmpty()
                && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE) && !state.is(Blocks.SWEET_BERRY_BUSH)
                && !state.is(Blocks.COBWEB) && !state.is(Blocks.WITHER_ROSE) && !state.is(Blocks.POWDER_SNOW);
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
