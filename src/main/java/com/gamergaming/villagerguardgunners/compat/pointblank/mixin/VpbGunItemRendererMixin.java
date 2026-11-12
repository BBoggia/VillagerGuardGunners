package com.gamergaming.villagerguardgunners.compat.pointblank.mixin;

import com.gamergaming.villagerguardgunners.client.GuardRenderContext;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Lets the mod's gun renderer draw the gun a guard holds. Without this it draws nothing for a non-player. */
@Pseudo
@Mixin(targets = "com.vicmatskiv.pointblank.client.render.GunItemRenderer", remap = false)
public abstract class VpbGunItemRendererMixin {
    @ModifyReturnValue(method = "getPlayer", at = @At("RETURN"), require = 0)
    private Player villagerGuardGunners$drawForGuard(Player found) {
        return GuardRenderContext.holderFor(found);
    }
}
