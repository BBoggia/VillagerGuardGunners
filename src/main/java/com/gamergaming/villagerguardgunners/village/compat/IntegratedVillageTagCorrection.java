package com.gamergaming.villagerguardgunners.village.compat;

import java.security.MessageDigest;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraftforge.fml.ModList;

/** Correct only the verified broken resource before TagLoader resolves required references. */
public final class IntegratedVillageTagCorrection {
    public static final String VERSION = "1.3.2+1.20.1-forge";
    public static final String RESOURCE_HASH = "1a2586240d3fe8c1fedf536e751da30c1ba9bdb25dfd97d93ed8c4ce065fbc9b";
    public static final ResourceLocation MISSING = ResourceLocation.parse("integrated_villages:swamp_village");
    public static final ResourceLocation VILLAGE = ResourceLocation.parse("minecraft:village");
    private static volatile Map<String, Object> status = Map.of("state", "not_observed");
    private IntegratedVillageTagCorrection() { }
    public static Map<String, Object> status() { return status; }
    public static boolean verified(String version, String forge, String hash, boolean present) {
        return VERSION.equals(version) && "47.4.10".equals(forge) && RESOURCE_HASH.equals(hash) && !present;
    }
    public static void correct(ResourceManager resources, Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags, boolean missingExists) {
        var mods = ModList.get();
        var mod = mods == null ? Optional.<net.minecraftforge.fml.ModContainer>empty() : mods.getModContainerById("integrated_villages");
        if (mod.isEmpty()) { status = Map.of("state", "optional_mod_absent"); return; }
        String version = mod.get().getModInfo().getVersion().toString();
        String forge = mods.getModContainerById("forge").map(m -> m.getModInfo().getVersion().toString()).orElse("");
        if (!VERSION.equals(version) || !"47.4.10".equals(forge) || missingExists) {
            status = Map.of("state", missingExists ? "entry_resolves" : "unverified_version", "version", version, "forge", forge); return;
        }
        String source = mods.getModFileById("integrated_villages").getFile().getFileName();
        var contributions = resources.getResourceStack(ResourceLocation.parse("minecraft:tags/worldgen/structure/village.json"))
                .stream().filter(r -> r.sourcePackId().equals(source)).toList();
        if (contributions.size() != 1) { status = Map.of("state", "unverified_resource_source"); return; }
        try (var stream = contributions.get(0).open()) {
            byte[] bytes = stream.readNBytes(16_385);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!verified(version, forge, hash, missingExists)) { status = Map.of("state", "resource_changed"); return; }
            int removed = removeBrokenEntry(tags, source);
            status = Map.of("state", removed == 0 ? "entry_absent_or_replaced" : "corrected", "removed", removed, "source", source, "resourceSha256", hash);
        } catch (Exception failure) { status = Map.of("state", "resource_unavailable", "reason", failure.toString()); }
    }
    public static int removeBrokenEntry(Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags, String source) {
        var entries = tags.get(VILLAGE);
        if (entries == null) return 0;
        var corrected = entries.stream().filter(e -> !(source.equals(e.source()) && !e.remove()
                && e.entry().isRequired() && !e.entry().isTag() && MISSING.equals(e.entry().getId()))).toList();
        int removed = entries.size() - corrected.size();
        if (removed > 0) tags.put(VILLAGE, new ArrayList<>(corrected));
        return removed;
    }
}
