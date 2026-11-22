package vggcompatprobe;

import com.google.gson.*;
import com.gamergaming.villagerguardgunners.api.GuardPostAccess;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIds;
import com.gamergaming.villagerguardgunners.defense.duty.*;
import com.gamergaming.villagerguardgunners.worldgen.village.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.saveddata.SavedData;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Disposable natural-road walking and garrison persistence trials. */
final class RegionalTrainingYardNatural {
    static final ResourceLocation MANIFEST=ResourceLocation.parse("vggtrainingyards:integration_manifest.json");
    private record Scene(JsonObject row, BlockPos anchor, BlockPos service, BlockPos work, BlockPos road,
                         Rotation rotation, VillageBuildingRecord record, List<Guard> residents, List<BlockPos> route) { }
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
            for(var p:Probe.start.getPieces()){
                if(!(p instanceof PoolElementStructurePiece piece)||!(piece.getElement() instanceof VillageBuildingPoolElement element))continue;
                var row=rows.get(element.entryId().toString());if(row==null)continue;
                var rotation=piece.getRotation();var origin=piece.getPosition();
                var connector=origin.offset(pos(row.getAsJsonArray("connector")).rotate(rotation));
                var outside=connector.offset(new BlockPos(0,0,-1).rotate(rotation));
                var anchor=origin.offset(pos(row.getAsJsonArray("anchor")).rotate(rotation));
                var service=origin.offset(pos(row.getAsJsonArray("service")).rotate(rotation));
                var record=VillageBuildingSavedData.get(Probe.level).getAt(Probe.level.dimension().location(),anchor);
                require(record!=null&&record.identity().buildingType()==VillageBuildingType.TRAINING_YARD,"Unregistered Training Yard "+anchor);
                require(record.hasClaimed(VillageBuildingAction.GARRISON)&&!record.hasClaimed(VillageBuildingAction.INITIAL_STOCK),"Wrong Training Yard action claims "+anchor);
                require(Probe.level.getBlockState(anchor).isAir(),"Expected clear finalized Training Yard anchor at "+anchor+", found "+Probe.level.getBlockState(anchor));
                var layout=VillageDefenseDutySavedData.get(Probe.level).layout(record.identity().buildingId());
                require(layout!=null&&layout.slots().size()==2,"Missing paired drill duties "+anchor);
                for(var slot:layout.slots()){
                    int dx=slot.slotKey().equals("firing_center")?1:slot.slotKey().equals("firing_west")?-2:99;
                    require(slot.position().equals(anchor.offset(new BlockPos(dx,0,0).rotate(rotation)))
                        &&slot.facing()==rotation.rotate(Direction.SOUTH)&&slot.kind()==VillageDefenseDutySlotKind.DRILL
                        &&slot.weaponRequirement()==VillageDefenseDutyWeaponRequirement.RANGED,"Wrong rotated drill duty "+slot);
                    require(Probe.level.getFluidState(slot.position()).isEmpty(),"Flooded drill duty "+slot);
                }
                var residents=residents(record);
                require(residents.size()==2,"Expected two natural Training Yard guards on Normal for "+anchor+", found "+residents.size());
                for(var resident:residents){
                    int member=GuardPostAccess.getIdentity(resident).memberIndex();
                    require(member>=0&&member<=1&&resident.getUUID().equals(GuardPostIds.guardId(record.identity().buildingId(),member)),"Wrong garrison UUID");
                }
                var work=origin.offset(pos(row.getAsJsonArray("work")).rotate(rotation));
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
                        // below completes the full route using repeated native searches.
                        if(candidate==null||candidate.getNodeCount()<2)break;
                        standing=surface;roadState=state.toString();path=candidate;
                        road=new BlockPos(approach.getX(),(int)Math.ceil(surface),approach.getZ());break;
                    }
                }
                require(road!=null,element.entryId()+" no clear advancing natural road-to-service approach at "+connector);
                for(int y=0;y<2;y++)require(Probe.level.getFluidState(service.above(y)).isEmpty(),"Flooded service area "+service);
                int dryCells=0;
                for(var point:row.getAsJsonArray("standing")){
                    var cell=origin.offset(pos(point.getAsJsonArray()).rotate(rotation));
                    require(Probe.level.getBlockState(cell.below()).isFaceSturdy(Probe.level,cell.below(),Direction.UP),"Unsupported Training Yard standing cell "+cell);
                    for(int y=0;y<2;y++)require(Probe.level.getFluidState(cell.above(y)).isEmpty(),"Flooded Training Yard standing cell "+cell);
                    dryCells++;
                }
                if(row.has("rails"))for(var point:row.getAsJsonArray("rails")){
                    var cell=origin.offset(pos(point.getAsJsonArray()).rotate(rotation));
                    var shape=Probe.level.getBlockState(cell).getCollisionShape(Probe.level,cell);
                    require(!shape.isEmpty()&&shape.max(Direction.Axis.Y)>=1.5,"Missing Training Yard perimeter rail "+cell);
                }
                var route=new ArrayList<BlockPos>();
                for(var point:row.getAsJsonArray("walkTargets"))route.add(origin.offset(pos(point.getAsJsonArray()).rotate(rotation)));
                require(route.size()==8&&route.get(0).equals(service),"Unexpected Training Yard authored route");
                // The first route point is firing_center; also visit firing_west.
                var west=layout.slots().stream().filter(slot->slot.slotKey().equals("firing_west")).findFirst().orElseThrow().position();
                route.add(1,west);
                // Return along the firing line before taking the circulation lane.
                route.add(2,service);
                route.set(route.size()-1,road);
                var scene=new Scene(row,anchor,service,work,road,rotation,record,residents,route);scenes.add(scene);
                var walk=new Walk(scene,guard);walks.add(walk);guard.goalSelector.addGoal(0,walk);guard.goalSelector.addGoal(1,new OpenDoorGoal(guard,true));Probe.level.addFreshEntity(guard);
                var check=new LinkedHashMap<String,Object>(Map.of("entry",element.entryId().toString(),"rotation",rotation.name(),
                    "connector",List.of(connector.getX(),connector.getY(),connector.getZ()),"roadStandingY",standing,
                    "roadState",roadState,"anchor",List.of(anchor.getX(),anchor.getY(),anchor.getZ()),
                    "service",List.of(service.getX(),service.getY(),service.getZ()),"roadToServicePath",path.canReach(),
                    "initialPathNodes",path.getNodeCount(),"initialPathEnd",path.getEndNode().asBlockPos().toShortString()));
                check.put("road",List.of(road.getX(),road.getY(),road.getZ()));check.put("guards",residents.stream().map(g->Map.of("id",g.getUUID().toString(),"member",GuardPostAccess.getIdentity(g).memberIndex(),
                    "position",g.blockPosition().toShortString(),"mainHand",g.getMainHandItem().toString(),
                    "gunEquipped",com.tacz.guns.api.item.IGun.getIGunOrNull(g.getMainHandItem())!=null)).toList());
                check.put("walkingRoute",route.stream().map(BlockPos::toShortString).toList());
                check.put("dryStandingCells",dryCells);
                check.put("dutySlots",layout.slots().stream().map(Object::toString).toList());
                var slotIds=layout.slots().stream().map(VillageDefenseDutySlot::slotId).toList();
                check.put("observedAssignedGuards",Probe.level.getEntitiesOfClass(Guard.class,Probe.area).stream()
                    .filter(g->{var assignment=VillageDefenseStaffingRuntime.assignment(g);return assignment!=null&&slotIds.contains(assignment.slotId());}).count());
                checks.add(check);
            }
            require(!scenes.isEmpty(),"No selected regional Training Yards; an empty sample is not a pass");
            Probe.result.put("regionalTrainingYardNaturalChecks",checks);started=Probe.ticks;
            Probe.progress("walking from natural roads into generated Training Yards");
        }
        if(leg<10)for(var w:walks)require(w.guard.isAlive()&&!w.guard.isInWater()&&w.guard.getHealth()==w.guard.getMaxHealth(),"Unsafe natural Training Yard walking route "+w.scene.anchor()+" position="+w.guard.position()
            +" health="+w.guard.getHealth()+" water="+w.guard.isInWater()+" damage="
            +(w.guard.getLastDamageSource()==null?"none":w.guard.getLastDamageSource().getMsgId()));
        if(leg<10){
            if(walks.stream().allMatch(Walk::arrived)){
                leg++;started=Probe.ticks;
                if(leg<10)for(var w:walks)w.target=w.scene.route().get(leg);
                Probe.progress("natural Training Yard walking leg "+leg);
            }
            if(leg<10){
                require(Probe.ticks-started<700,"Natural Training Yard walk timed out: "+walks.stream().filter(w->!w.arrived()).map(w->w.scene.row().get("entry")+" "+w.guard.position()+" -> "+w.target).toList());
                return false;
            }
        }
        walks.forEach(w->w.guard.discard());
        var saved=Probe.level.getDataStorage().computeIfAbsent(GarrisonCheckpoint::load,GarrisonCheckpoint::new,"vgg_probe_training_yard_garrison");
        boolean restart=!saved.entries.isEmpty();List<Object> garrisons=new ArrayList<>();
        for(var scene:scenes){
            var id=scene.record().identity().buildingId().toString();var residentIds=scene.residents().stream().map(g->g.getUUID().toString()).sorted().toList();
            var layout=VillageDefenseDutySavedData.get(Probe.level).layout(scene.record().identity().buildingId());
            var checkpoint=new CompoundTag();checkpoint.putString("guards",residentIds.toString());
            checkpoint.putInt("actions",scene.record().claimedActions());
            checkpoint.putString("layout",layout.toString());
            if(restart){require(saved.entries.contains(id)&&saved.entries.getCompound(id).equals(checkpoint),"Garrison or duty identity changed after reload "+id);}
            else {saved.entries.put(id,checkpoint);saved.setDirty();}
            var before=residents(scene.record()).stream().map(Guard::getUUID).toList();
            require(before.stream().map(UUID::toString).sorted().toList().equals(residentIds),"Resident disappeared during walking trial "+id);
            var status=VillageBuildingGarrisonLifecycle.populate(Probe.level,scene.record().identity(),scene.rotation());
            require(status.status()==VillageBuildingGarrisonLifecycle.Status.ALREADY_CLAIMED&&status.spawnedGuards()==0
                &&before.equals(residents(scene.record()).stream().map(Guard::getUUID).toList()),"Repeated activation changed garrison "+id);
            garrisons.add(Map.of("entry",scene.row().get("entry").getAsString(),"buildingId",id,"guardIds",residentIds,
                "guards",before.size(),"repeatActivationNoChange",true,"reloadMatched",restart));
        }
        Probe.result.put("regionalTrainingYardMovement",Map.of("guards",walks.size(),"completedLegs",walks.size()*10,"fixture","scripted native navigation on naturally generated roads"));
        Probe.result.put("regionalTrainingYardGarrisons",Map.of("stage",restart?"reload_verified":"checkpoint_saved","posts",garrisons));
        return true;
    }
    private static List<Guard> residents(VillageBuildingRecord record){
        var found=new ArrayList<Guard>();
        for(var entity:Probe.level.getAllEntities())if(entity instanceof Guard guard&&guard.isAlive()){
            var identity=GuardPostAccess.getIdentity(guard);
            if(identity!=null&&identity.postId().equals(record.identity().buildingId()))found.add(guard);
        }
        found.sort(Comparator.comparing(Guard::getUUID));
        return List.copyOf(found);
    }
    private static final class GarrisonCheckpoint extends SavedData{
        CompoundTag entries=new CompoundTag();
        static GarrisonCheckpoint load(CompoundTag tag){var s=new GarrisonCheckpoint();s.entries=tag.getCompound("entries");return s;}
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
