package com.gamergaming.villagerguardgunners.mixin.client;

import com.gamergaming.villagerguardgunners.client.GuardThirdPersonAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tallestegg.guardvillagers.client.models.GuardModel;
import tallestegg.guardvillagers.entities.Guard;

/** Restores TaCZ's pose after the normal Guard model's additional animation. */
@Mixin(GuardModel.class)
public abstract class GuardModelMixin {
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
        GuardModel model = (GuardModel) (Object) this;
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
