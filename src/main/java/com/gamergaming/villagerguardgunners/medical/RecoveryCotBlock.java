package com.gamergaming.villagerguardgunners.medical;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Dedicated two-block recovery furniture. It is not a villager HOME POI or a sleeping bed. */
public final class RecoveryCotBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);
    public RecoveryCotBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection(); BlockPos head = context.getClickedPos().relative(facing);
        return context.getLevel().getWorldBorder().isWithinBounds(head) && context.getLevel().getBlockState(head).canBeReplaced(context)
                ? defaultBlockState().setValue(FACING, facing) : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) level.setBlock(pos.relative(state.getValue(FACING)), state.setValue(PART, BedPart.HEAD), 3);
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction other = state.getValue(PART) == BedPart.FOOT ? state.getValue(FACING) : state.getValue(FACING).getOpposite();
        if (side == other && (!neighbor.is(this) || neighbor.getValue(PART) == state.getValue(PART) || neighbor.getValue(FACING) != state.getValue(FACING))) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == BedPart.FOOT) {
            BlockPos head = pos.relative(state.getValue(FACING));
            if (level.getBlockState(head).is(this)) level.setBlock(head, Blocks.AIR.defaultBlockState(), 35);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return state.getValue(PART) == BedPart.HEAD ? new RecoveryCotBlockEntity(pos, state) : null; }
}
