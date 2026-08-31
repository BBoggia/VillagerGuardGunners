package com.gamergaming.villagerguardgunners.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.RangeOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.TacticalOrder;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.ToggleOrder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardRolesTest {
    @Test
    void inheritanceResolvesOnceAndChildDefaultsOverrideParents() {
        Map<ResourceLocation, GuardRoleDefinition> roles = GuardRoles.compile(Map.of(
                id("base"), json("""
                        {"format":1,"orders":{"ammo_sharing":"enabled","movement":"tactical"}}
                        """),
                id("marksman"), json("""
                        {"format":1,"inherits":["test:base"],"orders":{
                          "tactics":"marksman","movement":"hold_position","maximum_range":"blocks_64"
                        }}
                        """)));

        var defaults = roles.get(id("marksman")).effectiveDefaults();
        assertEquals(TacticalOrder.MARKSMAN, defaults.tactics());
        assertEquals(ToggleOrder.ENABLED, defaults.ammoSharing());
        assertEquals(MovementOrder.HOLD_POSITION, defaults.movement());
        assertEquals(RangeOrder.BLOCKS_64, defaults.maximumRange());
    }

    @Test
    void cyclesMissingParentsAndConflictingParentsRejectTheWholeGraph() {
        assertThrows(JsonParseException.class, () -> GuardRoles.compile(Map.of(
                id("a"), json("""
                        {"format":1,"inherits":["test:b"],"orders":{}}
                        """),
                id("b"), json("""
                        {"format":1,"inherits":["test:a"],"orders":{}}
                        """))));
        assertThrows(JsonParseException.class, () -> GuardRoles.compile(Map.of(
                id("missing"), json("""
                        {"format":1,"inherits":["test:not_present"],"orders":{}}
                        """))));
        assertThrows(JsonParseException.class, () -> GuardRoles.compile(Map.of(
                id("left"), json("""
                        {"format":1,"orders":{"tactics":"support"}}
                        """),
                id("right"), json("""
                        {"format":1,"orders":{"tactics":"marksman"}}
                        """),
                id("child"), json("""
                        {"format":1,"inherits":["test:left","test:right"],"orders":{}}
                        """))));
    }

    @Test
    void strictSyntaxFutureVersionsAndBoundsFailClosed() {
        assertThrows(JsonParseException.class, () -> GuardRoles.compile(Map.of(
                id("future"), json("""
                        {"format":2,"orders":{}}
                        """))));
        assertThrows(JsonParseException.class, () -> GuardRoles.compile(Map.of(
                id("unknown"), json("""
                        {"format":1,"orders":{},"surprise":true}
                        """))));

        Map<ResourceLocation, JsonElement> tooMany = new LinkedHashMap<>();
        for (int index = 0; index <= GuardRoles.MAXIMUM_ROLES; index++) {
            tooMany.put(id("role_" + index), json("""
                    {"format":1,"orders":{}}
                    """));
        }
        assertThrows(JsonParseException.class, () -> GuardRoles.compile(tooMany));
    }

    @Test
    void failedPublicationRetainsLastKnownGoodDefinitionsAndGeneration() {
        GuardRoles registry = new GuardRoles();
        registry.publish(new GuardRoles.PreparedResources(
                Map.of(id("good"), json("""
                        {"format":1,"orders":{"tactics":"support"}}
                        """)),
                List.of()));
        GuardRoleReloadSnapshot accepted = registry.currentSnapshot();

        registry.publish(new GuardRoles.PreparedResources(
                Map.of(id("future"), json("""
                        {"format":99,"orders":{}}
                        """)),
                List.of()));
        GuardRoleReloadSnapshot rejected = registry.currentSnapshot();

        assertTrue(accepted.lastReloadAccepted());
        assertFalse(rejected.lastReloadAccepted());
        assertEquals(accepted.generation(), rejected.generation());
        assertEquals(accepted.roles(), rejected.roles());
        assertTrue(rejected.roles().containsKey(id("good")));
        assertFalse(rejected.lastErrors().isEmpty());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static JsonElement json(String source) {
        return JsonParser.parseString(source);
    }
}
