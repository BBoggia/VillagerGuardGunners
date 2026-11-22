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
import net.minecraft.world.phys.AABB;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Disposable packaged Forge architecture trials, excluded from the release JAR. */
final class WatchtowerDesignPreview {
    private static final ResourceLocation MANIFEST=ResourceLocation.parse("vggpreview:watchtower_manifest.json");
    private record Scene(JsonObject row,BlockPos origin,Rotation rotation){
        BlockPos at(BlockPos p){return origin.offset(p.rotate(rotation));}
        BlockPos at(String key){return at(pos(row.getAsJsonArray(key)));}
        BlockPos shift(){return row.has("shift")?pos(row.getAsJsonArray("shift")):BlockPos.ZERO;}
        BlockPos original(int x,int y,int z){return at(new BlockPos(x,y,z).offset(shift()));}
        int feet(){return row.has("floorY")?row.get("floorY").getAsInt():1;}
        boolean wet(){return row.has("wetPlot")&&row.get("wetPlot").getAsBoolean();}
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
        require(rows.size()==2,"Expected two Watchtower layouts");
        Probe.result.put("observationKind","controlled_watchtower_preview");
        int i=0;var checks=new ArrayList<Object>();
        for(var value:rows){var row=value.getAsJsonObject();int r=0;
            for(var rotation:Rotation.values()){
                var s=new Scene(row,new BlockPos(i*160+(r%2)*64,60,(r/2)*96),rotation);r++;scenes.add(s);
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)Probe.level.setChunkForced((s.origin().getX()>>4)+x,(s.origin().getZ()>>4)+z,true);
                var template=Probe.level.getStructureManager().get(ResourceLocation.parse(row.get("template").getAsString())).orElseThrow();
                require(template.getSize().equals(pos(row.getAsJsonArray("size"))),"Wrong size "+s.id());
                if(s.wet())for(int x=-8;x<=template.getSize().getX()+6;x++)for(int z=-16;z<=template.getSize().getZ()+6;z++)Probe.level.setBlock(s.at(new BlockPos(x,0,z)),Blocks.WATER.defaultBlockState(),2);
                if(row.has("region")&&row.get("region").getAsString().equals("snowy")){
                    var a=s.at(new BlockPos(-4,0,-4));var b=s.at(new BlockPos(template.getSize()).offset(3,0,3));
                    String command="fillbiome "+Math.min(a.getX(),b.getX())+" 48 "+Math.min(a.getZ(),b.getZ())+" "+Math.max(a.getX(),b.getX())+" 88 "+Math.max(a.getZ(),b.getZ())+" minecraft:snowy_plains";
                    int changed=Probe.level.getServer().getCommands().performPrefixedCommand(Probe.level.getServer().createCommandSourceStack().withSuppressedOutput(),command);
                    require(changed>0,"Snowy review biome setup failed: "+command);
                    for(var point:row.getAsJsonArray("targets"))require(Probe.level.getBiome(s.at(pos(point.getAsJsonArray()))).is(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS),"Occupied sample is outside snowy biome "+s.id());
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
                for(var p:row.getAsJsonArray("details")){
                    var detail=p.getAsJsonObject();var at=s.at(pos(detail.getAsJsonArray("pos")));
                    var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(detail.get("block").getAsString()));
                    var actual=Probe.level.getBlockState(at);
                    require(actual.is(block)&&actual.canSurvive(Probe.level,at),"Unsupported detail "+s.id()+at+" "+block);
                }
                for(var p:row.getAsJsonArray("rails")){
                    var at=s.at(pos(p.getAsJsonArray()));
                    require(Probe.level.getBlockState(at).getCollisionShape(Probe.level,at).max(Direction.Axis.Y)>=1.5,"Missing rail "+s.id()+at);
                }
                var dummy=guard(s.at("anchor"));
                for(var p:row.getAsJsonArray("slits")){
                    var at=s.at(pos(p.getAsJsonArray()));
                    double half=dummy.getBbWidth()/2.0;
                    require(!Probe.level.noCollision(dummy,new AABB(at.getX()+.5-half,at.getY()+.5,at.getZ()+.5-half,at.getX()+.5+half,at.getY()+.5+dummy.getBbHeight(),at.getZ()+.5+half)),"Guard could stand through slit "+s.id()+at);
                }
                for(var p:row.getAsJsonArray("initialDeckPositions")){
                    var at=s.at(pos(p.getAsJsonArray()));dummy.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);
                    require(Probe.level.noCollision(dummy),"Unsafe upper spawn "+s.id()+at);
                }
                checks.add(Map.of("scene",s.id(),"clearStandingCells",row.getAsJsonArray("standing").size(),"survivingDetails",row.getAsJsonArray("details").size(),"guardHeight",dummy.getBbHeight(),"protectedSlits",row.getAsJsonArray("slits").size()));
                if(rotation==Rotation.NONE){
                    view(s,views,"arrival",-12,10,-16,7,6,6,4500);
                    view(s,views,"rear",27,15,28,7,6,7,4500);
                    view(s,views,"roof",25,25,-9,7,7,6,4500);
                    view(s,views,"ready-room",4.7,1.1,8.7,2,2,5,4500);
                    view(s,views,"deck",5.3,6.1,7.5,4,7,1,4500);
                    view(s,views,"stairs",s.row().get("id").getAsString().contains("gatehouse")?13:5.5,6.2,11,8,4,6,4500);
                    view(s,views,"front",-12,10,-16,7,6,6,4500);
                    view(s,views,"night",-12,10,-16,7,6,6,18000);
                    view(s,views,"rain",20,12,-12,7,6,6,4500);
                }
            }i++;
        }
        Probe.result.put("watchtowerPlacement",checks);
    }
    private static void view(Scene s,List<BarracksDesignPreview.View> views,String name,double x,double y,double z,double tx,double ty,double tz,long time){
        var o=s.origin().offset(s.shift());views.add(new BarracksDesignPreview.View("watchtower-"+s.row().get("id").getAsString()+"-"+name,o.getX()+x,o.getY()+y,o.getZ()+z,o.getX()+tx,o.getY()+ty,o.getZ()+tz,75,time));
    }
    static boolean tick(){
        if(stage==-1){
            var lighting=new ArrayList<Object>();require(Probe.level.isRaining(),"Rain review missing");
            for(var s:scenes){
                for(var p:s.row().getAsJsonArray("targets")){
                    var at=s.at(pos(p.getAsJsonArray()));int light=Probe.level.getBrightness(LightLayer.BLOCK,at);
                    require(light>0&&!Probe.level.canSeeSky(at)&&!Probe.level.isRainingAt(at),"Unlit or exposed occupied cell "+s.id()+at);
                    lighting.add(Map.of("scene",s.id(),"position",at.toShortString(),"light",light));
                }
                // An occupied neighboring duty stays in place while a second guard walks upstairs.
                var occupied=guard(s.original(3,6,3));occupied.setNoAi(true);Probe.level.addFreshEntity(occupied);occupants.add(occupied);
                var g=guard(s.at(new BlockPos(pos(s.row().getAsJsonArray("entrance")).getX(),s.feet(),-3)));
                var w=new Walk(s,g,s.at(pos(s.row().getAsJsonArray("walkTargets").get(0).getAsJsonArray())));
                walks.add(w);g.goalSelector.addGoal(0,w);g.goalSelector.addGoal(1,new OpenDoorGoal(g,true));Probe.level.addFreshEntity(g);
            }
            Probe.result.put("watchtowerLighting",lighting);Probe.level.setWeatherParameters(999999,0,false,false);Probe.level.setDayTime(4500);
            stage=0;started=Probe.ticks;Probe.progress("walking guards through Watchtower rooms, stairs and occupied decks");
        }
        for(var w:walks)require(w.mob.getAttributeValue(Attributes.FOLLOW_RANGE)==20.0&&w.mob.getHealth()==w.mob.getMaxHealth()&&w.mob.isAlive()&&!w.mob.isInWater()&&w.mob.getY()>=w.scene.origin().getY()+w.scene.feet()-.5,"Walking guard left supported structure "+w.scene.id());
        if(stage<6){
            if(walks.stream().allMatch(Walk::arrived)){
                stage++;started=Probe.ticks;Probe.progress("Watchtower walking leg "+stage);
                if(stage<6)for(var w:walks)w.target=w.scene.at(pos(w.scene.row().getAsJsonArray("walkTargets").get(stage).getAsJsonArray()));
                else{
                    Probe.result.put("watchtowerMovement",Map.of("guards",walks.size(),"completedLegs",walks.size()*6,"nativeNavigation",true,"occupiedNeighbor",true,"defaultReturnPassed",true,"observerPathOverride",false));
                    walks.forEach(w->w.mob.discard());walks.clear();occupants.forEach(Entity::discard);occupants.clear();checkNavigationPolicy();startFire();
                }
            }
        }else if(stage<10){
            boolean hit=true;
            for(int i=0;i<shooters.size();i++){
                var g=shooters.get(i);var enemy=enemies.get(i);var s=scenes.get(i/2);
                var at=s.at(pos(s.row().getAsJsonArray("deckPositions").get(i%2).getAsJsonArray()));
                require(g.position().distanceToSqr(at.getCenter().add(0,-.5,0))<2.25,"Shooter left bay "+s.id()+" member="+(i%2)+" at="+g.position()+" expected="+at+" diagnostics="+combat(g));
                hit &= enemy.getHealth()<enemy.getMaxHealth()&&((IGun)g.getMainHandItem().getItem()).getCurrentAmmoCount(g.getMainHandItem())<30;
            }
            if(hit){
                for(int i=0;i<shooters.size();i++)shots.add(Map.of("scene",scenes.get(i/2).id(),"member",i%2,"trial",stage-6,"damage",enemies.get(i).getMaxHealth()-enemies.get(i).getHealth(),"ammoRemaining",((IGun)shooters.get(i).getMainHandItem().getItem()).getCurrentAmmoCount(shooters.get(i).getMainHandItem())));
                Probe.result.put("watchtowerGunfire",new ArrayList<>(shots));Probe.write("watchtower-shot-results.json",shots);
                stage++;started=Probe.ticks;enemies.forEach(Entity::discard);enemies.clear();
                if(stage<10)startFire();else{Probe.result.put("watchtowerGunfire",shots);shooters.forEach(Entity::discard);return true;}
            }
        }
        if(Probe.ticks-started>300)throw new AssertionError("Watchtower trial timed out stage "+stage+" "+walks.stream().filter(w->!w.arrived()).map(w->w.scene.id()+w.mob.position()+" -> "+w.target+" path="+w.mob.getNavigation().getPath()).toList()+" targets="+enemies.stream().map(e->e.getHealth()).toList()+" shooters="+shooters.stream().map(g->g.goalSelector.getAvailableGoals().stream().filter(w->w.getGoal() instanceof com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal).map(w->((com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal)w.getGoal()).captureDiagnostics().toString()).toList()).toList());
        return false;
    }
    private static void checkNavigationPolicy(){
        var s=scenes.get(0);var from=s.original(4,6,10);var dest=s.original(4,1,10);
        var g=guard(from);g.setOnGround(true);var nav=g.getNavigation();
        var baseline=nav.createPath(dest,0,20); // Explicit native budget bypasses the new ordinary-request hook.
        require(baseline!=null&&!baseline.canReach(),"Missing native 20-block regression fixture");
        require(nav.moveTo(baseline,1),"Cannot install the baseline partial path");
        var active=nav.getPath();int chunks=Probe.level.getChunkSource().getLoadedChunksCount();
        var retried=nav.createPath(dest,0);
        require(retried!=null&&retried.canReach(),"Production retry did not complete the regression route");
        require(nav.getPath()==active,"Planning replaced the actively followed path");
        require(Probe.level.getChunkSource().getLoadedChunksCount()==chunks,"Retry expanded the loaded chunk set");
        var limited=nav.createPath(dest,0);
        require(limited==active,"Repeated query bypassed the per-guard retry budget");
        require(g.getAttributeValue(Attributes.FOLLOW_RANGE)==20.0,"Detection range was changed");
        require(nav.moveTo(retried,1),"Could not accept the completed retry");
        require(nav.createPath(dest,0)==retried,"Completed cached path was replaced");

        var combatGuard=guard(from);combatGuard.setOnGround(true);
        var hostile=EntityType.HUSK.create(Probe.level);require(hostile!=null,"Missing hostile control");combatGuard.setTarget(hostile);
        var combatPath=combatGuard.getNavigation().createPath(dest,0);
        require(combatPath==null||!combatPath.canReach(),"Combat query received walking-only retry");
        var villager=EntityType.VILLAGER.create(Probe.level);require(villager!=null,"Missing non-guard control");
        villager.setPos(from.getX()+.5,from.getY(),from.getZ()+.5);villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(20);
        var villagerPath=villager.getNavigation().createPath(dest,0);
        require(villagerPath==null||!villagerPath.canReach(),"Non-guard query received guard retry");
        Probe.result.put("watchtowerNavigationPolicy",Map.of("native20Fails",true,"ordinaryRequestSucceeds",true,"preservesActivePath",true,"throttlesRepeatedQuery",true,"reusesCompletePath",true,"followRange",20,"additionalLoadedChunks",0,"combatExcluded",true,"nonGuardExcluded",true));
    }
    private static void startFire(){
        int dx=new int[]{0,0,-5,5}[stage-6],distance=new int[]{12,24,24,24}[stage-6];
        // Independent trials avoid swapping a live gun during the previous burst.
        shooters.forEach(Entity::discard);shooters.clear();
        for(int i=0;i<scenes.size();i++)for(int member=0;member<2;member++){
            var s=scenes.get(i);var at=pos(s.row().getAsJsonArray("deckPositions").get(member).getAsJsonArray());
            var g=guard(s.at(at));
            var target=s.at(new BlockPos(at.getX()+dx,s.feet(),at.getZ()-distance));
            if(s.feet()>1)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)Probe.level.setBlock(target.offset(x,-1,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
            var enemy=EntityType.HUSK.create(Probe.level);require(enemy!=null,"Missing target");
            enemy.setBaby(false);enemy.setNoAi(true);enemy.setPersistenceRequired();enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);enemy.setHealth(200);
            enemy.setPos(target.getX()+.5,target.getY(),target.getZ()+.5);Probe.level.addFreshEntity(enemy);enemies.add(enemy);
            g.setItemSlot(EquipmentSlot.MAINHAND,GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).setFireMode(FireMode.AUTO).setAmmoCount(30).setAmmoInBarrel(true).build());
            GuardOrdersAccess.setOrders(g,GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION).withEngagement(GuardOrders.EngagementOrder.HOSTILE_MOBS_ONLY));g.setTarget(enemy);
            Probe.level.addFreshEntity(g);shooters.add(g);
            require(g.hasLineOfSight(enemy),"Blocked eye sightline "+s.id()+" member="+member+" distance="+distance+" offset="+dx);
        }
        Probe.progress("checking Watchtower TaCZ target damage: distance "+distance+", offset "+dx);
    }
    private static Object combat(Guard g){return g.goalSelector.getAvailableGoals().stream().filter(w->w.getGoal() instanceof com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal).map(w->((com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal)w.getGoal()).captureDiagnostics().toString()).toList();}
    private static Guard guard(BlockPos p){var g=GuardEntityType.GUARD.get().create(Probe.level);require(g!=null,"Missing guard");g.setPersistenceRequired();g.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);return g;}
    private static final class Walk extends Goal{
        final Scene scene;final Guard mob;BlockPos target;
        Walk(Scene s,Guard mob,BlockPos target){this.scene=s;this.mob=mob;this.target=target;setFlags(EnumSet.of(Flag.MOVE));var nav=(GroundPathNavigation)mob.getNavigation();nav.setCanOpenDoors(true);nav.setCanPassDoors(true);}
        boolean arrived(){return mob.isAlive()&&Math.abs(mob.getY()-target.getY())<.6&&mob.position().distanceToSqr(target.getCenter().add(0,-.5,0))<.75;}
        public boolean canUse(){return true;}
        public boolean requiresUpdateEveryTick(){return true;}
        public void tick(){if(arrived())mob.getNavigation().stop();else if(mob.tickCount%10==0)mob.getNavigation().moveTo(mob.getNavigation().createPath(target,0),1.0);}
    }
}
