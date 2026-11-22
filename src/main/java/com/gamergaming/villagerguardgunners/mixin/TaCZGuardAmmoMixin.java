package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.ammunition.GuardInfiniteAmmo;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tacz.guns.entity.shooter.LivingEntityAmmoCheck;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Use TaCZ's unlimited-reserve reload path only for configured guards. */
@Mixin(value = LivingEntityAmmoCheck.class, remap = false)
public abstract class TaCZGuardAmmoMixin {
    @Shadow @Final private LivingEntity shooter;

    @ModifyReturnValue(method = "needCheckAmmo", at = @At("RETURN"))
    private boolean villagerGuardGunners$virtualReserve(boolean original) {
        return original && !GuardInfiniteAmmo.virtualReserve(shooter);
    }
}
