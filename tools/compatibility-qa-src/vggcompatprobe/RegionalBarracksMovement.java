package vggcompatprobe;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Controlled four-guard traffic trials; no teleporting after the initial spawn. */
final class RegionalBarracksMovement {
    private record Scene(JsonObject row, BlockPos origin) { }
    private static final List<Scene> scenes = new ArrayList<>();
    private static final List<Walk> walks = new ArrayList<>();
    private static int started = -1, leg;
    static void register(JsonObject row, BlockPos origin) {
        if (!row.has("integration")) return;
        scenes.add(new Scene(row, origin));
        int x = row.getAsJsonArray("connector").get(0).getAsInt();
        int f = row.get("floorY").getAsInt();
        boolean ocean = row.get("id").getAsString().startsWith("ocean-");
        int support = f == 1 ? 0 : f - 2;
        for (int dx=-1; dx<=2; dx++) for (int z=-5; z<0; z++) {
            var pos = origin.offset(x+dx,support,z);
            Probe.level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
            if(ocean) Probe.level.setBlock(pos.above(), Blocks.CAMPFIRE.defaultBlockState()
                    .setValue(BlockStateProperties.LIT,false),3);
        }
    }
    static boolean tick() {
        if(scenes.isEmpty()) return true;
        if(started == -1) {
            for(var scene:scenes) {
                var row=scene.row();var o=scene.origin();int f=row.get("floorY").getAsInt();
                int x=row.getAsJsonArray("connector").get(0).getAsInt();
                for(int i=0;i<4;i++) {
                    var home=o.offset(x+i%2,f==1?1:f-1,-3-i/2);
                    var destination=o.offset(RegionalBarracksPreview.pos(row.getAsJsonArray("guards").get(i).getAsJsonArray()));
                    Guard guard=GuardEntityType.GUARD.get().create(Probe.level);
                    guard.setPersistenceRequired();
                    guard.setPos(home.getX()+.5,home.getY()+(row.get("id").getAsString().startsWith("ocean-")?.4375:0),home.getZ()+.5);
                    var nav=(GroundPathNavigation)guard.getNavigation();nav.setCanOpenDoors(true);nav.setCanPassDoors(true);
                    var walk=new Walk(guard,home,destination,row.get("id").getAsString());
                    guard.goalSelector.addGoal(0,walk);guard.goalSelector.addGoal(1,new OpenDoorGoal(guard,true));
                    Probe.level.addFreshEntity(guard);walks.add(walk);
                }
            }
            started=Probe.ticks;Probe.progress("walking four guards into each regional Barracks");
        }
        if(walks.stream().allMatch(Walk::arrived)) {
            if(leg==0) {
                for(var walk:walks)walk.target=walk.home;
                leg=1;started=Probe.ticks;Probe.progress("walking four guards back onto each approach");
            } else {
                Probe.result.put("regionalGroupMovement",Map.of("layouts",scenes.size(),"guards",walks.size(),"completedLegs",walks.size()*2,"fixture","scripted goals with native navigation, doors and entity collision"));
                walks.forEach(w->w.guard.discard());return true;
            }
        }
        if(Probe.ticks-started>600)throw new AssertionError("Regional group movement timed out on leg "+leg+": "+walks.stream().filter(w->!w.arrived()).map(w->w.layout+" "+w.guard.position()+" -> "+w.target).toList());
        return false;
    }
    private static final class Walk extends Goal {
        final Guard guard;final BlockPos home;final String layout;BlockPos target;
        Walk(Guard guard,BlockPos home,BlockPos target,String layout) {
            this.guard=guard;this.home=home;this.target=target;this.layout=layout;
            setFlags(EnumSet.of(Flag.MOVE));
        }
        boolean arrived(){return guard.isAlive()&&Math.abs(guard.getY()-target.getY())<.8&&guard.position().distanceToSqr(target.getCenter().add(0,-.5,0))<.75;}
        public boolean canUse(){return true;}
        public boolean requiresUpdateEveryTick(){return true;}
        public void tick(){if(arrived())guard.getNavigation().stop();else if(guard.tickCount%10==0)guard.getNavigation().moveTo(guard.getNavigation().createPath(target,0),1.0);}
    }
}
