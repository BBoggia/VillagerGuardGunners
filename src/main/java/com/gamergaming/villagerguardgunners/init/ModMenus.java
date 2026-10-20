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
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import tallestegg.guardvillagers.common.entities.Guard;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, VillagerGuardGunners.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<GuardGunnerContainer>> GUARD_GUNNER_CONTAINER = MENUS.register("guard_gunner_container", () ->
        IMenuTypeExtension.create((windowId, inv, data) -> {
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

    public static final DeferredHolder<MenuType<?>, MenuType<ArmoryMenu>> ARMORY = MENUS.register(
            "armory",
            () -> IMenuTypeExtension.create((windowId, inventory, data) -> {
                var position = data.readBlockPos();
                var blockEntity = inventory.player.level().getBlockEntity(position);
                if (blockEntity instanceof ArmoryBlockEntity armory) {
                    return new ArmoryMenu(windowId, inventory, armory);
                }
                throw new IllegalStateException(
                        "Armory menu opened for missing block entity at " + position);
            }));

    public static final DeferredHolder<MenuType<?>, MenuType<QuartermasterMenu>> QUARTERMASTER =
            MENUS.register("quartermaster", () -> IMenuTypeExtension.create((id, inventory, data) ->
                    new QuartermasterMenu(id, inventory, data.readBlockPos())));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }

    public static final DeferredHolder<MenuType<?>, MenuType<com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu>> QUARTERMASTER_MANAGEMENT =
            MENUS.register("quartermaster_management", () -> IMenuTypeExtension.create((id, inventory, data) ->
                    new com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu(id, inventory, data.readUUID())));

    private ModMenus() {}
}
