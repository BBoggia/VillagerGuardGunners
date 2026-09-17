package com.gamergaming.villagerguardgunners.quartermaster.trade;

import com.google.gson.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TradeDefinitionTest {
    private JsonObject valid() {return JsonParser.parseString("""
        {"version":1,"level":2,"inputs":[{"type":"item","id":"minecraft:emerald","count":4}],
         "output":{"type":"ammo","id":"tacz:9mm","count":16},"max_uses":8,"xp":5,"price_multiplier":0.05}
        """).getAsJsonObject();}
    @Test void validTypedSupplyAndTwoInputs() {
        var json=valid();var trade=TradeDefinition.parse(json);assertEquals(16,trade.output().count());assertEquals(TradeDefinition.Kind.AMMO,trade.output().type());
        json.getAsJsonArray("inputs").add(JsonParser.parseString("{\"type\":\"item\",\"id\":\"minecraft:iron_ingot\",\"count\":2}"));
        assertEquals(2,TradeDefinition.parse(json).inputs().size());
    }
    @Test void boundsRejectNegativeFractionalAndOversizedValues() {
        for(String key:List.of("version","level","max_uses","xp"))for(Number n:List.of(-1,0,999,1.5)) {
            var json=valid();json.addProperty(key,n);assertThrows(RuntimeException.class,()->TradeDefinition.parse(json),key+"="+n);
        }
        for(String key:List.of("version","level","max_uses","xp","price_multiplier")) {
            var json=valid();json.addProperty(key,"1");assertThrows(RuntimeException.class,()->TradeDefinition.parse(json));
        }
        var json=valid();json.addProperty("price_multiplier",-0.1);assertThrows(RuntimeException.class,()->TradeDefinition.parse(json));
        json.addProperty("price_multiplier",1.1);assertThrows(RuntimeException.class,()->TradeDefinition.parse(json));
    }
    @Test void rejectsUnknownFieldsArbitraryNbtAndInvalidIds() {
        var json=valid();json.addProperty("command","give @p diamond");assertThrows(RuntimeException.class,()->TradeDefinition.parse(json));
        var nbt=valid();nbt.getAsJsonObject("output").addProperty("nbt","{}");assertThrows(RuntimeException.class,()->TradeDefinition.parse(nbt));
        for(String id:List.of("NOT:VALID","test:"+"a".repeat(97))) {var x=valid();x.getAsJsonObject("output").addProperty("id",id);assertThrows(RuntimeException.class,()->TradeDefinition.parse(x));}
    }
    @Test void gunsAreSingleUnloadedOutputsNeverInputs() {
        var json=valid();json.getAsJsonObject("output").addProperty("type","gun");json.getAsJsonObject("output").addProperty("count",1);
        assertEquals(TradeDefinition.Kind.GUN,TradeDefinition.parse(json).output().type());
        json.getAsJsonObject("output").addProperty("count",2);assertThrows(RuntimeException.class,()->TradeDefinition.parse(json));
        var input=valid();input.getAsJsonArray("inputs").get(0).getAsJsonObject().addProperty("type","gun");input.getAsJsonArray("inputs").get(0).getAsJsonObject().addProperty("count",1);
        assertThrows(RuntimeException.class,()->TradeDefinition.parse(input));
    }
    @Test void disablingIsExplicitAndMinimal() {
        var disabled=JsonParser.parseString("{\"version\":1,\"enabled\":false}").getAsJsonObject();assertFalse(TradeDefinition.parse(disabled).enabled());
        disabled.addProperty("level",1);assertThrows(RuntimeException.class,()->TradeDefinition.parse(disabled));
        var invalid=valid();invalid.addProperty("enabled","false");assertThrows(RuntimeException.class,()->TradeDefinition.parse(invalid));
    }
    @Test void inputAndStackCountsAreBounded() {
        var json=valid();json.add("inputs",new JsonArray());assertThrows(RuntimeException.class,()->TradeDefinition.parse(json));
        var many=valid();many.getAsJsonArray("inputs").add(many.getAsJsonArray("inputs").get(0).deepCopy());many.getAsJsonArray("inputs").add(many.getAsJsonArray("inputs").get(0).deepCopy());
        assertThrows(RuntimeException.class,()->TradeDefinition.parse(many));
        for(int count:new int[]{0,-1,65}){var x=valid();x.getAsJsonObject("output").addProperty("count",count);assertThrows(RuntimeException.class,()->TradeDefinition.parse(x));}
    }
    @Test void defaultsCoverAllLevelsWithoutAnEmeraldBuybackCycle() throws Exception {
        var levels=new int[6];int guns=0;
        try(var files=Files.list(Path.of("src/main/resources/data/villagerguardgunners/quartermaster_trades"))) {
            for(var path:files.toList()) {
                var d=TradeDefinition.parse(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
                assertTrue(d.enabled());assertEquals(1,d.inputs().size());assertEquals("minecraft:emerald",d.inputs().get(0).id().toString());
                assertNotEquals("minecraft:emerald",d.output().id().toString());
                if(d.output().type()==TradeDefinition.Kind.GUN)guns++;else levels[d.level()]++;
            }
        }
        assertEquals(2,guns);for(int level=1;level<=5;level++)assertEquals(2,levels[level]);
    }
    @Test void distributedExampleUsesSupportedFormatAndBoundedTwoInputAmmoTrade() throws Exception {
        var root=Path.of("examples/quartermaster-trades");
        assertEquals(15,JsonParser.parseString(Files.readString(root.resolve("pack.mcmeta"))).getAsJsonObject().getAsJsonObject("pack").get("pack_format").getAsInt());
        var trade=TradeDefinition.parse(JsonParser.parseString(Files.readString(root.resolve("data/vgg_example/quartermaster_trades/rifle_ammo.json"))).getAsJsonObject());
        assertTrue(trade.enabled());assertEquals(3,trade.level());assertEquals(2,trade.inputs().size());
        assertEquals(TradeDefinition.Kind.AMMO,trade.output().type());assertEquals("tacz:762x39",trade.output().id().toString());
        assertEquals(30,trade.output().count());assertEquals(4,trade.maxUses());
    }
}
