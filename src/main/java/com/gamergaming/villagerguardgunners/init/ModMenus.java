package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import tallestegg.guardvillagers.entities.Guard;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, VillagerGuardGunners.MODID);

    public static final RegistryObject<MenuType<GuardGunnerContainer>> GUARD_GUNNER_CONTAINER = MENUS.register("guard_gunner_container", () ->
        IForgeMenuType.create((windowId, inv, data) -> {
            int entityId = data.readInt();
            Level level = inv.player.level();
            Entity entity = level.getEntity(entityId);
            if (entity instanceof Guard guard) {
                return new GuardGunnerContainer(
                        windowId,
                        inv,
                        guard.guardInventory,
                        GuardAmmoAccess.getAmmoHandler(guard),
                        guard);
            }
            throw new IllegalStateException("Guard gunner menu opened for missing guard entity " + entityId);
        })
    );

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }

    private ModMenus() {}
}
