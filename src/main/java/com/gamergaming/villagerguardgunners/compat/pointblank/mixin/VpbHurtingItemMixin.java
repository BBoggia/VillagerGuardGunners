package com.gamergaming.villagerguardgunners.compat.pointblank.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * The mod gives a non-player shooter's hit an anonymous damage source. A
 * guard's hit names the guard instead, so its target knows who shot it.
 */
@Pseudo
@Mixin(targets = "com.vicmatskiv.pointblank.item.HurtingItem", remap = false)
public abstract class VpbHurtingItemMixin {
    @WrapOperation(
            method = "hurtEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSources;generic()Lnet/minecraft/world/damagesource/DamageSource;"),
            require = 0)
    private DamageSource villagerGuardGunners$creditGuard(
            DamageSources sources, Operation<DamageSource> original, @Local(argsOnly = true) LivingEntity shooter) {
        return shooter instanceof Guard ? sources.mobAttack(shooter) : original.call(sources);
    }
}
