package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.init.ModEntities;
import com.gamergaming.villagerguardgunners.init.ModMenus;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
@Mod.EventBusSubscriber(modid = VillagerGuardGunners.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void registerEntityRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GUARD_BULLET.get(), NoopRenderer::new);
        // NoopRenderer is a placeholder; you can create a custom renderer if desired
    }

    // @SubscribeEvent
    // public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
    //     event.registerEntityRenderer(ModEntities.GUARD_BULLET.get(), GuardBulletRenderer::new);
    // }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Register your screen here
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.GUARD_GUNNER_CONTAINER.get(), GuardGunnerInventoryScreen::new);
        });
    }
}
