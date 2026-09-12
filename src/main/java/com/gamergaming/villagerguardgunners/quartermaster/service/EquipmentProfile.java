package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import com.tacz.guns.api.item.IGun;

/** Selects physical stock only. No item construction or NBT rewriting is part of a profile. */
public record EquipmentProfile(List<String> primary, List<String> sidearm, Map<String, List<String>> armor,
        int ammoReserve, List<String> medical, int medicalReserve, List<String> roles) {
    private static final Set<String> ARMOR = Set.of("head", "chest", "legs", "feet");
    public static EquipmentProfile parse(JsonObject json) {
        if (!Set.of("format", "primary", "sidearm", "armor", "ammo_reserve", "medical", "medical_reserve", "roles").containsAll(json.keySet())
                || integer(json, "format", 1, 1, -1) != 1) throw new IllegalArgumentException("Unsupported equipment profile");
        Map<String, List<String>> armor = new HashMap<>();
        if (json.has("armor")) {
            var values = json.getAsJsonObject("armor");
            if (!ARMOR.containsAll(values.keySet())) throw new IllegalArgumentException("Invalid armor slot");
            for (String key : values.keySet()) armor.put(key, selectors(values, key));
        }
        List<String> roles = selectors(json, "roles");
        if (roles.stream().anyMatch(role -> role.startsWith("#"))) throw new IllegalArgumentException("Role associations require role IDs");
        return new EquipmentProfile(selectors(json, "primary"), selectors(json, "sidearm"), Map.copyOf(armor),
                integer(json, "ammo_reserve", 0, 512, 64), selectors(json, "medical"),
                integer(json, "medical_reserve", 0, 192, 16), roles);
    }
    private static int integer(JsonObject json, String key, int min, int max, int fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException("Expected integer");
        int value = json.get(key).getAsBigDecimal().intValueExact();
        if (value < min || value > max) throw new IllegalArgumentException("Profile bound exceeded"); return value;
    }
    private static List<String> selectors(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        var values = json.getAsJsonArray(key); if (values.size() > 16) throw new IllegalArgumentException("Too many selectors");
        var result = new ArrayList<String>();
        for (var value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected item ID or tag");
            String text = value.getAsString(); String id = text.startsWith("#") ? text.substring(1) : text;
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed == null || !parsed.toString().equals(id) || text.length() > 96 || result.contains(text)) throw new IllegalArgumentException("Invalid or duplicate selector");
            result.add(text);
        }
        return List.copyOf(result);
    }
    public static boolean matches(ItemStack stack, List<String> selectors, boolean gun) {
        if (stack.isEmpty() || (gun && !(stack.getItem() instanceof IGun))) return false;
        ResourceLocation id = gun ? ((IGun) stack.getItem()).getGunId(stack) : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String selector : selectors) {
            if (selector.startsWith("#")) {
                if (stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(selector.substring(1))))) return true;
            } else if (id != null && id.toString().equals(selector)) return true;
        }
        return false;
    }
}
