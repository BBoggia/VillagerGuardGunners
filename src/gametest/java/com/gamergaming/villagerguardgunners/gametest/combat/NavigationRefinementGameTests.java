package com.gamergaming.villagerguardgunners.gametest.combat;

import com.gamergaming.villagerguardgunners.ai.navigation.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class NavigationRefinementGameTests {
    private static final String ARENA = "villagerguardgunners/gametest_arena";
    @GameTest(batch = "navigation_door", templateNamespace = "villagerguardgunners_navigation", template = ARENA, timeoutTicks = 220)
    public static void localRouteOpensWoodenDoorAndWalksThroughIt(GameTestHelper h) {
        floor(h);
        for (int z = 2; z <= 22; z++) for (int y = 1; y <= 3; y++) setBlock(h, new BlockPos(10, y, z), Blocks.STONE);
        BlockPos door = new BlockPos(10, 1, 12);
        var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        setBlock(h, door, state); setBlock(h, door.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        Guard g = guard(h, new BlockPos(5, 1, 12));
        BlockPos target = absolute(h, new BlockPos(15, 1, 12));
        Path path = GuardLocalRoutes.search(g, target, 20);
        h.assertTrue(path != null, "No conditional wooden-door route: " + GuardLocalRoutes.status(g));
        h.assertFalse(h.getBlockState(relative(h, door)).getValue(DoorBlock.OPEN), "Planning opened the door");
        var progress = install(h, g, path);
        h.runAtTickTime(210, g::discard);
        h.onEachTick(() -> {
            if (g.isRemoved()) return;
            if (GuardRouteArrival.at(g, target, 1, 1)) {
                h.assertTrue(h.getBlockState(relative(h, door)).getValue(DoorBlock.OPEN), "Guard never opened the crossed door");
                g.discard(); h.succeed(); return;
            }
            h.assertFalse(progress.stalled(g, path, h.getLevel().getGameTime(), 40), "Door route stalled at " + g.position());
        });
    }
    @GameTest(batch = "navigation_detour", templateNamespace = "villagerguardgunners_navigation", template = ARENA, timeoutTicks = 320)
    public static void sameLevelDetourWalksAwayFromGoalWithoutFalseStall(GameTestHelper h) {
        floor(h);
        for (int z = 2; z <= 18; z++) for (int y = 1; y <= 3; y++) setBlock(h, new BlockPos(10, y, z), Blocks.STONE);
        Guard g = guard(h, new BlockPos(7, 1, 12));
        BlockPos target = absolute(h, new BlockPos(13, 1, 12));
        Path path = GuardLocalRoutes.search(g, target, 24);
        h.assertTrue(path != null && path.getNodeCount() > 16, "Same-level detour was not discovered: " + GuardLocalRoutes.status(g));
        var progress = install(h, g, path);
        double[] furthest = {0};
        h.runAtTickTime(310, g::discard);
        h.onEachTick(() -> {
            if (g.isRemoved()) return;
            furthest[0] = Math.max(furthest[0], g.distanceToSqr(Vec3.atBottomCenterOf(target)));
            if (GuardRouteArrival.at(g, target, 1, 1)) {
                h.assertTrue(furthest[0] > 50, "Fixture did not exercise travel away from its goal");
                g.discard(); h.succeed(); return;
            }
            h.assertFalse(progress.stalled(g, path, h.getLevel().getGameTime(), 40), "Useful detour counted as stalled: " + g.position() + ", runtime=" + GuardNavigationRuntime.status(g)
                    + ", owns=" + (g.getNavigation().getPath() == path) + ", node=" + path.getNextNodeIndex());
        });
    }
    @GameTest(batch = "navigation_bridge", templateNamespace = "villagerguardgunners_navigation", template = ARENA, timeoutTicks = 360)
    public static void opposingGuardsWaitAndCrossNarrowBridgeWithoutFalling(GameTestHelper h) {
        opposingBridge(h, false);
    }
    @GameTest(batch = "navigation_slab_bridge", templateNamespace = "villagerguardgunners_navigation", template = ARENA, timeoutTicks = 360)
    public static void opposingGuardsKeepPassageAdmissionOnBottomSlabBridge(GameTestHelper h) {
        opposingBridge(h, true);
    }
    private static void opposingBridge(GameTestHelper h, boolean slabs) {
        floor(h);
        if (slabs) for (int x = 2; x <= 30; x++) for (int z = 2; z <= 22; z++)
            setBlock(h, new BlockPos(x, 0, z), Blocks.STONE_SLAB);
        for (int x = 10; x <= 17; x++) for (int z = 2; z <= 22; z++) if (z != 12)
            setBlock(h, new BlockPos(x, 0, z), Blocks.AIR);
        Guard a = guard(h, new BlockPos(9, 1, 12)), b = guard(h, new BlockPos(18, 1, 12));
        if (slabs) { a.setPos(a.position().add(0, -.5, 0)); b.setPos(b.position().add(0, -.5, 0)); }
        BlockPos endA = absolute(h, new BlockPos(22, 1, 12)), endB = absolute(h, new BlockPos(6, 1, 12));
        Path pa = GuardLocalRoutes.search(a, endA, 24), pb = GuardLocalRoutes.search(b, endB, 24);
        install(h, a, pa); install(h, b, pb);
        boolean[] waited = {false}, arrived = {false, false};
        h.runAtTickTime(340, () -> {
            if (!a.isRemoved()) {
                String state = "a=" + a.position() + "/" + GuardNavigationRuntime.status(a) + "/" + a.getNavigation().isDone()
                        + ", b=" + b.position() + "/" + GuardNavigationRuntime.status(b) + "/" + b.getNavigation().isDone()
                        + ", arrived=" + Arrays.toString(arrived) + ", waited=" + waited[0];
                a.discard(); b.discard(); h.fail("Bridge crossing did not complete: " + state);
            }
        });
        h.onEachTick(() -> {
            if (a.isRemoved() || b.isRemoved()) return;
            waited[0] |= GuardNavigationRuntime.waiting(a) || GuardNavigationRuntime.waiting(b);
            h.assertTrue(a.getY() >= endA.getY() - (slabs ? .7 : .2) && b.getY() >= endB.getY() - (slabs ? .7 : .2), "Guard was pushed off its bridge");
            arrived[0] |= GuardRouteArrival.at(a, endA, 1, 1); arrived[1] |= GuardRouteArrival.at(b, endB, 1, 1);
            if (arrived[0] && arrived[1]) {
                h.assertTrue(waited[0], "Opposing traffic never used passage admission");
                a.discard(); b.discard(); h.succeed();
            }
        });
    }
    @GameTest(batch = "navigation_search_status", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void invalidLoadedRoutesDoNotDeferAsUnknownTerrain(GameTestHelper h) {
        floor(h);
        Guard g = guard(h, new BlockPos(5, 1, 12));
        try {
            h.assertTrue(GuardLocalRoutes.search(g, absolute(h, new BlockPos(20, 1, 12)), 5) == null, "Out-of-range route was accepted");
            h.assertFalse(GuardLocalRoutes.deferred(g), "Loaded out-of-range target deferred forever");
            g.setPos(g.position().add(0, 3, 0));
            h.assertTrue(GuardLocalRoutes.search(g, absolute(h, new BlockPos(10, 1, 12)), 20) == null, "Unsupported starting position was accepted");
            h.assertFalse(GuardLocalRoutes.deferred(g), "Unsupported loaded start counted as unknown terrain");
            h.assertTrue(GuardLocalRoutes.search(g, absolute(h, new BlockPos(10, 1, 12)), 0) == null, "Invalid search radius accepted");
            h.assertTrue(GuardLocalRoutes.status(g).equals("INVALID_SEARCH"), "Invalid search retained stale diagnostics");
        } finally { g.discard(); }
        h.succeed();
    }
    @GameTest(batch = "navigation_door_start", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void guardInsideClosingWoodenDoorCanPlanAnExitWithoutOpeningIt(GameTestHelper h) {
        floor(h);
        BlockPos door = new BlockPos(10, 1, 12);
        var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        setBlock(h, door, state); setBlock(h, door.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        Guard g = guard(h, door);
        try {
            h.assertTrue(GuardLocalRoutes.search(g, absolute(h, door.east(4)), 12) != null,
                    "Capable guard could not plan an exit from its closing door: " + GuardLocalRoutes.status(g));
            h.assertFalse(h.getBlockState(relative(h, door)).getValue(DoorBlock.OPEN), "Planning mutated the door");
            ((net.minecraft.world.entity.ai.navigation.GroundPathNavigation) g.getNavigation()).setCanOpenDoors(false);
            h.assertTrue(GuardLocalRoutes.search(g, absolute(h, door.east(4)), 12) == null, "Incapable guard planned through a closed door");
        } finally { g.discard(); }
        h.succeed();
    }
    @GameTest(batch = "navigation_progress_ownership", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void oldProgressTrackerCannotStealReplacementRouteOrDiagnostics(GameTestHelper h) {
        floor(h);
        Guard g = guard(h, new BlockPos(5, 1, 12));
        try {
            Path old = GuardLocalRoutes.search(g, absolute(h, new BlockPos(15, 1, 12)), 20);
            var oldProgress = install(h, g, old);
            Path replacement = GuardLocalRoutes.search(g, absolute(h, new BlockPos(5, 1, 20)), 20);
            var current = install(h, g, replacement);
            h.assertFalse(current.stalled(g, replacement, h.getLevel().getGameTime(), 40), "New route stalled immediately");
            h.assertTrue(oldProgress.stalled(g, old, h.getLevel().getGameTime(), 40), "Detached old route still reported active progress");
            h.assertTrue(GuardNavigationRuntime.owns(g), "Old progress tracker released the replacement route");
            h.assertFalse(GuardRouteProgress.snapshot(g).status().equals("IDLE"), "Old tracker erased replacement diagnostics");
            oldProgress.start(g, old, h.getLevel().getGameTime());
            h.assertTrue(GuardNavigationRuntime.owns(g), "Detached start stole runtime ownership");
        } finally { GuardNavigationRuntime.release(g); g.discard(); }
        h.succeed();
    }
    @GameTest(batch = "navigation_native_cache", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void nativeCacheCannotReturnAnInstalledLocalRouteToTheWrongDestination(GameTestHelper h) {
        floor(h);
        Guard g = guard(h, new BlockPos(12, 1, 12));
        try {
            BlockPos a = absolute(h, new BlockPos(16, 1, 12)), b = absolute(h, new BlockPos(12, 1, 18));
            // Explicit fixture preparation; production route construction remains cache-only.
            BlockPos origin = g.blockPosition();
            for (int x = (origin.getX() - 20) >> 4; x <= (origin.getX() + 20) >> 4; x++)
                for (int z = (origin.getZ() - 20) >> 4; z <= (origin.getZ() + 20) >> 4; z++) h.getLevel().getChunk(x, z);
            h.assertTrue(GuardLocalRoutes.nativeRegionLoaded(g, 12), "Fixture lacks its native search region");
            Path nativeA = g.getNavigation().createPath(a, 0, 12);
            install(h, g, nativeA);
            Path localB = GuardLocalRoutes.search(g, b, 12);
            install(h, g, localB);
            h.assertTrue(a.equals(g.getNavigation().getTargetPos()) && g.getNavigation().getPath() == localB,
                    "Fixture did not retain the native cache target after local path installation");
            h.assertTrue(g.getNavigation().createPath(a, 0, 12) == localB, "Fixture no longer reproduces the native cache behavior");
            Path requestedA = GuardLocalRoutes.create(g, a, 12);
            h.assertTrue(requestedA != null && requestedA.canReach() && a.equals(requestedA.getTarget())
                    && a.equals(requestedA.getEndNode().asBlockPos()), "Planner returned the old local route for a different destination");
            h.assertTrue(g.getNavigation().getPath() == localB, "Planning replaced an installed route before its owner accepted the result");
        } finally { GuardNavigationRuntime.release(g); g.discard(); }
        h.succeed();
    }
    @GameTest(batch = "navigation_staging_safety", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void displacedWaiterCannotCrossNewGapToReachItsSafeStagingEndpoint(GameTestHelper h) {
        Guard[] pair = stagingPair(h);
        Guard waiter = pair[1];
        try {
            var control = waiter.getMoveControl();
            Vec3 stage = new Vec3(control.getWantedX(), control.getWantedY(), control.getWantedZ());
            Vec3 direction = stage.subtract(waiter.position()).normalize();
            waiter.setPos(stage.add(direction.scale(2)));
            BlockPos gap = BlockPos.containing(stage.add(direction)).below();
            // Model a modded terrain change without a native recomputation notification.
            h.getLevel().getChunkAt(gap).setBlockState(gap, Blocks.AIR.defaultBlockState(), false);
            h.assertTrue(GuardGroundTraversal.safeStanding(waiter, stage), "Fixture lost its safe staging endpoint");
            h.assertTrue(GuardGroundTraversal.safeStanding(waiter, waiter.position()), "Fixture displaced guard onto unsupported ground");
            GuardNavigationRuntime.beforeTick(waiter);
            h.assertTrue(waiter.getNavigation().isDone() && GuardNavigationRuntime.status(waiter).equals("STAGING_CHANGED"),
                    "Waiter attempted to cross the gap to its old endpoint: " + GuardNavigationRuntime.status(waiter));
        } finally { discardPair(pair); }
        h.succeed();
    }
    @GameTest(batch = "navigation_staging_orders", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void holdOrderCancelsAnAlreadyStartedSidestep(GameTestHelper h) {
        Guard[] pair = stagingPair(h);
        Guard waiter = pair[1];
        try {
            com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(waiter,
                    com.gamergaming.villagerguardgunners.orders.GuardOrders.DEFAULT.withMovement(
                            com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder.HOLD_POSITION));
            GuardNavigationRuntime.beforeTick(waiter);
            h.assertTrue(waiter.getNavigation().isDone() && waiter.getMoveControl().getSpeedModifier() == 0,
                    "Guard continued its sidestep after a hold order");
        } finally { discardPair(pair); }
        h.succeed();
    }
    @GameTest(batch = "navigation_staging_interruption", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void releasedSidestepStopsItsOwnMoveControlButPreservesReplacementCommand(GameTestHelper h) {
        Guard[] pair = stagingPair(h);
        Guard waiter = pair[1];
        try {
            GuardNavigationRuntime.release(waiter);
            h.assertTrue(waiter.getMoveControl().getSpeedModifier() == 0, "Cancelled sidestep left a live MoveControl command");
            GuardNavigationRuntime.register(waiter, waiter.getNavigation().getPath());
            GuardNavigationRuntime.beforeTick(waiter);
            h.assertTrue(waiter.getMoveControl().getSpeedModifier() > 0, "Fixture failed to resume staging");
            Vec3 replacement = waiter.position().add(0, 0, -2);
            waiter.getMoveControl().setWantedPosition(replacement.x, replacement.y, replacement.z, .9);
            GuardNavigationRuntime.release(waiter);
            h.assertTrue(waiter.getMoveControl().getSpeedModifier() == .9 && waiter.getMoveControl().getWantedZ() == replacement.z,
                    "Old session cleared the replacement goal's movement command");
        } finally { discardPair(pair); }
        h.succeed();
    }
    @GameTest(batch = "navigation_rejoin_timeout", templateNamespace = "villagerguardgunners_navigation", template = ARENA, timeoutTicks = 140)
    public static void stationaryRejoinExpiresEvenWithoutAControllerProgressTracker(GameTestHelper h) {
        Guard[] pair = stagingPair(h);
        Guard waiter = pair[1];
        var control = waiter.getMoveControl();
        waiter.setPos(control.getWantedX(), control.getWantedY(), control.getWantedZ());
        waiter.setNoAi(true);
        GuardNavigationRuntime.release(pair[0]); pair[0].discard();
        GuardNavigationRuntime.beforeTick(waiter);
        h.assertTrue(GuardNavigationRuntime.status(waiter).equals("REJOINING_ROUTE"), "Fixture did not start rejoining");
        h.onEachTick(() -> { if (!waiter.isRemoved()) GuardNavigationRuntime.beforeTick(waiter); });
        h.runAtTickTime(110, () -> {
            try {
                h.assertTrue(waiter.getNavigation().isDone() && GuardNavigationRuntime.status(waiter).equals("PASSAGE_TIMEOUT"),
                        "Stationary rejoin outlived its transition deadline: " + GuardNavigationRuntime.status(waiter));
                h.succeed();
            } finally { discardPair(pair); }
        });
    }
    @GameTest(batch = "navigation_wait_clock", templateNamespace = "villagerguardgunners_navigation", template = ARENA, timeoutTicks = 100)
    public static void passageWaitDoesNotConsumeNativeWaypointTimeout(GameTestHelper h) {
        Guard[] pair = stagingPair(h);
        Guard waiter = pair[1];
        for (Guard g : pair) g.setNoAi(true);
        var navigation = waiter.getNavigation();
        Path path = navigation.getPath();
        path.setNextNodeIndex(1);
        NavigationTimeoutGameTestAccess.prime(navigation, path.getNextNodePos(), 7, 10, h.getLevel().getGameTime());
        h.onEachTick(() -> {
            if (waiter.isRemoved()) return;
            GuardNavigationRuntime.beforeTick(pair[0]);
            navigation.tick();
        });
        h.runAtTickTime(70, () -> {
            try {
                h.assertTrue(GuardNavigationRuntime.waiting(waiter), "Fixture did not maintain passage contention");
                GuardNavigationRuntime.release(pair[0]); pair[0].discard();
                navigation.tick();
                h.assertFalse(navigation.isDone(), "Legal passage wait exhausted the native waypoint timeout on resume");
                long elapsed = NavigationTimeoutGameTestAccess.elapsed(navigation);
                h.assertTrue(elapsed >= 7 && elapsed <= 9, "Waiting changed the accrued active timeout: " + elapsed);
                // Active movement must still time out if the guard actually gets stuck.
                NavigationTimeoutGameTestAccess.prime(navigation, path.getNextNodePos(), elapsed, 10, h.getLevel().getGameTime() - 40);
                navigation.tick();
                h.assertTrue(navigation.isDone(), "Native timeout stopped detecting real active stalls");
                h.succeed();
            } finally { discardPair(pair); }
        });
    }
    @GameTest(batch = "navigation_new_route_clock", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void newLocalRouteDoesNotInheritPreviousWaypointTimeout(GameTestHelper h) {
        floor(h);
        Guard g = guard(h, new BlockPos(5, 1, 12));
        try {
            var navigation = g.getNavigation();
            Path old = GuardLocalRoutes.search(g, absolute(h, new BlockPos(15, 1, 12)), 20);
            install(h, g, old);
            NavigationTimeoutGameTestAccess.prime(navigation, old.getNodePos(1), 20, 1, h.getLevel().getGameTime() - 1);
            Path replacement = GuardLocalRoutes.search(g, absolute(h, new BlockPos(16, 1, 12)), 20);
            install(h, g, replacement);
            h.assertTrue(replacement.getNodePos(1).equals(old.getNodePos(1)), "Fixture does not share its first waypoint");
            navigation.tick();
            h.assertFalse(navigation.isDone(), "New local route inherited an expired waypoint clock");
            h.assertTrue(NavigationTimeoutGameTestAccess.elapsed(navigation) <= 1, "New route retained old active timeout");
        } finally { GuardNavigationRuntime.release(g); g.discard(); }
        h.succeed();
    }
    @GameTest(batch = "navigation_repeated_registration", templateNamespace = "villagerguardgunners_navigation", template = ARENA, timeoutTicks = 130)
    public static void registeringRetainedPathPreservesPassageDeadline(GameTestHelper h) {
        Guard[] pair = stagingPair(h);
        Guard waiter = pair[1];
        for (Guard g : pair) g.setNoAi(true);
        Path retained = waiter.getNavigation().getPath();
        h.onEachTick(() -> {
            if (waiter.isRemoved()) return;
            GuardNavigationRuntime.beforeTick(pair[0]);
            // Bounded combat approaches can reinstall an equivalent native path every twenty ticks.
            if (h.getLevel().getGameTime() % 20 == 0) GuardNavigationRuntime.register(waiter, retained);
            GuardNavigationRuntime.beforeTick(waiter);
        });
        h.runAtTickTime(110, () -> {
            try {
                h.assertTrue(waiter.getNavigation().isDone() && GuardNavigationRuntime.status(waiter).equals("PASSAGE_TIMEOUT"),
                        "Repeated registration reset the passage deadline: " + GuardNavigationRuntime.status(waiter));
                h.succeed();
            } finally { discardPair(pair); }
        });
    }
    @GameTest(batch = "navigation_retained_path_ownership", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void oldTrackerCannotReleaseNewOwnerOfSameRetainedPath(GameTestHelper h) {
        floor(h);
        Guard g = guard(h, new BlockPos(5, 1, 12));
        try {
            Path path = GuardLocalRoutes.search(g, absolute(h, new BlockPos(15, 1, 12)), 20);
            var old = install(h, g, path);
            var current = new GuardRouteProgress();
            current.start(g, path, h.getLevel().getGameTime());
            h.assertFalse(current.stalled(g, path, h.getLevel().getGameTime(), 40), "Replacement owner stalled immediately");
            h.assertTrue(old.stalled(g, path, h.getLevel().getGameTime(), 40), "Old tracker continued to own the retained path");
            old.clear();
            h.assertTrue(old.stalled(g, path, h.getLevel().getGameTime(), 40), "A later stale sample reacquired the retained path");
            h.assertFalse(old.owns(g, path), "Old tracker still permits stopping the replacement owner's path");
            h.assertTrue(GuardNavigationRuntime.owns(g), "Old tracker released the new owner of the retained path");
            h.assertFalse(GuardRouteProgress.snapshot(g).status().equals("IDLE"), "Old tracker erased the new owner's diagnostics");
        } finally { GuardNavigationRuntime.release(g); g.discard(); }
        h.succeed();
    }
    @GameTest(batch = "navigation_controller_handoff", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void staleControllerCleanupPreservesReplacementOwnerOfRetainedPath(GameTestHelper h) throws Exception {
        floor(h);
        Guard g = guard(h, new BlockPos(5, 1, 12));
        try {
            Path path = GuardLocalRoutes.search(g, absolute(h, new BlockPos(15, 1, 12)), 20);
            install(h, g, path);
            var tactical = new com.gamergaming.villagerguardgunners.ai.tactics.TacticalNavigationController(g);
            // Reproduce a goal handoff after native moveTo retains an equivalent path.
            var pathField = tactical.getClass().getDeclaredField("path"); pathField.setAccessible(true); pathField.set(tactical, path);
            var progressField = tactical.getClass().getDeclaredField("routeProgress"); progressField.setAccessible(true);
            ((GuardRouteProgress) progressField.get(tactical)).start(g, path, h.getLevel().getGameTime());
            var replacement = new GuardRouteProgress(); replacement.start(g, path, h.getLevel().getGameTime());
            h.assertFalse(tactical.ownsPath(), "Old tactical controller still claims the handed-off path");
            tactical.cancel();
            h.assertTrue(g.getNavigation().getPath() == path && !path.isDone(), "Old tactical cleanup stopped replacement movement");

            var combat = new com.gamergaming.villagerguardgunners.ai.SquadCommandMovementController(g);
            var combatPath = combat.getClass().getDeclaredField("screenedCombatPath"); combatPath.setAccessible(true); combatPath.set(combat, path);
            var combatOwner = combat.getClass().getDeclaredField("screenedPathOwner"); combatOwner.setAccessible(true);
            GuardNavigationRuntime.register(g, path, combatOwner.get(combat));
            h.assertTrue(combat.ownsScreenedCombatPath(), "Fixture did not register the combat approach");
            replacement.start(g, path, h.getLevel().getGameTime());
            h.assertFalse(combat.ownsScreenedCombatPath(), "Old combat controller still claims the handed-off path");
            combat.stop();
            h.assertTrue(g.getNavigation().getPath() == path && !path.isDone(), "Old combat cleanup stopped replacement movement");
            h.assertFalse(replacement.stalled(g, path, h.getLevel().getGameTime(), 40), "Cleanup released the replacement session");
        } finally { GuardNavigationRuntime.release(g); g.discard(); }
        h.succeed();
    }
    @GameTest(batch = "navigation_repeated_progress", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void restartingProgressOnRetainedPathCannotResetItsStallClock(GameTestHelper h) {
        floor(h);
        Guard g = guard(h, new BlockPos(5, 1, 12));
        try {
            Path path = GuardLocalRoutes.search(g, absolute(h, new BlockPos(15, 1, 12)), 20);
            var progress = install(h, g, path);
            long now = h.getLevel().getGameTime();
            progress.start(g, path, now + 39);
            h.assertTrue(progress.stalled(g, path, now + 40, 40), "Repeated start renewed a stationary route's progress clock");
        } finally { GuardNavigationRuntime.release(g); g.discard(); }
        h.succeed();
    }
    private static Guard[] stagingPair(GameTestHelper h) {
        floor(h);
        for (int x = 10; x <= 17; x++) for (int z = 2; z <= 22; z++) if (z != 12)
            setBlock(h, new BlockPos(x, 0, z), Blocks.AIR);
        Guard a = guard(h, new BlockPos(9, 1, 12)), b = guard(h, new BlockPos(18, 1, 12));
        install(h, a, GuardLocalRoutes.search(a, absolute(h, new BlockPos(22, 1, 12)), 24));
        install(h, b, GuardLocalRoutes.search(b, absolute(h, new BlockPos(6, 1, 12)), 24));
        GuardNavigationRuntime.beforeTick(a);
        GuardNavigationRuntime.beforeTick(b);
        h.assertTrue(GuardNavigationRuntime.waiting(b) && b.getMoveControl().getSpeedModifier() > 0,
                "Fixture did not start a safe sidestep: " + GuardNavigationRuntime.status(b));
        return new Guard[]{a, b};
    }
    @GameTest(batch = "navigation_chase_handoff", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void cancellingOrdinaryCombatApproachStopsChaseWhilePeekWaitsForBudget(GameTestHelper h) {
        floor(h);
        Guard g = guard(h, new BlockPos(5, 1, 12));
        var target = net.minecraft.world.entity.EntityType.HUSK.create(h.getLevel());
        target.moveTo(Vec3.atBottomCenterOf(absolute(h, new BlockPos(18, 1, 12))));
        target.setNoAi(true); h.getLevel().addFreshEntity(target);
        try {
            var movement = new com.gamergaming.villagerguardgunners.ai.SquadCommandMovementController(g);
            movement.moveTowardCombatTarget(null, target, .75);
            h.assertFalse(g.getNavigation().isDone(), "Fixture did not start an ordinary combat approach");
            h.assertFalse(movement.ownsScreenedCombatPath(), "Fixture unexpectedly used bounded combat screening");
            g.getNavigation().tick();
            h.assertTrue(g.getMoveControl().hasWanted(), "Fixture did not issue a chase movement command");
            // The gun goal makes this handoff before requesting a bounded peek path.
            movement.invalidateCombatPath();
            h.assertTrue(g.getNavigation().isDone(), "Old ordinary chase continued while investigation waited for a path slot");
            h.assertTrue(g.getMoveControl().getSpeedModifier() == 0, "Cancelled chase retained its movement command");
        } finally { GuardNavigationRuntime.release(g); g.discard(); target.discard(); }
        h.succeed();
    }
    private static void discardPair(Guard[] pair) {
        for (Guard guard : pair) { GuardNavigationRuntime.release(guard); guard.discard(); }
    }
    @GameTest(batch = "navigation_policy", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void wallsAndOtherFloorsDoNotCountAsArrivalAndIronDoorStaysBlocked(GameTestHelper h) {
        floor(h);
        Guard g = guard(h, new BlockPos(8, 1, 12));
        BlockPos close = absolute(h, new BlockPos(9, 1, 12));
        h.assertTrue(GuardRouteArrival.at(g, close, 1.5, 1.25), "Open final approach was rejected");
        setBlock(h, new BlockPos(9, 1, 12), Blocks.STONE);
        h.assertFalse(GuardRouteArrival.at(g, close.above(), 1.5, 1.25), "A different floor counted as arrival");
        setBlock(h, new BlockPos(9, 2, 12), Blocks.STONE);
        h.assertFalse(GuardRouteArrival.at(g, close, 1.5, 1.25), "A wall counted as arrival");
        for (int z = 2; z <= 22; z++) for (int y = 1; y <= 3; y++) setBlock(h, new BlockPos(10, y, z), Blocks.STONE);
        var door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        setBlock(h, new BlockPos(10, 1, 12), door);
        setBlock(h, new BlockPos(10, 2, 12), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        h.assertFalse(GuardGroundTraversal.safeStanding(g, h.absoluteVec(new Vec3(10.5, 1, 12.5)).add(0, floorOffset(h), 0), true), "Iron door became a conditional wooden transition");
        g.discard(); h.succeed();
    }
    private static GuardRouteProgress install(GameTestHelper h, Guard g, Path path) {
        h.assertTrue(path != null && path.canReach() && g.getNavigation().moveTo(path, .85), "Route installation failed: " + GuardLocalRoutes.status(g));
        var progress = new GuardRouteProgress(); progress.start(g, g.getNavigation().getPath(), h.getLevel().getGameTime()); return progress;
    }
    @GameTest(batch = "navigation_peek_diversity", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void shortPeekCanLookBeyondTheNearestRingAroundAWiderWall(GameTestHelper h) {
        assertWideWallPeek(h, false);
    }
    @GameTest(batch = "navigation_peek_diversity_mirrored", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void mirroredWiderWallPeekRespectsThreatAndAssignmentBounds(GameTestHelper h) {
        assertWideWallPeek(h, true);
    }
    private static void assertWideWallPeek(GameTestHelper h, boolean mirrored) {
        floor(h);
        Guard g = guard(h, new BlockPos(mirrored ? 25 : 5, 1, 12));
        try {
            for (int y = 1; y <= 3; y++) for (int z = 10; z <= 14; z++) setBlock(h, new BlockPos(mirrored ? 19 : 11, y, z), Blocks.STONE);
            var planner = new com.gamergaming.villagerguardgunners.ai.tactics.TacticalPositionPlanner(g);
            Vec3 known = Vec3.atBottomCenterOf(absolute(h, new BlockPos(mirrored ? 11 : 19, 1, 12)));
            var threats = List.of(new com.gamergaming.villagerguardgunners.ai.tactics.ThreatMemory.Observation(
                    java.util.UUID.randomUUID(), h.getLevel().dimension().location().toString(), known, Vec3.ZERO,
                    h.getLevel().getGameTime(), -1, false, false, false));
            var bounds = com.gamergaming.villagerguardgunners.ai.tactics.TacticalAssignmentBounds.UNBOUNDED;
            var failed = new java.util.HashSet<BlockPos>();
            com.gamergaming.villagerguardgunners.ai.tactics.TacticalPositionPlanner.Plan plan = null;
            // Exercise the investigation's actual retry limit and failed-destination memory.
            for (int attempt = 0; attempt < com.gamergaming.villagerguardgunners.ai.tactics.GuardInvestigationController.MAX_ATTEMPTS; attempt++) {
                plan = planner.planPeek(known, g.position(), threats, bounds, failed);
                h.assertTrue(planner.candidatesChecked() <= 48 && planner.sightRays() <= 8 && planner.pathsBuilt() <= 1,
                        "Wider-wall peek increased the per-attempt work limits");
                if (plan.path() != null) break;
                if (plan.destination() != null) failed.add(plan.destination());
            }
            h.assertTrue(plan.path() != null && plan.path().canReach(), "Nearest blind candidates hid a reachable short peek: " + plan.reason());
            h.assertTrue(planner.safePeekRoute(plan.path(), threats, bounds, g.position()), "Peek exceeded its ground safety, threat clearance, or travel bounds");
            var restricted = new com.gamergaming.villagerguardgunners.ai.tactics.TacticalAssignmentBounds(g.position(), 3.5);
            h.assertTrue(planner.planPeek(known, g.position(), threats, restricted, Set.of()).path() == null,
                    "Wider peek escaped a restrictive assignment boundary");
        } finally { GuardNavigationRuntime.release(g); g.discard(); }
        h.succeed();
    }
    @GameTest(batch = "navigation_fallback_screening", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void verifiedLocalGroundRoutesPassTacticalScreeningAndRecheckTerrain(GameTestHelper h) {
        floor(h);
        Guard g = guard(h, new BlockPos(5, 1, 12));
        try {
            Path path = GuardLocalRoutes.search(g, absolute(h, new BlockPos(15, 1, 16)), 24);
            h.assertTrue(path != null && path.canReach(), "No complete verified local route");
            var planner = new com.gamergaming.villagerguardgunners.ai.tactics.TacticalPositionPlanner(g);
            var bounds = com.gamergaming.villagerguardgunners.ai.tactics.TacticalAssignmentBounds.UNBOUNDED;
            h.assertTrue(planner.safeRoute(path, 0, List.of(), bounds, false),
                    "Verified fallback ground retained unsafe native node metadata");
            h.getLevel().setBlock(path.getNodePos(1).below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            h.assertFalse(planner.safeRoute(path, 0, List.of(), bounds, false),
                    "Walkable node metadata hid a new terrain hazard");
        } finally { GuardNavigationRuntime.release(g); g.discard(); }
        h.succeed();
    }
    @GameTest(batch = "navigation_invalid_native_range", templateNamespace = "villagerguardgunners_navigation", template = ARENA)
    public static void invalidPlannerRangesCannotBypassTheLoadedRegionGuard(GameTestHelper h) {
        floor(h);
        Guard g = guard(h, new BlockPos(5, 1, 12));
        try {
            Path current = GuardLocalRoutes.search(g, absolute(h, new BlockPos(15, 1, 12)), 20);
            install(h, g, current);
            for (int range : new int[]{-1000, 0, 129, Integer.MAX_VALUE}) {
                h.assertFalse(GuardLocalRoutes.nativeRegionLoaded(g, range), "Invalid native range reported a verified region: " + range);
                h.assertTrue(GuardLocalRoutes.create(g, absolute(h, new BlockPos(10, 1, 12)), range) == null,
                        "Invalid range started a search: " + range);
                h.assertTrue(GuardLocalRoutes.status(g).equals("INVALID_SEARCH"), "Invalid range retained stale search diagnostics");
                h.assertTrue(g.getNavigation().getPath() == current && GuardNavigationRuntime.owns(g), "Rejected planning disturbed the active route");
            }
            h.assertTrue(GuardLocalRoutes.create(g, null, 20) == null && GuardLocalRoutes.search(g, null, 20) == null,
                    "Absent target started a search");
        } finally { GuardNavigationRuntime.release(g); g.discard(); }
        h.succeed();
    }
    private static Guard guard(GameTestHelper h, BlockPos pos) {
        Guard guard = GuardEntityType.GUARD.get().create(h.getLevel());
        guard.moveTo(Vec3.atBottomCenterOf(absolute(h, pos))); guard.setOnGround(true); guard.setInvulnerable(true);
        guard.setPersistenceRequired();
        guard.goalSelector.removeAllGoals(goal -> true); guard.targetSelector.removeAllGoals(goal -> true);
        var navigation = (net.minecraft.world.entity.ai.navigation.GroundPathNavigation) guard.getNavigation();
        navigation.setCanOpenDoors(true); navigation.setCanPassDoors(true);
        h.getLevel().addFreshEntity(guard);
        guard.goalSelector.removeAllGoals(goal -> true); guard.targetSelector.removeAllGoals(goal -> true);
        return guard;
    }
    private static int floorOffset(GameTestHelper h) {
        return h.getLevel().getMaxBuildHeight() - 8 - h.absolutePos(BlockPos.ZERO).getY();
    }
    private static BlockPos relative(GameTestHelper h, BlockPos p) { return p.offset(0, floorOffset(h), 0); }
    private static BlockPos absolute(GameTestHelper h, BlockPos p) { return h.absolutePos(relative(h, p)); }
    private static void setBlock(GameTestHelper h, BlockPos p, net.minecraft.world.level.block.Block block) {
        h.setBlock(relative(h, p), block);
    }
    private static void setBlock(GameTestHelper h, BlockPos p, net.minecraft.world.level.block.state.BlockState state) {
        h.setBlock(relative(h, p), state);
    }
    private static void floor(GameTestHelper h) {
        for (int x = 2; x <= 30; x++) for (int z = 2; z <= 22; z++) {
            setBlock(h, new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 4; y++) setBlock(h, new BlockPos(x, y, z), Blocks.AIR);
        }
    }
}
