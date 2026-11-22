package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import java.util.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/** Incremental, copied assessment. Stock stays fixed for a scan; changes discard the estimate. */
public final class ServiceSurvey {
    private final QuartermasterBlockEntity station;
    private final Player viewer;
    private final ServiceAssessment.Captured base;
    private final Map<String,ServiceNeeds.Variant> variants=new LinkedHashMap<>();
    private final Map<String,Set<String>> ammo=new HashMap<>();
    private final List<ServiceNeeds.GuardInput> guards=new ArrayList<>();
    private final Map<UUID,ServiceNeeds.Transfer> incoming=new HashMap<>();
    private final List<ServicePlan> deliveries=new ArrayList<>();
    private final List<Long> visibleSources;
    public ServiceSurvey(Player viewer,QuartermasterBlockEntity station) {
        this.viewer=viewer;this.station=station;
        var sources=visibleSources(viewer,station);
        visibleSources=sources.stream().map(s->s.block().getBlockPos().asLong()).toList();
        base=ServiceAssessment.capture(station,List.of(),Map.of(),sources);
        variants.putAll(base.input.variants());
    }
    /** Linked Armories have public inventories, but retain their ordinary eight-block viewing range. */
    public static List<ServiceSource> visibleSources(Player viewer,QuartermasterBlockEntity station) {
        return QuartermasterServiceRuntime.sources(station).stream().filter(s->s.block()==station
                ||s.block() instanceof ArmoryBlockEntity a&&a.serviceInventoryAccessible()
                &&viewer.distanceToSqr(a.getBlockPos().getCenter())<=64).toList();
    }
    private boolean same(ServiceAssessment.Captured other) {
        if(base.profileRevision!=other.profileRevision||!base.input.sources().equals(other.input.sources())
                ||!base.input.returns().equals(other.input.returns())||other.input.guards().isEmpty()&&base.restrictedSources!=other.restrictedSources)return false;
        for(var e:base.input.variants().entrySet())if(!e.getValue().equals(other.input.variants().get(e.getKey())))return false;
        for(var e:base.variants.entrySet())if(!ItemStack.matches(e.getValue(),other.variants.getOrDefault(e.getKey(),ItemStack.EMPTY)))return false;
        return true;
    }
    public boolean current() {
        return visibleSources.equals(visibleSources(viewer,station).stream().map(s->s.block().getBlockPos().asLong()).toList())
                &&deliveries.stream().allMatch(ServiceTransactions::incoming)
                &&same(ServiceAssessment.capture(station,List.of(),Map.of(),visibleSources(viewer,station)));
    }
    public boolean add(Guard guard) {
        return add(guard,GuardEquipmentServiceAccess.get(guard).settings());
    }
    public boolean add(Guard guard,ServiceSettings requested) {
        var captured=ServiceAssessment.capture(station,List.of(guard),Map.of(guard.getUUID(),requested),visibleSources(viewer,station),true);
        if(!same(captured))return false;
        var input=captured.input;var g=input.guards().get(0);
        java.util.function.Function<String,String> key=k->k==null?null:base.input.variants().containsKey(k)?k:guard.getUUID()+"/"+k;
        java.util.function.Function<Set<String>,Set<String>> keys=set->{Set<String> result=new HashSet<>();set.forEach(k->result.add(key.apply(k)));return result;};
        for(var v:input.variants().values())variants.put(key.apply(v.key()),new ServiceNeeds.Variant(key.apply(v.key()),v.itemId(),v.gunId(),v.ammoId(),v.kind(),v.definitionKnown(),v.physicalReserve()));
        Map<String,List<Integer>> capacities=new HashMap<>();g.capacities().forEach((k,v)->capacities.put(key.apply(k),v));
        guards.add(new ServiceNeeds.GuardInput(g.id(),g.inventory().stream().map(s->new ServiceNeeds.Stack(key.apply(s.variant()),s.count())).toList(),
                g.equipment().stream().map(r->new ServiceNeeds.Rule(r.configured(),keys.apply(r.variants()))).toList(),g.manual(),g.profileMissing(),g.readable(),g.replace(),
                g.ammoReserve(),g.medicalReserve(),keys.apply(g.medicines()),g.medicalConfigured(),keys.apply(g.returnable()),capacities,g.sources(),g.restrictedSources()));
        var pending=input.incoming().get(guard.getUUID());
        if(pending!=null)incoming.put(guard.getUUID(),new ServiceNeeds.Transfer(pending.guard(),pending.source(),pending.sourceSlot(),pending.destination(),pending.returnSlot(),key.apply(pending.variant()),pending.amount()));
        deliveries.addAll(captured.incoming.values());
        input.compatibleAmmo().forEach((k,v)->ammo.computeIfAbsent(key.apply(k),ignored->new HashSet<>()).addAll(keys.apply(v)));
        return true;
    }
    public ServiceNeeds.Result finish() {
        return ServiceNeeds.evaluate(new ServiceNeeds.Input(variants,base.input.sources(),base.input.returns(),guards,ammo,incoming));
    }
    public record Stock(ItemStack item,ServiceNeeds.Variant variant,int count,int reserved) {}
    public List<Stock> stock() {
        Map<String,int[]> totals=new LinkedHashMap<>();
        for(var s:base.input.sources())if(!s.stack().emptyStack()) {
            var n=totals.computeIfAbsent(s.stack().variant(),ignored->new int[2]);n[0]+=s.stack().count();if(s.reserved())n[1]+=s.stack().count();
        }
        return totals.entrySet().stream().map(e->new Stock(base.variants.get(e.getKey()).copy(),base.input.variants().get(e.getKey()),e.getValue()[0],e.getValue()[1])).toList();
    }
    public List<Long> visibleSources(){return visibleSources;}
    public List<ServicePlan> deliveries(){return List.copyOf(deliveries);}
    public boolean restricted(){return base.restrictedSources;}
    public int returns(){return base.input.returns().size();}
}
