package com.gamergaming.villagerguardgunners.mixin.accessor;

import java.util.Optional;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Unlike getOrLoad, this never requests an uncached POI column. Null means unresolved. */
@Mixin(SectionStorage.class)
public interface VillagePoiStorageAccessor {
    @Invoker("get")
    Optional<?> villagerGuardGunners$cachedSection(long key);
}
