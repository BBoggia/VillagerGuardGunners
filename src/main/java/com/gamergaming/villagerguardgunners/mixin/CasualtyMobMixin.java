package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tallestegg.guardvillagers.entities.Guard;

/** Suspend goals and controls without changing or persisting the player's NoAI choice. */
@Mixin(Mob.class)
public abstract class CasualtyMobMixin {
    @Inject(method = "isEffectiveAi", at = @At("HEAD"), cancellable = true)
    private void vgg$casualtyNotEffectiveAi(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof Guard guard && (GuardLifeRuntime.blocked(guard) || com.gamergaming.villagerguardgunners.medical.TransportRuntime.awaitingRelease(guard))) cir.setReturnValue(false);
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void vgg$suspendCasualtyAi(CallbackInfo ci) { if ((Object)this instanceof Guard guard && (GuardLifeRuntime.blocked(guard) || com.gamergaming.villagerguardgunners.medical.TransportRuntime.awaitingRelease(guard))) ci.cancel(); }
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void vgg$casualtyDoesNotSteerCarrier(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.entity.LivingEntity> cir) {
        // Mob passengers normally disable the vehicle's MOVE/LOOK goals. The casualty
        // is cargo: the medic must keep its own navigation and controls while carrying it.
        if ((Object)this instanceof Guard guard && com.gamergaming.villagerguardgunners.medical.TransportRuntime.busy(guard)
                && guard.getFirstPassenger() instanceof Guard) cir.setReturnValue(null);
    }
}
