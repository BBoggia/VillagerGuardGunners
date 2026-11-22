package com.gamergaming.villagerguardgunners.medical;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CotApproachRoutesTest {
    private static final BlockPos A = new BlockPos(2, 70, 0), B = new BlockPos(0, 70, 3);
    private static Path path(boolean reached, BlockPos... points) {
        return new Path(Arrays.stream(points).map(p -> new Node(p.getX(), p.getY(), p.getZ())).toList(), points[points.length-1], reached);
    }
    @Test void unreachableNearSideDoesNotHideReachableFarSide() {
        Path alternate = path(true, BlockPos.ZERO, B);
        assertSame(alternate, CotApproachRoutes.select(List.of(A,B), p -> p.equals(A) ? path(false,A) : alternate, p -> true));
    }
    @Test void blockedPassengerNodeFallsBack() {
        BlockPos lowDoor = new BlockPos(1,70,0);
        Path alternate = path(true, new BlockPos(0,70,0),B);
        assertSame(alternate, CotApproachRoutes.select(List.of(A,B), p -> p.equals(A) ? path(true,lowDoor,A) : alternate, p -> !p.equals(lowDoor)));
    }
    @Test void ranksTravelLengthInsteadOfEndpointDistance() {
        Path detour = path(true,new BlockPos(0,70,0),new BlockPos(10,70,0),A);
        Path direct = path(true,new BlockPos(0,70,0),B);
        assertSame(direct,CotApproachRoutes.select(List.of(A,B),p -> p.equals(A) ? detour : direct,p -> true));
    }
    @Test void rejectsWrongEndpointAndOversizedRoutes() {
        assertNull(CotApproachRoutes.select(List.of(A),p -> path(true,B),p -> true));
        var nodes = new ArrayList<Node>(); for(int i=0;i<129;i++) nodes.add(new Node(A.getX(),A.getY(),A.getZ()));
        assertNull(CotApproachRoutes.select(List.of(A),p -> new Path(nodes,A,true),p -> true));
    }
    @Test void exceptionOnOneApproachAllowsAlternative() {
        Path alternate=path(true,B);
        assertSame(alternate,CotApproachRoutes.select(List.of(A,B),p -> {if(p.equals(A))throw new IllegalStateException();return alternate;},p -> true));
    }
    @Test void rechecksSupportAndEnforcesSearchBound() {
        Path route=path(true,A);
        assertNull(CotApproachRoutes.select(List.of(A),p -> route,p -> false));
        assertThrows(IllegalArgumentException.class,()->CotApproachRoutes.select(List.of(A,A,A,A,A),p -> route,p -> true));
    }
    @Test void ignoresCompletedPrefixButScreensRemainingNodes() {
        var start=new BlockPos(0,70,0);
        Path cached=path(true,start,new BlockPos(1,70,0),A);
        cached.setNextNodeIndex(1);
        assertSame(cached,CotApproachRoutes.select(List.of(A),p->cached,p->!p.equals(start)));
        assertNull(CotApproachRoutes.select(List.of(A),p->cached,p->!p.equals(A)));
    }
    @Test void comparesRemainingCostOfAdvancedPath() {
        Path cached=path(true,new BlockPos(100,70,0),new BlockPos(1,70,0),A);
        cached.setNextNodeIndex(1);
        Path fresh=path(true,new BlockPos(1,70,0),B);
        assertSame(cached,CotApproachRoutes.select(List.of(A,B),p->p.equals(A)?cached:fresh,p->true,
                new net.minecraft.world.phys.Vec3(1.5,70,.5)));
    }

}
