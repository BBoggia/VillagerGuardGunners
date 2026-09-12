package com.gamergaming.villagerguardgunners.medical;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Bounded, whole-registry publication; a bad reload retains the last valid profiles. */
public final class TreatmentProfiles extends SimplePreparableReloadListener<TreatmentProfiles.Prepared> {
    private static final TreatmentProfiles INSTANCE = new TreatmentProfiles();
    private static volatile Map<ResourceLocation, TreatmentProfile> profiles = Map.of();
    private static volatile Map<ResourceLocation, List<TreatmentProfile>> byItem = Map.of();
    public record Prepared(Map<ResourceLocation, TreatmentProfile> profiles, @Nullable String error) {}
    public static void onReload(AddReloadListenerEvent event) { event.addListener(INSTANCE); }
    public static void onStopped(ServerStoppedEvent event) { profiles = Map.of(); byItem = Map.of(); }
    public static Map<ResourceLocation, TreatmentProfile> snapshot() { return profiles; }
    @Nullable public static TreatmentProfile resolve(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) return null;
        for (var profile : byItem.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), List.of())) {
            if (profile.potion() != null && (stack.hasTag() && stack.getTag().contains("CustomPotionEffects")
                    || !BuiltInRegistries.POTION.getKey(PotionUtils.getPotion(stack)).equals(profile.potion()))) continue;
            if (profile.returnedItem() != null && stack.getCount() != 1) continue;
            return profile;
        }
        return null;
    }
    public static void validate(TreatmentProfile profile) {
        var item = BuiltInRegistries.ITEM.getOptional(profile.item()).orElseThrow(() -> new IllegalArgumentException("Unknown treatment item"));
        if (item == Items.AIR || !item.canFitInsideContainerItems() || item instanceof IGun
                || item instanceof IAmmo || item instanceof IAmmoBox) throw new IllegalArgumentException("Invalid medical item");
        if (item instanceof PotionItem) {
            if (item != Items.POTION || profile.potion() == null
                    || !(profile.potion().equals(BuiltInRegistries.POTION.getKey(Potions.HEALING))
                    || profile.potion().equals(BuiltInRegistries.POTION.getKey(Potions.STRONG_HEALING)))
                    || !ResourceLocation.withDefaultNamespace("glass_bottle").equals(profile.returnedItem()))
                throw new IllegalArgumentException("Only plain drinkable healing potions with returned bottles are supported");
        } else if (profile.potion() != null) throw new IllegalArgumentException("Potion selector on a non-potion item");
        if (profile.returnedItem() != null) {
            var returned = BuiltInRegistries.ITEM.getOptional(profile.returnedItem()).orElseThrow();
            if (new ItemStack(item).getMaxStackSize() != 1 || returned == Items.AIR || !returned.canFitInsideContainerItems())
                throw new IllegalArgumentException("Container-return treatments require a single-stack dose item");
        }
    }
    @Override protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        var converter = FileToIdConverter.json("medical_treatments");
        var resources = converter.listMatchingResources(manager);
        if (resources.size() > 128) return new Prepared(Map.of(), "Too many medical profiles (maximum 128)");
        Map<ResourceLocation, TreatmentProfile> next = new HashMap<>();
        try {
            for (var entry : resources.entrySet()) {
                try (var reader = entry.getValue().openAsReader()) {
                    var value = TreatmentProfile.parse(JsonParser.parseReader(reader).getAsJsonObject());
                    next.put(converter.fileToId(entry.getKey()), value);
                }
            }
            return new Prepared(Map.copyOf(next), null);
        } catch (Exception exception) { return new Prepared(Map.of(), exception.toString()); }
    }
    @Override protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        try {
            if (prepared.error != null) throw new IllegalArgumentException(prepared.error);
            var selectors = new java.util.HashSet<String>();
            Map<ResourceLocation, java.util.ArrayList<TreatmentProfile>> index = new HashMap<>();
            for (var value : prepared.profiles.values()) {
                validate(value);
                if (!selectors.add(value.item() + "/" + value.potion())) throw new IllegalArgumentException("Ambiguous medical selectors");
                index.computeIfAbsent(value.item(), ignored -> new java.util.ArrayList<>()).add(value);
            }
            Map<ResourceLocation, List<TreatmentProfile>> frozen = new HashMap<>();
            index.forEach((item, values) -> frozen.put(item, List.copyOf(values)));
            byItem = Map.copyOf(frozen); profiles = prepared.profiles;
        } catch (RuntimeException exception) {
            LogUtils.getLogger().warn("Medical treatment reload rejected; retaining the previous profiles: {}", exception.getMessage());
        }
    }
}
