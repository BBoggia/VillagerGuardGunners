package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.menu.ArmoryMenu;
import com.gamergaming.villagerguardgunners.menu.QuartermasterMenu;
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

    public static final RegistryObject<MenuType<ArmoryMenu>> ARMORY = MENUS.register(
            "armory",
            () -> IForgeMenuType.create((windowId, inventory, data) -> {
                var position = data.readBlockPos();
                var blockEntity = inventory.player.level().getBlockEntity(position);
                if (blockEntity instanceof ArmoryBlockEntity armory) {
                    return new ArmoryMenu(windowId, inventory, armory);
                }
                throw new IllegalStateException(
                        "Armory menu opened for missing block entity at " + position);
            }));

    public static final RegistryObject<MenuType<QuartermasterMenu>> QUARTERMASTER =
            MENUS.register("quartermaster", () -> IForgeMenuType.create((id, inventory, data) ->
                    new QuartermasterMenu(id, inventory, data.readBlockPos())));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }

    public static final RegistryObject<MenuType<com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu>> QUARTERMASTER_MANAGEMENT =
            MENUS.register("quartermaster_management", () -> IForgeMenuType.create((id, inventory, data) ->
                    new com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu(id, inventory, data.readUUID())));

    private ModMenus() {}
}
