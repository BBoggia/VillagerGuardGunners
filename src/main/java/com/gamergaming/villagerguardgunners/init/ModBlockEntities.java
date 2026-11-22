package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Block-entity registrations. Armories are intentionally unticked. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(
                    ForgeRegistries.BLOCK_ENTITY_TYPES,
                    VillagerGuardGunners.MODID);

    public static final RegistryObject<BlockEntityType<ArmoryBlockEntity>> ARMORY =
            BLOCK_ENTITIES.register(
                    "armory",
                    () -> BlockEntityType.Builder.of(
                                    ArmoryBlockEntity::new,
                                    ModBlocks.ARMORY.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<QuartermasterBlockEntity>> QUARTERMASTER =
            BLOCK_ENTITIES.register("quartermaster", () -> BlockEntityType.Builder.of(
                    QuartermasterBlockEntity::new,
                    ModBlocks.QUARTERMASTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.gamergaming.villagerguardgunners.medical.RecoveryCotBlockEntity>> RECOVERY_COT =
            BLOCK_ENTITIES.register("recovery_cot", () -> BlockEntityType.Builder.of(
                    com.gamergaming.villagerguardgunners.medical.RecoveryCotBlockEntity::new, ModBlocks.RECOVERY_COT.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }

    private ModBlockEntities() {
    }
}
