package com.gamergaming.villagerguardgunners.quartermaster.service;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Bounded volatile notifications of successful transfers, never persistence or recovery authority. */
public final class ServiceHistory {
    public record Entry(UUID guard,long time,String item,int amount) {}
    private static final Map<ServerLevel,LinkedHashMap<UUID,ArrayDeque<Entry>>> LEVELS=new WeakHashMap<>();
    public static void completed(ServicePlan plan,int amount) {
        try {
            var level=(ServerLevel)plan.guard().level();var stations=LEVELS.computeIfAbsent(level,ignored->new LinkedHashMap<>());
            var events=stations.computeIfAbsent(plan.placement(),ignored->new ArrayDeque<>());
            String item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(plan.replacement().getItem()).toString();
            try {
                if(plan.replacement().getItem() instanceof com.tacz.guns.api.item.IGun gun)item=gun.getGunId(plan.replacement().copy()).toString();
                else if(plan.replacement().getItem() instanceof com.tacz.guns.api.item.IAmmo ammo)item=ammo.getAmmoId(plan.replacement().copy()).toString();
            }catch(RuntimeException unavailable){/* Keep the base item identity. */}
            events.addFirst(new Entry(plan.guard().getUUID(),level.getGameTime(),item,amount));
            while(events.size()>16)events.removeLast();while(stations.size()>128)stations.remove(stations.keySet().iterator().next());
        } catch(RuntimeException ignored) { /* Optional reporting must never change a completed physical transfer. */ }
    }
    public static List<Entry> recent(ServerLevel level,UUID placement) {
        var stations=LEVELS.get(level);return stations==null?List.of():List.copyOf(stations.getOrDefault(placement,new ArrayDeque<>()));
    }
    public static void stopped(ServerStoppedEvent event){LEVELS.keySet().removeIf(l->l.getServer()==event.getServer());}
    private ServiceHistory(){}
}
