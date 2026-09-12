package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.*;
import com.gamergaming.villagerguardgunners.medical.*;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.gametest.*;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class CasualtyGameTests {
    private static final String ARENA="villagerguardgunners/gametest_arena";
    private static final class Fixture implements AutoCloseable {
        final Guard guard; final FakePlayer player; final boolean old=ModConfigs.GUARD_GUNNERS.medical.downed.get();
        Fixture(GameTestHelper h) {
            ModConfigs.GUARD_GUNNERS.medical.downed.accept(true);guard=GuardEntityType.GUARD.get().create(h.getLevel());
            var p=h.absolutePos(new BlockPos(8,2,8));guard.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);
            player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"CasualtyCare"));player.setPos(guard.position().add(1,0,0));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,1000));
            guard.setOwnerId(player.getUUID());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.BANDAGE.get(),4));
        }
        void down(GameTestHelper h) { guard.setHealth(2);guard.invulnerableTime=0;guard.hurt(h.getLevel().damageSources().generic(),100);h.assertTrue(GuardLifeRuntime.casualty(guard)&&guard.isAlive(),"Did not enter downed state"); }
        MedicalTreatment.Result treat(){GuardMedicalAccess.get(guard).readyAt(0);return MedicalTreatment.fromHand(player,guard,InteractionHand.MAIN_HAND);}
        @Override public void close(){MedicRuntime.cancel(guard,MedicState.Status.INTERRUPTED);guard.discard();ModConfigs.GUARD_GUNNERS.medical.downed.accept(old);}
    }
    private static <T extends net.minecraftforge.eventbus.api.Event> void listen(Class<T> type,Consumer<T> listener,Runnable action) {
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,type,listener);
        try{action.run();}finally{MinecraftForge.EVENT_BUS.unregister(listener);}
    }
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void defaultOffKeepsOrdinaryDeath(GameTestHelper h){try(var f=new Fixture(h)){ModConfigs.GUARD_GUNNERS.medical.downed.accept(false);int[] deaths={0};listen(LivingDeathEvent.class,e->{if(e.getEntity()==f.guard)deaths[0]++;},()->f.guard.hurt(h.getLevel().damageSources().generic(),1000));h.assertTrue(deaths[0]==1&&!f.guard.isAlive()&&!GuardLifeRuntime.casualty(f.guard),"Default-off intercepted death");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void downingPreservesIdentityEquipmentAndStopsActions(GameTestHelper h){try(var f=new Fixture(h)){var id=f.guard.getUUID();var sword=new ItemStack(Items.IRON_SWORD);sword.getOrCreateTag().putString("Owner","original");f.guard.setItemSlot(EquipmentSlot.MAINHAND,sword.copy());GuardMedicalAccess.get(f.guard).inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get(),7));int[] deaths={0};listen(LivingDeathEvent.class,e->{if(e.getEntity()==f.guard)deaths[0]++;},()->f.down(h));h.assertTrue(deaths[0]==0&&id.equals(f.guard.getUUID())&&ItemStack.matches(sword,f.guard.getMainHandItem())&&GuardMedicalAccess.get(f.guard).inventory().getStackInSlot(0).getCount()==7,"Downing lost identity/items or emitted death");h.assertTrue(!f.guard.isEffectiveAi()&&!f.guard.isNoAi()&&GuardBurstSafetyAccess.shouldSuppress(f.guard)&&!((GuardCoverageAccess)f.guard).villagerGuardGunners$nativeEquipmentServiceSafe(),"Casualty actions remained available");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void twoDosesReviveOriginalGuardAtLowHealth(GameTestHelper h){try(var f=new Fixture(h)){var id=f.guard.getUUID();f.down(h);h.assertTrue(f.treat()==MedicalTreatment.Result.HEALED&&GuardLifeAccess.get(f.guard).stage()==GuardLifeState.Stage.STABILIZED&&f.guard.getHealth()==1,"First dose did not stabilize");h.assertTrue(f.treat()==MedicalTreatment.Result.HEALED&&GuardLifeAccess.get(f.guard).stage()==GuardLifeState.Stage.RECOVERING&&f.guard.getHealth()==4&&f.player.getMainHandItem().getCount()==2&&id.equals(f.guard.getUUID()),"Revival or dose count wrong");for(int i=0;i<100;i++)GuardLifeRuntime.tick(f.guard);h.assertTrue(!GuardLifeRuntime.blocked(f.guard)&&f.guard.isEffectiveAi(),"Recovery stranded guard");f.guard.invulnerableTime=0;f.guard.hurt(h.getLevel().damageSources().generic(),1000);h.assertTrue(!f.guard.isAlive(),"Cooldown permitted immediate repeated downing");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void canceledRevivalCostsNothing(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);listen(LivingHealEvent.class,e->{if(e.getEntity()==f.guard)e.setCanceled(true);},()->h.assertTrue(f.treat()==MedicalTreatment.Result.CANCELED,"Canceled event accepted"));h.assertTrue(f.player.getMainHandItem().getCount()==4&&GuardLifeAccess.get(f.guard).stage()==GuardLifeState.Stage.DOWNED,"Canceled dose changed state/items");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void totemPrecedesDowning(GameTestHelper h){try(var f=new Fixture(h)){f.guard.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.TOTEM_OF_UNDYING));f.guard.hurt(h.getLevel().damageSources().generic(),1000);h.assertTrue(f.guard.isAlive()&&f.guard.getOffhandItem().isEmpty()&&!GuardLifeRuntime.blocked(f.guard),"Downing bypassed totem");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void bypassDamageDiesOnce(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);int[] deaths={0},drops={0};listen(LivingDeathEvent.class,e->{if(e.getEntity()==f.guard)deaths[0]++;},()->listen(LivingDropsEvent.class,e->{if(e.getEntity()==f.guard)drops[0]++;},()->{f.guard.invulnerableTime=0;f.guard.hurt(h.getLevel().damageSources().fellOutOfWorld(),1000);f.guard.die(h.getLevel().damageSources().fellOutOfWorld());}));h.assertTrue(deaths[0]==1&&drops[0]==1&&!f.guard.isAlive(),"Bypass did not use ordinary pipeline once: "+deaths[0]+"/"+drops[0]);h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void bleedoutReachesDeathOnce(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);for(int i=0;i<1199;i++)GuardLifeAccess.get(f.guard).tick();int[] deaths={0};listen(LivingDeathEvent.class,e->{if(e.getEntity()==f.guard)deaths[0]++;},()->{GuardLifeRuntime.tick(f.guard);GuardLifeRuntime.tick(f.guard);});h.assertTrue(deaths[0]==1&&!f.guard.isAlive(),"Bleedout did not die exactly once");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void disablingResolvesCasualtyWithoutFullHeal(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);ModConfigs.GUARD_GUNNERS.medical.downed.accept(false);for(int i=0;i<100;i++)GuardLifeRuntime.tick(f.guard);h.assertTrue(f.guard.isAlive()&&f.guard.getHealth()==4&&!GuardLifeRuntime.blocked(f.guard)&&f.guard.isEffectiveAi(),"Disabled feature stranded or fully healed guard");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void saveReloadPreservesTimerIdentityAndNoAiChoice(GameTestHelper h){try(var f=new Fixture(h)){f.guard.setNoAi(true);f.down(h);for(int i=0;i<50;i++)GuardLifeRuntime.tick(f.guard);var tag=new CompoundTag();f.guard.saveWithoutId(tag);Guard copy=GuardEntityType.GUARD.get().create(h.getLevel());try{copy.load(tag);h.assertTrue(copy.getUUID().equals(f.guard.getUUID())&&GuardLifeAccess.get(copy).remaining()==1150&&copy.isNoAi(),"Saved casualty lost timer/identity/NoAI");ModConfigs.GUARD_GUNNERS.medical.downed.accept(false);for(int i=0;i<100;i++)GuardLifeRuntime.tick(copy);h.assertTrue(!GuardLifeRuntime.blocked(copy)&&copy.isNoAi(),"Recovery changed deliberate NoAI");}finally{copy.discard();}h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void finishingDamageUsesTimerAndSameTickGrace(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);f.guard.invulnerableTime=0;f.guard.hurt(h.getLevel().damageSources().generic(),100);h.assertTrue(GuardLifeAccess.get(f.guard).remaining()==1200&&f.guard.isAlive(),"Split damage immediately finished casualty");GuardLifeAccess.get(f.guard).lastDamageTick=Long.MIN_VALUE;f.guard.invulnerableTime=0;f.guard.hurt(h.getLevel().damageSources().generic(),2);h.assertTrue(GuardLifeAccess.get(f.guard).remaining()==1160&&f.guard.getHealth()==1,"Finishing damage did not shorten window");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void canceledLethalDamageDoesNotDown(GameTestHelper h){try(var f=new Fixture(h)){listen(LivingDamageEvent.class,e->{if(e.getEntity()==f.guard)e.setCanceled(true);},()->f.guard.hurt(h.getLevel().damageSources().generic(),1000));h.assertTrue(!GuardLifeRuntime.blocked(f.guard)&&f.guard.getHealth()==f.guard.getMaxHealth(),"Canceled damage downed guard");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void ordinaryFoodAndRegenerationCannotRevive(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);f.guard.heal(100);f.guard.eat(h.getLevel(),new ItemStack(Items.BREAD));h.assertTrue(f.guard.getHealth()==1&&GuardLifeRuntime.casualty(f.guard),"Ordinary healing bypassed revival");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA,setupTicks=50)
    public static void medicCanTreatCasualtyDespiteSavedPlayerOrders(GameTestHelper h){try(var f=new Fixture(h)){Guard medic=GuardEntityType.GUARD.get().create(h.getLevel());try{medic.setPos(f.guard.position().add(1,0,0));var data=IGunOperator.fromLivingEntity(medic).getDataHolder();data.drawTimestamp=data.shootTimestamp=-1;data.meleePrepTickCount=-1;GuardMedicAccess.get(medic).medic(true);GuardMedicalAccess.get(medic).inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get(),3));f.down(h);f.guard.interacting=true;for(int i=0;i<2;i++){GuardMedicalAccess.get(f.guard).readyAt(0);h.assertTrue(MedicRuntime.reserve(medic,f.guard),"Medic cannot reserve casualty");var lease=MedicRuntime.lease(medic);MedicRuntime.treat(lease);h.assertTrue(lease.preparedAt>=0,"Casualty channel failed");lease.preparedAt-=40;MedicRuntime.treat(lease);}h.assertTrue(GuardLifeAccess.get(f.guard).stage()==GuardLifeState.Stage.RECOVERING&&GuardMedicalAccess.get(medic).inventory().getStackInSlot(0).getCount()==1,"Medic did not stabilize/revive with two doses");}finally{MedicRuntime.cancel(medic,MedicState.Status.INTERRUPTED);medic.discard();}h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void recursiveHealingDoesNotBypassOrDoubleRevive(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);listen(LivingHealEvent.class,e->{if(e.getEntity()==f.guard){f.guard.heal(100);h.assertTrue(f.treat()==MedicalTreatment.Result.BUSY,"Recursive treatment accepted");}},()->f.treat());h.assertTrue(f.guard.getHealth()==1&&f.player.getMainHandItem().getCount()==3&&GuardLifeAccess.get(f.guard).stage()==GuardLifeState.Stage.STABILIZED,"Recursive callback bypassed revival");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void activeSidearmLayoutSurvivesDowningRevivalAndReload(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var primary=com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(net.minecraft.resources.ResourceLocation.parse("tacz:ak47")).build();
            var sidearm=com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(net.minecraft.resources.ResourceLocation.parse("tacz:glock_17")).build();
            f.guard.setItemSlot(EquipmentSlot.MAINHAND,primary);
            var storage=com.gamergaming.villagerguardgunners.sidearm.GuardSidearmGameTestAccess.getStorage(f.guard);
            storage.setStackInSlot(com.gamergaming.villagerguardgunners.sidearm.GuardSidearmStorage.SIDEARM_SLOT,sidearm);
            var result=com.gamergaming.villagerguardgunners.sidearm.GuardSidearmTransactions.activate(f.guard,com.gamergaming.villagerguardgunners.sidearm.SidearmActivationReason.PRIMARY_DRY,100,UUID.randomUUID(),0,true);
            h.assertTrue(result.status()==com.gamergaming.villagerguardgunners.sidearm.SidearmTransitionStatus.ACTIVATED,"Sidearm fixture not active");
            var held=f.guard.getMainHandItem().copy();var stowed=GuardSidearmAccess.getEffectivePrimaryCopy(f.guard);
            f.down(h);f.treat();f.treat();var tag=new CompoundTag();f.guard.saveWithoutId(tag);var copy=GuardEntityType.GUARD.get().create(h.getLevel());
            try { copy.load(tag);h.assertTrue(ItemStack.matches(held,copy.getMainHandItem())&&ItemStack.matches(stowed,GuardSidearmAccess.getEffectivePrimaryCopy(copy))
                && GuardSidearmAccess.getState(copy)==com.gamergaming.villagerguardgunners.sidearm.SidearmState.SIDEARM_ACTIVE,"Casualty copied/lost/reassigned active hand owners"); }
            finally{copy.discard();}h.succeed();
        }
    }
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void disablingAllTreatmentResolvesLoadedCasualty(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);boolean old=ModConfigs.GUARD_GUNNERS.medical.enabled.get();try{ModConfigs.GUARD_GUNNERS.medical.enabled.accept(false);GuardLifeRuntime.tick(f.guard);h.assertTrue(GuardLifeAccess.get(f.guard).stage()==GuardLifeState.Stage.RECOVERING&&f.guard.getHealth()==4,"No medical path left casualty downed");}finally{ModConfigs.GUARD_GUNNERS.medical.enabled.accept(old);}h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void canceledFinishingDamageLeavesTimerUnchanged(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);GuardLifeAccess.get(f.guard).lastDamageTick=Long.MIN_VALUE;f.guard.invulnerableTime=0;listen(LivingDamageEvent.class,e->{if(e.getEntity()==f.guard)e.setCanceled(true);},()->f.guard.hurt(h.getLevel().damageSources().generic(),3));h.assertTrue(GuardLifeAccess.get(f.guard).remaining()==1200,"Canceled finishing damage shortened timer");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void changedDoseDuringRevivalCannotAdvanceState(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);listen(LivingHealEvent.class,e->{if(e.getEntity()==f.guard)f.player.getMainHandItem().getOrCreateTag().putBoolean("Changed",true);},()->f.treat());h.assertTrue(f.player.getMainHandItem().getCount()==4&&GuardLifeAccess.get(f.guard).stage()==GuardLifeState.Stage.DOWNED,"Changed source spent or revived");h.succeed();}}
    @GameTest(templateNamespace="minecraft",template=ARENA)
    public static void deathDuringTreatmentDoesNotSpendDose(GameTestHelper h){try(var f=new Fixture(h)){f.down(h);listen(LivingHealEvent.class,e->{if(e.getEntity()==f.guard){f.guard.invulnerableTime=0;f.guard.hurt(h.getLevel().damageSources().fellOutOfWorld(),1000);}},()->f.treat());h.assertTrue(f.player.getMainHandItem().getCount()==4&&!f.guard.isAlive(),"Death callback spent dose or revived dead guard");h.succeed();}}
}
