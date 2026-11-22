package com.gamergaming.villagerguardgunners.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import tallestegg.guardvillagers.common.entities.Guard;

/** Scoped to the renderer invocation on the render thread, including nested draws. */
public final class GuardRenderContext {
    private static LivingEntity holder;

    private GuardRenderContext() {}

    public static LivingEntity setHolder(LivingEntity next) {
        LivingEntity previous = holder;
        holder = next;
        return previous;
    }

    public static Player guardPlayer(ItemDisplayContext context) {
        boolean held = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        return held && holder instanceof Guard ? Minecraft.getInstance().player : null;
    }
}
