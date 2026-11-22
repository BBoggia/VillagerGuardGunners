package com.gamergaming.villagerguardgunners.mixin.accessor;

import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to an Armory loot entry's bounded condition list. */
@Mixin(LootPoolEntryContainer.class)
public interface LootPoolEntryAccessor {
    @Accessor("conditions")
    LootItemCondition[] villagerGuardGunners$getConditions();
}
