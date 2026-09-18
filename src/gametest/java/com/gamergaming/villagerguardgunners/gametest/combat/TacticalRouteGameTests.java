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

    @GameTest(batch = "route_screening", templateNamespace = "minecraft", template = ARENA)
    public static void laneDestinationsRespectSelectedTargetAlliesAndWeaponRange(GameTestHelper h) {
        Guard guard = guard(h);
        var target = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(34, 1, 12));
        var ally = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(27, 1, 12));
        ACTORS.add(target); ACTORS.add(ally);
        var request = new TacticalFiringLane.Request(target, 10, 18, 24, .5, 0);
        var lane = TacticalFiringLane.capture(guard, request);
        h.assertTrue(lane != null && !lane.clearFrom(guard.position()), "Fixture did not block the original lane");
        var planner = new TacticalPositionPlanner(guard);
        var plan = planner.plan(List.of(threat(h, 24, 22), threat(h, 34, 12)),
                TacticalAssignmentBounds.UNBOUNDED, Set.of(), true, List.of(), request);
        h.assertTrue(plan.path() != null && lane.clearFrom(Vec3.atBottomCenterOf(plan.destination())),
                "Reposition accepted no clear lane to the selected target: " + plan.reason());
        h.assertTrue(Vec3.atBottomCenterOf(plan.destination()).distanceTo(target.position()) >= 10,
                "Lane ignored the weapon's minimum range");
        var stale = planner.plan(List.of(), TacticalAssignmentBounds.UNBOUNDED, Set.of(), true, List.of());
        h.assertTrue(stale.path() == null && planner.pathsBuilt() == 0, "Blind reposition had no target context");
        var excessive = new TacticalFiringLane.Request(target, 40, 45, 50, .5, 0);
        h.assertTrue(planner.plan(List.of(), TacticalAssignmentBounds.UNBOUNDED, Set.of(), true, List.of(), excessive).path() == null,
                "Planner offered a destination outside the weapon window");
        for (int i = 0; i <= TacticalFiringLane.ENTITY_LIMIT; i++)
            ACTORS.add(h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(30, 1, 18)));
        h.assertTrue(TacticalFiringLane.capture(guard, request) == null, "Truncated ally geometry claimed a complete lane");
        h.succeed();
    }

    @GameTest(batch = "route_screening", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 60)
    public static void movingAllyInvalidatesThePlannedLane(GameTestHelper h) {
        Guard guard = guard(h);
        var target = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(34, 1, 12));
        var ally = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(27, 1, 12));
        ACTORS.add(target); ACTORS.add(ally);
        guard.setTarget(target);
        var tactics = new GuardTacticalController(guard); tactics.observe(target, true);
        var nav = new TacticalNavigationController(guard);
        var request = new TacticalFiringLane.Request(target, 0, 18, 30, .5, 0);
        nav.tick(tactics, null, 30, () -> true, .75, true, true, request);
        h.assertTrue(nav.isMoving(), "Missing initial lane: " + nav.status());
        Vec3 obstruction = Vec3.atBottomCenterOf(nav.destination()).lerp(target.position(), .5);
        ally.setPos(obstruction); ally.setNoGravity(true);
        h.runAtTickTime(12, () -> {
            nav.tick(tactics, null, 30, () -> false, .75, true, false, request);
            h.assertTrue(!nav.isMoving() && nav.status().equals("LANE_INVALIDATED"),
                    "Changed ally geometry retained a blocked destination: " + nav.status());
            h.succeed();
        });
    }

    @GameTest(batch = "route_screening", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 100)
    public static void laneCommitmentSurvivesShotHistoryAndRevokesOnContextChange(GameTestHelper h) {
        Guard guard = guard(h);
        var target = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(34, 1, 12));
        var ally = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(27, 1, 12));
        ACTORS.add(target); ACTORS.add(ally);
        guard.setTarget(target);
        var tactics = new GuardTacticalController(guard); tactics.observe(target, true);
        var nav = new TacticalNavigationController(guard);
        var request = new TacticalFiringLane.Request(target, 0, 18, 30, .5, 0);
        nav.tick(tactics, null, 30, () -> true, .75, true, true, request);
        h.assertTrue(nav.isMoving() && nav.isLaneChange(), "Fixture did not start repositioning: " + nav.status());
        h.runAtTickTime(25, () -> {
            h.assertTrue(nav.canContinueLane(target, true, true, com.gamergaming.villagerguardgunners.combat.WeaponReadiness.READY),
                    "Lane commitment expired with the short blocked-shot history");
            h.assertFalse(nav.canContinueLane(target, true, false, com.gamergaming.villagerguardgunners.combat.WeaponReadiness.READY),
                    "Revoked engagement retained lane commitment");
            h.assertFalse(nav.canContinueLane(target, true, true, com.gamergaming.villagerguardgunners.combat.WeaponReadiness.RELOADING),
                    "Reload retained an offensive lane commitment");
            com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(guard,
                    com.gamergaming.villagerguardgunners.orders.GuardOrders.DEFAULT.withMovement(
                            com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder.HOLD_POSITION));
            h.assertFalse(nav.canContinueLane(target, true, true, com.gamergaming.villagerguardgunners.combat.WeaponReadiness.READY),
                    "Changed orders retained lane commitment");
            nav.clear(); h.succeed();
        });
    }

    @GameTest(batch = "route_screening", templateNamespace = "minecraft", template = ARENA)
    public static void tacticalRoutesOwnRetainedPathsAndNeverStopForeignPaths(GameTestHelper h) throws Exception {
        Guard guard = guard(h);
        var tactics = new GuardTacticalController(guard);
        var enemy = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(26, 1, 12));
        ACTORS.add(enemy); tactics.observe(enemy, true);
        var planner = new TacticalPositionPlanner(guard);
        var plan = planner.plan(tactics.tracker().observations(), TacticalAssignmentBounds.UNBOUNDED, Set.of(), false, List.of());
        h.assertTrue(plan.path() != null, "Missing fixture route");
        guard.getNavigation().moveTo(plan.path(), .75);
        var targetField = net.minecraft.world.entity.ai.navigation.PathNavigation.class.getDeclaredField("targetPos");
        targetField.setAccessible(true); targetField.set(guard.getNavigation(), null);
        var nav = new TacticalNavigationController(guard);
        nav.tick(tactics, null, 24, () -> true, .75, false, true);
        h.assertTrue(guard.getNavigation().getPath() == plan.path() && nav.ownsPath() && nav.isMoving(),
                "Equivalent installed path lost tactical ownership");
        Path foreign = path(h, 20, 19, 18);
        guard.getNavigation().moveTo(foreign, .5);
        h.assertFalse(nav.isMoving(), "Foreign navigation was reported as tactical movement");
        h.assertTrue(nav.lookDestination() == null, "Foreign navigation retained a stale tactical look point");
        nav.tick(tactics, null, 24, () -> false, .75, false, false);
        h.assertTrue(guard.getNavigation().getPath() == foreign && !guard.getNavigation().isDone()
                && nav.status().equals("ROUTE_REPLACED"), "Lost ownership stopped another controller's path");
        h.succeed();
    }

    @GameTest(batch = "route_live_lane", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 260)
    public static void liveGuardFindsLaneAndResumesNativeFire(GameTestHelper h) {
        Guard floorProbe = guard(h); floorProbe.discard();
        Guard guard = h.spawn(GuardEntityType.GUARD.get(), new BlockPos(20, 1, 12));
        guard.setInvulnerable(true); ACTORS.add(guard);
        var target = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.RAVAGER, new BlockPos(34, 1, 12));
        var ally = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(27, 1, 12));
        target.setInvulnerable(true); ally.setInvulnerable(true); ACTORS.add(target); ACTORS.add(ally);
        var gun = com.tacz.guns.api.item.builder.GunItemBuilder.create()
                .setId(net.minecraft.resources.ResourceLocation.parse("tacz:glock_17"))
                .setFireMode(com.tacz.guns.api.item.gun.FireMode.SEMI).setAmmoCount(17).setAmmoInBarrel(true).build();
        guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, gun);
        guard.setTarget(target);
        com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(guard,
                com.gamergaming.villagerguardgunners.orders.GuardOrders.DEFAULT.withMovement(
                        com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder.HOLD_POSITION));
        var goal = guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal.class::isInstance)
                .map(com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal.class::cast).findFirst().orElseThrow();
        long[] nextFrame = {System.nanoTime()};
        boolean[] observed = {false, false};
        h.onEachTick(() -> {
            long remaining;
            while ((remaining = nextFrame[0] - System.nanoTime()) > 0) {
                java.util.concurrent.locks.LockSupport.parkNanos(remaining);
                if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Lane test interrupted");
            }
            nextFrame[0] = System.nanoTime() + 50_000_000L;
            var safety = com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess.controller(guard).currentShotSafety(target);
            int ammo = ((com.tacz.guns.api.item.IGun) guard.getMainHandItem().getItem()).getCurrentAmmoCount(guard.getMainHandItem());
            if (!observed[0]) {
                h.assertTrue(ammo == 17, "Guard fired before proving the blocked original lane");
                if (safety.status() == com.gamergaming.villagerguardgunners.combat.GuardShotSafety.Status.PROTECTED_ENTITY_IN_PATH) {
                    observed[0] = true;
                    com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(guard,
                            com.gamergaming.villagerguardgunners.orders.GuardOrders.DEFAULT.withMovement(
                                    com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder.TACTICAL));
                }
            }
            if (goal.tacticalNavigation().isMoving() && goal.tacticalNavigation().isLaneChange()) {
                observed[1] = true;
                h.assertTrue(goal.getPlayerFacingStatus() == com.gamergaming.villagerguardgunners.combat.GuardCombatStatus.REPOSITIONING,
                        "Live lane movement was not reported as repositioning");
                h.assertTrue(ammo == 17, "Guard fired during its lane change");
            }
            if (observed[1] && ammo < 17) {
                h.assertTrue(safety.status() == com.gamergaming.villagerguardgunners.combat.GuardShotSafety.Status.CLEAR,
                        "Fire resumed without native safety approval");
                goal.stop(); cleanup(); h.succeed();
            }
        });
        h.runAtTickTime(245, () -> h.fail("Live lane failed to finish: observed=" + java.util.Arrays.toString(observed)
                + ", route=" + goal.tacticalNavigation().status() + ", diagnostics=" + goal.captureDiagnostics()));
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

    @net.minecraft.gametest.framework.AfterBatch(batch = "route_live_lane")
    public static void afterLiveLane(net.minecraft.server.level.ServerLevel level) { cleanup(); }

    @GameTest(batch = "route_shared_ground", templateNamespace = "minecraft", template = ARENA)
    public static void combatAndCoverageAgreeOnVillageGround(GameTestHelper h) {
        Guard guard = guard(h);
        BlockPos floor = new BlockPos(20, 0, 12), node = h.absolutePos(floor.above());
        var planner = new TacticalPositionPlanner(guard);
        var lit = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
        var waterlogged = net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
        var slabType = net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE;
        for (var state : List.of(Blocks.STONE_SLAB.defaultBlockState(),
                Blocks.STONE_BRICK_STAIRS.defaultBlockState(),
                Blocks.CAMPFIRE.defaultBlockState().setValue(lit, false),
                Blocks.STONE_SLAB.defaultBlockState().setValue(slabType, net.minecraft.world.level.block.state.properties.SlabType.TOP)
                    .setValue(waterlogged, true))) {
            h.setBlock(floor, state);
            h.assertTrue(com.gamergaming.villagerguardgunners.village.coverage.live.CoverageTerrain.safeStandingPosition(h.getLevel(), node),
                    "Coverage rejected dry support " + state);
            h.assertTrue(planner.safeStanding(Vec3.atBottomCenterOf(node)), "Combat rejected coverage support " + state);
            double y = com.gamergaming.villagerguardgunners.ai.navigation.GuardGroundTraversal.standingHeight(h.getLevel(), node);
            h.assertTrue(planner.safeStanding(new Vec3(node.getX() + .5, y, node.getZ() + .5)), "Actual fractional standing height rejected " + state);
        }
        for (var state : List.of(Blocks.CAMPFIRE.defaultBlockState().setValue(lit, true),
                Blocks.STONE_SLAB.defaultBlockState().setValue(waterlogged, true), Blocks.MAGMA_BLOCK.defaultBlockState())) {
            h.setBlock(floor, state);
            h.assertFalse(com.gamergaming.villagerguardgunners.village.coverage.live.CoverageTerrain.safeStandingPosition(h.getLevel(), node),
                    "Coverage accepted unsafe support " + state);
            h.assertFalse(planner.safeStanding(Vec3.atBottomCenterOf(node)), "Combat accepted unsafe support " + state);
        }
        guard.discard(); h.succeed();
    }

    @GameTest(batch = "route_emergency_supply", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 640)
    public static void dryGuardResuppliesDuringCombatAndResumesNativeFire(GameTestHelper h) {
        Guard floor = guard(h); floor.discard();
        Guard guard = h.spawn(GuardEntityType.GUARD.get(), new BlockPos(20, 1, 12));
        guard.setInvulnerable(true); ACTORS.add(guard);
        var target = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.RAVAGER, new BlockPos(34, 1, 12));
        target.setInvulnerable(true); ACTORS.add(target);
        guard.targetSelector.removeAllGoals(goal -> true);
        guard.setTarget(target);
        var gun = com.tacz.guns.api.item.builder.GunItemBuilder.create()
                .setId(net.minecraft.resources.ResourceLocation.parse("tacz:ak47"))
                .setFireMode(com.tacz.guns.api.item.gun.FireMode.AUTO).setAmmoCount(0).setAmmoInBarrel(false).build();
        guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, gun);
        com.gamergaming.villagerguardgunners.api.GuardAmmoAccess.getAmmoHandler(guard).clear();
        BlockPos armoryPos = new BlockPos(8, 1, 12);
        h.setBlock(armoryPos, com.gamergaming.villagerguardgunners.init.ModBlocks.ARMORY.get());
        var armory = (com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(armoryPos));
        var ammo = com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(net.minecraft.resources.ResourceLocation.parse("tacz:762x39")).build();
        ammo.setCount(60); armory.getInventory().setStackInSlot(0, ammo);
        var runtime = com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess.getController(guard);
        long[] nextFrame = {System.nanoTime()}; boolean[] transferred = {false};
        BlockPos[] hiddenHazard = {null};
        int[] stage = {0}; long[] changedAt = {0};
        h.onEachTick(() -> {
            long remaining;
            while ((remaining = nextFrame[0] - System.nanoTime()) > 0) java.util.concurrent.locks.LockSupport.parkNanos(remaining);
            nextFrame[0] = System.nanoTime() + 50_000_000L;
            h.assertTrue(guard.getTarget() == target, "Emergency resupply cleared the target to bypass combat precedence");
            if (runtime.isEmergency()) h.assertFalse(com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(guard).getDataHolder().isAiming,
                    "Emergency movement retained native aim");
            if (stage[0] == 0 && runtime.ownsEmergencyPath()) {
                // Recalculation must invalidate this route rather than build an unscreened replacement.
                guard.getNavigation().recomputePath();
                h.assertTrue(guard.getNavigation().isDone(), "Native resupply recalculation bypassed screening");
                com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(guard,
                        com.gamergaming.villagerguardgunners.orders.GuardOrders.DEFAULT.withMovement(
                                com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder.HOLD_POSITION));
                stage[0] = 1; changedAt[0] = h.getTick();
            } else if (stage[0] == 1 && h.getTick() > changedAt[0] + 2) {
                h.assertFalse(runtime.hasActiveRequest(), "Hold Position failed to revoke emergency supply");
                h.assertTrue(armory.getInventory().getStackInSlot(0).getCount() == 60, "Cancelled trip consumed stock");
                com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(guard,
                        com.gamergaming.villagerguardgunners.orders.GuardOrders.DEFAULT);
                stage[0] = 2;
            } else if (stage[0] == 2 && runtime.ownsEmergencyPath()) {
                target.moveTo(guard.getX() + 4, guard.getY(), guard.getZ());
                stage[0] = 3; changedAt[0] = h.getTick();
            } else if (stage[0] == 3 && h.getTick() > changedAt[0] + 2) {
                h.assertFalse(runtime.hasActiveRequest(), "Close threat failed to revoke emergency supply");
                h.assertTrue(armory.getInventory().getStackInSlot(0).getCount() == 60, "Threat interruption consumed stock");
                var position = h.absoluteVec(new Vec3(34.5, 1, 12.5));
                target.moveTo(position.x, position.y, position.z);
                stage[0] = 4;
            } else if (stage[0] == 4 && runtime.ownsEmergencyPath()) {
                var path = guard.getNavigation().getPath();
                hiddenHazard[0] = path.getNodePos(Math.min(path.getNodeCount() - 1, path.getNextNodeIndex() + 4)).below();
                // Simulate a mod's direct chunk edit: no native terrain notification.
                h.getLevel().getChunkAt(hiddenHazard[0]).setBlockState(hiddenHazard[0], Blocks.MAGMA_BLOCK.defaultBlockState(), false);
                stage[0] = 5;
            } else if (stage[0] == 5 && !runtime.hasActiveRequest()) {
                h.assertTrue(armory.getInventory().getStackInSlot(0).getCount() == 60, "Unsafe route consumed armory stock");
                h.getLevel().getChunkAt(hiddenHazard[0]).setBlockState(hiddenHazard[0], Blocks.STONE.defaultBlockState(), false);
                // Restart the next independent cancellation probe outside withdrawal distance.
                guard.setPos(h.absoluteVec(new Vec3(20.5, 1, 12.5)));
                guard.setDeltaMovement(Vec3.ZERO);
                stage[0] = 6;
            } else if (stage[0] == 6 && runtime.ownsEmergencyPath()) {
                var config = com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS;
                boolean enabled = config.enabled.get();
                var goal = guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                        .filter(com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal.class::isInstance)
                        .map(com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal.class::cast).findFirst().orElseThrow();
                try {
                    config.enabled.accept(false); goal.tick();
                    h.assertFalse(runtime.hasActiveRequest() || runtime.isEmergency(), "Disablement leaked emergency ownership");
                    h.assertTrue(armory.getInventory().getStackInSlot(0).getCount() == 60, "Disablement consumed stock");
                } finally { config.enabled.accept(enabled); }
                stage[0] = 7;
            }
            if (runtime.performanceMetrics().successfulTransfers() > 0) {
                transferred[0] = true;
                h.assertTrue(runtime.performanceMetrics().successfulTransfers() == 1
                        && runtime.performanceMetrics().transferredUnits() == 60
                        && armory.getInventory().getStackInSlot(0).isEmpty(), "Emergency transfer lost or duplicated ammo");
            }
            var nativeGun = com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess.controller(guard);
            var summary = com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition.captureSummary(guard,
                    com.gamergaming.villagerguardgunners.api.GuardAmmoAccess.getTaCZInventoryHandler(guard));
            if (stage[0] == 7 && transferred[0] && summary.primary().displayedLoadedAmmo() + summary.primary().reserveUnits() < 60
                    && nativeGun.currentShotSafety(target).status()
                    == com.gamergaming.villagerguardgunners.combat.GuardShotSafety.Status.CLEAR) {
                h.assertFalse(runtime.hasActiveRequest(), "Successful transfer retained its reservation");
                cleanup(); h.setBlock(armoryPos, Blocks.AIR); h.succeed();
            }
        });
        h.runAtTickTime(630, () -> h.fail("Emergency resupply did not resume native fire: stage=" + stage[0] + ", " + runtime.snapshot()
                + ", position=" + guard.position() + ", native=" + com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess.controller(guard).captureDiagnostics()));
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = "route_emergency_supply")
    public static void afterEmergency(net.minecraft.server.level.ServerLevel level) { cleanup(); }

    @GameTest(batch = "route_shared_ground", templateNamespace = "minecraft", template = ARENA)
    public static void emergencyNeedRejectsUsableSidearmAndReloadReserve(GameTestHelper h) {
        Guard guard = guard(h);
        var primary = com.tacz.guns.api.item.builder.GunItemBuilder.create()
                .setId(net.minecraft.resources.ResourceLocation.parse("tacz:ak47"))
                .setFireMode(com.tacz.guns.api.item.gun.FireMode.AUTO).setAmmoCount(0).setAmmoInBarrel(false).build();
        guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, primary);
        var reserve = com.gamergaming.villagerguardgunners.api.GuardAmmoAccess.getAmmoHandler(guard);
        reserve.clear();
        h.assertTrue(com.gamergaming.villagerguardgunners.armory.resupply.GuardArmoryResupplyController.completelyDry(guard),
                "Empty physical loadout was not recognized");
        reserve.insertItem(0, com.tacz.guns.api.item.builder.AmmoItemBuilder.create()
                .setId(net.minecraft.resources.ResourceLocation.parse("tacz:762x39")).build(), false);
        h.assertFalse(com.gamergaming.villagerguardgunners.armory.resupply.GuardArmoryResupplyController.completelyDry(guard),
                "Reloadable primary authorized emergency resupply");
        reserve.clear();
        var sidearm = com.tacz.guns.api.item.builder.GunItemBuilder.create()
                .setId(net.minecraft.resources.ResourceLocation.parse("tacz:glock_17"))
                .setFireMode(com.tacz.guns.api.item.gun.FireMode.SEMI).setAmmoCount(1).setAmmoInBarrel(true).build();
        h.assertTrue(com.gamergaming.villagerguardgunners.api.GuardSidearmAccess.getConfiguredSidearmInventory(guard)
                .insertItem(0, sidearm, false).isEmpty(), "Sidearm fixture was not installed");
        h.assertFalse(com.gamergaming.villagerguardgunners.armory.resupply.GuardArmoryResupplyController.completelyDry(guard),
                "Usable sidearm authorized emergency resupply");
        guard.discard(); h.succeed();
    }

    @GameTest(batch = "route_shared_ground", templateNamespace = "minecraft", template = ARENA)
    public static void squadPatrolCombatAnchorRejectsStaleRouteRevisions(GameTestHelper h) {
        Guard guard = guard(h); guard.setNoAi(false);
        var config = com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS;
        boolean enabled = config.squads.patrolRouteExecution.get();
        var registry = com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData.get(h.getLevel().getServer());
        UUID squad = UUID.randomUUID();
        try {
            config.squads.patrolRouteExecution.accept(true);
            registry.register(squad, "Duty boundary fixture", null, h.getLevel().getGameTime()).orElseThrow();
            var points = List.of(h.absolutePos(new BlockPos(10, 1, 12)), h.absolutePos(new BlockPos(30, 1, 12)));
            registry.replacePatrolRoute(squad, h.getLevel().dimension().location(), points, h.getLevel().getGameTime());
            com.gamergaming.villagerguardgunners.api.GuardSquadAccess.setMembership(guard,
                    com.gamergaming.villagerguardgunners.squad.SquadMembership.assigned(squad));
            GuardCombatDuty.capture(guard);
            var duty = GuardCombatDuty.current(guard);
            h.assertTrue(duty != null && duty.source() == GuardCombatDuty.Source.PATROL_ROUTE
                    && !duty.bounds().contains(guard.position().add(25, 0, 0)), "Patrol route lost its local combat boundary");
            registry.replacePatrolRoute(squad, h.getLevel().dimension().location(), List.of(points.get(1), points.get(0)), h.getLevel().getGameTime());
            h.assertTrue(GuardCombatDuty.current(guard) == null, "Edited route retained the old combat anchor");
        } finally { registry.remove(squad); config.squads.patrolRouteExecution.accept(enabled); guard.discard(); }
        h.succeed();
    }

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
