package com.gamergaming.villagerguardgunners.mixin;

import com.gamergaming.villagerguardgunners.village.compat.IntegratedVillageTagCorrection;
import java.util.*;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TagLoader.class)
public abstract class IntegratedVillageTagLoaderMixin<T> {
    @Shadow @Final private String directory;
    @Shadow @Final private Function<ResourceLocation, Optional<? extends T>> idToValue;
    @Inject(method = "load", at = @At("RETURN"))
    private void correctIntegratedEntry(ResourceManager manager,
            CallbackInfoReturnable<Map<ResourceLocation, List<TagLoader.EntryWithSource>>> callback) {
        if ("tags/worldgen/structure".equals(directory)) IntegratedVillageTagCorrection.correct(manager,
                callback.getReturnValue(), idToValue.apply(IntegratedVillageTagCorrection.MISSING).isPresent());
    }
}
