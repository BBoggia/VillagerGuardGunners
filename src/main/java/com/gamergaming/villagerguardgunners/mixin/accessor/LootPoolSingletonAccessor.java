package com.gamergaming.villagerguardgunners.mixin.accessor;

import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to an Armory item entry's bounded function list. */
@Mixin(LootPoolSingletonContainer.class)
public interface LootPoolSingletonAccessor {
    @Accessor("functions")
    LootItemFunction[] villagerGuardGunners$getFunctions();
}
