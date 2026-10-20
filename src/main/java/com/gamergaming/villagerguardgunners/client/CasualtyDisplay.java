package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.api.GuardLifeAccess;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import tallestegg.guardvillagers.common.entities.Guard;

@net.neoforged.fml.common.EventBusSubscriber(modid = "villagerguardgunners", value = Dist.CLIENT)
public final class CasualtyDisplay {
    @SubscribeEvent
    public static void name(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Guard guard)) return;
        int state = ((GuardLifeAccess)guard).villagerGuardGunners$getLifeVisual();
        if (state == 0 && com.gamergaming.villagerguardgunners.medical.TransportRuntime.busy(guard)) {
            event.setContent(guard.getDisplayName().copy().append(" — ").append(Component.translatable("entity.villagerguardgunners.life.carrier")));
            event.setCanRender(net.neoforged.neoforge.common.util.TriState.TRUE); return;
        }
        if (state < 1 || state > 3) return;
        String key = switch (state) { case 1 -> "downed"; case 2 -> "stabilized"; default -> "recovering"; };
        int care = ((GuardLifeAccess)guard).villagerGuardGunners$getCareVisual();
        if (care >= 1000) key = switch(care / 1000) {
            case 1 -> "cot_care"; case 2 -> "field_care"; case 3 -> "stabilizing";
            case 4 -> "awaiting_care"; case 5 -> "no_cot"; case 6 -> "route_blocked"; case 7 -> "exit_blocked"; default -> key;
        };
        if (guard.getVehicle() instanceof Guard) key = "carried";
        event.setContent(guard.getDisplayName().copy().append(" — ").append(Component.translatable("entity.villagerguardgunners.life." + key)));
        if (care >= 1000 && care < 4000 && net.minecraft.client.Minecraft.getInstance().crosshairPickEntity == guard)
            event.setContent(event.getContent().copy().append(" (" + care % 1000 + "%)"));
        event.setCanRender(net.neoforged.neoforge.common.util.TriState.TRUE);
    }
}
