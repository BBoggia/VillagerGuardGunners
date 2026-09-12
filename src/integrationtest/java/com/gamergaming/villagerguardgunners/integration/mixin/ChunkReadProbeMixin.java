package com.gamergaming.villagerguardgunners.integration.mixin;

import com.gamergaming.villagerguardgunners.integration.ProbeScope;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerChunkCache.class, remap = false)
public abstract class ChunkReadProbeMixin {
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"))
    private void observeRequest(int x, int z, ChunkStatus status, boolean create,
                                CallbackInfoReturnable<ChunkAccess> callback) {
        if (create && ((ServerChunkCache) (Object) this).getChunkNow(x, z) == null) {
            ProbeScope.missingChunkRequest();
        }
    }
}
