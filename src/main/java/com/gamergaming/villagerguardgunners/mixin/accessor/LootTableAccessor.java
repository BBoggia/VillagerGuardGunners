package com.gamergaming.villagerguardgunners.mixin.accessor;

import java.util.List;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only structural access used to reject unbounded generated-Armory tables. */
@Mixin(LootTable.class)
public interface LootTableAccessor {
    @Accessor("pools")
    List<LootPool> villagerGuardGunners$getPools();

    @Accessor("functions")
    LootItemFunction[] villagerGuardGunners$getFunctions();
}
