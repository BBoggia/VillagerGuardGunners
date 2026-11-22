package com.gamergaming.villagerguardgunners.qa;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.medical.*;
import com.gamergaming.villagerguardgunners.init.*;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;
@Mod.EventBusSubscriber(modid="villagerguardgunners",value=Dist.CLIENT)
public final class VillageClientQa {
 static final Path DIR=Path.of(System.getProperty("village.qa.dir"));
 static int ticks,stage,entered; static String capture; static volatile int carrierId,patientId;static volatile String serverError;static volatile boolean verified;static ItemStack originalGun;static java.util.UUID originalPatient;
 static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
 static void advance(){stage++;entered=ticks;System.out.println("VGG_CLIENT_QA stage="+stage+" tick="+ticks);}
 static void finish(String result){try{Files.writeString(DIR.resolve("client-qa-result.txt"),result+"\n");}catch(Exception ignored){}Minecraft.getInstance().stop();}
 @SubscribeEvent public static void render(TickEvent.RenderTickEvent e){if(e.phase!=TickEvent.Phase.END||capture==null)return;try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(DIR.resolve(capture));capture=null;}catch(Exception x){finish("FAIL "+x);}}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;ticks++;if(ticks>2400){finish("FAIL timeout "+stage);return;}try{
 if(serverError!=null)throw new AssertionError(serverError);
 if(stage==0&&mc.player!=null&&mc.getSingleplayerServer()!=null){var server=mc.getSingleplayerServer();var id=mc.player.getUUID();mc.setScreen(null);mc.options.hideGui=true;server.execute(()->{try{
  var player=server.getPlayerList().getPlayer(id);var level=player.serverLevel();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  for(var entity:level.getEntitiesOfClass(Guard.class,new net.minecraft.world.phys.AABB(-30,80,-30,40,120,30)))entity.discard();
  for(int x=-10;x<=24;x++)for(int z=-16;z<=10;z++){level.setBlock(new BlockPos(x,99,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);for(int y=100;y<=106;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
  player.removeAllEffects();player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,6000,0,false,false));player.teleportTo(level,8,100,-11,0,6);ModConfigs.GUARD_GUNNERS.medical.downed.accept(true);ModConfigs.GUARD_GUNNERS.medical.transport.accept(true);
  var cot=ModBlocks.RECOVERY_COT.get().defaultBlockState().setValue(RecoveryCotBlock.FACING,Direction.EAST);level.setBlock(new BlockPos(14,100,0),cot.setValue(RecoveryCotBlock.PART,BedPart.FOOT),3);level.setBlock(new BlockPos(15,100,0),cot.setValue(RecoveryCotBlock.PART,BedPart.HEAD),3);
  var carrier=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);var patient=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
  for(var g:java.util.List.of(carrier,patient)){g.setOwnerId(id);g.setPersistenceRequired();g.goalSelector.removeAllGoals(goal->!(goal instanceof TransportMovementGoal)&&!(goal instanceof MedicMovementGoal));g.targetSelector.removeAllGoals(goal->true);var data=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(g).getDataHolder();data.drawTimestamp=data.shootTimestamp=-1;data.meleePrepTickCount=-1;}
  carrier.setPos(.5,100,.5);patient.setPos(2.5,100,.5);carrier.setCustomName(Component.literal("Medic"));patient.setCustomName(Component.literal("Casualty"));
  GuardMedicAccess.get(carrier).medic(true);
  // Supply the medic through the real Quartermaster transaction before injury.
  var stationPos=new BlockPos(-2,100,0);level.setBlock(stationPos,ModBlocks.QUARTERMASTER.get().defaultBlockState(),3);
  var station=(com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity)level.getBlockEntity(stationPos);station.placedBy(player);
  station.inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get(),4));
  var operator=new net.minecraft.world.entity.npc.Villager(net.minecraft.world.entity.EntityType.VILLAGER,level);operator.setPos(-1.5,100,1.5);
  operator.setVillagerData(operator.getVillagerData().setProfession(net.minecraftforge.registries.ForgeRegistries.VILLAGER_PROFESSIONS.getValue(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterProfession.ID)));
  operator.refreshBrain(level);operator.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,GlobalPos.of(level.dimension(),stationPos));level.addFreshEntity(operator);station.operator=operator.getUUID();
  var service=GuardEquipmentServiceAccess.get(carrier);service.policy(com.gamergaming.villagerguardgunners.quartermaster.service.GuardServiceState.Policy.PROFILE);service.profile(new net.minecraft.resources.ResourceLocation("villagerguardgunners","rifleman"));
  var plan=com.gamergaming.villagerguardgunners.quartermaster.service.ServicePlanner.plan(carrier,station,com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime.sources(station));
  require(plan!=null&&com.gamergaming.villagerguardgunners.quartermaster.service.ServiceTransactions.transfer(plan)==com.gamergaming.villagerguardgunners.quartermaster.service.ServiceStatus.SUPPLIED,"Quartermaster did not supply medic");
  require(station.inventory().getStackInSlot(0).isEmpty()&&GuardMedicalAccess.get(carrier).inventory().getStackInSlot(0).getCount()==4,"Quartermaster supply conservation failed");
  service.policy(com.gamergaming.villagerguardgunners.quartermaster.service.GuardServiceState.Policy.MANUAL);operator.discard();
  originalGun=com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(new net.minecraft.resources.ResourceLocation("tacz","ak47")).setAmmoCount(17).build();patient.setItemSlot(EquipmentSlot.MAINHAND,originalGun.copy());GuardMedicalAccess.get(patient).inventory().setStackInSlot(0,new ItemStack(ModItems.BANDAGE.get(),7));
  originalPatient=patient.getUUID();level.addFreshEntity(carrier);level.addFreshEntity(patient);carrierId=carrier.getId();patientId=patient.getId();patient.setHealth(2);patient.hurt(level.damageSources().generic(),100);require(GuardLifeRuntime.casualty(patient),"Server down failed");
 }catch(Throwable x){serverError=x.toString();}});advance();}
 else if(stage>=1&&mc.level!=null&&mc.level.getEntity(patientId) instanceof Guard patient&&mc.level.getEntity(carrierId) instanceof Guard carrier){
  if(stage==1&&ticks-entered>=15&&((GuardLifeAccess)patient).villagerGuardGunners$getLifeVisual()==1){mc.getToasts().clear();capture="client-transport-downed.png";advance();}
  else if(stage==2&&patient.getVehicle()==carrier&&carrier.getX()>4){require(carrier.getControllingPassenger()==null,"Passenger took control");capture="client-transport-carrying.png";advance();}
  else if(stage==3&&!patient.isPassenger()&&patient.getX()>13&&((GuardLifeAccess)patient).villagerGuardGunners$getLifeVisual()==2){capture="client-transport-cot.png";advance();}
  else if(stage==4&&((GuardLifeAccess)patient).villagerGuardGunners$getLifeVisual()==0){capture="client-transport-recovered.png";var patientUuid=patient.getUUID();mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();var p=(Guard)level.getEntity(patientId);var c=(Guard)level.getEntity(carrierId);require(p.getUUID().equals(originalPatient)&&p.getUUID().equals(patientUuid)&&ItemStack.matches(p.getMainHandItem(),originalGun)&&GuardMedicalAccess.get(p).inventory().getStackInSlot(0).getCount()==7,"Patient identity/items changed");require(GuardMedicalAccess.get(c).inventory().getStackInSlot(0).getCount()==2,"Two-dose supply accounting failed");require(!TransportRuntime.busy(c)&&!p.isPassenger(),"Carry remained after recovery");verified=true;}catch(Throwable x){serverError=x.toString();}});advance();}
  else if(stage==5&&verified&&capture==null&&ticks-entered>10){finish("PASS: Quartermaster medical supply, automatic first aid, visible original passenger transport, cot delivery, automatic second-dose revival, client synchronization, two-dose supply conservation and release; four screenshots captured");advance();}
 }
 }catch(Throwable x){finish("FAIL stage="+stage+": "+x);}}
}
