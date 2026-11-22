package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.HashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

public final class EquipmentProfiles extends SimplePreparableReloadListener<EquipmentProfiles.Prepared> {
    private static final EquipmentProfiles INSTANCE = new EquipmentProfiles();
    private static Map<ResourceLocation, EquipmentProfile> profiles = Map.of();
    private static long revision;
    public record Prepared(Map<ResourceLocation, EquipmentProfile> profiles, @Nullable String error) {}
    public static Map<ResourceLocation, EquipmentProfile> snapshot() { return profiles; }
    public static long revision() { return revision; }
    public static void onReload(AddReloadListenerEvent event) { event.addListener(INSTANCE); }
    public static void stopped(ServerStoppedEvent event) { profiles = Map.of(); revision++; }
    @Override protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        var converter = FileToIdConverter.json("quartermaster_loadouts");
        var resources = converter.listMatchingResources(manager);
        if (resources.size() > 128) return new Prepared(Map.of(), "Too many equipment profiles (maximum 128)");
        Map<ResourceLocation, EquipmentProfile> next = new HashMap<>();
        try {
            for (var entry : resources.entrySet()) {
                try (var reader = entry.getValue().openAsReader()) {
                    var value = EquipmentProfile.parse(JsonParser.parseReader(reader).getAsJsonObject());
                    next.put(converter.fileToId(entry.getKey()), value);
                }
            }
            return new Prepared(Map.copyOf(next), null);
        } catch (Exception exception) { return new Prepared(Map.of(), exception.toString()); }
    }
    @Override protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        if (prepared.error != null || prepared.profiles.keySet().stream().anyMatch(id -> id.toString().length() > 96)) {
            LogUtils.getLogger().warn("Equipment profile reload rejected; retaining previous profiles: {}", prepared.error); return;
        }
        profiles = Map.copyOf(prepared.profiles); revision++;
    }
}
