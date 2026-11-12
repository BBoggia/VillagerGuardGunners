package com.gamergaming.villagerguardgunners.qa;

import com.gamergaming.villagerguardgunners.ammunition.*;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.builder.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.common.entities.Guard;

/** Installed-JAR regression cases shared with the Forge observer through API-only substitutions. */
public final class AmmoServiceCases {
    private static void check(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
    private static ItemStack ammo(String id, int count) {
        return AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:"+id)).setCount(count).build();
    }
    public static List<String> run(ServerLevel level, QuartermasterBlockEntity station, Player owner) {
        var passed=new ArrayList<String>(); var savedMode=GuardInfiniteAmmo.mode();
        var stock=new ArrayList<ItemStack>();
        for(int i=0;i<station.inventory().getSlots();i++) { stock.add(station.inventory().getStackInSlot(i).copy()); station.inventory().setStackInSlot(i,ItemStack.EMPTY); }
        Guard g=GuardEntityType.GUARD.get().create(level);
        g.setPos(station.getBlockPos().getX()+.5,station.getBlockPos().getY(),station.getBlockPos().getZ()+1.5);
        g.setNoAi(true);g.setOwnerId(owner.getUUID());level.addFreshEntity(g);
        var data=IGunOperator.fromLivingEntity(g).getDataHolder();data.drawTimestamp=-1;data.shootTimestamp=-1;data.meleePrepTickCount=-1;
        var state=GuardEquipmentServiceAccess.get(g);state.policy(GuardServiceState.Policy.RESTOCK);
        var primary=GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).setAmmoCount(30).build(level.registryAccess());
        var sidearm=GunItemBuilder.create().setId(ResourceLocation.parse("tacz:glock_17")).setAmmoCount(17).build(level.registryAccess());
        g.setItemSlot(EquipmentSlot.MAINHAND,primary);check(com.gamergaming.villagerguardgunners.sidearm.GuardSidearmTransactions.configureNaturalSidearm(g,sidearm).configured(),"Could not configure test sidearm");
        var handler=GuardAmmoAccess.getAmmoHandler(g);
        try {
            for(var mode:List.of(InfiniteAmmoMode.WITH_RELOADING,InfiniteAmmoMode.WITHOUT_RELOADING)) {
                ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(InfiniteAmmoMode.OFF);
                station.inventory().setStackInSlot(0,ammo("762x39",12));
                var pending=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
                check(pending!=null&&pending.targetSlot()>=6&&pending.targetSlot()<9&&QuartermasterServiceRuntime.reserve(pending),"No pending ammo fixture");
                ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(mode);
                check(!ServiceTransactions.valid(pending)&&!ServiceTransactions.incoming(pending)&&ServiceTransactions.transfer(pending)==ServiceStatus.RETRY,"Pending ammo survived mode change: "+mode);
                var report=ServiceAssessment.preview(owner,station,ServiceManagementPermissions.Context.of(station),List.of(new ServiceAssessment.Request(g.getUUID(),null)));
                check(report.inspected()==1&&report.guards().get(0).rows().stream().filter(r->r.category().equals("ammo")).allMatch(r->r.requested()==0&&r.incoming()==0),"Preview retained stale ammo demand");
                check(handler.getStackInSlot(0).isEmpty()&&station.inventory().getStackInSlot(0).getCount()==12,"Rejected delivery moved ammo");
                QuartermasterServiceRuntime.cancel(g,ServiceStatus.RETRY);
                check(!QuartermasterServiceRuntime.reserved(station,0),"Reservation was not released");
                passed.add("pending-ammo-canceled-"+mode);
                // Medical supplies remain deliverable in both modes.
                ServiceEquipment.set(g,9,new ItemStack(ModItems.BANDAGE.get(),1));
                station.inventory().setStackInSlot(1,new ItemStack(ModItems.BANDAGE.get(),4));
                var medical=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
                check(medical!=null&&medical.targetSlot()==9&&ServiceTransactions.transfer(medical)==ServiceStatus.SUPPLIED&&ServiceEquipment.get(g,9).getCount()==5,"Virtual mode blocked medicine");
                ServiceEquipment.set(g,9,ItemStack.EMPTY);passed.add("virtual-medical-service-"+mode);
            }
            // Equipment reservations made before the setting change stay usable.
            ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(InfiniteAmmoMode.OFF);
            state.update(new ServiceSettings(GuardServiceState.Policy.PROFILE,ResourceLocation.parse("villagerguardgunners:rifleman"),false,null));
            station.inventory().setStackInSlot(1,new ItemStack(Items.IRON_HELMET));
            var equipment=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
            check(equipment!=null&&equipment.targetSlot()==2&&QuartermasterServiceRuntime.reserve(equipment),"No equipment fixture");
            ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(InfiniteAmmoMode.WITH_RELOADING);
            check(ServiceTransactions.valid(equipment)&&ServiceTransactions.transfer(equipment)==ServiceStatus.SUPPLIED,"Mode change blocked equipment");
            QuartermasterServiceRuntime.cancel(g,ServiceStatus.RETRY);state.policy(GuardServiceState.Policy.RESTOCK);passed.add("pending-equipment-preserved");
            ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(InfiniteAmmoMode.CREATIVE_AMMO_BOX);
            var box=GuardInfiniteAmmo.spawnSupply(primary,ItemStack.EMPTY);handler.setStackInSlot(0,box);
            // The physical box still covers this caliber after switching OFF.
            ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(InfiniteAmmoMode.OFF);
            station.inventory().setStackInSlot(1,ammo("9mm",7));
            var pistolPlan=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
            check(pistolPlan!=null&&pistolPlan.sourceSlot()==1&&ServiceTransactions.transfer(pistolPlan)==ServiceStatus.SUPPLIED,"Creative primary box blocked sidearm or requested primary ammo");
            check(station.inventory().getStackInSlot(0).getCount()==12&&ItemStack.matches(box,handler.getStackInSlot(0)),"Creative box was changed or unnecessary primary ammo moved");
            passed.add("creative-primary-preserves-sidearm-demand");
            handler.setStackInSlot(0,ItemStack.EMPTY);
            var pending=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
            check(pending!=null&&pending.sourceSlot()==0&&QuartermasterServiceRuntime.reserve(pending),"Removing creative box did not resume demand");
            handler.setStackInSlot(2,box.copy());
            check(!ServiceTransactions.valid(pending)&&!ServiceTransactions.incoming(pending)&&ServiceTransactions.transfer(pending)==ServiceStatus.RETRY,"New creative box did not cancel pending ammo");
            QuartermasterServiceRuntime.cancel(g,ServiceStatus.RETRY);passed.add("creative-box-added-during-delivery");
            check(ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station))==null,"Creative box in third slot ignored");
            // Universal boxes cover both guns; finite and wrong-caliber boxes do not suppress demand.
            var universal=box.copy();((IAmmoBox)universal.getItem()).setCreative(universal,true);handler.setStackInSlot(2,universal);
            station.inventory().setStackInSlot(1,ammo("9mm",7));
            check(ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station))==null,"Universal creative box ignored");passed.add("universal-creative-box-covers-both-weapons");
            handler.setStackInSlot(2,ItemStack.EMPTY);
            var finite=new ItemStack(com.tacz.guns.init.ModItems.AMMO_BOX.get());var nativeBox=(IAmmoBox)finite.getItem();nativeBox.setAmmoId(finite,ResourceLocation.parse("tacz:762x39"));nativeBox.setAmmoCount(finite,20);handler.setStackInSlot(2,finite);
            check(ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station))!=null,"Finite box incorrectly suppressed loose reserve");passed.add("finite-box-keeps-existing-reserve-policy");
            ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(InfiniteAmmoMode.CREATIVE_AMMO_BOX);
            handler.setStackInSlot(2,GuardInfiniteAmmo.spawnSupply(sidearm,ItemStack.EMPTY));
            pending=ServicePlanner.plan(g,station,QuartermasterServiceRuntime.sources(station));
            check(pending!=null&&pending.sourceSlot()==0,"Wrong-caliber creative box suppressed primary demand");
            check(ServiceTransactions.transfer(pending)==ServiceStatus.SUPPLIED&&station.inventory().getStackInSlot(0).isEmpty(),"Finite demand did not transfer normally");passed.add("wrong-caliber-box-keeps-primary-demand");
            return List.copyOf(passed);
        } finally {
            QuartermasterServiceRuntime.cancel(g,ServiceStatus.RETRY);g.discard();
            ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.accept(savedMode);
            for(int i=0;i<stock.size();i++)station.inventory().setStackInSlot(i,stock.get(i));
        }
    }
}
