package com.gamergaming.villagerguardgunners.gametest;
import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.*;
import com.gamergaming.villagerguardgunners.medical.*;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.gametest.*;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;
import java.util.function.Consumer;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class MedicAiGameTests {
    private static final String TEMPLATE="villagerguardgunners/gametest_arena";
    private record Fixture(Guard medic,Guard patient) implements AutoCloseable {
        @Override public void close(){MedicRuntime.cancel(medic,MedicState.Status.INTERRUPTED);medic.discard();patient.discard();}
    }
    private static Guard guard(GameTestHelper h,int x){var g=GuardEntityType.GUARD.get().create(h.getLevel());var pos=h.absolutePos(new BlockPos(x,2,8));g.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);var data=IGunOperator.fromLivingEntity(g).getDataHolder();data.drawTimestamp=-1;data.shootTimestamp=-1;data.meleePrepTickCount=-1;return g;}
    private static Fixture fixture(GameTestHelper h){var m=guard(h,8);var p=guard(h,9);p.setHealth(8);GuardMedicAccess.get(m).medic(true);GuardMedicalAccess.get(m).inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get(),3));return new Fixture(m,p);}
    private static MedicRuntime.Lease prepare(GameTestHelper h,Fixture f){h.assertTrue(MedicRuntime.reserve(f.medic,f.patient),"Reservation rejected");var lease=MedicRuntime.lease(f.medic);MedicRuntime.treat(lease);h.assertTrue(lease.preparedAt>=0,"Channel did not start");return lease;}
    private static void finish(MedicRuntime.Lease lease){lease.preparedAt-=40;MedicRuntime.treat(lease);}
    private static int doses(Fixture f){return GuardMedicalAccess.get(f.medic).inventory().getStackInSlot(0).getCount();}
    private static void listener(Consumer<LivingHealEvent> listener,Runnable run){MinecraftForge.EVENT_BUS.addListener(listener);try{run.run();}finally{MinecraftForge.EVENT_BUS.unregister(listener);}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void channelWaitsAndConsumesOneDose(GameTestHelper h){try(var f=fixture(h)){var lease=prepare(h,f);MedicRuntime.treat(lease);h.assertTrue(f.patient.getHealth()==8&&doses(f)==3,"Early treatment");finish(lease);h.assertTrue(f.patient.getHealth()==12&&doses(f)==2&&MedicRuntime.lease(f.medic)==null,"Dose or lease incorrect");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void twoMedicsReserveDifferentPatients(GameTestHelper h){try(var a=fixture(h);var b=fixture(h)){h.assertTrue(MedicRuntime.reserve(a.medic,a.patient),"First reservation");h.assertTrue(!MedicRuntime.reserve(b.medic,a.patient),"Patient double booked");h.assertTrue(MedicRuntime.reserve(b.medic,b.patient),"Second medic did not get other patient");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void movementInterruptsWithoutSpending(GameTestHelper h){try(var f=fixture(h)){var lease=prepare(h,f);f.patient.setPos(f.patient.position().add(.5,0,0));finish(lease);h.assertTrue(f.patient.getHealth()==8&&doses(f)==3&&MedicRuntime.lease(f.medic)==null,"Moved patient consumed dose");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void damageInterruptsWithoutSpending(GameTestHelper h){try(var f=fixture(h)){var lease=prepare(h,f);f.patient.hurt(h.getLevel().damageSources().generic(),1);finish(lease);h.assertTrue(doses(f)==3&&MedicRuntime.lease(f.medic)==null,"Damaged patient consumed dose");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void canceledHealingCostsNothing(GameTestHelper h){try(var f=fixture(h)){var lease=prepare(h,f);listener(e->{if(e.getEntity()==f.patient)e.setCanceled(true);},()->finish(lease));h.assertTrue(f.patient.getHealth()==8&&doses(f)==3,"Canceled event spent dose");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void anotherHealingSourceSatisfiesPatientWithoutDose(GameTestHelper h){try(var f=fixture(h)){var lease=prepare(h,f);listener(e->{if(e.getEntity()==f.patient)f.patient.setHealth(f.patient.getMaxHealth());},()->finish(lease));h.assertTrue(doses(f)==3&&f.patient.getHealth()==f.patient.getMaxHealth(),"Other healing consumed dose");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void changedSuppliesInvalidateChannel(GameTestHelper h){try(var f=fixture(h)){var lease=prepare(h,f);GuardMedicalAccess.get(f.medic).inventory().getStackInSlot(0).getOrCreateTag().putString("Changed","yes");finish(lease);h.assertTrue(doses(f)==3&&f.patient.getHealth()==8,"Changed supplies consumed");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void potionReturnsBottleInSamePouchSlot(GameTestHelper h){try(var f=fixture(h)){GuardMedicalAccess.get(f.medic).inventory().setStackInSlot(0,PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.HEALING));finish(prepare(h,f));h.assertTrue(f.patient.getHealth()==12&&GuardMedicalAccess.get(f.medic).inventory().getStackInSlot(0).is(Items.GLASS_BOTTLE),"Bottle lost or healing wrong");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void recursiveTreatmentCannotSpendTwice(GameTestHelper h){try(var f=fixture(h)){var lease=prepare(h,f);listener(e->{if(e.getEntity()==f.patient)h.assertTrue(MedicalTreatment.fromMedic(f.medic,f.patient,0,()->true)!=MedicalTreatment.Result.HEALED,"Reentrant dose succeeded");},()->finish(lease));h.assertTrue(doses(f)==2&&f.patient.getHealth()==12,"Reentry double healed");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void playerControlAndFeatureDisableCancelTreatment(GameTestHelper h){try(var f=fixture(h)){var lease=prepare(h,f);f.patient.interacting=true;finish(lease);h.assertTrue(doses(f)==3&&MedicRuntime.lease(f.medic)==null,"Player control ignored");f.patient.interacting=false;boolean old=ModConfigs.GUARD_GUNNERS.medical.medicAi.get();try{ModConfigs.GUARD_GUNNERS.medical.medicAi.accept(false);h.assertTrue(!MedicRuntime.reserve(f.medic,f.patient),"Disabled medic reserved patient");}finally{ModConfigs.GUARD_GUNNERS.medical.medicAi.accept(old);}h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void fullPatientAndAbsentSuppliesCannotReserve(GameTestHelper h){try(var f=fixture(h)){f.patient.setHealth(f.patient.getMaxHealth());h.assertTrue(!MedicRuntime.reserve(f.medic,f.patient),"Full patient reserved");f.patient.setHealth(8);GuardMedicalAccess.get(f.medic).inventory().setStackInSlot(0,ItemStack.EMPTY);h.assertTrue(!MedicRuntime.reserve(f.medic,f.patient),"Empty medic reserved");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,timeoutTicks=300,batch="medic_live_channel")
    public static void nativeGoalApproachesTreatsAndReleasesPatient(GameTestHelper h){
        var f=fixture(h);f.medic.setPos(f.medic.position().add(10000,0,10000));f.patient.setPos(f.patient.position().add(10000,0,10000));var pos=f.medic.blockPosition();for(int x=-2;x<=10;x++)for(int z=-2;z<=2;z++){h.getLevel().setBlock(pos.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);for(int y=0;y<3;y++)h.getLevel().setBlock(pos.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);}
        // Supply the complete native navigation region in fixture setup; production never loads it.
        int radius=(int)Math.ceil(f.medic.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE))+24;
        var forced=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int x=(pos.getX()-radius)>>4;x<=(pos.getX()+radius)>>4;x++)for(int z=(pos.getZ()-radius)>>4;z<=(pos.getZ()+radius)>>4;z++){
            var chunk=new net.minecraft.world.level.ChunkPos(x,z);if(!h.getLevel().getForcedChunks().contains(chunk.toLong())){h.getLevel().setChunkForced(x,z,true);forced.add(chunk);}h.getLevel().getChunk(x,z);
        }
        Runnable releaseChunks=()->forced.forEach(chunk->h.getLevel().setChunkForced(chunk.x,chunk.z,false));h.runAfterDelay(300,releaseChunks);
        f.patient.setPos(f.patient.position().add(6,0,0));f.medic.setInvulnerable(true);f.patient.setInvulnerable(true);f.medic.targetSelector.removeAllGoals(goal->true);f.patient.targetSelector.removeAllGoals(goal->true);
        // Isolate medical navigation from random stroll; both actors still run the native medic goal.
        f.medic.goalSelector.removeAllGoals(goal->!(goal instanceof MedicMovementGoal));f.patient.goalSelector.removeAllGoals(goal->!(goal instanceof MedicMovementGoal));
        h.getLevel().addFreshEntity(f.medic);h.getLevel().addFreshEntity(f.patient);
        h.onEachTick(()->{f.medic.setTarget(null);f.patient.setTarget(null);
            if(h.getTick()<30||h.getTick()%20==0){
                var lease=MedicRuntime.lease(f.medic);int missing=0;
                for(int x=(f.medic.blockPosition().getX()-36)>>4;x<=(f.medic.blockPosition().getX()+36)>>4;x++)for(int z=(f.medic.blockPosition().getZ()-36)>>4;z<=(f.medic.blockPosition().getZ()+36)>>4;z++)if(!h.getLevel().getChunkSource().hasChunk(x,z))missing++;
                System.out.println("MEDIC_NAV tick="+h.getTick()+" source="+f.medic.position()+" patient="+f.patient.position()+" status="+GuardMedicAccess.get(f.medic).status+" lease="+(lease!=null)+" valid="+MedicRuntime.valid(lease)+" missing="+missing+" safe="+MedicEligibility.available(f.medic,false)+" nav="+f.medic.getNavigation().isDone()+" medic="+GuardMedicAccess.get(f.medic).medic()+" next="+(GuardMedicAccess.get(f.medic).nextAssessment-h.getLevel().getGameTime())+" patientSafe="+MedicEligibility.available(f.patient,false)+" leasedPatient="+(lease==null?"none":lease.patient.position())+" patientLease="+(MedicRuntime.lease(f.patient)!=null));
            }
        });
        h.succeedWhen(()->{h.assertTrue(f.patient.getHealth()>8,"No native treatment: "+GuardMedicAccess.get(f.medic).status);h.assertTrue(doses(f)==2,"Native treatment dose mismatch: doses="+doses(f)+", health="+f.patient.getHealth()+", status="+GuardMedicAccess.get(f.medic).status+", distance="+f.medic.distanceTo(f.patient));h.assertTrue(MedicRuntime.lease(f.medic)==null,"Native lease retained");f.close();releaseChunks.run();});
    }

    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void retreatRequiresOptInAndUsesRecoveryHysteresis(GameTestHelper h){try(var f=fixture(h)){
        h.getLevel().addFreshEntity(f.medic);h.getLevel().addFreshEntity(f.patient);var state=GuardMedicAccess.get(f.patient);f.patient.setHealth(6);
        MedicRuntime.assessRetreat(f.patient);h.assertTrue(!MedicRuntime.retreating(f.patient),"Unconfigured guard retreated");
        state.retreat(true);MedicRuntime.assessRetreat(f.patient);h.assertTrue(MedicRuntime.retreating(f.patient),"Critical guard did not seek medic");
        f.patient.setHealth(12);MedicRuntime.assessRetreat(f.patient);h.assertTrue(MedicRuntime.retreating(f.patient),"Retreat oscillated at intermediate health");
        f.patient.setHealth(16);MedicRuntime.assessRetreat(f.patient);h.assertTrue(!MedicRuntime.retreating(f.patient)&&!state.recovering,"Recovered guard did not resume");h.succeed();
    }}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void retreatHonorsPlayerControlAndMissingSupplies(GameTestHelper h){try(var f=fixture(h)){
        h.getLevel().addFreshEntity(f.medic);h.getLevel().addFreshEntity(f.patient);var state=GuardMedicAccess.get(f.patient);state.retreat(true);f.patient.setHealth(6);f.patient.interacting=true;
        MedicRuntime.assessRetreat(f.patient);h.assertTrue(!MedicRuntime.retreating(f.patient),"Retreat ignored player control");f.patient.interacting=false;
        GuardMedicalAccess.get(f.medic).inventory().setStackInSlot(0,ItemStack.EMPTY);MedicRuntime.assessRetreat(f.patient);
        h.assertTrue(!MedicRuntime.retreating(f.patient)&&state.status==MedicState.Status.NO_SAFE_DESTINATION,"Empty medic accepted as recovery point");h.succeed();
    }}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void roleChangesPreservePouchAndMedicChoices(GameTestHelper h){try(var f=fixture(h)){
        GuardRoleAccess.setRoleId(f.medic,net.minecraft.resources.ResourceLocation.parse("villagerguardgunners:marksman"));
        var root=new net.minecraft.nbt.CompoundTag();f.medic.saveWithoutId(root);var copy=GuardEntityType.GUARD.get().create(h.getLevel());copy.load(root);
        h.assertTrue(GuardMedicAccess.get(copy).medic()&&GuardMedicalAccess.get(copy).inventory().getStackInSlot(0).getCount()==3,"Role change/load lost medical state");h.succeed();
    }}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void disablingDuringChannelPreservesDose(GameTestHelper h){try(var f=fixture(h)){
        var lease=prepare(h,f);boolean old=ModConfigs.GUARD_GUNNERS.medical.medicAi.get();
        try {ModConfigs.GUARD_GUNNERS.medical.medicAi.accept(false);finish(lease);
            h.assertTrue(doses(f)==3&&f.patient.getHealth()==8&&MedicRuntime.lease(f.medic)==null,"Disabled channel spent dose");
        }finally{ModConfigs.GUARD_GUNNERS.medical.medicAi.accept(old);}h.succeed();
    }}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void unloadingPatientReleasesMedicWithoutSpending(GameTestHelper h){try(var f=fixture(h)){
        h.getLevel().addFreshEntity(f.medic);h.getLevel().addFreshEntity(f.patient);var lease=prepare(h,f);f.patient.discard();finish(lease);
        h.assertTrue(doses(f)==3&&MedicRuntime.lease(f.medic)==null,"Removed patient retained treatment");h.succeed();
    }}
    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void retreatRejectsMedicNearThreat(GameTestHelper h){try(var f=fixture(h)){
        var threat=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());threat.setPos(f.medic.position().add(2,0,0));
        f.patient.setTarget(threat);h.assertTrue(!MedicRuntime.safeDestination(f.patient,f.medic),"Medic near threat considered safe");
        threat.discard();h.succeed();
    }}

    @GameTest(templateNamespace="minecraft",template=TEMPLATE,timeoutTicks=120,batch="medic_triage_priority")
    public static void nativeTriagePrioritizesCriticalPatient(GameTestHelper h){
        var f=fixture(h);var critical=guard(h,11);critical.setHealth(2);
        // Nearby combat fixtures can alert guards even after their target goals are
        // removed. Triage selection needs a loaded area away from those attackers.
        var forced=new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        for(Guard actor:new Guard[]{f.medic,f.patient,critical}) {
            // The arena's underground Y coordinate is solid terrain outside its
            // template. Keep this selection-only fixture above terrain instead.
            actor.setPos(actor.getX()+30000,h.getLevel().getMaxBuildHeight()-16,actor.getZ()+30000);
            var chunk=actor.chunkPosition();
            if(!h.getLevel().getForcedChunks().contains(chunk.toLong())) {
                h.getLevel().setChunkForced(chunk.x,chunk.z,true);forced.add(chunk);
            }
            h.getLevel().getChunk(chunk.x,chunk.z);
        }
        Runnable cleanup=()->{f.close();critical.discard();forced.forEach(chunk->h.getLevel().setChunkForced(chunk.x,chunk.z,false));};
        h.runAfterDelay(119,cleanup);
        for(Guard actor:new Guard[]{f.medic,f.patient,critical}) {
            actor.setNoGravity(true);actor.setInvulnerable(true);actor.goalSelector.removeAllGoals(goal->true);actor.targetSelector.removeAllGoals(goal->true);
            h.getLevel().addFreshEntity(actor);
        }
        h.succeedWhen(()->{
            var lease=MedicRuntime.lease(f.medic);
            h.assertTrue(lease!=null,"Triage did not assign patient: "+GuardMedicAccess.get(f.medic).status+", target="+f.medic.getTarget()+", health="+f.medic.getHealth()+"/"+critical.getHealth());
            h.assertTrue(lease.patient==critical,"Triage preferred a less urgent patient");
            cleanup.run();
        });
    }

    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void publicMedicBoundaryRechecksChannelAfterHealingEvent(GameTestHelper h){try(var f=fixture(h)){
        var lease=prepare(h,f);lease.preparedAt-=40;
        listener(e->{if(e.getEntity()==f.patient)f.patient.setPos(f.patient.position().add(1,0,0));},()->
            h.assertTrue(MedicalTreatment.fromMedic(f.medic,f.patient,0,()->true)!=MedicalTreatment.Result.HEALED,"Caller bypassed channel revalidation"));
        h.assertTrue(doses(f)==3&&f.patient.getHealth()==8,"Invalid channel consumed dose");h.succeed();
    }}

    @GameTest(templateNamespace="minecraft",template=TEMPLATE,setupTicks=50)
    public static void failedApproachAllowsAnotherPatientWithoutLoadingChunks(GameTestHelper h){try(var f=fixture(h)){
        var far=h.absolutePos(new BlockPos(4096,2,4096));f.medic.setPos(far.getX()+.5,far.getY(),far.getZ()+.5);f.patient.setPos(f.medic.position().add(7,0,0));
        h.assertTrue(!h.getLevel().hasChunkAt(far),"Fixture destination already loaded");
        h.assertTrue(MedicRuntime.reserve(f.medic,f.patient),"Could not reserve fixture patient");
        var goal=new MedicMovementGoal(f.medic);h.assertTrue(goal.canUse(),"Fixture goal unavailable");goal.start();goal.tick();goal.stop();
        h.assertTrue(!h.getLevel().hasChunkAt(far)&&!MedicRuntime.reserve(f.medic,f.patient),"Failed approach loaded chunks or immediately retried patient");
        var other=guard(h,9);other.setPos(f.medic.position().add(1,0,0));other.setHealth(8);
        try{h.assertTrue(MedicRuntime.reserve(f.medic,other),"Unreachable patient blocked another patient");}finally{MedicRuntime.cancel(f.medic,MedicState.Status.INTERRUPTED);other.discard();}h.succeed();
    }}

}
