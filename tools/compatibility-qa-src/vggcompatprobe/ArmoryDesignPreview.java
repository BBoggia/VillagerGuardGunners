package vggcompatprobe;

import com.google.gson.*;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraftforge.items.IItemHandler;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Disposable packaged-client architecture and movement checks, outside the release JAR. */
final class ArmoryDesignPreview {
    private static final ResourceLocation MANIFEST=ResourceLocation.parse("vggpreview:armory_manifest.json");
    private record Scene(JsonObject row, BlockPos origin, Rotation rotation) {
        BlockPos at(BlockPos relative){return origin.offset(relative.rotate(rotation));}
        String id(){return row.get("id").getAsString();}
        BlockPos home(int member){var entry=pos(row.getAsJsonArray("entrance"));return at(new BlockPos(entry.getX()+member,row.has("roadFeetY")?row.get("roadFeetY").getAsInt():entry.getY(),-3));}
        BlockPos destination(int stage,int member){return at(pos(row.getAsJsonArray("walkTargets").get(stage).getAsJsonArray().get(member).getAsJsonArray()));}
    }
    private record Resupply(Scene scene, Guard guard, ArmoryBlockEntity source) { }
    private static final List<Scene> scenes=new ArrayList<>();
    private static final List<Walk> walks=new ArrayList<>();
    private static final List<Resupply> resupplies=new ArrayList<>();
    private static int started=-1,leg;
    private static boolean rainReview;
    static boolean available(){return Probe.level.getServer().getResourceManager().getResource(MANIFEST).isPresent();}
    private static BlockPos pos(JsonArray a){return RegionalBarracksPreview.pos(a);}
    private static void require(boolean b,String message){if(!b)throw new AssertionError(message);}

    static void setup(List<BarracksDesignPreview.View> views){
        JsonArray layouts;
        try(var reader=Probe.level.getServer().getResourceManager().getResourceOrThrow(MANIFEST).openAsReader()){
            var root=JsonParser.parseReader(reader);
            layouts=root.isJsonArray()?root.getAsJsonArray():new JsonArray();
            if(root.isJsonObject())layouts.add(root);
        }catch(java.io.IOException ex){throw new RuntimeException(ex);}
        require(layouts.size()>0&&layouts.size()<=4,"Expected one to four Armory previews per controlled plot");
        Probe.result.put("observationKind","controlled_armory_design_preview");
        for(int x=-3;x<=4+(layouts.size()-1)*6;x++)for(int z=-3;z<=4;z++)Probe.level.setChunkForced(x,z,true);
        var checks=new ArrayList<Object>();int layoutIndex=0;
        for(var value:layouts){
            var row=value.getAsJsonObject();
            // Keep older compact-only preview packs usable with this helper.
            if(!row.has("id")){
                row.addProperty("id","compact");row.add("doors",JsonParser.parseString("[[5,1,3],[6,1,3]]"));
                row.add("walkTargets",JsonParser.parseString("[[[7,1,5],[8,1,5]],[[8,1,9],[9,1,9]]]"));row.add("openSky",new JsonArray());
            }
            BlockPos[] origins={new BlockPos(0,60,0),new BlockPos(40,60,0),new BlockPos(0,60,40),new BlockPos(40,60,40)};
            int index=0;
            for(var rotation:Rotation.values()){
                var scene=new Scene(row,origins[index++].offset(layoutIndex*96,0,0),rotation);scenes.add(scene);
                var template=Probe.level.getStructureManager().get(ResourceLocation.parse(row.get("template").getAsString())).orElseThrow();
                require(template.getSize().equals(pos(row.getAsJsonArray("size"))),"Wrong Armory size "+scene.id());
                var settings=new StructurePlaceSettings().setRotation(rotation).setKnownShape(false);
                require(template.placeInWorld(Probe.level,scene.origin(),BlockPos.ZERO,settings,Probe.level.random,2),"Armory placement failed");
                int entryX=pos(row.getAsJsonArray("entrance")).getX();
                for(int x=entryX-1;x<=entryX+2;x++)for(int z=-5;z<0;z++)Probe.level.setBlock(scene.at(new BlockPos(x,0,z)),Blocks.COBBLESTONE.defaultBlockState(),3);
                int lamps=0,doors=0,panes=0,shutters=0,banners=0;
                var detailBlocks=new LinkedHashSet<Block>();
                if(row.has("details"))for(var valueDetail:row.getAsJsonArray("details")){
                    var detail=valueDetail.getAsJsonObject();
                    var block=BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(detail.get("block").getAsString())).orElseThrow();
                    detailBlocks.add(block);
                    var at=scene.at(pos(detail.getAsJsonArray("pos")));
                    require(Probe.level.getBlockState(at).is(block),"Missing scheduled detail "+scene.id()+rotation+at);
                }else detailBlocks.addAll(List.of(Blocks.LANTERN,Blocks.OAK_DOOR,Blocks.GLASS_PANE,Blocks.OAK_TRAPDOOR,Blocks.GREEN_WALL_BANNER));
                for(var block:detailBlocks){
                    for(var info:template.filterBlocks(scene.origin(),settings,block)){
                        var state=Probe.level.getBlockState(info.pos());
                        require(state.is(block)&&state.canSurvive(Probe.level,info.pos()),"Missing/unsupported detail "+scene.id()+rotation+" "+info.pos());
                        if(block==Blocks.LANTERN)lamps++;
                        if(block instanceof DoorBlock)doors++;
                        if(block==Blocks.GLASS_PANE)panes++;
                        if(block instanceof TrapDoorBlock)shutters++;
                        if(block instanceof WallBannerBlock)banners++;
                    }
                }
                require(lamps==row.getAsJsonArray("lanterns").size()&&doors==row.getAsJsonArray("doors").size()*2&&banners==1,"Lost Armory decorations "+scene.id()+rotation);
                if(!row.has("details"))require(panes==4&&shutters==4,"Lost Plains glazing/shutters");
                if(row.has("thresholds"))for(var valueThreshold:row.getAsJsonArray("thresholds")){
                    var threshold=valueThreshold.getAsJsonObject();var local=pos(threshold.getAsJsonArray("pos"));var at=scene.at(local);
                    var shape=Probe.level.getBlockState(at).getCollisionShape(Probe.level,at);
                    require(!shape.isEmpty()&&Math.abs(local.getY()+shape.max(Direction.Axis.Y)-threshold.get("top").getAsDouble())<.001,"Wrong landing height "+scene.id()+rotation+at);
                }
                var anchor=scene.at(pos(row.getAsJsonArray("anchor")));
                require(Probe.level.getBlockEntity(anchor) instanceof ArmoryBlockEntity,"Functional Armory missing "+rotation);
                var be=(ArmoryBlockEntity)Probe.level.getBlockEntity(anchor);
                require(count(be.getInventory())==0,"Preview must begin empty");
                var armoryState=Probe.level.getBlockState(anchor);
                require(armoryState.getValue(BlockStateProperties.HORIZONTAL_FACING)==rotation.rotate(Direction.NORTH),"Wrong Armory facing "+rotation);
                for(var mirror:Mirror.values())require(armoryState.mirror(mirror).getValue(BlockStateProperties.HORIZONTAL_FACING)==mirror.mirror(rotation.rotate(Direction.NORTH)),"Wrong Armory mirror "+rotation+mirror);
                var doorCells=new HashSet<BlockPos>();for(var a:row.getAsJsonArray("doors"))doorCells.add(pos(a.getAsJsonArray()));
                int clear=0;
                for(var a:row.getAsJsonArray("standing")){
                    var local=pos(a.getAsJsonArray());var p=scene.at(local);
                    require(Probe.level.getBlockState(p.below()).isFaceSturdy(Probe.level,p.below(),Direction.UP),"Unsupported route "+p);
                    if(doorCells.contains(local))continue;
                    for(int y=0;y<3;y++)require(Probe.level.getBlockState(p.above(y)).getCollisionShape(Probe.level,p.above(y)).isEmpty(),"Route collision "+p);
                    clear++;
                }
                for(var a:row.getAsJsonArray("openSky"))require(Probe.level.canSeeSky(scene.at(pos(a.getAsJsonArray()))),"Covered courtyard "+scene.id()+rotation+a);
                checks.add(Map.of("layout",scene.id(),"rotation",rotation.name(),"lanterns",lamps,"doorHalves",doors,"panes",panes,"shutters",shutters,"banner",banners,"clearStandingCells",clear,"emptyFunctionalArmory",true,"openCourtCells",row.getAsJsonArray("openSky").size()));
                if(rotation==Rotation.NONE)addViews(scene,views);
            }
            layoutIndex++;
        }
        rainReview=layouts.asList().stream().anyMatch(v->v.getAsJsonObject().get("id").getAsString().endsWith("supply_yard"));
        Probe.result.put("armoryDesignChecks",checks);
    }
    private static void addViews(Scene s,List<BarracksDesignPreview.View> views){
        if(s.row().has("region")){
            var size=pos(s.row().getAsJsonArray("size"));double w=size.getX(),h=size.getY(),d=size.getZ();
            double f=s.row().get("floorY").getAsDouble();var store=s.row().getAsJsonArray("storeBounds");
            double sx=store.get(0).getAsDouble(),front=store.get(1).getAsDouble(),back=store.get(3).getAsDouble();
            view(s,views,"front",-9,h*.8,-12,w*.5,h*.35,d*.45,65,4500);
            view(s,views,"roof",w+12,h+16,-10,w*.5,4,d*.5,65,4500);
            view(s,views,"store",sx+3.4,f+.1,front+2.7,w-3,f+1,back-1,85,4500);
            if(s.row().get("region").getAsString().equals("snowy"))
                view(s,views,"lobby",7.5,f+.1,front-2.5,9,f+1,front+2,85,4500);
            view(s,views,"night",-9,h*.8,-12,w*.5,h*.35,d*.45,65,18000);
            if(s.id().endsWith("supply_yard"))view(s,views,"rain",w-3,f+1,-5,w*.5,f+1,front,75,4500);
        }else if(s.id().equals("compact")){
            view(s,views,"front",-8,8,-10,6,4,6,65,4500);
            view(s,views,"rear",20,10,23,6,4,6,65,4500);
            view(s,views,"roof",18,22,-10,6,5,6,65,4500);
            view(s,views,"workroom",4.3,1.1,5.2,9.5,2,9,85,4500);
            view(s,views,"shelves",9.5,1.1,8.4,2,2,6,85,4500);
            view(s,views,"night",-8,8,-10,6,4,6,65,18000);
        }else{
            view(s,views,"front",26,10,-11,8,3,8,65,4500);
            view(s,views,"approach",9,2,-6,8,2,10,75,4500);
            view(s,views,"rear",-11,11,29,8,4,11,65,4500);
            view(s,views,"roof",26,26,-8,8,3,9,65,4500);
            view(s,views,"workshop",10.5,1.1,4.7,3,2,5.7,80,4500);
            view(s,views,"storehouse",4.8,1.1,10.6,10.2,2,13,85,4500);
            view(s,views,"shelves",12.5,1.1,13,2.5,2,10.5,85,4500);
            view(s,views,"night",26,10,-11,8,3,8,65,18000);
            view(s,views,"rain",14,3,-5,7,2,8,75,4500);
        }
    }
    private static void view(Scene s,List<BarracksDesignPreview.View> views,String name,double x,double y,double z,double tx,double ty,double tz,int fov,long time){
        var o=s.origin();views.add(new BarracksDesignPreview.View("armory-"+s.id()+"-"+name,o.getX()+x,o.getY()+y,o.getZ()+z,o.getX()+tx,o.getY()+ty,o.getZ()+tz,fov,time));
    }

    static boolean tick(){
        if(started==-1){
            var lights=new ArrayList<Object>();
            if(rainReview)require(Probe.level.isRaining(),"Rain review did not start");
            for(var scene:scenes)if(scene.rotation()==Rotation.NONE){
                for(var a:scene.row().getAsJsonArray("targets")){
                    var p=scene.at(pos(a.getAsJsonArray()));int light=Probe.level.getBrightness(LightLayer.BLOCK,p);
                    require(light>0,"Unlit work/service area "+p);
                    require(!Probe.level.canSeeSky(p),"Uncovered work/service area "+p);
                    require(!Probe.level.isRainingAt(p),"Wet sheltered work area "+p);
                    lights.add(Map.of("layout",scene.id(),"position",p.toShortString(),"blockLight",light,"roofCovered",true));
                }
                if(rainReview)for(var a:scene.row().getAsJsonArray("openSky"))require(Probe.level.isRainingAt(scene.at(pos(a.getAsJsonArray()))),"Court is not open to rain");
            }
            Probe.result.put("armoryLighting",lights);Probe.result.put("armoryRainReview",rainReview);
            Probe.level.setWeatherParameters(999999,0,false,false);Probe.level.setDayTime(4500);
            for(var scene:scenes)for(int i=0;i<2;i++){
                var guard=newGuard(scene.home(i));
                var nav=(GroundPathNavigation)guard.getNavigation();nav.setCanOpenDoors(true);nav.setCanPassDoors(true);
                var walk=new Walk(scene,i,guard);walks.add(walk);
                guard.goalSelector.addGoal(0,walk);guard.goalSelector.addGoal(1,new OpenDoorGoal(guard,true));
                Probe.level.addFreshEntity(guard);
            }
            started=Probe.ticks;Probe.progress("walking pairs of guards to each Armory work area");
        }
        if(leg<3){
            if(walks.stream().allMatch(Walk::arrived)){
                leg++;started=Probe.ticks;
                if(leg<3){
                    for(var w:walks)w.target=leg==1?w.scene.destination(1,w.member):w.scene.home(w.member);
                    Probe.progress(leg==1?"walking guards from work areas to supplies":"walking guards back through each entrance");
                }else{
                    Probe.result.put("armoryGroupMovement",Map.of("layouts",scenes.size()/4,"rotations",4,"guards",walks.size(),"completedLegs",walks.size()*3,"fixture","scripted goals with native navigation, doors and entity collision"));
                    walks.forEach(w->w.guard.discard());startResupply();
                }
            }
            if(leg<3&&Probe.ticks-started>600)throw new AssertionError("Armory walking timed out on leg "+leg+": "+walks.stream().filter(w->!w.arrived()).map(w->w.scene.id()+w.scene.rotation()+" "+w.guard.position()+" -> "+w.target).toList());
            return false;
        }
        if(resupplies.stream().allMatch(r->count(GuardAmmoAccess.getAmmoHandler(r.guard()))>0)){
            var results=new ArrayList<Object>();
            for(var r:resupplies){
                int reserve=count(GuardAmmoAccess.getAmmoHandler(r.guard())),remaining=count(r.source().getInventory());
                require(remaining+reserve==64,"Armory transfer did not conserve ammo "+r.scene().id());
                results.add(Map.of("layout",r.scene().id(),"initialSource",64,"remainingSource",remaining,"guardReserve",reserve,"guardAtObservation",r.guard().position().toString()));
                r.guard().discard();
            }
            Probe.result.put("armoryResupply",Map.of("passed",true,"layouts",results,"fixture","ordinary guard goals; no scripted movement or direct withdrawal; transfer range is not proof of entrance use"));
            return true;
        }
        if(Probe.ticks-started>1200)throw new AssertionError("Ordinary Armory resupply timed out: "+resupplies.stream().map(r->r.scene().id()+" "+r.guard().position()+" source="+count(r.source().getInventory())).toList());
        return false;
    }
    private static void startResupply(){
        for(var scene:scenes)if(scene.rotation()==Rotation.NONE){
            var supply=(ArmoryBlockEntity)Probe.level.getBlockEntity(scene.at(pos(scene.row().getAsJsonArray("anchor"))));
            var ammo=AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(64).build();
            require(!ammo.isEmpty(),"Missing TaCZ ammo");var remainder=ammo;
            for(int slot=0;slot<supply.getInventory().getSlots()&&!remainder.isEmpty();slot++)remainder=supply.getInventory().insertItem(slot,remainder,false);
            require(remainder.isEmpty()&&count(supply.getInventory())==64,"Armory rejected preview test stock: "+remainder);
            var guard=newGuard(scene.home(0));
            var gun=GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).setFireMode(FireMode.AUTO).build();
            require(gun.getItem() instanceof IGun,"Missing TaCZ gun");
            ((IGun)gun.getItem()).setCurrentAmmoCount(gun,30);((IGun)gun.getItem()).setBulletInBarrel(gun,true);
            guard.setItemSlot(EquipmentSlot.MAINHAND,gun);GuardAmmoAccess.getAmmoHandler(guard).clear();
            Probe.level.addFreshEntity(guard);resupplies.add(new Resupply(scene,guard,supply));
        }
        Probe.progress("checking ordinary guard resupply in the furnished Armories");
    }
    private static Guard newGuard(BlockPos p){
        var guard=GuardEntityType.GUARD.get().create(Probe.level);require(guard!=null,"Missing Guard Villagers");
        guard.setPersistenceRequired();guard.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);return guard;
    }
    private static int count(IItemHandler inventory){int sum=0;for(int i=0;i<inventory.getSlots();i++)sum+=inventory.getStackInSlot(i).getCount();return sum;}
    private static final class Walk extends Goal{
        final Scene scene;final int member;final Guard guard;BlockPos target;
        Walk(Scene scene,int member,Guard guard){this.scene=scene;this.member=member;this.guard=guard;this.target=scene.destination(0,member);setFlags(EnumSet.of(Flag.MOVE));}
        boolean arrived(){return guard.isAlive()&&Math.abs(guard.getY()-target.getY())<.8&&guard.position().distanceToSqr(target.getCenter().add(0,-.5,0))<.75;}
        public boolean canUse(){return true;}
        public boolean requiresUpdateEveryTick(){return true;}
        public void tick(){if(arrived())guard.getNavigation().stop();else if(guard.tickCount%10==0)guard.getNavigation().moveTo(guard.getNavigation().createPath(target,0),1.0);}
    }
}
