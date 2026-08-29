package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.item.OrdersLedgerItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers the add-on's deliberately small player-facing item surface. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            ForgeRegistries.ITEMS,
            VillagerGuardGunners.MODID);

    public static final RegistryObject<Item> ORDERS_LEDGER = ITEMS.register(
            "orders_ledger",
            () -> new OrdersLedgerItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        eventBus.addListener(ModItems::addCreativeModeTabItems);
    }

    private static void addCreativeModeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ORDERS_LEDGER.get());
        }
    }

    private ModItems() {
    }
}
