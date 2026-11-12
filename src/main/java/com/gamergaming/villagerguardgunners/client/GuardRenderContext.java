package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Knows when the entity being drawn is a guard, so Point Blank's gun renderer
 * can be told to draw a guard's held gun. Render thread only.
 */
@EventBusSubscriber(modid = VillagerGuardGunners.MODID, value = Dist.CLIENT)
public final class GuardRenderContext {
    private static boolean renderingGuard;

    private GuardRenderContext() {
    }

    @SubscribeEvent
    public static void beforeRender(RenderLivingEvent.Pre<?, ?> event) {
        renderingGuard = event.getEntity() instanceof Guard;
    }

    @SubscribeEvent
    public static void afterRender(RenderLivingEvent.Post<?, ?> event) {
        renderingGuard = false;
    }

    /**
     * Point Blank draws a held gun only when it finds a player holding it. For
     * a guard it is given the local player instead, which is what it already
     * uses for a gun lying on the ground: the model is drawn without any
     * per-gun animation state.
     *
     * @param found the holder Point Blank resolved, or null when it found none
     */
    public static Player holderFor(Player found) {
        return found == null && renderingGuard ? Minecraft.getInstance().player : found;
    }
}
