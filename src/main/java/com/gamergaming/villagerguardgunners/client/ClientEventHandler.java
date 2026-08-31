package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.init.ModMenus;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;

@Mod.EventBusSubscriber(modid = VillagerGuardGunners.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEventHandler {

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                {
                    MenuScreens.register(
                            ModMenus.GUARD_GUNNER_CONTAINER.get(),
                            GuardGunnerInventoryScreen::new);
                    MenuScreens.register(ModMenus.ARMORY.get(), ArmoryScreen::new);
                });
    }

    private ClientEventHandler() {}
}
