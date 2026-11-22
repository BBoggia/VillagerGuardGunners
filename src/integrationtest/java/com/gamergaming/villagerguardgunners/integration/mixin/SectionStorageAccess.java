package com.gamergaming.villagerguardgunners.integration.mixin;

import java.util.Optional;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = SectionStorage.class, remap = false)
public interface SectionStorageAccess {
    @Invoker("get")
    Optional<?> integrationCachedSection(long key);
}
