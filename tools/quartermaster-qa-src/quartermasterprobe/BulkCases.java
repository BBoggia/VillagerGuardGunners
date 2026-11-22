package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.gamergaming.villagerguardgunners.squad.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Exercises the production bulk editor in an installed Forge server, without any inventory test doubles. */
final class BulkCases {
    private static int sequence;
    private static UUID session;
    private static BulkProtocol.Action action(int op,int value,UUID guard,UUID token){return new BulkProtocol.Action(1,session,++sequence,op,value,guard,token);}
    private static boolean act(BulkSession b,int op,int value){return b.act(action(op,value,null,null));}
    private static void select(BulkSession b,Guard g){if(!b.act(action(BulkProtocol.TOGGLE,0,g.getUUID(),null)))throw new AssertionError("select");}
    private static void advance(ServerLevel l){((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+25);}
    private static void check(List<String> cases,String name,boolean ok){System.out.println("VGG bulk "+name+"="+ok);if(!ok)throw new AssertionError("bulk-"+name);cases.add("bulk-"+name);}
    private static BulkSession editor(FakePlayer owner,QuartermasterBlockEntity station){var s=new ManagementSession(owner,station,null);session=s.id();var b=new BulkSession(owner,s);act(b,BulkProtocol.OPEN,0);return b;}
    static List<String> run(ServerLevel level,QuartermasterBlockEntity station,FakePlayer owner) {
        var cases=new ArrayList<String>();var guards=new ArrayList<Guard>();var savedTime=level.getGameTime();
        var stock=station.inventory().getStackInSlot(0).copy();
        var enemy=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);enemy.setPos(3,80,3);enemy.setNoAi(true);level.addFreshEntity(enemy);
        try {
            for(int i=0;i<4;i++){var g=GuardEntityType.GUARD.get().create(level);g.setPos(.5,80,3.5+i);g.setNoAi(true);g.setOwnerId(owner.getUUID());level.addFreshEntity(g);guards.add(g);}
            var a=guards.get(0);var b=guards.get(1);var denied=guards.get(2);var far=guards.get(3);
            denied.setOwnerId(UUID.randomUUID());far.setPos(70,80,0);
            var sa=GuardEquipmentServiceAccess.get(a);var sb=GuardEquipmentServiceAccess.get(b);sb.replaceExisting(true);
            var squad=UUID.randomUUID();SquadRegistrySavedData.get(level.getServer()).register(squad,"Bulk test",owner.getUUID(),level.getGameTime());
            for(var g:guards)GuardSquadAccess.setMembership(g,SquadMembership.assigned(squad));
            var editor=editor(owner,station);
            check(cases,"squad-explicit-loaded-snapshot",editor.act(action(BulkProtocol.SQUAD,0,a.getUUID(),null)));
            var state=editor.state(1,1);check(cases,"squad-excludes-unauthorized-and-distant",state.selected()==2&&state.excluded()==2);
            check(cases,"mixed-replacement-defaults-keep",state.currentReplacement()==-2&&state.replacement()==-1);
            act(editor,BulkProtocol.POLICY,1);act(editor,BulkProtocol.ASSIGN,1);
            var inventoryA=ServiceEquipment.snapshot(a);var inventoryB=ServiceEquipment.snapshot(b);var stationBefore=((net.minecraft.world.level.block.entity.BlockEntity)station).saveWithFullMetadata();
            check(cases,"preview-created",act(editor,BulkProtocol.PREVIEW,0));state=editor.state(1,2);var token=state.token();
            check(cases,"preview-lists-exact-identities",state.rows().size()==2&&state.rows().stream().map(BulkProtocol.Line::guard).collect(java.util.stream.Collectors.toSet()).equals(Set.of(a.getUUID(),b.getUUID())));
            check(cases,"preview-does-not-change-settings-or-stock",sa.policy()==GuardServiceState.Policy.MANUAL&&sb.policy()==GuardServiceState.Policy.MANUAL&&stationBefore.equals(((net.minecraft.world.level.block.entity.BlockEntity)station).saveWithFullMetadata()));
            station.inventory().setStackInSlot(0,ItemStack.EMPTY);advance(level);
            check(cases,"stock-only-change-keeps-token",editor.state(1,3).token().equals(token));
            check(cases,"missing-stock-still-applies",editor.act(action(BulkProtocol.APPLY,0,null,token))&&sa.policy()==GuardServiceState.Policy.RESTOCK&&sb.policy()==GuardServiceState.Policy.RESTOCK);
            check(cases,"mixed-replacement-preserved",!sa.replaceExisting()&&sb.replaceExisting()&&sa.assignment()!=null&&sb.assignment()!=null);
            check(cases,"no-remote-transfer-or-squad-change",ServiceEquipment.same(a,inventoryA)&&ServiceEquipment.same(b,inventoryB)&&GuardSquadAccess.isMemberOf(a,squad)&&GuardSquadAccess.isMemberOf(b,squad));
            check(cases,"token-single-use",!editor.act(action(BulkProtocol.APPLY,0,null,token)));
            advance(level);act(editor,BulkProtocol.POLICY,0);check(cases,"second-preview",act(editor,BulkProtocol.PREVIEW,0));token=editor.state(1,4).token();
            sb.replaceExisting(false);var rev=sa.settingsRevision();
            check(cases,"concurrent-edit-rejects-whole-batch",!editor.act(action(BulkProtocol.APPLY,0,null,token))&&sa.settingsRevision()==rev&&sa.policy()==GuardServiceState.Policy.RESTOCK);
            advance(level);act(editor,BulkProtocol.PREVIEW,0);token=editor.state(1,5).token();
            GuardSquadAccess.setMembership(b,null);
            check(cases,"membership-change-requires-new-preview",!editor.act(action(BulkProtocol.APPLY,0,null,token))&&sa.settingsRevision()==rev);
            advance(level);act(editor,BulkProtocol.PREVIEW,0);token=editor.state(1,6).token();b.setOwnerId(UUID.randomUUID());
            check(cases,"authority-change-rejects-whole-batch",!editor.act(action(BulkProtocol.APPLY,0,null,token))&&sa.settingsRevision()==rev);b.setOwnerId(owner.getUUID());
            advance(level);act(editor,BulkProtocol.PREVIEW,0);token=editor.state(1,7).token();b.setPos(80,80,0);
            check(cases,"range-change-rejects-whole-batch",!editor.act(action(BulkProtocol.APPLY,0,null,token))&&sa.settingsRevision()==rev);b.setPos(.5,80,4.5);
            advance(level);act(editor,BulkProtocol.PREVIEW,0);token=editor.state(1,8).token();
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+BulkSession.PREVIEW_TICKS);
            check(cases,"expiry-rejects-batch",!editor.act(action(BulkProtocol.APPLY,0,null,token))&&sa.settingsRevision()==rev);
            advance(level);act(editor,BulkProtocol.PREVIEW,0);token=editor.state(1,9).token();
            var field=EquipmentProfiles.class.getDeclaredField("revision");field.setAccessible(true);field.setLong(null,EquipmentProfiles.revision()+1);
            check(cases,"profile-reload-invalidates",!editor.act(action(BulkProtocol.APPLY,0,null,token))&&sa.settingsRevision()==rev);
            advance(level);act(editor,BulkProtocol.PREVIEW,0);token=editor.state(1,10).token();
            check(cases,"other-editor-cannot-reuse-token",!editor(owner,station).act(action(BulkProtocol.APPLY,0,null,token)));
            act(editor,BulkProtocol.REPLACE,1);check(cases,"choice-change-invalidates-token",!editor.act(action(BulkProtocol.APPLY,0,null,token)));
            act(editor,BulkProtocol.POLICY,2);advance(level);check(cases,"missing-profile-blocks-preview",!act(editor,BulkProtocol.PREVIEW,0)&&editor.state(1,11).status().equals("profile_missing"));
            act(editor,BulkProtocol.PROFILE,0);advance(level);check(cases,"explicit-loadout-preview",act(editor,BulkProtocol.PREVIEW,0));
            act(editor,BulkProtocol.VIEW,2);
            for(int i=0;i<20&&editor.state(1,12).refreshing();i++)OverviewRuntime.advance(level);
            check(cases,"supply-estimate-paginated",!editor.state(1,12).rows().isEmpty());
            act(editor,BulkProtocol.POLICY,1);station.changeSetting(owner,3);advance(level);
            check(cases,"service-permissions-block-preview",!act(editor,BulkProtocol.PREVIEW,0)&&editor.state(1,13).status().equals("service_denied"));
            station.changeSetting(owner,3);station.changeSetting(owner,3);station.changeSetting(owner,3);
            act(editor,BulkProtocol.CLEAR,0);select(editor,a);act(editor,BulkProtocol.POLICY,1);advance(level);
            check(cases,"single-guard-preview",act(editor,BulkProtocol.PREVIEW,0));token=editor.state(1,14).token();
            check(cases,"single-guard-apply",editor.act(action(BulkProtocol.APPLY,0,null,token))&&sa.replaceExisting());
            // An ordinary per-guard settings update by a second authorized player invalidates the token.
            var peer=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BulkPeer"));peer.setPos(owner.position());
            level.getServer().getPlayerList().op(peer.getGameProfile());peer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,1000));
            try {
                advance(level);act(editor,BulkProtocol.PREVIEW,0);token=editor.state(1,15).token();
                var next=new ServiceSettings(sa.policy(),sa.profile(),!sa.replaceExisting(),sa.assignment());
                check(cases,"second-player-can-edit-authorized-guard",ServiceControls.applyAtStation(peer,station,ServiceManagementPermissions.Context.of(station),a,next)==ServiceManagementPermissions.Result.ALLOWED);
                check(cases,"second-player-edit-invalidates-preview",!editor.act(action(BulkProtocol.APPLY,0,null,token)));
            } finally {level.getServer().getPlayerList().deop(peer.getGameProfile());peer.closeContainer();}
            // Pending deliveries are released by the same state update used by normal controls.
            a.setItemSlot(EquipmentSlot.MAINHAND,com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).build());
            var data=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(a).getDataHolder();data.drawTimestamp=-1;data.shootTimestamp=-1;data.meleePrepTickCount=-1;
            station.inventory().setStackInSlot(0,com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(60).build());
            var plan=ServicePlanner.plan(a,station,QuartermasterServiceRuntime.sources(station));
            check(cases,"delivery-can-be-reserved-before-bulk-edit",plan!=null&&QuartermasterServiceRuntime.reserve(plan));
            var equipmentBefore=ServiceEquipment.snapshot(a);act(editor,BulkProtocol.POLICY,0);advance(level);act(editor,BulkProtocol.PREVIEW,0);token=editor.state(1,16).token();
            check(cases,"bulk-cancels-old-reservation",editor.act(action(BulkProtocol.APPLY,0,null,token))&&QuartermasterServiceRuntime.plan(a)==null);
            check(cases,"cancel-leaves-physical-stock-and-equipment",station.inventory().getStackInSlot(0).getCount()==60&&ServiceEquipment.same(a,equipmentBefore));
            // Orders and combat defer equipment trips; they do not prevent configuring service.
            act(editor,BulkProtocol.POLICY,1);a.setTarget(enemy);advance(level);act(editor,BulkProtocol.PREVIEW,0);var combat=editor.state(1,17);
            check(cases,"combat-preview-reports-deferred",combat.token()!=null&&combat.rows().get(0).detail().toLowerCase(java.util.Locale.ROOT).contains("fight"));
            check(cases,"combat-apply-preserves-target",editor.act(action(BulkProtocol.APPLY,0,null,combat.token()))&&a.getTarget()==enemy);a.setTarget(null);
            a.setPatrolling(true);advance(level);act(editor,BulkProtocol.PREVIEW,0);token=editor.state(1,18).token();
            check(cases,"apply-preserves-patrol",editor.act(action(BulkProtocol.APPLY,0,null,token))&&a.isPatrolling());a.setPatrolling(false);
            advance(level);act(editor,BulkProtocol.PREVIEW,0);token=editor.state(1,19).token();a.discard();
            check(cases,"unloaded-guard-is-not-queued",!editor.act(action(BulkProtocol.APPLY,0,null,token)));
            // A large squad is bounded and reports every excluded loaded member.
            for(int i=0;i<66;i++){var g=GuardEntityType.GUARD.get().create(level);g.setPos(2.5,80,2.5);g.setNoAi(true);g.setOwnerId(owner.getUUID());level.addFreshEntity(g);guards.add(g);GuardSquadAccess.setMembership(g,SquadMembership.assigned(squad));}
            var large=editor(owner,station);check(cases,"large-squad-selects",large.act(action(BulkProtocol.SQUAD,0,guards.get(4).getUUID(),null)));
            var largeState=large.state(1,20);check(cases,"large-squad-bounded",largeState.selected()==64&&largeState.excluded()==4&&largeState.pages()>13);
            act(large,BulkProtocol.POLICY,1);advance(level);long began=System.nanoTime();check(cases,"full-batch-preview",act(large,BulkProtocol.PREVIEW,0));
            System.out.println("VGG bulk full-preview-ms="+(System.nanoTime()-began)/1_000_000.0);
            token=large.state(1,21).token();check(cases,"full-batch-apply",large.act(action(BulkProtocol.APPLY,0,null,token))&&large.state(1,22).applied()==64);
            var menuOwner=owner;QuartermasterManagement.open(menuOwner,station,null);var menu=(com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu)owner.containerMenu;
            var packet=menu.requestBulk(BulkProtocol.OPEN,0,null,null);check(cases,"menu-opens-bulk",menu.act(owner,packet));advance(level);
            check(cases,"menu-rejects-replayed-sequence",!menu.act(owner,packet));
            var otherSession=new BulkProtocol.Action(((net.minecraft.world.inventory.AbstractContainerMenu)menu).containerId,UUID.randomUUID(),packet.sequence()+1,BulkProtocol.CLEAR,0,null,null);
            check(cases,"menu-rejects-foreign-session",!menu.act(owner,otherSession));owner.closeContainer();
            var old=editor.state(1,15).token();act(editor,BulkProtocol.CLOSE,0);check(cases,"closed-editor-refuses-mutations",!act(editor,BulkProtocol.POLICY,0));
            return cases;
        } catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
        finally {
            enemy.discard();owner.closeContainer();for(var g:guards)g.discard();station.inventory().setStackInSlot(0,stock);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(savedTime);
        }
    }
}
