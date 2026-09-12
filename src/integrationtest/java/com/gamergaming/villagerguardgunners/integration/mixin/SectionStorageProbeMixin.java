package com.gamergaming.villagerguardgunners.integration.mixin;

import com.gamergaming.villagerguardgunners.integration.ProbeScope;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SectionStorage.class, remap = false)
public abstract class SectionStorageProbeMixin {
    @Inject(method = "readColumn", at = @At("HEAD"))
    private void observeRead(ChunkPos chunk, CallbackInfo callback) {
        ProbeScope.poiRead();
    }
}
