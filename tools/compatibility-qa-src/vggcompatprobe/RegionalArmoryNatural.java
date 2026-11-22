package vggcompatprobe;

import com.google.gson.*;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.worldgen.village.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.saveddata.SavedData;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Disposable natural-road walking and generated-stock persistence trials. */
final class RegionalArmoryNatural {
    static final ResourceLocation MANIFEST=ResourceLocation.parse("vggarmories:integration_manifest.json");
    private record Scene(JsonObject row, BlockPos anchor, BlockPos service, BlockPos road,
                         Rotation rotation, VillageBuildingRecord record, ArmoryBlockEntity armory) { }
    private static final List<Scene> scenes=new ArrayList<>();
    private static final List<Walk> walks=new ArrayList<>();
    private static final List<Object> checks=new ArrayList<>();
    private static int started=-1,leg;
    private static BlockPos pos(JsonArray a){return RegionalBarracksPreview.pos(a);}
    private static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    static boolean beforeFinish(){
        var resource=Probe.level.getServer().getResourceManager().getResource(MANIFEST);
        if(resource.isEmpty())return true;
        if(started<0){
            Map<String,JsonObject> rows=new HashMap<>();
            try(var reader=resource.get().openAsReader()){
                for(var v:JsonParser.parseReader(reader).getAsJsonArray()){
                    var row=v.getAsJsonObject();rows.put(row.get("entry").getAsString(),row);
                }
            }catch(java.io.IOException e){throw new RuntimeException(e);}
            // Keep resident activity from changing the stock between the fixture's
            // save and reload checkpoints. This flag persists only in its test world.
            for(var guard:Probe.level.getEntitiesOfClass(Guard.class,Probe.area))guard.setNoAi(true);
            for(var p:Probe.start.getPieces()){
                if(!(p instanceof PoolElementStructurePiece piece)||!(piece.getElement() instanceof VillageBuildingPoolElement element))continue;
                var row=rows.get(element.entryId().toString());if(row==null)continue;
                var rotation=piece.getRotation();var origin=piece.getPosition();
                var connector=origin.offset(pos(row.getAsJsonArray("connector")).rotate(rotation));
                var outside=connector.offset(new BlockPos(0,0,-1).rotate(rotation));
                var anchor=origin.offset(pos(row.getAsJsonArray("anchor")).rotate(rotation));
                var service=origin.offset(pos(row.getAsJsonArray("service")).rotate(rotation));
                var record=VillageBuildingSavedData.get(Probe.level).getAt(Probe.level.dimension().location(),anchor);
                require(record!=null&&record.identity().buildingType()==VillageBuildingType.ARMORY,"Unregistered Armory "+anchor);
                require(record.hasClaimed(VillageBuildingAction.INITIAL_STOCK)&&!record.hasClaimed(VillageBuildingAction.GARRISON),"Wrong Armory action claims "+anchor);
                require(Probe.level.getBlockEntity(anchor) instanceof ArmoryBlockEntity,"Missing generated Armory "+anchor);
                var be=(ArmoryBlockEntity)Probe.level.getBlockEntity(anchor);
                require(be.isGeneratedFor(record.identity().buildingId())&&!be.getGeneratedIdentity().pending(),"Pending or foreign stock identity "+anchor);
                require(be.getSquadLock()==null,"Generated village Armory should be public");
                require(Probe.level.getBlockState(anchor).getValue(BlockStateProperties.HORIZONTAL_FACING)==rotation.rotate(Direction.NORTH),"Wrong Armory facing");
                Double standing=null;String roadState="";BlockPos road=null;
                var guard=GuardEntityType.GUARD.get().create(Probe.level);require(guard!=null,"Missing guard type");
                guard.setPersistenceRequired();
                var nav=(GroundPathNavigation)guard.getNavigation();nav.setCanOpenDoors(true);nav.setCanPassDoors(true);
                net.minecraft.world.level.pathfinder.Path path=null;
                // Both entrance lanes are usable approaches. Trees may overhang
                // one lane; never treat their canopy as the adjoining road.
                for(int lane=0;lane<2&&road==null;lane++){
                    var approach=outside.offset(new BlockPos(lane,0,0).rotate(rotation));
                    for(int dy=0;dy>=-2;dy--){
                        var at=approach.offset(0,dy,0);var state=Probe.level.getBlockState(at);var shape=state.getCollisionShape(Probe.level,at);
                        if(shape.isEmpty())continue;
                        if(state.getBlock() instanceof FenceBlock||state.getBlock() instanceof WallBlock)break;
                        double surface=at.getY()+shape.max(Direction.Axis.Y);
                        guard.setPos(approach.getX()+.5,surface,approach.getZ()+.5);guard.setOnGround(true);
                        if(Probe.level.getBlockCollisions(guard,guard.getBoundingBox()).iterator().hasNext())break;
                        var feet=BlockPos.containing(guard.position());
                        if(!Probe.level.getFluidState(feet).isEmpty()||!Probe.level.getFluidState(feet.above()).isEmpty())break;
                        var candidate=nav.createPath(service,0);
                        // A bounded partial path counts only if the physical walk
                        // below completes both legs using repeated native searches.
                        if(candidate==null||candidate.getNodeCount()<2)break;
                        standing=surface;roadState=state.toString();path=candidate;
                        road=new BlockPos(approach.getX(),(int)Math.ceil(surface),approach.getZ());break;
                    }
                }
                require(road!=null,element.entryId()+" no clear advancing natural road-to-service approach at "+connector);
                for(int y=0;y<2;y++)require(Probe.level.getFluidState(service.above(y)).isEmpty(),"Flooded service area "+service);
                var scene=new Scene(row,anchor,service,road,rotation,record,be);scenes.add(scene);
                var walk=new Walk(scene,guard);walks.add(walk);guard.goalSelector.addGoal(0,walk);guard.goalSelector.addGoal(1,new OpenDoorGoal(guard,true));Probe.level.addFreshEntity(guard);
                var check=new LinkedHashMap<String,Object>(Map.of("entry",element.entryId().toString(),"rotation",rotation.name(),
                    "connector",List.of(connector.getX(),connector.getY(),connector.getZ()),"roadStandingY",standing,
                    "roadState",roadState,"anchor",List.of(anchor.getX(),anchor.getY(),anchor.getZ()),
                    "service",List.of(service.getX(),service.getY(),service.getZ()),"roadToServicePath",path.canReach(),
                    "initialPathNodes",path.getNodeCount(),"initialPathEnd",path.getEndNode().asBlockPos().toShortString()));
                check.put("road",List.of(road.getX(),road.getY(),road.getZ()));checks.add(check);
            }
            require(!scenes.isEmpty(),"No selected regional Armories; an empty sample is not a pass");
            Probe.result.put("regionalArmoryNaturalChecks",checks);started=Probe.ticks;
            Probe.progress("walking from natural roads into generated Armories");
        }
        if(leg<2){
            if(walks.stream().allMatch(Walk::arrived)){
                leg++;started=Probe.ticks;
                if(leg==1){for(var w:walks)w.target=w.scene.road();Probe.progress("walking back out to natural roads");}
            }
            if(leg<2){
                require(Probe.ticks-started<700,"Natural Armory walk timed out: "+walks.stream().filter(w->!w.arrived()).map(w->w.scene.row().get("entry")+" "+w.guard.position()+" -> "+w.target).toList());
                return false;
            }
        }
        walks.forEach(w->w.guard.discard());
        var saved=Probe.level.getDataStorage().computeIfAbsent(StockCheckpoint::load,StockCheckpoint::new,"vgg_probe_armory_stock");
        boolean restart=!saved.entries.isEmpty();List<Object> stock=new ArrayList<>();
        for(var scene:scenes){
            var id=scene.record().identity().buildingId().toString();var inventory=scene.armory().getInventory();int initial=count(inventory);
            if(restart){
                require(saved.entries.contains(id),"Reload selected a different Armory "+id);
                var prior=saved.entries.getCompound(id);
                require(prior.getString("inventory").equals(inventory.serializeNBT().toString()),"Stock changed or refilled across restart "+id);
                require(prior.getInt("actions")==scene.record().claimedActions(),"Action claims changed across restart "+id);
            }else{
                require(initial>0,"Initial generated stock missing "+id);
                // Exercise consumed stock and player-added metadata before saving.
                ItemStack sample=ItemStack.EMPTY;
                for(int slot=0;slot<inventory.getSlots();slot++){
                    var removed=inventory.extractItem(slot,Integer.MAX_VALUE,false);
                    if(sample.isEmpty()&&!removed.isEmpty())sample=removed;
                }
                require(!sample.isEmpty(),"No generated stock to exercise");sample.setCount(7);
                sample.getOrCreateTag().putString("vgg_probe_stock","preserve_after_reload");
                require(inventory.insertItem(0,sample,false).isEmpty(),"Cannot insert persistence sample");
                CompoundTag checkpoint=new CompoundTag();checkpoint.putString("inventory",inventory.serializeNBT().toString());checkpoint.putInt("actions",scene.record().claimedActions());
                saved.entries.put(id,checkpoint);saved.setDirty();
            }
            var before=inventory.serializeNBT().copy();
            var status=VillageBuildingArmoryLifecycle.activate(Probe.level,scene.record().identity(),scene.rotation()).status();
            require(status==VillageBuildingArmoryLifecycle.Status.ALREADY_CLAIMED&&before.equals(inventory.serializeNBT()),"Repeated activation changed stock "+id);
            stock.add(Map.of("entry",scene.row().get("entry").getAsString(),"buildingId",id,"initialObservedCount",initial,
                "savedCount",count(inventory),"publicAccess",true,"repeatActivationNoChange",true,"reloadMatched",restart));
        }
        Probe.result.put("regionalArmoryMovement",Map.of("guards",walks.size(),"completedLegs",walks.size()*2,"fixture","scripted native navigation on naturally generated roads"));
        Probe.result.put("regionalArmoryStock",Map.of("stage",restart?"reload_verified":"checkpoint_saved","armories",stock));
        return true;
    }
    private static int count(net.minecraftforge.items.IItemHandler h){int n=0;for(int i=0;i<h.getSlots();i++)n+=h.getStackInSlot(i).getCount();return n;}
    private static final class StockCheckpoint extends SavedData{
        CompoundTag entries=new CompoundTag();
        static StockCheckpoint load(CompoundTag tag){var s=new StockCheckpoint();s.entries=tag.getCompound("entries");return s;}
        @Override public CompoundTag save(CompoundTag tag){tag.put("entries",entries.copy());return tag;}
    }
    private static final class Walk extends Goal{
        final Scene scene;final Guard guard;BlockPos target;
        Walk(Scene scene,Guard guard){this.scene=scene;this.guard=guard;target=scene.service();setFlags(EnumSet.of(Flag.MOVE));}
        boolean arrived(){return guard.isAlive()&&Math.abs(guard.getY()-target.getY())<.8&&guard.position().distanceToSqr(target.getCenter().add(0,-.5,0))<1;}
        public boolean canUse(){return true;}
        public boolean requiresUpdateEveryTick(){return true;}
        public void tick(){if(arrived())guard.getNavigation().stop();else if(guard.tickCount%10==0)guard.getNavigation().moveTo(guard.getNavigation().createPath(target,0),1);}
    }
}
