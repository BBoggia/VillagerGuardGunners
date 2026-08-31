package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.compat.tacz.network.TaCZGunPackSync;
import com.tacz.guns.resource.CommonAssetsManager;
import net.minecraftforge.event.OnDatapackSyncEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only TaCZ common-data payloads that cannot fit Minecraft's packet limit. */
@Mixin(value = CommonAssetsManager.class, remap = false)
public abstract class TaCZCommonAssetsManagerMixin {
    @Inject(method = "OnDatapackSync", at = @At("HEAD"), cancellable = true, remap = false)
    private static void villagerGuardGunners$chunkOversizedTaCZSync(
            OnDatapackSyncEvent event,
            CallbackInfo callback) {
        if (TaCZGunPackSync.replaceIfOversized(event)) {
            callback.cancel();
        }
    }
}
