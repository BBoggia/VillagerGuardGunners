package com.gamergaming.villagerguardgunners.gametest.combat;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.api.GuardRoleAccess;
import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmGameTestAccess;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.google.gson.GsonBuilder;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceLocation;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Opt-in observation and future acceptance scenarios. Neither mode requires the
 * current AI defects to remain. Baseline mode asserts fixture validity and records
 * outcomes; acceptance mode additionally enforces the planned movement contracts.
 * All guard goals and target selectors run normally. No manual goal ticks,
 * repeated teleporting, native timestamp rewrites, or shared config mutations.
 */
@GameTestHolder(VillagerGuardGunners.MODID + "_combat_baseline")
@PrefixGameTestTemplate(false)
public final class CombatIntelligenceGameTests {
    private static final String ARENA = "villagerguardgunners/gametest_arena";
    private static final ResourceLocation RIFLE = id("ak47");
    private static final ResourceLocation PISTOL = id("glock_17");
    private static final List<Fixture> OPEN = new ArrayList<>();

    private CombatIntelligenceGameTests() {}

    @GameTest(template = ARENA, batch = "combat_dry", timeoutTicks = 240)
    public static void dryGunMovement(GameTestHelper helper) {
        new Fixture(helper, Scenario.DRY).run();
    }

    @GameTest(template = ARENA, batch = "combat_sidearm", timeoutTicks = 240)
    public static void sidearmTargetReplacement(GameTestHelper helper) {
        new Fixture(helper, Scenario.SIDEARM).run();
    }

    @GameTest(template = ARENA, batch = "combat_reload", timeoutTicks = 240)
    public static void reloadUnderClosingPressure(GameTestHelper helper) {
        new Fixture(helper, Scenario.RELOAD).run();
    }

    @GameTest(template = ARENA, batch = "combat_wall", timeoutTicks = 240)
    public static void wallRetreatWithSideOpening(GameTestHelper helper) {
        new Fixture(helper, Scenario.WALL).run();
    }

    @AfterBatch(batch = "combat_dry")
    public static void afterDry(ServerLevel level) { closeFixtures(); }
    @AfterBatch(batch = "combat_sidearm")
    public static void afterSidearm(ServerLevel level) { closeFixtures(); }
    @AfterBatch(batch = "combat_reload")
    public static void afterReload(ServerLevel level) { closeFixtures(); }
    @AfterBatch(batch = "combat_wall")
    public static void afterWall(ServerLevel level) { closeFixtures(); }

    private static void closeFixtures() {
        for (Fixture fixture : List.copyOf(OPEN)) fixture.close();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tacz", path);
    }

    private static ItemStack gun(ResourceLocation id, FireMode mode, int rounds) {
        return GunItemBuilder.create().setId(id).setFireMode(mode)
                .setAmmoCount(rounds).setAmmoInBarrel(false).build();
    }

    private enum Scenario { DRY, SIDEARM, RELOAD, WALL }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final Scenario scenario;
        private final Guard guard;
        private final IGunOperator operator;
        private final List<Husk> enemies = new ArrayList<>();
        private final Map<java.util.UUID, Vec3> enemyPositions = new LinkedHashMap<>();
        private final List<Map<String, Object>> samples = new ArrayList<>();
        private final Vec3 origin;
        private final Vec3 start;
        private final int originalGoalCount;
        private final Map<String, Object> metrics = new LinkedHashMap<>();
        private final Map<String, Boolean> contracts = new LinkedHashMap<>();
        private final Map<String, Object> settings = new LinkedHashMap<>();
        private final long beganNanos = System.nanoTime();
        private Vec3 previous;
        private long nextFrame = System.nanoTime();
        private int ticks;
        private int meleeTicks;
        private int gunGoalTicks;
        private int reloadTicks;
        private int sidearmFirstTick = -1;
        private int replacedAt = -1;
        private int emptyPrimaryAfterReplacement;
        private int advancedDryTicks;
        private int firstDryTick = -1;
        private double reloadAdvance;
        private double maxAdvance;
        private double enemyTravel;
        private boolean gunDataObserved;
        private boolean reloadCompleted;
        private boolean previousReloading;
        private boolean firstReloadEnded;
        private double firstReloadWithdrawal;
        private int vulnerableReloadTicks;
        private int firstReloadCloseExposureTicks;
        private int safeWallTicks;
        private int enemyNavigationTicks;
        private boolean closed;
        private boolean reported;

        Fixture(GameTestHelper helper, Scenario scenario) {
            this.helper = helper;
            this.scenario = scenario;
            String mode = System.getProperty("villagerguardgunners.combatTestMode", "off");
            helper.assertTrue(mode.equals("baseline") || mode.equals("acceptance"),
                    "Use the dedicated guardIntelligenceTestMode Gradle option");
            helper.assertTrue(OPEN.isEmpty(), "Combat fixtures must run in separate batches");
            helper.assertTrue(ModConfigs.GUARD_GUNNERS.enabled.get(), "Combat must be enabled");
            settings.put("engagement", ModConfigs.GUARD_GUNNERS.engagement.mode.get().name());
            settings.put("decisionInterval", ModConfigs.GUARD_GUNNERS.performance.movementDecisionIntervalTicks.get());
            settings.put("pathInterval", ModConfigs.GUARD_GUNNERS.performance.pathRecalculationIntervalTicks.get());
            settings.put("visibilityInterval", ModConfigs.GUARD_GUNNERS.performance.visibilitySampleIntervalTicks.get());
            settings.put("approachSpeed", ModConfigs.GUARD_GUNNERS.movement.approachSpeed.get());
            settings.put("retreatSpeed", ModConfigs.GUARD_GUNNERS.movement.retreatSpeed.get());
            BlockPos marker = helper.absolutePos(BlockPos.ZERO);
            // Underground cave ceilings can shed gravel after setup and invalidate contact assumptions.
            origin = new Vec3(marker.getX(), helper.getLevel().getMaxBuildHeight() - 8, marker.getZ());
            settings.put("arenaFloorY", origin.y);
            // Explicit enclosed test floor, with room for a future withdrawal in every direction.
            for (int x = 0; x <= 48; x++) {
                for (int z = 0; z <= 32; z++) {
                    // Preserve the GameTest structure marker at the origin.
                    if (x != 0 || z != 0) setArenaBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    for (int y = 1; y < 8; y++) {
                        setArenaBlock(new BlockPos(x, y, z),
                                (x == 0 || x == 48 || z == 0 || z == 32) && y <= 5 ? Blocks.BARRIER : Blocks.AIR);
                    }
                }
            }
            if (scenario == Scenario.WALL) {
                for (int z = 10; z <= 22; z++) {
                    for (int y = 1; y <= 3; y++) setArenaBlock(new BlockPos(19, y, z), Blocks.STONE);
                }
            }
            guard = helper.spawn(GuardEntityType.GUARD.get(), new BlockPos(20, 1, 16));
            guard.setPos(origin.add(20.5, 1, 16.5));
            operator = IGunOperator.fromLivingEntity(guard);
            OPEN.add(this); // Batch cleanup also handles setup/assertion/timeout failure.
            Objects.requireNonNull(guard.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(10_000);
            guard.setHealth(10_000);
            guard.setPersistenceRequired();
            for (EquipmentSlot slot : EquipmentSlot.values()) guard.setItemSlot(slot, ItemStack.EMPTY);
            GuardAmmoAccess.getAmmoHandler(guard).clear();
            GuardSidearmGameTestAccess.getStorage(guard).setStackInSlot(0, ItemStack.EMPTY);
            GuardSidearmGameTestAccess.getStorage(guard).setStackInSlot(1, ItemStack.EMPTY);
            GuardRoleAccess.setRoleId(guard, null);
            GuardOrders orders = GuardOrders.DEFAULT
                    .withEngagement(GuardOrders.EngagementOrder.HOSTILE_MOBS_ONLY)
                    .withMovement(GuardOrders.MovementOrder.TACTICAL)
                    .withTactics(scenario == Scenario.RELOAD
                            ? GuardOrders.TacticalOrder.BREACHER : GuardOrders.TacticalOrder.GENERALIST);
            GuardOrdersAccess.setOrders(guard, orders);
            settings.put("orders", orders.toString());
            guard.setItemSlot(EquipmentSlot.MAINHAND,
                    gun(scenario == Scenario.DRY ? PISTOL : RIFLE,
                            scenario == Scenario.DRY ? FireMode.SEMI : FireMode.AUTO,
                            scenario == Scenario.WALL ? 30 : scenario == Scenario.DRY ? 1 : 0));
            if (scenario == Scenario.SIDEARM) {
                GuardSidearmGameTestAccess.getStorage(guard).setStackInSlot(0, gun(PISTOL, FireMode.SEMI, 17));
            }
            if (scenario == Scenario.RELOAD || scenario == Scenario.WALL) {
                GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0,
                        AmmoItemBuilder.create().setId(id("762x39")).setCount(64).build());
            }
            originalGoalCount = guard.goalSelector.getAvailableGoals().size();
            helper.assertTrue(guard.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.getGoal() instanceof GuardGunCombatGoal), "Gun goal missing");
            helper.assertTrue(guard.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.getGoal() instanceof MeleeAttackGoal), "Upstream melee goal missing");
            Husk target = enemy(scenario == Scenario.WALL ? 23 : scenario == Scenario.RELOAD ? 30 : 32,
                    16, scenario == Scenario.RELOAD ? .30 : .23);
            guard.setTarget(target);
            if (scenario == Scenario.RELOAD) {
                enemy(30, 14, .30);
                enemy(30, 18, .30);
            }
            start = guard.position();
            previous = start;
        }

        private void setArenaBlock(BlockPos relative, net.minecraft.world.level.block.Block block) {
            helper.getLevel().setBlockAndUpdate(BlockPos.containing(origin).offset(relative), block.defaultBlockState());
        }

        private Husk enemy(int x, int z, double speed) {
            Husk enemy = helper.spawn(EntityType.HUSK, new BlockPos(x, 1, z));
            enemy.setPos(origin.add(x + .5, 1, z + .5));
            enemy.setBaby(false);
            Objects.requireNonNull(enemy.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(10_000);
            enemy.setHealth(10_000);
            enemy.setPersistenceRequired();
            Objects.requireNonNull(enemy.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(speed);
            enemy.setTarget(guard);
            enemies.add(enemy);
            enemyPositions.put(enemy.getUUID(), enemy.position());
            return enemy;
        }

        void run() {
            helper.onEachTick(() -> {
                if (closed) return;
                try {
                    pace();
                    sample();
                    if (ticks >= 180) finish();
                } catch (RuntimeException | AssertionError failure) {
                    if (!reported) writeReport(false, failure.toString());
                    close();
                    throw failure;
                }
            });
        }

        /** Pace this isolated test to real 20 TPS; do not alter TaCZ timestamps or reload state. */
        private void pace() {
            long remaining;
            while ((remaining = nextFrame - System.nanoTime()) > 0) {
                LockSupport.parkNanos(remaining);
                if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Combat test interrupted");
            }
            nextFrame = System.nanoTime() + 50_000_000L;
        }

        private void sample() {
            ticks++;
            boolean reloading = operator.getSynReloadState().getStateType().isReloading();
            if (scenario == Scenario.WALL && ticks == 5) {
                // Wait for ordinary physics to put the actor on the floor before asking navigation.
                // Existence proof only: never install this path or steer the guard along it.
                var path = guard.getNavigation().createPath(BlockPos.containing(origin.add(16, 1, 8)), 0);
                helper.assertTrue(path != null && path.canReach(), "Side opening must have a reachable escape path");
            }
            boolean melee = guard.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.isRunning() && g.getGoal() instanceof MeleeAttackGoal);
            boolean gunGoal = guard.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.isRunning() && g.getGoal() instanceof GuardGunCombatGoal);
            if (melee) meleeTicks++;
            if (gunGoal) gunGoalTicks++;
            if (reloading) reloadTicks++;
            ItemStack stack = guard.getMainHandItem();
            int rounds = stack.getItem() instanceof IGun gun ? gun.getCurrentAmmoCount(stack) : -1;
            boolean chambered = stack.getItem() instanceof IGun gun && gun.hasBulletInBarrel(stack);
            if (rounds == 0 && !chambered && firstDryTick < 0) firstDryTick = ticks;
            var state = GuardSidearmAccess.getState(guard);
            if (state == SidearmState.SIDEARM_ACTIVE && sidearmFirstTick < 0) sidearmFirstTick = ticks;
            if (scenario == Scenario.SIDEARM && replacedAt < 0 && sidearmFirstTick > 0
                    && ticks - sidearmFirstTick >= 35) {
                helper.assertTrue(rounds > 0 || chambered, "Sidearm must still have a usable round at target replacement");
                Husk replacement = enemy(32, 18, .23);
                guard.setTarget(replacement); // One deliberate target replacement, never a per-tick override.
                enemies.get(0).discard();
                replacedAt = ticks;
            }
            ItemStack stored = GuardSidearmGameTestAccess.getStorage(guard).getStackInSlot(0);
            boolean usableStoredBackup = stored.getItem() instanceof IGun backup
                    && (backup.getCurrentAmmoCount(stored) > 0 || backup.hasBulletInBarrel(stored));
            if (replacedAt >= 0 && ticks > replacedAt && state == SidearmState.PRIMARY_ACTIVE
                    && rounds == 0 && !chambered && usableStoredBackup) emptyPrimaryAfterReplacement++;
            double dx = guard.getX() - previous.x;
            double nearest = enemies.stream().filter(Entity::isAlive)
                    .mapToDouble(guard::distanceTo).min().orElse(100);
            if (scenario == Scenario.DRY && ticks > 5 && dx > .02) advancedDryTicks++;
            if (reloading && nearest <= 16 && dx > 0) reloadAdvance += dx;
            if (!reloading && reloadTicks > 0 && (rounds > 0 || chambered)) reloadCompleted = true;
            if (previousReloading && !reloading) firstReloadEnded = true;
            if (reloading && !firstReloadEnded) {
                firstReloadWithdrawal = Math.max(firstReloadWithdrawal, start.x - guard.getX());
                if (nearest <= 8) vulnerableReloadTicks++;
                if (nearest <= 6) firstReloadCloseExposureTicks++;
            }
            previousReloading = reloading;
            if (guard.getX() - origin.x < 18 && nearest >= 6) safeWallTicks++;
            maxAdvance = Math.max(maxAdvance, guard.getX() - start.x);
            for (Husk enemy : enemies) {
                if (!enemy.isAlive()) continue;
                Vec3 last = enemyPositions.put(enemy.getUUID(), enemy.position());
                if (last != null) enemyTravel += enemy.position().distanceTo(last);
                if (!enemy.getNavigation().isDone()) enemyNavigationTicks++;
            }
            // Cheap synchronized state and actual positions; avoid per-tick diagnostic inventory/script scans.
            gunDataObserved |= operator.getSynDrawCoolDown() > 0 || reloading || operator.getSynIsAiming();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tick", ticks);
            row.put("position", position(guard));
            row.put("enemies", enemies.stream().filter(Entity::isAlive).map(this::position).toList());
            row.put("meleeGoal", melee);
            row.put("gunGoal", gunGoal);
            row.put("reloading", reloading);
            row.put("loadedRounds", rounds);
            row.put("chambered", chambered);
            row.put("sidearmState", state.name());
            row.put("nearestEnemy", nearest);
            row.put("navigationDone", guard.getNavigation().isDone());
            row.put("health", guard.getHealth());
            row.put("target", guard.getTarget() == null ? null : guard.getTarget().getUUID().toString());
            row.put("activeGoals", guard.goalSelector.getRunningGoals()
                    .map(g -> g.getGoal().getClass().getSimpleName()).toList());
            guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                    .filter(GuardGunCombatGoal.class::isInstance).map(GuardGunCombatGoal.class::cast)
                    .findFirst().ifPresent(goal -> {
                        var route = goal.tacticalNavigation();
                        row.put("intent", goal.tacticalController().decision().intent().name());
                        row.put("intentReason", goal.tacticalController().decision().reason().name());
                        row.put("continuingCombat", goal.tacticalController().hasContinuation());
                        row.put("observedThreats", goal.tacticalController().assessment().threats());
                        if (guard.getTarget() != null) {
                            var hit = guard.level().clip(new net.minecraft.world.level.ClipContext(
                                    guard.getEyePosition(), guard.getTarget().getEyePosition(),
                                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                    net.minecraft.world.level.ClipContext.Fluid.NONE, guard));
                            row.put("sightObstruction", hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                                    ? null : guard.level().getBlockState(hit.getBlockPos()).toString());
                        }

                        row.put("routeStatus", route.status());
                        row.put("routeAttempts", route.pathAttempts());
                        row.put("routeMoving", route.isMoving());
                        row.put("routeDestination", route.destination() == null ? null : route.destination().toShortString());
                    });
            samples.add(row);
            previous = guard.position();
        }

        private List<Double> position(Entity entity) {
            return List.of(entity.getX() - origin.x, entity.getY() - origin.y, entity.getZ() - origin.z);
        }

        private void finish() {
            helper.assertTrue(guard.isAlive() && !guard.isNoAi(), "Guard must remain alive with normal AI");
            helper.assertTrue(guard.goalSelector.getAvailableGoals().size() == originalGoalCount,
                    "Fixture must retain the registered goals");
            helper.assertTrue(gunGoalTicks > 0 && gunDataObserved, "Native gun behavior was never exercised");
            helper.assertTrue(enemyTravel > .25 && enemyNavigationTicks > 0,
                    "Enemy AI never produced observable navigation/movement");
            if (scenario == Scenario.DRY) helper.assertTrue(firstDryTick > 0,
                    "The last-round fixture never exhausted its ammo");
            if (scenario == Scenario.SIDEARM) {
                helper.assertTrue(replacedAt > 0, "Usable sidearm activation and target replacement never occurred");
                var storage = GuardSidearmGameTestAccess.getStorage(guard);
                int weapons = (guard.getMainHandItem().isEmpty() ? 0 : 1)
                        + (storage.getStackInSlot(0).isEmpty() ? 0 : 1)
                        + (storage.getStackInSlot(1).isEmpty() ? 0 : 1);
                helper.assertTrue(weapons == 2, "The fixture lost or duplicated a weapon stack");
            }
            if (scenario == Scenario.RELOAD) helper.assertTrue(reloadTicks >= 10 && reloadCompleted,
                    "Fixture must observe a real native reload through completion");
            switch (scenario) {
                case DRY -> {
                    contracts.put("noMeleePursuit", meleeTicks == 0);
                    contracts.put("noOffensiveAdvance", maxAdvance <= .75);
                    contracts.put("purposefulWithdrawal", guard.position().distanceTo(start) >= 2
                            && enemies.stream().filter(Entity::isAlive).allMatch(e -> guard.distanceTo(e) >= 8));
                }
                case SIDEARM -> contracts.put("keepUsableSidearmAcrossTargetReplacement", emptyPrimaryAfterReplacement == 0);
                case RELOAD -> {
                    contracts.put("noAdvanceIntoClosingGroupWhileReloading", reloadAdvance <= .75);
                    contracts.put("createSeparationDuringVulnerableReload",
                            firstReloadWithdrawal >= 1 && firstReloadCloseExposureTicks <= 5);
                }
                case WALL -> contracts.put("reachSafeSideOfWall", safeWallTicks >= 10);
            }
            writeReport(true, null);
            close();
            if (System.getProperty("villagerguardgunners.combatTestMode").equals("acceptance")) {
                helper.assertTrue(contracts.values().stream().allMatch(Boolean::booleanValue),
                        "Planned combat behavior is not implemented: " + contracts);
            }
            helper.succeed();
        }

        private void writeReport(boolean fixtureValid, String failure) {
            metrics.put("samples", ticks);
            metrics.put("meleeGoalTicks", meleeTicks);
            metrics.put("gunGoalTicks", gunGoalTicks);
            metrics.put("reloadTicks", reloadTicks);
            metrics.put("reloadCompleted", reloadCompleted);
            metrics.put("sidearmFirstTick", sidearmFirstTick);
            metrics.put("targetReplacedAt", replacedAt);
            metrics.put("emptyPrimaryTicksAfterReplacement", emptyPrimaryAfterReplacement);
            metrics.put("dryAdvanceTicks", advancedDryTicks);
            metrics.put("firstDryTick", firstDryTick);
            metrics.put("maximumForwardDisplacement", maxAdvance);
            metrics.put("forwardTravelDuringReload", reloadAdvance);
            metrics.put("enemyTravel", enemyTravel);
            metrics.put("enemyNavigationTicks", enemyNavigationTicks);
            metrics.put("firstReloadWithdrawal", firstReloadWithdrawal);
            metrics.put("vulnerableReloadTicks", vulnerableReloadTicks);
            metrics.put("firstReloadCloseExposureTicks", firstReloadCloseExposureTicks);
            metrics.put("safeWallTicks", safeWallTicks);
            metrics.put("wallClockSeconds", (System.nanoTime() - beganNanos) / 1_000_000_000.0);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("schema", 1);
            report.put("scenario", scenario.name().toLowerCase(java.util.Locale.ROOT));
            report.put("mode", System.getProperty("villagerguardgunners.combatTestMode"));
            report.put("fixtureValid", fixtureValid);
            report.put("fixtureFailure", failure);
            report.put("settings", settings);
            report.put("limitations", List.of("Actors have 10000 health to preserve the observation window; this is not survival evidence.",
                    "Husks use explicit movement speeds; all actors retain AI and ordinary physics.",
                    "A test-only wall-clock pacer permits native TaCZ actions without timestamp rewrites."));
            report.put("metrics", metrics);
            report.put("acceptance", contracts);
            report.put("samples", samples);
            try {
                Path directory = Path.of(System.getProperty("villagerguardgunners.combatReportDir"));
                Files.createDirectories(directory);
                Files.writeString(directory.resolve(scenario.name().toLowerCase(java.util.Locale.ROOT) + ".json"),
                        new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report) + "\n");
                reported = true;
            } catch (IOException failureToWrite) {
                throw new IllegalStateException("Cannot retain combat observation", failureToWrite);
            }
        }

        private void close() {
            if (closed) return;
            if (!reported) writeReport(false, "Fixture ended before its observation window completed");
            closed = true;
            guard.discard();
            enemies.forEach(Entity::discard);
            // Remove this arena's projectiles/items as well as its actors before the next batch.
            OPEN.remove(this);
            helper.getLevel().getEntitiesOfClass(Entity.class,
                    new AABB(origin, origin.add(49, 6, 33))).forEach(Entity::discard);
        }
    }
}
