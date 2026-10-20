package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.init.ModMenus;

import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;

@net.neoforged.fml.common.EventBusSubscriber(modid = VillagerGuardGunners.MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEventHandler {

    @SubscribeEvent
    public static void onClientSetup(final net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {

                    event.register(
                            ModMenus.GUARD_GUNNER_CONTAINER.get(),
                            GuardGunnerInventoryScreen::new);
                    event.register(ModMenus.ARMORY.get(), ArmoryScreen::new);
                    event.register(ModMenus.QUARTERMASTER.get(), QuartermasterScreen::new);
                    event.register(ModMenus.QUARTERMASTER_MANAGEMENT.get(), QuartermasterManagementScreen::new);

    }

    private ClientEventHandler() {}
}
