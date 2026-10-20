package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.orders.GuardNativeCommands;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tallestegg.guardvillagers.networking.GuardSetPatrolPosPacket;

@Mixin(value = GuardSetPatrolPosPacket.class, remap = false)
public abstract class GuardPatrolPacketMixin {
    @Inject(method = "setPatrolPosition", at = @At("HEAD"), cancellable = true)
    private static void vgg$authorize(GuardSetPatrolPosPacket packet, IPayloadContext context, CallbackInfo ci) {
        GuardNativeCommands.patrol(context, packet.entityId(), packet.pressed());
        ci.cancel();
    }
}
