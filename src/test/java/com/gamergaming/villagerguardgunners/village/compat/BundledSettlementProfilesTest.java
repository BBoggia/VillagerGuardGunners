package com.gamergaming.villagerguardgunners.village.compat;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BundledSettlementProfilesTest {
    @Test void everyBundledProfileValidatesAllVariantsAndKeepsExplicitBoundaries() throws Exception {
        var root=Path.of("src/main/resources/data/villagerguardgunners/village_compatibility");
        List<SettlementProfile> profiles=new ArrayList<>();
        try(var paths=Files.list(root)) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                Map<String,String> installed=new HashMap<>();
                json.getAsJsonObject("mods").entrySet().forEach(e->installed.put(e.getKey(),e.getValue().getAsString().equals("[0,)")?"4.0":e.getValue().getAsString().replace("[","").replace("]","")));
                var profile=SettlementProfile.parse("test:"+path.getFileName().toString().replace(".json",""),"bundled",json,installed,
                        id->new PieceMeaning.ResourceEvidence("template-pack",json.getAsJsonObject("templates").getAsJsonArray(id).get(0).getAsJsonObject().get("sha256").getAsString())).profile();
                assertNotNull(profile,path.toString());profiles.add(profile);
            }
        }
        for(var family:List.of("small","medium","large")) {
            var decision=SettlementCatalog.resolve(profiles,"ctov:"+family+"/village_plains",Set.of("ctov:"+family+"_village"));
            assertEquals(family,decision.sizeFamily());assertTrue(decision.friendly());
            assertTrue(decision.meanings().values().stream().anyMatch(m->m.kind()==SettlementProfile.Kind.DEFENSE));
        }
        assertFalse(SettlementCatalog.resolve(profiles,"villagesandpillages:village_witch",Set.of("minecraft:village")).friendly());
        var catalog=new SettlementCatalog(1,profiles,Map.of(),Map.of());
        for(var pool:List.of("center","decoration","entity/cat","entity/villager","entity/witch","road_and_house","terminators")) {
            assertFalse(catalog.allowsPool("villagesandpillages:village_witch/"+pool), pool);
        }
        assertTrue(catalog.allowsPool("minecraft:village/plains/houses"));
        assertTrue(catalog.allowsPool("skyvillages:skyvillage_houses"));
        assertTrue(SettlementCatalog.resolve(profiles,"joshie:village_ocean",Set.of()).traversalLimits().contains("water"));
        assertTrue(SettlementCatalog.resolve(profiles,"skyvillages:skyvillage",Set.of()).traversalLimits().contains("elevation"));
        for(var variant:List.of("jungle","dark_forest","swamp")) assertTrue(SettlementCatalog.resolve(profiles,"mmv:village_"+variant,Set.of()).friendly());
    }
}
