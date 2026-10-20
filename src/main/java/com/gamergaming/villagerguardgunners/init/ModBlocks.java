package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlock;
import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Player-facing blocks supplied by the add-on. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            BuiltInRegistries.BLOCK,
            VillagerGuardGunners.MODID);

    public static final DeferredHolder<Block, Block> ARMORY = BLOCKS.register(
            "armory",
            () -> new ArmoryBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL)));

    public static final DeferredHolder<Block, Block> QUARTERMASTER = BLOCKS.register(
            "quartermaster", () -> new QuartermasterBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL).strength(2.5F, 1200.0F)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    public static final DeferredHolder<Block, Block> RECOVERY_COT = BLOCKS.register("recovery_cot", () ->
            new com.gamergaming.villagerguardgunners.medical.RecoveryCotBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL)
                    .strength(1.5F).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

    private ModBlocks() {
    }
}
