package com.gamergaming.villagerguardgunners.menu;

import com.gamergaming.villagerguardgunners.init.ModMenus;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/** Slotless interaction shell; all authority and navigation live on the server. */
public final class QuartermasterManagementMenu extends AbstractContainerMenu {
    private final Player viewer;
    private final ManagementSession session;
    private final UUID sessionId;
    private final ManagementProtocol.Gate gate=new ManagementProtocol.Gate();
    private ManagementProtocol.Summary summary;
    private int tab,revision,clientSequence,page;
    private OverviewProtocol.Page details;
    private BulkSession bulk;
    private BulkProtocol.State bulkState;
    private int pendingNavigation;
    public BulkProtocol.State bulkState(){return bulkState;}
    public BulkProtocol.Action requestBulk(int action,int value,UUID guard,UUID token) {
        int sequence=++clientSequence;
        if(action==BulkProtocol.OPEN||action==BulkProtocol.CLOSE)pendingNavigation=sequence;
        return new BulkProtocol.Action(containerId,sessionId,sequence,action,value,guard,token);
    }
    public boolean receive(BulkProtocol.Navigation value) {
        if(session!=null||value.menu()!=containerId||!value.session().equals(sessionId)||value.sequence()!=pendingNavigation)return false;
        pendingNavigation=0;return true;
    }
    public void receive(BulkProtocol.State value) {
        if(session==null&&value.menu()==containerId&&value.session().equals(sessionId)&&(bulkState==null||value.revision()>bulkState.revision()))bulkState=value;
    }
    public boolean act(ServerPlayer player,BulkProtocol.Action action) {
        if(player.containerMenu!=this||action.menu()!=containerId||!action.session().equals(sessionId)||!stillValid(player)||!session.manage())return false;
        if(!gate.accept(action.sequence(),ManagementProtocol.SUPPLIES,player.level().getGameTime())) {
            acknowledge(player,action);return false;
        }
        if(bulk==null)bulk=new BulkSession(player,session);
        boolean result=bulk.act(action);sync(player);acknowledge(player,action);return result;
    }
    private void acknowledge(ServerPlayer player,BulkProtocol.Action action) {
        if(action.action()==BulkProtocol.OPEN||action.action()==BulkProtocol.CLOSE)
            ManagementNetwork.send(player,new BulkProtocol.Navigation(containerId,sessionId,action.sequence(),bulk!=null&&bulk.active()));
    }
    public void estimateComplete(){nextSync=Long.MIN_VALUE;}
    @Override public void removed(Player player) {
        if(bulk!=null)bulk.close();super.removed(player);
    }
    private OverviewRuntime.Frame frame;
    private java.util.Set<UUID> filter;
    private String filterLabel,filterDetail;
    private long nextRefresh=Long.MIN_VALUE;
    public ServerPlayer viewer(){return (ServerPlayer)viewer;}
    public boolean active(ManagementSession expected){return session==expected&&viewer instanceof ServerPlayer player&&player.containerMenu==this&&stillValid(player);}
    public OverviewProtocol.Page details(){return details;}
    public void receive(OverviewProtocol.Page value) {
        if(session==null&&value.menu()==containerId&&value.session().equals(sessionId)&&(details==null||value.revision()>details.revision()))details=value;
    }
    public void complete(OverviewRuntime.Frame value) {
        if(frame!=null&&value.scope().equals(frame.scope())&&value.generation()<frame.generation())return;
        frame=value;nextSync=Long.MIN_VALUE;
        if(filterLabel!=null)filter=value.needs().stream().filter(r->r.label().equals(filterLabel)&&r.detail().equals(filterDetail)).findFirst()
                .map(r->value.affected().getOrDefault(r.selection(),java.util.Set.of())).orElse(java.util.Set.of());
    }
    public ManagementProtocol.Action request(int action,int page,int selection) {
        return new ManagementProtocol.Action(containerId,sessionId,++clientSequence,action,page,selection,details==null?0:details.generation());
    }
    private long nextSync=Long.MIN_VALUE;
    public QuartermasterManagementMenu(int id,Inventory inventory,UUID sessionId) {
        super(ModMenus.QUARTERMASTER_MANAGEMENT.get(),id);viewer=inventory.player;session=null;this.sessionId=sessionId;
    }
    public QuartermasterManagementMenu(int id,Inventory inventory,ManagementSession session) {
        super(ModMenus.QUARTERMASTER_MANAGEMENT.get(),id);viewer=inventory.player;this.session=session;sessionId=session.id();
    }
    public UUID sessionId(){return sessionId;}
    public ManagementProtocol.Summary summary(){return summary;}
    public ManagementProtocol.Action request(int action) {
        return new ManagementProtocol.Action(containerId,sessionId,++clientSequence,action,0);
    }
    public void receive(ManagementProtocol.Summary value) {
        if(session==null&&value.menu()==containerId&&value.session().equals(sessionId)&&(summary==null||value.revision()>summary.revision()))summary=value;
    }
    @Override public boolean stillValid(Player player) {return player==viewer&&(player.level().isClientSide||session!=null&&session.valid(player));}
    @Override public ItemStack quickMoveStack(Player player,int slot) {return ItemStack.EMPTY;}
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if(session!=null&&viewer instanceof ServerPlayer player&&stillValid(player)) {
            long now=player.level().getGameTime();
            if(session.stock()&&now>=nextRefresh){nextRefresh=now+20;OverviewRuntime.request(this,player,session);}
            if(now>=nextSync)sync(player);
        }
    }
    public boolean act(ServerPlayer player,ManagementProtocol.Action action) {
        if(player.containerMenu!=this||action.menu()!=containerId||!action.session().equals(sessionId)||!stillValid(player))return false;
        if(!gate.accept(action.sequence(),action.action(),player.level().getGameTime()))return false;
        switch(action.action()) {
            case ManagementProtocol.SUPPLIES,ManagementProtocol.GUARDS,ManagementProtocol.NEEDS,ManagementProtocol.SERVICE -> {
                if(!session.stock()||action.action()!=ManagementProtocol.SUPPLIES&&!session.manage())return false;
                var rows=frame==null?java.util.List.<OverviewProtocol.Row>of():frame.rows(action.action(),filter);
                int pages=Math.max(1,(rows.size()+ManagementProtocol.PAGE_SIZE-1)/ManagementProtocol.PAGE_SIZE);
                if(action.page()>=pages)return false;tab=action.action();page=action.page();
            }
            case ManagementProtocol.FILTER -> {
                if(!session.manage()||frame==null||action.generation()!=frame.generation()||!frame.scope().equals(OverviewRuntime.scope(player,session))
                        ||!frame.affected().containsKey(action.selection())||tab!=ManagementProtocol.NEEDS)return false;
                var row=frame.needs().get(action.selection());filterLabel=row.label();filterDetail=row.detail();filter=frame.affected().get(action.selection());tab=ManagementProtocol.GUARDS;page=0;
            }
            case ManagementProtocol.CLEAR_FILTER -> {filter=null;filterLabel=null;filterDetail=null;page=0;}
            case ManagementProtocol.FORGET -> {
                if(!session.manage()||tab!=ManagementProtocol.GUARDS||frame==null||action.generation()!=frame.generation()
                        ||!frame.scope().equals(OverviewRuntime.scope(player,session))||action.selection()<0||action.selection()>=frame.guards().size())return false;
                var row=frame.guards().get(action.selection());
                if(row.selection()!=action.selection()||row.guard()==null
                        ||!com.gamergaming.villagerguardgunners.quartermaster.service.ServiceControls.forgetUnavailable(player,session.station(),
                        com.gamergaming.villagerguardgunners.quartermaster.service.ServiceManagementPermissions.Context.of(session.station()),row.guard()))return false;
                frame=null;nextRefresh=Long.MIN_VALUE;
                player.displayClientMessage(QuartermasterManagement.text("forgotten"),true);
            }

            case ManagementProtocol.TRADE -> {
                boolean opened=QuartermasterManagement.trade(player,session);
                if(!opened&&player.containerMenu==this)sync(player);
                return opened;
            }
            case ManagementProtocol.STOCK -> {
                if(!session.stock()||!session.station().canInteract(player))return false;
                NetworkHooks.openScreen(player,session.station(),session.station().getBlockPos());
                return player.containerMenu instanceof QuartermasterMenu;
            }
            case ManagementProtocol.REFRESH -> {
                long now=player.level().getGameTime();
                if(now>=nextRefresh){nextRefresh=now+20;OverviewRuntime.request(this,player,session);}
            }
            default -> {return false;}
        }
        sync(player);return true;
    }
    private void sync(ServerPlayer player) {
        if(!stillValid(player))return;
        int occupied=-1,returns=-1,links=-1;
        if(session.stock()) {
            occupied=0;returns=0;
            for(int i=0;i<27;i++)if(!session.station().inventory().getStackInSlot(i).isEmpty())occupied++;
            for(int i=27;i<36;i++)if(session.station().inventory().getStackInSlot(i).isEmpty()
                    &&!com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime.reserved(session.station(),i))returns++;
            links=session.station().linkedArmories().size();
        }
        if(frame!=null&&(!frame.incomingCurrent()||!frame.scope().equals(OverviewRuntime.scope(player,session))))frame=null;
        var rows=frame==null?java.util.List.<OverviewProtocol.Row>of():frame.rows(tab,filter);
        int pages=Math.max(1,(rows.size()+ManagementProtocol.PAGE_SIZE-1)/ManagementProtocol.PAGE_SIZE);
        page=Math.min(page,pages-1);
        summary=new ManagementProtocol.Summary(containerId,sessionId,++revision,tab,page,pages,session.stock(),session.manage(),
                QuartermasterManagement.tradeStatus(player,session.operator(),session.station()),occupied,returns,links);
        nextSync=player.level().getGameTime()+20;
        ManagementNetwork.send(player,summary);
        int start=page*ManagementProtocol.PAGE_SIZE;
        details=new OverviewProtocol.Page(containerId,sessionId,revision,frame==null?0:frame.generation(),tab,page,pages,frame==null?0:frame.observedAt(),
                frame==null||player.level().getGameTime()-frame.observedAt()>=20,frame!=null&&frame.scope().truncated(),frame!=null&&frame.restricted(),session.stock(),session.manage(),
                frame==null?0:frame.inspected(),frame==null?0:frame.unavailable(),OverviewRuntime.stationStatus(session.station()).ordinal(),
                rows.subList(start,Math.min(rows.size(),start+ManagementProtocol.PAGE_SIZE)));
        ManagementNetwork.send(player,details);
        if(bulk!=null&&bulk.active())ManagementNetwork.send(player,bulk.state(containerId,revision));
    }
}
