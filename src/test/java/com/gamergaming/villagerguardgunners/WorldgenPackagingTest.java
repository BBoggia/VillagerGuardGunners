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
    @Test void armoryLootIncludesFallbackCalibersForAllProviders() throws Exception {
        try(var jar=new ZipFile(System.getProperty("villagerguardgunners.artifact"))) {
            for(String name:List.of("watchtower_armory","village_armory")) {
                var file=jar.getEntry("data/villagerguardgunners/loot_table/chests/"+name+".json");
                assertNotNull(file);
                try(var reader=new InputStreamReader(jar.getInputStream(file),StandardCharsets.UTF_8)) {
                    var pools=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("pools");
                    assertEquals(7,pools.size());
                    for(var pool:pools) {
                        var entry = pool.getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
                        assertEquals("villagerguardgunners:guard_ammo", entry.get("type").getAsString());
                        var choices = entry.getAsJsonArray("choices");
                        assertEquals(3, choices.size());
                        var mods = new java.util.HashSet<String>();
                        choices.forEach(id -> mods.add(id.getAsString().split(":")[0]));
                        assertEquals(java.util.Set.of("tacz", "cgm", "pointblank"), mods);
                        var count = entry.getAsJsonArray("functions").get(0).getAsJsonObject();
                        assertEquals("minecraft:set_count", count.get("function").getAsString());
                        assertEquals(32, count.get("count").getAsInt());
                    }
                }
            }
        }
    }
}
