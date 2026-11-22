package com.gamergaming.villagerguardgunners.mixin.client;

import com.gamergaming.villagerguardgunners.api.GuardLifeAccess;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tallestegg.guardvillagers.entities.Guard;

@Mixin(EntityRenderer.class)
public abstract class CotNameTagMixin {
    @Redirect(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getNameTagOffsetY()F"))
    private float vgg$cotNameHeight(Entity entity) {
        return entity instanceof Guard guard && ((GuardLifeAccess)guard).villagerGuardGunners$getCotVisual().isPresent() ? .8F : entity.getNameTagOffsetY();
    }
}
