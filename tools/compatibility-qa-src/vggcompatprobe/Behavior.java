package vggcompatprobe;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import tallestegg.guardvillagers.entities.Guard;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.resources.ResourceLocation;

/** Controlled checkpoint and live combat probes in untouched generated terrain. */
final class Behavior {
 static final boolean FOCUSED=Boolean.getBoolean("vgg.compat.focused");
 static int preparedAt;static boolean selected;static List<Guard> candidates;static boolean supplied;
 static final List<Object> damage=new ArrayList<>();
 static final List<Trial> trials=new ArrayList<>();static LivingEntity threat;static Guard fighter;static float threatHealth;static int minAmmo=999,initialAmmo;static boolean threatDead;static List<Object> combatSamples=new ArrayList<>();
 static class Trial {Guard guard;BlockPos target,old;boolean patrolling,created;Vec3 origin,last;double walked,minDistance;Map<String,Object> qualification;float health;Trial(Guard g,BlockPos t,boolean c){guard=g;target=t;old=g.getPatrolPos();patrolling=g.isPatrolling();created=c;origin=last=g.position();minDistance=g.position().distanceTo(Vec3.atBottomCenterOf(t));health=g.getHealth();}}
 static List<BlockPos> destinations(){return Probe.level.getEntitiesOfClass(Villager.class,Probe.area).stream().map(v->v.blockPosition().immutable()).distinct().toList();}
 static boolean safe(BlockPos p){return Probe.level.getFluidState(p).isEmpty()&&Probe.level.getBlockState(p).getCollisionShape(Probe.level,p).isEmpty()&&Probe.level.getBlockState(p.above()).getCollisionShape(Probe.level,p.above()).isEmpty()&&Probe.level.getBlockState(p.below()).isFaceSturdy(Probe.level,p.below(),net.minecraft.core.Direction.UP);}
 static void begin(){
  var guards=new ArrayList<>(Probe.level.getEntitiesOfClass(Guard.class,Probe.area));boolean created=false;
  if(guards.isEmpty()){
   // Explicitly labeled supplied guards; never count these as natural generation.
   for(var anchor:destinations()){
    if(guards.size()>=3)break;
    BlockPos pos=null;outer:for(int r=1;r<=4;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++)for(int dy=-1;dy<=1;dy++){var p=anchor.offset(dx,dy,dz);if(safe(p)){pos=p;break outer;}}
    if(pos==null)continue;var g=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(Probe.level);g.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);if(!Probe.level.noCollision(g))continue;g.setPersistenceRequired();g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));if(Probe.level.addFreshEntity(g))guards.add(g);
   }created=true;
  }
  candidates=guards;supplied=created;preparedAt=Probe.ticks;
  Probe.result.put("checkpointCandidates",guards.size());Probe.result.put("controlledGuardsAdded",created?guards.size():0);
  if(!FOCUSED)selectTrials();
 }
 static void selectTrials(){
  selected=true;int queries=0,reachable=0;List<Object> attempts=new ArrayList<>();
  for(var guard:candidates.stream().limit(4).toList()){
   BlockPos chosen=null;double score=0;Map<String,Object> qualification=null;
   for(var target:destinations()){
    double distance=guard.position().distanceTo(Vec3.atBottomCenterOf(target));if(distance<8||distance>48)continue;
    queries++;var path=guard.getNavigation().createPath(target,FOCUSED?1:0);
    String status=path==null?"no_path":!path.canReach()?"incomplete_path":path.getEndNode()==null?"missing_endpoint":"reachable";
    attempts.add(Map.of("guard",guard.getUUID(),"requestedTarget",List.of(target.getX(),target.getY(),target.getZ()),"status",status));
    if(!status.equals("reachable"))continue;reachable++;
    var endpoint=FOCUSED?path.getEndNode().asBlockPos():target;double actual=guard.position().distanceTo(Vec3.atBottomCenterOf(endpoint));
    if(actual<8||actual>48||actual<=score)continue;chosen=endpoint;score=actual;
    qualification=Map.of("requestedTarget",List.of(target.getX(),target.getY(),target.getZ()),"pathEndpoint",List.of(path.getEndNode().x,path.getEndNode().y,path.getEndNode().z),"pathNodes",path.getNodeCount(),"reason","native_navigation_canReach","targetTolerance",FOCUSED?1:0);
   }
   if(chosen==null)continue;var trial=new Trial(guard,chosen,supplied);trial.qualification=qualification;trials.add(trial);guard.setPatrolPos(chosen);guard.setPatrolling(true);
  }
  Probe.result.put("checkpointTrials",trials.size());Probe.result.put("checkpointSelection",Map.of("focused",FOCUSED,"queries",queries,"reachable",reachable,"tick",Probe.ticks,"arrivalRadius",3,"status",trials.isEmpty()?"no_route_selected":"routes_issued","attempts",attempts));
 }
 static void sample(){if(FOCUSED&&!selected&&Probe.ticks-preparedAt>=100)selectTrials();for(var t:trials){var p=t.guard.position();t.walked+=p.distanceTo(t.last);t.last=p;t.minDistance=Math.min(t.minDistance,p.distanceTo(Vec3.atBottomCenterOf(t.target)));}}
 static void finishPatrol(){List<Object> rows=new ArrayList<>();for(var t:trials){Map<String,Object> r=new LinkedHashMap<>();r.put("guard",t.guard.getUUID());r.put("qualification",t.qualification);r.put("movementAtEnd",Observations.guard(t.guard));r.put("outcome",t.minDistance<=3?"arrived":t.guard.isAlive()?"deadline_without_arrival":"guard_died");r.put("suppliedGuard",t.created);r.put("origin",List.of(t.origin.x,t.origin.y,t.origin.z));r.put("destination",List.of(t.target.getX(),t.target.getY(),t.target.getZ()));r.put("startDistance",t.origin.distanceTo(Vec3.atBottomCenterOf(t.target)));r.put("minimumDistance",t.minDistance);r.put("walked",t.walked);r.put("healthLost",t.health-t.guard.getHealth());r.put("water",t.guard.isInWater());r.put("alive",t.guard.isAlive());r.put("arrivedWithin3",t.minDistance<=3);rows.add(r);t.guard.setPatrolling(t.patrolling);t.guard.setPatrolPos(t.old);}Probe.result.put("controlledBehavior",Map.of("kind","checkpoint_orders_and_supplied_combat","routesIssued",trials.size(),"arrivals",trials.stream().filter(t->t.minDistance<=3).count(),"failedIssuedRoutes",trials.stream().filter(t->t.minDistance>3).count(),"noRouteSelected",trials.isEmpty()));Probe.result.put("checkpointResults",rows);Probe.write("checkpoint-results.json",rows);}
 static void combat(){
  var guards=Probe.level.getEntitiesOfClass(Guard.class,Probe.area);BlockPos chosen=null;
  var golems=Probe.level.getEntitiesOfClass(net.minecraft.world.entity.animal.IronGolem.class,Probe.area);
  var candidateZombie=EntityType.ZOMBIE.create(Probe.level);Vec3 chosenCenter=null;
  outer:for(var g:guards){
   if(!g.isAlive()||g.getHealth()<g.getMaxHealth()*.75||g.isInWater())continue;
   for(int dx=-24;dx<=24;dx+=(FOCUSED?2:4))for(int dz=-24;dz<=24;dz+=(FOCUSED?2:4)){
    double horizontal=Math.hypot(dx,dz);if(horizontal<(FOCUSED?8:16)||horizontal>28)continue;
    for(int dy=FOCUSED?-6:-3;dy<=(FOCUSED?6:3);dy++){
     var p=g.blockPosition().offset(dx,dy,dz);Vec3 center=Vec3.atBottomCenterOf(p);
     if(FOCUSED){
      var floor=Probe.level.getBlockState(p.below());var shape=floor.getCollisionShape(Probe.level,p.below());
      if(shape.isEmpty()||shape.min(net.minecraft.core.Direction.Axis.X)>.2||shape.max(net.minecraft.core.Direction.Axis.X)<.8||shape.min(net.minecraft.core.Direction.Axis.Z)>.2||shape.max(net.minecraft.core.Direction.Axis.Z)<.8)continue;
      if((floor.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)||floor.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE))&&floor.getValue(net.minecraft.world.level.block.CampfireBlock.LIT))continue;
      center=new Vec3(p.getX()+.5,p.getY()-1+shape.max(net.minecraft.core.Direction.Axis.Y),p.getZ()+.5);
      if(!Probe.level.getFluidState(BlockPos.containing(center)).isEmpty())continue;
     }
     final Vec3 targetCenter=center;
     if(!Probe.area.contains(center)||(!FOCUSED&&!safe(p))||guards.stream().anyMatch(other->other.position().distanceTo(targetCenter)<(FOCUSED?6:12))||golems.stream().anyMatch(other->other.position().distanceTo(targetCenter)<(FOCUSED?6:12)))continue;
     candidateZombie.moveTo(center.x,center.y,center.z);if(!Probe.level.noCollision(candidateZombie)||!g.hasLineOfSight(candidateZombie))continue;
     var path=g.getNavigation().createPath(p,0);if(FOCUSED||(path!=null&&path.canReach())){fighter=g;chosen=p;chosenCenter=center;Probe.result.put("combatPlacement",Map.of("focused",FOCUSED,"reachableByGuard",path!=null&&path.canReach(),"distance",g.position().distanceTo(center)));break outer;}
    }
   }
  }
  if(chosen==null){Probe.result.put("combat",Map.of("status","not exercised","reason",FOCUSED?"No dry position 8–28 blocks away with line of sight and defender separation":"No dry reachable position 16–28 blocks away with line of sight and separation from other defenders"));return;}
  var gun=GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).setFireMode(com.tacz.guns.api.item.gun.FireMode.AUTO).build();((IGun)gun.getItem()).setCurrentAmmoCount(gun,30);((IGun)gun.getItem()).setBulletInBarrel(gun,true);fighter.setItemSlot(EquipmentSlot.MAINHAND,gun);initialAmmo=30;
  var zombie=EntityType.ZOMBIE.create(Probe.level);zombie.moveTo(chosenCenter.x,chosenCenter.y,chosenCenter.z);zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));zombie.setPersistenceRequired();Probe.level.addFreshEntity(zombie);threat=zombie;threatHealth=zombie.getHealth();zombie.setTarget(fighter);fighter.setTarget(zombie);
 }
 static void sampleCombat(){if(threat==null)return;threatDead|=!threat.isAlive();var stack=fighter.getMainHandItem();int ammo=stack.getItem() instanceof IGun gun?gun.getCurrentAmmoCount(stack):-1;if(ammo>=0)minAmmo=Math.min(minAmmo,ammo);if(Probe.ticks%20==0)combatSamples.add(Map.of("tick",Probe.ticks,"guardPos",fighter.position().toString(),"guardHealth",fighter.getHealth(),"targetPos",threat.position().toString(),"targetHealth",threat.getHealth(),"ammo",ammo,"distance",fighter.distanceTo(threat)));}
 static void finishCombat(){if(threat==null)return;var row=Map.of("status","exercised","guard",fighter.getUUID(),"initialAmmo",initialAmmo,"minimumAmmo",minAmmo,"targetKilled",threatDead,"targetHealthLost",threatHealth-threat.getHealth(),"guardAlive",fighter.isAlive(),"guardWater",fighter.isInWater(),"samples",combatSamples,"damage",damage);Probe.result.put("combat",row);Probe.write("combat.json",row);}
}
