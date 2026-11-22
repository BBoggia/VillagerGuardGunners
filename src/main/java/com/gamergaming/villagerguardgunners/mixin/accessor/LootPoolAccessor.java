package com.gamergaming.villagerguardgunners.mixin.accessor;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only structural access used to reject unbounded generated-Armory pools. */
@Mixin(LootPool.class)
public interface LootPoolAccessor {
    @Accessor("entries")
    LootPoolEntryContainer[] villagerGuardGunners$getEntries();

    @Accessor("conditions")
    LootItemCondition[] villagerGuardGunners$getConditions();

    @Accessor("functions")
    LootItemFunction[] villagerGuardGunners$getFunctions();
}
