package com.gamergaming.villagerguardgunners.compat.pointblank.mixin;

import com.gamergaming.villagerguardgunners.client.GuardRenderContext;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Lets the mod's gun renderer draw the gun a guard holds. Without this it draws nothing for a non-player. */
@Pseudo
@Mixin(targets = "mod.pbj.client.render.GunItemRenderer", remap = false)
public abstract class PbjGunItemRendererMixin {
    @Inject(method = "getPlayer", at = @At("RETURN"), cancellable = true, require = 0)
    private void villagerGuardGunners$drawForGuard(ItemDisplayContext context, CallbackInfoReturnable<Player> result) {
        if (result.getReturnValue() == null) {
            Player holder = GuardRenderContext.guardPlayer(context);
            if (holder != null) result.setReturnValue(holder);
        }
    }
}
