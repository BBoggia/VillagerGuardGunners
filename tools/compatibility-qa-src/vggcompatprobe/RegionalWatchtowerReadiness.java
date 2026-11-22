package vggcompatprobe;

import com.gamergaming.villagerguardgunners.defense.*;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.defense.duty.*;
import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingRecord;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/** Separate, explicitly requested observer exercise on naturally generated buildings. */
final class RegionalWatchtowerReadiness {
    private record Trial(Guard guard,VillageBuildingRecord building,ItemStack previousWeapon,GuardOrders previousOrders) { }
    private static final List<Trial> trials=new ArrayList<>();
    private static final List<Object> observations=new ArrayList<>();
    private static int started=-1,settled;
    private static boolean done;
    static void add(VillageBuildingRecord building,List<Guard> guards){
        for(var g:guards)trials.add(new Trial(g,building,g.getMainHandItem().copy(),GuardOrdersAccess.getOrders(g)));
    }
    static boolean tick(){
        if(done||trials.isEmpty())return true;
        if(started<0){
            started=Probe.ticks;
            for(var t:trials){
                // Model a player equipping the real resident. No teleport, path
                // override, forced assignment or replacement guard is used.
                t.guard().setItemSlot(EquipmentSlot.MAINHAND,GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47"))
                    .setFireMode(FireMode.AUTO).setAmmoCount(30).setAmmoInBarrel(true).build());
                if(!GuardOrdersAccess.setOrders(t.guard(),t.previousOrders().withMovement(GuardOrders.MovementOrder.TACTICAL)))
                    throw new AssertionError("Could not issue normal Tactical movement order");
                VillageDefenseStaffingRuntime.synchronizeGuard(t.guard());
                var network=VillageDefenseNetworkSavedData.get(Probe.level).networkForBuilding(t.building().identity().buildingId());
                if(network==null)throw new AssertionError("Missing Watchtower defense network");
                var result=VillageDefenseStateService.signal(Probe.level,network.networkId(),VillageDefenseSignal.BELL_RUNG);
                if(!result.status().successful())throw new AssertionError("Watch readiness signal failed: "+result);
            }
            Probe.progress("equipped natural Watchtower guards responding to Watch readiness");
        }
        // Keep the exercise in Watch using normal bell signals. One default
        // 600-tick Watch period can expire while a roaming resident returns.
        if(Probe.ticks>started&&(Probe.ticks-started)%400==0){
            var networks=new HashSet<UUID>();
            for(var t:trials)networks.add(VillageDefenseNetworkSavedData.get(Probe.level)
                .networkForBuilding(t.building().identity().buildingId()).networkId());
            for(var id:networks)VillageDefenseStateService.signal(Probe.level,id,VillageDefenseSignal.BELL_RUNG);
        }
        boolean arrived=true;
        if((Probe.ticks-started)%100==0){
            for(var t:trials){
                var g=t.guard();
                var network=VillageDefenseNetworkSavedData.get(Probe.level).networkForBuilding(t.building().identity().buildingId());
                var movement=g.goalSelector.getAvailableGoals().stream().map(w->w.getGoal())
                    .filter(com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal.class::isInstance)
                    .map(com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal.class::cast)
                    .map(goal->goal.captureDiagnostics().toString()).toList();
                observations.add(Map.of("elapsed",Probe.ticks-started,"guard",g.getUUID().toString(),
                    "position",g.position().toString(),"assignment",String.valueOf(VillageDefenseStaffingRuntime.assignment(g)),
                    "readiness",network.defenseStatus().toString(),"movement",movement,
                    "runningGoals",g.goalSelector.getAvailableGoals().stream().filter(w->w.isRunning())
                        .map(w->w.getPriority()+":"+w.getGoal().getClass().getSimpleName()).toList()));
            }
            Probe.result.put("regionalWatchtowerReadinessObservations",observations);
        }
        for(var t:trials){
            var g=t.guard();var assignment=VillageDefenseStaffingRuntime.assignment(g);
            var slot=assignment==null?null:VillageDefenseDutySavedData.get(Probe.level).slot(assignment.slotId());
            if(!g.isAlive())throw new AssertionError("Resident died during Watch readiness exercise");
            arrived &= !g.isInWater()&&slot!=null&&slot.buildingId().equals(t.building().identity().buildingId())
                &&g.position().distanceToSqr(slot.position().getCenter().add(0,-.5,0))<2.25;
        }
        settled=arrived?settled+1:0;
        if(settled>=240){
            var results=new ArrayList<Object>();
            for(var t:trials){
                var a=VillageDefenseStaffingRuntime.assignment(t.guard());
                results.add(Map.of("guard",t.guard().getUUID().toString(),"building",t.building().identity().buildingId().toString(),
                    "slot",a.slotId().toString(),"position",t.guard().blockPosition().toShortString()));
                t.guard().setItemSlot(EquipmentSlot.MAINHAND,t.previousWeapon());
                GuardOrdersAccess.setOrders(t.guard(),t.previousOrders());
                VillageDefenseStaffingRuntime.synchronizeGuard(t.guard());
            }
            Probe.result.put("regionalWatchtowerReadiness",Map.of("fixture","manual arming, Tactical movement orders, and bell signals every 400 ticks on natural residents",
                "guards",results,"settledTicks",settled,"teleported",false,"forcedAssignments",false));
            done=true;return true;
        }
        if(Probe.ticks-started>1000)throw new AssertionError("Watchtower readiness response timed out: "+trials.stream()
            .map(t->t.guard().getUUID()+" "+t.guard().position()+" assignment="+VillageDefenseStaffingRuntime.assignment(t.guard())).toList());
        return false;
    }
}
