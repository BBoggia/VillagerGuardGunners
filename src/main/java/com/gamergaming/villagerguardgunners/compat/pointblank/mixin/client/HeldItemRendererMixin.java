package com.gamergaming.villagerguardgunners.compat.pointblank.mixin.client;

import com.gamergaming.villagerguardgunners.client.GuardRenderContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @WrapOperation(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"))
    private void villagerGuardGunners$renderWithHolder(ItemRenderer renderer, ItemStack stack,
            ItemDisplayContext context, boolean leftHand, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay, BakedModel model, Operation<Void> original,
            @Local(argsOnly = true) LivingEntity holder) {
        LivingEntity previous = GuardRenderContext.setHolder(holder);
        try {
            original.call(renderer, stack, context, leftHand, pose, buffers, light, overlay, model);
        } finally {
            GuardRenderContext.setHolder(previous);
        }
    }
}
