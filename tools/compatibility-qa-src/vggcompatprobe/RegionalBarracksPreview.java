package vggcompatprobe;

import com.google.gson.*;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostSpawner;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import tallestegg.guardvillagers.GuardEntityType;

/** Native checks of the separate architecture-only datapack, using packaged Forge. */
final class RegionalBarracksPreview {
    private static final ResourceLocation MANIFEST=ResourceLocation.fromNamespaceAndPath("vggpreview","regional_barracks_manifest.json");
    static boolean available(){return Probe.level.getServer().getResourceManager().getResource(MANIFEST).isPresent();}
    static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    static void setup(List<BarracksDesignPreview.View> views,List<Object> checks){
        try(var reader=Probe.level.getServer().getResourceManager().getResourceOrThrow(MANIFEST).openAsReader()){
            var layouts=JsonParser.parseReader(reader).getAsJsonArray();require(layouts.size()==16,"Expected all regional layouts");
            for(int x=-2;x<=7;x++)for(int z=-2;z<=7;z++)Probe.level.setChunkForced(x,z,true);
            int index=0;
            for(var value:layouts){
                var row=value.getAsJsonObject();String id=row.get("id").getAsString();
                for(var rotation:Rotation.values())validate(row,rotation,checks);
                BlockPos o=new BlockPos((index%4)*28,60,(index/4)*28);place(row,o,Rotation.NONE);RegionalBarracksMovement.register(row,o);
                int w=row.getAsJsonArray("size").get(0).getAsInt(),d=row.getAsJsonArray("size").get(2).getAsInt();
                double cx=o.getX()+w*.5,cz=o.getZ()+d*.5;
                if(row.has("integration")) {
                    if(index==0)views.add(new BarracksDesignPreview.View("regional-approach",cx,75,-12,cx,64,cz,70,4500));
                    Probe.progress("validated regional integration "+id);index++;continue;
                }
                views.add(new BarracksDesignPreview.View(id+"-front",o.getX()+w+9,o.getY()+10,o.getZ()-11,cx,o.getY()+5,cz,65,4500));
                views.add(new BarracksDesignPreview.View(id+"-roof",o.getX()+w+5,o.getY()+25,o.getZ()-5,cx,o.getY()+4,cz,70,4500));
                if(id.equals("plains-compact")||id.equals("snowy-courtyard")||id.equals("ocean-compact")){
                    var a=pos(row.getAsJsonArray("guards").get(0).getAsJsonArray()).offset(o);
                    views.add(new BarracksDesignPreview.View(id+"-inside",a.getX()+.5,a.getY(),a.getZ()+.5,o.getX()+3,o.getY()+4,o.getZ()+4,95,4500));
                    views.add(new BarracksDesignPreview.View(id+"-night",o.getX()+w+9,o.getY()+10,o.getZ()-11,cx,o.getY()+5,cz,65,18000));
                }
                Probe.progress("validated regional layout "+id);index++;
            }
            Probe.result.put("regionalLayoutCount",layouts.size());
            Probe.result.put("regionalScope","controlled geometry, native attachments and path construction; no natural pool integration");
        }catch(java.io.IOException ex){throw new RuntimeException(ex);}
    }
    private static StructureTemplate place(JsonObject row,BlockPos origin,Rotation rotation){
        var id=ResourceLocation.parse(row.get("template").getAsString());
        var t=Probe.level.getStructureManager().get(id).orElseThrow();
        require(t.getSize().equals(pos(row.getAsJsonArray("size"))),"Wrong size "+id);
        var settings=new StructurePlaceSettings().setRotation(rotation).setKnownShape(false);
        if(row.has("integration"))settings.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK).addProcessor(JigsawReplacementProcessor.INSTANCE);
        require(t.placeInWorld(Probe.level,origin,BlockPos.ZERO,settings,Probe.level.random,2),"Failed placement "+id);
        return t;
    }
    private static void validate(JsonObject row,Rotation rotation,List<Object> checks){
        var level=Probe.level;var origin=new BlockPos(8,160,8);var bounds=new BoundingBox(-9,159,-9,25,178,25);String id=row.get("id").getAsString();
        place(row,origin,rotation);
        for(var a:row.getAsJsonArray("guards"))require(GuardPostSpawner.isSafeSpawnCell(level,bounds,origin.offset(pos(a.getAsJsonArray()).rotate(rotation))),id+" unsafe initial cell "+a);
        require(GuardPostSpawner.isSafeSpawnCell(level,bounds,origin.offset(pos(row.getAsJsonArray("sentry")).rotate(rotation))),id+" unsafe sentry");
        int lamps=0,doors=0,banners=0,carpets=0;
        for(var p:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ())){
            var state=level.getBlockState(p);var block=state.getBlock();
            if(block instanceof LanternBlock||block instanceof DoorBlock||block instanceof WallBannerBlock||block instanceof CarpetBlock){require(state.canSurvive(level,p),id+" unsupported "+p+" "+state);if(block instanceof LanternBlock)lamps++;if(block instanceof DoorBlock)doors++;if(block instanceof WallBannerBlock)banners++;if(block instanceof CarpetBlock)carpets++;}
        }
        require(lamps==row.get("lanterns").getAsInt(),id+" missing lanterns "+lamps);
        require(doors==row.get("doorHalves").getAsInt(),id+" missing door halves "+doors);
        require(banners==1,id+" missing banner");
        require(carpets==row.get("carpets").getAsInt(),id+" missing decorative bedrolls");
        var guard=GuardEntityType.GUARD.get().create(level);require(guard!=null,"Missing Guard Villagers");
        var nav=(GroundPathNavigation)guard.getNavigation();nav.setCanOpenDoors(true);nav.setCanPassDoors(true);
        var entry=origin.offset(pos(row.getAsJsonArray("entrance")).rotate(rotation));guard.setPos(entry.getX()+.5,entry.getY(),entry.getZ()+.5);guard.setOnGround(true);
        int paths=0;
        for(var a:row.getAsJsonArray("targets")){var destination=origin.offset(pos(a.getAsJsonArray()).rotate(rotation));var path=nav.createPath(destination,0);require(path!=null&&path.canReach(),id+" "+rotation+" no path "+a);paths++;}
        guard.discard();checks.add(Map.of("layout",id,"rotation",rotation.name(),"safeSpawnCells",4,"supportedLanterns",lamps,"doorHalves",doors,"nativePaths",paths,"banners",banners,"carpets",carpets));
        for(var p:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()))if(!level.getBlockState(p).isAir())level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
    }
}
