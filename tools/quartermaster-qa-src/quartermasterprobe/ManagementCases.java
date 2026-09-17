package quartermasterprobe;

import com.gamergaming.villagerguardgunners.menu.*;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.server.level.ServerLevel;

final class ManagementCases {
    private final List<String> cases=new ArrayList<>();
    private void check(String name,boolean pass){System.out.println("VGG management "+name+"="+pass);if(!pass)throw new AssertionError(name);cases.add("management-"+name);}
    static List<String> run(ServerLevel level,QuartermasterBlockEntity station,Villager operator,FakePlayer owner) {
        var test=new ManagementCases();test.runCases(level,station,operator,owner);return test.cases;
    }
    private void runCases(ServerLevel level,QuartermasterBlockEntity station,Villager operator,FakePlayer owner) {
        var pos=((net.minecraft.world.level.block.entity.BlockEntity)station).getBlockPos();
        var originalOffers=new net.minecraft.world.item.trading.MerchantOffers();originalOffers.addAll(operator.getOffers());
        try {
            check("actual-job-site-resolved",QuartermasterManagement.station(operator)==station);
            UUID cached=station.operator;station.operator=UUID.randomUUID();
            check("cached-operator-not-authority",QuartermasterManagement.operator(station)==operator);station.operator=cached;
            var stockSession=new ManagementSession(owner,station,null);
            check("station-session-permissions",stockSession.valid(owner)&&stockSession.stock()&&stockSession.manage());
            var npcSession=new ManagementSession(owner,station,operator);
            check("npc-session-valid-without-trading",npcSession.valid(owner)&&!operator.isTrading());
            var visitor=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"ManagementVisitor"));visitor.setPos(owner.position());
            var guestSession=new ManagementSession(visitor,station,operator);
            check("visitor-trade-only-session",guestSession.valid(visitor)&&!guestSession.stock()&&!guestSession.manage());
            check("visitor-station-entry-refused",!new ManagementSession(visitor,station,null).valid(visitor));
            check("different-player-cannot-reuse-session",!npcSession.valid(visitor));
            float health=operator.getHealth();operator.setHealth(0);
            check("operator-death-invalidates",!npcSession.valid(owner));operator.setHealth(health);
            health=owner.getHealth();owner.setHealth(0);
            check("viewer-death-invalidates",!npcSession.valid(owner)&&!stockSession.valid(owner));owner.setHealth(health);
            level.setDayTime(15000);check("browsing-outside-work-hours",npcSession.valid(owner));level.setDayTime(3000);
            owner.setPos(15,80,0);check("distance-invalidates",!npcSession.valid(owner)&&!stockSession.valid(owner));owner.setPos(.5,80,1.5);
            operator.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);check("job-loss-invalidates",!npcSession.valid(owner));
            operator.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),pos));
            level.getPoiManager().release(pos);check("unclaimed-poi-invalidates",!npcSession.valid(owner));
            level.getPoiManager().take(t->t.value()==net.minecraftforge.registries.ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),(t,p)->p.equals(pos),pos,1);
            var data=operator.getVillagerData();operator.setVillagerData(data.setProfession(VillagerProfession.NONE));check("profession-loss-invalidates",!npcSession.valid(owner));operator.setVillagerData(data);
            operator.getBrain().setActiveActivityIfPossible(Activity.PANIC);check("panic-blocks-trade",QuartermasterManagement.tradeStatus(owner,operator,station)==ManagementProtocol.UNSAFE);
            operator.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            operator.setTradingPlayer(visitor);check("busy-villager-not-stolen",!npcSession.valid(owner)&&QuartermasterManagement.tradeStatus(owner,operator,station)==ManagementProtocol.BUSY);operator.setTradingPlayer(null);
            station.changeSetting(owner,0);station.changeSetting(owner,0);
            check("permission-change-invalidates",!guestSession.valid(visitor));station.changeSetting(owner,0);

            for(var item:List.of(Items.NAME_TAG,Items.LEAD,Items.VILLAGER_SPAWN_EGG)) {
                owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
                var event=new PlayerInteractEvent.EntityInteract(owner,InteractionHand.MAIN_HAND,operator);MinecraftForge.EVENT_BUS.post(event);
                check("held-item-preserved-"+item,event.isCanceled()==false);owner.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            }
            owner.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.NAME_TAG));
            var event=new PlayerInteractEvent.EntityInteract(owner,InteractionHand.MAIN_HAND,operator);MinecraftForge.EVENT_BUS.post(event);
            check("offhand-item-preserved",!event.isCanceled());owner.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            event=new PlayerInteractEvent.EntityInteract(owner,InteractionHand.OFF_HAND,operator);MinecraftForge.EVENT_BUS.post(event);check("offhand-does-not-open-second-menu",!event.isCanceled());
            owner.setShiftKeyDown(true);event=new PlayerInteractEvent.EntityInteract(owner,InteractionHand.MAIN_HAND,operator);MinecraftForge.EVENT_BUS.post(event);check("sneak-interaction-preserved",!event.isCanceled());owner.setShiftKeyDown(false);

            event=new PlayerInteractEvent.EntityInteract(owner,InteractionHand.MAIN_HAND,operator);MinecraftForge.EVENT_BUS.post(event);
            check("forge-interaction-opens-management",event.isCanceled()&&owner.containerMenu instanceof QuartermasterManagementMenu&&!operator.isTrading());
            var menu=(QuartermasterManagementMenu)owner.containerMenu;
            ((net.minecraft.world.inventory.AbstractContainerMenu)menu).broadcastChanges();check("owner-summary-authorized",menu.summary()!=null&&menu.summary().manage());
            var wrong=new ManagementProtocol.Action(((net.minecraft.world.inventory.AbstractContainerMenu)menu).containerId,UUID.randomUUID(),1,ManagementProtocol.STOCK,0);
            check("wrong-session-token-rejected",!menu.act(owner,wrong));
            var action=menu.request(ManagementProtocol.GUARDS);check("guard-tab-navigation",menu.act(owner,action)&&menu.summary().tab()==ManagementProtocol.GUARDS);
            check("replayed-action-rejected",!menu.act(owner,action));
            owner.closeContainer();check("closed-menu-action-rejected",!menu.act(owner,menu.request(ManagementProtocol.STOCK)));
            check("station-opens-management",QuartermasterManagement.open(owner,station,null));
            menu=(QuartermasterManagementMenu)owner.containerMenu;
            check("open-stock-native-menu",menu.act(owner,menu.request(ManagementProtocol.STOCK))&&owner.containerMenu instanceof QuartermasterMenu);
            check("stock-management-entry",owner.containerMenu.clickMenuButton(owner,6)&&owner.containerMenu instanceof QuartermasterManagementMenu);owner.closeContainer();

            check("visitor-npc-entry",QuartermasterManagement.open(visitor,station,operator));
            var guest=(QuartermasterManagementMenu)visitor.containerMenu;((net.minecraft.world.inventory.AbstractContainerMenu)guest).broadcastChanges();
            check("visitor-summary-hides-private-counts",!guest.summary().stock()&&!guest.summary().manage()&&guest.summary().occupied()==-1&&guest.summary().freeReturns()==-1&&guest.summary().links()==-1);
            check("visitor-stock-request-refused",!guest.act(visitor,guest.request(ManagementProtocol.STOCK)));visitor.closeContainer();
            operator.getOffers().add(new MerchantOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.BREAD),12,1,.05f));
            check("visitor-trade-session",QuartermasterManagement.open(visitor,station,operator));
            guest=(QuartermasterManagementMenu)visitor.containerMenu;
            check("native-merchant-route",guest.act(visitor,guest.request(ManagementProtocol.TRADE))&&visitor.containerMenu instanceof MerchantMenu&&operator.getTradingPlayer()==visitor);
            check("second-player-cannot-steal-trade",!QuartermasterManagement.open(owner,station,operator));visitor.closeContainer();
            check("closing-trade-releases-operator",operator.getTradingPlayer()==null);

            check("stale-placement-fixture-valid",stockSession.valid(owner));station.placedBy(owner);
            check("placement-change-invalidates",!stockSession.valid(owner)&&!npcSession.valid(owner));
        } finally {
            owner.closeContainer();operator.setTradingPlayer(null);operator.getOffers().clear();operator.getOffers().addAll(originalOffers);
            owner.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);owner.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);owner.setShiftKeyDown(false);
        }
    }
}
