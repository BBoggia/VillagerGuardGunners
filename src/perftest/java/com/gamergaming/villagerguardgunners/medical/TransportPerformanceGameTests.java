package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.*;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder("villagerguardgunners_village_buildings_performance")
@PrefixGameTestTemplate(false)
public final class TransportPerformanceGameTests {
    @GameTest(batch="transport_32",templateNamespace="villagerguardgunners_village_buildings_performance",template="villagerguardgunners/gametest_arena",timeoutTicks=550)
    public static void transportSafetyFor16ConcurrentCarriers(GameTestHelper h) throws Exception {
        var level=h.getLevel();var guards=new ArrayList<Guard>();var cots=new ArrayList<BlockPos>();
        boolean oldDowned=ModConfigs.GUARD_GUNNERS.medical.downed.get(),oldTransport=ModConfigs.GUARD_GUNNERS.medical.transport.get();
        ModConfigs.GUARD_GUNNERS.medical.downed.accept(true);ModConfigs.GUARD_GUNNERS.medical.transport.accept(true);
        // A high platform prevents natural cave gravel from falling into the measured lanes.
        var origin=new BlockPos(70926,level.getMaxBuildHeight()-10,70002);
        // The harness owns terrain residency; the production runtime must not change it.
        var fixtureChunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int x=origin.getX()>>4;x<=(origin.getX()+15)>>4;x++)for(int z=origin.getZ()>>4;z<=(origin.getZ()+63)>>4;z++){
            var chunk=new net.minecraft.world.level.ChunkPos(x,z);
            if(!level.getForcedChunks().contains(chunk.toLong())){level.setChunkForced(x,z,true);fixtureChunks.add(chunk);}level.getChunk(x,z);
        }
        for(int x=0;x<16;x++)for(int z=0;z<64;z++){level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<10;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        for(int i=0;i<16;i++){
            var cot=origin.offset(10,0,i*4+1);cots.add(cot);var state=ModBlocks.RECOVERY_COT.get().defaultBlockState().setValue(RecoveryCotBlock.FACING,Direction.EAST);
            level.setBlock(cot.west(),state.setValue(RecoveryCotBlock.PART,BedPart.FOOT),3);level.setBlock(cot,state.setValue(RecoveryCotBlock.PART,BedPart.HEAD),3);
            TransportRuntime.cotLoaded((RecoveryCotBlockEntity)level.getBlockEntity(cot));
            for(int j=0;j<2;j++){
                Guard guard=GuardEntityType.GUARD.get().create(level);guard.setPos(Vec3.atBottomCenterOf(origin.offset(1+j,0,i*4+1)));
                var holder=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard).getDataHolder();holder.drawTimestamp=holder.shootTimestamp=-1;holder.meleePrepTickCount=-1;
                guard.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));guards.add(guard);
                if(j==0){GuardMedicAccess.get(guard).medic(true);GuardMedicalAccess.get(guard).inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get(),4));}
                else{guard.setHealth(2);guard.hurt(level.damageSources().generic(),100);GuardLifeRuntime.completeTreatment(guard,4,40);}
            }
        }
        int forcedBefore=level.getForcedChunks().size();var recording=new Recording(Configuration.getConfiguration("default"));recording.setName("transport-32");recording.setToDisk(true);recording.setMaxSize(64L*1024*1024);recording.start();
        long[] idle=new long[200],active=new long[200],batch=new long[200];int[] samples={0};boolean[] done={false};
        Runnable close=()->{guards.forEach(TransportRuntime::cancel);guards.forEach(Guard::discard);for(var cot:cots){level.removeBlock(cot,false);level.removeBlock(cot.west(),false);}for(var chunk:fixtureChunks)level.setChunkForced(chunk.x,chunk.z,false);ModConfigs.GUARD_GUNNERS.medical.downed.accept(oldDowned);ModConfigs.GUARD_GUNNERS.medical.transport.accept(oldTransport);recording.close();};
        h.onEachTick(()->{
            if(done[0]||h.getTick()<40)return;
            try{
                int index=samples[0]++;long[] times=level.getServer().tickTimes;long previous=times[Math.floorMod(level.getServer().getTickCount()-1,times.length)];
                if(index<200)idle[index]=previous;if(index>=201&&index<=400)active[index-201]=previous;
                if(index>=200&&index<400){
                    for(int i=0;i<32;i+=2)GuardMedicAccess.get(guards.get(i)).nextTransportAttempt=0;
                    long start=System.nanoTime();
                    for(int i=0;i<32;i+=2){Guard carrier=guards.get(i),patient=guards.get(i+1);h.assertTrue(TransportRuntime.tryStart(carrier,patient),"Transport claim failed at carrier "+i+", cycle="+index+", enabled="+TransportRuntime.enabled()+", eligible="+MedicEligibility.available(carrier,false)+", stage="+GuardLifeAccess.get(patient).stage()+", cotLoaded="+level.hasChunkAt(cots.get(i/2)));if(!TransportRuntime.pickup(TransportRuntime.lease(carrier)))h.fail("Transport pickup failed carrier="+i+", cycle="+index+", valid="+TransportRuntime.valid(TransportRuntime.lease(carrier))+", distance="+carrier.distanceToSqr(patient)+", sight="+carrier.hasLineOfSight(patient)+", standing="+TransportRuntime.safeStanding(patient,patient.position())+", clear="+TransportRuntime.clear(level,TransportRuntime.carryBox(carrier.position()))+", patient="+patient.position()+", clearance="+clearanceDetails(carrier));carrier.positionRider(patient);}
                    for(int i=0;i<32;i+=2){Guard carrier=guards.get(i),patient=guards.get(i+1);TransportRuntime.tickGuard(carrier);TransportRuntime.tickGuard(patient);h.assertTrue(TransportRuntime.valid(TransportRuntime.lease(carrier)),"Concurrent carry became invalid");TransportRuntime.cancel(carrier);
                        h.assertTrue(!patient.isPassenger()&&patient.getMainHandItem().is(Items.IRON_SWORD)&&GuardMedicalAccess.get(carrier).inventory().getStackInSlot(0).getCount()==4,"Carry lost entity/items");}
                    batch[index-200]=System.nanoTime()-start;
                }else if(index>=400){
                    done[0]=true;h.assertTrue(forcedBefore==level.getForcedChunks().size(),"Transport forced chunks");
                    Path directory=Path.of(System.getProperty("villagerguardgunners.villageBuildingPerformanceReportDir")).resolveSibling("transport-performance");Files.createDirectories(directory);
                    recording.stop();recording.dump(directory.resolve("transport-32.jfr"));var report=new JsonObject();report.addProperty("scenario","transport-32");report.addProperty("guards",32);report.addProperty("concurrentCarriers",16);report.addProperty("sampleTicksPerStage",200);report.addProperty("completedCycles",3200);report.addProperty("itemsConserved",true);
                    report.addProperty("forcedChunksBefore",forcedBefore);report.addProperty("forcedChunksAfter",level.getForcedChunks().size());metrics(report,"idle",idle);metrics(report,"active",active);metrics(report,"transportBatch",batch);
                    report.addProperty("productionArtifactSha256",System.getProperty("villagerguardgunners.phase8ProductionArtifactSha256"));report.addProperty("performanceHarnessSha256",System.getProperty("villagerguardgunners.performanceHarnessSha256"));
                    report.addProperty("timingNote","16 simultaneous claim/pickup/safety/release cycles per batch; loaded terrain and stationary original actors; retry reset outside timed work. Native behavior/client tests cover navigation. Hardware-specific timing, no absolute timing gate.");
                    Files.writeString(directory.resolve("transport-32.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");close.run();h.succeed();
                }
            }catch(Exception|AssertionError e){done[0]=true;close.run();h.fail("Transport performance workload failed: "+e);}
        });
    }
    private static String clearanceDetails(Guard guard) {
        var level=(net.minecraft.server.level.ServerLevel)guard.level();var box=TransportRuntime.carryBox(guard.position());var details=new ArrayList<String>();
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){
            if(!level.hasChunkAt(pos))details.add("unloaded "+pos);else{var state=level.getBlockState(pos);if(!state.isAir())details.add(pos+"="+state);}
        }
        return details.toString();
    }
    private static void metrics(JsonObject report,String prefix,long[] values){var sorted=values.clone();Arrays.sort(sorted);report.addProperty(prefix+"P50Millis",sorted[99]/1_000_000.0);report.addProperty(prefix+"P95Millis",sorted[189]/1_000_000.0);report.addProperty(prefix+"P99Millis",sorted[197]/1_000_000.0);report.addProperty(prefix+"MaxMillis",sorted[199]/1_000_000.0);}
}
