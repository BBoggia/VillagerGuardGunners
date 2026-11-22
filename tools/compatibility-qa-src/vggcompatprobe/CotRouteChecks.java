package vggcompatprobe;

import com.gamergaming.villagerguardgunners.medical.CotApproachRoutes;
import com.gamergaming.villagerguardgunners.medical.TransportRuntime;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Artificial restored fixture; does not claim a full patient evacuation. */
public final class CotRouteChecks {
    public static Map<String,Object> run(ServerLevel level, BlockPos origin) {
        BlockPos center=new BlockPos(origin.getX()+8,level.getMaxBuildHeight()-12,origin.getZ()+8);
        Map<BlockPos,BlockState> old=new LinkedHashMap<>();
        for(BlockPos p:BlockPos.betweenClosed(center.offset(-7,-1,-5),center.offset(7,4,5))) {
            require(level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null,"Fixture chunk missing");
            require(level.getBlockState(p).isAir(),"Cot fixture would overwrite terrain");
            old.put(p.immutable(),level.getBlockState(p));
        }
        var guard=tallestegg.guardvillagers.GuardEntityType.GUARD.get().create(level);
        require(guard!=null,"Guard creation failed");
        try {
            for(BlockPos p:old.keySet()) if(p.getY()==center.getY()-1) level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
            guard.moveTo(center.getX()+.5,center.getY(),center.getZ()+.5);
            guard.setOnGround(true);
            BlockPos near=center.east(3), far=center.west(4);
            level.setBlock(near.above(2),Blocks.STONE.defaultBlockState(),2);
            require(standing(guard,near,false),"Ordinary guard clearance rejected");
            require(!standing(guard,near,true),"Low ceiling admitted passenger");
            Map<BlockPos,String> paths=new LinkedHashMap<>();
            var route=CotApproachRoutes.select(List.of(near,far),p->{
                var nativePath=guard.getNavigation().createPath(p,0);
                paths.put(p,nativePath==null?"null":nativePath.toString()+" reach="+nativePath.canReach()+" end="+nativePath.getEndNode()+" nodes="+nativePath.getNodeCount());
                return nativePath;
            },p->standing(guard,p,true));
            require(route!=null && route.getEndNode().asBlockPos().equals(far),"Alternate native route was not selected: "+paths);
            level.setBlock(far.below(),Blocks.AIR.defaultBlockState(),2);
            require(!standing(guard,far,true),"Destroyed endpoint support admitted");
            level.setBlock(far.below(),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(near.above(2),Blocks.AIR.defaultBlockState(),2);
            var selected=CotApproachRoutes.prepare(guard.getNavigation(),guard.position(),List.of(near,far),p->standing(guard,p,true));
            require(selected!=null && selected.getEndNode().asBlockPos().equals(near),"Shorter first approach not selected");
            require(guard.getNavigation().moveTo(selected,.45),"Selected path installation failed");
            require(guard.getNavigation().getTargetPos().equals(near),"Native target differs from selected path");
            require(guard.getNavigation().getPath().getEndNode().asBlockPos().equals(near),"Native path differs from selected target");
            checkRecomputationInterception(level,guard,near);
            return Map.of("kind","artificial_native_route_in_production","alternateNativeRoute",true,"passengerClearance",true,"removedSupport",true,"selectedTargetRetained",true,"carrierReplanScreened",true);
        } finally { guard.discard();old.forEach((p,s)->level.setBlock(p,s,2)); }
    }
    @SuppressWarnings("unchecked")
    private static void checkRecomputationInterception(ServerLevel level,tallestegg.guardvillagers.entities.Guard guard,BlockPos cot) {
        // Install only a transient synthetic carrier lease; no real patient is moved or treated.
        try {
            var levelsField=TransportRuntime.class.getDeclaredField("LEVELS");levelsField.setAccessible(true);
            var levels=(Map<ServerLevel,Object>)levelsField.get(null);
            Object state=levels.get(level);boolean created=state==null;
            if(created) {
                var constructor=Class.forName(TransportRuntime.class.getName()+"$State").getDeclaredConstructor();constructor.setAccessible(true);
                state=constructor.newInstance();levels.put(level,state);
            }
            var actorsField=state.getClass().getDeclaredField("actors");actorsField.setAccessible(true);
            var actors=(Map<java.util.UUID,Object>)actorsField.get(state);
            var constructor=TransportRuntime.Lease.class.getDeclaredConstructor(tallestegg.guardvillagers.entities.Guard.class,tallestegg.guardvillagers.entities.Guard.class,BlockPos.class);
            constructor.setAccessible(true);
            actors.put(guard.getUUID(),constructor.newInstance(guard,guard,cot));
            try {
                guard.getNavigation().recomputePath();
                require(guard.getNavigation().getPath()==null,"Carrier native replan bypassed transport screening");
            } finally { actors.remove(guard.getUUID());if(created)levels.remove(level); }
        } catch(ReflectiveOperationException exception) {throw new AssertionError(exception);}
    }
    private static boolean standing(tallestegg.guardvillagers.entities.Guard guard,BlockPos pos,boolean carrying) {
        try {
            var method=TransportRuntime.class.getDeclaredMethod(carrying?"safeCarryStanding":"safeStanding",tallestegg.guardvillagers.entities.Guard.class,carrying?BlockPos.class:Vec3.class);
            method.setAccessible(true);
            return (boolean)method.invoke(null,guard,carrying?pos:Vec3.atBottomCenterOf(pos));
        } catch(ReflectiveOperationException exception) {throw new AssertionError(exception);}
    }
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
