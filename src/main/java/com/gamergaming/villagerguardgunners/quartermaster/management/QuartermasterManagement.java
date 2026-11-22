package com.gamergaming.villagerguardgunners.quartermaster.management;

import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/** Server entry and native trading. Browsing neither claims a POI nor marks a villager as trading. */
public final class QuartermasterManagement {
    public static Component text(String key,Object... args) {return Component.translatable("gui.villagerguardgunners.management."+key,args);}
    public static boolean profession(Villager v) {
        return v.isAlive()&&!v.isRemoved()&&!v.isBaby()
                &&v.getVillagerData().getProfession()==ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID);
    }
    public static QuartermasterBlockEntity station(Villager v) {
        if(!(v.level() instanceof ServerLevel level)||!profession(v))return null;
        var site=v.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if(site==null||!site.dimension().equals(level.dimension())||!level.hasChunkAt(site.pos())
                ||!ServiceEligibility.claimedStation(level,site.pos()))return null;
        return level.getBlockEntity(site.pos()) instanceof QuartermasterBlockEntity station&&!station.isRemoved()?station:null;
    }
    public static Villager operator(QuartermasterBlockEntity station) {
        if(!(station.getLevel() instanceof ServerLevel level))return null;
        Villager found=null;
        for(var v:level.getEntitiesOfClass(Villager.class,new AABB(station.getBlockPos()).inflate(4))) {
            if(station(v)!=station||v.distanceToSqr(station.getBlockPos().getCenter())>16)continue;
            if(found!=null)return null; // Ambiguous job-site memories cannot select a trader for the station.
            found=v;
        }
        return found;
    }
    public static int tradeStatus(ServerPlayer player,Villager villager,QuartermasterBlockEntity station) {
        if(villager==null||station(villager)!=station||player.distanceToSqr(villager)>16||!player.hasLineOfSight(villager))return ManagementProtocol.NO_OPERATOR;
        if(villager.isTrading())return ManagementProtocol.BUSY;
        if(villager.isSleeping()||villager.getBrain().isActive(Activity.PANIC)||villager.getBrain().isActive(Activity.RAID)
                ||((ServerLevel)villager.level()).getRaidAt(villager.blockPosition())!=null)return ManagementProtocol.UNSAFE;
        if(!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty()||player.isSecondaryUseActive())return ManagementProtocol.EMPTY_HAND;
        return ManagementProtocol.READY;
    }
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if(event.isCanceled()||event.getHand()!=InteractionHand.MAIN_HAND||!(event.getEntity() instanceof ServerPlayer player)
                ||!(event.getTarget() instanceof Villager villager)||player.isSecondaryUseActive()
                ||!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty())return;
        var station=station(villager);
        if(station!=null&&open(player,station,villager)) {
            event.setCancellationResult(InteractionResult.CONSUME);event.setCanceled(true);
        }
    }
    public static boolean open(ServerPlayer player,QuartermasterBlockEntity station,Villager npc) {
        var session=new ManagementSession(player,station,npc);
        if(!session.valid(player))return false;
        NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,viewer)->new QuartermasterManagementMenu(id,inventory,session),text("title")),
                data->data.writeUUID(session.id()));
        return player.containerMenu instanceof QuartermasterManagementMenu;
    }
    public static boolean trade(ServerPlayer player,ManagementSession session) {
        var villager=session.operator();
        if(!session.valid(player)||tradeStatus(player,villager,session.station())!=ManagementProtocol.READY)return false;
        // Direct native interaction retains pricing, reputation and merchant hooks; it does not repost EntityInteract.
        villager.mobInteract(player,InteractionHand.MAIN_HAND);
        if(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu)return true;
        player.displayClientMessage(text("no_offers"),true);
        return false;
    }
    private QuartermasterManagement() {}
}
