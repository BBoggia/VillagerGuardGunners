package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import tallestegg.guardvillagers.common.entities.Guard;

@Mixin(LivingEntity.class)
public abstract class CasualtyDamageMixin {
    // NeoForge Pre runs before absorption, and Post cannot prevent health loss. Intercept
    // only the final health-damage read, after native absorption and reduction callbacks.
    @Redirect(method = "actuallyHurt", slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V")),
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;getNewDamage()F", remap = false))
    private float vgg$casualtyDamage(DamageContainer damage) {
        if ((Object) this instanceof Guard guard) GuardLifeRuntime.damaged(guard, damage);
        return damage.getNewDamage();
    }
}
