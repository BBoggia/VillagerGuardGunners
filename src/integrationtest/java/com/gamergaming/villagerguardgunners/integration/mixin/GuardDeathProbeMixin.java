package com.gamergaming.villagerguardgunners.integration.mixin;

import com.gamergaming.villagerguardgunners.integration.MedicalTrace;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tallestegg.guardvillagers.entities.Guard;

@Mixin(value = Guard.class, remap = false)
public abstract class GuardDeathProbeMixin {
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void probeEarlyDeath(DamageSource source, CallbackInfo callback) {
        if (MedicalTrace.intercept((Guard) (Object) this, source)) callback.cancel();
    }
}
