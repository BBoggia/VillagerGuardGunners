package com.gamergaming.villagerguardgunners.quartermaster.trade;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Strict data-only trade schema. Native item validation happens after server assets load. */
public record TradeDefinition(boolean enabled,int level,List<Stack> inputs,Stack output,int maxUses,int xp,float multiplier) {
    public enum Kind { ITEM, AMMO, GUN }
    public record Stack(Kind type,ResourceLocation id,int count) {
        public Stack {
            Objects.requireNonNull(type);Objects.requireNonNull(id);
            if(id.toString().length()>96||count<1||count>64||type==Kind.GUN&&count!=1)throw new IllegalArgumentException("Invalid trade stack");
        }
        static Stack parse(JsonObject json) {
            keys(json,"type","id","count");
            Kind type=Kind.valueOf(string(json,"type").toUpperCase(Locale.ROOT));
            return new Stack(type,ResourceLocation.parse(string(json,"id")),integer(json,"count",1,64));
        }
    }
    public TradeDefinition {
        inputs=List.copyOf(inputs);
        if(enabled&&(level<1||level>5||inputs.isEmpty()||inputs.size()>2||output==null||maxUses<1||maxUses>64||xp<1||xp>30
                ||!Float.isFinite(multiplier)||multiplier<0||multiplier>1||inputs.stream().anyMatch(s->s.type()==Kind.GUN)))
            throw new IllegalArgumentException("Invalid trade definition");
    }
    public static TradeDefinition parse(JsonObject json) {
        keys(json,"version","enabled","level","inputs","output","max_uses","xp","price_multiplier");
        integer(json,"version",1,1);boolean enabled=true;
        if(json.has("enabled")) {
            if(!json.get("enabled").isJsonPrimitive()||!json.getAsJsonPrimitive("enabled").isBoolean())throw new IllegalArgumentException("enabled must be boolean");
            enabled=json.get("enabled").getAsBoolean();
        }
        if(!enabled){keys(json,"version","enabled");return new TradeDefinition(false,0,List.of(),null,0,0,0);}
        var array=json.getAsJsonArray("inputs");if(array==null||array.isEmpty()||array.size()>2)throw new IllegalArgumentException("Expected one or two inputs");
        List<Stack> inputs=new ArrayList<>();for(var value:array)inputs.add(Stack.parse(value.getAsJsonObject()));
        var multiplier=json.getAsJsonPrimitive("price_multiplier");
        if(multiplier==null||!multiplier.isNumber())throw new IllegalArgumentException("price_multiplier must be a number");
        return new TradeDefinition(true,integer(json,"level",1,5),inputs,Stack.parse(json.getAsJsonObject("output")),
                integer(json,"max_uses",1,64),integer(json,"xp",1,30),multiplier.getAsFloat());
    }
    private static String string(JsonObject json,String key) {
        var value=json.getAsJsonPrimitive(key);if(value==null||!value.isString()||value.getAsString().length()>96)throw new IllegalArgumentException("Invalid "+key);
        return value.getAsString();
    }
    private static int integer(JsonObject json,String key,int min,int max) {
        var value=json.getAsJsonPrimitive(key);if(value==null||!value.isNumber())throw new IllegalArgumentException("Invalid "+key);
        int number=value.getAsBigDecimal().intValueExact();if(number<min||number>max)throw new IllegalArgumentException("Out of range "+key);return number;
    }
    private static void keys(JsonObject json,String... keys) {
        if(json==null||!Set.of(keys).containsAll(json.keySet()))throw new IllegalArgumentException("Unknown trade fields");
    }
}
