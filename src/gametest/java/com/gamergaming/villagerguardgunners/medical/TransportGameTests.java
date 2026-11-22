package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.*;
import com.tacz.guns.api.entity.IGunOperator;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class TransportGameTests {
    private static final String ARENA="villagerguardgunners/gametest_arena";
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final Guard carrier,patient; final BlockPos origin,cot;
        final boolean downed=ModConfigs.GUARD_GUNNERS.medical.downed.get(), transport=ModConfigs.GUARD_GUNNERS.medical.transport.get();
        final List<ChunkPos> forced=new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h) { this(h,false); }
        Fixture(GameTestHelper h,boolean live) {
            this.h=h;ModConfigs.GUARD_GUNNERS.medical.downed.accept(true);ModConfigs.GUARD_GUNNERS.medical.transport.accept(true);
            // Cave ceilings can shed gravel after setup; the live movement fixture uses a high platform.
            origin=live?new BlockPos(60848,h.getLevel().getMaxBuildHeight()-10,60008):h.absolutePos(new BlockPos(8,2,8));
            if(live){var chunk=new ChunkPos(origin);for(int x=chunk.x-4;x<=chunk.x+4;x++)for(int z=chunk.z-4;z<=chunk.z+4;z++){
                var pos=new ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(pos.toLong())){h.getLevel().setChunkForced(x,z,true);forced.add(pos);}h.getLevel().getChunk(x,z);}}
            for(int x=-3;x<=16;x++)for(int z=-4;z<=4;z++){
                h.getLevel().setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
                for(int y=0;y<(live?10:5);y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            }
            cot=origin.offset(12,0,0);placeCot(cot);
            carrier=actor(origin);patient=actor(origin.offset(1,0,0));
            GuardMedicAccess.get(carrier).medic(!live);GuardMedicalAccess.get(carrier).inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get(),4));
            patient.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            patient.setHealth(2);patient.hurt(h.getLevel().damageSources().generic(),100);GuardLifeRuntime.completeTreatment(patient,4,40);
            h.assertTrue(GuardLifeAccess.get(patient).stage()==GuardLifeState.Stage.STABILIZED,"Fixture was not stabilized");
            if(live){h.getLevel().addFreshEntity(carrier);h.getLevel().addFreshEntity(patient);}
        }
        Guard actor(BlockPos pos){Guard g=GuardEntityType.GUARD.get().create(h.getLevel());g.setPos(Vec3.atBottomCenterOf(pos));
            var data=IGunOperator.fromLivingEntity(g).getDataHolder();data.drawTimestamp=data.shootTimestamp=-1;data.meleePrepTickCount=-1;
            g.goalSelector.removeAllGoals(goal->!(goal instanceof TransportMovementGoal));g.targetSelector.removeAllGoals(goal->true);return g;}
        void placeCot(BlockPos head){var state=ModBlocks.RECOVERY_COT.get().defaultBlockState().setValue(RecoveryCotBlock.FACING,Direction.EAST);
            h.getLevel().setBlock(head.west(),state.setValue(RecoveryCotBlock.PART,BedPart.FOOT),3);
            h.getLevel().setBlock(head,state.setValue(RecoveryCotBlock.PART,BedPart.HEAD),3);
            h.assertTrue(h.getLevel().getBlockEntity(head) instanceof RecoveryCotBlockEntity,"Cot block entity missing");
            TransportRuntime.cotLoaded((RecoveryCotBlockEntity)h.getLevel().getBlockEntity(head));}
        TransportRuntime.Lease reserve(){h.assertTrue(TransportRuntime.tryStart(carrier,patient),"Could not reserve transport");return TransportRuntime.lease(carrier);}
        TransportRuntime.Lease carry(){var lease=reserve();h.assertTrue(TransportRuntime.pickup(lease),"Could not pick up patient");h.assertTrue(carrier.getControllingPassenger()==null,"Casualty took over carrier controls");carrier.positionRider(patient);return lease;}
        @Override public void close(){if(closed)return;closed=true;TransportRuntime.cancel(carrier);MedicRuntime.cancel(carrier,MedicState.Status.INTERRUPTED);carrier.discard();patient.discard();
            h.getLevel().removeBlock(cot,false);h.getLevel().removeBlock(cot.west(),false);for(var p:forced)h.getLevel().setChunkForced(p.x,p.z,false);
            ModConfigs.GUARD_GUNNERS.medical.downed.accept(downed);ModConfigs.GUARD_GUNNERS.medical.transport.accept(transport);}
    }
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void transportDefaultOffLeavesTreatmentInPlace(GameTestHelper h){try(var f=new Fixture(h)){ModConfigs.GUARD_GUNNERS.medical.transport.accept(false);h.assertTrue(!TransportRuntime.tryStart(f.carrier,f.patient)&&!f.patient.isPassenger(),"Disabled transport started");}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void carrierPatientAndCotAreExclusivelyReserved(GameTestHelper h){try(var f=new Fixture(h)){f.reserve();Guard other=f.actor(f.origin.offset(0,0,1));try{GuardMedicAccess.get(other).medic(true);GuardMedicalAccess.get(other).inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get()));h.assertTrue(!TransportRuntime.tryStart(other,f.patient)&&!MedicRuntime.reserve(other,f.patient),"Another medic stole the patient");}finally{other.discard();}h.assertTrue(TransportRuntime.busy(f.carrier)&&TransportRuntime.busy(f.patient),"Missing paired reservation");}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void carryingAndDeliveryKeepIdentityItemsAndRequireTreatment(GameTestHelper h){try(var f=new Fixture(h)){var id=f.patient.getUUID();var held=f.patient.getMainHandItem().copy();int timer=GuardLifeAccess.get(f.patient).remaining();var lease=f.carry();
        f.carrier.setPos(Vec3.atBottomCenterOf(f.cot.offset(-2,0,0)));f.carrier.positionRider(f.patient);h.assertTrue(TransportRuntime.deliver(lease),"Cot delivery failed");
        h.assertTrue(id.equals(f.patient.getUUID())&&ItemStack.matches(held,f.patient.getMainHandItem())&&!f.patient.isPassenger()&&TransportRuntime.onCot(f.patient),"Delivery lost original patient/items");
        h.assertTrue(GuardLifeAccess.get(f.patient).stage()==GuardLifeState.Stage.STABILIZED&&GuardLifeAccess.get(f.patient).remaining()==timer&&GuardMedicalAccess.get(f.carrier).inventory().getStackInSlot(0).getCount()==4,"Cot granted free treatment, time, or spent supplies");
        h.assertTrue(!TransportRuntime.tryStart(f.carrier,f.patient),"Cot patient was carried again");}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void carrierCombatReleasesCasualtyOnLoadedGround(GameTestHelper h){try(var f=new Fixture(h)){f.carry();var zombie=EntityType.ZOMBIE.create(h.getLevel());try{f.carrier.setTarget(zombie);TransportRuntime.tickGuard(f.carrier);h.assertTrue(!f.patient.isPassenger()&&!TransportRuntime.busy(f.carrier)&&TransportRuntime.safeStanding(f.patient,f.patient.position()),"Combat trapped patient");}finally{zombie.discard();}}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void carrierDeathPreservesPassengerAndInventory(GameTestHelper h){try(var f=new Fixture(h)){f.carry();f.carrier.hurt(h.getLevel().damageSources().fellOutOfWorld(),1000);h.assertTrue(f.patient.isAlive()&&!f.patient.isPassenger()&&f.patient.getMainHandItem().is(Items.IRON_SWORD)&&!TransportRuntime.busy(f.patient),"Carrier death lost patient");}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void patientTrueDeathReleasesCarrierAndCot(GameTestHelper h){try(var f=new Fixture(h)){f.carry();f.patient.invulnerableTime=0;f.patient.hurt(h.getLevel().damageSources().fellOutOfWorld(),1000);h.assertTrue(!f.patient.isAlive()&&!TransportRuntime.busy(f.carrier)&&!f.patient.isPassenger(),"Patient death retained carry claim");}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void disablingTransportAndBreakingCotReleaseClaims(GameTestHelper h){try(var f=new Fixture(h)){f.carry();ModConfigs.GUARD_GUNNERS.medical.transport.accept(false);TransportRuntime.tickGuard(f.carrier);h.assertTrue(!f.patient.isPassenger()&&!TransportRuntime.busy(f.patient),"Feature disable trapped patient");ModConfigs.GUARD_GUNNERS.medical.transport.accept(true);GuardMedicAccess.get(f.carrier).nextTransportAttempt=0;f.carry();h.getLevel().removeBlock(f.cot,false);h.assertTrue(!f.patient.isPassenger()&&!TransportRuntime.busy(f.patient),"Broken cot retained patient");}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void restartHintDismountsOriginalPatientWithoutCopyingItems(GameTestHelper h){try(var f=new Fixture(h)){f.carry();var saved=new CompoundTag();f.carrier.save(saved);TransportRuntime.cancel(f.carrier);
        var restored=EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);try{var patient=(Guard)restored.getFirstPassenger();h.assertTrue(patient!=null&&GuardTransportAccess.get(patient).present(),"Restart lost recovery hint");TransportRuntime.tickGuard(patient);
            h.assertTrue(!patient.isPassenger()&&!GuardTransportAccess.get(patient).present()&&patient.getUUID().equals(f.patient.getUUID())&&patient.getMainHandItem().is(Items.IRON_SWORD),"Restart failed to safely release original passenger");patient.discard();}finally{restored.discard();}}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void dimensionTravelIsCanceledDuringPhysicalCarry(GameTestHelper h){try(var f=new Fixture(h)){f.carry();var event=new EntityTravelToDimensionEvent(f.carrier,net.minecraft.world.level.Level.NETHER);MinecraftForge.EVENT_BUS.post(event);h.assertTrue(event.isCanceled()&&!f.patient.isPassenger()&&!TransportRuntime.busy(f.carrier),"Carry crossed dimensions or retained relationship");}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void occupiedCotAndPlayerOrdersKeepCareInPlace(GameTestHelper h){try(var f=new Fixture(h)){Guard occupant=f.actor(f.cot);occupant.setPos(Vec3.atBottomCenterOf(f.cot).add(-.5,.5,0));h.getLevel().addFreshEntity(occupant);try{h.assertTrue(!TransportRuntime.tryStart(f.carrier,f.patient),"Occupied cot was reserved");}finally{occupant.discard();}f.patient.setFollowing(true);h.assertTrue(!TransportRuntime.tryStart(f.carrier,f.patient),"Patient's follow order was ignored");f.patient.setFollowing(false);f.carrier.setFollowing(true);h.assertTrue(!TransportRuntime.tryStart(f.carrier,f.patient),"Carrier's follow order was ignored");}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void lowCeilingPreventsPickupAndCarrierActionsAreBlocked(GameTestHelper h){try(var f=new Fixture(h)){var lease=f.reserve();h.getLevel().setBlock(f.origin.above(3),Blocks.STONE.defaultBlockState(),3);h.assertTrue(!TransportRuntime.pickup(lease)&&!f.patient.isPassenger(),"Pickup ignored headroom");h.assertTrue(GuardBurstSafetyAccess.shouldSuppress(f.carrier)&&!((GuardCoverageAccess)f.carrier).villagerGuardGunners$nativeEquipmentServiceSafe(),"Carrier could fire or swap equipment");var combat=new com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal(f.carrier);
        combat.start(); // Exercise a lease acquired between goal selection and its next tick.
        combat.tick();
        h.assertTrue(!combat.retainsCombatOwnership()&&!combat.canUse()
                &&combat.lastInterruption().equals("medical or equipment preemption"),"Transport failed to preempt combat tick");
        combat.stop();}h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void cotHalvesDropExactlyOneItemAndAreNotVillagerBeds(GameTestHelper h){try(var f=new Fixture(h)){
        var state=h.getLevel().getBlockState(f.cot);h.assertTrue(net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state).isEmpty(),"Cot registered as a village POI");
        var box=new net.minecraft.world.phys.AABB(f.cot).inflate(3);
        long before=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box,e->e.getItem().is(ModItems.RECOVERY_COT.get())).stream().mapToInt(e->e.getItem().getCount()).sum();
        h.getLevel().destroyBlock(f.cot.west(),true);
        long after=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box,e->e.getItem().is(ModItems.RECOVERY_COT.get())).stream().mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(after-before==1&&!h.getLevel().getBlockState(f.cot).is(ModBlocks.RECOVERY_COT.get()),"Two cot halves did not yield exactly one item");
    }h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void unloadedCotAndCarrierLeaveReleaseOriginalPassenger(GameTestHelper h){try(var f=new Fixture(h)){
        f.carry();((RecoveryCotBlockEntity)h.getLevel().getBlockEntity(f.cot)).onChunkUnloaded();
        h.assertTrue(!f.patient.isPassenger()&&!TransportRuntime.busy(f.carrier)&&f.patient.isAlive(),"Cot unload stranded passenger");
        TransportRuntime.cotLoaded((RecoveryCotBlockEntity)h.getLevel().getBlockEntity(f.cot));GuardMedicAccess.get(f.carrier).nextTransportAttempt=0;f.carry();
        TransportRuntime.left(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(f.carrier,h.getLevel()));
        h.assertTrue(!f.patient.isPassenger()&&!TransportRuntime.busy(f.patient)&&f.patient.getMainHandItem().is(Items.IRON_SWORD),"Carrier leave lost passenger/items");
    }h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void canceledMountDoesNotKeepARecoveryHintOrCopyPatient(GameTestHelper h){try(var f=new Fixture(h)){
        var lease=f.reserve();java.util.function.Consumer<net.minecraftforge.event.entity.EntityMountEvent> listener=e->{if(e.getEntityMounting()==f.patient&&e.isMounting())e.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(listener);try{h.assertTrue(!TransportRuntime.pickup(lease),"Canceled mount succeeded");}finally{MinecraftForge.EVENT_BUS.unregister(listener);}
        TransportRuntime.cancel(f.carrier);h.assertTrue(!f.patient.isPassenger()&&!GuardTransportAccess.get(f.patient).present()&&f.patient.isAlive(),"Canceled pickup left an orphan marker");
    }h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void newObstacleAndManualRevivalEndPhysicalCarry(GameTestHelper h){try(var f=new Fixture(h)){
        f.carry();h.getLevel().setBlock(f.origin.above(3),Blocks.STONE.defaultBlockState(),3);TransportRuntime.tickGuard(f.carrier);
        h.assertTrue(!f.patient.isPassenger()&&!TransportRuntime.busy(f.carrier),"New overhead obstacle retained unsafe carry");
        h.getLevel().setBlock(f.origin.above(3),Blocks.AIR.defaultBlockState(),3);GuardMedicAccess.get(f.carrier).nextTransportAttempt=0;f.carry();
        GuardLifeRuntime.completeTreatment(f.patient,4,40);h.assertTrue(!f.patient.isPassenger()&&!TransportRuntime.busy(f.carrier)&&GuardLifeAccess.get(f.patient).stage()==GuardLifeState.Stage.RECOVERING,"Revived patient remained cargo");
    }h.succeed();}
    @GameTest(templateNamespace="minecraft",template=ARENA,timeoutTicks=80,batch="transport_dismount_cancel")
    public static void canceledDismountRetainsOwnershipUntilRetry(GameTestHelper h){var f=new Fixture(h);f.carry();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityMountEvent> listener=e->{if(e.getEntityMounting()==f.patient&&e.isDismounting())e.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(listener);try{TransportRuntime.cancel(f.carrier);h.assertTrue(f.patient.isPassenger()&&TransportRuntime.busy(f.carrier)&&GuardTransportAccess.get(f.patient).present()&&TransportRuntime.awaitingRelease(f.carrier),"Canceled dismount abandoned passenger ownership");}finally{MinecraftForge.EVENT_BUS.unregister(listener);}
        h.runAfterDelay(70,f::close);h.runAfterDelay(25,()->{try{TransportRuntime.tickGuard(f.carrier);h.assertTrue(!f.patient.isPassenger()&&!TransportRuntime.busy(f.carrier)&&!GuardTransportAccess.get(f.patient).present(),"Dismount retry did not release safely");}finally{f.close();}h.succeed();});
    }
    @GameTest(templateNamespace="minecraft",template=ARENA,batch="transport_live",timeoutTicks=360)
    public static void medicPhysicallyCarriesPatientToCot(GameTestHelper h){var f=new Fixture(h,true);boolean[] observed={false,false};
        boolean[] requested={false};
        h.runAfterDelay(350,f::close);
        h.onEachTick(()->{if(!f.closed){
            // This case measures native carrying movement. The shared medic search queue
            // also contains actors from other tests; client QA covers automatic selection.
            if(!requested[0]&&f.carrier.tickCount>=5&&f.patient.tickCount>=5){
                requested[0]=true;GuardMedicAccess.get(f.carrier).medic(true);
                try{f.reserve();}catch(RuntimeException|AssertionError failure){f.close();throw failure;}
            }
            observed[1]|=TransportRuntime.busy(f.carrier);if(f.patient.getVehicle()==f.carrier)observed[0]=true;
        }});
        h.succeedWhen(()->{h.assertTrue(observed[0]&&TransportRuntime.onCot(f.patient)&&!f.patient.isPassenger(),"Patient did not visibly travel to cot; carrier="+f.carrier.position()+", patient="+f.patient.position()+", lease="+TransportRuntime.lease(f.carrier)+", reserved="+observed[1]+", eligible="+MedicEligibility.available(f.carrier,false)+", patientStage="+GuardLifeAccess.get(f.patient).stage()+", retry="+GuardMedicAccess.get(f.carrier).nextTransportAttempt+", carrierTicks="+f.carrier.tickCount+", goals="+f.carrier.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(TransportMovementGoal.class::isInstance).map(g->((TransportMovementGoal)g).status()).toList());
            h.assertTrue(f.patient.getMainHandItem().is(Items.IRON_SWORD)&&GuardLifeRuntime.casualty(f.patient),"Live transport altered inventory or granted revival");f.close();});}
}
