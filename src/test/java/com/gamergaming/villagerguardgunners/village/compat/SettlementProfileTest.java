package com.gamergaming.villagerguardgunners.village.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;

class SettlementProfileTest {
    static SettlementProfile profile(String id, String extra) {
        return SettlementProfile.parse(id, "test-pack", JsonParser.parseString("{\"format\":1,\"intent\":\"friendly\",\"structures\":[\"test:village\"]" + extra + "}").getAsJsonObject(), Map.of()).profile();
    }
    @Test void absentAndUnverifiedModsDoNotResolveOptionalPayloads() {
        var json = JsonParser.parseString("{\"mods\":{\"missing_mod\":\"[1.0]\"},\"format\":999,\"reader\":\"missing:class\"}").getAsJsonObject();
        var absent = SettlementProfile.parse("test:optional", "pack", json, Map.of());
        assertNull(absent.profile()); assertTrue(absent.inactiveReason().contains("absent"));
        assertNull(SettlementProfile.parse("test:optional", "pack", json, Map.of("missing_mod", "2.0")).profile());
        assertThrows(IllegalArgumentException.class, () -> SettlementProfile.parse("test:optional", "pack", json, Map.of("missing_mod", "1.0")));
    }
    @Test void strictActiveFormatAndBounds() {
        for (String extra : List.of(",\"unknown\":true", ",\"priority\":1.5", ",\"priority\":101",
                ",\"reader\":\"test:unknown\"", ",\"templates\":{\"test:house\":\"shop\"}"))
            assertThrows(IllegalArgumentException.class, () -> profile("test:invalid", extra));
        assertThrows(IllegalArgumentException.class, () -> profile("UpperCase:invalid", ""));
    }
    @Test void vanillaAndAllSixtyEightTaggedCtovTypesRetainFallback() {
        assertTrue(SettlementCatalog.empty().get("minecraft:village_plains").friendly());
        for (int i = 0; i < 68; i++) assertTrue(SettlementCatalog.resolve(List.of(), "ctov:variant_" + i, Set.of("minecraft:village")).friendly());
        assertFalse(SettlementCatalog.resolve(List.of(), "test:village_named_hostile", Set.of()).friendly());
    }
    @Test void exclusionsWinEvenAgainstHigherPriorityFriendlyProfiles() {
        var friendly = profile("test:friendly", ",\"priority\":100");
        var hostile = new SettlementProfile("test:hostile", "pack", 0, SettlementProfile.Intent.HOSTILE,
                SettlementProfile.NATIVE, Set.of("test:village"), Set.of(), Map.of(), Set.of());
        var resolved = SettlementCatalog.resolve(List.of(friendly, hostile), "test:village", Set.of("minecraft:village"));
        assertEquals(SettlementProfile.Intent.HOSTILE, resolved.intent()); assertEquals("explicit_exclusion", resolved.reason());
    }
    @Test void conflictsAreIndependentOfIterationOrder() {
        var first = profile("test:first", ",\"templates\":{\"test:house\":\"residence\"}");
        var second = profile("test:second", ",\"templates\":{\"test:house\":\"decoration\"}");
        var a = SettlementCatalog.resolve(List.of(first, second), "test:village", Set.of());
        var b = SettlementCatalog.resolve(List.of(second, first), "test:village", Set.of());
        assertEquals(a, b); assertFalse(a.friendly()); assertEquals("ambiguous_profiles", a.reason());
        assertTrue(SettlementCatalog.resolve(List.of(first, profile("test:equivalent", ",\"templates\":{\"test:house\":\"residence\"}")), "test:village", Set.of()).friendly());
    }
    @Test void classificationsOverrideNativeHouseFallbackAndAreImmutable() {
        String house = "minecraft:village/plains/houses/plains_farm";
        var profile = profile("test:exact", ",\"templates\":{\"" + house + "\":\"decoration\",\"test:home\":\"residence\"}");
        var decision = SettlementCatalog.resolve(List.of(profile), "test:village", Set.of());
        assertEquals(SettlementProfile.Kind.DECORATION, decision.kind(house));
        assertEquals(SettlementProfile.Kind.RESIDENCE, decision.kind("test:home"));
        assertThrows(UnsupportedOperationException.class, () -> profile.templates().clear());
        assertThrows(UnsupportedOperationException.class, () -> decision.profiles().clear());
    }
}
