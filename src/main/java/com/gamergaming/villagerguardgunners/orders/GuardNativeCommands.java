package com.gamergaming.villagerguardgunners.orders;

import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.client.GuardSounds;
import tallestegg.guardvillagers.common.entities.Guard;

/** Server boundary for the upstream inventory buttons, including directly sent packets. */
public final class GuardNativeCommands {
    private GuardNativeCommands() { }

    private static Guard authorized(IPayloadContext context, int entityId, boolean heroRequired) {
        if (!(context.player() instanceof ServerPlayer player)
                || !player.server.isSameThread()
                || !(player.containerMenu instanceof GuardGunnerContainer menu)
                || menu.getGuard().getId() != entityId || !menu.stillValid(player)
                || !GuardOrderPermissions.mayManage(player, menu.getGuard())
                || heroRequired && !player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) return null;
        return menu.getGuard();
    }

    public static void follow(IPayloadContext context, int entityId) {
        context.enqueueWork(() -> {
            Guard guard = authorized(context, entityId, GuardConfig.COMMON.followHero.get());
            if (guard == null) return;
            guard.setFollowing(!guard.isFollowing());
            guard.setOwnerId(context.player().getUUID());
            guard.playSound(GuardSounds.GUARD_YES.value(), 1.0F, 1.0F);
        });
    }

    public static void patrol(IPayloadContext context, int entityId, boolean previousState) {
        context.enqueueWork(() -> {
            Guard guard = authorized(context, entityId, GuardConfig.COMMON.setGuardPatrolHotv.get());
            if (guard == null || guard.isPatrolling() != previousState) return;
            guard.setPatrolPos(previousState ? null : guard.blockPosition());
            guard.setPatrolling(!previousState);
            guard.playSound(GuardSounds.GUARD_YES.value());
        });
    }
}
