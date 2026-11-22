package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.builder.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayer;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

final class OverviewCases {
    private final List<String> cases=new ArrayList<>();
    private void check(String name,boolean ok){System.out.println("VGG overview "+name+"="+ok);if(!ok)throw new AssertionError(name);cases.add("overview-"+name);}
    static List<String> run(ServerLevel level,QuartermasterBlockEntity station,FakePlayer owner) {
        var test=new OverviewCases();var guards=new ArrayList<Guard>();
        var peer=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"OverviewPeer"));peer.setPos(owner.position());
        level.getServer().getPlayerList().op(peer.getGameProfile());peer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,1000));
        try {
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+20);
            var assignment=new ServiceStationAssignment(station.stationId(),level.dimension().location(),((net.minecraft.world.level.block.entity.BlockEntity)station).getBlockPos());
            for(int i=0;i<22;i++) {
                var guard=GuardEntityType.GUARD.get().create(level);guard.setPos(.5,80,2.5);guard.setNoAi(true);guard.setOwnerId(owner.getUUID());level.addFreshEntity(guard);
                guard.setItemSlot(EquipmentSlot.MAINHAND,GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).build());
                var data=IGunOperator.fromLivingEntity(guard).getDataHolder();data.drawTimestamp=-1;data.shootTimestamp=-1;data.meleePrepTickCount=-1;
                var state=GuardEquipmentServiceAccess.get(guard);state.policy(GuardServiceState.Policy.RESTOCK);if(i<21)state.assignment(assignment);guards.add(guard);
            }
            station.inventory().setStackInSlot(0,AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(60).build());
            station.inventory().setStackInSlot(1,AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(20).build());
            var direct=new ServiceSurvey(owner,station);test.check("incremental-first-guard",direct.add(guards.get(0)));test.check("incremental-stock-stable",direct.current());
            System.out.println("VGG overview scope="+OverviewRuntime.scope(owner,new ManagementSession(owner,station,null)).members().size());
            var before=((net.minecraft.world.level.block.entity.BlockEntity)station).saveWithFullMetadata();
            var states=guards.stream().map(g->GuardEquipmentServiceAccess.get(g).status).toList();
            test.check("same-permission-scope",OverviewRuntime.scope(owner,new ManagementSession(owner,station,null)).equals(OverviewRuntime.scope(peer,new ManagementSession(peer,station,null))));
            test.check("owner-entry",QuartermasterManagement.open(owner,station,null));var menu=(QuartermasterManagementMenu)owner.containerMenu;
            test.check("peer-entry",QuartermasterManagement.open(peer,station,null));var other=(QuartermasterManagementMenu)peer.containerMenu;
            ((AbstractContainerMenu)menu).broadcastChanges();((AbstractContainerMenu)other).broadcastChanges();
            long started=System.nanoTime();OverviewRuntime.advance(level);System.out.println("VGG overview budget="+OverviewRuntime.lastWork(level)+", nanos="+(System.nanoTime()-started));test.check("dimension-budget-sixteen",OverviewRuntime.lastWork(level)>0&&OverviewRuntime.lastWork(level)<=16);
            ((AbstractContainerMenu)menu).broadcastChanges();test.check("partial-is-refreshing",menu.details().refreshing());
            OverviewRuntime.advance(level);int work=OverviewRuntime.lastWork(level);
            ((AbstractContainerMenu)menu).broadcastChanges();((AbstractContainerMenu)other).broadcastChanges();
            System.out.println("VGG overview second work="+work+" owner="+menu.details().generation()+" peer="+other.details().generation());
            test.check("equivalent-viewers-share-captures",work>0&&work<16&&!menu.details().refreshing()&&menu.details().generation()==other.details().generation());
            test.check("stock-counts-reserved-aware",menu.details().rows().stream().mapToInt(OverviewProtocol.Row::available).sum()==80);
            test.check("view-leaves-inventory-and-status",before.equals(((net.minecraft.world.level.block.entity.BlockEntity)station).saveWithFullMetadata())&&states.equals(guards.stream().map(g->GuardEquipmentServiceAccess.get(g).status).toList()));
            test.check("needs-navigation",menu.act(owner,menu.request(ManagementProtocol.NEEDS)));
            var rows=menu.details().rows();int needed=rows.stream().filter(r->r.label().equals("tacz:762x39")).mapToInt(OverviewProtocol.Row::requested).sum();
            int available=rows.stream().mapToInt(OverviewProtocol.Row::available).sum();
            test.check("scarce-supplies-counted-once-across-batches",needed==21*64&&available==80);
            test.check("nearby-candidate-excluded-from-demand",needed!=22*64);
            var shortage=rows.stream().filter(r->r.missing()>0).findFirst().orElseThrow();
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+4);
            var wrong=new ManagementProtocol.Action(((AbstractContainerMenu)menu).containerId,menu.sessionId(),20,ManagementProtocol.FILTER,0,shortage.selection(),menu.details().generation()+1);
            test.check("stale-shortage-selection-refused",!menu.act(owner,wrong));
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+4);
            var select=new ManagementProtocol.Action(((AbstractContainerMenu)menu).containerId,menu.sessionId(),21,ManagementProtocol.FILTER,0,shortage.selection(),menu.details().generation());
            test.check("shortage-selects-affected-guards",menu.act(owner,select)&&menu.details().view()==ManagementProtocol.GUARDS&&menu.details().rows().size()==20&&menu.details().pages()==1);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+4);
            test.check("invalid-page-refused",!menu.act(owner,new ManagementProtocol.Action(((AbstractContainerMenu)menu).containerId,menu.sessionId(),22,ManagementProtocol.GUARDS,63)));
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+4);
            test.check("clear-filter",menu.act(owner,new ManagementProtocol.Action(((AbstractContainerMenu)menu).containerId,menu.sessionId(),23,ManagementProtocol.CLEAR_FILTER,0))&&menu.details().pages()==2);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+4);
            test.check("second-roster-page",menu.act(owner,new ManagementProtocol.Action(((AbstractContainerMenu)menu).containerId,menu.sessionId(),24,ManagementProtocol.GUARDS,1))&&menu.details().rows().size()>0);
            guards.get(0).setOwnerId(UUID.randomUUID());
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+4);
            test.check("permission-loss-clears-cached-rows",menu.act(owner,new ManagementProtocol.Action(((AbstractContainerMenu)menu).containerId,menu.sessionId(),25,ManagementProtocol.CLEAR_FILTER,0))&&menu.details().rows().isEmpty()&&menu.details().refreshing());
            guards.get(0).setOwnerId(owner.getUUID());
            owner.closeContainer();peer.closeContainer();OverviewRuntime.advance(level);test.check("idle-does-no-assessments",OverviewRuntime.lastWork(level)==0);
            test.check("changing-stock-refresh-entry",QuartermasterManagement.open(owner,station,null));
            var refreshing=(QuartermasterManagementMenu)owner.containerMenu;((AbstractContainerMenu)refreshing).broadcastChanges();OverviewRuntime.advance(level);
            station.inventory().getStackInSlot(1).shrink(1);
            for(int i=0;i<3;i++){OverviewRuntime.advance(level);test.check("retry-stays-in-budget-"+i,OverviewRuntime.lastWork(level)<=OverviewRuntime.BUDGET);}
            ((AbstractContainerMenu)refreshing).broadcastChanges();
            test.check("mid-scan-stock-change-restarts-and-finishes",!refreshing.details().refreshing()&&refreshing.details().rows().stream().mapToInt(OverviewProtocol.Row::available).sum()==79);
            owner.closeContainer();station.inventory().getStackInSlot(1).grow(1);
            var survey=new ServiceSurvey(owner,station);test.check("copied-stock-current",survey.current());station.inventory().getStackInSlot(1).shrink(1);
            test.check("external-stack-change-invalidates-survey",!survey.current());station.inventory().getStackInSlot(1).grow(1);
            var g=guards.get(0);int history=ServiceHistory.recent(level,station.dropId()).size();var plan=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
            test.check("actual-transfer",plan!=null&&ServiceTransactions.transfer(plan)==ServiceStatus.SUPPLIED);
            test.check("successful-transfer-recorded",ServiceHistory.recent(level,station.dropId()).size()==history+1);
            test.check("rejected-transfer-not-recorded",ServiceTransactions.transfer(plan)==ServiceStatus.RETRY&&ServiceHistory.recent(level,station.dropId()).size()==history+1);
            var armoryPos=new BlockPos(10,80,0);level.setBlockAndUpdate(armoryPos,ModBlocks.ARMORY.get().defaultBlockState());
            var armory=(com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity)level.getBlockEntity(armoryPos);QuartermasterServiceRuntime.loaded(armory);
            armory.getInventory().setStackInSlot(0,AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(50).build());station.changeSetting(owner,4);
            test.check("distant-linked-stock-hidden",new ServiceSurvey(owner,station).restricted()&&ServiceSurvey.visibleSources(owner,station).size()==1);
            station.changeSetting(owner,5);level.removeBlock(armoryPos,false);
            // Dead/unloaded metadata stays unavailable and does not contribute guessed demand.
            var missing=guards.get(1);var savedId=missing.getUUID();QuartermasterServiceRuntime.left(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(missing,level));
            missing.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var session=new ManagementSession(owner,station,null);var scope=OverviewRuntime.scope(owner,session);
            test.check("unloaded-record-never-has-current-equipment",scope.members().stream().anyMatch(m->m.id().equals(savedId)&&!m.loaded()));
            for(int i=0;i<45;i++) {
                var extra=GuardEntityType.GUARD.get().create(level);extra.setPos(.5,80,2.5);extra.setNoAi(true);extra.setOwnerId(owner.getUUID());level.addFreshEntity(extra);
                GuardEquipmentServiceAccess.get(extra).assignment(assignment);guards.add(extra);
            }
            var limited=OverviewRuntime.scope(owner,session);test.check("roster-cap-explicit",limited.truncated()&&limited.members().size()==64);
            return test.cases;
        } finally {
            owner.closeContainer();peer.closeContainer();level.getServer().getPlayerList().deop(peer.getGameProfile());
            for(var guard:guards){QuartermasterServiceRuntime.cancel(guard,ServiceStatus.RETRY);ServiceRoster.get(level).forget(guard.getUUID());guard.discard();}
            for(int i=0;i<36;i++)station.inventory().setStackInSlot(i,ItemStack.EMPTY);
            OverviewRuntime.advance(level);
        }
    }
}
