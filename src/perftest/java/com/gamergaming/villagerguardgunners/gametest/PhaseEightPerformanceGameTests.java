package com.gamergaming.villagerguardgunners.gametest;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.ai.GuardArmoryResupplyGoal;
import com.gamergaming.villagerguardgunners.ai.SquadCommandMovementGoal;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSharing;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess;
import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyServerServices;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyLimits;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyState;
import com.gamergaming.villagerguardgunners.armory.resupply.GuardArmoryResupplyController;
import com.gamergaming.villagerguardgunners.armory.resupply.LoadedArmoryIndex;
import com.gamergaming.villagerguardgunners.config.VillagerGuardGunnersConfig;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
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
    private static final int WARMUP_TICKS = 200;
    private static final int SAMPLE_TICKS = 1_000;
    private static final int FLOOR_Y = 4;
    private static final double ENTITY_Y = FLOOR_Y + 1.0D;
    private static final String REPORT_DIRECTORY_PROPERTY =
            "villagerguardgunners.phase8PerformanceReportDir";
    private static final String PRODUCTION_ARTIFACT_SHA256_PROPERTY =
            "villagerguardgunners.phase8ProductionArtifactSha256";
    private static final ResourceLocation NINE_MILLIMETER =
            ResourceLocation.fromNamespaceAndPath("tacz", "9mm");
    private static final ResourceLocation SEVEN_SIXTY_TWO_BY_THIRTY_NINE =
            ResourceLocation.fromNamespaceAndPath("tacz", "762x39");

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

    @GameTest(batch = "phase8_14_dry_sidearm_switch_50", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void simultaneousDryPrimarySidearmSwitch50(GameTestHelper helper) {
        runScenario(helper, "dry-primary-sidearm-switch-50", 50, Weapon.DRY_AUTOMATIC,
                Workload.DRY_PRIMARY_SIDEARM_SWITCH);
    }

    @GameTest(batch = "phase8_15_explosive_sidearm_20", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void mixedExplosiveStandoffAndSidearm20(GameTestHelper helper) {
        runScenario(helper, "mixed-explosive-standoff-sidearm-20", 20, Weapon.EXPLOSIVE,
                Workload.MIXED_EXPLOSIVE_STANDOFF_SIDEARM);
    }

    @GameTest(batch = "phase8_16_idle_sidearms_100", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void idleArmedGuardsWithSidearms100(GameTestHelper helper) {
        runScenario(helper, "idle-armed-sidearms-100", 100, Weapon.SEMI,
                Workload.IDLE_WITH_SIDEARMS);
    }

    @GameTest(batch = "phase8_17_armory_idle_100", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void loadedIdleArmories100(GameTestHelper helper) {
        runScenario(helper, "armory-idle-100", 0, Weapon.UNARMED,
                Workload.ARMORY_IDLE);
    }

    @GameTest(batch = "phase8_18_armory_satisfied_100", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void suppliedGuardsSkipArmoryQueries100(GameTestHelper helper) {
        runScenario(helper, "armory-satisfied-100", 100, Weapon.SEMI,
                Workload.ARMORY_SATISFIED);
    }

    @GameTest(batch = "phase8_19_armory_pressure_50", templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE, timeoutTicks = 1_600)
    public static void scarceUnreachableMixedArmoryRequests50(GameTestHelper helper) {
        runScenario(helper, "armory-request-pressure-50", 50, Weapon.DRY_SEMI,
                Workload.ARMORY_REQUEST_PRESSURE);
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
        List<Vec3> guardAnchors = new ArrayList<>(guardCount);
        List<IGunOperator> operators = new ArrayList<>(guardCount);
        List<ItemStack> gunStacks = new ArrayList<>(guardCount);
        List<GuardAmmoHandler> ammoHandlers = new ArrayList<>(guardCount);
        List<Ravager> targets = new ArrayList<>(guardCount);
        List<Vec3> targetAnchors = new ArrayList<>(guardCount);
        List<MovingFriendly> movingFriendlies = new ArrayList<>(guardCount);
        List<ItemEntity> droppedAmmoEntities = new ArrayList<>(guardCount);
        List<BlockPos> armoryPositions = new ArrayList<>(workload.armoryCount());
        double rowSpacing = rowSpacing(weapon, workload);
        double columnSpacing = workload.columnSpacing();
        double scenarioZOffset = scenarioZOffset(name);
        List<ChunkPos> forcedChunks = forceScenarioChunks(
                helper,
                Math.max(guardCount, workload.armoryCount()),
                columnSpacing,
                rowSpacing,
                scenarioZOffset);

        for (int index = 0; index < guardCount; index++) {
            int column = index / 10;
            int row = index % 10;
            double guardX = 2.5D + column * columnSpacing;
            double guardZ = scenarioZOffset + 2.5D + row * rowSpacing;
            prepareLane(helper, guardX, guardZ, workload);
            Guard guard = helper.spawn(
                    GuardEntityType.GUARD.get(),
                    new Vec3(guardX, ENTITY_Y, guardZ));
            retainOnlyAddOnGoals(guard);
            guard.setPersistenceRequired();
            if (workload.anchorsGuards()
                    || workload == Workload.DROPPED_AMMO_PRESSURE
                    || workload == Workload.DRY_SHARING_PRESSURE) {
                // These scenarios isolate firing and safety-query cost. The
                // obstacle and protected-crossing workloads retain movement.
                guard.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            }
            if (workload.anchorsGuards()) {
                guard.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            }
            GuardAmmoHandler ammoHandler = GuardAmmoAccess.getAmmoHandler(guard);
            ammoHandlers.add(ammoHandler);
            ItemStack gun = ItemStack.EMPTY;
            Weapon guardWeapon = workload.weaponForGuard(index, weapon);
            if (guardWeapon != Weapon.UNARMED) {
                gun = createGunStack(guardWeapon);
                guard.setItemSlot(EquipmentSlot.MAINHAND, gun);
                if (guardWeapon.reloadAmmoId != null) {
                    for (int slot = 0; slot < ammoHandler.getSlots(); slot++) {
                        ammoHandler.setStackInSlot(
                                slot,
                                AmmoItemBuilder.create()
                                        .setId(guardWeapon.reloadAmmoId)
                                        .setCount(guardWeapon.reloadAmmoPerSlot)
                                        .build());
                    }
                }
                if (workload == Workload.ARMORY_SATISFIED) {
                    ItemStack reserve = AmmoItemBuilder.create()
                            .setId(NINE_MILLIMETER)
                            .build();
                    reserve.setCount(reserve.getMaxStackSize());
                    ammoHandler.setStackInSlot(0, reserve);
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
            if (workload.hasSidearmLoadout()
                    && workload != Workload.DRY_PRIMARY_SIDEARM_SWITCH) {
                installPerformanceSidearm(helper, guard);
            }
            gunStacks.add(gun);
            cleanup.add(guard);
            guards.add(guard);
            guardAnchors.add(guard.position());
            operators.add(IGunOperator.fromLivingEntity(guard));

            if (workload.hasTarget()) {
                Vec3 targetRelativePosition = new Vec3(
                        guardX + workload.targetDistance(index),
                        ENTITY_Y,
                        guardZ);
                Ravager target = helper.spawnWithNoFreeWill(
                        EntityType.RAVAGER,
                        targetRelativePosition);
                Vec3 targetAnchor = target.position();
                target.setInvulnerable(true);
                target.setNoGravity(true);
                target.setDeltaMovement(Vec3.ZERO);
                target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
                target.setPersistenceRequired();
                if (!workload.delaysTargetUntilSample()) {
                    guard.setTarget(target);
                }
                targets.add(target);
                targetAnchors.add(targetAnchor);
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

        prepareArmoryFixture(
                helper,
                workload,
                scenarioZOffset,
                columnSpacing,
                rowSpacing,
                armoryPositions);

        MinecraftServer server = helper.getLevel().getServer();
        long[] samples = new long[SAMPLE_TICKS];
        int[] sampleCount = {0};
        long[] gcCollectionsAtStart = {0L};
        long[] usedHeapAtStart = {0L};
        int[] acceptedShotEventsAtStart = new int[guardCount];
        int[] acceptedShotEventsAtEnd = new int[guardCount];
        int[] ammoRemainingAtStart = new int[guardCount];
        int[] firstSidearmActiveTick = new int[guardCount];
        Arrays.fill(firstSidearmActiveTick, -1);
        LoadedArmoryIndex.Metrics[] armoryIndexMetricsAtStart = new LoadedArmoryIndex.Metrics[1];
        ArmoryControllerTotals[] armoryControllerTotalsAtStart = new ArmoryControllerTotals[1];
        int[] loadedArmoriesAtStart = {-1};
        int[] forcedChunksAtStart = {-1};
        boolean[] completed = {false};
        boolean[] shotRecorderRegistered = {false};
        AcceptedShotRecorder shotRecorder = new AcceptedShotRecorder(guards);
        NominalShotClock nominalShotClock = new NominalShotClock(guardCount);
        Recording recording;
        try {
            // The default preset plus an explicit execution-sample period is
            // sufficient for this focused attribution run. Avoid the broad
            // profile preset: JDK 17's JFR TypeLibrary can race while many
            // dynamically loaded mod classes/events are finalized.
            recording = new Recording(Configuration.getConfiguration("default"));
            recording.setName("villagerguardgunners-" + name);
            recording.setToDisk(true);
            recording.setMaxSize(256L * 1024L * 1024L);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5L));
        } catch (IOException | ParseException exception) {
            releaseScenarioChunks(helper, forcedChunks);
            helper.fail("Could not configure the Phase 8 JFR recording: " + exception.getMessage());
            return;
        }
        MinecraftForge.EVENT_BUS.register(shotRecorder);
        shotRecorderRegistered[0] = true;

        helper.onEachTick(() -> {
            if (completed[0]) {
                return;
            }

            long testTick = helper.getTick();
            maintainScenarioTargets(
                    guards,
                    guardAnchors,
                    targets,
                    targetAnchors,
                    workload,
                    weapon,
                    testTick);
            moveProtectedFriendlies(movingFriendlies, testTick);
            advanceUnthrottledTaCZClocks(
                    operators,
                    weapon,
                    testTick,
                    shotRecorder,
                    nominalShotClock);
            observeSidearmActivationTicks(guards, firstSidearmActiveTick, testTick);
            if (testTick == WARMUP_TICKS - 1L) {
                if (workload == Workload.DRY_PRIMARY_SIDEARM_SWITCH) {
                    // Install immediately before target release. Installing
                    // these dry-primary fallbacks during initial entity setup
                    // allowed the controller to activate them without a
                    // target, invalidating the intended simultaneous-switch
                    // timing evidence.
                    guards.forEach(guard -> installPerformanceSidearm(helper, guard));
                }
                recording.start();
            }
            if (testTick == WARMUP_TICKS) {
                // Warmup exists to initialize the same native firing paths,
                // not to carry its already-measured projectile population into
                // the sample. Only bullets owned by this scenario are removed.
                discardScenarioBullets(helper, guards);
                gcCollectionsAtStart[0] = garbageCollectionCount();
                usedHeapAtStart[0] = usedHeapBytes();
                shotRecorder.copyCountsTo(acceptedShotEventsAtStart);
                captureAmmoEvidence(
                        gunStacks,
                        ammoHandlers,
                        ammoRemainingAtStart);
                ArmoryResupplyServerServices.Services armoryServices =
                        ArmoryResupplyServerServices.forLevel(helper.getLevel());
                armoryIndexMetricsAtStart[0] = armoryServices.loadedArmories().metrics();
                armoryControllerTotalsAtStart[0] = armoryControllerTotals(guards);
                loadedArmoriesAtStart[0] = armoryServices.loadedArmories()
                        .loadedCount(helper.getLevel().dimension());
                forcedChunksAtStart[0] = helper.getLevel().getForcedChunks().size();
            }
            if (testTick < WARMUP_TICKS || sampleCount[0] >= SAMPLE_TICKS) {
                return;
            }

            long[] serverTickTimes = server.tickTimes;
            int previousTickIndex = Math.floorMod(server.getTickCount() - 1, serverTickTimes.length);
            samples[sampleCount[0]++] = serverTickTimes[previousTickIndex];
            if (sampleCount[0] == SAMPLE_TICKS) {
                completed[0] = true;
                shotRecorder.copyCountsTo(acceptedShotEventsAtEnd);
                if (shotRecorderRegistered[0]) {
                    MinecraftForge.EVENT_BUS.unregister(shotRecorder);
                    shotRecorderRegistered[0] = false;
                }
                ArmoryEvidence armoryEvidence = captureArmoryEvidence(
                        helper,
                        guards,
                        workload,
                        armoryIndexMetricsAtStart[0],
                        armoryControllerTotalsAtStart[0],
                        loadedArmoriesAtStart[0],
                        forcedChunksAtStart[0]);
                ShotEvidence shotEvidence = writeReport(
                        helper,
                        name,
                        guardCount,
                        weapon,
                        workload,
                        gunStacks,
                        ammoHandlers,
                        samples,
                        gcCollectionsAtStart[0],
                        usedHeapAtStart[0],
                        acceptedShotEventsAtStart,
                        acceptedShotEventsAtEnd,
                        ammoRemainingAtStart,
                        guards,
                        droppedAmmoEntities,
                        firstSidearmActiveTick,
                        armoryEvidence,
                        recording);
                discardScenarioBullets(helper, guards);
                cleanup.forEach(Entity::discard);
                armoryPositions.forEach(position -> helper.setBlock(position, Blocks.AIR));
                releaseScenarioChunks(helper, forcedChunks);
                int minimumShotEvidence = minimumAcceptedShotEvidence(guardCount, weapon, workload);
                int minimumFiringGuards = minimumAcceptedFiringGuards(guardCount, workload);
                int minimumPerActiveGuardEvidence =
                        minimumAcceptedPerActiveGuardShotEvidence(workload);
                int minimumSharingAttempts = minimumAmmoSharingAttempts(guardCount, workload);
                int minimumPersistentDrops = minimumPersistentDroppedAmmo(guardCount, workload);
                int expectedSidearmLoadouts = workload.expectedSidearmLoadouts(guardCount);
                int minimumSidearmActive = workload.minimumSidearmActiveGuards(guardCount);
                int maximumSidearmActive = workload.maximumSidearmActiveGuards(guardCount);
                if (shotEvidence.acceptedTotal() < minimumShotEvidence
                        || shotEvidence.activeFiringGuards() < minimumFiringGuards
                        || shotEvidence.minimumPerActiveGuard()
                                < minimumPerActiveGuardEvidence
                        || shotEvidence.ammoSharingAttemptsObserved() < minimumSharingAttempts
                        || shotEvidence.persistentDroppedAmmoEntities() < minimumPersistentDrops
                        || shotEvidence.sidearmLoadoutsObserved() != expectedSidearmLoadouts
                        || shotEvidence.sidearmActiveGuardsAtEnd() < minimumSidearmActive
                        || shotEvidence.sidearmActiveGuardsAtEnd() > maximumSidearmActive
                        || !shotEvidence.sidearmRoleEvidence().finalRoleMappingValid()
                        || !shotEvidence.sidearmRoleEvidence().activationTimingValid()
                        || shotEvidence.sidearmRoleEvidence().sidearmRoleFiringGuards()
                                < shotEvidence.sidearmRoleEvidence().minimumFiringGuardsPerRole()
                        || shotEvidence.sidearmRoleEvidence().primaryRoleFiringGuards()
                                < shotEvidence.sidearmRoleEvidence().minimumFiringGuardsPerRole()
                        || !armoryEvidence.validFor(workload, guardCount)) {
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
                            + minimumPersistentDrops
                            + ", sidearmLoadouts="
                            + shotEvidence.sidearmLoadoutsObserved()
                            + "/"
                            + expectedSidearmLoadouts
                            + ", sidearmActive="
                            + shotEvidence.sidearmActiveGuardsAtEnd()
                            + "/"
                            + minimumSidearmActive
                            + "-"
                            + maximumSidearmActive
                            + ", roleMappingValid="
                            + shotEvidence.sidearmRoleEvidence().finalRoleMappingValid()
                            + ", activationTimingValid="
                            + shotEvidence.sidearmRoleEvidence().activationTimingValid()
                            + ", roleFiring="
                            + shotEvidence.sidearmRoleEvidence().sidearmRoleFiringGuards()
                            + "/"
                            + shotEvidence.sidearmRoleEvidence().primaryRoleFiringGuards()
                            + "/"
                            + shotEvidence.sidearmRoleEvidence().minimumFiringGuardsPerRole()
                            + ", armoryEvidence="
                            + armoryEvidence);
                    return;
                }
                helper.succeed();
            }
        });
    }

    private static double rowSpacing(Weapon weapon, Workload workload) {
        if (workload == Workload.IDLE
                || workload == Workload.IDLE_WITH_SIDEARMS
                || workload == Workload.DROPPED_AMMO_PRESSURE
                || workload == Workload.DRY_SHARING_PRESSURE
                || workload == Workload.ARMORY_IDLE
                || workload == Workload.ARMORY_SATISFIED
                || workload == Workload.ARMORY_REQUEST_PRESSURE) {
            return 4.0D;
        }
        return switch (weapon) {
            case SHOTGUN -> 16.0D;
            case EXPLOSIVE -> 40.0D;
            default -> workload == Workload.PROTECTED_LANES ? 20.0D : 12.0D;
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
            case "dry-primary-sidearm-switch-50" -> 14;
            case "mixed-explosive-standoff-sidearm-20" -> 15;
            case "idle-armed-sidearms-100" -> 16;
            case "armory-idle-100" -> 17;
            case "armory-satisfied-100" -> 18;
            case "armory-request-pressure-50" -> 19;
            default -> throw new IllegalArgumentException("Unknown performance scenario: " + name);
        };
        return scenarioIndex * 512.0D;
    }

    private static void prepareArmoryFixture(
            GameTestHelper helper,
            Workload workload,
            double scenarioZOffset,
            double columnSpacing,
            double rowSpacing,
            List<BlockPos> positions) {
        if (workload.armoryCount() == 0) {
            return;
        }
        for (int index = 0; index < workload.armoryCount(); index++) {
            BlockPos position;
            ResourceLocation ammoId = null;
            if (workload == Workload.ARMORY_REQUEST_PRESSURE) {
                int column = index;
                position = new BlockPos(
                        (int) Math.floor(2.5D + column * columnSpacing) + 10,
                        FLOOR_Y + 1,
                        (int) Math.floor(scenarioZOffset + 20.5D));
                ammoId = column % 2 == 0
                        ? NINE_MILLIMETER
                        : SEVEN_SIXTY_TWO_BY_THIRTY_NINE;
                // Keep the nearest reachable point beyond the withdrawal
                // radius. A one-block cage correctly blocks the eight
                // approach nodes, but a guard can stand immediately outside
                // it and still satisfy the transactional distance check.
                // This 9x9, four-block-high solid enclosure makes the stocked
                // Armory intentionally unreachable without unloaded chunks or
                // fixture-only changes to production navigation.
                for (int x = position.getX() - 4; x <= position.getX() + 4; x++) {
                    for (int z = position.getZ() - 4; z <= position.getZ() + 4; z++) {
                        helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
                        if (x != position.getX() || z != position.getZ()) {
                            for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                            }
                        }
                    }
                }
            } else {
                int column = index / 10;
                int row = index % 10;
                int x = (int) Math.floor(2.5D + column * columnSpacing)
                        + (workload == Workload.ARMORY_SATISFIED ? 3 : 0);
                int z = (int) Math.floor(scenarioZOffset + 2.5D + row * rowSpacing);
                position = new BlockPos(x, FLOOR_Y + 1, z);
                if (workload == Workload.ARMORY_SATISFIED) {
                    ammoId = NINE_MILLIMETER;
                }
            }
            helper.setBlock(position, ModBlocks.ARMORY.get());
            ArmoryBlockEntity armory = (ArmoryBlockEntity) helper.getLevel()
                    .getBlockEntity(helper.absolutePos(position));
            helper.assertTrue(armory != null, "Could not create the Armory performance fixture");
            if (ammoId != null) {
                ItemStack ammunition = AmmoItemBuilder.create().setId(ammoId).build();
                ammunition.setCount(ammunition.getMaxStackSize());
                armory.getInventory().setStackInSlot(0, ammunition);
            }
            positions.add(position);
        }
    }

    private static void installPerformanceSidearm(
            GameTestHelper helper,
            Guard guard) {
        ItemStack sidearmRemainder = GuardSidearmAccess
                .getConfiguredSidearmInventory(guard)
                .insertItem(0, createGunStack(Weapon.SEMI), false);
        helper.assertTrue(
                sidearmRemainder.isEmpty(),
                "Could not install the performance scenario sidearm");
    }

    private static ArmoryControllerTotals armoryControllerTotals(List<Guard> guards) {
        ArmoryControllerTotals totals = ArmoryControllerTotals.ZERO;
        for (Guard guard : guards) {
            GuardArmoryResupplyController controller =
                    GuardArmoryResupplyAccess.getController(guard);
            if (controller != null) {
                totals = totals.plus(controller.performanceMetrics());
            }
        }
        return totals;
    }

    private static ArmoryEvidence captureArmoryEvidence(
            GameTestHelper helper,
            List<Guard> guards,
            Workload workload,
            LoadedArmoryIndex.Metrics indexStart,
            ArmoryControllerTotals controllerStart,
            int loadedAtStart,
            int forcedChunksAtStart) {
        ArmoryResupplyServerServices.Services services =
                ArmoryResupplyServerServices.forLevel(helper.getLevel());
        LoadedArmoryIndex.Metrics indexEnd = services.loadedArmories().metrics();
        ArmoryControllerTotals controllerEnd = armoryControllerTotals(guards);
        ArmoryControllerTotals controllerDelta = controllerEnd.minus(controllerStart);
        int activeGuardsAtEnd = 0;
        int backoffGuardsAtEnd = 0;
        for (Guard guard : guards) {
            GuardArmoryResupplyController controller =
                    GuardArmoryResupplyAccess.getController(guard);
            if (controller == null) {
                continue;
            }
            ArmoryResupplyState state = controller.snapshot().state();
            if (state.hasActiveRequest()) {
                activeGuardsAtEnd++;
            }
            if (state.isBackoff()) {
                backoffGuardsAtEnd++;
            }
        }
        int activeReservationsBeforeCleanup = services.reservations()
                .activeCount(helper.getLevel().getGameTime());
        if (workload.isArmoryPerformance()) {
            guards.forEach(GuardArmoryResupplyAccess::cancel);
        }
        int activeReservationsAfterCleanup = services.reservations()
                .activeCount(helper.getLevel().getGameTime());
        return new ArmoryEvidence(
                loadedAtStart,
                services.loadedArmories().loadedCount(helper.getLevel().dimension()),
                delta(indexEnd.queries(), indexStart.queries()),
                delta(indexEnd.visitedRecords(), indexStart.visitedRecords()),
                delta(indexEnd.returnedArmories(), indexStart.returnedArmories()),
                controllerDelta,
                controllerEnd.maximumPathAttemptsPerTrip(),
                activeGuardsAtEnd,
                backoffGuardsAtEnd,
                activeReservationsBeforeCleanup,
                activeReservationsAfterCleanup,
                helper.getLevel().getForcedChunks().size() - forcedChunksAtStart);
    }

    private static long delta(long end, long start) {
        return end >= start ? end - start : 0L;
    }

    private static List<ChunkPos> forceScenarioChunks(
            GameTestHelper helper,
            int guardCount,
            double columnSpacing,
            double rowSpacing,
            double scenarioZOffset) {
        int columnCount = Math.max(1, (guardCount + 9) / 10);
        int rowCount = Math.min(10, Math.max(1, guardCount));
        BlockPos minimum = helper.absolutePos(new BlockPos(
                -4,
                0,
                (int) Math.floor(scenarioZOffset - 4.0D)));
        BlockPos maximum = helper.absolutePos(new BlockPos(
                (int) Math.ceil(2.5D + (columnCount - 1) * columnSpacing + 24.0D),
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
            return -6.0D;
        }
        if (phase < 60L) {
            return -6.0D + (phase - 30L) * (12.0D / 30.0D);
        }
        if (phase < 90L) {
            return 6.0D;
        }
        return 6.0D - (phase - 90L) * (12.0D / 30.0D);
    }

    private static void maintainScenarioTargets(
            List<Guard> guards,
            List<Vec3> guardAnchors,
            List<Ravager> targets,
            List<Vec3> targetAnchors,
            Workload workload,
            Weapon weapon,
            long testTick) {
        if (!workload.hasTarget()) {
            return;
        }
        for (int index = 0; index < guards.size(); index++) {
            Guard guard = guards.get(index);
            Ravager target = targets.get(index);
            if (guard.isAlive() && target.isAlive()) {
                if (workload.anchorsGuards()) {
                    Vec3 guardAnchor = guardAnchors.get(index);
                    if (!guard.position().equals(guardAnchor)) {
                        guard.setPos(guardAnchor);
                    }
                    guard.setDeltaMovement(Vec3.ZERO);
                    guard.setSprinting(false);
                }
                Vec3 anchor = targetAnchors.get(index);
                if (!target.position().equals(anchor)) {
                    target.setPos(anchor);
                }
                if (!target.getDeltaMovement().equals(Vec3.ZERO)) {
                    target.setDeltaMovement(Vec3.ZERO);
                }
                boolean targetWithheld = (workload.delaysTargetUntilSample()
                                && testTick < WARMUP_TICKS)
                        || (weapon == Weapon.EXPLOSIVE
                                && workload == Workload.CLEAR_LANES
                                && testTick >= WARMUP_TICKS - 5L
                                && testTick < WARMUP_TICKS);
                if (targetWithheld) {
                    if (guard.getTarget() != null) {
                        guard.setTarget(null);
                    }
                } else if (guard.getTarget() != target) {
                    guard.setTarget(target);
                }
            }
        }
    }

    /** Removes unrelated Guard Villagers AI variance while retaining all add-on goals. */
    private static void retainOnlyAddOnGoals(Guard guard) {
        guard.goalSelector.removeAllGoals(goal -> !(goal instanceof GuardGunCombatGoal)
                && !(goal instanceof SquadCommandMovementGoal)
                && !(goal instanceof GuardArmoryResupplyGoal));
        guard.targetSelector.removeAllGoals(goal -> true);
    }

    private static void prepareLane(
            GameTestHelper helper,
            double guardX,
            double guardZ,
            Workload workload) {
        int firstX = (int) guardX - 1;
        int lastX = workload.hasTarget()
                ? (int) Math.ceil(guardX + workload.maximumTargetDistance()) + 1
                : (int) guardX + 1;
        int laneHalfWidth = workload == Workload.PROTECTED_LANES
                ? 5
                : workload.anchorsGuards() ? 4 : 1;
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
            long testTick,
            AcceptedShotRecorder shotRecorder,
            NominalShotClock nominalShotClock) {
        if (weapon == Weapon.UNARMED) {
            return;
        }
        boolean triggerDue = testTick % weapon.nominalShotIntervalTicks == 0L;
        long currentTime = System.currentTimeMillis();
        for (int index = 0; index < operators.size(); index++) {
            IGunOperator operator = operators.get(index);
            var data = operator.getDataHolder();
            data.drawTimestamp = currentTime - 10_000L;
            if (data.reloadTimestamp >= 0L) {
                // TaCZ reload scripts use wall-clock time. Advance an active
                // reload only, preserving the native script and inventory path
                // while keeping the unthrottled GameTest workload meaningful.
                data.reloadTimestamp = currentTime - 10_000L;
            }
            if (nominalShotClock.shouldAdvance(
                    index,
                    triggerDue,
                    shotRecorder.count(index))) {
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
            List<ItemStack> gunStacks,
            List<GuardAmmoHandler> ammoHandlers,
            long[] samples,
            long gcCollectionsAtStart,
            long usedHeapAtStart,
            int[] acceptedShotEventsAtStart,
            int[] acceptedShotEventsAtEnd,
            int[] ammoRemainingAtStart,
            List<Guard> guards,
            List<ItemEntity> droppedAmmoEntities,
            int[] firstSidearmActiveTick,
            ArmoryEvidence armoryEvidence,
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
        int acceptedShotEventCount = 0;
        int nativeAmmoConsumed = 0;
        int[] perGuardShotEvidence = new int[guardCount];
        for (int index = 0; index < guardCount; index++) {
            int acceptedShotEvents = Math.max(
                    0,
                    acceptedShotEventsAtEnd[index]
                            - acceptedShotEventsAtStart[index]);
            int ammoConsumed = Math.max(
                    0,
                    ammoRemainingAtStart[index]
                            - remainingAmmo(gunStacks.get(index), ammoHandlers.get(index)));
            acceptedShotEventCount += acceptedShotEvents;
            nativeAmmoConsumed += ammoConsumed;
            perGuardShotEvidence[index] = Math.max(acceptedShotEvents, ammoConsumed);
        }
        int acceptedShotEvidence = Math.max(acceptedShotEventCount, nativeAmmoConsumed);
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
        SidearmRoleEvidence sidearmRoleEvidence = sidearmRoleEvidence(
                guards,
                workload,
                perGuardShotEvidence,
                firstSidearmActiveTick);
        String reportDirectory = System.getProperty(REPORT_DIRECTORY_PROPERTY);
        helper.assertTrue(reportDirectory != null && !reportDirectory.isBlank(),
                "Phase 8 performance report directory was not configured");
        Path outputDirectory = Path.of(reportDirectory);
        String recordingFileName = name + ".jfr";
        try {
            recording.stop();
            Files.createDirectories(outputDirectory);
            recording.dump(outputDirectory.resolve(recordingFileName));
        } catch (IOException | RuntimeException exception) {
            helper.fail("Could not write the Phase 8 JFR recording: " + exception.getMessage());
        } finally {
            recording.close();
        }

        JsonObject report = new JsonObject();
        report.addProperty("scenario", name);
        report.addProperty("guards", guardCount);
        report.addProperty("weapon", workload.reportWeapon(weapon));
        report.addProperty("workload", workload.name().toLowerCase());
        report.addProperty("armories", workload.armoryCount());
        report.addProperty("warmupTicks", WARMUP_TICKS);
        report.addProperty("sampleTicks", samples.length);
        report.addProperty("configProfile", "default-v9");
        report.addProperty("configVersion", VillagerGuardGunnersConfig.CONFIG_VERSION);
        report.add("guardGunnerConfig", configSnapshot());
        report.addProperty("averageMspt", averageMspt);
        report.addProperty("p50Mspt", percentileMilliseconds(sorted, 0.50D));
        report.addProperty("p95Mspt", percentileMilliseconds(sorted, 0.95D));
        report.addProperty("p99Mspt", percentileMilliseconds(sorted, 0.99D));
        report.addProperty("maxMspt", sorted[sorted.length - 1] / 1_000_000.0D);
        // Keep the established report key for verifier compatibility. Its value
        // now comes from accepted server-side GunShootEvent instances instead
        // of TaCZ's reload-resettable ShooterDataHolder.shootCount field.
        report.addProperty("nativeTaCZShootCount", acceptedShotEventCount);
        report.addProperty("acceptedServerGunShootEvents", acceptedShotEventCount);
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
        int sidearmLoadoutsObserved = sidearmLoadoutsObserved(guards);
        int sidearmActiveGuardsAtEnd = sidearmActiveGuardsAtEnd(guards);
        report.addProperty("sidearmLoadoutsObserved", sidearmLoadoutsObserved);
        report.addProperty(
                "expectedSidearmLoadouts",
                workload.expectedSidearmLoadouts(guardCount));
        report.addProperty("sidearmActiveGuardsAtEnd", sidearmActiveGuardsAtEnd);
        report.addProperty(
                "minimumSidearmActiveGuards",
                workload.minimumSidearmActiveGuards(guardCount));
        report.addProperty(
                "maximumSidearmActiveGuards",
                workload.maximumSidearmActiveGuards(guardCount));
        report.addProperty(
                "sidearmActivationTriggerTick",
                workload.delaysTargetUntilSample() ? WARMUP_TICKS : -1);
        report.addProperty(
                "firstObservedSidearmActivationTick",
                firstObservedActivationTick(firstSidearmActiveTick));
        report.addProperty(
                "lastObservedSidearmActivationTick",
                lastObservedActivationTick(firstSidearmActiveTick));
        report.addProperty(
                "sidearmRoleFiringGuards",
                sidearmRoleEvidence.sidearmRoleFiringGuards());
        report.addProperty(
                "primaryRoleFiringGuards",
                sidearmRoleEvidence.primaryRoleFiringGuards());
        report.addProperty(
                "minimumFiringGuardsPerRole",
                sidearmRoleEvidence.minimumFiringGuardsPerRole());
        report.addProperty(
                "finalSidearmRoleMappingValid",
                sidearmRoleEvidence.finalRoleMappingValid());
        report.addProperty(
                "sidearmActivationTimingValid",
                sidearmRoleEvidence.activationTimingValid());
        armoryEvidence.addTo(report);
        JsonArray perGuardEvidence = new JsonArray();
        Arrays.stream(perGuardShotEvidence).forEach(perGuardEvidence::add);
        report.add("perGuardAcceptedShotEvidence", perGuardEvidence);
        JsonArray perGuardActivationTicks = new JsonArray();
        Arrays.stream(firstSidearmActiveTick).forEach(perGuardActivationTicks::add);
        report.add("perGuardFirstSidearmActivationTick", perGuardActivationTicks);
        report.add(
                "perGuardFinalState",
                perGuardFinalState(
                        guards,
                        weapon,
                        workload,
                        perGuardShotEvidence,
                        firstSidearmActiveTick));
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
                sidearmLoadoutsObserved,
                sidearmActiveGuardsAtEnd,
                sidearmRoleEvidence,
                perGuardShotEvidence);
    }

    /**
     * Captures an end-of-sample explanation for non-participating guards. This
     * remains outside the timed sample and makes intermittent fixture failures
     * actionable without adding allocations to the production combat loop.
     */
    private static JsonArray perGuardFinalState(
            List<Guard> guards,
            Weapon weapon,
            Workload workload,
            int[] perGuardShotEvidence,
            int[] firstSidearmActiveTick) {
        JsonArray states = new JsonArray();
        for (int index = 0; index < guards.size(); index++) {
            Guard guard = guards.get(index);
            JsonObject state = new JsonObject();
            state.addProperty("guardIndex", index);
            state.addProperty("entityId", guard.getId());
            state.addProperty(
                    "mainHand",
                    BuiltInRegistries.ITEM.getKey(guard.getMainHandItem().getItem()).toString());
            state.addProperty(
                    "offHand",
                    BuiltInRegistries.ITEM.getKey(guard.getOffhandItem().getItem()).toString());
            state.addProperty("usingItem", guard.isUsingItem());
            state.addProperty("sidearmState", GuardSidearmAccess.getState(guard).name());
            state.addProperty("activeGunId", activeGunId(guard.getMainHandItem()));
            state.addProperty(
                    "expectedGunId",
                    workload.expectsSidearmActiveForRole(index)
                            ? Weapon.SEMI.gunId.toString()
                            : workload.weaponForGuard(index, weapon).gunId == null
                                    ? "none"
                                    : workload.weaponForGuard(index, weapon).gunId.toString());
            state.addProperty(
                    "expectedSidearmActive",
                    workload.expectsSidearmActiveForRole(index));
            state.addProperty(
                    "expectedTargetDistance",
                    workload.hasTarget() ? workload.targetDistance(index) : -1.0D);
            state.addProperty("acceptedShotEvidence", perGuardShotEvidence[index]);
            state.addProperty(
                    "firstSidearmActivationTick",
                    firstSidearmActiveTick[index]);
            state.addProperty(
                    "usedHand",
                    guard.isUsingItem() ? guard.getUsedItemHand().name() : "NONE");
            state.addProperty("hasTarget", guard.getTarget() != null);
            state.addProperty("x", guard.getX());
            state.addProperty("y", guard.getY());
            state.addProperty("z", guard.getZ());
            if (guard.getTarget() != null) {
                state.addProperty("targetX", guard.getTarget().getX());
                state.addProperty("targetY", guard.getTarget().getY());
                state.addProperty("targetZ", guard.getTarget().getZ());
                state.addProperty("targetDistance", guard.distanceTo(guard.getTarget()));
            }
            JsonArray runningGoals = new JsonArray();
            guard.goalSelector.getAvailableGoals().stream()
                    .filter(wrapped -> wrapped.isRunning())
                    .map(wrapped -> wrapped.getGoal().getClass().getName())
                    .sorted()
                    .forEach(runningGoals::add);
            state.add("runningGoals", runningGoals);
            GuardGunCombatGoal goal = guard.goalSelector.getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(GuardGunCombatGoal.class::isInstance)
                    .map(GuardGunCombatGoal.class::cast)
                    .findFirst()
                    .orElse(null);
            if (goal != null) {
                GuardGunCombatGoal.Diagnostics diagnostics = goal.captureDiagnostics();
                state.addProperty("goalActive", diagnostics.goalActive());
                state.addProperty("decision", diagnostics.decision().name());
                state.addProperty("engagement", diagnostics.engagementReason().name());
                state.addProperty("lineOfSight", diagnostics.cachedLineOfSight());
                state.addProperty("visibleTicks", diagnostics.visibleTicks());
                addFiniteProperty(state, "yawErrorDegrees", diagnostics.yawErrorDegrees());
                addFiniteProperty(state, "pitchErrorDegrees", diagnostics.pitchErrorDegrees());
                state.addProperty("safety", diagnostics.safety().status().name());
                state.addProperty(
                        "safetyBlockerId",
                        diagnostics.safety().blocker() == null
                                ? -1
                                : diagnostics.safety().blocker().getId());
                state.addProperty("nativeAiming", diagnostics.nativeState().aiming());
                state.addProperty("nativeStateReady", diagnostics.nativeState().weaponDataReady());
                state.addProperty("nativeSuspended", diagnostics.nativeState().suspended());
                state.addProperty("loadedAmmo", diagnostics.nativeState().loadedAmmo());
                state.addProperty("chambered", diagnostics.nativeState().chambered());
                state.addProperty("reserveItems", diagnostics.nativeState().reserveItems());
                state.addProperty("dryRetryTicks", diagnostics.nativeState().dryRetryTicks());
                state.addProperty("reloadState", diagnostics.nativeState().reloadState());
                state.addProperty("bolting", diagnostics.nativeState().bolting());
                state.addProperty(
                        "drawCooldownMillis",
                        diagnostics.nativeState().drawCooldownMillis());
                state.addProperty(
                        "shootCooldownMillis",
                        diagnostics.nativeState().shootCooldownMillis());
                state.addProperty(
                        "lastShootResult",
                        diagnostics.nativeState().lastShootResult() == null
                                ? "NONE"
                                : diagnostics.nativeState().lastShootResult().name());
            } else {
                state.addProperty("goalActive", false);
                state.addProperty("decision", "GOAL_MISSING");
            }
            states.add(state);
        }
        return states;
    }

    private static void observeSidearmActivationTicks(
            List<Guard> guards,
            int[] firstSidearmActiveTick,
            long testTick) {
        for (int index = 0; index < guards.size(); index++) {
            if (firstSidearmActiveTick[index] < 0
                    && GuardSidearmAccess.getState(guards.get(index))
                            == SidearmState.SIDEARM_ACTIVE) {
                firstSidearmActiveTick[index] = Math.toIntExact(testTick);
            }
        }
    }

    private static SidearmRoleEvidence sidearmRoleEvidence(
            List<Guard> guards,
            Workload workload,
            int[] perGuardShotEvidence,
            int[] firstSidearmActiveTick) {
        int sidearmRoleFiringGuards = 0;
        int primaryRoleFiringGuards = 0;
        boolean finalRoleMappingValid = true;
        boolean activationTimingValid = true;
        for (int index = 0; index < guards.size(); index++) {
            Guard guard = guards.get(index);
            boolean expectedSidearm = workload.expectsSidearmActiveForRole(index);
            if (workload == Workload.MIXED_EXPLOSIVE_STANDOFF_SIDEARM) {
                if (expectedSidearm) {
                    if (perGuardShotEvidence[index] > 0) {
                        sidearmRoleFiringGuards++;
                    }
                } else if (perGuardShotEvidence[index] > 0) {
                    primaryRoleFiringGuards++;
                }
                String expectedGunId = expectedSidearm
                        ? Weapon.SEMI.gunId.toString()
                        : Weapon.EXPLOSIVE.gunId.toString();
                SidearmState expectedState = expectedSidearm
                        ? SidearmState.SIDEARM_ACTIVE
                        : SidearmState.PRIMARY_ACTIVE;
                finalRoleMappingValid &= GuardSidearmAccess.getState(guard) == expectedState
                        && activeGunId(guard.getMainHandItem()).equals(expectedGunId);
            }
            if (workload == Workload.DRY_PRIMARY_SIDEARM_SWITCH) {
                int activationTick = firstSidearmActiveTick[index];
                activationTimingValid &= activationTick < 0
                        || (activationTick >= WARMUP_TICKS
                                && activationTick <= WARMUP_TICKS + 200);
                if (GuardSidearmAccess.getState(guard) == SidearmState.SIDEARM_ACTIVE) {
                    activationTimingValid &= activationTick >= WARMUP_TICKS;
                }
            }
        }
        int minimumFiringGuardsPerRole = workload
                        == Workload.MIXED_EXPLOSIVE_STANDOFF_SIDEARM
                ? Math.max(1, ((guards.size() / 2) * 7 + 9) / 10)
                : 0;
        return new SidearmRoleEvidence(
                sidearmRoleFiringGuards,
                primaryRoleFiringGuards,
                minimumFiringGuardsPerRole,
                finalRoleMappingValid,
                activationTimingValid);
    }

    private static int firstObservedActivationTick(int[] activationTicks) {
        return Arrays.stream(activationTicks)
                .filter(tick -> tick >= 0)
                .min()
                .orElse(-1);
    }

    private static int lastObservedActivationTick(int[] activationTicks) {
        return Arrays.stream(activationTicks).max().orElse(-1);
    }

    private static String activeGunId(ItemStack stack) {
        if (!(stack.getItem() instanceof IGun gun)) {
            return "none";
        }
        try {
            ResourceLocation gunId = gun.getGunId(stack);
            return gunId == null ? "unreadable" : gunId.toString();
        } catch (RuntimeException exception) {
            return "unreadable";
        }
    }

    private static void addFiniteProperty(JsonObject object, String name, double value) {
        if (Double.isFinite(value)) {
            object.addProperty(name, value);
        } else {
            object.add(name, JsonNull.INSTANCE);
        }
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

    private static int sidearmLoadoutsObserved(List<Guard> guards) {
        int count = 0;
        for (Guard guard : guards) {
            SidearmState state = GuardSidearmAccess.getState(guard);
            if (state == SidearmState.SIDEARM_ACTIVE
                    || !GuardSidearmAccess.getConfiguredSidearmInventory(guard)
                            .getStackInSlot(0)
                            .isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static int sidearmActiveGuardsAtEnd(List<Guard> guards) {
        int count = 0;
        for (Guard guard : guards) {
            if (GuardSidearmAccess.getState(guard) == SidearmState.SIDEARM_ACTIVE) {
                count++;
            }
        }
        return count;
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

        JsonObject squads = new JsonObject();
        squads.addProperty("commandDispatchRadius", config.squads.commandDispatchRadius.get());
        squads.addProperty("maximumAffectedGuards", config.squads.maximumAffectedGuards.get());
        squads.addProperty(
                "positionalCommandDurationTicks",
                config.squads.positionalCommandDurationTicks.get());
        squads.addProperty(
                "focusCommandDurationTicks",
                config.squads.focusCommandDurationTicks.get());
        squads.addProperty("defendRadius", config.squads.defendRadius.get());
        squads.addProperty("airCommandRange", config.squads.airCommandRange.get());
        snapshot.add("squads", squads);

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

        JsonObject armoryResupply = new JsonObject();
        armoryResupply.addProperty("enabled", config.armoryResupply.enabled.get());
        armoryResupply.addProperty(
                "checkIntervalTicks",
                config.armoryResupply.checkIntervalTicks.get());
        armoryResupply.addProperty("searchRadius", config.armoryResupply.searchRadius.get());
        armoryResupply.addProperty(
                "maximumCandidateArmories",
                config.armoryResupply.maximumCandidateArmories.get());
        armoryResupply.addProperty(
                "lowReserveMagazines",
                config.armoryResupply.lowReserveMagazines.get());
        armoryResupply.addProperty(
                "targetReserveMagazines",
                config.armoryResupply.targetReserveMagazines.get());
        armoryResupply.addProperty("movementSpeed", config.armoryResupply.movementSpeed.get());
        armoryResupply.addProperty(
                "requestLifetimeTicks",
                config.armoryResupply.requestLifetimeTicks.get());
        armoryResupply.addProperty(
                "reservationLifetimeTicks",
                config.armoryResupply.reservationLifetimeTicks.get());
        armoryResupply.addProperty(
                "successCooldownTicks",
                config.armoryResupply.successCooldownTicks.get());
        armoryResupply.addProperty(
                "noSupplyRetryTicks",
                config.armoryResupply.noSupplyRetryTicks.get());
        armoryResupply.addProperty(
                "unreachableRetryTicks",
                config.armoryResupply.unreachableRetryTicks.get());
        snapshot.add("armoryResupply", armoryResupply);

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

    private static void captureAmmoEvidence(
            List<ItemStack> gunStacks,
            List<GuardAmmoHandler> ammoHandlers,
            int[] remainingAmmo) {
        for (int index = 0; index < gunStacks.size(); index++) {
            remainingAmmo[index] = remainingAmmo(gunStacks.get(index), ammoHandlers.get(index));
        }
    }

    /** Counts completed, non-cancelled native TaCZ shots per scenario guard. */
    private static final class AcceptedShotRecorder {
        private final Map<Guard, Integer> guardIndices = new IdentityHashMap<>();
        private final int[] counts;

        private AcceptedShotRecorder(List<Guard> guards) {
            counts = new int[guards.size()];
            for (int index = 0; index < guards.size(); index++) {
                guardIndices.put(guards.get(index), index);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
        public void onGunShoot(GunShootEvent event) {
            if (event.getLogicalSide() != LogicalSide.SERVER
                    || !(event.getShooter() instanceof Guard guard)) {
                return;
            }
            Integer index = guardIndices.get(guard);
            if (index != null) {
                counts[index]++;
            }
        }

        private void copyCountsTo(int[] destination) {
            if (destination.length != counts.length) {
                throw new IllegalArgumentException("Shot evidence length changed");
            }
            System.arraycopy(counts, 0, destination, 0, counts.length);
        }

        private int count(int index) {
            return counts[index];
        }
    }

    /**
     * Keeps each guard's nominal trigger window open until that guard actually
     * completes one TaCZ shot. This prevents a one-tick native retry from
     * becoming permanently phase-locked outside a global three-tick window.
     */
    private static final class NominalShotClock {
        private final boolean[] openWindows;
        private final int[] countsAtOpen;

        private NominalShotClock(int guardCount) {
            openWindows = new boolean[guardCount];
            countsAtOpen = new int[guardCount];
        }

        private boolean shouldAdvance(
                int guardIndex,
                boolean triggerDue,
                int acceptedShotCount) {
            if (openWindows[guardIndex]
                    && acceptedShotCount > countsAtOpen[guardIndex]) {
                openWindows[guardIndex] = false;
            }
            if (triggerDue && !openWindows[guardIndex]) {
                openWindows[guardIndex] = true;
                countsAtOpen[guardIndex] = acceptedShotCount;
            }
            return openWindows[guardIndex];
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
        return workload.expectsNativeShots()
                ? guardCount
                : 0;
    }

    private static int minimumAcceptedFiringGuards(int guardCount, Workload workload) {
        return workload.expectsNativeShots()
                ? Math.max(1, (guardCount * 4 + 4) / 5)
                : 0;
    }

    private static int minimumAcceptedPerActiveGuardShotEvidence(Workload workload) {
        return workload.expectsNativeShots()
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
        IDLE_WITH_SIDEARMS,
        CLEAR_LANES,
        OBSTACLES,
        PROTECTED_LANES,
        DROPPED_AMMO_PRESSURE,
        DRY_SHARING_PRESSURE,
        DRY_PRIMARY_SIDEARM_SWITCH,
        MIXED_EXPLOSIVE_STANDOFF_SIDEARM,
        ARMORY_IDLE,
        ARMORY_SATISFIED,
        ARMORY_REQUEST_PRESSURE;

        private boolean hasTarget() {
            return this != IDLE
                    && this != IDLE_WITH_SIDEARMS
                    && this != DROPPED_AMMO_PRESSURE
                    && this != ARMORY_IDLE
                    && this != ARMORY_SATISFIED
                    && this != ARMORY_REQUEST_PRESSURE;
        }

        private boolean hasSidearmLoadout() {
            return this == IDLE_WITH_SIDEARMS
                    || this == DRY_PRIMARY_SIDEARM_SWITCH
                    || this == MIXED_EXPLOSIVE_STANDOFF_SIDEARM;
        }

        private boolean delaysTargetUntilSample() {
            return this == DRY_PRIMARY_SIDEARM_SWITCH;
        }

        private boolean anchorsGuards() {
            return this == CLEAR_LANES
                    || this == DRY_PRIMARY_SIDEARM_SWITCH
                    || this == MIXED_EXPLOSIVE_STANDOFF_SIDEARM;
        }

        private boolean expectsNativeShots() {
            return this == CLEAR_LANES
                    || this == PROTECTED_LANES
                    || this == DRY_PRIMARY_SIDEARM_SWITCH
                    || this == MIXED_EXPLOSIVE_STANDOFF_SIDEARM;
        }

        private boolean expectsSidearmActiveForRole(int guardIndex) {
            return this == DRY_PRIMARY_SIDEARM_SWITCH
                    || (this == MIXED_EXPLOSIVE_STANDOFF_SIDEARM
                            && guardIndex % 2 == 0);
        }

        private double targetDistance(int guardIndex) {
            return this == MIXED_EXPLOSIVE_STANDOFF_SIDEARM
                    ? guardIndex % 2 == 0 ? 4.0D : 20.0D
                    : 12.0D;
        }

        private double maximumTargetDistance() {
            return this == MIXED_EXPLOSIVE_STANDOFF_SIDEARM ? 20.0D : 12.0D;
        }

        private double columnSpacing() {
            // A 20-block RPG target would otherwise occupy the next guard
            // column and correctly trip explosive ally protection. The moving
            // friendly workload also needs room for independent repositioning.
            if (this == ARMORY_IDLE) {
                return 4.0D;
            }
            return this == MIXED_EXPLOSIVE_STANDOFF_SIDEARM
                            || this == PROTECTED_LANES
                    ? 32.0D
                    : 20.0D;
        }

        private int armoryCount() {
            return switch (this) {
                case ARMORY_IDLE, ARMORY_SATISFIED -> 100;
                case ARMORY_REQUEST_PRESSURE -> 5;
                default -> 0;
            };
        }

        private boolean isArmoryPerformance() {
            return this == ARMORY_IDLE
                    || this == ARMORY_SATISFIED
                    || this == ARMORY_REQUEST_PRESSURE;
        }

        private Weapon weaponForGuard(int guardIndex, Weapon fallback) {
            if (this != ARMORY_REQUEST_PRESSURE) {
                return fallback;
            }
            return guardIndex % 2 == 0
                    ? Weapon.DRY_SEMI
                    : Weapon.DRY_AUTOMATIC;
        }

        private String reportWeapon(Weapon fallback) {
            return this == ARMORY_REQUEST_PRESSURE
                    ? "mixed"
                    : fallback.gunId == null ? "none" : fallback.gunId.toString();
        }

        private int expectedSidearmLoadouts(int guardCount) {
            return hasSidearmLoadout() ? guardCount : 0;
        }

        private int minimumSidearmActiveGuards(int guardCount) {
            return switch (this) {
                case DRY_PRIMARY_SIDEARM_SWITCH -> Math.max(1, (guardCount * 4 + 4) / 5);
                case MIXED_EXPLOSIVE_STANDOFF_SIDEARM -> guardCount / 2;
                default -> 0;
            };
        }

        private int maximumSidearmActiveGuards(int guardCount) {
            return switch (this) {
                case DRY_PRIMARY_SIDEARM_SWITCH -> guardCount;
                case MIXED_EXPLOSIVE_STANDOFF_SIDEARM -> guardCount / 2;
                default -> 0;
            };
        }
    }

    private record ArmoryControllerTotals(
            long assessments,
            long needAssessments,
            long armoryQueries,
            long candidateRecords,
            long sourceSlotInspections,
            long reservationAttempts,
            long pathConstructionAttempts,
            int maximumPathAttemptsPerTrip,
            long successfulTransfers,
            long transferredUnits,
            long noSupplyOutcomes,
            long unreachableOutcomes) {
        private static final ArmoryControllerTotals ZERO = new ArmoryControllerTotals(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0L, 0L, 0L, 0L);

        private ArmoryControllerTotals plus(
                GuardArmoryResupplyController.RuntimeMetrics metrics) {
            return new ArmoryControllerTotals(
                    assessments + metrics.assessments(),
                    needAssessments + metrics.needAssessments(),
                    armoryQueries + metrics.armoryQueries(),
                    candidateRecords + metrics.candidateRecords(),
                    sourceSlotInspections + metrics.sourceSlotInspections(),
                    reservationAttempts + metrics.reservationAttempts(),
                    pathConstructionAttempts + metrics.pathConstructionAttempts(),
                    Math.max(maximumPathAttemptsPerTrip, metrics.maximumPathAttemptsPerTrip()),
                    successfulTransfers + metrics.successfulTransfers(),
                    transferredUnits + metrics.transferredUnits(),
                    noSupplyOutcomes + metrics.noSupplyOutcomes(),
                    unreachableOutcomes + metrics.unreachableOutcomes());
        }

        private ArmoryControllerTotals minus(ArmoryControllerTotals start) {
            return new ArmoryControllerTotals(
                    delta(assessments, start.assessments),
                    delta(needAssessments, start.needAssessments),
                    delta(armoryQueries, start.armoryQueries),
                    delta(candidateRecords, start.candidateRecords),
                    delta(sourceSlotInspections, start.sourceSlotInspections),
                    delta(reservationAttempts, start.reservationAttempts),
                    delta(pathConstructionAttempts, start.pathConstructionAttempts),
                    maximumPathAttemptsPerTrip,
                    delta(successfulTransfers, start.successfulTransfers),
                    delta(transferredUnits, start.transferredUnits),
                    delta(noSupplyOutcomes, start.noSupplyOutcomes),
                    delta(unreachableOutcomes, start.unreachableOutcomes));
        }
    }

    private record ArmoryEvidence(
            int loadedAtStart,
            int loadedAtEnd,
            long indexQueries,
            long indexVisitedRecords,
            long indexReturnedArmories,
            ArmoryControllerTotals controller,
            int maximumPathAttemptsPerTrip,
            int activeGuardsAtEnd,
            int backoffGuardsAtEnd,
            int activeReservationsBeforeCleanup,
            int activeReservationsAfterCleanup,
            int forcedChunkCountDelta) {

        private void addTo(JsonObject report) {
            report.addProperty("loadedArmoriesAtStart", loadedAtStart);
            report.addProperty("loadedArmoriesAtEnd", loadedAtEnd);
            report.addProperty("armoryIndexQueries", indexQueries);
            report.addProperty("armoryIndexVisitedRecords", indexVisitedRecords);
            report.addProperty("armoryIndexReturnedArmories", indexReturnedArmories);
            report.addProperty("armoryAssessments", controller.assessments());
            report.addProperty("armoryNeedAssessments", controller.needAssessments());
            report.addProperty("armoryControllerQueries", controller.armoryQueries());
            report.addProperty("armoryCandidateRecords", controller.candidateRecords());
            report.addProperty("armorySourceSlotInspections", controller.sourceSlotInspections());
            report.addProperty("armoryReservationAttempts", controller.reservationAttempts());
            report.addProperty(
                    "armoryPathConstructionAttempts",
                    controller.pathConstructionAttempts());
            report.addProperty("armoryMaximumPathAttemptsPerTrip", maximumPathAttemptsPerTrip);
            report.addProperty("armorySuccessfulTransfers", controller.successfulTransfers());
            report.addProperty("armoryTransferredUnits", controller.transferredUnits());
            report.addProperty("armoryNoSupplyOutcomes", controller.noSupplyOutcomes());
            report.addProperty("armoryUnreachableOutcomes", controller.unreachableOutcomes());
            report.addProperty("armoryActiveGuardsAtEnd", activeGuardsAtEnd);
            report.addProperty("armoryBackoffGuardsAtEnd", backoffGuardsAtEnd);
            report.addProperty(
                    "activeArmoryReservationsBeforeCleanup",
                    activeReservationsBeforeCleanup);
            report.addProperty(
                    "activeArmoryReservationsAfterCleanup",
                    activeReservationsAfterCleanup);
            report.addProperty("armoryForcedChunkCountDelta", forcedChunkCountDelta);
        }

        private boolean validFor(Workload workload, int guardCount) {
            long candidateLimit = controller.armoryQueries()
                    * ModConfigs.GUARD_GUNNERS.armoryResupply.maximumCandidateArmories.get();
            long slotLimit = indexReturnedArmories
                    * ArmoryResupplyLimits.HARD_MAX_RESERVATION_SOURCE_SLOTS;
            boolean generallyValid = loadedAtStart == workload.armoryCount()
                    && loadedAtEnd == workload.armoryCount()
                    && indexQueries == controller.armoryQueries()
                    && indexVisitedRecords == controller.candidateRecords()
                    && indexVisitedRecords <= candidateLimit
                    && controller.sourceSlotInspections() <= slotLimit
                    && controller.reservationAttempts()
                            <= controller.sourceSlotInspections()
                    && maximumPathAttemptsPerTrip
                            <= ArmoryResupplyLimits.HARD_MAX_PATH_ATTEMPTS
                    && activeGuardsAtEnd >= 0
                    && activeGuardsAtEnd <= guardCount
                    && backoffGuardsAtEnd >= 0
                    && backoffGuardsAtEnd <= guardCount
                    && activeReservationsBeforeCleanup >= 0
                    && activeReservationsAfterCleanup >= 0
                    && forcedChunkCountDelta == 0;
            if (!generallyValid || !workload.isArmoryPerformance()) {
                return generallyValid;
            }
            if (activeReservationsAfterCleanup != 0
                    || controller.successfulTransfers() != 0L
                    || controller.transferredUnits() != 0L) {
                return false;
            }
            return switch (workload) {
                case ARMORY_IDLE -> controller.assessments() == 0L
                        && indexQueries == 0L
                        && indexVisitedRecords == 0L
                        && controller.sourceSlotInspections() == 0L
                        && controller.pathConstructionAttempts() == 0L
                        && controller.reservationAttempts() == 0L;
                case ARMORY_SATISFIED -> controller.assessments() > 0L
                        && controller.needAssessments() == controller.assessments()
                        && indexQueries == 0L
                        && indexVisitedRecords == 0L
                        && controller.sourceSlotInspections() == 0L
                        && controller.pathConstructionAttempts() == 0L
                        && controller.reservationAttempts() == 0L;
                case ARMORY_REQUEST_PRESSURE -> controller.assessments() > 0L
                        && controller.needAssessments() > 0L
                        && indexQueries > 0L
                        && indexVisitedRecords > 0L
                        && indexReturnedArmories > 0L
                        && controller.sourceSlotInspections() > 0L
                        && controller.reservationAttempts() > 0L
                        && controller.pathConstructionAttempts() > 0L
                        && maximumPathAttemptsPerTrip > 0
                        && controller.noSupplyOutcomes() > 0L
                        && controller.unreachableOutcomes() > 0L;
                default -> false;
            };
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
            int sidearmLoadoutsObserved,
            int sidearmActiveGuardsAtEnd,
            SidearmRoleEvidence sidearmRoleEvidence,
            int[] perGuard) {
    }

    private record SidearmRoleEvidence(
            int sidearmRoleFiringGuards,
            int primaryRoleFiringGuards,
            int minimumFiringGuardsPerRole,
            boolean finalRoleMappingValid,
            boolean activationTimingValid) {
    }

    private enum Weapon {
        UNARMED(null, null, 0, false, 1, null, 0),
        SEMI("tacz:glock_17", FireMode.SEMI, 10_000, false, 3, null, 0),
        DRY_SEMI("tacz:glock_17", FireMode.SEMI, 0, false, 3, null, 0),
        AUTOMATIC("tacz:ak47", FireMode.AUTO, 10_000, false, 2, null, 0),
        DRY_AUTOMATIC("tacz:ak47", FireMode.AUTO, 0, false, 2, null, 0),
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

    private static ItemStack createGunStack(Weapon weapon) {
        return GunItemBuilder.create()
                .setId(weapon.gunId)
                .setFireMode(weapon.fireMode)
                .setAmmoCount(weapon.ammoCount)
                .setAmmoInBarrel(weapon.ammoCount > 0)
                .setHeatData(weapon.heatData)
                .build();
    }
}
