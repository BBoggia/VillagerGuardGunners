package com.gamergaming.villagerguardgunners.compat.cgm.mixin;

import com.gamergaming.villagerguardgunners.compat.cgm.CgmProjectileKind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.mrcrayfish.guns.interfaces.IProjectileFactory;
import net.minecraft.world.item.Item;

@Pseudo
@Mixin(targets = "com.mrcrayfish.guns.GunMod", remap = false)
public abstract class GunModFactoriesMixin {
    // Only observe the native registrations. An addon may later replace a mapping.
    @ModifyArg(index = 1, method = {"onCommonSetup", "lambda$onCommonSetup$2"}, require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mrcrayfish/guns/common/ProjectileManager;registerFactory(Lnet/minecraft/world/item/Item;Lcom/mrcrayfish/guns/interfaces/IProjectileFactory;)V"))
    private static IProjectileFactory villagerGuardGunners$recordFactory(Item ammo, IProjectileFactory factory) {
        CgmProjectileKind.recordNative(ammo, factory);
        return factory;
    }
}
