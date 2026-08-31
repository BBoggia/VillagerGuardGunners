package com.gamergaming.villagerguardgunners.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class NaturalGuardLoadoutRoleFormatTest {
    @Test
    void formatFourAddsOnlyAnOptionalCanonicalRoleAssignment() {
        NaturalGuardLoadoutPool pool = NaturalGuardLoadoutPools.parsePool(
                id("roles"),
                JsonParser.parseString("""
                        {
                          "format": 4,
                          "entries": [
                            {"gun":"tacz:ak47","role":"test:rifleman","weight":2},
                            {"gun":"tacz:glock_17","weight":1}
                          ]
                        }
                        """));

        assertEquals(NaturalGuardLoadoutFormat.ROLE_VERSION, pool.format());
        assertEquals(id("rifleman"), pool.entries().get(0).roleId());
        assertNull(pool.entries().get(1).roleId());
    }

    @Test
    void oldFormatsRetainTheirMeaningAndRejectTheNewField() {
        assertThrows(JsonParseException.class, () -> NaturalGuardLoadoutPools.parsePool(
                id("legacy"),
                JsonParser.parseString("""
                        {
                          "format": 3,
                          "entries": [
                            {"gun":"tacz:ak47","role":"test:rifleman","weight":1}
                          ]
                        }
                        """)));
        assertThrows(JsonParseException.class, () -> NaturalGuardLoadoutPools.parsePool(
                id("noncanonical"),
                JsonParser.parseString("""
                        {
                          "format": 4,
                          "entries": [
                            {"gun":"tacz:ak47","role":"Rifleman","weight":1}
                          ]
                        }
                        """)));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
