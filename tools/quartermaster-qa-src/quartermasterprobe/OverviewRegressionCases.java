package quartermasterprobe;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.builder.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import tallestegg.guardvillagers.GuardEntityType;

/** Packaged regressions for source visibility, incoming deliveries and cache scope changes. */
final class OverviewRegressionCases {
    private static void check(List<String> cases,String name,boolean result) {
        System.out.println("VGG regression "+name+"="+result);
        if(!result)throw new AssertionError("regression-"+name);
        cases.add("regression-"+name);
    }
    static List<String> run(ServerLevel level,QuartermasterBlockEntity station,FakePlayer owner) {
        var cases=new ArrayList<String>();
        var guard=GuardEntityType.GUARD.get().create(level);
        guard.setPos(.5,80,2.5);guard.setOwnerId(owner.getUUID());guard.setNoAi(true);level.addFreshEntity(guard);
        var peer=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AuditPeer"));
        peer.setPos(owner.position());level.getServer().getPlayerList().op(peer.getGameProfile());
        peer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,1000));
        var extraGuards=new ArrayList<tallestegg.guardvillagers.entities.Guard>();
        var armoryPos=new net.minecraft.core.BlockPos(10,80,0);
        var state=GuardEquipmentServiceAccess.get(guard);
        try {
            guard.setItemSlot(EquipmentSlot.MAINHAND,GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).build());
            var data=IGunOperator.fromLivingEntity(guard).getDataHolder();data.drawTimestamp=-1;data.shootTimestamp=-1;data.meleePrepTickCount=-1;
            state.policy(GuardServiceState.Policy.RESTOCK);
            state.assignment(new ServiceStationAssignment(station.stationId(),level.dimension().location(),((net.minecraft.world.level.block.entity.BlockEntity)station).getBlockPos()));
            station.inventory().setStackInSlot(0,AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(60).build());
            var plan=ServicePlanner.plan(guard,station,QuartermasterServiceRuntime.sources(station));
            System.out.println("VGG regression setup plan="+plan+" guard="+ServiceEligibility.guard(guard)+" station="+ServiceEligibility.station(station)+" plannerStatus="+state.status);
            check(cases,"delivery-reserves-60-rounds",plan!=null&&plan.replacement().getCount()==60&&QuartermasterServiceRuntime.reserve(plan));
            var survey=new ServiceSurvey(owner,station);check(cases,"reserved-guard-captured",survey.add(guard));
            var need=survey.finish().guards().get(0).rows().stream().filter(r->r.category().equals("ammo")&&r.slot()==6).findFirst().orElseThrow();
            System.out.println("VGG regression in-flight requested="+need.requested()+" available="+need.available()+" missing="+need.missing()+" reserved="+plan.replacement().getCount());
            check(cases,"in-flight-supply-reduces-shortage",need.requested()==64&&need.incoming()==60&&need.missing()==4&&need.available()==0);
            var savedStation=((net.minecraft.world.level.block.entity.BlockEntity)station).saveWithFullMetadata();
            var savedEquipment=ServiceEquipment.snapshot(guard);var savedStatus=state.status;
            var request=List.of(new ServiceAssessment.Request(guard.getUUID(),null));
            var context=ServiceManagementPermissions.Context.of(station);
            var preview=ServiceAssessment.preview(owner,station,context,request);
            check(cases,"preview-includes-own-incoming",preview.guards().get(0).rows().stream().anyMatch(r->r.incoming()==60&&r.missing()==4));
            check(cases,"incoming-report-does-not-mutate",savedStation.equals(((net.minecraft.world.level.block.entity.BlockEntity)station).saveWithFullMetadata())
                    &&ServiceEquipment.same(guard,savedEquipment)&&state.status==savedStatus&&QuartermasterServiceRuntime.plan(guard)==plan);
            var proposed=new ServiceSettings(GuardServiceState.Policy.RESTOCK,null,true,state.assignment());
            check(cases,"proposed-settings-do-not-reuse-current-delivery",ServiceAssessment.preview(owner,station,context,List.of(new ServiceAssessment.Request(guard.getUUID(),proposed))).guards().get(0).rows().stream().allMatch(r->r.incoming()==0));
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+20);
            QuartermasterManagement.open(owner,station,null);
            var deliveryMenu=(QuartermasterManagementMenu)owner.containerMenu;
            ((AbstractContainerMenu)deliveryMenu).broadcastChanges();OverviewRuntime.advance(level);((AbstractContainerMenu)deliveryMenu).broadcastChanges();
            deliveryMenu.act(owner,deliveryMenu.request(ManagementProtocol.NEEDS));
            check(cases,"overview-reports-incoming-separately",deliveryMenu.details().rows().stream().anyMatch(r->r.requested()==64&&r.available()==0&&r.incoming()==60&&r.missing()==4));
            owner.closeContainer();
            long time=level.getGameTime();
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(plan.expiresAt());
            check(cases,"expired-delivery-invalidates-survey",!survey.current());
            var expired=new ServiceSurvey(owner,station);expired.add(guard);
            check(cases,"expired-delivery-is-not-incoming",expired.finish().guards().get(0).rows().stream().allMatch(r->r.incoming()==0));
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(time);
            station.inventory().getStackInSlot(0).shrink(1);
            var changed=new ServiceSurvey(owner,station);changed.add(guard);
            check(cases,"changed-stock-is-not-incoming",changed.finish().guards().get(0).rows().stream().allMatch(r->r.incoming()==0));
            station.inventory().getStackInSlot(0).grow(1);
            long receipt=state.receipt.revision();state.receipt.revision(receipt+1);
            check(cases,"read-only-incoming-check-does-not-quarantine",!ServiceTransactions.incoming(plan)&&!state.receipt.locked());state.receipt.revision(receipt);
            QuartermasterServiceRuntime.cancel(guard,ServiceStatus.READY);
            check(cases,"canceled-delivery-invalidates-survey",!survey.current());
            var canceled=new ServiceSurvey(owner,station);canceled.add(guard);
            check(cases,"canceled-delivery-is-not-incoming",canceled.finish().guards().get(0).rows().stream().allMatch(r->r.incoming()==0));
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+40);
            check(cases,"owner-open",QuartermasterManagement.open(owner,station,null));
            var menu=(QuartermasterManagementMenu)owner.containerMenu;
            ((AbstractContainerMenu)menu).broadcastChanges();OverviewRuntime.advance(level);((AbstractContainerMenu)menu).broadcastChanges();
            int a=menu.details().generation();check(cases,"first-scope-cached",a>0&&!menu.details().rows().isEmpty());
            state.status=ServiceStatus.FIGHTING;
            check(cases,"peer-open",QuartermasterManagement.open(peer,station,null));
            var other=(QuartermasterManagementMenu)peer.containerMenu;
            ((AbstractContainerMenu)other).broadcastChanges();OverviewRuntime.advance(level);((AbstractContainerMenu)other).broadcastChanges();
            int b=other.details().generation();check(cases,"second-scope-cached",b>a);
            // Equivalent cached scopes are reusable even while the requesting player's cooldown is active.
            OverviewRuntime.request(menu,owner,newSession(menu));((AbstractContainerMenu)menu).broadcastChanges();
            check(cases,"owner-observes-newer-scope",menu.details().generation()==b);
            state.status=ServiceStatus.READY;
            OverviewRuntime.request(menu,owner,newSession(menu));
            menu.act(owner,menu.request(ManagementProtocol.CLEAR_FILTER));
            check(cases,"return-to-cached-scope-keeps-valid-page",menu.details().generation()==a&&!menu.details().rows().isEmpty()&&!menu.details().refreshing());
            OverviewRuntime.request(menu,owner,newSession(menu));((AbstractContainerMenu)menu).broadcastChanges();
            check(cases,"same-cache-recovers-on-second-request",menu.details().generation()==a&&!menu.details().rows().isEmpty());
            owner.closeContainer();peer.closeContainer();
            level.setBlockAndUpdate(armoryPos,com.gamergaming.villagerguardgunners.init.ModBlocks.ARMORY.get().defaultBlockState());
            var armory=(com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity)level.getBlockEntity(armoryPos);
            QuartermasterServiceRuntime.loaded(armory);
            armory.getInventory().setStackInSlot(0,AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(50).build());
            station.changeSetting(owner,4);
            for(int i=0;i<20;i++) {
                var extra=GuardEntityType.GUARD.get().create(level);extra.setPos(.5,80,2.5);extra.setOwnerId(owner.getUUID());extra.setNoAi(true);level.addFreshEntity(extra);
                var settings=GuardEquipmentServiceAccess.get(extra);settings.policy(GuardServiceState.Policy.RESTOCK);settings.assignment(state.assignment());extraGuards.add(extra);
            }
            for(int scenario=0;scenario<3;scenario++) {
                owner.closeContainer();peer.closeContainer();
                boolean expanding=scenario==0;
                owner.setPos(expanding?.5:3.5,80,1.5);peer.setPos(owner.position());
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+40);
                QuartermasterManagement.open(owner,station,null);menu=(QuartermasterManagementMenu)owner.containerMenu;
                QuartermasterManagement.open(peer,station,null);other=(QuartermasterManagementMenu)peer.containerMenu;
                check(cases,"shared-equivalent-start-"+scenario,OverviewRuntime.scope(owner,newSession(menu)).equals(OverviewRuntime.scope(peer,newSession(other))));
                ((AbstractContainerMenu)menu).broadcastChanges();((AbstractContainerMenu)other).broadcastChanges();OverviewRuntime.advance(level);
                if(scenario==2)owner.closeContainer();else owner.setPos(expanding?3.5:.5,80,1.5);
                for(int i=0;i<4;i++){OverviewRuntime.advance(level);check(cases,"handoff-budget-"+scenario+"-"+i,OverviewRuntime.lastWork(level)<=16);}
                ((AbstractContainerMenu)other).broadcastChanges();
                int displayed=other.details().rows().stream().mapToInt(OverviewProtocol.Row::requested).sum();
                int authorized=new ServiceSurvey(peer,station).stock().stream().mapToInt(ServiceSurvey.Stock::count).sum();
                System.out.println("VGG regression shared-scope scenario="+scenario+" displayed="+displayed+" authorized="+authorized);
                check(cases,"shared-retry-keeps-recipient-sources-"+scenario,displayed==authorized&&authorized==(expanding?60:110)&&!other.details().refreshing());
            }
            return cases;
        } finally {
            owner.closeContainer();peer.closeContainer();owner.setPos(.5,80,1.5);station.changeSetting(owner,5);level.removeBlock(armoryPos,false);
            level.getServer().getPlayerList().deop(peer.getGameProfile());
            for(var extra:extraGuards){QuartermasterServiceRuntime.cancel(extra,ServiceStatus.RETRY);ServiceRoster.get(level).forget(extra.getUUID());extra.discard();}
            QuartermasterServiceRuntime.cancel(guard,ServiceStatus.RETRY);ServiceRoster.get(level).forget(guard.getUUID());guard.discard();
            for(int i=0;i<36;i++)station.inventory().setStackInSlot(i,ItemStack.EMPTY);
            OverviewRuntime.advance(level);
        }
    }
    private static ManagementSession newSession(QuartermasterManagementMenu menu) {
        try {var f=QuartermasterManagementMenu.class.getDeclaredField("session");f.setAccessible(true);return (ManagementSession)f.get(menu);}
        catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
}
