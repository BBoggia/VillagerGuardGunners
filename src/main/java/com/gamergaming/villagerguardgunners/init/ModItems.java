package com.gamergaming.villagerguardgunners.init;

import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterItem;
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

    public static final RegistryObject<Item> QUARTERMASTER = ITEMS.register(
            "quartermaster", () -> new QuartermasterItem(ModBlocks.QUARTERMASTER.get(),
                    new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> RECOVERY_COT = ITEMS.register("recovery_cot", () ->
            new BlockItem(ModBlocks.RECOVERY_COT.get(), new Item.Properties()));

    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MEDICAL_POUCH = ITEMS.register("medical_pouch", () ->
            new com.gamergaming.villagerguardgunners.medical.MedicalPouchItem(new Item.Properties().stacksTo(1).fireResistant()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        eventBus.addListener(ModItems::addCreativeModeTabItems);
    }

    private static void addCreativeModeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ORDERS_LEDGER.get());
            event.accept(BANDAGE.get());
            event.accept(SQUAD_COMMAND_LEDGER.get());
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ARMORY.get());
            event.accept(QUARTERMASTER.get());
            event.accept(RECOVERY_COT.get());
        }
    }

    private ModItems() {
    }
}
