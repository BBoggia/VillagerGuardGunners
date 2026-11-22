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
        // TaCZ's own animation classes exist only when TaCZ is installed.
        if (com.gamergaming.villagerguardgunners.client.GuardGunPoseGate.TACZ_INSTALLED) {
            GuardThirdPersonAnimation.reapply(
                    guard,
                    model.rightArm,
                    model.leftArm,
                    model.body,
                    model.head,
                    limbSwingAmount,
                    ageInTicks);
        }
        // Gun mods without their own pose for non-player models get a shared one.
        com.gamergaming.villagerguardgunners.client.GuardGenericGunPose.apply(
                guard, model.rightArm, model.leftArm, model.head);
    }
}
