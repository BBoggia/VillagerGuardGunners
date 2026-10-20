package com.gamergaming.villagerguardgunners.mixin.client;

import com.gamergaming.villagerguardgunners.api.GuardLifeAccess;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tallestegg.guardvillagers.common.entities.Guard;

@Mixin(EntityRenderer.class)
public abstract class CotNameTagMixin {
    @Redirect(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityAttachments;getNullable(Lnet/minecraft/world/entity/EntityAttachment;IF)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 vgg$cotNameHeight(EntityAttachments attachments, EntityAttachment attachment, int index, float yaw,
            Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
        return entity instanceof Guard guard && ((GuardLifeAccess) guard).villagerGuardGunners$getCotVisual().isPresent()
                ? new Vec3(0, .3, 0) : attachments.getNullable(attachment, index, yaw);
    }
}
