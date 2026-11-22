package com.gamergaming.villagerguardgunners.village.compat;

import com.google.gson.*;
import java.util.*;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

/** Versioned classification inputs. No world, chunk, entity, or optional class references. */
public record SettlementProfile(String id, String provider, int priority, Intent intent,
        String reader, Set<String> structures, Set<String> structureTags,
        Map<String, Kind> templates, Set<String> targetPools,
        Map<String, PieceMeaning> meanings, String sizeFamily, Set<String> traversalLimits, boolean strictTemplates) {
    public enum Intent { FRIENDLY, HOSTILE, ABANDONED, UNSUPPORTED }
    public enum Kind { RESIDENCE, ROAD, GATHERING, DEFENSE, SERVICE, DECORATION, UNSUPPORTED }
    public static final String NATIVE = "villagerguardgunners:native";
    public static final String INTEGRATED = "integrated_api:single";
    public SettlementProfile {
        structures = Set.copyOf(structures); structureTags = Set.copyOf(structureTags);
        templates = Map.copyOf(templates); targetPools = Set.copyOf(targetPools);
        meanings = Map.copyOf(meanings); traversalLimits = Set.copyOf(traversalLimits);
    }
    public SettlementProfile(String id, String provider, int priority, Intent intent, String reader,
            Set<String> structures, Set<String> structureTags, Map<String, Kind> templates, Set<String> targetPools) {
        this(id, provider, priority, intent, reader, structures, structureTags, templates, targetPools, Map.of(), "unspecified", Set.of(), false);
    }
    public record Parsed(SettlementProfile profile, String inactiveReason) { }
    public static Parsed parse(String id, String provider, JsonObject json, Map<String, String> installed) {
        return parse(id, provider, json, installed, ignored -> new PieceMeaning.ResourceEvidence("unavailable", ""));
    }
    public static Parsed parse(String id, String provider, JsonObject json, Map<String, String> installed,
            java.util.function.Function<String, PieceMeaning.ResourceEvidence> resources) {
        identifier(id);
        // Validate the applicability envelope before resolving any optional registry references.
        JsonObject conditions = json.has("mods") ? object(json.get("mods"), "mods") : new JsonObject();
        if (conditions.size() > 16) throw new IllegalArgumentException("Too many mod conditions");
        List<String> inactive = new ArrayList<>();
        for (var entry : conditions.entrySet()) {
            if (!entry.getKey().matches("[a-z][a-z0-9_]{1,63}")) throw new IllegalArgumentException("Invalid mod ID");
            String range = string(entry.getValue());
            try {
                var version = VersionRange.createFromVersionSpec(range);
                if (!version.hasRestrictions()) throw new IllegalArgumentException("Use a bounded Maven range or [exact.version]");
                String actual = installed.get(entry.getKey());
                if (actual == null) inactive.add(entry.getKey() + " absent");
                else if (!version.containsVersion(new DefaultArtifactVersion(actual))) inactive.add(entry.getKey() + " version " + actual + " outside " + range);
            } catch (org.apache.maven.artifact.versioning.InvalidVersionSpecificationException failure) {
                throw new IllegalArgumentException("Invalid version range", failure);
            }
        }
        if (!inactive.isEmpty()) return new Parsed(null, String.join("; ", inactive));
        int format = integer(json.get("format"), 1, 2);
        var allowed = new HashSet<>(Set.of("format", "mods", "priority", "intent", "reader", "structures", "structure_tags", "templates", "target_pools"));
        if (format == 2) allowed.addAll(Set.of("size_family", "traversal_limits"));
        fields(json, allowed);
        int priority = json.has("priority") ? integer(json.get("priority"), 0, 100) : 0;
        Intent intent = Intent.valueOf(string(json.get("intent")).toUpperCase(Locale.ROOT));
        String reader = json.has("reader") ? identifier(string(json.get("reader"))) : NATIVE;
        if (!Set.of(NATIVE, INTEGRATED).contains(reader)) throw new IllegalArgumentException("Unknown piece reader " + reader);
        Set<String> structures = identifiers(json.get("structures"), 256);
        Set<String> tags = identifiers(json.get("structure_tags"), 256);
        if (structures.size() + tags.size() == 0 || structures.size() + tags.size() > 256) throw new IllegalArgumentException("Expected 1..256 structure selectors");
        Map<String, Kind> templates = new TreeMap<>();
        Map<String, PieceMeaning> meanings = new TreeMap<>();
        if (json.has("templates")) {
            var entries = object(json.get("templates"), "templates");
            if (entries.size() > (format == 1 ? 512 : 4096)) throw new IllegalArgumentException("Too many template classifications");
            for (var entry : entries.entrySet()) {
                String template = identifier(entry.getKey());
                if (format == 1) templates.put(template, Kind.valueOf(string(entry.getValue()).toUpperCase(Locale.ROOT)));
                else {
                    JsonArray variants = entry.getValue().isJsonArray() ? entry.getValue().getAsJsonArray() : new JsonArray();
                    if (variants.isEmpty() || variants.size() > 8) throw new IllegalArgumentException("Expected 1..8 template variants");
                    var resource = resources.apply(template);
                    PieceMeaning winner = null;
                    Set<String> hashes = new HashSet<>();
                    for (var value : variants) {
                        var candidate = PieceMeaning.parse(object(value, "template variant"), resource);
                        if (!hashes.add(candidate.fingerprint())) throw new IllegalArgumentException("Duplicate template fingerprint");
                        if (candidate.supported()) winner = candidate;
                    }
                    if (winner == null) winner = PieceMeaning.unavailable(resource.provider(), resource.sha256(), "unverified_resource");
                    meanings.put(template, winner); templates.put(template, winner.kind());
                }
            }
        }
        String family = json.has("size_family") ? string(json.get("size_family")) : "unspecified";
        if (!Set.of("unspecified", "small", "medium", "large").contains(family)) throw new IllegalArgumentException("Unknown size family");
        Set<String> limits = new LinkedHashSet<>();
        if (json.has("traversal_limits")) {
            var values = json.get("traversal_limits");
            if (!values.isJsonArray() || values.getAsJsonArray().size() > 8) throw new IllegalArgumentException("Invalid traversal limits");
            for (var value : values.getAsJsonArray()) {
                String limit = string(value);
                if (!Set.of("water", "elevation", "steep_terrain", "mechanisms", "underground", "unverified_variant").contains(limit)
                        || !limits.add(limit)) throw new IllegalArgumentException("Invalid traversal limitation");
            }
        }
        return new Parsed(new SettlementProfile(id, provider, priority, intent, reader, structures, tags,
                templates, identifiers(json.get("target_pools"), 256), meanings, family, limits, format == 2), null);
    }
    public boolean matches(String structure, Set<String> tags) {
        return structures.contains(structure) || structureTags.stream().anyMatch(tags::contains);
    }
    public boolean sameMeaning(SettlementProfile other) {
        return intent == other.intent && reader.equals(other.reader) && templates.equals(other.templates)
                && targetPools.equals(other.targetPools) && meanings.equals(other.meanings)
                && sizeFamily.equals(other.sizeFamily) && traversalLimits.equals(other.traversalLimits) && strictTemplates == other.strictTemplates;
    }
    public static boolean nativeResidence(String template) {
        return template.startsWith("minecraft:village/") && template.contains("/houses/");
    }
    static Set<String> identifiers(JsonElement element, int maximum) {
        if (element == null) return Set.of();
        if (!element.isJsonArray() || element.getAsJsonArray().size() > maximum) throw new IllegalArgumentException("Invalid ID list");
        Set<String> result = new LinkedHashSet<>();
        for (var value : element.getAsJsonArray()) if (!result.add(identifier(string(value)))) throw new IllegalArgumentException("Duplicate ID");
        return result;
    }
    public static String identifier(String text) {
        if (text.length() > 256 || !text.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid namespaced ID: " + text);
        return text;
    }
    static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string");
        return value.getAsString();
    }
    static int integer(JsonElement value, int min, int max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer");
        int integer;
        try { integer = value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException failure) { throw new IllegalArgumentException("Expected exact integer", failure); }
        if (integer < min || integer > max) throw new IllegalArgumentException("Integer outside " + min + ".." + max);
        return integer;
    }
    static JsonObject object(JsonElement value, String name) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Expected " + name + " object");
        return value.getAsJsonObject();
    }
    static void fields(JsonObject json, Set<String> allowed) {
        if (!allowed.containsAll(json.keySet())) throw new IllegalArgumentException("Unknown profile fields");
    }
}
