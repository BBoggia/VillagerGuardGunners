package vggcompatprobe;

import com.google.gson.*;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Controlled packaged Forge checks; never included in the release mod. */
final class SentryPostDesignPreview {
    private static final ResourceLocation MANIFEST=ResourceLocation.parse("vggpreview:sentry_post_manifest.json");
    private record Scene(JsonObject row,BlockPos origin,Rotation rotation){
        BlockPos at(BlockPos p){return origin.offset(p.rotate(rotation));}
        BlockPos at(String key){return at(pos(row.getAsJsonArray(key)));}
        String id(){return row.get("id").getAsString()+"/"+rotation;}
        int feet(){return row.has("floorY")?row.get("floorY").getAsInt():1;}
        boolean wet(){return row.has("wetPlot")&&row.get("wetPlot").getAsBoolean();}
    }
    private static final List<Scene> scenes=new ArrayList<>();
    private static final List<Walk> walks=new ArrayList<>();
    private static final List<Guard> sentries=new ArrayList<>();
    private static final List<Husk> enemies=new ArrayList<>();
    private static final List<Object> shots=new ArrayList<>();
    private static int stage=-1,started;
    static boolean available(){return Probe.level.getServer().getResourceManager().getResource(MANIFEST).isPresent();}
    private static BlockPos pos(JsonArray a){return RegionalBarracksPreview.pos(a);}
    private static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    static void setup(List<BarracksDesignPreview.View> views){
        JsonArray rows;
        try(var r=Probe.level.getServer().getResourceManager().getResourceOrThrow(MANIFEST).openAsReader()){
            rows=JsonParser.parseReader(r).getAsJsonArray();
        }catch(java.io.IOException e){throw new RuntimeException(e);}
        require(rows.size()==2,"Expected two Sentry Post layouts");
        Probe.result.put("observationKind","controlled_sentry_post_preview");
        for(int x=-3;x<=11;x++)for(int z=-3;z<=6;z++)Probe.level.setChunkForced(x,z,true);
        int i=0;var checks=new ArrayList<Object>();
        for(var value:rows){var row=value.getAsJsonObject();int r=0;
            for(var rotation:Rotation.values()){
                var s=new Scene(row,new BlockPos(i*96+(r%2)*40,60,(r/2)*40),rotation);r++;scenes.add(s);
                var template=Probe.level.getStructureManager().get(ResourceLocation.parse(row.get("template").getAsString())).orElseThrow();
                require(template.getSize().equals(pos(row.getAsJsonArray("size"))),"Wrong size "+s.id());
                if(s.wet())for(int x=-8;x<=pos(row.getAsJsonArray("size")).getX()+6;x++)for(int z=-16;z<=pos(row.getAsJsonArray("size")).getZ()+6;z++)Probe.level.setBlock(s.at(new BlockPos(x,0,z)),Blocks.WATER.defaultBlockState(),2);
                if(row.has("region")&&row.get("region").getAsString().equals("snowy")){
                    var a=s.at(new BlockPos(-12,0,-12));var b=s.at(new BlockPos(20,20,20));
                    String command="fillbiome "+Math.min(a.getX(),b.getX())+" 60 "+Math.min(a.getZ(),b.getZ())+" "+Math.max(a.getX(),b.getX())+" 80 "+Math.max(a.getZ(),b.getZ())+" minecraft:snowy_plains";
                    int changed=Probe.level.getServer().getCommands().performPrefixedCommand(Probe.level.getServer().createCommandSourceStack().withSuppressedOutput(),command);
                    require(changed>0,"Snowy review biome setup failed");
                }
                var settings=new StructurePlaceSettings().setRotation(rotation).setKnownShape(false);
                require(template.placeInWorld(Probe.level,s.origin(),BlockPos.ZERO,settings,Probe.level.random,2),"Placement failed "+s.id());
                int ex=pos(row.getAsJsonArray("entrance")).getX();
                for(int x=ex-1;x<=ex+2;x++)for(int z=-5;z<0;z++)Probe.level.setBlock(s.at(new BlockPos(x,s.feet()-1,z)),Blocks.COBBLESTONE.defaultBlockState(),3);
                var doors=new HashSet<BlockPos>();for(var p:row.getAsJsonArray("doors"))doors.add(pos(p.getAsJsonArray()));
                for(var p:row.getAsJsonArray("standing")){
                    var local=pos(p.getAsJsonArray());var at=s.at(local);
                    require(Probe.level.getBlockState(at.below()).isFaceSturdy(Probe.level,at.below(),Direction.UP),"Unsupported floor "+s.id()+local);
                    if(!doors.contains(local))for(int y=0;y<3;y++)require(Probe.level.getBlockState(at.above(y)).getCollisionShape(Probe.level,at.above(y)).isEmpty(),"Blocked route "+s.id()+local);
                }
                int details=0;
                if(row.has("details")){
                    for(var v:row.getAsJsonArray("details")){
                        var detail=v.getAsJsonObject();var at=s.at(pos(detail.getAsJsonArray("pos")));
                        var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(detail.get("block").getAsString()));
                        var actual=Probe.level.getBlockState(at);
                        require(actual.is(block)&&actual.canSurvive(Probe.level,at),"Unsupported regional detail "+s.id()+at+" "+block);details++;
                    }
                    for(var v:row.getAsJsonArray("rails")){
                        var at=s.at(pos(v.getAsJsonArray()));
                        require(Probe.level.getBlockState(at).getCollisionShape(Probe.level,at).max(Direction.Axis.Y)>=1.5,"Missing deck rail "+s.id()+at);
                    }
                }else for(var block:List.of(Blocks.LANTERN,Blocks.OAK_DOOR,Blocks.GLASS_PANE,Blocks.OAK_TRAPDOOR,Blocks.GREEN_WALL_BANNER))
                    for(var info:template.filterBlocks(s.origin(),settings,block)){
                        var actual=Probe.level.getBlockState(info.pos());
                        require(actual.is(block)&&actual.canSurvive(Probe.level,info.pos()),"Unsupported detail "+s.id()+info.pos());details++;
                    }
                for(var p:row.getAsJsonArray("openSky"))require(Probe.level.canSeeSky(s.at(pos(p.getAsJsonArray()))),"Covered arrival strip");
                checks.add(Map.of("scene",s.id(),"clearStandingCells",row.getAsJsonArray("standing").size(),"survivingDetails",details));
                if(rotation==Rotation.NONE){
                    view(s,views,"front",-9,7,-12,5,3,5,4500);
                    view(s,views,"rear",20,10,23,5,4,6,4500);
                    view(s,views,"roof",18,20,-8,5,3,5,4500);
                    view(s,views,"interior",lodge(s)?4.6:5.2,1.1,lodge(s)?5.6:4.8,2.2,2,7.5,4500);
                    view(s,views,"night",-9,7,-12,5,3,5,18000);
                    view(s,views,"rain",11,5,-9,4,2,4,4500);
                }
            }i++;
        }
        Probe.result.put("sentryPlacement",checks);
    }
    private static boolean lodge(Scene s){return s.row().get("id").getAsString().endsWith("gateway_lodge");}
    private static void view(Scene s,List<BarracksDesignPreview.View> views,String name,double x,double y,double z,double tx,double ty,double tz,long time){
        var o=s.origin();if(s.row().has("shift")){var shift=pos(s.row().getAsJsonArray("shift"));x+=shift.getX();tx+=shift.getX();y+=shift.getY();ty+=shift.getY();z+=shift.getZ();tz+=shift.getZ();}views.add(new BarracksDesignPreview.View("sentry-"+s.row().get("id").getAsString()+"-"+name,o.getX()+x,o.getY()+y,o.getZ()+z,o.getX()+tx,o.getY()+ty,o.getZ()+tz,75,time));
    }
    static boolean tick(){
        if(stage==-1){
            var lighting=new ArrayList<Object>();
            require(Probe.level.isRaining(),"Rain review missing");
            for(var s:scenes){
                for(var p:s.row().getAsJsonArray("targets")){
                    var at=s.at(pos(p.getAsJsonArray()));int light=Probe.level.getBrightness(LightLayer.BLOCK,at);
                    require(light>0&&!Probe.level.canSeeSky(at)&&!Probe.level.isRainingAt(at),"Unlit or exposed occupied cell "+s.id()+at);
                    lighting.add(Map.of("scene",s.id(),"position",at.toShortString(),"light",light));
                }
                var guard=guard(s.at(new BlockPos(pos(s.row().getAsJsonArray("entrance")).getX(),s.feet(),-3)));
                var w=new Walk(s,guard,s.at(pos(s.row().getAsJsonArray("walkTargets").get(0).getAsJsonArray())));
                walks.add(w);guard.goalSelector.addGoal(0,w);guard.goalSelector.addGoal(1,new OpenDoorGoal(guard,true));Probe.level.addFreshEntity(guard);
            }
            Probe.result.put("sentryLighting",lighting);Probe.level.setWeatherParameters(999999,0,false,false);Probe.level.setDayTime(4500);
            stage=0;started=Probe.ticks;Probe.progress("walking guards to watch bays, work areas and exits");
        }
        for(var w:walks)require(!w.mob.isInWater()&&w.mob.getY()>=w.scene.origin().getY()+w.scene.feet()-.5,"Walking entity left supported platform "+w.scene.id());
        if(stage<4){
            if(walks.stream().allMatch(Walk::arrived)){
                stage++;started=Probe.ticks;
                if(stage<4)for(var w:walks)w.target=w.scene.at(pos(w.scene.row().getAsJsonArray("walkTargets").get(stage).getAsJsonArray()));
                else{
                    Probe.result.put("sentryMovement",Map.of("guards",walks.size(),"completedLegs",walks.size()*4,"nativeNavigation",true));
                    walks.forEach(w->w.mob.discard());walks.clear();
                    for(var s:scenes){
                        var guard=guard(s.at("anchor"));GuardOrdersAccess.setOrders(guard,GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION).withEngagement(GuardOrders.EngagementOrder.HOLD_FIRE));
                        guard.goalSelector.addGoal(0,new Walk(s,guard,s.at("anchor")));
                        Probe.level.addFreshEntity(guard);sentries.add(guard);
                        var villager=EntityType.VILLAGER.create(Probe.level);require(villager!=null,"Missing villager");
                        var start=s.at(pos(s.row().getAsJsonArray("traffic").get(0).getAsJsonArray()));villager.setPos(start.getX()+.5,start.getY(),start.getZ()+.5);villager.setPersistenceRequired();
                        villager.goalSelector.removeAllGoals(g->true);
                        var walk=new Walk(s,villager,s.at(pos(s.row().getAsJsonArray("traffic").get(1).getAsJsonArray())));walks.add(walk);villager.goalSelector.addGoal(0,walk);Probe.level.addFreshEntity(villager);
                    }
                    Probe.progress("passing villagers beside occupied sentry bays");
                }
            }
        }else if(stage<6){
            if(walks.stream().allMatch(Walk::arrived)){
                stage++;started=Probe.ticks;
                if(stage==5)for(var w:walks)w.target=w.scene.at(pos(w.scene.row().getAsJsonArray("traffic").get(0).getAsJsonArray()));
                else{
                    Probe.result.put("sentryTraffic",Map.of("villagers",walks.size(),"completedLegs",walks.size()*2));
                    walks.forEach(w->w.mob.discard());walks.clear();startFire();
                }
            }
        }else if(stage<9){
            boolean hit=true;
            for(int i=0;i<sentries.size();i++){
                var g=sentries.get(i);var enemy=enemies.get(i);var s=scenes.get(i);
                require(g.position().distanceToSqr(s.at("anchor").getCenter().add(0,-.5,0))<2.25,"Shooter left bay "+s.id());
                hit &= enemy.getHealth()<enemy.getMaxHealth()&&((IGun)g.getMainHandItem().getItem()).getCurrentAmmoCount(g.getMainHandItem())<30;
            }
            if(hit){
                for(int i=0;i<sentries.size();i++)shots.add(Map.of("scene",scenes.get(i).id(),"direction",new String[]{"north","northwest","northeast"}[stage-6],"damage",enemies.get(i).getMaxHealth()-enemies.get(i).getHealth(),"ammoRemaining",((IGun)sentries.get(i).getMainHandItem().getItem()).getCurrentAmmoCount(sentries.get(i).getMainHandItem())));
                stage++;started=Probe.ticks;enemies.forEach(Entity::discard);enemies.clear();
                if(stage<9)startFire();else{Probe.result.put("sentryGunfire",shots);sentries.forEach(Entity::discard);return true;}
            }
        }
        if(Probe.ticks-started>600)throw new AssertionError("Sentry test timed out stage "+stage+" "+walks.stream().filter(w->!w.arrived()).map(w->w.scene.id()+w.mob.position()+" -> "+w.target).toList()+" targets="+enemies.stream().map(e->e.getHealth()).toList());
        return false;
    }
    private static void startFire(){
        for(int i=0;i<scenes.size();i++){
            var s=scenes.get(i);var g=sentries.get(i);
            if(stage==6){g.discard();g=guard(s.at("anchor"));sentries.set(i,g);}
            int dx=new int[]{0,-5,5}[stage-6];
            var a=pos(s.row().getAsJsonArray("anchor"));var target=s.at(new BlockPos(a.getX()+dx,s.feet(),-12));
            if(s.feet()>1)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)Probe.level.setBlock(target.offset(x,-1,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
            var enemy=EntityType.HUSK.create(Probe.level);require(enemy!=null,"Missing target");
            enemy.setBaby(false);enemy.setNoAi(true);enemy.setPersistenceRequired();enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);enemy.setHealth(200);
            enemy.setPos(target.getX()+.5,target.getY(),target.getZ()+.5);Probe.level.addFreshEntity(enemy);enemies.add(enemy);
            g.setItemSlot(EquipmentSlot.MAINHAND,GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).setFireMode(FireMode.AUTO).setAmmoCount(30).setAmmoInBarrel(true).build());
            GuardOrdersAccess.setOrders(g,GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION).withEngagement(GuardOrders.EngagementOrder.HOSTILE_MOBS_ONLY));g.setTarget(enemy);
            if(stage==6)Probe.level.addFreshEntity(g);
            require(g.hasLineOfSight(enemy),"Blocked eye sightline "+s.id()+" offset "+dx+" guard="+g.position()+" eye="+g.getEyePosition()+" target="+enemy.getEyePosition()+" hit="+Probe.level.clip(new net.minecraft.world.level.ClipContext(g.getEyePosition(),enemy.getEyePosition(),net.minecraft.world.level.ClipContext.Block.VISUAL,net.minecraft.world.level.ClipContext.Fluid.NONE,g)).getBlockPos());
        }
        Probe.progress("checking actual TaCZ target damage from sentry bays, direction "+(stage-6));
    }
    private static Guard guard(BlockPos p){
        var guard=GuardEntityType.GUARD.get().create(Probe.level);require(guard!=null,"Missing guard");guard.setPersistenceRequired();guard.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);return guard;
    }
    private static final class Walk extends Goal{
        final Scene scene;final PathfinderMob mob;BlockPos target;
        Walk(Scene s,PathfinderMob mob,BlockPos target){this.scene=s;this.mob=mob;this.target=target;setFlags(EnumSet.of(Flag.MOVE));var nav=(GroundPathNavigation)mob.getNavigation();nav.setCanOpenDoors(true);nav.setCanPassDoors(true);}
        boolean arrived(){return mob.isAlive()&&Math.abs(mob.getY()-target.getY())<.8&&mob.position().distanceToSqr(target.getCenter().add(0,-.5,0))<.75;}
        public boolean canUse(){return true;}
        public boolean requiresUpdateEveryTick(){return true;}
        public void tick(){if(arrived())mob.getNavigation().stop();else if(mob.tickCount%10==0)mob.getNavigation().moveTo(mob.getNavigation().createPath(target,0),1.0);}
    }
}
