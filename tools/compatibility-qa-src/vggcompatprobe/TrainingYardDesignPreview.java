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

/** Controlled architecture trials in ordinary packaged Forge; excluded from release. */
final class TrainingYardDesignPreview {
    private static final ResourceLocation MANIFEST=ResourceLocation.parse("vggpreview:training_yard_manifest.json");
    private record Scene(JsonObject row,BlockPos origin,Rotation rotation){
        BlockPos at(BlockPos p){return origin.offset(p.rotate(rotation));}
        BlockPos at(String key){return at(pos(row.getAsJsonArray(key)));}
        BlockPos point(String key,int i){return at(pos(row.getAsJsonArray(key).get(i).getAsJsonArray()));}
        int feet(){return row.has("floorY")?row.get("floorY").getAsInt():1;}
        boolean wet(){return row.has("wetPlot")&&row.get("wetPlot").getAsBoolean();}
        BlockPos shift(){return row.has("shift")?pos(row.getAsJsonArray("shift")):BlockPos.ZERO;}
        String id(){return row.get("id").getAsString()+"/"+rotation;}
    }
    private static final List<Scene> scenes=new ArrayList<>();
    private static final List<Walk> walks=new ArrayList<>();
    private static final List<Guard> occupants=new ArrayList<>(),shooters=new ArrayList<>();
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
        require(rows.size()==2,"Expected two Training Yard layouts");
        Probe.result.put("observationKind","controlled_training_yard_preview");
        int i=0;var checks=new ArrayList<Object>();
        for(var value:rows){var row=value.getAsJsonObject();int r=0;
            for(var rotation:Rotation.values()){
                var s=new Scene(row,new BlockPos(i*160+(r%2)*64,60,(r/2)*96),rotation);r++;scenes.add(s);
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)Probe.level.setChunkForced((s.origin().getX()>>4)+x,(s.origin().getZ()>>4)+z,true);
                var template=Probe.level.getStructureManager().get(ResourceLocation.parse(row.get("template").getAsString())).orElseThrow();
                require(template.getSize().equals(pos(row.getAsJsonArray("size"))),"Wrong size "+s.id());
                if(s.wet())for(int x=-6;x<=template.getSize().getX()+5;x++)for(int z=-8;z<=template.getSize().getZ()+5;z++)
                    Probe.level.setBlock(s.at(new BlockPos(x,0,z)),Blocks.WATER.defaultBlockState(),2);
                if(row.has("region")){
                    String biome=switch(row.get("region").getAsString()){
                        case "desert"->"desert";case "savanna"->"savanna";case "taiga"->"taiga";
                        case "snowy"->"snowy_plains";case "jungle"->"jungle";case "swamp"->"swamp";default->"deep_ocean";};
                    var a=s.at(new BlockPos(-6,0,-6));var b=s.at(new BlockPos(template.getSize()).offset(5,0,5));
                    String command="fillbiome "+Math.min(a.getX(),b.getX())+" 56 "+Math.min(a.getZ(),b.getZ())+" "+Math.max(a.getX(),b.getX())+" 76 "+Math.max(a.getZ(),b.getZ())+" minecraft:"+biome;
                    require(Probe.level.getServer().getCommands().performPrefixedCommand(Probe.level.getServer().createCommandSourceStack().withSuppressedOutput(),command)>0,"Review biome setup failed");
                }
                var settings=new StructurePlaceSettings().setRotation(rotation).setKnownShape(false);
                require(template.placeInWorld(Probe.level,s.origin(),BlockPos.ZERO,settings,Probe.level.random,2),"Placement failed "+s.id());
                int ex=pos(row.getAsJsonArray("entrance")).getX();
                for(int x=ex-1;x<=ex+2;x++)for(int z=-5;z<0;z++)Probe.level.setBlock(s.at(new BlockPos(x,s.feet()-1,z)),Blocks.COBBLESTONE.defaultBlockState(),3);
                var doors=new HashSet<BlockPos>();for(var p:row.getAsJsonArray("doors"))doors.add(pos(p.getAsJsonArray()));
                for(var p:row.getAsJsonArray("standing")){
                    var local=pos(p.getAsJsonArray());var at=s.at(local);
                    require(Probe.level.getFluidState(at).isEmpty(),"Wet standing cell "+s.id()+local);
                    require(Probe.level.getBlockState(at.below()).isFaceSturdy(Probe.level,at.below(),Direction.UP),"Unsupported floor "+s.id()+local);
                    if(!doors.contains(local))for(int y=0;y<3;y++)require(Probe.level.getBlockState(at.above(y)).getCollisionShape(Probe.level,at.above(y)).isEmpty(),"Blocked route "+s.id()+local);
                }
                for(var p:row.getAsJsonArray("details")){
                    var detail=p.getAsJsonObject();var at=s.at(pos(detail.getAsJsonArray("pos")));
                    var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(detail.get("block").getAsString()));
                    var actual=Probe.level.getBlockState(at);
                    require(actual.is(block)&&actual.canSurvive(Probe.level,at),"Unsupported detail "+s.id()+at+" "+block);
                }
                if(row.has("rails"))for(var v:row.getAsJsonArray("rails")){
                    var at=s.at(pos(v.getAsJsonArray()));
                    require(Probe.level.getBlockState(at).getCollisionShape(Probe.level,at).max(Direction.Axis.Y)>=1.5,"Unprotected platform edge "+s.id()+at);
                }
                var dummy=guard(s.at("anchor"));
                for(String key:List.of("initialPositions","dutyPositions"))for(var p:row.getAsJsonArray(key)){
                    var at=s.at(pos(p.getAsJsonArray()));dummy.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);
                    require(Probe.level.noCollision(dummy),"Blocked guard cell "+s.id()+at);
                }
                checks.add(Map.of("scene",s.id(),"clearStandingCells",row.getAsJsonArray("standing").size(),"survivingDetails",row.getAsJsonArray("details").size()));
                if(rotation==Rotation.NONE){
                    boolean large=row.get("id").getAsString().endsWith("drill_courtyard");
                    view(s,views,"front",-10,9,-14,large?8:6,3,7,4500);
                    view(s,views,"rear",27,13,29,large?8:6,3,9,4500);
                    view(s,views,"roof",26,25,-8,large?8:6,3,8,4500);
                    view(s,views,"range",large?10:6,1.1,large?4:3,large?10:6,2,large?12:11,4500);
                    if(large)view(s,views,"pavilion",3.5,1.1,6.5,2,2,11.5,4500);
                    view(s,views,"night",24,8,21,large?8:6,2,9,18000);
                    view(s,views,"rain",22,7,-10,large?8:6,3,7,4500);
                }
            }i++;
        }
        Probe.result.put("trainingYardPlacement",checks);
    }
    private static void view(Scene s,List<BarracksDesignPreview.View> views,String name,double x,double y,double z,double tx,double ty,double tz,long time){
        var o=s.origin().offset(s.shift());views.add(new BarracksDesignPreview.View("training-yard-"+s.row().get("id").getAsString()+"-"+name,o.getX()+x,o.getY()+y,o.getZ()+z,o.getX()+tx,o.getY()+ty,o.getZ()+tz,70,time));
    }
    static boolean tick(){
        if(stage==-1){
            var lighting=new ArrayList<Object>();require(Probe.level.isRaining(),"Rain review missing");
            for(var s:scenes){
                for(var p:s.row().getAsJsonArray("lightSamples")){
                    var at=s.at(pos(p.getAsJsonArray()));int light=Probe.level.getBrightness(LightLayer.BLOCK,at);
                    require(light>0&&!Probe.level.canSeeSky(at)&&!Probe.level.isRainingAt(at),"Unlit or exposed occupied cell "+s.id()+at);
                    lighting.add(Map.of("scene",s.id(),"position",at.toShortString(),"light",light));
                }
                if(s.row().has("region")&&s.row().get("region").getAsString().equals("snowy"))snowfall(s);
                var occupied=guard(s.point("dutyPositions",1));occupied.setNoAi(true);Probe.level.addFreshEntity(occupied);occupants.add(occupied);
                var g=guard(s.at(new BlockPos(pos(s.row().getAsJsonArray("entrance")).getX(),s.feet(),-3)));
                addWalk(s,g,s.point("walkTargets",0));
            }
            Probe.result.put("trainingYardLighting",lighting);Probe.level.setWeatherParameters(999999,0,false,false);Probe.level.setDayTime(4500);
            stage=0;started=Probe.ticks;Probe.progress("walking guards through Training Yard routes beside occupied drill positions");
        }
        for(var w:walks)require(w.mob.isAlive()&&w.mob.getHealth()==w.mob.getMaxHealth()&&!w.mob.isInWater()&&supportedHeight(w),"Walking entity left level route or took damage "+w.scene.id()+" stage="+stage+" pos="+w.mob.position()+" target="+w.target+" health="+w.mob.getHealth()+"/"+w.mob.getMaxHealth()+" alive="+w.mob.isAlive()+" water="+w.mob.isInWater());
        if(stage<8){
            if(walks.stream().allMatch(Walk::arrived)){
                stage++;started=Probe.ticks;
                if(stage<8)for(var w:walks)w.target=w.scene.point("walkTargets",stage);
                else{
                    checkDoorVisits();
                    Probe.result.put("trainingYardMovement",Map.of("guards",walks.size(),"completedLegs",walks.size()*8,"nativeNavigation",true,"occupiedNeighbor",true,"observerPathOverride",false,"benchStepTicks",walks.stream().mapToInt(w->w.benchTicks).sum()));
                    walks.forEach(w->w.mob.discard());walks.clear();
                    for(var s:scenes){
                        var g=guard(s.point("dutyPositions",0));g.setNoAi(true);Probe.level.addFreshEntity(g);occupants.add(g);
                        var villager=EntityType.VILLAGER.create(Probe.level);require(villager!=null,"Missing villager");
                        var start=s.point("traffic",0);villager.setPos(start.getX()+.5,start.getY(),start.getZ()+.5);villager.setPersistenceRequired();
                        villager.goalSelector.removeAllGoals(goal->true);addWalk(s,villager,s.point("traffic",1));
                    }
                    Probe.progress("walking villagers around occupied Training Yards");
                }
            }
        }else if(stage<15){
            if(walks.stream().allMatch(Walk::arrived)){
                stage++;started=Probe.ticks;
                if(stage<15)for(var w:walks)w.target=w.scene.point("traffic",stage-7);
                else{
                    checkDoorVisits();
                    Probe.result.put("trainingYardTraffic",Map.of("villagers",walks.size(),"completedLegs",walks.size()*7,"twoOccupiedDuties",true,"benchStepTicks",walks.stream().mapToInt(w->w.benchTicks).sum()));
                    walks.forEach(w->w.mob.discard());walks.clear();occupants.forEach(Entity::discard);occupants.clear();startFire();
                }
            }
        }else if(stage<17){
            boolean hit=true;
            for(int i=0;i<shooters.size();i++){
                var g=shooters.get(i);var enemy=enemies.get(i);var s=scenes.get(i/2);var at=s.point("dutyPositions",i%2);
                require(g.position().distanceToSqr(at.getCenter().add(0,-.5,0))<2.25,"Shooter left bay "+s.id()+" member="+(i%2));
                require(g.getHealth()==g.getMaxHealth(),"Shooter took friendly damage "+s.id());
                hit &= enemy.getHealth()<enemy.getMaxHealth()&&((IGun)g.getMainHandItem().getItem()).getCurrentAmmoCount(g.getMainHandItem())<30;
            }
            if(hit){
                for(int i=0;i<shooters.size();i++)shots.add(Map.of("scene",scenes.get(i/2).id(),"member",i%2,"distance",stage-11,"damage",enemies.get(i).getMaxHealth()-enemies.get(i).getHealth(),"ammoRemaining",((IGun)shooters.get(i).getMainHandItem().getItem()).getCurrentAmmoCount(shooters.get(i).getMainHandItem()),"position",shooters.get(i).position().toString()));
                Probe.result.put("trainingYardGunfire",new ArrayList<>(shots));Probe.write("training-yard-shot-results.json",shots);
                stage++;started=Probe.ticks;enemies.forEach(Entity::discard);enemies.clear();
                if(stage<17)startFire();else{shooters.forEach(Entity::discard);return true;}
            }
        }
        if(Probe.ticks-started>400)throw new AssertionError("Training Yard trial timed out stage "+stage+" "+walks.stream().filter(w->!w.arrived()).map(w->w.scene.id()+w.mob.position()+" -> "+w.target).toList()+" targets="+enemies.stream().map(Husk::getHealth).toList());
        return false;
    }
    private static boolean supportedHeight(Walk w){
        int floor=w.scene.origin().getY()+w.scene.feet();
        double delta=w.mob.getY()-floor;
        if(Math.abs(delta)<.3)return true;
        if(delta<.3||delta>.52)return false;
        // A native half-slab step is ordinary seating traversal, not a fall or flood.
        // Accept it only with a real bottom slab beneath the entity's footprint.
        var box=w.mob.getBoundingBox();
        for(var p:BlockPos.betweenClosed((int)Math.floor(box.minX+.001),floor,(int)Math.floor(box.minZ+.001),
                (int)Math.floor(box.maxX-.001),floor,(int)Math.floor(box.maxZ-.001))){
            var state=Probe.level.getBlockState(p);
            if(state.getBlock() instanceof SlabBlock&&state.getValue(SlabBlock.TYPE)==net.minecraft.world.level.block.state.properties.SlabType.BOTTOM){
                w.benchTicks++;w.lastBenchTick=w.mob.tickCount;return true;
            }
        }
        // Stepping down from the same half slab takes a few gravity ticks.
        return w.mob.tickCount-w.lastBenchTick<=4;
    }
    private static void snowfall(Scene s){
        // Apply one controlled snow layer only where native cold-biome rules allow it.
        // This exercises snow-covered movement without claiming a long weather soak.
        require(Probe.level.getBiome(s.at("anchor")).is(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS),"Snow biome missing");
        var added=new ArrayList<String>();int field=0,approach=0;
        for(String key:List.of("field","standing"))for(var v:s.row().getAsJsonArray(key)){
            var at=s.at(pos(v.getAsJsonArray()));
            if(Probe.level.canSeeSky(at)&&Probe.level.getBlockState(at).isAir()&&Probe.level.getBiome(at).value().shouldSnow(Probe.level,at)){
                Probe.level.setBlock(at,Blocks.SNOW.defaultBlockState(),3);added.add(at.toShortString());
                if(key.equals("field"))field++;
            }
        }
        int ex=pos(s.row().getAsJsonArray("entrance")).getX();
        for(int x=ex;x<=ex+1;x++)for(int z=-3;z<0;z++){
            var at=s.at(new BlockPos(x,s.feet(),z));
            if(Probe.level.canSeeSky(at)&&Probe.level.getBiome(at).value().shouldSnow(Probe.level,at)){
                Probe.level.setBlock(at,Blocks.SNOW.defaultBlockState(),3);approach++;
            }
        }
        require(field>0&&approach>0,"Missing uncovered snow samples "+s.id());
        for(var v:s.row().getAsJsonArray("dutyPositions"))require(!Probe.level.getBlockState(s.at(pos(v.getAsJsonArray()))).is(Blocks.SNOW),"Snow in sheltered drill bay");
        Probe.result.put("trainingYardSnow/"+s.id(),Map.of("nativeBiome",true,"controlledSingleLayer",true,"fieldCells",field,"approachCells",approach,"positions",added));
    }
    private static void checkDoorVisits(){
        for(var w:walks)if(w.scene.row().getAsJsonArray("doors").size()>0)
            require(w.frontDoor&&w.rearDoor,"Route bypassed a pavilion door "+w.scene.id());
    }
    private static void addWalk(Scene s,PathfinderMob mob,BlockPos target){
        var w=new Walk(s,mob,target);walks.add(w);mob.goalSelector.addGoal(0,w);mob.goalSelector.addGoal(1,new OpenDoorGoal(mob,true));Probe.level.addFreshEntity(mob);
    }
    private static void startFire(){
        int distance=stage-11;
        // Actual combat goals fire at live hostiles before the decorative targets.
        // This does not implement automatic practice shooting at target blocks.
        shooters.forEach(Entity::discard);shooters.clear();
        for(var s:scenes)for(int member=0;member<2;member++){
            var at=pos(s.row().getAsJsonArray("dutyPositions").get(member).getAsJsonArray());var g=guard(s.at(at));
            var target=s.at(at.offset(0,0,distance));
            var enemy=EntityType.HUSK.create(Probe.level);require(enemy!=null,"Missing target");
            enemy.setBaby(false);enemy.setNoAi(true);enemy.setPersistenceRequired();enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);enemy.setHealth(200);
            enemy.setPos(target.getX()+.5,target.getY(),target.getZ()+.5);Probe.level.addFreshEntity(enemy);enemies.add(enemy);
            g.setItemSlot(EquipmentSlot.MAINHAND,GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).setFireMode(FireMode.AUTO).setAmmoCount(30).setAmmoInBarrel(true).build());
            GuardOrdersAccess.setOrders(g,GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION).withEngagement(GuardOrders.EngagementOrder.HOSTILE_MOBS_ONLY));g.setTarget(enemy);
            Probe.level.addFreshEntity(g);shooters.add(g);require(g.hasLineOfSight(enemy),"Blocked eye sightline "+s.id()+" member="+member);
        }
        Probe.progress("checking paired Training Yard TaCZ combat at distance "+distance);
    }
    private static Guard guard(BlockPos p){var g=GuardEntityType.GUARD.get().create(Probe.level);require(g!=null,"Missing guard");g.setPersistenceRequired();g.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);return g;}
    private static final class Walk extends Goal{
        final Scene scene;final PathfinderMob mob;BlockPos target;boolean frontDoor,rearDoor;int benchTicks,lastBenchTick=-1000;
        Walk(Scene s,PathfinderMob mob,BlockPos target){this.scene=s;this.mob=mob;this.target=target;setFlags(EnumSet.of(Flag.MOVE));var nav=(GroundPathNavigation)mob.getNavigation();nav.setCanOpenDoors(true);nav.setCanPassDoors(true);}
        boolean arrived(){return mob.isAlive()&&Math.abs(mob.getY()-target.getY())<.6&&mob.position().distanceToSqr(target.getCenter().add(0,-.5,0))<.75;}
        public boolean canUse(){return true;}
        public boolean requiresUpdateEveryTick(){return true;}
        public void tick(){
            if(scene.row().getAsJsonArray("doors").size()>0){
                for(int i=0;i<4;i++){
                    var p=scene.point("doors",i);
                    if(mob.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p))){if(i<2)frontDoor=true;else rearDoor=true;}
                }
            }
            if(arrived())mob.getNavigation().stop();else if(mob.tickCount%10==0)mob.getNavigation().moveTo(mob.getNavigation().createPath(target,0),1.0);}
    }
}
