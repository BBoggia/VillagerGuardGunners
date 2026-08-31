package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.item.OrdersLedgerItem;
import com.gamergaming.villagerguardgunners.item.SquadCommandLedgerItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
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

    public static final RegistryObject<Item> SQUAD_COMMAND_LEDGER = ITEMS.register(
            "squad_command_ledger",
            () -> new SquadCommandLedgerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ARMORY = ITEMS.register(
            "armory",
            () -> new BlockItem(ModBlocks.ARMORY.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        eventBus.addListener(ModItems::addCreativeModeTabItems);
    }

    private static void addCreativeModeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ORDERS_LEDGER.get());
            event.accept(SQUAD_COMMAND_LEDGER.get());
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ARMORY.get());
        }
    }

    private ModItems() {
    }
}
