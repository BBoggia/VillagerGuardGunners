package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.orders.GuardNativeCommands;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tallestegg.guardvillagers.networking.GuardFollowPacket;

@Mixin(value = GuardFollowPacket.class, remap = false)
public abstract class GuardFollowPacketMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void vgg$authorize(GuardFollowPacket packet, IPayloadContext context, CallbackInfo ci) {
        GuardNativeCommands.follow(context, packet.entityId());
        ci.cancel();
    }
}
