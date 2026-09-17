package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.medical.TreatmentProfiles;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import tallestegg.guardvillagers.entities.Guard;

/** Captures copies once on the server thread. Reports contain no live references or item NBT. */
public final class ServiceAssessment {
    public record Request(UUID guard, ServiceSettings settings) {
        public Request { Objects.requireNonNull(guard); }
    }
    public record Stock(String item, String gun, String ammo, ServiceNeeds.Kind kind, int count, int reserved) {}
    public record GuardReport(UUID guard, List<ServiceNeeds.Row> rows) {
        public GuardReport { rows=List.copyOf(rows); }
    }
    public record Report(long observedAt, long profileRevision, int requested, int inspected, int unavailable,
            boolean truncated, boolean restrictedSources, List<Stock> stock, List<GuardReport> guards, String status) {
        public Report { stock=List.copyOf(stock); guards=List.copyOf(guards); }
    }
    private static final String[] ARMOR={"head","chest","legs","feet"};

    /** Settings null means inspect the guard's current policy. A preview never applies the requested policy. */
    public static Report preview(Player viewer, QuartermasterBlockEntity station,
            ServiceManagementPermissions.Context context, List<Request> requests) {
        Objects.requireNonNull(requests);
        if(viewer!=null && viewer.level() instanceof ServerLevel serverLevel && !serverLevel.getServer().isSameThread())
            throw new IllegalStateException("Capture must run on the server thread");
        if (!ServiceManagementPermissions.station(viewer,station,context).manage()) return denied(requests.size(),"access_denied");
        var level=(ServerLevel)viewer.level();
        List<Guard> guards=new ArrayList<>(); Map<UUID,ServiceSettings> settings=new HashMap<>(); Set<UUID> seen=new HashSet<>();
        int considered=Math.min(requests.size(),ServiceNeeds.MAX_GUARDS), unavailable=0;
        for(int i=0;i<considered;i++) {
            var request=requests.get(i);
            if(!seen.add(request.guard())) { unavailable++; continue; }
            if(!(level.getEntity(request.guard()) instanceof Guard guard) || !guard.isAlive() || guard.isRemoved()
                    || !GuardOrderPermissions.mayManage(viewer,guard) || guard.blockPosition().distSqr(station.getBlockPos())>48*48) { unavailable++; continue; }
            guards.add(guard); settings.put(guard.getUUID(),request.settings()==null?GuardEquipmentServiceAccess.get(guard).settings():request.settings());
        }
        Captured capture=capture(station,guards,settings,ServiceSurvey.visibleSources(viewer,station),true);
        var result=ServiceNeeds.evaluate(capture.input);
        List<GuardReport> reports=result.guards().stream().map(g->new GuardReport(g.id(),g.rows())).toList();
        int unreadable=(int)result.guards().stream().filter(g->g.rows().stream().anyMatch(r->r.reasons().contains(ServiceNeeds.Reason.UNAVAILABLE))).count();
        return new Report(level.getGameTime(),capture.profileRevision,requests.size(),guards.size(),unavailable+unreadable,
                requests.size()>considered,capture.restrictedSources,capture.stock(),reports,"estimate");
    }
    private static Report denied(int requested,String reason) {
        return new Report(0,EquipmentProfiles.revision(),requested,0,requested,false,true,List.of(),List.of(),reason);
    }
    /** Internal server snapshot; physical references never appear in packets. */
    static final class Captured {
        final ServiceNeeds.Input input;
        final Map<String,ServiceSource> sources;
        final Map<String,ItemStack> variants;
        final Map<UUID,List<ItemStack>> before;
        final Map<UUID,ServicePlan> incoming;
        final long profileRevision;
        final boolean restrictedSources;
        Captured(ServiceNeeds.Input input,Map<String,ServiceSource> sources,Map<String,ItemStack> variants,
                Map<UUID,List<ItemStack>> before,long profileRevision,boolean restrictedSources,Map<UUID,ServicePlan> incoming) {
            this.input=input;this.sources=sources;this.variants=variants;this.before=before;
            this.incoming=Map.copyOf(incoming);this.profileRevision=profileRevision;this.restrictedSources=restrictedSources;
        }
        List<Stock> stock() {
            record StockKey(String item,String gun,String ammo,ServiceNeeds.Kind kind) {}
            Map<StockKey,int[]> totals=new LinkedHashMap<>();
            for(var source:input.sources()) {
                if(source.stack().emptyStack())continue;
                var v=input.variants().get(source.stack().variant()); var key=new StockKey(v.itemId(),v.gunId(),v.ammoId(),v.kind());
                var amount=totals.computeIfAbsent(key,ignored->new int[2]); amount[0]+=source.stack().count(); if(source.reserved())amount[1]+=source.stack().count();
            }
            return totals.entrySet().stream().map(e->new Stock(e.getKey().item(),e.getKey().gun(),e.getKey().ammo(),e.getKey().kind(),e.getValue()[0],e.getValue()[1])).toList();
        }
    }
    static Captured capture(QuartermasterBlockEntity station,List<Guard> guards,Map<UUID,ServiceSettings> requested,List<ServiceSource> offered) {
        return capture(station,guards,requested,offered,false);
    }
    static Captured capture(QuartermasterBlockEntity station,List<Guard> guards,Map<UUID,ServiceSettings> requested,List<ServiceSource> offered,boolean includeIncoming) {
        if (!(station.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) throw new IllegalStateException("Server capture required");
        if(guards.size()>ServiceNeeds.MAX_GUARDS)throw new IllegalArgumentException("Too many guards");
        var profileMap=EquipmentProfiles.snapshot(); long revision=EquipmentProfiles.revision();
        var journal=ServiceJournal.get(level.getServer()); var catalog=new Catalog();
        Map<String,ServiceSource> sources=new LinkedHashMap<>(); List<ServiceNeeds.Source> stock=new ArrayList<>();
        // Only the station and its actual loaded links can appear; ignore caller-supplied foreign sources.
        var linked=QuartermasterServiceRuntime.sources(station);
        boolean restricted=linked.size()-1<station.linkedArmories().size() || offered.size()<linked.size();
        for(var source:offered) {
            if(sources.size()>=5) break;
            if(linked.stream().noneMatch(s->s.block()==source.block())) { restricted=true; continue; }
            boolean physical=source.block().getLevel()==level && !source.block().isRemoved()
                    && level.hasChunkAt(source.block().getBlockPos()) && level.getBlockEntity(source.block().getBlockPos())==source.block();
            // Rebuild trusted handlers/receipts before inspecting caller-supplied wrappers.
            ServiceSource canonical=source.block()==station?ServiceSource.of(station):ServiceSource.of((ArmoryBlockEntity)source.block());
            if(!physical || ServiceTransactions.mutating(source.block()) || !journal.matches(canonical.receipt())
                    || source.block() instanceof ArmoryBlockEntity armory && !armory.serviceInventoryAccessible()
                    || source.block()==station && !station.serviceReadable()) { restricted=true; continue; }
            String key=level.dimension().location()+"@"+source.block().getBlockPos().asLong();
            if(sources.putIfAbsent(key,canonical)!=null)continue;
            for(int slot=0;slot<canonical.stockSlots();slot++) stock.add(new ServiceNeeds.Source(key,slot,catalog.stack(canonical.inventory().getStackInSlot(slot)),
                    canonical.block()==station,QuartermasterServiceRuntime.reserved(canonical.block(),slot)));
        }
        List<Integer> returns=new ArrayList<>();
        if(station.serviceReadable() && journal.matches(station.serviceReceipt()))for(int slot=27;slot<36;slot++)
            if(station.inventory().getStackInSlot(slot).isEmpty()&&!QuartermasterServiceRuntime.reserved(station,slot))returns.add(slot);
        Map<UUID,List<ItemStack>> before=new HashMap<>(); Map<UUID,Boolean> readable=new HashMap<>();
        for(var guard:guards) {
            var state=GuardEquipmentServiceAccess.get(guard); var wanted=requested.get(guard.getUUID());
            boolean read=guard.level()==level && guard.isAlive() && !guard.isRemoved() && state.readable()
                    && journal.matches(state.receipt) && station.serviceReadable() && journal.matches(station.serviceReceipt())
                    && !ServiceTransactions.mutating(guard) && !ServiceTransactions.mutating(station)
                    && GuardMedicalAccess.get(guard).available() && GuardSidearmAccess.getState(guard)==SidearmState.PRIMARY_ACTIVE;
            if(wanted.assignment()!=null) read &= QuartermasterServiceRuntime.assignedStation(level,wanted.assignment(),guard.blockPosition()).station()==station;
            List<ItemStack> inventory=read?ServiceEquipment.snapshot(guard):java.util.stream.IntStream.range(0,12).mapToObj(i->ItemStack.EMPTY).toList();
            before.put(guard.getUUID(),inventory); readable.put(guard.getUUID(),read);
            inventory.forEach(catalog::stack);
        }
        List<ServiceNeeds.GuardInput> inputs=new ArrayList<>();
        for(var guard:guards) {
            var wanted=requested.get(guard.getUUID()); var profile=wanted.policy()==GuardServiceState.Policy.PROFILE&&wanted.profile()!=null?profileMap.get(wanted.profile()):null;
            var inventory=before.get(guard.getUUID()); boolean read=readable.get(guard.getUUID());
            List<ServiceNeeds.Rule> rules=new ArrayList<>();
            for(int target=0;target<6;target++) {
                List<String> selectors=profile==null?List.of():target==0?profile.primary():target==1?profile.sidearm():profile.armor().getOrDefault(ARMOR[target-2],List.of());
                var matches=new HashSet<String>();
                for(var entry:catalog.stacks.entrySet()) if(matches(entry.getValue(),selectors,target<2)) matches.add(entry.getKey());
                rules.add(new ServiceNeeds.Rule(!selectors.isEmpty(),matches));
            }
            List<String> medicineIds=profile==null?inventory.subList(9,12).stream().filter(s->!s.isEmpty()).map(s->BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).distinct().toList():profile.medical();
            Set<String> medicines=new HashSet<>(),returnable=new HashSet<>(); Map<String,List<Integer>> capacities=new HashMap<>();
            for(var entry:catalog.stacks.entrySet()) {
                String key=entry.getKey(); ItemStack stack=entry.getValue(); var fact=catalog.facts.get(key);
                if(fact.kind()==ServiceNeeds.Kind.MEDICAL && matches(stack,medicineIds,false)) medicines.add(key);
                if(read && stack.getCount()==1 && !EnchantmentHelper.hasBindingCurse(stack)
                        && station.inventory().isItemValid(27,stack.copy()))returnable.add(key);
                List<Integer> limits=new ArrayList<>();
                for(int target=0;target<12;target++) {
                    boolean relevant=target<6?rules.get(target).variants().contains(key):target<9?fact.kind()==ServiceNeeds.Kind.LOOSE_AMMO:medicines.contains(key);
                    // All native validation receives isolated copies, never live inventory stacks.
                    if(read&&relevant) {
                        ItemStack single=stack.copy();single.setCount(1);
                        limits.add(ServiceEquipment.valid(guard,target,single)?ServiceEquipment.limit(guard,target,single):0);
                    } else limits.add(0);
                }
                capacities.put(key,limits);
            }
            Set<String> allowed=new HashSet<>(); boolean denied=restricted;
            for(var source:sources.entrySet()) if(station.mayService(guard) && source.getValue().live(guard))allowed.add(source.getKey()); else denied=true;
            inputs.add(new ServiceNeeds.GuardInput(guard.getUUID(),inventory.stream().map(catalog::stack).toList(),rules,
                    wanted.policy()==GuardServiceState.Policy.MANUAL,wanted.policy()==GuardServiceState.Policy.PROFILE&&profile==null,read,
                    wanted.replaceExisting(),profile==null?64:profile.ammoReserve(),profile==null?16:profile.medicalReserve(),medicines,
                    !medicineIds.isEmpty(),returnable,capacities,allowed,denied));
        }
        Map<String,Set<String>> compatibleAmmo=new HashMap<>();
        for(var gun:catalog.facts.values()) if(gun.kind()==ServiceNeeds.Kind.GUN && gun.definitionKnown() && gun.physicalReserve()) {
            Set<String> compatible=new HashSet<>();
            for(var ammo:catalog.facts.values()) if(ammo.kind()==ServiceNeeds.Kind.LOOSE_AMMO
                    && AmmoCompatibility.assess(catalog.stacks.get(gun.key()).copy(),catalog.stacks.get(ammo.key()).copy()).isCompatible()) compatible.add(ammo.key());
            compatibleAmmo.put(gun.key(),compatible);
        }
        Map<UUID,ServicePlan> pending=new HashMap<>();Map<UUID,ServiceNeeds.Transfer> incoming=new HashMap<>();
        if(includeIncoming)for(var guard:guards) {
            var plan=QuartermasterServiceRuntime.plan(guard);
            if(plan==null||plan.station()!=station||!requested.get(guard.getUUID()).equals(GuardEquipmentServiceAccess.get(guard).settings())
                    ||!ServiceTransactions.incoming(plan))continue;
            var source=sources.entrySet().stream().filter(e->e.getValue().block()==plan.source().block()).findFirst().orElse(null);
            if(source==null)continue;
            var item=stock.stream().filter(s->s.key().equals(source.getKey())&&s.slot()==plan.sourceSlot()&&s.reserved()).findFirst().orElse(null);
            if(item==null||item.stack().emptyStack())continue;
            int amount=plan.replacement().getCount()-(plan.targetSlot()>=6?plan.guardBefore().get(plan.targetSlot()).getCount():0);
            incoming.put(guard.getUUID(),new ServiceNeeds.Transfer(guard.getUUID(),source.getKey(),plan.sourceSlot(),plan.targetSlot(),plan.returnSlot(),item.stack().variant(),amount));
            pending.put(guard.getUUID(),plan);
        }
        return new Captured(new ServiceNeeds.Input(catalog.facts,stock,returns,inputs,compatibleAmmo,incoming),Map.copyOf(sources),Map.copyOf(catalog.stacks),Map.copyOf(before),revision,
                restricted||inputs.stream().anyMatch(ServiceNeeds.GuardInput::restrictedSources),pending);
    }
    private static boolean matches(ItemStack stack,List<String> selectors,boolean gun) {
        if(selectors.isEmpty())return false;
        try{return EquipmentProfile.matches(stack.copy(),selectors,gun);}catch(RuntimeException unavailable){return false;}
    }
    private static final class Catalog {
        final Map<String,ItemStack> stacks=new LinkedHashMap<>(); final Map<String,ServiceNeeds.Variant> facts=new LinkedHashMap<>();
        ServiceNeeds.Stack stack(ItemStack live) {
            if(live.isEmpty())return ServiceNeeds.Stack.empty();
            ItemStack copy=live.copy(); int count=copy.getCount(); copy.setCount(1);
            for(var entry:stacks.entrySet())if(ItemStack.isSameItemSameTags(entry.getValue(),copy))return new ServiceNeeds.Stack(entry.getKey(),count);
            String key="v"+stacks.size(); stacks.put(key,copy);
            String item=BuiltInRegistries.ITEM.getKey(copy.getItem()).toString(), gun=null,ammo=null;
            var kind=ServiceNeeds.Kind.OTHER; boolean known=true, physicalReserve=true;
            try {
                if(copy.getItem() instanceof IGun nativeGun) {
                    kind=ServiceNeeds.Kind.GUN; var id=nativeGun.getGunId(copy.copy()); gun=id==null?null:id.toString();
                    var definition=id==null?null:TimelessAPI.getCommonGunIndex(id).orElse(null);
                    known=definition!=null;
                    if(known&&definition.getGunData().getAmmoId()!=null)ammo=definition.getGunData().getAmmoId().toString();
                    if(known) {
                        var reserve=GuardAmmoSummary.capture(copy.copy(),definition.getGunData(),null);
                        known=reserve.isReady(); physicalReserve=reserve.reserveSource()==GuardAmmoSummary.ReserveSource.PHYSICAL;
                    }
                } else if(copy.getItem() instanceof IAmmoBox) kind=ServiceNeeds.Kind.AMMO_BOX;
                else if(copy.getItem() instanceof IAmmo nativeAmmo) {
                    kind=ServiceNeeds.Kind.LOOSE_AMMO; var id=nativeAmmo.getAmmoId(copy.copy()); ammo=id==null?null:id.toString();
                } else if(TreatmentProfiles.resolve(copy.copy())!=null)kind=ServiceNeeds.Kind.MEDICAL;
            } catch(RuntimeException unavailable) { known=false; }
            facts.put(key,new ServiceNeeds.Variant(key,item,gun,ammo,kind,known,physicalReserve)); return new ServiceNeeds.Stack(key,count);
        }
    }
}
