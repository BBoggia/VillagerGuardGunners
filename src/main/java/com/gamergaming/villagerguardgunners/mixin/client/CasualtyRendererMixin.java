package com.gamergaming.villagerguardgunners.mixin.client;

import com.gamergaming.villagerguardgunners.api.GuardLifeAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tallestegg.guardvillagers.entities.Guard;

/** Same pose on both upstream skins; the original entity retains its normal selectable hitbox. */
@Mixin(LivingEntityRenderer.class)
public abstract class CasualtyRendererMixin {
    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void vgg$cotPose(LivingEntity entity, PoseStack pose, float age, float yaw, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof Guard guard) || !guard.isAlive()) return;
        var head = ((GuardLifeAccess) guard).villagerGuardGunners$getCotVisual();
        if (head.isEmpty() || !guard.level().hasChunkAt(head.get())) return;
        var state = guard.level().getBlockState(head.get());
        if (!(state.getBlock() instanceof com.gamergaming.villagerguardgunners.medical.RecoveryCotBlock)) return;
        var facing = state.getValue(com.gamergaming.villagerguardgunners.medical.RecoveryCotBlock.FACING);
        // Rotate about the feet, then offset those feet toward the cot's foot end.
        // Normal renderer scaling/translation still applies to the model and equipment layers.
        pose.translate(-facing.getStepX() * .85, .22, -facing.getStepZ() * .85);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        ci.cancel();
    }
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void vgg$casualtyPose(LivingEntity entity, PoseStack pose, float age, float yaw, float partialTick, CallbackInfo ci) {
        if (entity instanceof Guard guard && ((GuardLifeAccess)guard).villagerGuardGunners$getLifeVisual() != 0 && guard.isAlive()
                && ((GuardLifeAccess)guard).villagerGuardGunners$getCotVisual().isEmpty()) {
            pose.translate(0, .3, 0); pose.mulPose(Axis.ZP.rotationDegrees(90));
        }
    }
}
