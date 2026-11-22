package com.gamergaming.villagerguardgunners.integration.mixin;

import com.gamergaming.villagerguardgunners.integration.ProbeScope;
import net.minecraft.world.entity.SpawnGroupData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tallestegg.guardvillagers.entities.Guard;

@Mixin(value = Guard.class, remap = false)
public abstract class GuardSpawnProbeMixin {
    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void captureSpawn(CallbackInfoReturnable<SpawnGroupData> callback) {
        ProbeScope.finalized((Guard) (Object) this);
    }
}
