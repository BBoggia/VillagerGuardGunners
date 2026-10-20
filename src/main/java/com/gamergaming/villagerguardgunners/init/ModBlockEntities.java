package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Block-entity registrations. Armories are intentionally unticked. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    VillagerGuardGunners.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArmoryBlockEntity>> ARMORY =
            BLOCK_ENTITIES.register(
                    "armory",
                    () -> BlockEntityType.Builder.of(
                                    ArmoryBlockEntity::new,
                                    ModBlocks.ARMORY.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuartermasterBlockEntity>> QUARTERMASTER =
            BLOCK_ENTITIES.register("quartermaster", () -> BlockEntityType.Builder.of(
                    QuartermasterBlockEntity::new,
                    ModBlocks.QUARTERMASTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.gamergaming.villagerguardgunners.medical.RecoveryCotBlockEntity>> RECOVERY_COT =
            BLOCK_ENTITIES.register("recovery_cot", () -> BlockEntityType.Builder.of(
                    com.gamergaming.villagerguardgunners.medical.RecoveryCotBlockEntity::new, ModBlocks.RECOVERY_COT.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }

    private ModBlockEntities() {
    }
}
