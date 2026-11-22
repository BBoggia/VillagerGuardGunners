package com.gamergaming.villagerguardgunners.gametest.combat;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.ai.tactics.*;
import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmGameTestAccess;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder("villagerguardgunners")
@PrefixGameTestTemplate(false)
public final class TacticalIntentGameTests {
    private static final java.util.List<Mob> ACTORS = new java.util.ArrayList<>();
    private static final String ARENA = "villagerguardgunners/gametest_arena";

    @GameTest(batch = "tactics_classification", templateNamespace = "minecraft", template = ARENA)
    public static void nearbyNeutralEntitiesAreNotGuardTargets(GameTestHelper h) {
        Guard guard = guard(h, 4, 4);
        var neutral = mob(h, EntityType.COW, 7, 4);
        var hostile = mob(h, EntityType.HUSK, 10, 4);
        var villager = mob(h, EntityType.VILLAGER, 7, 7);
        GuardThreatTracker tracker = new GuardThreatTracker(guard);
        tracker.update(null, false);
        h.assertTrue(tracker.remembered(neutral.getUUID()) == null, "GUARD_TARGETS classified an unselected neutral as hostile");
        h.assertTrue(tracker.remembered(villager.getUUID()) == null, "Protected villager became a threat");
        h.assertTrue(tracker.remembered(hostile.getUUID()) != null, "Visible hostile was not observed");
        neutral.setTarget(villager);
        tracker.clear(); tracker.update(null, false);
        h.assertTrue(tracker.remembered(neutral.getUUID()) != null
                        && tracker.remembered(neutral.getUUID()).unknownAttack(),
                "Unknown entity visibly threatening an ally was ignored");
        neutral.setTarget(null);
        guard.setLastHurtByMob(neutral);
        tracker.clear(); tracker.update(null, false);
        h.assertTrue(tracker.remembered(neutral.getUUID()) != null
                        && tracker.hasContinuation(h.getLevel().getGameTime()),
                "Recent direct attack without a selected target failed to retain combat evidence");
        neutral.discard(); hostile.discard(); villager.discard(); guard.discard(); h.succeed();
    }

    @GameTest(batch = "tactics_hidden_memory", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 100)
    public static void unseenEnemyPositionFreezesAndExpires(GameTestHelper h) {
        Guard guard = guard(h, 4, 4);
        var enemy = mob(h, EntityType.HUSK, 10, 4);
        GuardThreatTracker tracker = new GuardThreatTracker(guard);
        tracker.update(enemy, true);
        var first = tracker.remembered(enemy.getUUID());
        h.assertTrue(first != null, "Initial visible threat was not recorded");
        for (int z = -8; z <= 20; z++) for (int y = 1; y <= 7; y++) h.setBlock(new BlockPos(7, y, z), Blocks.STONE);
        enemy.setPos(enemy.getX() + 3, enemy.getY(), enemy.getZ() + 1);
        h.onEachTick(() -> {
            guard.getSensing().tick(); tracker.update(enemy, true);
            var memory = tracker.remembered(enemy.getUUID());
            if (memory != null) {
                h.assertTrue(memory.position().equals(first.position()) && memory.observedAt() == first.observedAt(),
                        "Unseen memory followed the enemy's live coordinates");
            }
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(tracker.remembered(enemy.getUUID()) == null && !tracker.hasContinuation(h.getLevel().getGameTime()),
                    "Unseen threat memory or combat ownership failed to expire");
            enemy.discard(); guard.discard(); h.succeed();
        });
    }

    @GameTest(batch = "tactics_bounded_scan", templateNamespace = "minecraft", template = ARENA)
    public static void denseThreatScanReportsIncompleteAndHonorsCaps(GameTestHelper h) {
        Guard guard = guard(h, 4, 4);
        java.util.List<Mob> crowd = new java.util.ArrayList<>();
        for (int i = 0; i < 48; i++) crowd.add(mob(h, EntityType.HUSK, 9 + i % 4, 2 + i / 4));
        GuardThreatTracker tracker = new GuardThreatTracker(guard);
        var assessment = tracker.update(crowd.get(0), true);
        h.assertFalse(assessment.complete(), "A truncated dense scan claimed complete knowledge");
        h.assertTrue(tracker.candidateCount() <= GuardThreatTracker.CANDIDATE_LIMIT
                        && tracker.sightChecks() <= GuardThreatTracker.SIGHT_LIMIT
                        && tracker.observations().size() <= ThreatMemory.CAPACITY,
                "Perception exceeded a candidate, sight, or memory bound");
        tracker.update(crowd.get(0), true);
        h.assertTrue(tracker.queryCount() == 2, "Repeated updates bypassed the perception cadence");
        crowd.forEach(Mob::discard); guard.discard(); h.succeed();
    }

    @GameTest(batch = "tactics_damage", templateNamespace = "minecraft", template = ARENA)
    public static void recentDamageChangesIntentImmediately(GameTestHelper h) {
        Guard guard = guard(h, 8, 8);
        var enemy = mob(h, EntityType.HUSK, 14, 8);
        GuardTacticalController tactics = new GuardTacticalController(guard);
        tactics.observe(enemy, true);
        var loaded = tactics.decide(enemy, true, true, WeaponReadiness.READY, null, false, 4);
        h.assertTrue(loaded.intent() == CombatIntentPolicy.Intent.ENGAGE, "Manageable loaded encounter did not engage");
        guard.setHealth(guard.getHealth() * .7F);
        tactics.observe(enemy, true);
        var injured = tactics.decide(enemy, true, true, WeaponReadiness.READY, null, false, 4);
        h.assertTrue(injured.reason() == CombatIntentPolicy.Reason.RECENT_DAMAGE,
                "Damage did not interrupt the ordinary decision cadence");
        enemy.discard(); guard.discard(); h.succeed();
    }

    @GameTest(batch = "tactics_target_gap", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 100)
    public static void targetGapKeepsBackupWithoutFiringThenSettles(GameTestHelper h) {
        Guard guard = guard(h, 4, 4);
        var enemy = mob(h, EntityType.HUSK, 14, 4);
        guard.setTarget(enemy);
        guard.setItemSlot(EquipmentSlot.MAINHAND, gun("ak47", 0));
        GuardSidearmGameTestAccess.getStorage(guard).setStackInSlot(0, gun("glock_17", 7));
        GuardGunCombatGoal goal = goal(guard); goal.start(); idle(guard); goal.tick();
        h.assertTrue(GuardSidearmAccess.getState(guard) == SidearmState.SIDEARM_ACTIVE, "Fixture did not activate backup");
        guard.setTarget(null);
        int ammo = ((IGun) guard.getMainHandItem().getItem()).getCurrentAmmoCount(guard.getMainHandItem());
        h.onEachTick(() -> { guard.getSensing().tick(); idle(guard); goal.tick(); });
        h.runAtTickTime(5, () -> {
            h.assertTrue(goal.canContinueToUse() && GuardSidearmAccess.getState(guard) == SidearmState.SIDEARM_ACTIVE,
                    "Brief target gap restored the empty primary: state=" + GuardSidearmAccess.getState(guard)
                            + ", continuation=" + goal.tacticalController().hasContinuation()
                            + ", assessment=" + goal.tacticalController().assessment() + ", native=" + goal.captureDiagnostics());
            h.assertTrue(guard.getTarget() == null
                            && ((IGun) guard.getMainHandItem().getItem()).getCurrentAmmoCount(guard.getMainHandItem()) == ammo
                            && !IGunOperator.fromLivingEntity(guard).getDataHolder().isAiming,
                    "Memory invented a target, fired, or kept an aimed pose");
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(GuardSidearmAccess.getState(guard) == SidearmState.PRIMARY_ACTIVE
                            && !goal.canContinueToUse(),
                    "An idle visible hostile extended the gap indefinitely or prevented cleanup");
            goal.stop(); guard.discard(); enemy.discard(); h.succeed();
        });
    }

    @GameTest(batch = "tactics_stale_target", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 130)
    public static void hiddenStaleTargetIsReleasedWithoutFollowingIt(GameTestHelper h) {
        Guard guard = guard(h, 4, 4);
        var enemy = mob(h, EntityType.HUSK, 12, 4);
        guard.setTarget(enemy); guard.setItemSlot(EquipmentSlot.MAINHAND, gun("glock_17", 7));
        GuardGunCombatGoal goal = goal(guard); goal.start(); idle(guard); goal.tick();
        for (int z = -8; z <= 20; z++) for (int y = 1; y <= 7; y++) h.setBlock(new BlockPos(7, y, z), Blocks.STONE);
        h.onEachTick(() -> {
            guard.getSensing().tick(); goal.tick();
            // Observe the lifecycle boundary itself: other runtime fixtures can later alert
            // nearby guards to an unrelated target through Guard Villagers' event hooks.
            if (h.getTick() >= ThreatMemory.LIFETIME_TICKS && guard.getTarget() == null) {
                h.assertTrue(!goal.canContinueToUse() && guard.getNavigation().isDone(),
                        "Expired hidden contact retained combat ownership or a chase path");
                goal.stop(); guard.discard(); enemy.discard(); h.succeed();
            }
        });
        h.runAtTickTime(115, () -> h.assertTrue(guard.getTarget() == null,
                "Hidden contact failed to expire: " + goal.tacticalController().decision()
                        + ", assessment=" + goal.tacticalController().assessment()));
    }

    @GameTest(batch = "tactics_support", templateNamespace = "minecraft", template = ARENA, timeoutTicks = 100)
    public static void supportRequiresCachedReadyShotAndIgnoresDryAlly(GameTestHelper h) {
        Guard observer = guard(h, 4, 4), ally = guard(h, 4, 8);
        var enemy = mob(h, EntityType.HUSK, 14, 8);
        ally.setTarget(enemy); ally.setItemSlot(EquipmentSlot.MAINHAND, gun("glock_17", 17));
        GuardGunCombatGoal allyGoal = goal(ally); allyGoal.start(); idle(ally);
        GuardThreatTracker tracker = new GuardThreatTracker(observer);
        long[] heldAt = {Long.MIN_VALUE};
        long[] exhaustedAt = {Long.MIN_VALUE};
        h.onEachTick(() -> {
            Vec3 delta = enemy.getEyePosition().subtract(ally.getEyePosition());
            float yaw = (float) (Math.atan2(delta.z, delta.x) * 180 / Math.PI) - 90;
            ally.setYRot(yaw); ally.setYHeadRot(yaw);
            ally.setXRot((float) -(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * 180 / Math.PI));
            ally.getSensing().tick(); allyGoal.tick();
            long now = h.getLevel().getGameTime();
            if (heldAt[0] == Long.MIN_VALUE && allyGoal.providesTacticalSupport(now, enemy)) {
                h.assertTrue(tracker.update(enemy, true).support() > 0, "Ready visible ally was not counted as support");
                com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(ally,
                        com.gamergaming.villagerguardgunners.orders.GuardOrders.DEFAULT.withEngagement(
                                com.gamergaming.villagerguardgunners.orders.GuardOrders.EngagementOrder.HOLD_FIRE));
                heldAt[0] = now;
            } else if (heldAt[0] != Long.MIN_VALUE && exhaustedAt[0] == Long.MIN_VALUE && now > heldAt[0]) {
                h.assertFalse(allyGoal.providesTacticalSupport(now, enemy),
                        "Hold-fire ally retained support from a previously safe shot");
                com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(ally,
                        com.gamergaming.villagerguardgunners.orders.GuardOrders.DEFAULT);
                ((IGun) ally.getMainHandItem().getItem()).setCurrentAmmoCount(ally.getMainHandItem(), 0);
                ((IGun) ally.getMainHandItem().getItem()).setBulletInBarrel(ally.getMainHandItem(), false);
                exhaustedAt[0] = now;
            } else if (exhaustedAt[0] != Long.MIN_VALUE && now - exhaustedAt[0] >= 20) {
                h.assertTrue(tracker.update(enemy, true).support() == 0, "Dry ally still counted as ready support");
                allyGoal.stop(); observer.discard(); ally.discard(); enemy.discard(); h.succeed();
            }
        });
        h.runAtTickTime(90, () -> h.assertTrue(exhaustedAt[0] != Long.MIN_VALUE,
                "Ready ally never published a usable shot: " + allyGoal.captureDiagnostics()));
    }

    @GameTest(batch = "tactics_orders", templateNamespace = "minecraft", template = ARENA)
    public static void holdOrderInterruptsInjuryIntentAndStopClearsMemory(GameTestHelper h) {
        Guard guard = guard(h, 4, 4);
        var enemy = mob(h, EntityType.HUSK, 12, 4);
        GuardTacticalController tactics = new GuardTacticalController(guard);
        tactics.observe(enemy, true); guard.setHealth(guard.getMaxHealth() * .2F);
        h.assertTrue(tactics.decide(enemy, true, true, WeaponReadiness.DRY, null, false, 4).intent()
                        == CombatIntentPolicy.Intent.WITHDRAW, "Injury did not select withdrawal");
        com.gamergaming.villagerguardgunners.api.GuardOrdersAccess.setOrders(guard,
                com.gamergaming.villagerguardgunners.orders.GuardOrders.DEFAULT.withMovement(
                        com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder.HOLD_POSITION));
        h.assertTrue(tactics.decide(enemy, true, true, WeaponReadiness.DRY, null, false, 4).reason()
                        == CombatIntentPolicy.Reason.MOVEMENT_ORDER,
                "Changed hold order did not interrupt commitment immediately");
        tactics.clear();
        h.assertTrue(!tactics.hasContinuation() && tactics.tracker().observations().isEmpty(),
                "Lifecycle cleanup retained tactical context");
        h.succeed();
    }

    @GameTest(batch = "tactics_exclusions", templateNamespace = "minecraft", template = ARENA)
    public static void creativeAndDeadTargetsDoNotBecomeThreatEvidence(GameTestHelper h) {
        Guard guard = guard(h, 4, 4);
        var player = h.makeMockPlayer(); player.getAbilities().instabuild = true;
        GuardThreatTracker tracker = new GuardThreatTracker(guard);
        tracker.update(player, true);
        h.assertTrue(tracker.remembered(player.getUUID()) == null, "Creative player became a threat");
        var dead = mob(h, EntityType.HUSK, 12, 4); dead.setHealth(0);
        tracker.clear(); tracker.update(dead, true);
        h.assertTrue(tracker.remembered(dead.getUUID()) == null, "Dead current target became threat evidence");
        h.succeed();
    }

    @GameTest(batch = "tactics_orders", templateNamespace = "minecraft", template = ARENA)
    public static void restrictiveInputsInterruptCachedEngagement(GameTestHelper h) {
        Guard guard = guard(h, 4, 4);
        var enemy = mob(h, EntityType.HUSK, 14, 4);
        var tactics = new GuardTacticalController(guard);
        tactics.observe(enemy, true);
        h.assertTrue(tactics.decide(enemy, true, true, WeaponReadiness.READY, null, false, 4).intent()
                == CombatIntentPolicy.Intent.ENGAGE, "Fixture did not engage");
        h.assertTrue(tactics.decide(enemy, true, false, WeaponReadiness.READY, null, false, 4).reason()
                == CombatIntentPolicy.Reason.FIRE_NOT_PERMITTED, "Revoked permission retained cached approach");
        tactics.clear(); tactics.observe(enemy, true);
        tactics.decide(enemy, true, true, WeaponReadiness.READY, null, false, 4);
        h.assertTrue(tactics.decide(enemy, true, true, WeaponReadiness.READY, null, true, 4).intent()
                == CombatIntentPolicy.Intent.REPOSITION, "Blocked shot retained cached approach");
        tactics.clear(); tactics.observe(enemy, true);
        tactics.decide(enemy, true, true, WeaponReadiness.READY, null, false, 4);
        var defend = com.gamergaming.villagerguardgunners.squad.SquadCommand.positional(
                com.gamergaming.villagerguardgunners.squad.SquadCommandType.DEFEND, java.util.UUID.randomUUID(),
                h.getLevel().getGameTime(), h.getLevel().getGameTime() + 600,
                h.getLevel().dimension().location(), guard.blockPosition().below(30), 12);
        h.assertTrue(tactics.decide(enemy, true, true, WeaponReadiness.READY, defend, false, 4).intent()
                == CombatIntentPolicy.Intent.ENGAGE, "DEFEND intent used vertical distance for a horizontal area");
        tactics.clear(); tactics.observe(enemy, true);
        tactics.decide(enemy, true, true, WeaponReadiness.READY, null, false, 4);
        for (int i = 0; i < 40; i++) mob(h, EntityType.VILLAGER, 5 + i % 5, 8 + i / 5);
        for (int i = 0; i < 12; i++) guard(h, 5 + i % 4, 4 + i / 4);
        tactics.tracker().clear(); tactics.observe(enemy, true);
        h.assertTrue(tactics.assessment().complete() && tactics.tracker().remembered(enemy.getUUID()) != null,
                "Civilians saturated the threat scan or hid the selected hostile");
        h.assertTrue(tactics.decide(enemy, true, true, WeaponReadiness.READY, null, true, 4).intent()
                == CombatIntentPolicy.Intent.REPOSITION, "Civilian density prevented a lane change");
        for (int i = 0; i < 34; i++) mob(h, EntityType.HUSK, 24 + i % 4, 12 + i / 4);
        tactics.tracker().clear(); tactics.observe(enemy, true);
        h.assertFalse(tactics.assessment().complete(), "Fixture did not exhaust perception budget");
        h.assertTrue(tactics.decide(enemy, true, true, WeaponReadiness.READY, null, false, 4).reason()
                == CombatIntentPolicy.Reason.INCOMPLETE_ASSESSMENT, "Incomplete evidence retained cached approach");
        h.succeed();
    }

    private static Guard guard(GameTestHelper h, int x, int z) {
        return mob(h, GuardEntityType.GUARD.get(), x, z);
    }
    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, int x, int z) {
        T entity = h.spawnWithNoFreeWill(type, new BlockPos(x, 1, z));
        entity.setNoAi(true); // These fixtures tick the combat adapter explicitly.
        entity.setNoGravity(true); entity.setInvulnerable(true); ACTORS.add(entity); return entity;
    }
    private static net.minecraft.world.item.ItemStack gun(String id, int ammo) {
        return GunItemBuilder.create().setId(new ResourceLocation("tacz", id))
                .setFireMode(FireMode.SEMI).setAmmoCount(ammo).setAmmoInBarrel(ammo > 0).build();
    }
    private static GuardGunCombatGoal goal(Guard guard) {
        return guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                .filter(GuardGunCombatGoal.class::isInstance).map(GuardGunCombatGoal.class::cast).findFirst()
                .orElseGet(() -> {
                    // spawnWithNoFreeWill removes goals; install the same adapter for manual fixture ticks.
                    GuardGunCombatGoal goal = new GuardGunCombatGoal(guard);
                    guard.goalSelector.addGoal(2, goal);
                    return goal;
                });
    }
    private static void idle(Guard guard) {
        var data = IGunOperator.fromLivingEntity(guard).getDataHolder();
        data.drawTimestamp = -1; data.shootTimestamp = -10_000; data.baseTimestamp = System.currentTimeMillis() - 10_000;
        com.tacz.guns.entity.sync.ModSyncedEntityData.DRAW_COOL_DOWN_KEY.setValue(guard, 0L);
        com.tacz.guns.entity.sync.ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.setValue(guard, 0L);
    }
    private static void cleanup() { ACTORS.forEach(Mob::discard); ACTORS.clear(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tactics_classification")
    public static void after_classification(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tactics_hidden_memory")
    public static void after_hidden_memory(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tactics_bounded_scan")
    public static void after_bounded_scan(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tactics_damage")
    public static void after_damage(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tactics_target_gap")
    public static void after_target_gap(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tactics_stale_target")
    public static void after_stale_target(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tactics_support")
    public static void after_support(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tactics_orders")
    public static void after_orders(net.minecraft.server.level.ServerLevel level) { cleanup(); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tactics_exclusions")
    public static void after_exclusions(net.minecraft.server.level.ServerLevel level) { cleanup(); }

}
