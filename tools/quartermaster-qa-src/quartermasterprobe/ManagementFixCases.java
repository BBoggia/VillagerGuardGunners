package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.common.util.FakePlayer;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Packaged regressions for the complete-expansion review findings. */
final class ManagementFixCases {
    private static Guard dimensionGuard;
    private final List<String> checks=new ArrayList<>();
    private void check(String name,boolean pass) {
        System.out.println("VGG management-fix "+name+"="+pass);
        if(!pass)throw new AssertionError("management-fix-"+name);checks.add("management-fix-"+name);
    }
    private static void advance(ServerLevel level,int ticks){((ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+ticks);}
    private static final class Editor {
        final BulkSession bulk;final ManagementSession session;int sequence,revision;
        Editor(FakePlayer player,QuartermasterBlockEntity station) {
            session=new ManagementSession(player,station,null);bulk=new BulkSession(player,session);act(BulkProtocol.OPEN,0);
        }
        boolean act(int op,int value){return bulk.act(new BulkProtocol.Action(1,session.id(),++sequence,op,value,null,null));}
        boolean select(UUID id){return bulk.act(new BulkProtocol.Action(1,session.id(),++sequence,BulkProtocol.TOGGLE,0,id,null));}
        BulkProtocol.State state(){return bulk.state(1,++revision);}
        boolean apply(){return bulk.act(new BulkProtocol.Action(1,session.id(),++sequence,BulkProtocol.APPLY,0,null,state().token()));}
    }
    static List<String> run(ServerLevel level,QuartermasterBlockEntity station,FakePlayer original) throws Exception {
        var test=new ManagementFixCases();test.cases(level,station,original);return test.checks;
    }
    static List<String> dimensionCheck(ServerLevel level,QuartermasterBlockEntity station,FakePlayer owner) {
        var test=new ManagementFixCases();var guard=dimensionGuard;
        try {
            test.check("other-dimension-fixture-is-loaded",guard!=null&&guard.level()!=level&&((ServerLevel)guard.level()).getEntity(guard.getUUID())==guard);
            var context=ServiceManagementPermissions.Context.of(station);
            GuardEquipmentServiceAccess.get(guard).assignment(new ServiceStationAssignment(station.stationId(),level.dimension().location(),((BlockEntity)station).getBlockPos()));
            test.check("other-dimension-record-has-manageable-station",ServiceRoster.get(level).find(guard.getUUID())!=null&&ServiceManagementPermissions.station(owner,station,context).manage());
            test.check("loaded-other-dimension-cannot-be-forgotten",!ServiceControls.forgetUnavailable(owner,station,context,guard.getUUID())&&ServiceRoster.get(level).find(guard.getUUID())!=null);
            return test.checks;
        } finally {cleanup(level);}
    }
    static void cleanup(ServerLevel level) {
        if(dimensionGuard!=null){dimensionGuard.discard();dimensionGuard=null;}
        level.getServer().getLevel(net.minecraft.world.level.Level.NETHER).setChunkForced(0,0,false);
    }
    private void cases(ServerLevel level,QuartermasterBlockEntity station,FakePlayer original) throws Exception {
        // Other fixtures restore a synthetic world clock; isolate their player cooldowns.
        var owner=new FakePlayer(level,new GameProfile(original.getUUID(),"FixOwner"));owner.setPos(original.position());
        var guards=new ArrayList<Guard>();var editors=new ArrayList<Editor>();var peers=new ArrayList<FakePlayer>();
        long time=level.getGameTime();int access=station.guardAccess();var position=((BlockEntity)station).getBlockPos();
        var context=ServiceManagementPermissions.Context.of(station);
        var assignment=new ServiceStationAssignment(station.stationId(),level.dimension().location(),position);
        var stockBefore=((BlockEntity)station).saveWithFullMetadata();
        try {
            for(int i=0;i<100;i++) {
                var g=GuardEntityType.GUARD.get().create(level);g.setUUID(new UUID(2,i+1));g.setPos(.5,80,3.5);g.setNoAi(true);g.setOwnerId(owner.getUUID());
                level.addFreshEntity(g);GuardEquipmentServiceAccess.get(g).assignment(assignment);guards.add(g);
            }
            var e=new Editor(owner,station);editors.add(e);
            var visible=new HashSet<UUID>();int pages=e.state().pages();
            for(int p=0;p<pages;p++){e.act(BulkProtocol.PAGE,p);e.state().rows().forEach(r->visible.add(r.guard()));}
            check("all-100-assigned-guards-paginate",pages>13&&guards.stream().allMatch(g->visible.contains(g.getUUID())));
            for(int batch=0;batch<2;batch++) {
                int first=batch==0?0:64,end=batch==0?64:100;e.act(BulkProtocol.CLEAR,0);
                boolean selected=true;for(int i=first;i<end;i++)selected&=e.select(guards.get(i).getUUID());
                check("batch-"+batch+"-selection",selected&&e.state().selected()==end-first);
                if(batch==0)check("selection-still-stops-at-64",!e.select(guards.get(64).getUUID()));
                e.act(BulkProtocol.POLICY,1);advance(level,25);
                check("batch-"+batch+"-preview",e.act(BulkProtocol.PREVIEW,0));
                check("batch-"+batch+"-apply-without-waiting-for-stock-estimate",e.apply());
            }
            check("all-100-restock-after-successive-batches",guards.stream().allMatch(g->GuardEquipmentServiceAccess.get(g).policy()==GuardServiceState.Policy.RESTOCK));
            var guard=guards.get(0);var state=GuardEquipmentServiceAccess.get(guard);
            while(station.guardAccess()!=0)station.changeSetting(owner,3);
            var manual=new ServiceSettings(GuardServiceState.Policy.MANUAL,null,false,assignment);
            check("manual-keeps-revoked-assignment",ServiceControls.applyAtStation(owner,station,context,guard,manual)==ServiceManagementPermissions.Result.ALLOWED&&state.assignment().equals(assignment));
            check("revoked-service-cannot-be-reenabled",ServiceControls.applyAtStation(owner,station,context,guard,new ServiceSettings(GuardServiceState.Policy.RESTOCK,null,false,assignment))==ServiceManagementPermissions.Result.SERVICE_DENIED);
            state.assignment(null);
            check("manual-does-not-authorize-new-revoked-assignment",ServiceControls.applyAtStation(owner,station,context,guard,manual)==ServiceManagementPermissions.Result.SERVICE_DENIED);
            state.assignment(assignment);QuartermasterServiceRuntime.removed(station);
            check("manual-retains-unresolved-existing-assignment",ServiceControls.applyAtStation(owner,station,context,guard,manual)==ServiceManagementPermissions.Result.ALLOWED);
            QuartermasterServiceRuntime.loaded(station);
            while(station.guardAccess()!=access)station.changeSetting(owner,3);
            state.policy(GuardServiceState.Policy.RESTOCK);

            var gun=com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).build();
            var ammo=com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(1).build();
            var armor=List.of(net.minecraft.world.item.Items.IRON_HELMET,net.minecraft.world.item.Items.IRON_CHESTPLATE,net.minecraft.world.item.Items.IRON_LEGGINGS,net.minecraft.world.item.Items.IRON_BOOTS);
            for(int n=0;n<64;n++) {
                var g=guards.get(n);var item=gun.copy();item.setHoverName(net.minecraft.network.chat.Component.literal("Rifle "+n));ServiceEquipment.set(g,0,item);
                for(int slot=2;slot<6;slot++){item=new net.minecraft.world.item.ItemStack(armor.get(slot-2));item.setDamageValue(n+slot);ServiceEquipment.set(g,slot,item);}
                for(int slot=6;slot<9;slot++){item=ammo.copy();item.setHoverName(net.minecraft.network.chat.Component.literal("Reserve "+n+" / "+slot));ServiceEquipment.set(g,slot,item);}
            }
            e.bulk.close();editors.clear();
            for(int viewer=0;viewer<4;viewer++) {
                var peer=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"FixViewer"+viewer));peer.setPos(owner.position());peers.add(peer);
                level.getServer().getPlayerList().op(peer.getGameProfile());peer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,2000));
                var editor=new Editor(peer,station);editors.add(editor);
                for(int i=0;i<64;i++)editor.select(guards.get(i).getUUID());
                long start=System.nanoTime();check("varied-preview-queued-"+viewer,editor.act(BulkProtocol.PREVIEW,0));
                System.out.println("VGG management-fix varied-enqueue-ms="+(System.nanoTime()-start)/1_000_000.0);
                check("varied-preview-starts-refreshing-"+viewer,editor.state().refreshing());
            }
            var inventories=guards.stream().map(ServiceEquipment::snapshot).toList();
            var tokens=editors.stream().map(editor->editor.state().token()).toList();
            // Add an ordinary overview concurrently; it must share the same sixteen-unit budget.
            QuartermasterManagement.open(owner,station,null);var overview=(QuartermasterManagementMenu)owner.containerMenu;((AbstractContainerMenu)overview).broadcastChanges();
            int work=0,steps=0;long peak=0;
            while(editors.stream().anyMatch(editor->editor.state().refreshing())&&steps++<40) {
                long start=System.nanoTime();OverviewRuntime.advance(level);peak=Math.max(peak,System.nanoTime()-start);
                work+=OverviewRuntime.lastWork(level);check("shared-budget-"+steps,OverviewRuntime.lastWork(level)<=16);
            }
            System.out.println("VGG management-fix four-varied-editors steps="+steps+" work="+work+" peak-budget-ms="+peak/1_000_000.0);
            check("four-varied-previews-complete",editors.stream().noneMatch(editor->editor.state().refreshing())&&steps>=16);
            ((AbstractContainerMenu)overview).broadcastChanges();check("overview-is-not-starved",overview.details()!=null&&overview.details().generation()>0);owner.closeContainer();
            check("estimates-preserve-tokens",java.util.stream.IntStream.range(0,4).allMatch(i->tokens.get(i).equals(editors.get(i).state().token())));
            check("estimates-preserve-items",java.util.stream.IntStream.range(0,guards.size()).allMatch(i->ServiceEquipment.same(guards.get(i),inventories.get(i)))&&stockBefore.equals(((BlockEntity)station).saveWithFullMetadata()));
            advance(level,25);for(var editor:editors)check("automatic-refresh-queued",editor.state().refreshing());
            // Changed settings while a job is in flight must invalidate its token and discard work.
            OverviewRuntime.advance(level);state.replaceExisting(true);OverviewRuntime.advance(level);
            check("settings-change-cancels-pending-estimates",editors.stream().allMatch(editor->editor.state().token()==null));
            state.replaceExisting(false);
            for(var editor:editors){advance(level,25);editor.act(BulkProtocol.PREVIEW,0);editor.bulk.close();}
            OverviewRuntime.advance(level);check("closing-editors-removes-pending-work",OverviewRuntime.lastWork(level)==0);

            var armoryPos=new BlockPos(10,80,0);
            var sourceViewer=new FakePlayer(level,new GameProfile(owner.getUUID(),"SourceViewer"));sourceViewer.setPos(3.5,80,1.5);
            Editor sources=null;
            try {
                level.setBlockAndUpdate(armoryPos,com.gamergaming.villagerguardgunners.init.ModBlocks.ARMORY.get().defaultBlockState());
                var armory=(com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity)level.getBlockEntity(armoryPos);QuartermasterServiceRuntime.loaded(armory);
                var stock=ammo.copy();stock.setCount(50);armory.getInventory().setStackInSlot(0,stock);station.changeSetting(owner,4);
                check("bulk-visible-armory-fixture",ServiceSurvey.visibleSources(sourceViewer,station).size()==2);
                sources=new Editor(sourceViewer,station);for(int i=0;i<64;i++)sources.select(guards.get(i).getUUID());
                check("bulk-visible-source-preview",sources.act(BulkProtocol.PREVIEW,0));var token=sources.state().token();sources.act(BulkProtocol.VIEW,2);
                OverviewRuntime.advance(level);sourceViewer.setPos(.5,80,1.5);
                var hidden=sources.state();check("bulk-source-change-clears-pending-rows",hidden.refreshing()&&hidden.rows().isEmpty()&&token.equals(hidden.token())&&ServiceSurvey.visibleSources(sourceViewer,station).size()==1);
                advance(level,25);for(int i=0;i<20&&sources.state().refreshing();i++)OverviewRuntime.advance(level);
                check("bulk-retry-excludes-hidden-stock",!sources.state().refreshing()&&!sources.state().rows().isEmpty()&&sources.state().rows().stream().allMatch(r->r.detail().contains("available: 0;")));
                sourceViewer.setPos(3.5,80,1.5);check("bulk-expanded-visibility-clears-old-estimate",sources.state().rows().isEmpty()&&token.equals(sources.state().token()));
                advance(level,25);sources.state();OverviewRuntime.advance(level);armory.getInventory().getStackInSlot(0).shrink(20);
                for(int i=0;i<30&&sources.state().refreshing();i++)OverviewRuntime.advance(level);
                check("bulk-changing-stock-retries-without-changing-settings-token",!sources.state().refreshing()&&token.equals(sources.state().token())&&armory.getInventory().getStackInSlot(0).getCount()==30);
            } finally {
                if(sources!=null)sources.bulk.close();station.changeSetting(owner,5);level.removeBlock(armoryPos,false);
            }

            // Last-seen records are forgettable only while their entities are genuinely absent.
            for(var g:guards)g.discard();guards.clear();
            var unavailable=GuardEntityType.GUARD.get().create(level);unavailable.setPos(.5,80,3.5);unavailable.setNoAi(true);unavailable.setOwnerId(owner.getUUID());
            level.addFreshEntity(unavailable);GuardEquipmentServiceAccess.get(unavailable).assignment(assignment);
            UUID absent=unavailable.getUUID();unavailable.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            check("unload-retains-last-seen-record",ServiceRoster.get(level).find(absent)!=null&&level.getEntity(absent)==null);
            advance(level,25);QuartermasterManagement.open(owner,station,null);var menu=(QuartermasterManagementMenu)owner.containerMenu;
            ((AbstractContainerMenu)menu).broadcastChanges();for(int i=0;i<6;i++)OverviewRuntime.advance(level);((AbstractContainerMenu)menu).broadcastChanges();
            check("roster-navigation",menu.act(owner,menu.request(ManagementProtocol.GUARDS)));
            var row=menu.details().rows().stream().filter(r->absent.equals(r.guard())).findFirst().orElseThrow();
            check("unavailable-row-offers-forget-action",row.selection()>=0);
            advance(level,5);
            var stale=new ManagementProtocol.Action(((AbstractContainerMenu)menu).containerId,menu.sessionId(),100,ManagementProtocol.FORGET,0,row.selection(),0);
            check("stale-generation-cannot-forget",!menu.act(owner,stale)&&ServiceRoster.get(level).find(absent)!=null);
            advance(level,5);
            var forget=new ManagementProtocol.Action(((AbstractContainerMenu)menu).containerId,menu.sessionId(),101,ManagementProtocol.FORGET,0,row.selection(),menu.details().generation());
            check("menu-forgets-unavailable-record",menu.act(owner,forget)&&ServiceRoster.get(level).find(absent)==null);
            advance(level,5);check("forget-packet-cannot-be-replayed",!menu.act(owner,forget));owner.closeContainer();
            var returned=GuardEntityType.GUARD.get().create(level);returned.setUUID(absent);returned.setPos(.5,80,3.5);returned.setNoAi(true);returned.setOwnerId(owner.getUUID());level.addFreshEntity(returned);guards.add(returned);
            GuardEquipmentServiceAccess.get(returned).assignment(assignment);
            check("returning-guard-restores-record",ServiceRoster.get(level).find(absent)!=null);
            check("loaded-guard-cannot-be-forgotten",!ServiceControls.forgetUnavailable(owner,station,context,absent));
            var stranger=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"FixStranger"));stranger.setPos(owner.position());
            returned.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            check("stranger-cannot-forget-record",!ServiceControls.forgetUnavailable(stranger,station,context,absent));
            var otherLevel=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            // Entity tracking becomes available on a later native tick in this destination.
            // Keep the actor above Nether terrain and verify the refusal after tracking settles.
            var elsewhere=GuardEntityType.GUARD.get().create(otherLevel);dimensionGuard=elsewhere;elsewhere.setUUID(absent);elsewhere.setPos(.5,140,3.5);elsewhere.setNoAi(true);elsewhere.setNoGravity(true);
            GuardEquipmentServiceAccess.get(elsewhere).assignment(assignment);check("other-dimension-fixture-added",otherLevel.addFreshEntity(elsewhere));
            ServiceRoster.get(level).forget(absent);
        } finally {
            owner.closeContainer();for(var editor:editors)editor.bulk.close();for(var guard:guards)guard.discard();
            for(var peer:peers){peer.closeContainer();level.getServer().getPlayerList().deop(peer.getGameProfile());}
            while(station.guardAccess()!=access)station.changeSetting(owner,3);
            ((ServerLevelData)level.getLevelData()).setGameTime(time);
        }
    }
}
