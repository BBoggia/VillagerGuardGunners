package com.gamergaming.villagerguardgunners.village.compat;

import java.util.*;
import static com.gamergaming.villagerguardgunners.village.compat.SettlementProfile.*;

/** Immutable publication: identity and saved population plans are deliberately outside this catalog. */
public record SettlementCatalog(long generation, List<SettlementProfile> profiles,
        Map<String, String> inactive, Map<String, Decision> settlements) {
    public static final Set<String> VANILLA = Set.of("minecraft:village_plains", "minecraft:village_desert",
            "minecraft:village_savanna", "minecraft:village_snowy", "minecraft:village_taiga");
    public SettlementCatalog {
        profiles = List.copyOf(profiles); inactive = Map.copyOf(inactive); settlements = Map.copyOf(settlements);
    }
    public static SettlementCatalog empty() { return new SettlementCatalog(0, List.of(), Map.of(), Map.of()); }
    public Decision get(String structure) {
        Decision resolved = settlements.get(structure);
        return resolved == null ? fallback(structure, Set.of()) : resolved;
    }
    public boolean allowsPool(String pool) {
        return profiles.stream().noneMatch(p -> p.targetPools().contains(pool) && p.intent() != Intent.FRIENDLY);
    }
    public record Decision(Intent intent, String reader, Map<String, Kind> templates,
            List<String> profiles, List<String> providers, String reason, Map<String, PieceMeaning> meanings,
            String sizeFamily, Set<String> traversalLimits, boolean strictTemplates) {
        public Decision { meanings = Map.copyOf(meanings); traversalLimits = Set.copyOf(traversalLimits); templates = Map.copyOf(templates); profiles = List.copyOf(profiles); providers = List.copyOf(providers); }
        public Decision(Intent intent, String reader, Map<String, Kind> templates, List<String> profiles, List<String> providers, String reason) {
            this(intent, reader, templates, profiles, providers, reason, Map.of(), "unspecified", Set.of(), false);
        }
        public PieceMeaning meaning(String template) {
            return meanings.getOrDefault(template, PieceMeaning.unavailable("unknown", "", "unmapped_template"));
        }
        public boolean friendly() { return intent == Intent.FRIENDLY; }
        public Kind kind(String template) {
            return templates.getOrDefault(template, !strictTemplates && nativeResidence(template) ? Kind.RESIDENCE : Kind.UNSUPPORTED);
        }
    }
    /** A rejected profile reload can still replace Minecraft's template resources. */
    public SettlementCatalog revalidateTemplates(Map<String, PieceMeaning.ResourceEvidence> resources) {
        List<SettlementProfile> updatedProfiles = new ArrayList<>();
        for (var p : profiles) {
            var meanings = revalidate(p.meanings(), resources);
            var kinds = new HashMap<>(p.templates()); meanings.forEach((id, m) -> kinds.put(id, m.kind()));
            updatedProfiles.add(new SettlementProfile(p.id(), p.provider(), p.priority(), p.intent(), p.reader(),
                    p.structures(), p.structureTags(), kinds, p.targetPools(), meanings, p.sizeFamily(), p.traversalLimits(), p.strictTemplates()));
        }
        Map<String, Decision> updated = new HashMap<>();
        settlements.forEach((id, d) -> {
            var meanings = revalidate(d.meanings(), resources);
            var kinds = new HashMap<>(d.templates()); meanings.forEach((template, m) -> kinds.put(template, m.kind()));
            updated.put(id, new Decision(d.intent(), d.reader(), kinds, d.profiles(), d.providers(), d.reason(),
                    meanings, d.sizeFamily(), d.traversalLimits(), d.strictTemplates()));
        });
        if (updatedProfiles.equals(profiles) && updated.equals(settlements)) return this;
        return new SettlementCatalog(generation + 1, updatedProfiles, inactive, updated);
    }
    private static Map<String, PieceMeaning> revalidate(Map<String, PieceMeaning> meanings,
            Map<String, PieceMeaning.ResourceEvidence> resources) {
        Map<String, PieceMeaning> result = new HashMap<>();
        meanings.forEach((id, meaning) -> {
            var resource = resources.getOrDefault(id, new PieceMeaning.ResourceEvidence("unavailable", ""));
            if (meaning.supported() && meaning.fingerprint().equals(resource.sha256()))
                result.put(id, new PieceMeaning(meaning.kind(), meaning.beds(), meaning.entrances(),
                        resource.provider(), meaning.fingerprint(), meaning.reason()));
            else result.put(id, PieceMeaning.unavailable(resource.provider(), resource.sha256(), "unverified_resource"));
        });
        return Map.copyOf(result);
    }
    public static Decision resolve(List<SettlementProfile> profiles, String structure, Set<String> tags) {
        List<SettlementProfile> matches = profiles.stream().filter(p -> p.matches(structure, tags)).toList();
        if (matches.isEmpty()) return fallback(structure, tags);
        List<SettlementProfile> excluded = matches.stream().filter(p -> p.intent() != Intent.FRIENDLY).toList();
        if (!excluded.isEmpty()) matches = excluded;
        int priority = matches.stream().mapToInt(SettlementProfile::priority).max().orElseThrow();
        List<SettlementProfile> winners = matches.stream().filter(p -> p.priority() == priority)
                .sorted(Comparator.comparing(SettlementProfile::id)).toList();
        var first = winners.get(0);
        boolean ambiguous = winners.stream().anyMatch(p -> !first.sameMeaning(p));
        return new Decision(ambiguous ? Intent.UNSUPPORTED : first.intent(), first.reader(),
                ambiguous ? Map.of() : first.templates(), winners.stream().map(SettlementProfile::id).toList(),
                winners.stream().map(SettlementProfile::provider).toList(), ambiguous ? "ambiguous_profiles" : excluded.isEmpty() ? "profile" : "explicit_exclusion",
                ambiguous ? Map.of() : first.meanings(), first.sizeFamily(), first.traversalLimits(), ambiguous || first.strictTemplates());
    }
    private static Decision fallback(String structure, Set<String> tags) {
        boolean nativeVillage = VANILLA.contains(structure) || tags.contains("minecraft:village");
        return new Decision(nativeVillage ? Intent.FRIENDLY : Intent.UNSUPPORTED, NATIVE, Map.of(),
                List.of(), List.of(), nativeVillage ? "native_village_fallback" : "no_definition");
    }
}
