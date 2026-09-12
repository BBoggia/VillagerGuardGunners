package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded-only index entry; cots have no inventory, saved occupancy, or ticker. */
public final class RecoveryCotBlockEntity extends BlockEntity {
    public RecoveryCotBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.RECOVERY_COT.get(), pos, state); }
    @Override public void onLoad() { super.onLoad(); TransportRuntime.cotLoaded(this); }
    @Override public void setRemoved() { TransportRuntime.cotRemoved(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { TransportRuntime.cotUnloaded(this); super.onChunkUnloaded(); }
}
