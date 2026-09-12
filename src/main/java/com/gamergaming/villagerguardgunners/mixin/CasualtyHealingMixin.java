package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tallestegg.guardvillagers.entities.Guard;

@Mixin(LivingEntity.class)
public abstract class CasualtyHealingMixin {
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void vgg$requireRevival(float amount, CallbackInfo ci) { if ((Object)this instanceof Guard guard && GuardLifeRuntime.casualty(guard)) ci.cancel(); }
    @org.spongepowered.asm.mixin.injection.Inject(method = "travel", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void vgg$restOnCot(net.minecraft.world.phys.Vec3 input, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if ((Object)this instanceof tallestegg.guardvillagers.entities.Guard guard && com.gamergaming.villagerguardgunners.medical.CotRestRuntime.anchored(guard)) {
            guard.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); ci.cancel();
        }
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "isPushable", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void vgg$restingPatientNotPushable(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof tallestegg.guardvillagers.entities.Guard guard && com.gamergaming.villagerguardgunners.medical.CotRestRuntime.anchored(guard)) cir.setReturnValue(false);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "pushEntities", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void vgg$restingPatientDoesNotPush(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if ((Object)this instanceof tallestegg.guardvillagers.entities.Guard guard && com.gamergaming.villagerguardgunners.medical.CotRestRuntime.anchored(guard)) ci.cancel();
    }
}
