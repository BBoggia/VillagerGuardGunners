package com.gamergaming.villagerguardgunners.village.compat;

import com.google.gson.*;
import java.util.*;
import static com.gamergaming.villagerguardgunners.village.compat.SettlementProfile.*;

/** Verified template metadata. Candidate entrances are not a claim of traversability. */
public record PieceMeaning(Kind kind, List<Point> beds, List<Point> entrances,
        String resourceProvider, String fingerprint, String reason) {
    public PieceMeaning { beds = List.copyOf(beds); entrances = List.copyOf(entrances); }
    public record Point(int x, int y, int z) { }
    public record ResourceEvidence(String provider, String sha256) { }
    public boolean supported() { return reason.equals("verified_template"); }
    public static PieceMeaning parse(JsonObject json, ResourceEvidence resource) {
        fields(json, Set.of("kind", "sha256", "beds", "entrances"));
        String hash = string(json.get("sha256"));
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Expected template SHA256");
        Kind kind = Kind.valueOf(string(json.get("kind")).toUpperCase(Locale.ROOT));
        var beds = points(json.get("beds")); var entrances = points(json.get("entrances"));
        if (kind != Kind.RESIDENCE && !beds.isEmpty()) throw new IllegalArgumentException("Only residences carry bed capacity");
        if (kind == Kind.RESIDENCE && beds.isEmpty()) throw new IllegalArgumentException("Residence needs verified bed positions");
        return new PieceMeaning(kind, beds, entrances, resource.provider(), hash,
                hash.equals(resource.sha256()) ? "verified_template" : "resource_mismatch");
    }
    public static PieceMeaning unavailable(String provider, String hash, String reason) {
        return new PieceMeaning(Kind.UNSUPPORTED, List.of(), List.of(), provider, hash, reason);
    }
    private static List<Point> points(JsonElement element) {
        if (element == null) return List.of();
        if (!element.isJsonArray() || element.getAsJsonArray().size() > 64) throw new IllegalArgumentException("Too many template points");
        List<Point> result = new ArrayList<>();
        for (var value : element.getAsJsonArray()) {
            if (!value.isJsonArray() || value.getAsJsonArray().size() != 3) throw new IllegalArgumentException("Expected x,y,z");
            var a = value.getAsJsonArray();
            var point = new Point(integer(a.get(0), -512, 512), integer(a.get(1), -512, 512), integer(a.get(2), -512, 512));
            if (result.contains(point)) throw new IllegalArgumentException("Duplicate template point");
            result.add(point);
        }
        return List.copyOf(result);
    }
}
