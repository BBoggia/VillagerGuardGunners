package com.gamergaming.villagerguardgunners.quartermaster.management;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import tallestegg.guardvillagers.entities.Guard;
import static com.gamergaming.villagerguardgunners.quartermaster.management.OverviewProtocol.*;

/** Shared permission-equivalent jobs, round-robin, at most sixteen native guard captures per dimension/tick. */
public final class OverviewRuntime {
    public static final int BUDGET=16, MAX_JOBS=32;
    public record Member(UUID id,int group,String name,String policy,String profile,String status,long revision,long seen,boolean loaded) {}
    public record Scope(UUID station,UUID placement,long profiles,boolean manage,List<Member> members,List<Long> sources,boolean truncated) {
        public Scope {members=List.copyOf(members);sources=List.copyOf(sources);}
    }
    public record Frame(int generation,long observedAt,Scope scope,List<Row> stock,List<Row> needs,List<Row> guards,List<Row> history,
            Map<Integer,Set<UUID>> affected,int inspected,int unavailable,boolean restricted,List<ServicePlan> deliveries) {
        public Frame {stock=List.copyOf(stock);needs=List.copyOf(needs);guards=List.copyOf(guards);history=List.copyOf(history);affected=Map.copyOf(affected);deliveries=List.copyOf(deliveries);}
        public boolean incomingCurrent(){return deliveries.stream().allMatch(ServiceTransactions::incoming);}
        public List<Row> rows(int view,Set<UUID> filter) {
            return switch(view) {
                case ManagementProtocol.GUARDS -> filter==null?guards:guards.stream().filter(r->filter.contains(r.guard())).toList();
                case ManagementProtocol.NEEDS -> needs;
                case ManagementProtocol.SERVICE -> history;
                default -> stock;
            };
        }
    }
    private static final Map<ServerLevel,State> STATES=new WeakHashMap<>();
    private static final class State {
        final LinkedHashMap<Scope,Job> jobs=new LinkedHashMap<>();
        final LinkedHashMap<Scope,Frame> cache=new LinkedHashMap<>();
        final Map<UUID,Long> requested=new HashMap<>();
        final LinkedHashSet<BulkSession> estimates=new LinkedHashSet<>();
        boolean bulkTurn;
        int generation,work;
    }
    private static final class Job {
        final Scope scope;ServerPlayer viewer;final QuartermasterBlockEntity station;
        final Map<QuartermasterManagementMenu,ManagementSession> viewers=new LinkedHashMap<>();
        ServiceSurvey survey;int cursor,retries;long started;
        Job(Scope scope,ServerPlayer viewer,QuartermasterBlockEntity station){this.scope=scope;this.viewer=viewer;this.station=station;}
    }
    public static Scope scope(ServerPlayer viewer,ManagementSession session) {
        var station=session.station();var level=viewer.serverLevel();
        List<Member> members=new ArrayList<>();Set<UUID> loaded=new HashSet<>();
        boolean truncated=!QuartermasterServiceRuntime.completeGuardIndex(level)||!ServiceRoster.get(level).readable()||ServiceRoster.get(level).full();
        if(session.manage()) {
            for(var guard:QuartermasterServiceRuntime.loadedGuards(level)) {
                loaded.add(guard.getUUID());
                if(!guard.isAlive()||guard.isRemoved())continue;
                var state=GuardEquipmentServiceAccess.get(guard);var a=state.assignment();
                boolean assigned=a!=null&&a.stationId().equals(station.stationId())&&a.dimension().equals(level.dimension().location());
                var plan=QuartermasterServiceRuntime.plan(guard);
                int group=assigned?0:a==null&&plan!=null&&plan.station()==station?1:2;
                if(!GuardOrderPermissions.mayManage(viewer,guard)){if(assigned||group==1)truncated=true;continue;}
                if(!assigned&&(a!=null||guard.blockPosition().distSqr(station.getBlockPos())>48*48))continue;
                boolean nearby=guard.blockPosition().distSqr(station.getBlockPos())<=48*48;
                members.add(new Member(guard.getUUID(),group,clean(guard.getName().getString(),64),state.policy().name().toLowerCase(Locale.ROOT),
                        state.profile()==null?"":clean(state.profile().toString(),96),state.readable()&&nearby?state.status.name().toLowerCase(Locale.ROOT):"unavailable",state.settingsRevision(),0,nearby));
            }
            for(var entry:ServiceRoster.get(level).entries()) {
                if(loaded.contains(entry.guard())||!entry.assignment().stationId().equals(station.stationId())||!entry.assignment().dimension().equals(level.dimension().location()))continue;
                // An unindexed but loaded guard must not evade current ownership checks.
                if(level.getEntity(entry.guard())!=null){truncated=true;continue;}
                members.add(new Member(entry.guard(),0,"Unavailable guard","","","unavailable",0,entry.seen(),false));
            }
        }
        members.sort(Comparator.comparingInt(Member::group).thenComparing(Member::id));
        truncated|=members.size()>64;members=new ArrayList<>(members.subList(0,Math.min(64,members.size())));
        return new Scope(station.stationId(),station.dropId(),EquipmentProfiles.revision(),session.manage(),members,
                ServiceSurvey.visibleSources(viewer,station).stream().map(s->s.block().getBlockPos().asLong()).toList(),truncated);
    }
    public static void request(QuartermasterManagementMenu menu,ServerPlayer viewer,ManagementSession session) {
        if(!session.stock()||!session.valid(viewer))return;
        var level=viewer.serverLevel();var state=STATES.computeIfAbsent(level,ignored->new State());var scope=scope(viewer,session);
        var cached=state.cache.get(scope);
        if(cached!=null&&cached.incomingCurrent()&&level.getGameTime()>=cached.observedAt()&&level.getGameTime()-cached.observedAt()<20){menu.complete(cached);return;}
        var last=state.requested.get(viewer.getUUID());
        if(last!=null&&level.getGameTime()>=last&&level.getGameTime()-last<20)return;
        var job=state.jobs.get(scope);
        if(job==null) {
            if(state.jobs.size()+state.estimates.size()>=MAX_JOBS)return;
            job=new Job(scope,viewer,session.station());state.jobs.put(scope,job);
        }
        if(job.viewers.size()<128&&state.requested.size()<4096){job.viewers.put(menu,session);state.requested.put(viewer.getUUID(),level.getGameTime());}
    }
    public static int lastWork(ServerLevel level){var state=STATES.get(level);return state==null?0:state.work;}
    static boolean requestEstimate(ServerLevel level,BulkSession session) {
        var state=STATES.computeIfAbsent(level,ignored->new State());
        if(state.estimates.contains(session))return true;
        if(state.jobs.size()+state.estimates.size()>=MAX_JOBS)return false;
        state.estimates.add(session);return true;
    }
    static void cancelEstimate(ServerLevel level,BulkSession session) {
        var state=STATES.get(level);if(state!=null)state.estimates.remove(session);
    }
    public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase==TickEvent.Phase.END)for(var level:event.getServer().getAllLevels())advance(level);
    }
    public static void advance(ServerLevel level) {
        var state=STATES.get(level);if(state==null)return;state.work=0;
        state.requested.entrySet().removeIf(e->level.getGameTime()<e.getValue()||level.getGameTime()-e.getValue()>=20);
        state.cache.entrySet().removeIf(e->level.getGameTime()-e.getValue().observedAt()>=20);
        Map<ManagementSession,Scope> checkedScopes=new IdentityHashMap<>();
        for(int remaining=BUDGET;remaining>0&&(!state.jobs.isEmpty()||!state.estimates.isEmpty());remaining--) {
            if(!state.estimates.isEmpty()&&(state.jobs.isEmpty()||state.bulkTurn)) {
                var estimate=state.estimates.iterator().next();state.estimates.remove(estimate);
                state.bulkTurn=false;state.work++;
                if(estimate.advanceEstimate())state.estimates.add(estimate);
                continue;
            }
            state.bulkTurn=true;
            var entry=state.jobs.entrySet().iterator().next();var job=entry.getValue();state.jobs.remove(entry.getKey());
            job.viewers.entrySet().removeIf(v->!v.getKey().active(v.getValue()));
            if(job.viewers.isEmpty())continue;
            state.work++;
            try {
                // Rebind only to an active viewer with this exact scope. Never restart a
                // shared survey using a moved/departed viewer's different source visibility.
                var primary=job.viewers.entrySet().stream().filter(v->v.getKey().viewer()==job.viewer
                        &&checkedScopes.computeIfAbsent(v.getValue(),session->scope(v.getKey().viewer(),session)).equals(job.scope)).findFirst().orElse(null);
                if(primary==null) {
                    primary=job.viewers.entrySet().stream().filter(v->checkedScopes.computeIfAbsent(v.getValue(),session->scope(v.getKey().viewer(),session)).equals(job.scope)).findFirst().orElse(null);
                    if(primary==null)continue;
                    job.viewer=primary.getKey().viewer();job.survey=null;job.cursor=0;
                }
                if(job.survey==null){job.survey=new ServiceSurvey(job.viewer,job.station);job.started=level.getGameTime();}
                if(!job.survey.visibleSources().equals(job.scope.sources()))continue;
                if(job.cursor<job.scope.members().size()) {
                    var member=job.scope.members().get(job.cursor++);
                    if(member.loaded()&&member.group()!=2) {
                        if(!(level.getEntity(member.id()) instanceof Guard guard)||!GuardOrderPermissions.mayManage(job.viewer,guard))continue;
                        if(!job.survey.add(guard)){retry(state,job);continue;}
                    }
                }
                if(job.cursor<job.scope.members().size()){state.jobs.put(entry.getKey(),job);continue;}
                if(!job.survey.current()){retry(state,job);continue;}
                if(!scope(job.viewer,primary.getValue()).equals(job.scope))continue;
                var frame=finish(job,++state.generation,level);
                boolean published=false;
                for(var viewer:job.viewers.entrySet())if(viewer.getKey().active(viewer.getValue())&&scope(viewer.getKey().viewer(),viewer.getValue()).equals(job.scope)) {
                    viewer.getKey().complete(frame);published=true;
                }
                if(published){state.cache.put(job.scope,frame);while(state.cache.size()>MAX_JOBS)state.cache.remove(state.cache.keySet().iterator().next());}
            }catch(RuntimeException unavailable){/* Unsupported item callbacks leave an explicitly refreshing, partial view. */}
        }
    }
    private static void retry(State state,Job job) {
        // A delivery every twenty ticks must not repeatedly invalidate the same phase of a long scan.
        // Restarts remain inside this dimension's existing tick budget and never expand the queue.
        if(job.retries++<2){job.survey=null;job.cursor=0;state.jobs.put(job.scope,job);}
    }
    private static Frame finish(Job job,int generation,ServerLevel level) {
        List<Row> stock=new ArrayList<>(),roster=new ArrayList<>(),history=new ArrayList<>(),needs=new ArrayList<>();
        for(var s:job.survey.stock()) {
            String id=s.variant().gunId()!=null?s.variant().gunId():s.variant().ammoId()!=null?s.variant().ammoId():s.item().getHoverName().getString();
            var item=s.item();String detail=(item.hasCustomHoverName()&&(s.variant().gunId()!=null||s.variant().ammoId()!=null)?item.getHoverName().getString()+"; ":"")+s.variant().itemId()+"; reserved: "+s.reserved();
            if(item.isDamageableItem())detail+="; durability: "+(item.getMaxDamage()-item.getDamageValue())+"/"+item.getMaxDamage();
            var enchants=net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(item);
            for(var e:enchants.entrySet())detail+="; "+net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getKey(e.getKey())+" "+e.getValue();
            stock.add(new Row(STOCK_ROW,null,clean(id,96),clean(detail,MAX_TEXT),"",s.count(),s.count()-s.reserved(),s.reserved(),0,-1));
        }
        int inspected=0,unavailable=0;
        Map<UUID,Member> members=new HashMap<>();
        for(var member:job.scope.members()) {
            members.put(member.id(),member);if(member.loaded())inspected++;else unavailable++;
            String group=member.group()==0?"Assigned":member.group()==1?"Current visitor (Auto)":"Nearby candidate (not included in needs)";
            String detail=group+"; "+member.policy()+(member.profile().isEmpty()?"":"; "+member.profile());
            if(!member.loaded())detail+="; last seen: "+(member.seen()==0?"unknown":Math.max(0,(level.getServer().overworld().getGameTime()-member.seen())/20)+"s ago");
            boolean forget=!member.loaded()&&ServiceControls.canForgetUnavailable(job.viewer,job.station,
                    ServiceManagementPermissions.Context.of(job.station),member.id());
            roster.add(new Row(GUARD_ROW,member.id(),member.name(),clean(detail,MAX_TEXT),member.status(),0,0,0,0,forget?roster.size():-1));
        }
        record Key(String category,int slot,String item){}
        final class Total {int requested,available,incoming,flags;final Set<UUID> affected=new HashSet<>();}
        Map<Key,Total> totals=new LinkedHashMap<>();
        for(var guard:job.survey.finish().guards())for(var r:guard.rows()) {
            if(r.reasons().contains(ServiceNeeds.Reason.MANUAL)||r.requested()==0&&r.reasons().equals(Set.of(ServiceNeeds.Reason.ACCEPTED))||r.reasons().contains(ServiceNeeds.Reason.NOT_CONFIGURED)||r.reasons().contains(ServiceNeeds.Reason.RESERVE_NOT_PHYSICAL))continue;
            if(r.reasons().contains(ServiceNeeds.Reason.UNAVAILABLE))unavailable++;
            var total=totals.computeIfAbsent(new Key(r.category(),r.slot(),r.item()),ignored->new Total());
            total.requested+=r.requested();total.available+=r.available();total.incoming+=r.incoming();for(var reason:r.reasons())total.flags|=1<<reason.ordinal();
            if(r.missing()>0||r.reasons().stream().anyMatch(x->x==ServiceNeeds.Reason.CONDITIONAL||x==ServiceNeeds.Reason.UNAVAILABLE||x==ServiceNeeds.Reason.DEFINITION_UNAVAILABLE||x==ServiceNeeds.Reason.PROFILE_MISSING||x==ServiceNeeds.Reason.SOURCE_RESTRICTED))total.affected.add(guard.id());
        }
        Map<Integer,Set<UUID>> affected=new HashMap<>();int index=0;
        for(var e:totals.entrySet()) {
            var k=e.getKey();var t=e.getValue();String target=k.category().equals("equipment")?switch(k.slot()){case 0->"Primary gun";case 1->"Sidearm";case 2->"Helmet";case 3->"Chestplate";case 4->"Leggings";default->"Boots";}:k.category();
            if(!t.affected.isEmpty())affected.put(index,Set.copyOf(t.affected));
            needs.add(new Row(NEED_ROW,null,clean(k.item().isEmpty()?target:k.item(),96),clean(target+"; requested / available / incoming / missing",MAX_TEXT),"",t.requested,t.available,t.requested-t.available-t.incoming,t.incoming,t.flags,t.affected.isEmpty()?-1:index));index++;
        }
        for(var e:ServiceHistory.recent(level,job.station.dropId())) {
            // History cannot reveal another owner's guard, including after ownership changes/unloads.
            var member=members.get(e.guard());if(member==null||!member.loaded())continue;
            history.add(new Row(HISTORY_ROW,e.guard(),member.name(),clean(e.item()+"; "+e.amount()+" items; "+Math.max(0,(level.getGameTime()-e.time())/20)+"s ago",MAX_TEXT),"supplied",e.amount(),e.amount(),0,0,-1));
        }
        return new Frame(generation,job.started,job.scope,stock,needs,roster,history,affected,inspected,unavailable,job.survey.restricted()||needs.stream().anyMatch(r->(r.flags()&(1<<ServiceNeeds.Reason.SOURCE_RESTRICTED.ordinal()))!=0),job.survey.deliveries());
    }
    /** Reporting does not call journal verification or run pathfinding. */
    public static ServiceStatus stationStatus(QuartermasterBlockEntity station) {
        var level=(ServerLevel)station.getLevel();
        if(!station.serviceReadable()||!ServiceJournal.get(level.getServer()).matches(station.serviceReceipt()))return ServiceStatus.RECOVERY_REQUIRED;
        if(level.getRaidAt(station.getBlockPos())!=null)return ServiceStatus.RAID;
        long time=Math.floorMod(level.getDayTime(),24000);if(time<2000||time>=9000)return ServiceStatus.OUTSIDE_WORK_HOURS;
        var npc=QuartermasterManagement.operator(station);
        if(npc==null||npc.isNoAi()||npc.isSleeping()||npc.isTrading()||npc.getBrain().isActive(Activity.PANIC)||npc.getBrain().isActive(Activity.RAID))return ServiceStatus.OPERATOR_UNAVAILABLE;
        return ServiceStatus.READY;
    }
    public static void stopped(ServerStoppedEvent event){STATES.keySet().removeIf(l->l.getServer()==event.getServer());}
    private OverviewRuntime(){}
}
