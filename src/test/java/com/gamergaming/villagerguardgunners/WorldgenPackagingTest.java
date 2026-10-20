package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

final class WorldgenPackagingTest {
    @Test void generatedBuildingsArePresentInTheActualJar() throws Exception {
        try(var jar=new ZipFile(System.getProperty("villagerguardgunners.artifact"))) {
            var entries=jar.stream().filter(e -> !e.isDirectory()).map(e->e.getName()).toList();
            assertEquals(26,entries.stream().filter(n->n.startsWith("data/villagerguardgunners/structure/")&&n.endsWith(".nbt")).count());
            assertEquals(25,entries.stream().filter(n->n.startsWith("data/villagerguardgunners/village_buildings/")&&n.endsWith(".json")).count());
            assertTrue(entries.contains("data/villagerguardgunners/structure/watchtower.nbt"));
            assertFalse(entries.stream().anyMatch(n->n.matches("data/[^/]+/(structures|loot_tables|recipes)/.*")));
            assertFalse(entries.stream().anyMatch(n->n.contains("/qa/")||n.contains("vggpreview")));
        }
    }
    @Test void ammoLootUsesTheTaczCustomDataComponent() throws Exception {
        try(var jar=new ZipFile(System.getProperty("villagerguardgunners.artifact"))) {
            for(String name:List.of("watchtower_armory","village_armory")) {
                var file=jar.getEntry("data/villagerguardgunners/loot_table/chests/"+name+".json");
                assertNotNull(file);
                try(var reader=new InputStreamReader(jar.getInputStream(file),StandardCharsets.UTF_8)) {
                    var pools=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("pools");
                    assertEquals(7,pools.size());
                    for(var pool:pools) {
                        var functions=pool.getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("functions");
                        assertEquals("minecraft:set_custom_data",functions.get(0).getAsJsonObject().get("function").getAsString());
                        assertTrue(functions.get(0).getAsJsonObject().get("tag").getAsString().contains("AmmoId"));
                        assertEquals(32,functions.get(1).getAsJsonObject().get("count").getAsInt());
                    }
                }
            }
        }
    }
}
