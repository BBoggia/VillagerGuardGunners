package com.gamergaming.villagerguardgunners.quartermaster.trade;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.resources.*;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.*;

/** Publishes complete validated generations; optional native IDs are resolved later. */
public final class TradeDefinitions extends SimplePreparableReloadListener<TradeDefinitions.Prepared> {
    private static final TradeDefinitions INSTANCE=new TradeDefinitions();
    private static Map<ResourceLocation,TradeDefinition> definitions=Map.of();
    private static boolean ready;
    public record Prepared(Map<ResourceLocation,TradeDefinition> entries,String error) {}
    public static Map<ResourceLocation,TradeDefinition> snapshot(){return definitions;}
    public static boolean ready(){return ready;}
    public static void onReload(AddReloadListenerEvent event){event.addListener(INSTANCE);}
    public static void started(ServerStartedEvent event){ready=true;QuartermasterTrades.clearWarnings();}
    public static void stopped(ServerStoppedEvent event){ready=false;definitions=Map.of();QuartermasterTrades.clearWarnings();}
    @Override protected Prepared prepare(ResourceManager manager,ProfilerFiller profiler) {
        var converter=FileToIdConverter.json("quartermaster_trades");
        try {
            var resources=converter.listMatchingResources(manager);
            if(resources.size()>128)throw new IllegalArgumentException("Maximum 128 trade definitions");
            Map<ResourceLocation,TradeDefinition> next=new TreeMap<>();int[] levels=new int[6];
            for(var entry:resources.entrySet()) {
                var id=converter.fileToId(entry.getKey());if(id.toString().length()>96)throw new IllegalArgumentException("Trade ID too long");
                try(var reader=entry.getValue().openAsReader()) {
                    StringBuilder source=new StringBuilder();char[] chars=new char[2048];int count;
                    while((count=reader.read(chars))!=-1){source.append(chars,0,count);if(source.length()>16384)throw new IllegalArgumentException("Trade JSON exceeds 16 KiB");}
                    var definition=TradeDefinition.parse(JsonParser.parseString(source.toString()).getAsJsonObject());
                    if(definition.enabled()&&++levels[definition.level()]>16)throw new IllegalArgumentException("Maximum 16 trades per level");
                    next.put(id,definition);
                } catch(Exception ex){throw new IllegalArgumentException(id+": "+ex.getMessage(),ex);}
            }
            return new Prepared(Map.copyOf(next),null);
        } catch(Exception ex){return new Prepared(Map.of(),ex.toString());}
    }
    @Override protected void apply(Prepared prepared,ResourceManager manager,ProfilerFiller profiler) {
        if(prepared.error()!=null){LogUtils.getLogger().warn("Quartermaster trade reload rejected; retaining previous definitions: {}",prepared.error());return;}
        definitions=Map.copyOf(prepared.entries());QuartermasterTrades.clearWarnings();
    }
}
