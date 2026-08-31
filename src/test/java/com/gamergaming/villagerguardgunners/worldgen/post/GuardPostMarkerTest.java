package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardPostMarkerTest {
    @Test
    void parserAcceptsOnlyTheClosedCanonicalGrammar() {
        Optional<GuardPostMarker> parsed = GuardPostMarker.parse(
                "guard_post:villagerguardgunners:watchtower");

        assertTrue(parsed.isPresent());
        assertEquals(GuardPostMarker.Action.GUARD_POST, parsed.orElseThrow().action());
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("villagerguardgunners", "watchtower"),
                parsed.orElseThrow().groupId());

        for (String invalid : new String[] {
                "",
                "guard_post:",
                "guard_post:watchtower",
                "guard_post:VillagerGuardGunners:watchtower",
                " guard_post:villagerguardgunners:watchtower",
                "guard_post:villagerguardgunners:watchtower ",
                "armory:villagerguardgunners:watchtower",
                "guard_post:villagerguardgunners:watchtower{Items:[]}",
                "guard_post:villagerguardgunners:watchtower run say hi"
        }) {
            assertTrue(GuardPostMarker.parse(invalid).isEmpty(), invalid);
        }
        assertTrue(GuardPostMarker.parse("guard_post:" + "a".repeat(129)).isEmpty());
        assertTrue(GuardPostMarker.parse(null).isEmpty());
    }
}
