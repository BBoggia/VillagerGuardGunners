package com.gamergaming.villagerguardgunners.gametest.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.ai.tactics.*;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class TargetIntelligenceGameTests {
    private static final String ARENA = "villagerguardgunners/gametest_arena";
    private static final List<Entity> ACTORS = new ArrayList<>();

    @GameTest(batch = "target_intelligence", templateNamespace = "minecraft", template = ARENA)
    public static void urgentVillagerThreatWinsButOrdersAndBlacklistRemainAuthoritative(GameTestHelper h) {
        Guard guard = guard(h);
        var old = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(36, 1, 12));
        var urgent = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(30, 1, 18));
        var villager = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(31, 1, 18));
        ACTORS.addAll(List.of(old, urgent, villager));
        urgent.setTarget(villager); guard.setTarget(old);
        var tactics = new GuardTacticalController(guard); tactics.observe(old, true);
        h.assertTrue(new GuardTargetRanking(guard).choose(tactics, old, null, 0, 30, false) == urgent,
                "A visible attacker threatening a villager did not outrank the old idle target");
        long now = h.getLevel().getGameTime();
        var focus = SquadCommand.focusTarget(UUID.randomUUID(), now, now + 200, h.getLevel().dimension().location(), old.getUUID());
        h.assertTrue(new GuardTargetRanking(guard).choose(tactics, old, focus, 0, 30, false) == old,
                "Ranking overrode player Focus Target");
        GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT.withEngagement(GuardOrders.EngagementOrder.HOLD_FIRE));
        h.assertTrue(new GuardTargetRanking(guard).choose(tactics, old, null, 0, 30, false) == old,
                "Hold Fire allowed autonomous acquisition");
        GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT.withEngagement(GuardOrders.EngagementOrder.DEFENSIVE));
        h.assertTrue(new GuardTargetRanking(guard).choose(tactics, old, null, 0, 30, false) == old,
                "Defensive permission was inferred from intent without an actual attack");
        GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT);
        var blacklist = tallestegg.guardvillagers.configuration.GuardConfig.MobBlackList;
        try {
            tallestegg.guardvillagers.configuration.GuardConfig.MobBlackList = List.of("minecraft:zombie");
            h.assertTrue(new GuardTargetRanking(guard).choose(tactics, old, null, 0, 30, false) == old,
                    "Ranking bypassed upstream canAttack/blacklist");
        } finally { tallestegg.guardvillagers.configuration.GuardConfig.MobBlackList = blacklist; }
        h.succeed();
    }

    @GameTest(batch = "target_intelligence", templateNamespace = "minecraft", template = ARENA)
    public static void targetReplacementAndRejectedProposalPreservePatrolOrigin(GameTestHelper h) throws ReflectiveOperationException {
        guard(h).discard();
        Guard guard = armed(h, 20, 12);
        guard.setNoAi(true); guard.setNoGravity(true); guard.setOnGround(true);
        GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.PATROL_BOUND));
        var old = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(36, 1, 12));
        var urgent = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(30, 1, 18));
        var villager = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(31, 1, 18));
        ACTORS.addAll(List.of(old, urgent, villager)); urgent.setTarget(villager); guard.setTarget(old);
        var goal = goal(guard); goal.start();
        var nav = goal.tacticalNavigation();
        nav.tick(goal.tacticalController(), null, 8, () -> false, .75, false, false);
        // Read the original anchor directly: changing guard position must not silently rebase it.
        var field = TacticalNavigationController.class.getDeclaredField("engagementAnchor");
        field.setAccessible(true); Vec3 original = (Vec3) field.get(nav);
        h.assertTrue(original != null, "Patrol engagement did not capture an origin");
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingChangeTargetEvent> veto = event -> {
            if (event.getEntity() == guard) event.setCanceled(true);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(veto);
        try {
            goal.tick();
            h.assertTrue(guard.getTarget() == old && goal.targetRankingStatus().equals("TARGET_CHANGE_REJECTED"),
                    "Fixture did not reject a higher-priority target proposal");
            h.assertTrue(original.equals(field.get(nav)), "Rejected proposal discarded combat navigation state");
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto); }
        guard.setPos(guard.position().add(-8, 0, 0)); guard.setTarget(urgent); goal.tick();
        h.assertTrue(original.equals(field.get(nav)), "Accepted target replacement moved the patrol origin");
        goal.stop(); h.succeed();
    }

    @GameTest(batch = "target_intelligence", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 65)
    public static void firingPositionLeasesSeparateGuardsAndExpire(GameTestHelper h) {
        Guard first = guard(h);
        Guard second = h.spawnWithNoFreeWill(GuardEntityType.GUARD.get(), new BlockPos(20, 1, 12));
        second.setNoAi(true); second.setNoGravity(true); second.setOnGround(true); ACTORS.add(second);
        var target = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(34, 1, 12));
        var villager = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(27, 1, 12));
        ACTORS.addAll(List.of(target, villager));
        var request = new TacticalFiringLane.Request(target, 0, 18, 30, .5, 0);
        var planner = new TacticalPositionPlanner(first);
        var plan = planner.plan(List.of(), TacticalAssignmentBounds.UNBOUNDED, Set.of(), true, List.of(), request);
        h.assertTrue(plan.path() != null, "Missing initial lane: " + plan.reason());
        Vec3 chosen = planner.standingPosition(plan.destination());
        h.assertTrue(FiringPositionReservations.reserve(first, chosen), "Initial reservation failed");
        h.assertFalse(FiringPositionReservations.reserve(second, chosen.add(.5, 0, 0)), "Guards reserved overlapping positions");
        var alternate = new TacticalPositionPlanner(second).plan(List.of(), TacticalAssignmentBounds.UNBOUNDED,
                Set.of(), true, List.of(), request);
        h.assertTrue(alternate.path() != null && Vec3.atBottomCenterOf(alternate.destination()).distanceToSqr(chosen) >= 4,
                "Second guard chose the reserved firing position: " + alternate.reason());
        h.runAtTickTime(42, () -> {
            h.assertTrue(FiringPositionReservations.reserve(second, chosen), "Abandoned reservation did not expire");
            FiringPositionReservations.release(second); h.succeed();
        });
    }

    @GameTest(batch = "target_intelligence", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 150)
    public static void investigationUsesFrozenSightingAndRespectsOrdersOwnershipAndBudget(GameTestHelper h) {
        Guard guard = guard(h);
        var target = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(34, 1, 12)); ACTORS.add(target);
        var goal = new GuardGunCombatGoal(guard); guard.goalSelector.addGoal(2, goal);
        var tactics = goal.tacticalController(); tactics.observe(target, true);
        var search = goal.investigation(); search.observe(target, tactics.tracker());
        Vec3 known = target.position();
        for (int y = 1; y <= 3; y++) for (int z = 11; z <= 13; z++) h.setBlock(new BlockPos(26, y, z), Blocks.STONE);
        target.setPos(h.absoluteVec(new Vec3(36.5, 1, 22.5)));
        h.assertTrue(search.canInvestigate(target, false, true, WeaponReadiness.READY, null), "Lost visible contact could not investigate");
        search.tick(tactics, null, () -> true, .75);
        h.assertTrue(search.isMoving(), "No bounded peek path: " + search.status());
        h.assertTrue(search.lastSeenPosition().equals(known) && search.attempts() == 1,
                "Peek tracked the hidden enemy or exceeded the shared slot");
        h.assertFalse(search.canInvestigate(target, false, false, WeaponReadiness.READY, null), "Denied target authorized investigation");
        h.assertFalse(search.canInvestigate(target, false, true, WeaponReadiness.RELOADING, null), "Reload authorized investigation");
        guard.getNavigation().recomputePath();
        h.assertFalse(search.ownsPath(), "Native recomputation bypassed peek screening");
        search.tick(tactics, null, () -> { throw new AssertionError("Invalidation bypassed backoff"); }, .75);
        h.runAtTickTime(22, () -> {
            search.tick(tactics, null, () -> true, .75);
            h.assertTrue(search.attempts() == 2, "Second bounded attempt was not available");
            GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION));
            h.assertFalse(search.canInvestigate(target, false, true, WeaponReadiness.READY, null), "Hold Position failed to revoke investigation");
            search.cancel();
            h.assertFalse(search.ownsPath(), "Revoked search kept navigation ownership");
            GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT);
            h.assertFalse(search.canInvestigate(target, false, true, WeaponReadiness.READY, null), "Failed search bypassed cooldown");
        });
        h.runAtTickTime(125, () -> {
            h.assertFalse(search.canInvestigate(target, false, true, WeaponReadiness.READY, null), "Expired sighting restarted investigation");
            h.succeed();
        });
    }

    @GameTest(batch = "target_live_corner", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 260)
    public static void liveGuardPeeksAroundCornerAndResumesNativeFire(GameTestHelper h) {
        guard(h).discard();
        Guard guard = armed(h, 20, 12);
        // Establish the first shot from a fixed position. An earlier approach or
        // firing-lane maneuver can otherwise round the new wall before a peek starts.
        GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION));
        var target = h.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(34, 1, 12));
        // Keep the target eligible for vanilla target selection while allowing repeated real shots.
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);
        target.setHealth(10000); ACTORS.add(target); guard.setTarget(target);
        var goal = goal(guard);
        long[] frame = {System.nanoTime()}; int[] stage = {0}, ammoAtWall = {0};
        h.onEachTick(() -> {
            pace(frame);
            int ammo = ammo(guard);
            var safety = com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess.controller(guard).currentShotSafety(target);
            if (stage[0] == 0 && ammo < 17 && safety.clear()) {
                ammoAtWall[0] = ammo;
                // Native movement before the first shot can shift the firing line.
                // Center the obstruction on the actual ray, then verify this fixture lost sight.
                double wallX = h.absolutePos(new BlockPos(26, 1, 0)).getX() + .5;
                double fraction = (wallX - guard.getX()) / (target.getX() - guard.getX());
                int centerZ = net.minecraft.util.Mth.floor(guard.getZ() + fraction * (target.getZ() - guard.getZ()))
                        - h.absolutePos(BlockPos.ZERO).getZ();
                // Keep a short peek available from the stationary start.
                for (int y = 1; y <= 3; y++) for (int z = centerZ - 1; z <= centerZ + 1; z++)
                    h.setBlock(new BlockPos(26, y, z), Blocks.STONE);
                h.assertFalse(guard.hasLineOfSight(target), "Corner fixture failed to obstruct the current firing line");
                GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.TACTICAL));
                stage[0] = 1;
            } else if (stage[0] == 1 && goal.investigation().isMoving()) {
                stage[0] = 2;
                h.assertTrue(ammo == ammoAtWall[0], "Guard fired into the obstructed lane before investigation");
            } else if (stage[0] == 2) {
                if (goal.investigation().isMoving()) h.assertTrue(ammo == ammoAtWall[0], "Guard fired while investigating");
                if (ammo < ammoAtWall[0] && safety.clear() && guard.hasLineOfSight(target)) {
                    h.assertTrue(!goal.investigation().ownsPath(), "Reacquired contact retained the search path");
                    goal.stop(); h.succeed();
                }
            }
        });
        h.runAtTickTime(250, () -> h.fail("Corner search did not restore fire: stage=" + stage[0]
                + ", search=" + goal.investigation().status() + ", attempts=" + goal.investigation().attempts()
                + ", sighting=" + goal.investigation().lastSeenPosition() + ", position=" + guard.position()
                + ", diagnostics=" + goal.captureDiagnostics()));
    }

    @GameTest(batch = "target_live_cooperation", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 260)
    public static void liveGuardsCoverSeparateVisibleThreats(GameTestHelper h) {
        guard(h).discard();
        Guard first = armed(h, 20, 10), second = armed(h, 20, 16);
        var one = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(34, 1, 10));
        var two = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(34, 1, 16));
        for (var mob : List.of(one, two)) {
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);
            mob.setHealth(10000); mob.setTarget(first); ACTORS.add(mob);
        }
        first.setTarget(one); second.setTarget(one);
        long[] frame = {System.nanoTime()}; int[] separateTicks = {0};
        boolean[] fired = {false, false};
        h.onEachTick(() -> {
            pace(frame);
            var firstNative = com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess.controller(first);
            var secondNative = com.gamergaming.villagerguardgunners.api.GuardNativeShotAccess.controller(second);
            if (first.getTarget() != null && second.getTarget() != null && first.getTarget() != second.getTarget()) {
                separateTicks[0]++;
                if (firstNative.currentShotSafety(first.getTarget()).clear() && ammo(first) < 17) fired[0] = true;
                if (secondNative.currentShotSafety(second.getTarget()).clear() && ammo(second) < 17) fired[1] = true;
            } else separateTicks[0] = 0;
            if (separateTicks[0] >= 20 && fired[0] && fired[1]) { goal(first).stop(); goal(second).stop(); h.succeed(); }
        });
        h.runAtTickTime(250, () -> h.fail("Guards did not sustain separate useful engagements: separate=" + separateTicks[0]
                + ", fired=" + java.util.Arrays.toString(fired) + ", first=" + goal(first).captureDiagnostics()
                + ", second=" + goal(second).captureDiagnostics()));
    }

    private static Guard armed(GameTestHelper h, int x, int z) {
        Guard guard = h.spawn(GuardEntityType.GUARD.get(), new BlockPos(x, 1, z));
        guard.setInvulnerable(true); ACTORS.add(guard);
        guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                com.tacz.guns.api.item.builder.GunItemBuilder.create()
                        .setId(net.minecraft.resources.ResourceLocation.parse("tacz:glock_17"))
                        .setFireMode(com.tacz.guns.api.item.gun.FireMode.SEMI).setAmmoCount(17).setAmmoInBarrel(true).build());
        return guard;
    }
    private static GuardGunCombatGoal goal(Guard guard) {
        return guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(GuardGunCombatGoal.class::isInstance).map(GuardGunCombatGoal.class::cast).findFirst().orElseThrow();
    }
    private static int ammo(Guard guard) {
        return ((com.tacz.guns.api.item.IGun) guard.getMainHandItem().getItem()).getCurrentAmmoCount(guard.getMainHandItem());
    }
    private static void pace(long[] frame) {
        long remaining;
        while ((remaining = frame[0] - System.nanoTime()) > 0) {
            java.util.concurrent.locks.LockSupport.parkNanos(remaining);
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Intelligence fixture interrupted");
        }
        frame[0] = System.nanoTime() + 50_000_000L;
    }
    @net.minecraft.gametest.framework.AfterBatch(batch = "target_live_corner")
    public static void afterCorner(ServerLevel level) { cleanup(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "target_live_cooperation")
    public static void afterCooperation(ServerLevel level) { cleanup(level); }

    private static Guard guard(GameTestHelper h) {
        for (int x = 1; x < 39; x++) for (int z = 1; z < 25; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y < 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        Guard guard = h.spawnWithNoFreeWill(GuardEntityType.GUARD.get(), new BlockPos(20, 1, 12));
        guard.setNoAi(true); guard.setNoGravity(true); guard.setOnGround(true); ACTORS.add(guard); return guard;
    }
    @net.minecraft.gametest.framework.AfterBatch(batch = "target_intelligence")
    public static void cleanup(ServerLevel level) {
        for (Entity entity : ACTORS) { if (entity instanceof Guard guard) FiringPositionReservations.release(guard); entity.discard(); }
        ACTORS.clear();
    }
}
