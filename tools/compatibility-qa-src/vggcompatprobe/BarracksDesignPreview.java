package vggcompatprobe;

import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingDefinitions;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraftforge.event.TickEvent;
import tallestegg.guardvillagers.GuardEntityType;

/** Disposable, level-ground architectural review. Never used by the release mod. */
final class BarracksDesignPreview {
    record View(String name, double x, double y, double z, double tx, double ty, double tz, int fov, long time) { }
    static final List<View> views = new ArrayList<>();
    static volatile int view = -1, captured = -1;
    private static int cameraTick;
    private static final List<Object> checks = new ArrayList<>();
    private static final BlockPos COMPACT = new BlockPos(0,60,0), COURT = new BlockPos(24,60,0);

    static void tick(TickEvent.ServerTickEvent event) {
        var level = Probe.level;
        if (view == -1) {
            require(level.getChunkSource().getGenerator() instanceof FlatLevelSource, "Fresh flat review world required");
            Probe.result.put("observationKind", "controlled_barracks_design_preview");
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false,event.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,event.getServer());
            level.setWeatherParameters(999999,0,false,false);
            // Small bounded comparison plot: main templates are placed at surface height.
            for (int x=-3;x<=5;x++) for(int z=-3;z<=3;z++)level.setChunkForced(x,z,true);
            if (TrainingYardDesignPreview.available()) {
                TrainingYardDesignPreview.setup(views);
            } else if (WatchtowerDesignPreview.available()) {
                WatchtowerDesignPreview.setup(views);
            } else if (SentryPostDesignPreview.available()) {
                SentryPostDesignPreview.setup(views);
            } else if (ArmoryDesignPreview.available()) {
                ArmoryDesignPreview.setup(views);
            } else if (RegionalBarracksPreview.available()) {
                RegionalBarracksPreview.setup(views, checks);
            } else {
            validate("compact", new BlockPos(8,100,8), new BlockPos(11,1,8), new BlockPos(10,1,2));
            validate("courtyard", new BlockPos(8,100,8), new BlockPos(11,1,12), new BlockPos(13,1,2));
            place("village/plains/barracks",new BlockPos(-23,60,0),Rotation.NONE);
            place("village/plains/barracks/compact",COMPACT,Rotation.NONE);
            place("village/plains/barracks/courtyard",COURT,Rotation.NONE);
            require(level.getBlockState(COMPACT).is(Blocks.GRASS_BLOCK)
                    &&level.getBlockState(COURT).is(Blocks.GRASS_BLOCK),"Template overwrote reserved terrain");
            for(int x=-26;x<=44;x++)for(int z=-4;z<=-2;z++)level.setBlock(new BlockPos(x,60,z),Blocks.GRAVEL.defaultBlockState(),3);
            // A one-block apron joins all three connectors to the shared comparison road.
            for(var p:List.of(new BlockPos(-17,60,-1),new BlockPos(8,60,-1),new BlockPos(34,60,-1)))
                for(int dx=0;dx<=1;dx++)level.setBlock(p.offset(dx,0,0),Blocks.COBBLESTONE.defaultBlockState(),3);
            paths("compact",COMPACT,List.of(new BlockPos(3,1,7),new BlockPos(12,1,9),new BlockPos(14,1,10)));
            paths("courtyard",COURT,List.of(new BlockPos(3,1,6),new BlockPos(3,1,10),new BlockPos(15,1,4)));
            views.add(new View("comparison",9,84,-34,9,64,6,70,4500));
            addViews("compact",COMPACT,15,13);
            addViews("courtyard",COURT,16,16);
            views.add(new View("compact-common-room",8.6,61.1,9.8,11.3,62.2,4.7,90,4500));
            views.add(new View("compact-sleeping-wing",5.4,61.0,7.4,2.8,61.8,4.4,90,4500));
            views.add(new View("courtyard-common-room",31.4,61.1,12.7,36.5,62.1,10.1,90,4500));
            views.add(new View("courtyard-sleeping-wing",29.1,61.0,7.1,26.7,61.9,3.6,90,4500));
            views.add(new View("courtyard-covered-walk",36.3,61.1,3.8,31.5,63,8,80,4500));
            views.add(new View("compact-night",-9,68,-12,7.5,64,6,70,18000));
            views.add(new View("courtyard-night",46,70,-12,32,64,7,70,18000));
            }
            setView(0,event);
        } else if (captured == view) {
            if (view+1 < views.size()) setView(view+1,event);
            else {
                if(TrainingYardDesignPreview.available()&&!TrainingYardDesignPreview.tick())return;
                if(WatchtowerDesignPreview.available()&&!WatchtowerDesignPreview.tick())return;
                if(SentryPostDesignPreview.available()&&!SentryPostDesignPreview.tick())return;
                if(ArmoryDesignPreview.available()&&!ArmoryDesignPreview.tick())return;
                if(!RegionalBarracksMovement.tick())return;
                Probe.result.put("barracksDesignChecks",checks);
                Probe.result.put("screenshots",views.stream().map(View::name).toList());
                Probe.result.put("status","observed");
                Probe.write("result.json",Probe.result);Probe.done=true;event.getServer().halt(false);
            }
        } else if (Probe.ticks-cameraTick>400) throw new IllegalStateException("Screenshot did not complete");
    }
    private static void addViews(String name,BlockPos o,int width,int depth) {
        views.add(new View(name+"-front",o.getX()+(name.equals("courtyard")?width+8:-8),68,-11,o.getX()+width*.5,64,depth*.5,60,4500));
        views.add(new View(name+"-rear",o.getX()+width+10,71,depth+13,o.getX()+width*.5,65,depth*.5,70,4500));
        views.add(new View(name+"-roof",o.getX()+width+5,85,-5,o.getX()+width*.5,65,depth*.5,70,4500));
    }
    private static void setView(int index,TickEvent.ServerTickEvent event) {
        var v=views.get(index);
        double dx=v.tx-v.x,dy=v.ty-(v.y+1.62),dz=v.tz-v.z;
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
        float pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        for(var player:event.getServer().getPlayerList().getPlayers()) {
            player.setGameMode(GameType.SPECTATOR);player.teleportTo(Probe.level,v.x,v.y,v.z,yaw,pitch);
        }
        if(ArmoryDesignPreview.available()||SentryPostDesignPreview.available()||WatchtowerDesignPreview.available()||TrainingYardDesignPreview.available()) {
            if(v.name.endsWith("-rain"))Probe.level.setWeatherParameters(0,999999,true,false);
            else Probe.level.setWeatherParameters(999999,0,false,false);
        }
        Probe.level.setDayTime(v.time);cameraTick=Probe.ticks;view=index;
        Probe.progress("capturing "+v.name);
    }
    private static StructureTemplate place(String path,BlockPos origin,Rotation rotation) {
        var template=Probe.level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("villagerguardgunners",path)).orElseThrow();
        var settings=new StructurePlaceSettings().setRotation(rotation).setKnownShape(false);
        // Legacy templates contain authored structure void blocks. Ignore those in this comparison scene.
        if(path.equals("village/plains/barracks"))settings.addProcessor(new BlockIgnoreProcessor(List.of(Blocks.STRUCTURE_VOID)));
        require(template.placeInWorld(Probe.level,origin,BlockPos.ZERO,settings,Probe.level.random,2),"Template failed to place");
        for(var block:List.of(Blocks.STRUCTURE_BLOCK,Blocks.JIGSAW))for(var marker:template.filterBlocks(origin,settings,block))
            Probe.level.setBlock(marker.pos(),Blocks.AIR.defaultBlockState(),3);
        return template;
    }
    private static void validate(String name,BlockPos origin,BlockPos anchorOffset,BlockPos sentryOffset) {
        var level=Probe.level;
        var entry=VillageBuildingDefinitions.get(ResourceLocation.fromNamespaceAndPath("villagerguardgunners","barracks/plains/"+name));
        require(entry!=null&&entry.dutySlots().orElseThrow().size()==4,"Authored definition missing");
        var bounds=new BoundingBox(-9,99,-9,25,113,25);
        for(var rotation:Rotation.values()) {
            var template=place("village/plains/barracks/"+name,origin,rotation);
            var settings=new StructurePlaceSettings().setRotation(rotation);
            BlockPos anchor=origin.offset(anchorOffset.rotate(rotation));
            for(int member=0;member<4;member++)require(GuardPostSpawner.isSafeSpawnCell(level,bounds,
                    GuardPostSpawner.spawnPosition(anchor,rotation,member)),"Unsafe initial guard cell: "+name+rotation);
            require(GuardPostSpawner.isSafeSpawnCell(level,bounds,origin.offset(sentryOffset.rotate(rotation))),"Unsafe sentry");
            int lamps=0,doors=0;
            for(var block:List.of(Blocks.LANTERN,Blocks.OAK_DOOR,Blocks.SPRUCE_PRESSURE_PLATE,Blocks.GREEN_WALL_BANNER))
                for(var info:template.filterBlocks(origin,settings,block)) {
                    var state=level.getBlockState(info.pos());
                    require(state.is(block)&&state.canSurvive(level,info.pos()),"Unsupported decoration: "+info.pos()+" "+state);
                    if(block==Blocks.LANTERN)lamps++;
                    if(block==Blocks.OAK_DOOR)doors++;
                }
            require(lamps==(name.equals("compact")?3:4)&&doors==4,"Lighting/paired door contract changed");
            checks.add(Map.of("layout",name,"rotation",rotation.name(),"safeSpawnCells",4,"safeSentry",true,"supportedLanterns",lamps,"doorHalves",doors));
            for(var p:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()))
                if(!level.getBlockState(p).isAir())level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
        }
    }
    private static void paths(String name,BlockPos origin,List<BlockPos> destinations) {
        var guard=GuardEntityType.GUARD.get().create(Probe.level);
        require(guard!=null,"Guard entity unavailable");
        var nav=(GroundPathNavigation)guard.getNavigation();nav.setCanOpenDoors(true);nav.setCanPassDoors(true);
        BlockPos entry=origin.offset(name.equals("compact")?8:10,1,1);
        guard.setPos(entry.getX()+.5,entry.getY(),entry.getZ()+.5);guard.setOnGround(true);
        for(var relative:destinations) {
            var target=origin.offset(relative);
            var path=nav.createPath(target,0);
            require(path!=null&&path.canReach(),"No native guard path: "+name+relative);
        }
        checks.add(Map.of("layout",name,"nativePathsFromEntrance",destinations.size(),"doorOpeningEnabled",true));
        guard.discard();
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
