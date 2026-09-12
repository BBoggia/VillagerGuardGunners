package com.gamergaming.villagerguardgunners.ai.tactics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TacticalRoutePolicyTest {
    @Test void assignmentIncludesBoundaryButRejectsOutsideSamples() {
        var bounds = new TacticalAssignmentBounds(new Vec3(.5, 1, .5), 4);
        assertTrue(bounds.contains(new Vec3(4.5, 1, .5)));
        assertFalse(bounds.contains(new Vec3(4.51, 1, .5)));
    }
    @Test void necessaryDetourCanMakeRouteProgressWhileEndpointGetsFarther() {
        Path path = new Path(List.of(new Node(0, 1, 0), new Node(0, 1, 3), new Node(3, 1, 3), new Node(3, 1, 0)), new BlockPos(3, 1, 0), true);
        Vec3 start = new Vec3(.5, 1, .5), detour = new Vec3(.5, 1, 3.5), endpoint = new Vec3(3.5, 1, .5);
        assertTrue(detour.distanceTo(endpoint) > start.distanceTo(endpoint));
        assertTrue(TacticalNavigationController.remainingDistance(path, 1, detour)
                < TacticalNavigationController.remainingDistance(path, 0, start));
    }
    @Test void knockbackCannotImmediatelyRetryTheSameBlockedArea() {
        var failed = java.util.Set.of(new BlockPos(10, 1, 10));
        assertTrue(TacticalPositionPlanner.nearFailedDestination(new BlockPos(11, 2, 10), failed));
        assertTrue(TacticalPositionPlanner.nearFailedDestination(new BlockPos(13, 1, 10), failed));
        assertFalse(TacticalPositionPlanner.nearFailedDestination(new BlockPos(14, 1, 10), failed));
        assertFalse(TacticalPositionPlanner.nearFailedDestination(new BlockPos(10, 4, 10), failed));
        assertFalse(TacticalPositionPlanner.nearFailedDestination(new BlockPos(10, 1, 10), java.util.Set.of()));
    }
}
