package com.gamergaming.villagerguardgunners.quartermaster.service;

import java.util.*;

/** Pure allocation over immutable facts. No Minecraft entities, handlers, NBT or callbacks. */
public final class ServiceNeeds {
    public static final int MAX_GUARDS = 64, SLOTS = 12;
    public enum Kind { GUN, LOOSE_AMMO, AMMO_BOX, MEDICAL, OTHER }
    public enum Reason { ACCEPTED, PROTECTED, MISSING_STOCK, PARTIAL, RETURN_FULL, DESTINATION_FULL,
        INCOMPATIBLE_STOCK, DEFINITION_UNAVAILABLE, CONDITIONAL, NOT_CONFIGURED, MANUAL,
        PROFILE_MISSING, UNAVAILABLE, SOURCE_RESTRICTED, RESERVE_NOT_PHYSICAL }
    public record Variant(String key, String itemId, String gunId, String ammoId, Kind kind,
            boolean definitionKnown, boolean physicalReserve) {}
    public record Stack(String variant, int count) {
        public Stack { if (count < 0 || (count > 0 && variant == null)) throw new IllegalArgumentException("Invalid stack"); }
        public static Stack empty() { return new Stack(null, 0); }
        public boolean emptyStack() { return count == 0; }
    }
    public record Source(String key, int slot, Stack stack, boolean station, boolean reserved) {}
    public record Rule(boolean configured, Set<String> variants) {
        public Rule { variants = Set.copyOf(variants); }
    }
    public record GuardInput(UUID id, List<Stack> inventory, List<Rule> equipment, boolean manual,
            boolean profileMissing, boolean readable, boolean replace, int ammoReserve, int medicalReserve,
            Set<String> medicines, boolean medicalConfigured, Set<String> returnable,
            Map<String, List<Integer>> capacities, Set<String> sources, boolean restrictedSources) {
        public GuardInput {
            inventory = List.copyOf(inventory); equipment = List.copyOf(equipment);
            medicines = Set.copyOf(medicines); returnable = Set.copyOf(returnable); sources = Set.copyOf(sources);
            Map<String,List<Integer>> copy = new HashMap<>(); capacities.forEach((k,v)->copy.put(k,List.copyOf(v))); capacities = Map.copyOf(copy);
            if (inventory.size()!=SLOTS || equipment.size()!=6 || ammoReserve<0 || ammoReserve>512
                    || medicalReserve<0 || medicalReserve>192 || capacities.values().stream().anyMatch(v->v.size()!=SLOTS))
                throw new IllegalArgumentException("Invalid assessment bounds");
        }
        int capacity(String variant, int slot) { var limits=capacities.get(variant); return limits==null?0:limits.get(slot); }
    }
    public record Input(Map<String,Variant> variants, List<Source> sources, List<Integer> returns,
            List<GuardInput> guards, Map<String,Set<String>> compatibleAmmo, Map<UUID,Transfer> incoming) {
        public Input(Map<String,Variant> variants,List<Source> sources,List<Integer> returns,List<GuardInput> guards,Map<String,Set<String>> compatibleAmmo) {
            this(variants,sources,returns,guards,compatibleAmmo,Map.of());
        }
        public Input {
            incoming = Map.copyOf(incoming);
            variants = Map.copyOf(variants); sources = List.copyOf(sources); returns = List.copyOf(returns); guards = List.copyOf(guards);
            Map<String,Set<String>> copy=new HashMap<>(); compatibleAmmo.forEach((k,v)->copy.put(k,Set.copyOf(v))); compatibleAmmo=Map.copyOf(copy);
            if (guards.size()>MAX_GUARDS || sources.size()>63 || returns.size()>9 || variants.size()>MAX_GUARDS*SLOTS+63)
                throw new IllegalArgumentException("Assessment too large");
            if (new HashSet<>(guards.stream().map(GuardInput::id).toList()).size()!=guards.size()) throw new IllegalArgumentException("Duplicate guard");
            if (new HashSet<>(returns).size()!=returns.size() || returns.stream().anyMatch(i->i<27||i>35)) throw new IllegalArgumentException("Invalid return slots");
            if(incoming.size()>guards.size())throw new IllegalArgumentException("Too many incoming deliveries");
            Set<String> claims=new HashSet<>();
            for(var e:incoming.entrySet())if(!e.getKey().equals(e.getValue().guard())
                    ||guards.stream().noneMatch(g->g.id().equals(e.getKey()))
                    ||!claims.add(e.getValue().source()+"/"+e.getValue().sourceSlot()))throw new IllegalArgumentException("Duplicate or foreign incoming delivery");
            Set<String> physical = new HashSet<>();
            for (var source:sources) if (!physical.add(source.key()+"/"+source.slot())) throw new IllegalArgumentException("Duplicate physical stock");
        }
    }
    public record Row(String category, int slot, String item, int requested, int available, int incoming, Set<Reason> reasons) {
        public Row(String category,int slot,String item,int requested,int available,Set<Reason> reasons) {
            this(category,slot,item,requested,available,0,reasons);
        }
        public Row { reasons = Set.copyOf(reasons); }
        public int missing() { return requested-available-incoming; }
    }
    /** An internal allocation reference, not a reservation or client-authorized transfer. */
    public record Transfer(UUID guard, String source, int sourceSlot, int destination, int returnSlot, String variant, int amount) {}
    public record GuardResult(UUID id, List<Row> rows, List<Transfer> transfers) {
        public GuardResult { rows=List.copyOf(rows); transfers=List.copyOf(transfers); }
    }
    public record Result(List<GuardResult> guards) { public Result { guards=List.copyOf(guards); } }

    public static Result evaluate(Input input) {
        int[] remaining = input.sources().stream().mapToInt(s->s.reserved()?0:s.stack().count()).toArray();
        var returns = new ArrayDeque<>(input.returns()); var results = new ArrayList<GuardResult>();
        for (var guard : input.guards().stream().sorted(Comparator.comparing(GuardInput::id)).toList()) {
            var rows=new ArrayList<Row>(); var transfers=new ArrayList<Transfer>();
            if (!guard.readable() || guard.manual() || guard.profileMissing()) {
                var reason=!guard.readable()?Reason.UNAVAILABLE:guard.manual()?Reason.MANUAL:Reason.PROFILE_MISSING;
                rows.add(row("guard",-1,"",0,0,reason)); results.add(new GuardResult(guard.id(),rows,transfers)); continue;
            }
            var inventory = new ArrayList<>(guard.inventory());
            var pending=validIncoming(input,guard);
            boolean[] conditional = new boolean[2];
            for(int target=0;target<6;target++) {
                var rule=guard.equipment().get(target); var old=inventory.get(target);
                if(!rule.configured()) continue;
                if (!old.emptyStack() && rule.variants().contains(old.variant())) {
                    rows.add(row("equipment",target,label(input,old.variant()),0,0,Reason.ACCEPTED)); continue;
                }
                if(!old.emptyStack() && (!guard.replace() || old.count()!=1 || !guard.returnable().contains(old.variant()))) {
                    rows.add(row("equipment",target,label(input,old.variant()),0,0,Reason.PROTECTED)); continue;
                }
                if(pending!=null&&pending.destination()==target&&rule.variants().contains(pending.variant())) {
                    inventory.set(target,new Stack(pending.variant(),1));
                    rows.add(new Row("equipment",target,label(input,pending.variant()),1,0,1,Set.of(Reason.ACCEPTED)));
                    continue;
                }
                if(!old.emptyStack() && returns.isEmpty()) {
                    rows.add(row("equipment",target,"",1,0,Reason.RETURN_FULL)); if(target<2)conditional[target]=true; continue;
                }
                int selected=-1; boolean incompatible=false, unavailable=false;
                for(int i=0;i<input.sources().size();i++) {
                    var source=input.sources().get(i);
                    if(!source.station() || !guard.sources().contains(source.key()) || remaining[i]<=0 || !rule.variants().contains(source.stack().variant())) continue;
                    if(guard.capacity(source.stack().variant(),target)<1 || !input.variants().get(source.stack().variant()).definitionKnown()) {
                        incompatible=true; unavailable|=!input.variants().get(source.stack().variant()).definitionKnown(); continue;
                    }
                    selected=i; break;
                }
                if(selected<0) {
                    rows.add(row("equipment",target,"",1,0,unavailable?Reason.DEFINITION_UNAVAILABLE:incompatible?Reason.INCOMPATIBLE_STOCK:Reason.MISSING_STOCK));
                    if(target<2)conditional[target]=true;
                } else {
                    var source=input.sources().get(selected); int returned=old.emptyStack()?-1:returns.removeFirst();
                    remaining[selected]--; inventory.set(target,new Stack(source.stack().variant(),1));
                    transfers.add(new Transfer(guard.id(),source.key(),source.slot(),target,returned,source.stack().variant(),1));
                    rows.add(row("equipment",target,label(input,source.stack().variant()),1,1,Reason.ACCEPTED));
                }
            }
            // One reserve per distinct physical ammo type, even when both weapons share it.
            var ammoTypes=new LinkedHashMap<String,Set<String>>();
            for(int weapon=0;weapon<2;weapon++) {
                if(conditional[weapon]) rows.add(row("ammo",weapon,"",0,0,Reason.CONDITIONAL));
                var stack=inventory.get(weapon); if(stack.emptyStack()) continue;
                var fact=input.variants().get(stack.variant());
                if(fact.kind()!=Kind.GUN) continue;
                if(!fact.definitionKnown()) {
                    rows.add(row("ammo",weapon,label(input,stack.variant()),0,0,Reason.DEFINITION_UNAVAILABLE)); continue;
                }
                if(!fact.physicalReserve()) {
                    rows.add(row("ammo",weapon,label(input,stack.variant()),0,0,Reason.RESERVE_NOT_PHYSICAL)); continue;
                }
                if(fact.ammoId()==null) {
                    rows.add(row("ammo",weapon,label(input,stack.variant()),0,0,Reason.DEFINITION_UNAVAILABLE)); continue;
                }
                var compatible=input.compatibleAmmo().getOrDefault(stack.variant(),Set.of());
                // Shared reserves must work in both guns, including native tag-sensitive callbacks.
                ammoTypes.merge(fact.ammoId(),new HashSet<>(compatible),(a,b)->{a.retainAll(b);return a;});
            }
            for(var ammo:ammoTypes.entrySet()) {
                if(topUp(input,guard,inventory,remaining,rows,transfers,6,9,guard.ammoReserve(),ammo.getValue(),"ammo",ammo.getKey(),false,pending)>0)pending=null;
            }
            if(guard.medicalConfigured()) topUp(input,guard,inventory,remaining,rows,transfers,9,12,
                    guard.medicalReserve(),guard.medicines(),"medical","",true,pending);
            else rows.add(row("medical",-1,"",0,0,Reason.NOT_CONFIGURED));
            if(guard.restrictedSources()) rows.add(row("sources",-1,"",0,0,Reason.SOURCE_RESTRICTED));
            results.add(new GuardResult(guard.id(),rows,transfers));
        }
        return new Result(results);
    }
    private static int topUp(Input input,GuardInput guard,List<Stack> inventory,int[] remaining,List<Row> rows,
            List<Transfer> transfers,int first,int end,int reserve,Set<String> variants,String category,String item,boolean stationOnly,Transfer pending) {
        int existing=0; for(int slot=first;slot<end;slot++) if(!inventory.get(slot).emptyStack() && variants.contains(inventory.get(slot).variant())) existing+=inventory.get(slot).count();
        int needed=Math.max(0,reserve-existing), supplied=0, incoming=0;
        if(pending!=null&&pending.destination()>=first&&pending.destination()<end&&variants.contains(pending.variant())&&pending.amount()<=needed) {
            incoming=pending.amount();var old=inventory.get(pending.destination());
            inventory.set(pending.destination(),new Stack(pending.variant(),old.count()+incoming));
        }
        for(int slot=first;slot<end && supplied+incoming<needed;slot++) {
            if(pending!=null&&pending.destination()==slot&&!variants.contains(pending.variant()))continue;
            for(int i=0;i<input.sources().size() && supplied+incoming<needed;i++) {
                var source=input.sources().get(i); String variant=source.stack().variant();
                if(remaining[i]<=0 || !guard.sources().contains(source.key()) || stationOnly&&!source.station() || !variants.contains(variant)) continue;
                var old=inventory.get(slot);
                if(!old.emptyStack()&&!Objects.equals(old.variant(),variant)) continue;
                int amount=Math.min(needed-supplied-incoming,Math.min(remaining[i],guard.capacity(variant,slot)-old.count()));
                if(amount<=0) continue;
                remaining[i]-=amount; supplied+=amount; inventory.set(slot,new Stack(variant,old.count()+amount));
                transfers.add(new Transfer(guard.id(),source.key(),source.slot(),slot,-1,variant,amount));
            }
        }
        var reasons=EnumSet.noneOf(Reason.class);
        if(supplied+incoming==needed) reasons.add(Reason.ACCEPTED);
        else {
            if(supplied+incoming>0) reasons.add(Reason.PARTIAL);
            boolean anyRoom=false;
            for(int slot=first;slot<end;slot++) {
                if(pending!=null&&pending.destination()==slot&&!variants.contains(pending.variant()))continue;
                var stack=inventory.get(slot);
                if(stack.emptyStack()) { anyRoom=true; break; }
                if(variants.contains(stack.variant()) && stack.count()<guard.capacity(stack.variant(),slot)) anyRoom=true;
            }
            boolean stockRemains=false, rejectedAmmo=false;
            for(int i=0;i<input.sources().size();i++) {
                var source=input.sources().get(i);
                if(remaining[i]<=0 || !guard.sources().contains(source.key()) || stationOnly&&!source.station())continue;
                if(variants.contains(source.stack().variant()))stockRemains=true;
                else {
                    var fact=input.variants().get(source.stack().variant());
                    // IDs only explain rejection here; native compatibility still decides allocation.
                    rejectedAmmo|=category.equals("ammo")&&fact.kind()==Kind.LOOSE_AMMO&&item.equals(fact.ammoId());
                }
            }
            if(!anyRoom) reasons.add(Reason.DESTINATION_FULL);
            else if(stockRemains||rejectedAmmo) reasons.add(Reason.INCOMPATIBLE_STOCK);
            else reasons.add(Reason.MISSING_STOCK);
        }
        rows.add(new Row(category,first,item,needed,supplied,incoming,reasons));
        return incoming;
    }
    /** Only the owning guard can project a still-reserved physical transfer. */
    private static Transfer validIncoming(Input input,GuardInput guard) {
        var p=input.incoming().get(guard.id());
        if(p==null||p.destination()<0||p.destination()>=SLOTS||p.amount()<=0||!guard.sources().contains(p.source()))return null;
        var source=input.sources().stream().filter(s->s.key().equals(p.source())&&s.slot()==p.sourceSlot()).findFirst().orElse(null);
        var fact=input.variants().get(p.variant());
        if(source==null||!source.reserved()||!Objects.equals(source.stack().variant(),p.variant())||source.stack().count()<p.amount()
                ||fact==null||!fact.definitionKnown()||guard.capacity(p.variant(),p.destination())<p.amount())return null;
        var old=guard.inventory().get(p.destination());
        if(p.destination()<6) {
            if(!source.station()||p.amount()!=1||!guard.equipment().get(p.destination()).variants().contains(p.variant())
                    ||!old.emptyStack()&&(!guard.replace()||old.count()!=1||!guard.returnable().contains(old.variant())||p.returnSlot()<27||p.returnSlot()>35))return null;
        } else if(p.destination()>=9&&!source.station()||!old.emptyStack()&&!old.variant().equals(p.variant())
                ||old.count()+p.amount()>guard.capacity(p.variant(),p.destination()))return null;
        return p;
    }
    private static String label(Input input,String key) {
        var value=input.variants().get(key); return value.gunId()==null?value.itemId():value.gunId();
    }
    private static Row row(String category,int slot,String item,int requested,int available,Reason reason) {
        return new Row(category,slot,item,requested,available,Set.of(reason));
    }
}
