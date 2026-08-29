package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSharing;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
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
    private static final String EMPTY_TEMPLATE = "villagerguardgunners/gametest_arena";
    private static final int WARMUP_TICKS = 100;
    private static final int SAMPLE_TICKS = 1_000;
    private static final int FLOOR_Y = 4;
    private static final double ENTITY_Y = FLOOR_Y + 1.0D;
    private static final String REPORT_DIRECTORY_PROPERTY =
            "villagerguardgunners.phase8PerformanceReportDir";
    private static final String PRODUCTION_ARTIFACT_SHA256_PROPERTY =
            "villagerguardgunners.phase8ProductionArtifactSha256";
    private static final ResourceLocation NINE_MILLIMETER =
            ResourceLocation.fromNamespaceAndPath("tacz", "9mm");

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

    @GameTest(batch = "phase8_12_dropped_ammo_100", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void boundedDroppedAmmoChecks100(GameTestHelper helper) {
        runScenario(helper, "dropped-ammo-pressure-100", 100, Weapon.SEMI,
                Workload.DROPPED_AMMO_PRESSURE);
    }

    @GameTest(batch = "phase8_13_dry_sharing_50", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void boundedDryGuardSharingChecks50(GameTestHelper helper) {
        runScenario(helper, "dry-sharing-pressure-50", 50, Weapon.DRY_SEMI,
                Workload.DRY_SHARING_PRESSURE);
    }

    private static void runScenario(
            GameTestHelper helper,
            String name,
            int guardCount,
            Weapon weapon,
            Workload workload) {
        String productionArtifactSha256 =
                System.getProperty(PRODUCTION_ARTIFACT_SHA256_PROPERTY);
        helper.assertTrue(
                productionArtifactSha256 != null
                        && productionArtifactSha256.matches("[0-9a-f]{64}"),
                "The performance run was not bound to a production artifact SHA-256");
        List<Entity> cleanup = new ArrayList<>(guardCount * 3);
        List<Guard> guards = new ArrayList<>(guardCount);
        List<IGunOperator> operators = new ArrayList<>(guardCount);
        List<ItemStack> gunStacks = new ArrayList<>(guardCount);
        List<GuardAmmoHandler> ammoHandlers = new ArrayList<>(guardCount);
        List<Ravager> targets = new ArrayList<>(guardCount);
        List<MovingFriendly> movingFriendlies = new ArrayList<>(guardCount);
        List<ItemEntity> droppedAmmoEntities = new ArrayList<>(guardCount);
        double rowSpacing = rowSpacing(weapon, workload);
        double scenarioZOffset = scenarioZOffset(name);
        List<ChunkPos> forcedChunks = forceScenarioChunks(
                helper,
                guardCount,
                rowSpacing,
                scenarioZOffset);

        for (int index = 0; index < guardCount; index++) {
            int column = index / 10;
            int row = index % 10;
            double guardX = 2.5D + column * 20.0D;
            double guardZ = scenarioZOffset + 2.5D + row * rowSpacing;
            prepareLane(helper, guardX, guardZ, workload);
            Guard guard = helper.spawn(
                    GuardEntityType.GUARD.get(),
                    new Vec3(guardX, ENTITY_Y, guardZ));
            guard.setPersistenceRequired();
            if (workload == Workload.CLEAR_LANES
                    || workload == Workload.DROPPED_AMMO_PRESSURE
                    || workload == Workload.DRY_SHARING_PRESSURE) {
                // These scenarios isolate firing and safety-query cost. The
                // obstacle and protected-crossing workloads retain movement.
                guard.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            }
            GuardAmmoHandler ammoHandler = GuardAmmoAccess.getAmmoHandler(guard);
            ammoHandlers.add(ammoHandler);
            ItemStack gun = ItemStack.EMPTY;
            if (weapon != Weapon.UNARMED) {
                gun = GunItemBuilder.create()
                        .setId(weapon.gunId)
                        .setFireMode(weapon.fireMode)
                        .setAmmoCount(weapon.ammoCount)
                        .setAmmoInBarrel(weapon != Weapon.DRY_SEMI)
                        .setHeatData(weapon.heatData)
                        .build();
                guard.setItemSlot(EquipmentSlot.MAINHAND, gun);
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
                if (workload == Workload.DROPPED_AMMO_PRESSURE) {
                    for (int slot = 0; slot < ammoHandler.getSlots(); slot++) {
                        ammoHandler.setStackInSlot(
                                slot,
                                AmmoItemBuilder.create()
                                        .setId(NINE_MILLIMETER)
                                        .setCount(64)
                                        .build());
                    }
                    ItemEntity droppedAmmo = new ItemEntity(
                            helper.getLevel(),
                            guardX + 0.75D,
                            ENTITY_Y,
                            guardZ,
                            AmmoItemBuilder.create()
                                    .setId(NINE_MILLIMETER)
                                    .setCount(1)
                                    .build());
                    droppedAmmo.setNoPickUpDelay();
                    droppedAmmo.setNoGravity(true);
                    droppedAmmo.setDeltaMovement(Vec3.ZERO);
                    helper.getLevel().addFreshEntity(droppedAmmo);
                    droppedAmmoEntities.add(droppedAmmo);
                    cleanup.add(droppedAmmo);
                }
            }
            gunStacks.add(gun);
            cleanup.add(guard);
            guards.add(guard);
            operators.add(IGunOperator.fromLivingEntity(guard));

            if (workload.hasTarget()) {
                Ravager target = helper.spawnWithNoFreeWill(
                        EntityType.RAVAGER,
                        new Vec3(guardX + 12.0D, ENTITY_Y, guardZ));
                target.setInvulnerable(true);
                target.setPersistenceRequired();
                guard.setTarget(target);
                targets.add(target);
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
        int[] nativeShootCountsAtStart = new int[guardCount];
        int[] ammoRemainingAtStart = new int[guardCount];
        boolean[] completed = {false};
        Recording recording;
        try {
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.setName("villagerguardgunners-" + name);
            recording.setToDisk(true);
            recording.setMaxSize(256L * 1024L * 1024L);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5L));
        } catch (IOException | ParseException exception) {
            releaseScenarioChunks(helper, forcedChunks);
            helper.fail("Could not configure the Phase 8 JFR recording: " + exception.getMessage());
            return;
        }

        helper.onEachTick(() -> {
            if (completed[0]) {
                return;
            }

            long testTick = helper.getTick();
            maintainScenarioTargets(guards, targets, workload);
            moveProtectedFriendlies(movingFriendlies, testTick);
            advanceUnthrottledTaCZClocks(operators, weapon, testTick);
            if (testTick == WARMUP_TICKS - 1L) {
                recording.start();
            }
            if (testTick == WARMUP_TICKS) {
                gcCollectionsAtStart[0] = garbageCollectionCount();
                usedHeapAtStart[0] = usedHeapBytes();
                capturePerGuardEvidence(
                        operators,
                        gunStacks,
                        ammoHandlers,
                        nativeShootCountsAtStart,
                        ammoRemainingAtStart);
            }
            if (testTick < WARMUP_TICKS || sampleCount[0] >= SAMPLE_TICKS) {
                return;
            }

            long[] serverTickTimes = server.tickTimes;
            int previousTickIndex = Math.floorMod(server.getTickCount() - 1, serverTickTimes.length);
            samples[sampleCount[0]++] = serverTickTimes[previousTickIndex];
            if (sampleCount[0] == SAMPLE_TICKS) {
                completed[0] = true;
                ShotEvidence shotEvidence = writeReport(
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
                        nativeShootCountsAtStart,
                        ammoRemainingAtStart,
                        guards,
                        droppedAmmoEntities,
                        recording);
                discardScenarioBullets(helper, guards);
                cleanup.forEach(Entity::discard);
                releaseScenarioChunks(helper, forcedChunks);
                int minimumShotEvidence = minimumAcceptedShotEvidence(guardCount, weapon, workload);
                int minimumFiringGuards = minimumAcceptedFiringGuards(guardCount, workload);
                int minimumPerActiveGuardEvidence =
                        minimumAcceptedPerActiveGuardShotEvidence(workload);
                int minimumSharingAttempts = minimumAmmoSharingAttempts(guardCount, workload);
                int minimumPersistentDrops = minimumPersistentDroppedAmmo(guardCount, workload);
                if (shotEvidence.acceptedTotal() < minimumShotEvidence
                        || shotEvidence.activeFiringGuards() < minimumFiringGuards
                        || shotEvidence.minimumPerActiveGuard()
                                < minimumPerActiveGuardEvidence
                        || shotEvidence.ammoSharingAttemptsObserved() < minimumSharingAttempts
                        || shotEvidence.persistentDroppedAmmoEntities() < minimumPersistentDrops) {
                    helper.fail("The performance scenario recorded "
                            + shotEvidence.acceptedTotal()
                            + " accepted native TaCZ shots across "
                            + shotEvidence.activeFiringGuards()
                            + " guards, with a per-active-guard minimum of "
                            + shotEvidence.minimumPerActiveGuard()
                            + "; it requires at least "
                            + minimumShotEvidence
                            + " total, "
                            + minimumFiringGuards
                            + " active guards, and "
                            + minimumPerActiveGuardEvidence
                            + " per active guard; ammunition evidence was sharing="
                            + shotEvidence.ammoSharingAttemptsObserved()
                            + "/"
                            + minimumSharingAttempts
                            + ", persistentDrops="
                            + shotEvidence.persistentDroppedAmmoEntities()
                            + "/"
                            + minimumPersistentDrops);
                    return;
                }
                helper.succeed();
            }
        });
    }

    private static double rowSpacing(Weapon weapon, Workload workload) {
        if (workload == Workload.IDLE
                || workload == Workload.DROPPED_AMMO_PRESSURE
                || workload == Workload.DRY_SHARING_PRESSURE) {
            return 4.0D;
        }
        return switch (weapon) {
            case SHOTGUN -> 16.0D;
            case EXPLOSIVE -> 40.0D;
            default -> 12.0D;
        };
    }

    /** Keeps every sequential performance workload in its own persistent world region. */
    private static double scenarioZOffset(String name) {
        int scenarioIndex = switch (name) {
            case "unarmed-idle-baseline-100" -> 0;
            case "idle-armed-100" -> 1;
            case "semi-10" -> 2;
            case "semi-25" -> 3;
            case "semi-50" -> 4;
            case "automatic-10" -> 5;
            case "automatic-25" -> 6;
            case "automatic-50" -> 7;
            case "automatic-shotgun-10" -> 8;
            case "explosive-10" -> 9;
            case "obstacle-navigation-25" -> 10;
            case "protected-crossing-25" -> 11;
            case "dropped-ammo-pressure-100" -> 12;
            case "dry-sharing-pressure-50" -> 13;
            default -> throw new IllegalArgumentException("Unknown performance scenario: " + name);
        };
        return scenarioIndex * 512.0D;
    }

    private static List<ChunkPos> forceScenarioChunks(
            GameTestHelper helper,
            int guardCount,
            double rowSpacing,
            double scenarioZOffset) {
        int columnCount = Math.max(1, (guardCount + 9) / 10);
        int rowCount = Math.min(10, Math.max(1, guardCount));
        BlockPos minimum = helper.absolutePos(new BlockPos(
                -4,
                0,
                (int) Math.floor(scenarioZOffset - 4.0D)));
        BlockPos maximum = helper.absolutePos(new BlockPos(
                (int) Math.ceil(2.5D + (columnCount - 1) * 20.0D + 16.0D),
                0,
                (int) Math.ceil(
                        scenarioZOffset + 2.5D + (rowCount - 1) * rowSpacing + 4.0D)));
        int minimumChunkX = Math.min(minimum.getX(), maximum.getX()) >> 4;
        int maximumChunkX = Math.max(minimum.getX(), maximum.getX()) >> 4;
        int minimumChunkZ = Math.min(minimum.getZ(), maximum.getZ()) >> 4;
        int maximumChunkZ = Math.max(minimum.getZ(), maximum.getZ()) >> 4;
        List<ChunkPos> forcedChunks = new ArrayList<>(
                (maximumChunkX - minimumChunkX + 1) * (maximumChunkZ - minimumChunkZ + 1));
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                    helper.getLevel().setChunkForced(chunkX, chunkZ, true);
                    forcedChunks.add(chunk);
                }
            }
        }
        return forcedChunks;
    }

    private static void releaseScenarioChunks(GameTestHelper helper, List<ChunkPos> forcedChunks) {
        for (ChunkPos chunk : forcedChunks) {
            helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
        }
    }

    private static void moveProtectedFriendlies(
            List<MovingFriendly> movingFriendlies,
            long testTick) {
        for (int index = 0; index < movingFriendlies.size(); index++) {
            MovingFriendly movingFriendly = movingFriendlies.get(index);
            Vec3 center = movingFriendly.center();
            double crossingOffset = protectedFriendlyOffset(testTick, index);
            movingFriendly.entity().setPos(center.x, center.y, center.z + crossingOffset);
            movingFriendly.entity().setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * Deterministic crossing with a clear-lane dwell on both sides. The dwell
     * guarantees every independent firing lane receives repeated open windows
     * while the two linear transitions retain the moving-friendly workload.
     */
    static double protectedFriendlyOffset(long testTick, int laneIndex) {
        long phase = Math.floorMod(testTick + laneIndex * 7L, 120L);
        if (phase < 30L) {
            return -4.0D;
        }
        if (phase < 60L) {
            return -4.0D + (phase - 30L) * (8.0D / 30.0D);
        }
        if (phase < 90L) {
            return 4.0D;
        }
        return 4.0D - (phase - 90L) * (8.0D / 30.0D);
    }

    private static void maintainScenarioTargets(
            List<Guard> guards,
            List<Ravager> targets,
            Workload workload) {
        if (!workload.hasTarget()) {
            return;
        }
        for (int index = 0; index < guards.size(); index++) {
            Guard guard = guards.get(index);
            Ravager target = targets.get(index);
            if (guard.isAlive() && target.isAlive() && guard.getTarget() != target) {
                guard.setTarget(target);
            }
        }
    }

    private static void prepareLane(
            GameTestHelper helper,
            double guardX,
            double guardZ,
            Workload workload) {
        int firstX = (int) guardX - 1;
        int lastX = workload.hasTarget() ? (int) guardX + 13 : (int) guardX + 1;
        int laneHalfWidth = workload == Workload.PROTECTED_LANES ? 5 : 1;
        for (int x = firstX; x <= lastX; x++) {
            for (int z = (int) guardZ - laneHalfWidth;
                    z <= (int) guardZ + laneHalfWidth;
                    z++) {
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

    private static ShotEvidence writeReport(
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
            int[] nativeShootCountsAtStart,
            int[] ammoRemainingAtStart,
            List<Guard> guards,
            List<ItemEntity> droppedAmmoEntities,
            Recording recording) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        double averageMspt = Arrays.stream(samples).average().orElse(0.0D) / 1_000_000.0D;
        int liveBullets = 0;
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof EntityKineticBullet bullet
                    && bullet.getOwner() instanceof Guard owner
                    && guards.contains(owner)) {
                liveBullets++;
            }
        }
        int nativeShootCount = 0;
        int nativeAmmoConsumed = 0;
        int[] perGuardShotEvidence = new int[guardCount];
        for (int index = 0; index < guardCount; index++) {
            int shootCount = Math.max(
                    0,
                    operators.get(index).getDataHolder().shootCount
                            - nativeShootCountsAtStart[index]);
            int ammoConsumed = Math.max(
                    0,
                    ammoRemainingAtStart[index]
                            - remainingAmmo(gunStacks.get(index), ammoHandlers.get(index)));
            nativeShootCount += shootCount;
            nativeAmmoConsumed += ammoConsumed;
            perGuardShotEvidence[index] = Math.max(shootCount, ammoConsumed);
        }
        int acceptedShotEvidence = Math.max(nativeShootCount, nativeAmmoConsumed);
        int activeFiringGuards = (int) Arrays.stream(perGuardShotEvidence)
                .filter(value -> value > 0)
                .count();
        int minimumPerGuard = Arrays.stream(perGuardShotEvidence).min().orElse(0);
        int minimumPerActiveGuard = Arrays.stream(perGuardShotEvidence)
                .filter(value -> value > 0)
                .min()
                .orElse(0);
        int ammoSharingAttemptsObserved = observedAmmoSharingAttempts(guards);
        int persistentDroppedAmmoEntities = (int) droppedAmmoEntities.stream()
                .filter(ItemEntity::isAlive)
                .count();
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
        report.addProperty("configProfile", "default-v7");
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
        report.addProperty("activeFiringGuards", activeFiringGuards);
        report.addProperty(
                "minimumAcceptedFiringGuards",
                minimumAcceptedFiringGuards(guardCount, workload));
        report.addProperty("minimumPerGuardShotEvidence", minimumPerGuard);
        report.addProperty(
                "minimumPerActiveGuardShotEvidence",
                minimumPerActiveGuard);
        report.addProperty(
                "minimumAcceptedPerActiveGuardShotEvidence",
                minimumAcceptedPerActiveGuardShotEvidence(workload));
        report.addProperty("ammoSharingAttemptsObserved", ammoSharingAttemptsObserved);
        report.addProperty(
                "minimumAmmoSharingAttempts",
                minimumAmmoSharingAttempts(guardCount, workload));
        report.addProperty("persistentDroppedAmmoEntities", persistentDroppedAmmoEntities);
        report.addProperty(
                "minimumPersistentDroppedAmmo",
                minimumPersistentDroppedAmmo(guardCount, workload));
        JsonArray perGuardEvidence = new JsonArray();
        Arrays.stream(perGuardShotEvidence).forEach(perGuardEvidence::add);
        report.add("perGuardAcceptedShotEvidence", perGuardEvidence);
        report.addProperty("liveTaCZBulletsAtEnd", liveBullets);
        report.addProperty("garbageCollectionsDuringSample",
                Math.max(0L, garbageCollectionCount() - gcCollectionsAtStart));
        report.addProperty("usedHeapDeltaBytes", usedHeapBytes() - usedHeapAtStart);
        report.addProperty("jfrProfile", recordingFileName);
        report.addProperty(
                "productionArtifactSha256",
                System.getProperty(PRODUCTION_ARTIFACT_SHA256_PROPERTY));
        report.addProperty("absoluteTimingGateApplied", false);
        report.addProperty("relativeTimingGateApplied", true);
        report.addProperty(
                "timingNote",
                "Validated against the unarmed baseline from this run; compare absolute values only on one machine.");

        try {
            Files.writeString(
                    outputDirectory.resolve(name + ".json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            helper.fail("Could not write Phase 8 performance report: " + exception.getMessage());
        }
        return new ShotEvidence(
                acceptedShotEvidence,
                activeFiringGuards,
                minimumPerGuard,
                minimumPerActiveGuard,
                ammoSharingAttemptsObserved,
                persistentDroppedAmmoEntities,
                perGuardShotEvidence);
    }

    private static int observedAmmoSharingAttempts(List<Guard> guards) {
        int attempts = 0;
        for (Guard guard : guards) {
            boolean attempted = guard.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(GuardGunCombatGoal.class::isInstance)
                    .map(GuardGunCombatGoal.class::cast)
                    .map(GuardGunCombatGoal::captureDiagnostics)
                    .map(diagnostics -> diagnostics.nativeState().lastAmmoSharingStatus())
                    .anyMatch(status -> status != GuardAmmoSharing.SharingStatus.NOT_ATTEMPTED);
            if (attempted) {
                attempts++;
            }
        }
        return attempts;
    }

    private static JsonObject configSnapshot() {
        VillagerGuardGunnersConfig config = ModConfigs.GUARD_GUNNERS;
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("enabled", config.enabled.get());

        JsonObject naturalLoadouts = new JsonObject();
        naturalLoadouts.addProperty("enabled", config.naturalLoadouts.enabled.get());
        JsonArray enabledSpawnSources = new JsonArray();
        config.naturalLoadouts.enabledSpawnSources.get().stream()
                .map(Enum::name)
                .sorted()
                .forEach(enabledSpawnSources::add);
        naturalLoadouts.add("enabledSpawnSources", enabledSpawnSources);
        naturalLoadouts.addProperty(
                "replaceCrossbowsOnly",
                config.naturalLoadouts.replaceCrossbowsOnly.get());
        naturalLoadouts.addProperty(
                "nearbyCapRadius",
                config.naturalLoadouts.nearbyCapRadius.get());
        naturalLoadouts.addProperty(
                "allowExplosiveWeapons",
                config.naturalLoadouts.allowExplosiveWeapons.get());
        naturalLoadouts.add("easy", naturalDifficultySnapshot(config.naturalLoadouts.easy));
        naturalLoadouts.add("normal", naturalDifficultySnapshot(config.naturalLoadouts.normal));
        naturalLoadouts.add("hard", naturalDifficultySnapshot(config.naturalLoadouts.hard));
        snapshot.add("naturalLoadouts", naturalLoadouts);

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

        JsonObject tactics = new JsonObject();
        JsonObject typeProfiles = new JsonObject();
        config.tactics.typeProfiles.get().forEach(
                (type, profile) -> typeProfiles.addProperty(type, profile.name()));
        tactics.add("typeProfiles", typeProfiles);
        JsonObject gunOverrides = new JsonObject();
        config.tactics.gunOverrides.get().forEach(
                (gunId, profile) -> gunOverrides.addProperty(gunId.toString(), profile.name()));
        tactics.add("gunOverrides", gunOverrides);
        tactics.add("generalist", profileSnapshot(config.tactics.generalist));
        tactics.add("skirmisher", profileSnapshot(config.tactics.skirmisher));
        tactics.add("breacher", profileSnapshot(config.tactics.breacher));
        tactics.add("marksman", profileSnapshot(config.tactics.marksman));
        tactics.add("support", profileSnapshot(config.tactics.support));
        tactics.add("demolitions", profileSnapshot(config.tactics.demolitions));
        snapshot.add("tactics", tactics);

        JsonObject engagement = new JsonObject();
        engagement.addProperty("mode", config.engagement.mode.get().name());
        engagement.addProperty(
                "defensiveMemoryTicks",
                config.engagement.defensiveMemoryTicks.get());
        JsonArray additionalTargets = new JsonArray();
        config.engagement.additionalTargets.get().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(additionalTargets::add);
        engagement.add("additionalTargets", additionalTargets);
        JsonArray excludedTargets = new JsonArray();
        config.engagement.excludedEntities.get().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(excludedTargets::add);
        engagement.add("excludedEntities", excludedTargets);
        snapshot.add("engagement", engagement);

        JsonObject safety = new JsonObject();
        safety.addProperty("baseClearance", config.safety.baseClearance.get());
        safety.addProperty(
                "preserveBlocksFromGuardExplosions",
                config.safety.preserveBlocksFromGuardExplosions.get());
        safety.addProperty(
                "explosiveStandoffRadiusMultiplier",
                config.safety.explosiveStandoffRadiusMultiplier.get());
        safety.addProperty(
                "explosiveStandoffAdditionalBlocks",
                config.safety.explosiveStandoffAdditionalBlocks.get());
        JsonObject allies = new JsonObject();
        allies.addProperty(
                "protectOwnedEntities",
                config.safety.allies.protectOwnedEntities.get());
        allies.addProperty(
                "protectAlliedEntities",
                config.safety.allies.protectAlliedEntities.get());
        JsonArray additionalProtectedEntities = new JsonArray();
        config.safety.allies.additionalProtectedEntities.get().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(additionalProtectedEntities::add);
        allies.add("additionalProtectedEntities", additionalProtectedEntities);
        JsonArray excludedEntities = new JsonArray();
        config.safety.allies.excludedEntities.get().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(excludedEntities::add);
        allies.add("excludedEntities", excludedEntities);
        safety.add("allies", allies);
        snapshot.add("safety", safety);

        JsonObject ammunition = new JsonObject();
        ammunition.addProperty(
                "pickupDroppedAmmo",
                config.ammunition.pickupDroppedAmmo.get());
        ammunition.addProperty(
                "droppedAmmoPickupRadius",
                config.ammunition.droppedAmmoPickupRadius.get());
        ammunition.addProperty(
                "shareAmmoBetweenGuards",
                config.ammunition.shareAmmoBetweenGuards.get());
        ammunition.addProperty(
                "ammoSharingRadius",
                config.ammunition.ammoSharingRadius.get());
        ammunition.addProperty(
                "donorReserveMagazines",
                config.ammunition.donorReserveMagazines.get());
        ammunition.addProperty(
                "requireMatchingOwnerForSharing",
                config.ammunition.requireMatchingOwnerForSharing.get());
        snapshot.add("ammunition", ammunition);

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

    private static JsonObject profileSnapshot(
            VillagerGuardGunnersConfig.ProfileSettings profile) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty(
                "preferredRangeMultiplier",
                profile.preferredRangeMultiplier.get());
        snapshot.addProperty(
                "retreatDistanceMultiplier",
                profile.retreatDistanceMultiplier.get());
        snapshot.addProperty("visibleTicksAdjustment", profile.visibleTicksAdjustment.get());
        snapshot.addProperty("aimToleranceMultiplier", profile.aimToleranceMultiplier.get());
        snapshot.addProperty(
                "limitFiringToEffectiveRange",
                profile.limitFiringToEffectiveRange.get());
        snapshot.addProperty(
                "maximumFiringRangeMultiplier",
                profile.maximumFiringRangeMultiplier.get());
        return snapshot;
    }

    private static JsonObject naturalDifficultySnapshot(
            VillagerGuardGunnersConfig.NaturalDifficultySettings difficulty) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("upgradeChance", difficulty.upgradeChance.get());
        snapshot.addProperty("maximumNearby", difficulty.maximumNearby.get());
        snapshot.addProperty("poolId", difficulty.poolId.get().toString());
        return snapshot;
    }

    private static void capturePerGuardEvidence(
            List<IGunOperator> operators,
            List<ItemStack> gunStacks,
            List<GuardAmmoHandler> ammoHandlers,
            int[] nativeShootCounts,
            int[] remainingAmmo) {
        for (int index = 0; index < operators.size(); index++) {
            nativeShootCounts[index] = operators.get(index).getDataHolder().shootCount;
            remainingAmmo[index] = remainingAmmo(gunStacks.get(index), ammoHandlers.get(index));
        }
    }

    private static int remainingAmmo(ItemStack gunStack, GuardAmmoHandler ammoHandler) {
        int remaining = gunStack.getItem() instanceof IGun gun
                ? gun.getCurrentAmmoCount(gunStack)
                : 0;
        for (int slot = 0; slot < ammoHandler.getSlots(); slot++) {
            remaining += ammoHandler.getStackInSlot(slot).getCount();
        }
        return remaining;
    }

    private static void discardScenarioBullets(GameTestHelper helper, List<Guard> guards) {
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof EntityKineticBullet bullet
                    && bullet.getOwner() instanceof Guard owner
                    && guards.contains(owner)) {
                bullet.discard();
            }
        }
    }

    private static int minimumAcceptedShotEvidence(
            int guardCount,
            Weapon weapon,
            Workload workload) {
        if (weapon == Weapon.EXPLOSIVE && workload == Workload.CLEAR_LANES) {
            // Twice the guard count proves the group exercised native
            // dedicated-inventory reloads rather than only preloaded RPG shots.
            return guardCount * 2;
        }
        return workload == Workload.CLEAR_LANES || workload == Workload.PROTECTED_LANES
                ? guardCount
                : 0;
    }

    private static int minimumAcceptedFiringGuards(int guardCount, Workload workload) {
        return workload == Workload.CLEAR_LANES || workload == Workload.PROTECTED_LANES
                ? Math.max(1, (guardCount * 4 + 4) / 5)
                : 0;
    }

    private static int minimumAcceptedPerActiveGuardShotEvidence(Workload workload) {
        return workload == Workload.CLEAR_LANES || workload == Workload.PROTECTED_LANES
                ? 1
                : 0;
    }

    private static int minimumAmmoSharingAttempts(int guardCount, Workload workload) {
        return workload == Workload.DRY_SHARING_PRESSURE ? guardCount : 0;
    }

    private static int minimumPersistentDroppedAmmo(int guardCount, Workload workload) {
        return workload == Workload.DROPPED_AMMO_PRESSURE ? guardCount : 0;
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
        PROTECTED_LANES,
        DROPPED_AMMO_PRESSURE,
        DRY_SHARING_PRESSURE;

        private boolean hasTarget() {
            return this != IDLE && this != DROPPED_AMMO_PRESSURE;
        }
    }

    private record MovingFriendly(Villager entity, Vec3 center) {
    }

    private record ShotEvidence(
            int acceptedTotal,
            int activeFiringGuards,
            int minimumPerGuard,
            int minimumPerActiveGuard,
            int ammoSharingAttemptsObserved,
            int persistentDroppedAmmoEntities,
            int[] perGuard) {
    }

    private enum Weapon {
        UNARMED(null, null, 0, false, 1, null, 0),
        SEMI("tacz:glock_17", FireMode.SEMI, 10_000, false, 3, null, 0),
        DRY_SEMI("tacz:glock_17", FireMode.SEMI, 0, false, 3, null, 0),
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
