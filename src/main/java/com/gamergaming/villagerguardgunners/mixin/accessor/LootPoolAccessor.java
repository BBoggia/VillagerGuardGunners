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
    java.util.List<LootPoolEntryContainer> villagerGuardGunners$getEntries();

    @Accessor("conditions")
    java.util.List<LootItemCondition> villagerGuardGunners$getConditions();

    @Accessor("functions")
    java.util.List<LootItemFunction> villagerGuardGunners$getFunctions();
}
