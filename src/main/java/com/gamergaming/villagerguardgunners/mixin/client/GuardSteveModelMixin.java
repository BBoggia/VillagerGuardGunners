package com.gamergaming.villagerguardgunners.mixin.client;

import com.gamergaming.villagerguardgunners.client.GuardThirdPersonAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tallestegg.guardvillagers.client.models.GuardSteveModel;
import tallestegg.guardvillagers.entities.Guard;

/** Restores TaCZ's pose after the Steve Guard model's additional animation. */
@Mixin(GuardSteveModel.class)
public abstract class GuardSteveModelMixin {
    @Inject(
            method = "setupAnim(Ltallestegg/guardvillagers/entities/Guard;FFFFF)V",
            at = @At("TAIL"),
            remap = false)
    private void reapplyNativeGunPose(
            Guard guard,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        GuardSteveModel model = (GuardSteveModel) (Object) this;
        GuardThirdPersonAnimation.reapply(
                guard,
                model.rightArm,
                model.leftArm,
                model.body,
                model.head,
                limbSwingAmount,
                ageInTicks);
    }
}
