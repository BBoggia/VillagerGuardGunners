package com.gamergaming.villagerguardgunners.quartermaster.management;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import tallestegg.guardvillagers.entities.Guard;

/** Bounded, transient settings editor. A preview owns no stock and never queues unloaded edits. */
public final class BulkSession {
    public static final long PREVIEW_TICKS=600;
    private static final Map<ServerPlayer,Long> NEXT_ESTIMATE=new WeakHashMap<>();
    private final ServerPlayer player;
    private final ManagementSession session;
    private final ServiceManagementPermissions.Context context;
    private final net.minecraft.world.inventory.AbstractContainerMenu openingMenu;
    private final LinkedHashSet<UUID> selected=new LinkedHashSet<>();
    private BulkChoices choices=BulkChoices.KEEP;
    private int view,page,excluded,applied;
    private boolean active,limited;
    private String status="select";
    private Preview preview;
    private List<BulkProtocol.Line> needs=List.of();
    private List<Long> estimateSources=List.of();
    private List<ServicePlan> estimateDeliveries=List.of();
    private ServiceSurvey survey;
    private int estimateCursor,estimateRetries;
    private boolean estimatePending;
    private long estimatedAt=Long.MIN_VALUE;
    private record Preview(UUID token,long expires,long profiles,List<ServiceControls.BatchEntry> entries) {}
    public BulkSession(ServerPlayer player,ManagementSession session) {
        this.player=player;this.session=session;context=ServiceManagementPermissions.Context.of(session.station());openingMenu=player.containerMenu;
    }
    public boolean active(){return active;}
    private long now(){return player.level().getGameTime();}
    private Guard guard(UUID id){return player.serverLevel().getEntity(id) instanceof Guard g?g:null;}
    private boolean eligible(Guard g) {
        if(g==null||g.isRemoved()||!g.isAlive()||g.level()!=player.level()||g.blockPosition().distSqr(context.position())>48*48
                ||!GuardOrderPermissions.mayManage(player,g)||!GuardEquipmentServiceAccess.get(g).readable())return false;
        var a=GuardEquipmentServiceAccess.get(g).assignment();
        return a==null||a.stationId().equals(context.station())&&a.dimension().equals(context.dimension());
    }
    private UUID squad(Guard g) {
        var m=GuardSquadAccess.getMembership(g);
        return m!=null&&m.schemaVersion()==1&&SquadRegistrySavedData.get(player.server).find(m.squadId()).isPresent()?m.squadId():null;
    }
    private List<Guard> candidates() {
        var all=QuartermasterServiceRuntime.loadedGuards(player.serverLevel()).stream().filter(this::eligible)
                .sorted(Comparator.<Guard,Boolean>comparing(g->GuardEquipmentServiceAccess.get(g).assignment()==null).thenComparing(Guard::getUUID)).toList();
        limited=!QuartermasterServiceRuntime.completeGuardIndex(player.serverLevel());
        return all;
    }
    private void clearEstimate() {
        OverviewRuntime.cancelEstimate(player.serverLevel(),this);estimatePending=false;survey=null;
        needs=List.of();estimateDeliveries=List.of();estimatedAt=Long.MIN_VALUE;
    }
    private void invalidate(String reason){preview=null;clearEstimate();status=reason;applied=0;}
    public void close(){active=false;invalidate("select");}
    public boolean act(BulkProtocol.Action action) {
        if(!session.valid(player)||!session.manage())return false;
        int op=action.action(),v=action.value();
        if(op==BulkProtocol.OPEN){active=true;return true;}
        if(!active)return false;
        if(op==BulkProtocol.CLOSE){close();return true;}
        if(op==BulkProtocol.PAGE){if(v<0)return false;page=v;return true;}
        if(op==BulkProtocol.VIEW){if(v<0||v>2)return false;view=v;page=0;return true;}
        if(op==BulkProtocol.APPLY){return apply(action.token());}
        if(op==BulkProtocol.PREVIEW){return createPreview();}
        switch(op) {
            case BulkProtocol.TOGGLE -> {
                if(!selected.remove(action.guard())) {
                    if(selected.size()>=64||candidates().stream().noneMatch(g->g.getUUID().equals(action.guard())))return false;
                    selected.add(action.guard());
                }
                excluded=0;
            }
            case BulkProtocol.SQUAD -> {
                var seed=guard(action.guard());if(!eligible(seed))return false;var id=squad(seed);if(id==null)return false;
                selected.clear();excluded=0;
                for(var g:QuartermasterServiceRuntime.loadedGuards(player.serverLevel()).stream().sorted(Comparator.comparing(Guard::getUUID)).toList()) {
                    if(!GuardSquadAccess.isMemberOf(g,id))continue;
                    if(eligible(g)&&id.equals(squad(g))&&selected.size()<64)selected.add(g.getUUID());else excluded++;
                }
                view=0;page=0;
            }
            case BulkProtocol.CLEAR -> {selected.clear();excluded=0;view=0;page=0;}
            case BulkProtocol.POLICY -> {if(v< -1||v>2)return false;choices=new BulkChoices(v,choices.profile(),choices.replacement(),choices.assignment());}
            case BulkProtocol.PROFILE -> {
                var ids=EquipmentProfiles.snapshot().keySet().stream().sorted().toList();
                int index=choices.profile()==null?-1:ids.indexOf(choices.profile());
                ResourceLocation next=index+1<ids.size()?ids.get(index+1):null;
                choices=new BulkChoices(choices.policy(),next,choices.replacement(),choices.assignment());
            }
            case BulkProtocol.REPLACE -> {if(v< -1||v>1)return false;choices=new BulkChoices(choices.policy(),choices.profile(),v,choices.assignment());}
            case BulkProtocol.ASSIGN -> {if(v< -1||v>1)return false;choices=new BulkChoices(choices.policy(),choices.profile(),choices.replacement(),v);}
            default -> {return false;}
        }
        invalidate("changed");return true;
    }
    private ServiceSettings resolve(Guard g) {
        return choices.resolve(GuardEquipmentServiceAccess.get(g).settings(),new ServiceStationAssignment(context.station(),context.dimension(),context.position()));
    }
    private boolean createPreview() {
        if(now()<NEXT_ESTIMATE.getOrDefault(player,Long.MIN_VALUE)){status="cooldown";return false;}
        invalidate("select");if(selected.isEmpty())return false;
        List<ServiceControls.BatchEntry> entries=new ArrayList<>();
        for(var id:selected) {
            var g=guard(id);if(!eligible(g)){status="stale";return false;}
            var state=GuardEquipmentServiceAccess.get(g);var requested=resolve(g);
            var permission=ServiceManagementPermissions.change(player,session.station(),context,g,requested);
            if(permission!=ServiceManagementPermissions.Result.ALLOWED){status=permission.name().toLowerCase(Locale.ROOT);return false;}
            entries.add(new ServiceControls.BatchEntry(g,state.settings(),state.settingsRevision(),GuardSquadAccess.getRevision(g),requested));
        }
        preview=new Preview(UUID.randomUUID(),now()+PREVIEW_TICKS,EquipmentProfiles.revision(),List.copyOf(entries));
        view=1;page=0;status="preview";estimate();return true;
    }
    private boolean current() {
        if(preview==null)return false;
        if(now()>=preview.expires()){invalidate("expired");return false;}
        if(!session.valid(player)||EquipmentProfiles.revision()!=preview.profiles()){invalidate("stale");return false;}
        for(var e:preview.entries()) {
            var g=e.guard();var s=GuardEquipmentServiceAccess.get(g);
            if(guard(g.getUUID())!=g||!eligible(g)||s.settingsRevision()!=e.revision()||!s.settings().equals(e.expected())
                    ||GuardSquadAccess.getRevision(g)!=e.squadRevision()
                    ||ServiceManagementPermissions.change(player,session.station(),context,g,e.requested())!=ServiceManagementPermissions.Result.ALLOWED) {
                invalidate("stale");return false;
            }
        }
        return true;
    }
    private void estimate() {
        if(preview==null||estimatePending||now()<NEXT_ESTIMATE.getOrDefault(player,Long.MIN_VALUE))return;
        if(!OverviewRuntime.requestEstimate(player.serverLevel(),this))return;
        NEXT_ESTIMATE.put(player,now()+20);
        estimateSources=ServiceSurvey.visibleSources(player,session.station()).stream().map(s->s.block().getBlockPos().asLong()).toList();
        estimatePending=true;survey=null;estimateCursor=0;estimateRetries=0;
    }
    /** Called only by the shared per-dimension assessment budget. Captures at most one guard. */
    boolean advanceEstimate() {
        if(!estimatePending)return false;
        if(player.containerMenu!=openingMenu||!active||!current()) {clearEstimate();return false;}
        if(!estimateSources.equals(ServiceSurvey.visibleSources(player,session.station()).stream().map(s->s.block().getBlockPos().asLong()).toList())) {
            clearEstimate();return false;
        }
        try {
            if(survey==null)survey=new ServiceSurvey(player,session.station());
            var entry=preview.entries().get(estimateCursor);
            if(!survey.add(entry.guard(),entry.requested()))return retryEstimate();
            if(++estimateCursor<preview.entries().size())return true;
            if(!survey.current()||!current())return retryEstimate();
            List<BulkProtocol.Line> rows=new ArrayList<>();
            for(var g:survey.finish().guards())for(var r:g.rows()) {
                String reasons=r.reasons().stream().sorted().map(x->x.name().toLowerCase(Locale.ROOT).replace('_',' ')).reduce((a,b)->a+"; "+b).orElse("");
                rows.add(new BulkProtocol.Line(g.id(),cut(name(guard(g.id()))+": "+r.category(),96),cut(g.id()+"\n"+r.item()+"\nRequested: "+r.requested()+"; available: "+r.available()+"; incoming: "+r.incoming()+"; missing: "+r.missing()+"\n"+reasons,1024),true,false));
            }
            needs=List.copyOf(rows.subList(0,Math.min(rows.size(),1280)));
            estimateDeliveries=survey.deliveries();estimatedAt=now();
        } catch(RuntimeException ex) {
            needs=List.of(new BulkProtocol.Line(null,"Estimate unavailable","Settings can still be saved. Equipment will be checked again during service.",false,false));
            estimatedAt=now();
        }
        estimatePending=false;survey=null;NEXT_ESTIMATE.put(player,now()+20);
        if(openingMenu instanceof com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu menu)menu.estimateComplete();
        return false;
    }
    private boolean retryEstimate() {
        survey=null;estimateCursor=0;
        if(preview!=null&&estimateRetries++<2)return true;
        clearEstimate();return false;
    }
    private boolean apply(UUID token) {
        if(preview==null||!preview.token().equals(token))return false;
        if(!current())return false;
        var entries=preview.entries();
        boolean result=ServiceControls.applyBatch(player,session.station(),context,entries);
        invalidate(result?"applied":"stale");applied=result?entries.size():0;view=0;page=0;return result;
    }
    private static String cut(String s,int max){return s.length()>max?s.substring(0,max-1)+"…":s;}
    private String name(Guard g){return g==null?"Unavailable guard":cut(g.getDisplayName().getString(),96);}
    private String policy(int p){return p<0?"Keep":switch(p){case 0->"Manual";case 1->"Restock Current";default->"Maintain Loadout";};}
    private String describe(ServiceSettings s) {
        return policy(s.policy().ordinal())+"; profile: "+(s.profile()==null?"None":s.profile())+"; replace: "+s.replaceExisting()+"; station: "+(s.assignment()==null?"Auto":"This station");
    }
    private String deferred(Guard g,ServiceSettings requested) {
        var guardStatus=ServiceEligibility.inspectGuard(g,requested.policy());
        if(guardStatus==ServiceStatus.READY)guardStatus=g.isNoAi()?ServiceStatus.NATIVE_ACTION:ServiceEligibility.inspectStation(session.station());
        return switch(guardStatus) {
            case READY -> "Ready for service";
            case MANUAL -> "Manual (service disabled)";
            case FIGHTING -> "Waiting until fighting ends";
            case PLAYER_ORDER -> "Waiting for Guard Orders or other duties";
            case NATIVE_ACTION -> "Waiting for native actions or AI availability";
            case OPERATOR_UNAVAILABLE -> "Waiting for a working Quartermaster";
            case OUTSIDE_WORK_HOURS -> "Waiting for work hours";
            default -> guardStatus.name().toLowerCase(Locale.ROOT).replace('_',' ');
        };
    }
    private static int mixed(List<Integer> values){return values.isEmpty()?-1:values.stream().distinct().count()>1?-2:values.get(0);}
    public BulkProtocol.State state(int menu,int revision) {
        current();
        if(preview!=null) {
            if(!estimateSources.equals(ServiceSurvey.visibleSources(player,session.station()).stream().map(s->s.block().getBlockPos().asLong()).toList())
                    ||!estimateDeliveries.stream().allMatch(ServiceTransactions::incoming))clearEstimate();
            estimate();
        }
        var candidates=candidates();List<BulkProtocol.Line> rows=new ArrayList<>();
        int total=0;
        if(view==0) {
            // Discovery pages are independent of the 64 selected guards. Only format the visible page.
            var shown=new LinkedHashMap<UUID,Guard>();for(var g:candidates)shown.put(g.getUUID(),g);
            for(var id:selected){var g=guard(id);if(eligible(g))shown.putIfAbsent(id,g);}
            total=shown.size();page=Math.min(page,Math.max(0,(total-1)/5));
            for(var g:shown.values().stream().skip((long)page*5).limit(5).toList())
                rows.add(new BulkProtocol.Line(g.getUUID(),name(g),cut(g.getUUID()+"\n"+describe(GuardEquipmentServiceAccess.get(g).settings())+"\nService: "+deferred(g,GuardEquipmentServiceAccess.get(g).settings()),1024),selected.contains(g.getUUID()),squad(g)!=null));
        } else if(preview!=null&&view==1) {
            for(var e:preview.entries())rows.add(new BulkProtocol.Line(e.guard().getUUID(),name(e.guard()),cut(e.guard().getUUID()+"\nBefore: "+describe(e.expected())+"\nAfter: "+describe(e.requested())+"\nService: "+deferred(e.guard(),e.requested()),1024),true,false));
        } else if(preview!=null)rows.addAll(needs);
        var settings=selected.stream().map(this::guard).filter(this::eligible).map(g->GuardEquipmentServiceAccess.get(g).settings()).toList();
        var profiles=settings.stream().map(s->s.profile()==null?"None":s.profile().toString()).distinct().toList();
        if(view!=0)total=rows.size();
        int pages=Math.max(1,(total+4)/5);page=Math.min(page,pages-1);int start=page*5;
        return new BulkProtocol.State(menu,session.id(),revision,view,page,pages,selected.size(),excluded,limited,
                choices.policy(),choices.profile()==null?"":choices.profile().toString(),choices.replacement(),choices.assignment(),
                mixed(settings.stream().map(s->s.policy().ordinal()).toList()),profiles.isEmpty()?"":profiles.size()>1?"Mixed":profiles.get(0),
                mixed(settings.stream().map(s->s.replaceExisting()?1:0).toList()),mixed(settings.stream().map(s->s.assignment()==null?0:1).toList()),
                preview==null?null:preview.token(),preview==null?0:preview.expires(),status,applied,
                preview!=null&&(estimatePending||estimatedAt==Long.MIN_VALUE||now()-estimatedAt>=20),
                view==0?rows:rows.subList(start,Math.min(rows.size(),start+5)));
    }
}
