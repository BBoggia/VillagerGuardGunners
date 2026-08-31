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

        GuardPostMarker lockedLoot = GuardPostMarker.parse(
                "armory:squad:villagerguardgunners:chests/watchtower_armory")
                .orElseThrow();
        assertEquals(GuardPostMarker.Action.ARMORY, lockedLoot.action());
        assertEquals(GuardPostMarker.ArmoryAccess.SQUAD_LOCKED, lockedLoot.armoryAccess());
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "villagerguardgunners", "chests/watchtower_armory"),
                lockedLoot.lootTableId());

        GuardPostMarker publicEmpty = GuardPostMarker.parse("armory:public:empty")
                .orElseThrow();
        assertEquals(GuardPostMarker.ArmoryAccess.PUBLIC, publicEmpty.armoryAccess());
        assertEquals(null, publicEmpty.lootTableId());

        for (String invalid : new String[] {
                "",
                "guard_post:",
                "guard_post:watchtower",
                "guard_post:VillagerGuardGunners:watchtower",
                " guard_post:villagerguardgunners:watchtower",
                "guard_post:villagerguardgunners:watchtower ",
                "armory:villagerguardgunners:watchtower",
                "armory:locked:empty",
                "armory:squad:",
                "armory:public:Watchtower:loot",
                "armory:public:minecraft:loot run say hi",
                "guard_post:villagerguardgunners:watchtower{Items:[]}",
                "guard_post:villagerguardgunners:watchtower run say hi"
        }) {
            assertTrue(GuardPostMarker.parse(invalid).isEmpty(), invalid);
        }
        assertTrue(GuardPostMarker.parse("guard_post:" + "a".repeat(129)).isEmpty());
        assertTrue(GuardPostMarker.parse(null).isEmpty());
    }
}
