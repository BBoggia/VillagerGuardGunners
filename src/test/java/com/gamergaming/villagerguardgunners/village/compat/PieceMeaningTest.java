package com.gamergaming.villagerguardgunners.village.compat;

import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PieceMeaningTest {
    private static final String A = "a".repeat(64), B = "b".repeat(64);
    private static JsonObject json(String hash, String kind, String beds) {
        return JsonParser.parseString("{\"sha256\":\""+hash+"\",\"kind\":\""+kind+"\",\"beds\":"+beds+"}").getAsJsonObject();
    }
    private static SettlementProfile profile(String effective) {
        var root = JsonParser.parseString("{\"format\":2,\"intent\":\"friendly\",\"structures\":[\"minecraft:village_plains\"]}").getAsJsonObject();
        var templates = new JsonObject(); var variants = new JsonArray();
        variants.add(json(A,"residence","[[1,2,3]]")); variants.add(json(B,"decoration","[]"));
        templates.add("minecraft:village/plains/houses/test", variants); root.add("templates", templates);
        return SettlementProfile.parse("test:profile", "definitions", root, Map.of(), id -> new PieceMeaning.ResourceEvidence("winning-pack",effective)).profile();
    }
    @Test void effectiveResourceSelectsOnlyItsOwnMeaning() {
        assertEquals(SettlementProfile.Kind.RESIDENCE, profile(A).templates().values().iterator().next());
        assertEquals(SettlementProfile.Kind.DECORATION, profile(B).templates().values().iterator().next());
        assertEquals("winning-pack", profile(B).meanings().values().iterator().next().resourceProvider());
    }
    @Test void unknownOverrideCannotInheritHouseHeuristic() {
        var decision = SettlementCatalog.resolve(List.of(profile("c".repeat(64))),"minecraft:village_plains",Set.of());
        assertEquals(SettlementProfile.Kind.UNSUPPORTED, decision.kind("minecraft:village/plains/houses/test"));
        assertEquals(SettlementProfile.Kind.UNSUPPORTED, decision.kind("minecraft:village/plains/houses/new"));
        assertEquals(0, decision.meaning("minecraft:village/plains/houses/test").beds().size());
    }
    @Test void decorationAndEmptyResidencesCannotInventCapacity() {
        var evidence = new PieceMeaning.ResourceEvidence("pack",A);
        assertThrows(IllegalArgumentException.class, () -> PieceMeaning.parse(json(A,"decoration","[[0,0,0]]"), evidence));
        assertThrows(IllegalArgumentException.class, () -> PieceMeaning.parse(json(A,"residence","[]"), evidence));
        assertThrows(IllegalArgumentException.class, () -> PieceMeaning.parse(json(A,"residence","[[0,0,0],[0,0,0]]"), evidence));
        assertThrows(IllegalArgumentException.class, () -> PieceMeaning.parse(json(A,"residence","[[0,0.5,0]]"), evidence));
    }
}
