package com.gamergaming.villagerguardgunners.init;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.entity.GuardBulletEntity;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, VillagerGuardGunners.MODID);

    // public static final RegistryObject<EntityType<GuardBulletEntity>> GUARD_BULLET = ENTITIES.register("guard_bullet", () -> GuardBulletEntity.TYPE);
    public static final RegistryObject<EntityType<GuardBulletEntity>> GUARD_BULLET =
            ENTITIES.register("guard_bullet", () -> EntityType.Builder.<GuardBulletEntity>of(GuardBulletEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F) // Size of the bullet entity
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("guard_bullet"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}