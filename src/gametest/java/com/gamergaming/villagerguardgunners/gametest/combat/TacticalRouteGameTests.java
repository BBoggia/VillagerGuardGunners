package com.gamergaming.villagerguardgunners.gametest.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.gamergaming.villagerguardgunners.ai.tactics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class TacticalRouteGameTests {
    private static final List<net.minecraft.world.entity.Entity> ACTORS = new ArrayList<>();
    private static final String ARENA = "villagerguardgunners/gametest_arena";

    @GameTest(batch = "route_screening", templateNamespace = "minecraft", template = ARENA)
    public static void safeEndpointCannotHideDangerousRoute(GameTestHelper h) {
        Guard guard = guard(h);
        var planner = new TacticalPositionPlanner(guard);
        var threats = List.of(threat(h, 26, 12));
        Path dangerous = path(h, 20, 21, 22, 23, 24, 23, 22, 21, 20, 19, 18, 17, 16);
        h.assertFalse(planner.safeRoute(dangerous, 0, threats, TacticalAssignmentBounds.UNBOUNDED, false),
                "A safe endpoint authorized crossing close to an observed enemy");
        var plan = planner.plan(threats, TacticalAssignmentBounds.UNBOUNDED, Set.of(), false, List.of());
        h.assertTrue(plan.path() != null && plan.path().canReach(), "An alternative safe native route was not found: " + plan.reason());
        h.assertTrue(planner.candidatesChecked() <= 48 && planner.pathsBuilt() == 1, "Planner exceeded its fixed work budget");
        var laneChange = planner.plan(List.of(threat(h, 30, 12)), TacticalAssignmentBounds.UNBOUNDED,
                Set.of(), true, List.of());
        h.assertTrue(laneChange.path() != null, "A known blocked lane incorrectly made holding the best baseline");
        var ordinary = threat(h, 30, 12);
        var explosive = new ThreatMemory.Observation(ordinary.id(), ordinary.dimension(), ordinary.position(),
                ordinary.velocity(), ordinary.observedAt(), ordinary.attackEvidenceAt(), false, true, false);
        h.assertFalse(planner.safeRoute(path(h, 20, 21, 22, 23), 0, List.of(explosive),
                TacticalAssignmentBounds.UNBOUNDED, false), "Route entered observed explosive danger using ordinary melee clearance");
        guard.discard(); h.succeed();
    }

    @GameTest(batch = "route_constraints", templateNamespace = "minecraft", template = ARENA)
    public static void routeChecksAssignmentTerrainAndChangedObstruction(GameTestHelper h) {
        Guard guard = guard(h); var planner = new TacticalPositionPlanner(guard);
        Path path = path(h, 20, 19, 18, 17, 16);
        h.assertTrue(planner.safeRoute(path, 0, List.of(), TacticalAssignmentBounds.UNBOUNDED, false), "Flat route rejected");
        h.setBlock(new BlockPos(18, 1, 12), Blocks.STONE);
        h.assertFalse(planner.safeRoute(path, 0, List.of(), TacticalAssignmentBounds.UNBOUNDED, false), "New obstruction was ignored");
        h.setBlock(new BlockPos(18, 1, 12), Blocks.AIR);
        h.setBlock(new BlockPos(18, 0, 12), Blocks.MAGMA_BLOCK);
        h.assertFalse(planner.safeRoute(path, 0, List.of(), TacticalAssignmentBounds.UNBOUNDED, false), "Hazardous support was accepted");
        h.setBlock(new BlockPos(18, 0, 12), Blocks.STONE);
        h.setBlock(new BlockPos(18, 0, 12), Blocks.AIR);
        h.assertFalse(planner.safeRoute(path, 0, List.of(), TacticalAssignmentBounds.UNBOUNDED, false), "Route crossed unsupported cliff space");
        h.setBlock(new BlockPos(18, 0, 12), Blocks.STONE);
        for (var hazard : List.of(Blocks.WATER, Blocks.LAVA, Blocks.POWDER_SNOW)) {
            h.setBlock(new BlockPos(18, 1, 12), hazard);
            h.assertFalse(planner.safeRoute(path, 0, List.of(), TacticalAssignmentBounds.UNBOUNDED, false), "Route accepted " + hazard);
            h.setBlock(new BlockPos(18, 1, 12), Blocks.AIR);
        }
        var bounds = new TacticalAssignmentBounds(h.absoluteVec(new Vec3(20.5, 1, 12.5)), 2);
        Path excursion = path(h, 20, 19, 18, 17, 18, 19, 20);
        h.assertFalse(planner.safeRoute(excursion, 0, List.of(), bounds, false), "Route left assignment despite returning to an allowed endpoint");
        guard.discard(); h.succeed();
    }

    @GameTest(batch = "route_hazard_start", templateNamespace = "minecraft", template = ARENA)
    public static void hazardousStartSearchesForEscapeAndUnloadedRegionIsRejected(GameTestHelper h) {
        Guard guard = guard(h); var planner = new TacticalPositionPlanner(guard);
        h.setBlock(new BlockPos(20, 0, 12), Blocks.MAGMA_BLOCK);
        h.assertFalse(planner.safeStanding(guard.position()), "Hazardous starting block counted as a safe hold");
        var plan = planner.plan(List.of(), TacticalAssignmentBounds.UNBOUNDED, Set.of(), false, List.of());
        h.assertTrue(plan.path() != null, "Hazard did not produce a reachable escape attempt: " + plan.reason());
        BlockPos remote = new BlockPos(10_000_000, 80, 10_000_000);
        h.assertFalse(h.getLevel().hasChunk(remote.getX() >> 4, remote.getZ() >> 4), "Remote fixture chunk was already loaded");
        guard.setPos(Vec3.atBottomCenterOf(remote));
        var unloaded = planner.plan(List.of(), TacticalAssignmentBounds.UNBOUNDED, Set.of(), false, List.of());
        h.assertTrue(unloaded.path() == null && planner.pathsBuilt() == 0, "Unloaded terrain requested path construction");
        h.assertFalse(h.getLevel().hasChunk(remote.getX() >> 4, remote.getZ() >> 4), "Planner loaded a remote chunk");
        guard.discard(); h.succeed();
    }

    @GameTest(batch = "route_retry", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 100)
    public static void blockedRouteStopsAndRetriesWithinBudget(GameTestHelper h) {
        Guard guard = guard(h);
        var tactics = new GuardTacticalController(guard);
        var enemy = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(26, 1, 12));
        enemy.setNoAi(true); enemy.setInvulnerable(true); ACTORS.add(enemy);
        tactics.observe(enemy, true);
        var goal = new com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal(guard);
        guard.goalSelector.addGoal(2, goal);
        // Queue a native delayed retry before the tactical controller takes ownership.
        guard.getNavigation().moveTo(guard.getX() - 6, guard.getY(), guard.getZ(), .75);
        guard.getNavigation().recomputePath();
        guard.getNavigation().recomputePath();
        var navigation = goal.tacticalNavigation();
        guard.setOnGround(false);
        navigation.tick(tactics, null, 24, () -> { throw new AssertionError("Airborne guard spent a path slot"); }, .75, false, true);
        guard.setOnGround(true);
        navigation.tick(tactics, null, 24, () -> true, .75, false, true);
        h.assertTrue(navigation.isMoving(), "Fixture did not start a route");
        BlockPos blocked = guard.getNavigation().getPath().getNodePos(2);
        h.getLevel().setBlockAndUpdate(blocked, Blocks.STONE.defaultBlockState());
        int initial = navigation.pathAttempts();
        guard.getNavigation().recomputePath();
        h.assertFalse(navigation.isMoving(), "Native recalculation bypassed tactical route ownership");
        h.assertTrue(navigation.pathAttempts() == initial, "Native recalculation bypassed the path budget");
        h.onEachTick(() -> {
            guard.getNavigation().tick();
            navigation.tick(tactics, null, 24, () -> h.getTick() > 25 && h.getTick() % 20 == 0, .75, false, true);
            h.assertTrue(navigation.pathAttempts() <= 5 && navigation.failedDestinations() <= 8,
                    "Blocked route retried without a bound");
        });
        h.runAtTickTime(15, () -> {
            h.assertFalse(navigation.isMoving(), "Blocked owned path remained active");
            h.assertTrue(navigation.pathAttempts() == initial, "Route retried before its cooldown");
            h.getLevel().setBlockAndUpdate(blocked, Blocks.AIR.defaultBlockState());
        });
        h.runAtTickTime(25, () -> h.assertTrue(guard.getNavigation().getPath() == null,
                "A queued native retry constructed a path outside the tactical budget"));
        h.runAtTickTime(55, () -> {
            h.assertTrue(navigation.pathAttempts() > initial, "Route failure froze planning permanently");
            navigation.clear(); guard.discard(); enemy.discard(); h.succeed();
        });
    }

    @GameTest(batch = "route_no_exit", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 100)
    public static void trappedGuardRetriesWhenAnOpeningAppears(GameTestHelper h) {
        Guard guard = guard(h);
        var tactics = new GuardTacticalController(guard);
        var enemy = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(26, 1, 12));
        enemy.setNoAi(true); ACTORS.add(enemy); tactics.observe(enemy, true);
        for (int x = 19; x <= 21; x++) for (int z = 11; z <= 13; z++) {
            if (x == 20 && z == 12) continue;
            for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        var navigation = new TacticalNavigationController(guard);
        h.onEachTick(() -> {
            navigation.tick(tactics, null, 24, () -> h.getTick() % 20 == 0, .75, false, true);
            h.assertTrue(navigation.pathAttempts() <= 1 + h.getTick() / 20,
                    "No-route case bypassed the bounded retry cadence");
            if (h.getTick() < 30) h.assertFalse(navigation.isMoving(), "Trapped guard accepted an unreachable path");
            if (h.getTick() > 30 && navigation.isMoving()) {
                navigation.clear(); h.succeed();
            }
        });
        h.runAtTickTime(30, () -> {
            for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(19, y, 12), Blocks.AIR);
        });
    }

    @GameTest(batch = "route_screening", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 100)
    public static void startingWithdrawalReleasesNativeAimImmediately(GameTestHelper h) {
        Guard guard = guard(h);
        var enemy = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(30, 1, 12));
        enemy.setNoAi(true); enemy.setInvulnerable(true); ACTORS.add(enemy);
        guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                com.tacz.guns.api.item.builder.GunItemBuilder.create()
                        .setId(new net.minecraft.resources.ResourceLocation("tacz", "glock_17"))
                        .setFireMode(com.tacz.guns.api.item.gun.FireMode.SEMI)
                        .setAmmoCount(17).setAmmoInBarrel(true).build());
        var held = com.gamergaming.villagerguardgunners.orders.GuardOrders.DEFAULT.withMovement(
                com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder.HOLD_POSITION);
        com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(guard, held);
        guard.setTarget(enemy);
        var goal = new com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal(guard);
        guard.goalSelector.addGoal(2, goal); goal.start();
        var operator = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard);
        h.onEachTick(() -> {
            guard.getSensing().tick(); goal.tick();
            if (!operator.getDataHolder().isAiming) return;
            com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(guard,
                    held.withMovement(com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder.TACTICAL));
            guard.setHealth(guard.getMaxHealth() * .2f);
            goal.tick();
            h.assertTrue(goal.tacticalNavigation().isMoving(), "Fixture failed to start a withdrawal route");
            h.assertFalse(operator.getDataHolder().isAiming, "Withdrawal retained aim until another goal tick");
            h.assertFalse(goal.providesTacticalSupport(h.getLevel().getGameTime(), enemy),
                    "Moving guard published covering support");
            goal.stop(); h.succeed();
        });
    }

    @GameTest(batch = "route_screening", templateNamespace = "minecraft", template = ARENA)
    public static void defendApproachCannotRecomputeOutsideScreeningBudget(GameTestHelper h) throws Exception {
        Guard guard = guard(h);
        var enemy = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(30, 1, 12));
        enemy.setNoAi(true); ACTORS.add(enemy);
        var goal = new com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal(guard);
        guard.goalSelector.addGoal(2, goal);
        var field = goal.getClass().getDeclaredField("commandMovement");
        field.setAccessible(true);
        var movement = (com.gamergaming.villagerguardgunners.ai.SquadCommandMovementController) field.get(goal);
        long now = h.getLevel().getGameTime();
        var command = com.gamergaming.villagerguardgunners.squad.SquadCommand.positional(
                com.gamergaming.villagerguardgunners.squad.SquadCommandType.DEFEND, UUID.randomUUID(),
                now, now + 600, h.getLevel().dimension().location(), h.absolutePos(new BlockPos(20, 1, 12)), 12);
        var navigation = guard.getNavigation();
        var existing = navigation.createPath(enemy.blockPosition(), 0, TacticalPositionPlanner.PATH_RANGE);
        h.assertTrue(existing != null && navigation.moveTo(existing, .75), "Fixture could not install initial approach");
        // Force a fresh native search with identical nodes, as can occur when
        // the cached destination changes but the traversable route does not.
        var targetField = net.minecraft.world.entity.ai.navigation.PathNavigation.class.getDeclaredField("targetPos");
        targetField.setAccessible(true);
        targetField.set(navigation, null);
        movement.moveTowardCombatTarget(command, enemy, .75);
        h.assertFalse(guard.getNavigation().isDone(), "Fixture did not start a bounded combat approach");
        h.assertTrue(navigation.getPath() == existing, "Fixture did not exercise native equivalent-path retention");
        h.assertTrue(movement.ownsScreenedCombatPath(), "Equivalent native path lost its screening owner");
        guard.getNavigation().recomputePath();
        h.assertTrue(guard.getNavigation().isDone(), "Native recomputation bypassed DEFEND path screening");
        h.assertFalse(movement.takePathSlot(now), "Invalidation reset the shared planning budget");
        movement.stop(); h.succeed();
    }

    private static void cleanup() { ACTORS.forEach(net.minecraft.world.entity.Entity::discard); ACTORS.clear(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "route_screening")
    public static void afterScreening(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "route_constraints")
    public static void afterConstraints(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "route_hazard_start")
    public static void afterHazard(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "route_retry")
    public static void afterRetry(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "route_no_exit")
    public static void afterNoExit(net.minecraft.server.level.ServerLevel level) { cleanup(); }

    private static Guard guard(GameTestHelper h) {
        for (int x = 1; x < 39; x++) for (int z = 1; z < 25; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y < 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        Guard guard = h.spawnWithNoFreeWill(GuardEntityType.GUARD.get(), new BlockPos(20, 1, 12));
        guard.setNoAi(true); guard.setNoGravity(true); guard.setInvulnerable(true); guard.setOnGround(true);
        ACTORS.add(guard); return guard;
    }
    private static ThreatMemory.Observation threat(GameTestHelper h, int x, int z) {
        return new ThreatMemory.Observation(UUID.randomUUID(), h.getLevel().dimension().location().toString(),
                h.absoluteVec(new Vec3(x + .5, 1, z + .5)), Vec3.ZERO, h.getLevel().getGameTime(),
                h.getLevel().getGameTime(), false, false, false);
    }
    private static Path path(GameTestHelper h, int... xs) {
        List<Node> nodes = new ArrayList<>();
        for (int x : xs) {
            BlockPos p = h.absolutePos(new BlockPos(x, 1, 12));
            Node node = new Node(p.getX(), p.getY(), p.getZ());
            node.type = net.minecraft.world.level.pathfinder.BlockPathTypes.WALKABLE;
            nodes.add(node);
        }
        return new Path(nodes, nodes.get(nodes.size() - 1).asBlockPos(), true);
    }
}
