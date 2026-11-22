package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.gamergaming.villagerguardgunners.init.ModStructures;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** Produces the first available ammo stack in a pool's ordered list of calibers. */
public final class GuardAmmoLootEntry extends LootPoolSingletonContainer {
    public static final MapCodec<GuardAmmoLootEntry> CODEC = RecordCodecBuilder.mapCodec(instance ->
            singletonFields(instance).and(ResourceLocation.CODEC.listOf(1, 16)
                    .fieldOf("choices").forGetter(entry -> entry.choices))
                    .apply(instance, GuardAmmoLootEntry::new));

    private final List<ResourceLocation> choices;

    private GuardAmmoLootEntry(int weight, int quality, List<LootItemCondition> conditions,
            List<LootItemFunction> functions, List<ResourceLocation> choices) {
        super(weight, quality, conditions, functions);
        this.choices = List.copyOf(choices);
    }

    @Override
    public LootPoolEntryType getType() {
        return ModStructures.GUARD_AMMO.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        for (ResourceLocation id : choices) {
            var ammo = GunProviders.forAmmoId(id).flatMap(provider -> provider.createAmmo(id, 1));
            if (ammo.isPresent()) {
                output.accept(ammo.get());
                return;
            }
        }
    }
}
