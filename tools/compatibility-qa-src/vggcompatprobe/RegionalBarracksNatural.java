package vggcompatprobe;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingPoolElement;
import tallestegg.guardvillagers.GuardEntityType;

/** Observes actual selected pieces after normal village generation. */
final class RegionalBarracksNatural {
    static net.minecraft.world.level.levelgen.structure.StructureStart select(
            net.minecraft.world.level.levelgen.structure.Structure structure,
            net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure> holder,
            net.minecraft.world.level.levelgen.structure.StructureStart first) {
        var resource=Probe.level.getServer().getResourceManager().getResource(ResourceLocation.parse("vggregional:integration_manifest.json"));
        if(resource.isEmpty())resource=Probe.level.getServer().getResourceManager().getResource(RegionalArmoryNatural.MANIFEST);
        if(resource.isEmpty())resource=Probe.level.getServer().getResourceManager().getResource(RegionalSentryPostNatural.MANIFEST);
        if(resource.isEmpty())resource=Probe.level.getServer().getResourceManager().getResource(RegionalWatchtowerNatural.MANIFEST);
        if(resource.isEmpty())resource=Probe.level.getServer().getResourceManager().getResource(RegionalTrainingYardNatural.MANIFEST);
        if(resource.isEmpty())resource=Probe.level.getServer().getResourceManager().getResource(CustomBarracksNatural.MANIFEST);
        if(resource.isEmpty())resource=Probe.level.getServer().getResourceManager().getResource(ResourceLocation.parse("vggcompat:terrain_survey.json"));
        if(resource.isEmpty())return first;
        Set<String> wanted=new HashSet<>();int searchOrigins=9;int optionalBuildings=0;JsonArray terrainSites=new JsonArray();
        try(var reader=resource.get().openAsReader()) {
            for(var value:JsonParser.parseReader(reader).getAsJsonArray()){
                var row=value.getAsJsonObject();wanted.add(row.get("entry").getAsString());
                if(row.has("terrainSites"))terrainSites=row.getAsJsonArray("terrainSites");
                if(row.has("naturalSearchOrigins")){
                    int n=row.get("naturalSearchOrigins").getAsInt();
                    if(n<1||n>25)throw new IllegalArgumentException("naturalSearchOrigins must be 1..25");
                    searchOrigins=Math.max(searchOrigins,n);
                }
            }
        }catch(java.io.IOException e){throw new RuntimeException(e);}
        List<Object> samples=new ArrayList<>();Set<Long> seen=new HashSet<>();
        var chosen=first;
        boolean picked=false;
        int requested=Integer.getInteger("vgg.compat.regionalSampleCount",0);
        int[][] origins={{0,0},{8192,0},{-8192,0},{0,8192},{0,-8192},{8192,8192},{-8192,-8192},{8192,-8192},{-8192,8192},
                {-16384,-16384},{-8192,-16384},{0,-16384},{8192,-16384},{16384,-16384},
                {-16384,16384},{-8192,16384},{0,16384},{8192,16384},{16384,16384},
                {-16384,-8192},{-16384,0},{-16384,8192},{16384,-8192},{16384,0},{16384,8192}};
        for(int i=0;i<(requested>0?Math.min(requested,origins.length):searchOrigins);i++) {
            var candidate=i==0?first:null;
            if(candidate==null) {
                var located=Probe.level.getChunkSource().getGenerator().findNearestMapStructure(Probe.level,
                        net.minecraft.core.HolderSet.direct(holder),first.getChunkPos().getWorldPosition().offset(origins[i][0],0,origins[i][1]),100,false);
                if(located==null)continue;
                var pos=located.getFirst();
                for(int x=(pos.getX()>>4)-2;x<=(pos.getX()>>4)+2&&candidate==null;x++)
                    for(int z=(pos.getZ()>>4)-2;z<=(pos.getZ()>>4)+2&&candidate==null;z++) {
                        var found=Probe.level.getChunk(x,z).getAllStarts().get(structure);
                        if(found!=null&&found.isValid())candidate=found;
                    }
            }
            if(candidate==null||!seen.add(candidate.getChunkPos().toLong()))continue;
            long count=candidate.getPieces().stream().filter(p->p instanceof PoolElementStructurePiece piece
                    &&piece.getElement() instanceof VillageBuildingPoolElement e&&wanted.contains(e.entryId().toString())).count();
            var buildings = new ArrayList<Object>();
            for (var piece : candidate.getPieces()) {
                if (piece instanceof PoolElementStructurePiece pooled
                        && pooled.getElement() instanceof VillageBuildingPoolElement building) {
                    if(!building.entryId().getNamespace().equals("villagerguardgunners"))optionalBuildings++;
                    var b = pooled.getBoundingBox();
                    buildings.add(Map.of("entry", building.entryId().toString(), "type", building.buildingType().toString(),
                            "terrainSafety", building.terrainSafety(), "bounds", List.of(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())));
                }
            }
            samples.add(Map.of("chunkX",candidate.getChunkPos().x,"chunkZ",candidate.getChunkPos().z,
                    "regionalPieces",count,"totalPieces",candidate.getPieces().size(), "buildings", buildings));
            Probe.progress("inspected natural regional sample "+samples.size());
            if(count>0){if(!picked){chosen=candidate;picked=true;}if(requested==0)break;}
        }
        Probe.result.put("regionalNaturalSamples",samples);
        Probe.result.put("regionalNaturalOptionalBuildings", optionalBuildings);
        var siteChecks=new ArrayList<Object>();
        for(var value:terrainSites){
            var site=value.getAsJsonObject();var b=site.getAsJsonArray("bounds");int floor=site.get("floor").getAsInt();
            var columns=new ArrayList<Object>();var generator=Probe.level.getChunkSource().getGenerator();
            var randomState=Probe.level.getChunkSource().randomState();
            boolean allowed=com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingTerrainSafety.allows(
                    b.get(0).getAsInt(),b.get(2).getAsInt(),b.get(3).getAsInt(),b.get(5).getAsInt(),floor,(x,z)->{
                        int surface=generator.getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,Probe.level,randomState);
                        int oceanFloor=generator.getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,Probe.level,randomState);
                        columns.add(Map.of("x",x,"z",z,"surface",surface,"oceanFloor",oceanFloor));
                        return new com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingTerrainSafety.Column(surface,oceanFloor);
                    });
            siteChecks.add(Map.of("name",site.get("name").getAsString(),"allowed",allowed,"floor",floor,"columns",columns));
        }
        Probe.result.put("terrainSiteChecks",siteChecks);
        return chosen;
    }
    static void finish() {
        var resource=Probe.level.getServer().getResourceManager().getResource(ResourceLocation.parse("vggregional:integration_manifest.json"));
        if(resource.isEmpty())return;
        try(var reader=resource.get().openAsReader()) {
            var manifest=JsonParser.parseReader(reader).getAsJsonArray();
            Map<String,JsonObject> rows=new HashMap<>();for(var v:manifest){var row=v.getAsJsonObject();rows.put(row.get("entry").getAsString(),row);}
            List<Object> checks=new ArrayList<>();
            for(var p:Probe.start.getPieces()) {
                if(!(p instanceof PoolElementStructurePiece piece)||!(piece.getElement() instanceof VillageBuildingPoolElement element))continue;
                var row=rows.get(element.entryId().toString());if(row==null)continue;
                var rotation=piece.getRotation();var origin=piece.getPosition();
                var connector=origin.offset(RegionalBarracksPreview.pos(row.getAsJsonArray("connector")).rotate(rotation));
                var outside=connector.offset(new BlockPos(0,0,-1).rotate(rotation));
                var anchor=origin.offset(RegionalBarracksPreview.pos(row.getAsJsonArray("anchor")).rotate(rotation));
                Double standing=null;String roadState="";
                for(int dy=1;dy>=-2;dy--) {
                    var at=outside.offset(0,dy,0);var shape=Probe.level.getBlockState(at).getCollisionShape(Probe.level,at);
                    if(!shape.isEmpty()){
                        var block=Probe.level.getBlockState(at).getBlock();
                        if(block instanceof net.minecraft.world.level.block.FenceBlock||block instanceof net.minecraft.world.level.block.WallBlock)throw new AssertionError("Blocked road approach at "+at);
                        roadState=Probe.level.getBlockState(at).toString();standing=at.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);break;}
                }
                if(standing==null)throw new AssertionError(element.entryId()+" has no road support at "+outside);
                var guard=GuardEntityType.GUARD.get().create(Probe.level);var nav=(GroundPathNavigation)guard.getNavigation();nav.setCanOpenDoors(true);nav.setCanPassDoors(true);
                guard.setPos(outside.getX()+.5,standing,outside.getZ()+.5);guard.setOnGround(true);
                var path=nav.createPath(anchor,0);boolean reachable=path!=null&&path.canReach();guard.discard();
                checks.add(Map.of("entry",element.entryId().toString(),"rotation",rotation.name(),"connector",List.of(connector.getX(),connector.getY(),connector.getZ()),"roadStandingY",standing,"roadState",roadState,"anchorY",anchor.getY(),"roadToAssemblyPath",reachable));
                if(!reachable)throw new AssertionError(element.entryId()+" no natural road-to-assembly path at "+connector);
                if(com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingSavedData.get(Probe.level).getAt(Probe.level.dimension().location(),anchor)==null)throw new AssertionError("Missing registered Barracks at "+anchor);
                if(!Probe.level.getBlockState(anchor).isAir())throw new AssertionError("Unfinalized Barracks anchor "+anchor);
            }
            Probe.result.put("regionalNaturalChecks",checks);
        }catch(java.io.IOException e){throw new RuntimeException(e);}
    }
}
