package com.gamergaming.villagerguardgunners;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

final class SupplyMedicalPackagingTest {
    @Test void installedRecipesUseComponentEraResultStacks() throws Exception {
        try(var jar=new ZipFile(System.getProperty("villagerguardgunners.artifact"))) {
            for(String name:List.of("armory","quartermaster","bandage","recovery_cot")) {
                String path="data/villagerguardgunners/recipe/"+name+".json";
                var entry=jar.getEntry(path); assertNotNull(entry,path);
                try(var reader=new java.io.InputStreamReader(jar.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)) {
                    var result=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("result");
                    assertEquals("villagerguardgunners:"+name,result.get("id").getAsString());
                    assertFalse(result.has("item"),path);
                }
                assertNull(jar.getEntry("data/villagerguardgunners/recipes/"+name+".json"));
            }
        }
    }
    @Test void blockDropsAndToolTagsUseInstalledDataDirectories() throws Exception {
        String entries=PackagedArtifact.entries();
        for(String name:List.of("armory","quartermaster","recovery_cot")) {
            assertTrue(entries.contains("data/villagerguardgunners/loot_table/blocks/"+name+".json"));
            assertFalse(entries.contains("data/villagerguardgunners/loot_tables/blocks/"+name+".json"));
        }
        assertTrue(entries.contains("data/minecraft/tags/block/mineable/axe.json"));
        assertFalse(entries.contains("data/minecraft/tags/blocks/mineable/axe.json"));
        assertFalse(entries.contains("/qa/"));
        assertFalse(entries.contains("shotqa/"));
        assertFalse(entries.contains("shotconflict/"));
        assertFalse(entries.contains("vgg-shot-conflict.mixins.json"));
    }
}
