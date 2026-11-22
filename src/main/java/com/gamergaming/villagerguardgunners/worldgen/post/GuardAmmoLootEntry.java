package com.gamergaming.villagerguardgunners.worldgen.post;

import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.gamergaming.villagerguardgunners.init.ModStructures;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** One stack of the first available caliber. Each Armory pool specifies its own preference order. */
public final class GuardAmmoLootEntry extends LootPoolSingletonContainer {
    private final List<ResourceLocation> choices;

    private GuardAmmoLootEntry(List<ResourceLocation> choices, int weight, int quality,
            LootItemCondition[] conditions, LootItemFunction[] functions) {
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

    public static final class Serializer extends LootPoolSingletonContainer.Serializer<GuardAmmoLootEntry> {
        @Override
        protected GuardAmmoLootEntry deserialize(JsonObject json, JsonDeserializationContext context,
                int weight, int quality, LootItemCondition[] conditions, LootItemFunction[] functions) {
            var array = GsonHelper.getAsJsonArray(json, "choices");
            if (array.isEmpty() || array.size() > 16) throw new JsonParseException("Expected 1 to 16 ammo choices");
            List<ResourceLocation> choices = new ArrayList<>(array.size());
            for (var value : array) {
                choices.add(ResourceLocation.parse(GsonHelper.convertToString(value, "ammo ID")));
            }
            return new GuardAmmoLootEntry(choices, weight, quality, conditions, functions);
        }

        @Override
        public void serializeCustom(JsonObject json, GuardAmmoLootEntry entry, JsonSerializationContext context) {
            super.serializeCustom(json, entry, context);
            JsonArray choices = new JsonArray();
            entry.choices.forEach(id -> choices.add(id.toString()));
            json.add("choices", choices);
        }
    }
}
