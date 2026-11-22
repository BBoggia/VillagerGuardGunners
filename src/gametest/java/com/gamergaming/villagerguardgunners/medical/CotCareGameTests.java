package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.gametest.*;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class CotCareGameTests {
    private static final String ARENA = "villagerguardgunners/gametest_arena";
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_manual_cancellation", timeoutTicks=100)
    public static void manualCancellationInsideHealCallbackCannotSpendADose(GameTestHelper h) {
        var f = new TransportGameTests.Fixture(h);
        deliver(h, f);
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "CotCancelTest"));
        player.setPos(f.patient.position().add(0, 0, 1));
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 200));
        f.patient.setOwnerId(player.getUUID());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BANDAGE.get(), 2));
        boolean[] callback = {false};
        Consumer<LivingHealEvent> listener = event -> {
            if (event.getEntity() == f.patient) {
                callback[0] = true;
                PlayerTreatmentRuntime.cancel(f.patient);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        h.runAtTickTime(60, () -> {
            try {
                h.assertTrue(callback[0] && GuardLifeRuntime.casualty(f.patient)
                        && player.getMainHandItem().getCount() == 2
                        && !PlayerTreatmentRuntime.busy(player) && !PlayerTreatmentRuntime.busy(f.patient),
                        "Canceled completion spent a dose, revived the patient, or retained its claim");
                h.succeed();
            } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
                PlayerTreatmentRuntime.cancel(f.patient); f.close(); player.discard();
            }
        });
        h.assertTrue(MedicalTreatment.fromHand(player, f.patient, InteractionHand.MAIN_HAND) == MedicalTreatment.Result.STARTED,
                "Manual cot treatment did not start");
    }
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_care")
    public static void brokenCotCancelsShortChannelBeforeFieldCare(GameTestHelper h) {
        try (var f = new TransportGameTests.Fixture(h)) {
            deliver(h,f); h.assertTrue(MedicRuntime.reserve(f.carrier,f.patient),"Cot care unavailable");
            var old=MedicRuntime.lease(f.carrier); MedicRuntime.treat(old); old.preparedAt-=40;
            h.getLevel().removeBlock(f.cot,false); MedicRuntime.treat(old);
            h.assertTrue(GuardLifeRuntime.casualty(f.patient)&&GuardMedicalAccess.get(f.carrier).inventory().getStackInSlot(0).getCount()==4,"Broken cot completed a short dose");
            h.assertTrue(MedicRuntime.reserve(f.carrier,f.patient),"Field care unavailable after broken cot");
            var field=MedicRuntime.lease(f.carrier); MedicRuntime.treat(field); field.preparedAt-=40; MedicRuntime.treat(field);
            h.assertTrue(GuardLifeRuntime.casualty(f.patient),"Field care inherited the cot channel");
        } h.succeed();
    }
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_care")
    public static void failedDestinationAllowsAnotherCot(GameTestHelper h) {
        try (var f = new TransportGameTests.Fixture(h)) {
            var second=f.cot.north(3); f.placeCot(second);
            try {
                var first=f.reserve(); h.assertTrue(first.cot.equals(f.cot),"Unexpected first cot");
                TransportRuntime.failed(f.carrier); GuardMedicAccess.get(f.carrier).nextTransportAttempt=0;
                h.assertTrue(TransportRuntime.tryStart(f.carrier,f.patient)&&TransportRuntime.lease(f.carrier).cot.equals(second),"Repeated failed cot instead of trying another");
            } finally { h.getLevel().removeBlock(second,false); h.getLevel().removeBlock(second.west(),false); }
        } h.succeed();
    }
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_care")
    public static void unloadingFurniturePreservesTheSavedRestingRelationship(GameTestHelper h) {
        try (var f = new TransportGameTests.Fixture(h)) {
            deliver(h,f); GuardLifeRuntime.completeTreatment(f.patient,4,40);
            var cot=(RecoveryCotBlockEntity)h.getLevel().getBlockEntity(f.cot); cot.onChunkUnloaded();
            h.assertTrue(f.cot.equals(GuardLifeAccess.cot(f.patient).head())&&GuardLifeAccess.get(f.patient).remaining()==100,"Unload treated cot as destroyed");
            TransportRuntime.cotLoaded(cot); CotRestRuntime.tick(f.patient);
            h.assertTrue(TransportRuntime.onCot(f.patient),"Loaded patient did not reclaim cot");
        } h.succeed();
    }
    private static void deliver(GameTestHelper h, TransportGameTests.Fixture f) {
        var lease = f.carry();
        f.carrier.setPos(Vec3.atBottomCenterOf(f.cot.offset(-2,0,0))); f.carrier.positionRider(f.patient);
        h.assertTrue(TransportRuntime.deliver(lease), "Delivery did not establish cot occupancy");
    }
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_care")
    public static void exactCotAnchorAndAllFacingsKeepPatientOnMattress(GameTestHelper h) {
        try (var f = new TransportGameTests.Fixture(h)) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                h.getLevel().removeBlock(f.cot, false); h.getLevel().removeBlock(f.cot.west(), false);
                var state = ModBlocks.RECOVERY_COT.get().defaultBlockState().setValue(RecoveryCotBlock.FACING, facing);
                var foot = f.cot.relative(facing.getOpposite());
                h.getLevel().setBlock(foot, state.setValue(RecoveryCotBlock.PART, BedPart.FOOT), 3);
                h.getLevel().setBlock(f.cot, state.setValue(RecoveryCotBlock.PART, BedPart.HEAD), 3);
                f.patient.setPos(TransportRuntime.cotPosition(h.getLevel(), f.cot));
                h.assertTrue(!TransportRuntime.onCot(f.patient), "Proximity alone granted cot care");
                f.patient.walkAnimation.update(1, 1); f.patient.walkAnimation.update(1, 1);
                h.assertTrue(CotRestRuntime.attach(f.patient, f.cot), "Could not attach " + facing);
                var exact = f.patient.position(); f.patient.setDeltaMovement(1,1,1); f.patient.travel(Vec3.ZERO); CotRestRuntime.tick(f.patient);
                h.assertTrue(f.patient.position().distanceToSqr(exact) < 1e-8 && f.patient.getDeltaMovement().lengthSqr() == 0
                        && GuardLifeAccess.cot(f.patient).facing() == facing && !f.patient.isPushable(), "Cot position/facing drifted");
                for(float partial : new float[]{0, .5F, 1}) h.assertTrue(f.patient.walkAnimation.speed(partial) == 0,
                        "Resting legs retained an interpolated walking animation");
                CotRestRuntime.release(f.patient, false);
                f.patient.walkAnimation.update(.5F, 1);
                h.assertTrue(f.patient.walkAnimation.isMoving(), "Walking animation stayed locked after cot release");
                h.getLevel().removeBlock(f.cot, false); h.getLevel().removeBlock(foot, false);
            }
        } h.succeed();
    }
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_care")
    public static void cotOccupancyPersistsAndRestExitsBesideFurniture(GameTestHelper h) {
        try (var f = new TransportGameTests.Fixture(h)) {
            deliver(h,f); var saved = new CompoundTag(); f.patient.save(saved); var id = f.patient.getUUID();
            CotRestRuntime.release(f.patient,false);
            var restored = (Guard)EntityType.loadEntityRecursive(saved,h.getLevel(),e->e);
            try {
                CotRestRuntime.tick(restored); h.assertTrue(TransportRuntime.onCot(restored) && restored.getUUID().equals(id), "Reload lost cot or patient identity");
                GuardLifeRuntime.completeTreatment(restored,4,40);
                for(int i=0;i<100;i++)GuardLifeAccess.get(restored).tick(true);
                h.assertTrue(CotRestRuntime.occupied(h.getLevel(),f.cot,f.carrier), "Cot freed before rest ended");
                CotRestRuntime.tick(restored); GuardLifeRuntime.tick(restored);
                h.assertTrue(!GuardLifeRuntime.blocked(restored) && !CotRestRuntime.anchored(restored)
                        && restored.getY()==f.cot.getY() && TransportRuntime.safeStanding(restored,restored.position()), "Patient did not exit beside cot");
            } finally { CotRestRuntime.release(restored,false); restored.discard(); }
        } h.succeed();
    }
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_care")
    public static void brokenCotPreservesRevivalAndCreditsCompletedRest(GameTestHelper h) {
        try (var f = new TransportGameTests.Fixture(h)) {
            deliver(h,f); GuardLifeRuntime.completeTreatment(f.patient,4,40);
            for(int i=0;i<30;i++)GuardLifeAccess.get(f.patient).tick(true);
            h.getLevel().removeBlock(f.cot,false);
            h.assertTrue(!CotRestRuntime.anchored(f.patient) && GuardLifeAccess.get(f.patient).stage()==GuardLifeState.Stage.RECOVERING
                    && GuardLifeAccess.get(f.patient).remaining()==370 && f.patient.getHealth()==4, "Broken cot reset revival or shortened recovery");
        } h.succeed();
    }
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_care")
    public static void blockedExitRetainsOccupancyUntilGroundIsClear(GameTestHelper h) {
        try (var f = new TransportGameTests.Fixture(h)) {
            deliver(h,f); GuardLifeRuntime.completeTreatment(f.patient,4,40);
            for(int x=-2;x<=1;x++)for(int z=-1;z<=1;z++)if(!(z==0&&(x==-1||x==0)))h.getLevel().setBlock(f.cot.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
            for(int i=0;i<100;i++)GuardLifeAccess.get(f.patient).tick(true);
            CotRestRuntime.tick(f.patient); GuardLifeRuntime.tick(f.patient);
            h.assertTrue(CotRestRuntime.anchored(f.patient)&&GuardLifeRuntime.blocked(f.patient), "Patient exited into a block");
            h.getLevel().removeBlock(f.cot.south(),false); GuardLifeAccess.cot(f.patient).nextExitAttempt=0;
            CotRestRuntime.tick(f.patient); GuardLifeRuntime.tick(f.patient);
            h.assertTrue(!CotRestRuntime.anchored(f.patient)&&!GuardLifeRuntime.blocked(f.patient), "Clear exit did not release patient");
        } h.succeed();
    }
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_care")
    public static void fieldDoseRequiresFullChannelAndSpendsOneSupply(GameTestHelper h) {
        try (var f = new TransportGameTests.Fixture(h)) {
            h.assertTrue(MedicRuntime.reserve(f.carrier,f.patient), "Could not reserve field care");
            var lease=MedicRuntime.lease(f.carrier); MedicRuntime.treat(lease); lease.preparedAt-=40;
            MedicRuntime.treat(lease);
            h.assertTrue(GuardLifeRuntime.casualty(f.patient)&&GuardMedicalAccess.get(f.carrier).inventory().getStackInSlot(0).getCount()==4, "Field revival bypassed long channel");
            lease.preparedAt-=120; MedicRuntime.treat(lease);
            h.assertTrue(GuardLifeAccess.get(f.patient).remaining()==400&&f.patient.getHealth()==4
                    && GuardMedicalAccess.get(f.carrier).inventory().getStackInSlot(0).getCount()==3, "Field dose or rest duration incorrect");
        } h.succeed();
    }
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_care")
    public static void transportOffKeepsShortRevivalAndUrgencyAvoidsEvacuation(GameTestHelper h) {
        try (var f = new TransportGameTests.Fixture(h)) {
            var life = new CompoundTag(); GuardLifeAccess.get(f.patient).save(life); life.getCompound(GuardLifeState.KEY).putInt("Remaining",180); GuardLifeAccess.get(f.patient).load(life);
            h.assertTrue(!TransportRuntime.tryStart(f.carrier,f.patient), "Urgent patient was sent on another trip");
            ModConfigs.GUARD_GUNNERS.medical.transport.accept(false);
            h.assertTrue(CasualtyCarePolicy.forPatient(f.patient).channelTicks()==40 && CasualtyCarePolicy.forPatient(f.patient).recoveryTicks()==100, "Disabled transport changed standalone revival");
        } h.succeed();
    }
    @GameTest(templateNamespace="minecraft", template=ARENA, batch="cot_manual", timeoutTicks=450)
    public static void playerHandAndPouchRevivalAreTimedAndCanceledSupplyCostsNothing(GameTestHelper h) {
        var f = new TransportGameTests.Fixture(h);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"CotCareTest"));
        player.setPos(f.patient.position().add(1,0,0)); player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,1000)); f.patient.setOwnerId(player.getUUID());
        var initial=new CompoundTag(); GuardLifeAccess.get(f.patient).save(initial);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.BANDAGE.get(),3));
        h.assertTrue(MedicalTreatment.fromHand(player,f.patient,InteractionHand.MAIN_HAND)==MedicalTreatment.Result.STARTED,"Player revival was not timed");
        h.runAtTickTime(50,()->{
            h.assertTrue(GuardLifeRuntime.casualty(f.patient)&&player.getMainHandItem().getCount()==3,"Manual dose finished early");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.BANDAGE.get(),2));
        });
        h.runAtTickTime(60,()->{
            h.assertTrue(!PlayerTreatmentRuntime.busy(f.patient)&&player.getMainHandItem().getCount()==2,"Canceled channel spent a dose or kept claim");
            h.assertTrue(MedicalTreatment.fromHand(player,f.patient,InteractionHand.MAIN_HAND)==MedicalTreatment.Result.STARTED,"Manual retry failed");
        });
        h.runAtTickTime(230,()->{
            h.assertTrue(!GuardLifeRuntime.casualty(f.patient)&&player.getMainHandItem().getCount()==1&&GuardLifeAccess.get(f.patient).remaining()==400,"Hand completion lost dose or recovery timing");
            GuardLifeAccess.get(f.patient).load(initial); f.patient.setHealth(1); GuardMedicalAccess.get(f.patient).readyAt(0);
            GuardMedicalAccess.get(f.patient).inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get(),2));
            h.assertTrue(MedicalTreatment.fromPouch(player,f.patient)==MedicalTreatment.Result.STARTED,"Pouch revival bypassed channel");
        });
        h.runAtTickTime(410,()->{
            try { h.assertTrue(!GuardLifeRuntime.casualty(f.patient)&&GuardMedicalAccess.get(f.patient).inventory().getStackInSlot(0).getCount()==1,"Pouch completion failed"); h.succeed(); }
            finally { PlayerTreatmentRuntime.cancel(f.patient); f.close(); player.discard(); }
        });
    }
}
