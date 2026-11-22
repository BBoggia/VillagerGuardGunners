package com.gamergaming.villagerguardgunners.medical;

import com.google.gson.JsonObject;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Declarative one-item dose. Profiles never execute an item's use or potion-effect callbacks. */
public record TreatmentProfile(ResourceLocation item, @Nullable ResourceLocation potion,
        float healing, int cooldownTicks, @Nullable ResourceLocation returnedItem) {
    public TreatmentProfile {
        if (item == null || !Float.isFinite(healing) || healing <= 0 || healing > 64
                || cooldownTicks < 1 || cooldownTicks > 1200) throw new IllegalArgumentException("Invalid treatment bounds");
    }
    public static TreatmentProfile parse(JsonObject json) {
        if (!Set.of("format", "item", "potion", "healing", "cooldown_ticks", "return_item").containsAll(json.keySet())
                || integer(json, "format") != 1) throw new IllegalArgumentException("Unsupported treatment format or fields");
        if (!json.has("healing") || !json.get("healing").isJsonPrimitive()
                || !json.getAsJsonPrimitive("healing").isNumber()) throw new IllegalArgumentException("Healing must be numeric");
        return new TreatmentProfile(id(json, "item"), json.has("potion") ? id(json, "potion") : null,
                json.get("healing").getAsFloat(), integer(json, "cooldown_ticks"),
                json.has("return_item") ? id(json, "return_item") : null);
    }
    private static int integer(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key + " must be an integer");
        return json.get(key).getAsBigDecimal().intValueExact();
    }
    private static ResourceLocation id(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException(key + " must be an identifier");
        String value = json.get(key).getAsString();
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || !id.toString().equals(value)) throw new IllegalArgumentException("Use a canonical namespaced identifier");
        return id;
    }
}
