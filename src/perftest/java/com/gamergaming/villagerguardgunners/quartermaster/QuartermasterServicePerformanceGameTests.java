package com.gamergaming.villagerguardgunners.quartermaster;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import java.nio.file.*;
import java.util.*;
import jdk.jfr.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder(VillagerGuardGunners.MODID + "_village_buildings_performance")
@PrefixGameTestTemplate(false)
public final class QuartermasterServicePerformanceGameTests {
    @GameTest(batch="equipment_service_100",templateNamespace="villagerguardgunners_village_buildings_performance",template="villagerguardgunners/gametest_arena",timeoutTicks=550)
    public static void durablePhysicalServiceFor100Guards(GameTestHelper h) throws Exception {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(8,2,8)); int forced=level.getForcedChunks().size();
        level.setBlock(pos,ModBlocks.QUARTERMASTER.get().defaultBlockState(),3);
        var station=(QuartermasterBlockEntity)level.getBlockEntity(pos);var owner=h.makeMockPlayer();owner.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);station.placedBy(owner);
        var operator=new Villager(EntityType.VILLAGER,level); operator.setVillagerData(operator.getVillagerData().setProfession(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(QuartermasterProfession.ID)));
        operator.setPos(owner.position());operator.setInvulnerable(true);operator.setNoGravity(true);operator.refreshBrain(level);operator.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),pos));level.addFreshEntity(operator);station.operator=operator.getUUID();
        level.getPoiManager().take(type -> type.value() == ForgeRegistries.POI_TYPES.getValue(QuartermasterProfession.ID),
                (type, point) -> point.equals(pos), pos, 1);

        var guards=new ArrayList<Guard>();for(int i=0;i<100;i++){
            var guard=GuardEntityType.GUARD.get().create(level);guard.setPos(pos.getX()+5.5+i%10*2,pos.getY(),pos.getZ()+5.5+i/10*2);guard.setNoAi(true);guard.setNoGravity(true);guard.setInvulnerable(true);guard.setOwnerId(owner.getUUID());level.addFreshEntity(guard);
            var data=IGunOperator.fromLivingEntity(guard).getDataHolder();data.drawTimestamp=-1;data.shootTimestamp=-1;data.meleePrepTickCount=-1;
            var state=GuardEquipmentServiceAccess.get(guard);state.policy(GuardServiceState.Policy.PROFILE);state.profile(ResourceLocation.parse("villagerguardgunners:rifleman"));guards.add(guard);
        }
        var recording=new Recording(Configuration.getConfiguration("default"));recording.setName("equipment-service-100");recording.setToDisk(true);recording.setMaxSize(64L*1024*1024);recording.start();
        long[] idle=new long[200],active=new long[200],transfer=new long[100];int[] samples={0};boolean[] done={false};
        h.onEachTick(()->{if(done[0]||h.getTick()<40)return;try{
            int index=samples[0]++;var times=level.getServer().tickTimes;long previous=times[Math.floorMod(level.getServer().getTickCount()-1,times.length)];
            if(index<200)idle[index]=previous;if(index>=201&&index<=400)active[index-201]=previous;
            if(index>=200&&index<400&&index%2==0){
                int target=(index-200)/2;guards.get(target).setPos(owner.position());level.setDayTime(3000);operator.setPos(owner.position());operator.getBrain().setMemory(MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),pos));
                var stack=GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).build();stack.getOrCreateTag().putInt("ServiceSample",target);station.inventory().setStackInSlot(0,stack.copy());
                long start=System.nanoTime();var plan=ServicePlanner.plan(guards.get(target),station,QuartermasterServiceRuntime.sources(station));
                var result=plan==null?ServiceStatus.NO_MATCHING_STOCK:ServiceTransactions.transfer(plan);
                h.assertTrue(result==ServiceStatus.SUPPLIED,"Durable transfer failed sample="+target+" result="+result+" guard="+ServiceEligibility.guard(guards.get(target))+" station="+ServiceEligibility.station(station)+" operator="+operator.getVillagerData().getProfession()+" job="+operator.getBrain().getMemory(MemoryModuleType.JOB_SITE));transfer[target]=System.nanoTime()-start;
                h.assertTrue(net.minecraft.world.item.ItemStack.matches(stack,guards.get(target).getMainHandItem())&&station.inventory().getStackInSlot(0).isEmpty(),"Physical item conservation failed");
                guards.get(target).setPos(pos.getX()+5.5+target%10*2,pos.getY(),pos.getZ()+5.5+target/10*2);
            }else if(index>=400){done[0]=true;h.assertTrue(forced==level.getForcedChunks().size(),"Forced chunks");
                Path dir=Path.of(System.getProperty("villagerguardgunners.villageBuildingPerformanceReportDir")).resolveSibling("equipment-service-performance");Files.createDirectories(dir);recording.stop();recording.dump(dir.resolve("equipment-service-100.jfr"));recording.close();
                var report=new JsonObject();report.addProperty("scenario","equipment-service-100");report.addProperty("guards",100);report.addProperty("sampleTicksPerStage",200);report.addProperty("durableTransfers",100);report.addProperty("itemsConserved",true);report.addProperty("forcedChunksBefore",forced);report.addProperty("forcedChunksAfter",level.getForcedChunks().size());
                metrics(report,"idle",idle);metrics(report,"active",active);metrics(report,"durableTransfer",transfer);
                report.addProperty("productionArtifactSha256",System.getProperty("villagerguardgunners.phase8ProductionArtifactSha256"));report.addProperty("performanceHarnessSha256",System.getProperty("villagerguardgunners.performanceHarnessSha256"));
                report.addProperty("timingNote","Hardware-specific whole-server MSPT and planner plus fsync/atomic-replace physical transfer cost. Stress fixture performs one transfer every two ticks, above normal one-per-second cadence; stock and arrival-position setup occur outside timing; waiting guards occupy separate positions to avoid entity cramming. No absolute timing gate.");
                Files.writeString(dir.resolve("equipment-service-100.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");guards.forEach(Guard::discard);operator.discard();h.succeed();
            }
        }catch(Exception|AssertionError error){done[0]=true;recording.close();guards.forEach(Guard::discard);operator.discard();h.fail("Equipment service workload: "+error);}});
    }
    private static void metrics(JsonObject r,String prefix,long[] values){var sorted=values.clone();Arrays.sort(sorted);r.addProperty(prefix+"P50Millis",sorted[(int)(sorted.length*.5)-1]/1e6);r.addProperty(prefix+"P95Millis",sorted[(int)(sorted.length*.95)-1]/1e6);r.addProperty(prefix+"P99Millis",sorted[(int)(sorted.length*.99)-1]/1e6);r.addProperty(prefix+"MaxMillis",sorted[sorted.length-1]/1e6);}
}
