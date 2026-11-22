package com.gamergaming.villagerguardgunners.compat.pointblank.mixin;

import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import tallestegg.guardvillagers.entities.Guard;

/**
 * The mod gives a non-player shooter's hit an anonymous damage source. A
 * guard's hit names the guard instead, so its target knows who shot it.
 */
@Pseudo
@Mixin(targets = "com.vicmatskiv.pointblank.item.HurtingItem", remap = false)
public abstract class VpbHurtingItemMixin {
    @ModifyVariable(method = "hurtEntity", at = @At("STORE"), ordinal = 0, require = 0)
    private DamageSource villagerGuardGunners$creditGuard(DamageSource source, LivingEntity shooter,
            EntityHitResult hit, Entity projectile, ItemStack weapon) {
        return shooter instanceof Guard && source == shooter.damageSources().generic()
                ? shooter.damageSources().mobAttack(shooter) : source;
    }
}
