package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Opt-in, separately sourced development-only scalability scenarios. Run with
 * {@code ./gradlew runGameTestServer -Pphase8Performance=true}.
 *
 * <p>GameTest servers are intentionally unthrottled, while TaCZ uses wall-clock
 * timestamps. The harness advances only the public TaCZ cooldown timestamps on
 * each weapon's nominal 20 TPS cadence. Projectile creation, gun scripts,
 * safety queries, AI, events, and entity ticking still use the real native
 * implementations and are captured by JFR.</p>
 */
@GameTestHolder(VillagerGuardGunners.MODID + "_performance")
@PrefixGameTestTemplate(false)
public final class PhaseEightPerformanceGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";
    private static final int WARMUP_TICKS = 100;
    private static final int SAMPLE_TICKS = 1_000;
    private static final int FLOOR_Y = 4;
    private static final double ENTITY_Y = FLOOR_Y + 1.0D;
    private static final String REPORT_DIRECTORY_PROPERTY =
            "villagerguardgunners.phase8PerformanceReportDir";

    private PhaseEightPerformanceGameTests() {
    }

    @GameTest(batch = "phase8_00_unarmed_idle_100", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void unarmedIdleGuards100Baseline(GameTestHelper helper) {
        runScenario(helper, "unarmed-idle-baseline-100", 100, Weapon.UNARMED, Workload.IDLE);
    }

    @GameTest(batch = "phase8_01_idle_100", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void idleArmedGuards100(GameTestHelper helper) {
        runScenario(helper, "idle-armed-100", 100, Weapon.SEMI, Workload.IDLE);
    }

    @GameTest(batch = "phase8_02_semi_10", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void semiAutomaticGuards10(GameTestHelper helper) {
        runScenario(helper, "semi-10", 10, Weapon.SEMI, Workload.CLEAR_LANES);
    }

    @GameTest(batch = "phase8_03_semi_25", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void semiAutomaticGuards25(GameTestHelper helper) {
        runScenario(helper, "semi-25", 25, Weapon.SEMI, Workload.CLEAR_LANES);
    }

    @GameTest(batch = "phase8_04_semi_50", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void semiAutomaticGuards50(GameTestHelper helper) {
        runScenario(helper, "semi-50", 50, Weapon.SEMI, Workload.CLEAR_LANES);
    }

    @GameTest(batch = "phase8_05_auto_10", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void automaticGuards10(GameTestHelper helper) {
        runScenario(helper, "automatic-10", 10, Weapon.AUTOMATIC, Workload.CLEAR_LANES);
    }

    @GameTest(batch = "phase8_06_auto_25", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void automaticGuards25(GameTestHelper helper) {
        runScenario(helper, "automatic-25", 25, Weapon.AUTOMATIC, Workload.CLEAR_LANES);
    }

    @GameTest(batch = "phase8_07_auto_50", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void automaticGuards50(GameTestHelper helper) {
        runScenario(helper, "automatic-50", 50, Weapon.AUTOMATIC, Workload.CLEAR_LANES);
    }

    @GameTest(batch = "phase8_08_shotgun_10", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void automaticShotgunGuards10(GameTestHelper helper) {
        runScenario(helper, "automatic-shotgun-10", 10, Weapon.SHOTGUN, Workload.CLEAR_LANES);
    }

    @GameTest(batch = "phase8_09_explosive_10", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void explosiveGuards10(GameTestHelper helper) {
        runScenario(helper, "explosive-10", 10, Weapon.EXPLOSIVE, Workload.CLEAR_LANES);
    }

    @GameTest(batch = "phase8_10_obstacles_25", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void obstacleHeavyNavigation25(GameTestHelper helper) {
        runScenario(helper, "obstacle-navigation-25", 25, Weapon.SEMI, Workload.OBSTACLES);
    }

    @GameTest(batch = "phase8_11_friendlies_25", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void protectedFriendliesInFiringLanes25(GameTestHelper helper) {
        runScenario(helper, "protected-crossing-25", 25, Weapon.AUTOMATIC, Workload.PROTECTED_LANES);
    }

    private static void runScenario(
            GameTestHelper helper,
            String name,
            int guardCount,
            Weapon weapon,
            Workload workload) {
        List<Entity> cleanup = new ArrayList<>(guardCount * 3);
        List<IGunOperator> operators = new ArrayList<>(guardCount);
        List<ItemStack> gunStacks = new ArrayList<>(guardCount);
        List<GuardAmmoHandler> ammoHandlers = new ArrayList<>(guardCount);
        List<MovingFriendly> movingFriendlies = new ArrayList<>(guardCount);

        for (int index = 0; index < guardCount; index++) {
            int column = index / 10;
            int row = index % 10;
            double guardX = 2.5D + column * 20.0D;
            double rowSpacing = weapon == Weapon.EXPLOSIVE ? 12.0D : 4.0D;
            double guardZ = 2.5D + row * rowSpacing;
            prepareLane(helper, guardX, guardZ, workload != Workload.IDLE);
            Guard guard = helper.spawn(
                    GuardEntityType.GUARD.get(),
                    new Vec3(guardX, ENTITY_Y, guardZ));
            guard.setPersistenceRequired();
            GuardAmmoHandler ammoHandler = GuardAmmoAccess.getAmmoHandler(guard);
            ammoHandlers.add(ammoHandler);
            if (weapon != Weapon.UNARMED) {
                ItemStack gun = GunItemBuilder.create()
                        .setId(weapon.gunId)
                        .setFireMode(weapon.fireMode)
                        .setAmmoCount(weapon.ammoCount)
                        .setAmmoInBarrel(true)
                        .setHeatData(weapon.heatData)
                        .build();
                guard.setItemSlot(EquipmentSlot.MAINHAND, gun);
                gunStacks.add(gun);
                if (weapon.reloadAmmoId != null) {
                    for (int slot = 0; slot < ammoHandler.getSlots(); slot++) {
                        ammoHandler.setStackInSlot(
                                slot,
                                AmmoItemBuilder.create()
                                        .setId(weapon.reloadAmmoId)
                                        .setCount(weapon.reloadAmmoPerSlot)
                                        .build());
                    }
                }
            }
            cleanup.add(guard);
            operators.add(IGunOperator.fromLivingEntity(guard));

            if (workload != Workload.IDLE) {
                Ravager target = helper.spawnWithNoFreeWill(
                        EntityType.RAVAGER,
                        new Vec3(guardX + 12.0D, ENTITY_Y, guardZ));
                target.setInvulnerable(true);
                target.setPersistenceRequired();
                guard.setTarget(target);
                cleanup.add(target);

                if (workload == Workload.PROTECTED_LANES) {
                    Villager protectedVillager = helper.spawnWithNoFreeWill(
                            EntityType.VILLAGER,
                            new Vec3(guardX + 6.0D, ENTITY_Y, guardZ));
                    protectedVillager.setInvulnerable(true);
                    protectedVillager.setNoGravity(true);
                    protectedVillager.setPersistenceRequired();
                    movingFriendlies.add(new MovingFriendly(
                            protectedVillager,
                            protectedVillager.position()));
                    cleanup.add(protectedVillager);
                } else if (workload == Workload.OBSTACLES) {
                    for (int z = (int) guardZ - 1; z <= (int) guardZ + 1; z++) {
                        for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) {
                            helper.setBlock(
                                    new BlockPos((int) guardX + 6, y, z),
                                    Blocks.STONE);
                        }
                    }
                }
            }
        }

        MinecraftServer server = helper.getLevel().getServer();
        long[] samples = new long[SAMPLE_TICKS];
        int[] sampleCount = {0};
        long[] gcCollectionsAtStart = {0L};
        long[] usedHeapAtStart = {0L};
        int[] nativeShootCountAtStart = {0};
        int[] ammoRemainingAtStart = {0};
        boolean[] completed = {false};
        Recording recording;
        try {
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.setName("villagerguardgunners-" + name);
            recording.setToDisk(true);
            recording.setMaxSize(256L * 1024L * 1024L);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5L));
        } catch (IOException | ParseException exception) {
            helper.fail("Could not configure the Phase 8 JFR recording: " + exception.getMessage());
            return;
        }

        helper.onEachTick(() -> {
            if (completed[0]) {
                return;
            }

            long testTick = helper.getTick();
            moveProtectedFriendlies(movingFriendlies, testTick);
            advanceUnthrottledTaCZClocks(operators, weapon, testTick);
            if (testTick == WARMUP_TICKS - 1L) {
                recording.start();
            }
            if (testTick == WARMUP_TICKS) {
                gcCollectionsAtStart[0] = garbageCollectionCount();
                usedHeapAtStart[0] = usedHeapBytes();
                nativeShootCountAtStart[0] = nativeShootCount(operators);
                ammoRemainingAtStart[0] = remainingAmmo(gunStacks, ammoHandlers);
            }
            if (testTick < WARMUP_TICKS || sampleCount[0] >= SAMPLE_TICKS) {
                return;
            }

            long[] serverTickTimes = server.tickTimes;
            int previousTickIndex = Math.floorMod(server.getTickCount() - 1, serverTickTimes.length);
            samples[sampleCount[0]++] = serverTickTimes[previousTickIndex];
            if (sampleCount[0] == SAMPLE_TICKS) {
                completed[0] = true;
                int acceptedShotEvidence = writeReport(
                        helper,
                        name,
                        guardCount,
                        weapon,
                        workload,
                        operators,
                        gunStacks,
                        ammoHandlers,
                        samples,
                        gcCollectionsAtStart[0],
                        usedHeapAtStart[0],
                        nativeShootCountAtStart[0],
                        ammoRemainingAtStart[0],
                        recording);
                cleanup.forEach(Entity::discard);
                int minimumShotEvidence = minimumAcceptedShotEvidence(guardCount, weapon, workload);
                if (acceptedShotEvidence < minimumShotEvidence) {
                    helper.fail("The performance scenario recorded "
                            + acceptedShotEvidence
                            + " accepted native TaCZ shots but requires at least "
                            + minimumShotEvidence);
                    return;
                }
                helper.succeed();
            }
        });
    }

    private static void moveProtectedFriendlies(
            List<MovingFriendly> movingFriendlies,
            long testTick) {
        for (int index = 0; index < movingFriendlies.size(); index++) {
            MovingFriendly movingFriendly = movingFriendlies.get(index);
            Vec3 center = movingFriendly.center();
            double crossingOffset = Math.sin((testTick + index * 3L) * 0.15D) * 2.0D;
            movingFriendly.entity().setPos(center.x, center.y, center.z + crossingOffset);
            movingFriendly.entity().setDeltaMovement(Vec3.ZERO);
        }
    }

    private static void prepareLane(
            GameTestHelper helper,
            double guardX,
            double guardZ,
            boolean fullLane) {
        int firstX = (int) guardX - 1;
        int lastX = fullLane ? (int) guardX + 13 : (int) guardX + 1;
        for (int x = firstX; x <= lastX; x++) {
            for (int z = (int) guardZ - 1; z <= (int) guardZ + 1; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static void advanceUnthrottledTaCZClocks(
            List<IGunOperator> operators,
            Weapon weapon,
            long testTick) {
        if (weapon == Weapon.UNARMED) {
            return;
        }
        boolean triggerDue = testTick % weapon.nominalShotIntervalTicks == 0L;
        long currentTime = System.currentTimeMillis();
        for (IGunOperator operator : operators) {
            var data = operator.getDataHolder();
            data.drawTimestamp = currentTime - 10_000L;
            if (data.reloadTimestamp >= 0L) {
                // TaCZ reload scripts use wall-clock time. Advance an active
                // reload only, preserving the native script and inventory path
                // while keeping the unthrottled GameTest workload meaningful.
                data.reloadTimestamp = currentTime - 10_000L;
            }
            if (triggerDue) {
                long relativeNow = currentTime - data.baseTimestamp;
                data.shootTimestamp = relativeNow - 10_000L;
                data.lastShootTimestamp = relativeNow - 10_000L;
            }
        }
    }

    private static int writeReport(
            GameTestHelper helper,
            String name,
            int guardCount,
            Weapon weapon,
            Workload workload,
            List<IGunOperator> operators,
            List<ItemStack> gunStacks,
            List<GuardAmmoHandler> ammoHandlers,
            long[] samples,
            long gcCollectionsAtStart,
            long usedHeapAtStart,
            int nativeShootCountAtStart,
            int ammoRemainingAtStart,
            Recording recording) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        double averageMspt = Arrays.stream(samples).average().orElse(0.0D) / 1_000_000.0D;
        AABB scenarioBounds = new AABB(
                helper.absolutePos(new BlockPos(-4, -2, -4)),
                helper.absolutePos(new BlockPos(110, 16, 125)));
        int liveBullets = helper.getLevel()
                .getEntitiesOfClass(EntityKineticBullet.class, scenarioBounds)
                .size();
        int nativeShootCount = Math.max(0, nativeShootCount(operators) - nativeShootCountAtStart);
        int nativeAmmoConsumed = Math.max(
                0,
                ammoRemainingAtStart - remainingAmmo(gunStacks, ammoHandlers));
        int acceptedShotEvidence = Math.max(nativeShootCount, nativeAmmoConsumed);
        String reportDirectory = System.getProperty(REPORT_DIRECTORY_PROPERTY);
        helper.assertTrue(reportDirectory != null && !reportDirectory.isBlank(),
                "Phase 8 performance report directory was not configured");
        Path outputDirectory = Path.of(reportDirectory);
        String recordingFileName = name + ".jfr";
        try {
            recording.stop();
            Files.createDirectories(outputDirectory);
            recording.dump(outputDirectory.resolve(recordingFileName));
        } catch (IOException exception) {
            helper.fail("Could not write the Phase 8 JFR recording: " + exception.getMessage());
        } finally {
            recording.close();
        }

        JsonObject report = new JsonObject();
        report.addProperty("scenario", name);
        report.addProperty("guards", guardCount);
        report.addProperty("weapon", weapon.gunId == null ? "none" : weapon.gunId.toString());
        report.addProperty("workload", workload.name().toLowerCase());
        report.addProperty("warmupTicks", WARMUP_TICKS);
        report.addProperty("sampleTicks", samples.length);
        report.addProperty("configProfile", "default-v1");
        report.addProperty("configVersion", VillagerGuardGunnersConfig.CONFIG_VERSION);
        report.add("guardGunnerConfig", configSnapshot());
        report.addProperty("averageMspt", averageMspt);
        report.addProperty("p50Mspt", percentileMilliseconds(sorted, 0.50D));
        report.addProperty("p95Mspt", percentileMilliseconds(sorted, 0.95D));
        report.addProperty("p99Mspt", percentileMilliseconds(sorted, 0.99D));
        report.addProperty("maxMspt", sorted[sorted.length - 1] / 1_000_000.0D);
        report.addProperty("nativeTaCZShootCount", nativeShootCount);
        report.addProperty("nativeAmmoConsumed", nativeAmmoConsumed);
        report.addProperty(
                "minimumAcceptedShotEvidence",
                minimumAcceptedShotEvidence(guardCount, weapon, workload));
        report.addProperty("liveTaCZBulletsAtEnd", liveBullets);
        report.addProperty("garbageCollectionsDuringSample",
                Math.max(0L, garbageCollectionCount() - gcCollectionsAtStart));
        report.addProperty("usedHeapDeltaBytes", usedHeapBytes() - usedHeapAtStart);
        report.addProperty("jfrProfile", recordingFileName);
        report.addProperty("absoluteTimingGateApplied", false);
        report.addProperty(
                "timingNote",
                "Compare repeat runs on one machine; GameTest server timing is not a portable release threshold.");

        try {
            Files.writeString(
                    outputDirectory.resolve(name + ".json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            helper.fail("Could not write Phase 8 performance report: " + exception.getMessage());
        }
        return acceptedShotEvidence;
    }

    private static JsonObject configSnapshot() {
        VillagerGuardGunnersConfig config = ModConfigs.GUARD_GUNNERS;
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("enabled", config.enabled.get());

        JsonObject combat = new JsonObject();
        combat.addProperty("requiredVisibleTicks", config.combat.requiredVisibleTicks.get());
        combat.addProperty("maximumEngagementRange", config.combat.maximumEngagementRange.get());
        combat.addProperty("closeRangeDistance", config.combat.closeRangeDistance.get());
        combat.addProperty("aimToleranceDegrees", config.combat.aimToleranceDegrees.get());
        snapshot.add("combat", combat);

        JsonObject movement = new JsonObject();
        movement.addProperty("approachSpeed", config.movement.approachSpeed.get());
        movement.addProperty("retreatSpeed", config.movement.retreatSpeed.get());
        movement.addProperty("repositionSpeed", config.movement.repositionSpeed.get());
        movement.addProperty("friendlyRepositionTicks", config.movement.friendlyRepositionTicks.get());
        snapshot.add("movement", movement);

        JsonObject safety = new JsonObject();
        safety.addProperty("baseClearance", config.safety.baseClearance.get());
        snapshot.add("safety", safety);

        JsonObject performance = new JsonObject();
        performance.addProperty(
                "visibilitySampleIntervalTicks",
                config.performance.visibilitySampleIntervalTicks.get());
        performance.addProperty(
                "movementDecisionIntervalTicks",
                config.performance.movementDecisionIntervalTicks.get());
        performance.addProperty(
                "pathRecalculationIntervalTicks",
                config.performance.pathRecalculationIntervalTicks.get());
        performance.addProperty(
                "gunDataRefreshIntervalTicks",
                config.performance.gunDataRefreshIntervalTicks.get());
        performance.addProperty("unsafeShotCacheTicks", config.performance.unsafeShotCacheTicks.get());
        performance.addProperty(
                "repositionProgressCheckTicks",
                config.performance.repositionProgressCheckTicks.get());
        snapshot.add("performance", performance);
        return snapshot;
    }

    private static int nativeShootCount(List<IGunOperator> operators) {
        return operators.stream()
                .mapToInt(operator -> operator.getDataHolder().shootCount)
                .sum();
    }

    private static int remainingAmmo(
            List<ItemStack> gunStacks,
            List<GuardAmmoHandler> ammoHandlers) {
        int remaining = gunStacks.stream()
                .mapToInt(stack -> ((IGun) stack.getItem()).getCurrentAmmoCount(stack))
                .sum();
        for (GuardAmmoHandler handler : ammoHandlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                remaining += handler.getStackInSlot(slot).getCount();
            }
        }
        return remaining;
    }

    private static int minimumAcceptedShotEvidence(
            int guardCount,
            Weapon weapon,
            Workload workload) {
        if (weapon == Weapon.EXPLOSIVE && workload == Workload.CLEAR_LANES) {
            // At least two rounds per guard proves the test exercised native
            // dedicated-inventory reloads rather than one preloaded RPG shot.
            return guardCount * 2;
        }
        return workload == Workload.CLEAR_LANES || workload == Workload.PROTECTED_LANES
                ? 1
                : 0;
    }

    private static double percentileMilliseconds(long[] sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(sorted.length * percentile) - 1);
        return sorted[index] / 1_000_000.0D;
    }

    private static long garbageCollectionCount() {
        long count = 0L;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (collector.getCollectionCount() >= 0L) {
                count += collector.getCollectionCount();
            }
        }
        return count;
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private enum Workload {
        IDLE,
        CLEAR_LANES,
        OBSTACLES,
        PROTECTED_LANES
    }

    private record MovingFriendly(Villager entity, Vec3 center) {
    }

    private enum Weapon {
        UNARMED(null, null, 0, false, 1, null, 0),
        SEMI("tacz:glock_17", FireMode.SEMI, 10_000, false, 3, null, 0),
        AUTOMATIC("tacz:ak47", FireMode.AUTO, 10_000, false, 2, null, 0),
        SHOTGUN("tacz:aa12", FireMode.AUTO, 10_000, false, 4, null, 0),
        EXPLOSIVE("tacz:rpg7", FireMode.SEMI, 1, false, 8, "tacz:rpg_rocket", 64);

        private final ResourceLocation gunId;
        private final FireMode fireMode;
        private final int ammoCount;
        private final boolean heatData;
        private final int nominalShotIntervalTicks;
        private final ResourceLocation reloadAmmoId;
        private final int reloadAmmoPerSlot;

        Weapon(
                String gunId,
                FireMode fireMode,
                int ammoCount,
                boolean heatData,
                int nominalShotIntervalTicks,
                String reloadAmmoId,
                int reloadAmmoPerSlot) {
            this.gunId = gunId == null ? null : ResourceLocation.parse(gunId);
            this.fireMode = fireMode;
            this.ammoCount = ammoCount;
            this.heatData = heatData;
            this.nominalShotIntervalTicks = nominalShotIntervalTicks;
            this.reloadAmmoId = reloadAmmoId == null ? null : ResourceLocation.parse(reloadAmmoId);
            this.reloadAmmoPerSlot = reloadAmmoPerSlot;
        }
    }
}
