package com.gamergaming.villagerguardgunners.village.compat;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.ModList;

/** Stage complete profiles during reload; publish once server tags have been bound. */
public final class SettlementDefinitions extends SimplePreparableReloadListener<SettlementDefinitions.Prepared> {
    public static final String DIRECTORY = "village_compatibility";
    public static final int MAX_PROFILES = 128, MAX_BYTES = 2_097_152, MAX_TOTAL_BYTES = 16_777_216;
    private static volatile SettlementCatalog snapshot = SettlementCatalog.empty();
    private static volatile List<String> errors = List.of();
    private static volatile Prepared pending;
    private final RegistryAccess access;
    private SettlementDefinitions(RegistryAccess access) { this.access = access; }
    public static SettlementCatalog snapshot() { return snapshot; }
    public static List<String> errors() { return errors; }
    public static SettlementCatalog.Decision lookup(ResourceLocation structure) { return snapshot.get(structure.toString()); }
    public static Map<String, String> installed() {
        Map<String, String> result = new TreeMap<>();
        ModList.get().getMods().forEach(m -> result.put(m.getModId(), m.getVersion().toString()));
        return Map.copyOf(result);
    }
    public static void register(AddReloadListenerEvent event) { event.addListener(new SettlementDefinitions(event.getRegistryAccess())); }
    public static void stopped(ServerStoppedEvent event) { snapshot = SettlementCatalog.empty(); errors = List.of(); pending = null; }
    public record Prepared(List<SettlementProfile> profiles, Map<String, String> inactive, List<String> errors, Map<String, PieceMeaning.ResourceEvidence> evidence) {
        public Prepared { evidence = Map.copyOf(evidence); profiles = List.copyOf(profiles); inactive = Map.copyOf(inactive); errors = List.copyOf(errors); }
    }
    @Override protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        var converter = FileToIdConverter.json(DIRECTORY);
        var resources = converter.listMatchingResources(manager);
        List<SettlementProfile> profiles = new ArrayList<>();
        Map<String, String> inactive = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        if (resources.size() > MAX_PROFILES) return new Prepared(List.of(), Map.of(), List.of("Too many compatibility profiles"), Map.of());
        var installed = installed();
        long[] templateBytes = {0};
        Map<String, PieceMeaning.ResourceEvidence> evidence = new HashMap<>();
        java.util.function.Function<String, PieceMeaning.ResourceEvidence> resolve = id -> evidence.computeIfAbsent(id, template -> {
            if (evidence.size() >= 16_384) throw new IllegalArgumentException("Too many template resources");
            var name = ResourceLocation.parse(template);
            var resource = manager.getResource(ResourceLocation.parse(name.getNamespace() + ":structures/" + name.getPath() + ".nbt"));
            if (resource.isEmpty()) return new PieceMeaning.ResourceEvidence("missing", "");
            try (var input = resource.get().open()) {
                byte[] bytes = input.readNBytes(2_097_153);
                templateBytes[0] += bytes.length;
                if (templateBytes[0] > 134_217_728L) throw new IllegalArgumentException("Template evidence exceeds total byte limit");
                if (bytes.length > 2_097_152) return new PieceMeaning.ResourceEvidence(resource.get().sourcePackId(), "");
                return new PieceMeaning.ResourceEvidence(resource.get().sourcePackId(),
                        java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
                throw new IllegalArgumentException("Cannot inspect template " + template, failure);
            }
        });
        int total = 0;
        for (var entry : resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String id = converter.fileToId(entry.getKey()).toString();
            try (var input = entry.getValue().open()) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) { failures.add("Compatibility data exceeds total byte limit"); break; }
                if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Compatibility data exceeds byte limit");
                var json = CompatibilityJson.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                var parsed = SettlementProfile.parse(id, entry.getValue().sourcePackId(), json.getAsJsonObject(), installed, resolve);
                if (parsed.profile() == null) inactive.put(id, entry.getValue().sourcePackId() + ": " + parsed.inactiveReason());
                else if (parsed.profile().reader().equals(SettlementProfile.INTEGRATED) && !"1.8.0".equals(installed.get("integrated_api")))
                    inactive.put(id, entry.getValue().sourcePackId() + ": integrated reader requires verified Integrated API 1.8.0");
                else profiles.add(parsed.profile());
            } catch (Exception failure) { failures.add(id + " (" + entry.getValue().sourcePackId() + "): " + failure.getMessage()); }
        }
        // Retained profiles need fresh resource evidence even if another profile is invalid
        // or a previously active profile was removed from the incoming pack set.
        for (String template : snapshot.profiles().stream().flatMap(p -> p.meanings().keySet().stream()).distinct().limit(16_385).toList()) {
            try { resolve.apply(template); }
            catch (RuntimeException failure) { failures.add("Retained template " + template + ": " + failure.getMessage()); }
        }
        return new Prepared(profiles, inactive, failures, evidence);
    }
    @Override protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        List<String> failures = new ArrayList<>(prepared.errors());
        var structures = access.registryOrThrow(Registries.STRUCTURE);
        var pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
        for (var profile : prepared.profiles()) {
            for (String id : profile.structures()) if (!structures.containsKey(ResourceLocation.parse(id))) failures.add(profile.id() + ": missing structure " + id);
            for (String id : profile.targetPools()) if (!pools.containsKey(ResourceLocation.parse(id))) failures.add(profile.id() + ": missing pool " + id);
        }
        pending = new Prepared(prepared.profiles(), prepared.inactive(), failures, prepared.evidence());
    }
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        Prepared prepared = pending;
        pending = null;
        if (prepared == null) return;
        var registry = event.getRegistryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<String> tagNames = registry.getTagNames().map(k -> k.location().toString()).collect(java.util.stream.Collectors.toSet());
        List<String> failures = new ArrayList<>(prepared.errors());
        prepared.profiles().forEach(p -> p.structureTags().stream().filter(t -> !tagNames.contains(t)).forEach(t -> failures.add(p.id() + ": missing structure tag " + t)));
        if (registry.size() > 16_384) failures.add("Structure registry exceeds compatibility resolution limit");
        if (!failures.isEmpty()) {
            errors = List.copyOf(failures);
            // Profile choices remain unchanged, but stale resource-specific hints must not
            // survive a Minecraft resource replacement. Advance generation only if needed.
            snapshot = snapshot.revalidateTemplates(prepared.evidence());
            LogUtils.getLogger().warn("Village compatibility reload rejected; retaining profile choices at generation {}: {}", snapshot.generation(), errors);
            // Startup still obtains native tag support even when custom profiles are invalid.
            if (snapshot.generation() == 0 && registry.size() <= 16_384) publish(List.of(), prepared.inactive(), registry, 0);
            return;
        }
        publish(prepared.profiles(), prepared.inactive(), registry, snapshot.generation() + 1);
        errors = List.of();
    }
    private static void publish(List<SettlementProfile> profiles, Map<String, String> inactive,
            net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> registry, long generation) {
        Map<String, SettlementCatalog.Decision> decisions = new HashMap<>();
        registry.holders().forEach(holder -> {
            String id = holder.key().location().toString();
            Set<String> tags = holder.tags().map(k -> k.location().toString()).collect(java.util.stream.Collectors.toSet());
            decisions.put(id, SettlementCatalog.resolve(profiles, id, tags));
        });
        snapshot = new SettlementCatalog(generation, profiles, inactive, decisions);
    }
}
