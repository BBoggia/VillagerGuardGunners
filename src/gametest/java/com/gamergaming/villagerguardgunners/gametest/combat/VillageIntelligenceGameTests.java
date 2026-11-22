package com.gamergaming.villagerguardgunners.gametest.combat;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.squad.SquadMembership;
import com.gamergaming.villagerguardgunners.village.*;
import com.gamergaming.villagerguardgunners.village.coverage.live.*;
import com.google.gson.GsonBuilder;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Observations of normal goal arbitration, with explicit population and participation. */
@GameTestHolder(VillagerGuardGunners.MODID + "_village_baseline")
@PrefixGameTestTemplate(false)
public final class VillageIntelligenceGameTests {
    private static final String ARENA = "villagerguardgunners/gametest_arena";
    private static final String TEMPLATE_NAMESPACE = VillagerGuardGunners.MODID + "_village_baseline";
    private static final List<Fixture> OPEN = new ArrayList<>();
    private VillageIntelligenceGameTests() { }

    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = ARENA, batch = "village_default", timeoutTicks = 640)
    public static void coverageDisabled(GameTestHelper h) { new Fixture(h, "village_default").run(); }
    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = ARENA, batch = "village_coverage", timeoutTicks = 640)
    public static void mixedParticipation(GameTestHelper h) { new Fixture(h, "village_coverage").run(); }
    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = ARENA, batch = "village_crowd", timeoutTicks = 640)
    public static void crowdedVillage(GameTestHelper h) { new Fixture(h, "village_crowd").run(); }
    @GameTest(templateNamespace = TEMPLATE_NAMESPACE, template = ARENA, batch = "village_divided", timeoutTicks = 640)
    public static void disconnectedHomes(GameTestHelper h) { new Fixture(h, "village_divided").run(); }

    @AfterBatch(batch = "village_default") public static void afterDefault(ServerLevel l) { closeAll(); }
    @AfterBatch(batch = "village_coverage") public static void afterCoverage(ServerLevel l) { closeAll(); }
    @AfterBatch(batch = "village_crowd") public static void afterCrowd(ServerLevel l) { closeAll(); }
    @AfterBatch(batch = "village_divided") public static void afterDivided(ServerLevel l) { closeAll(); }
    private static void closeAll() { for (Fixture f : List.copyOf(OPEN)) f.close(); }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerLevel level;
        final String scenario;
        final boolean coverage;
        final boolean originalCoverage = ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.get();
        final VillageRegistrySavedData originalRegistry;
        final BlockPos origin;
        final UUID village;
        final UUID squad = UUID.randomUUID();
        final Map<String, Guard> guards = new LinkedHashMap<>();
        final Map<String, Vec3> starts = new LinkedHashMap<>();
        final Map<String, Integer> goalCounts = new LinkedHashMap<>();
        final List<Villager> residents = new ArrayList<>();
        final List<Husk> attackers = new ArrayList<>();
        final List<Map<String, Object>> samples = new ArrayList<>();
        final Map<String, Object> settings = new LinkedHashMap<>();
        final Map<String, Integer> leaseTicks = new LinkedHashMap<>();
        final Map<String, Integer> combatTicks = new LinkedHashMap<>();
        final Map<String, Integer> incompleteTicks = new LinkedHashMap<>();
        final Map<String, Integer> returnLeaseTicks = new LinkedHashMap<>();
        final long began = System.nanoTime();
        long nextFrame = began;
        int ticks, planTicks, visitTicks, nativeTicks, enemyMovingTicks;
        int newcomerAffiliatedAt = -1;
        boolean reported, closed;

        Fixture(GameTestHelper helper, String scenario) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.scenario = scenario;
            this.coverage = !scenario.equals("village_default");
            helper.assertTrue("baseline".equals(System.getProperty("villagerguardgunners.combatTestMode")),
                    "Village scenarios are baseline observations, not AI acceptance tests");
            helper.assertTrue(OPEN.isEmpty() && ModConfigs.GUARD_GUNNERS.enabled.get(), "Invalid isolated fixture configuration");
            originalRegistry = VillageRegistrySavedData.get(level);
            origin = helper.absolutePos(BlockPos.ZERO).atY(level.getMaxBuildHeight() - 10);
            VillageIdentity identity = new VillageIdentity(level.dimension().location(),
                    ResourceLocation.parse("minecraft:village_plains"), ChunkPos.asLong(origin));
            village = identity.id();
            OPEN.add(this);
            try {
                VillageCoverageRuntime.clear(level);
                ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.accept(coverage);
                settings.put("enabled", ModConfigs.GUARD_GUNNERS.enabled.get());
                settings.put("automaticCoverage", coverage);
                settings.put("automaticDuties", ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.get());
                settings.put("engagement", ModConfigs.GUARD_GUNNERS.engagement.mode.get().name());
                settings.put("movementDecisionInterval", ModConfigs.GUARD_GUNNERS.performance.movementDecisionIntervalTicks.get());
                settings.put("pathInterval", ModConfigs.GUARD_GUNNERS.performance.pathRecalculationIntervalTicks.get());
                settings.put("visibilityInterval", ModConfigs.GUARD_GUNNERS.performance.visibilitySampleIntervalTicks.get());
                settings.put("arenaFloorY", origin.getY() - 1);
                settings.put("incursionStartTick", 160);
                settings.put("incursionEndTick", 360);
                settings.put("divided", scenario.equals("village_divided"));
                for (int x = 0; x <= 48; x++) for (int z = 0; z <= 32; z++) {
                    block(x, -1, z, Blocks.STONE);
                    for (int y = 0; y <= 6; y++) block(x, y, z,
                            x == 0 || x == 48 || z == 0 || z == 32 ? Blocks.BARRIER : Blocks.AIR);
                }
                if (scenario.equals("village_divided")) {
                    for (int z = 0; z <= 32; z++) for (int y = 0; y <= 6; y++) block(24, y, z, Blocks.STONE);
                }
                VillageRegistrySavedData registry = new VillageRegistrySavedData(level.dimension().location());
                level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, registry);
                registry.observe(identity, VillageFootprint.of(List.of(new VillageFootprint.Piece(origin, "NONE",
                        ResourceLocation.parse("test:village_intelligence"), new VillageFootprint.Bounds(
                        origin.getX(), origin.getY() - 1, origin.getZ(), origin.getX() + 48, origin.getY() + 5, origin.getZ() + 32)))));
                Map<Long, List<BlockPos>> homes = new HashMap<>();
                for (int x : List.of(8, 39)) {
                    BlockPos home = origin.offset(x, 0, 8);
                    var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
                    level.setBlock(home.north(), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
                    level.setBlock(home, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
                    homes.computeIfAbsent(SectionPos.asLong(home), k -> new ArrayList<>()).add(home);
                }
                homes.forEach(registry::observeHomes);
                guard("resident_west", 12, 12, true);
                guard("resident_east", 34, 12, true);
                guard("squad_a", 18, 16, true);
                guard("squad_b", 20, 16, true);
                guard("hold_position", 15, 18, true);
                guard("newcomer", 28, 18, false);
                for (String name : List.of("squad_a", "squad_b"))
                    GuardSquadAccess.setMembership(guards.get(name), SquadMembership.assigned(squad));
                GuardOrdersAccess.setOrders(guards.get("hold_position"),
                        GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION));
                int count = scenario.equals("village_crowd") ? 36 : 4;
                for (int i = 0; i < count; i++) {
                    Villager resident = Objects.requireNonNull(EntityType.VILLAGER.create(level));
                    resident.moveTo(origin.getX() + 30.5 + i % 6, origin.getY(), origin.getZ() + 9.5 + i / 6);
                    preserve(resident);
                    level.addFreshEntity(resident);
                    residents.add(resident);
                }
                settings.put("villagers", count);
                settings.put("guards", guards.size());
                settings.put("initialAffiliatedGuards", 5);
                settings.put("squadMembers", 2);
                settings.put("squadCommand", "NONE");
                settings.put("fixedPosts", 0);
                // Observe topology independently; never install a path or advance the live scheduler.
                CoverageTerrain terrain = new CoverageTerrain(level, registry.get(village));
                for (int i = 0; i < 500 && !terrain.complete(); i++) terrain.advance(128);
                BlockPos west = origin.offset(12, 0, 12), east = origin.offset(34, 0, 12);
                helper.assertTrue(terrain.usable() && terrain.component(west) != null && terrain.component(east) != null,
                        "Fixture homes were not represented by usable terrain");
                boolean connected = terrain.component(west).equals(terrain.component(east));
                helper.assertTrue(connected != scenario.equals("village_divided"), "Unexpected fixture connectivity");
                settings.put("homesConnected", connected);
            } catch (RuntimeException | AssertionError error) {
                writeReport(false, error.toString()); close(); throw error;
            }
        }

        void block(int x, int y, int z, Block block) { level.setBlock(origin.offset(x, y, z), block.defaultBlockState(), 3); }
        void preserve(LivingEntity actor) {
            Objects.requireNonNull(actor.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(10_000);
            actor.setHealth(10_000);
            if (actor instanceof Mob mob) mob.setPersistenceRequired();
        }
        void guard(String name, int x, int z, boolean affiliated) {
            Guard guard = Objects.requireNonNull(GuardEntityType.GUARD.get().create(level));
            guard.moveTo(origin.getX() + x + .5, origin.getY(), origin.getZ() + z + .5);
            preserve(guard);
            for (EquipmentSlot slot : EquipmentSlot.values()) guard.setItemSlot(slot, ItemStack.EMPTY);
            GuardRoleAccess.setRoleId(guard, null);
            guard.setItemSlot(EquipmentSlot.MAINHAND, GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47"))
                    .setFireMode(FireMode.AUTO).setAmmoCount(30).setAmmoInBarrel(false).build());
            GuardAmmoAccess.getAmmoHandler(guard).clear();
            GuardAmmoAccess.getAmmoHandler(guard).setStackInSlot(0,
                    AmmoItemBuilder.create().setId(ResourceLocation.parse("tacz:762x39")).setCount(64).build());
            level.addFreshEntity(guard);
            if (affiliated) VillageRegistrySavedData.get(level).affiliateGuard(guard.getUUID(), village);
            guards.put(name, guard);
            starts.put(name, guard.position());
            goalCounts.put(name, guard.goalSelector.getAvailableGoals().size());
        }
        void invade() {
            for (int z : List.of(10, 14, 18)) {
                Husk enemy = Objects.requireNonNull(EntityType.HUSK.create(level));
                enemy.moveTo(origin.getX() + 43.5, origin.getY(), origin.getZ() + z + .5);
                enemy.setBaby(false); preserve(enemy);
                enemy.setTarget(residents.get(0));
                level.addFreshEntity(enemy); attackers.add(enemy);
            }
        }
        void run() {
            helper.onEachTick(() -> {
                if (closed) return;
                try {
                    long remaining;
                    while ((remaining = nextFrame - System.nanoTime()) > 0) {
                        LockSupport.parkNanos(remaining);
                        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Observation interrupted");
                    }
                    nextFrame = System.nanoTime() + 50_000_000L;
                    ticks++;
                    if (ticks == 160) invade();
                    if (ticks == 360) attackers.forEach(Entity::discard);
                    sample();
                    if (ticks == 600) finish();
                } catch (RuntimeException | AssertionError error) {
                    if (!reported) writeReport(false, error.toString());
                    close(); throw error;
                }
            });
        }
        List<Double> position(Entity e) {
            return List.of(e.getX() - origin.getX(), e.getY() - origin.getY(), e.getZ() - origin.getZ());
        }
        void sample() {
            var coverageState = VillageCoverageRuntime.snapshot(level, village);
            if (coverageState.plan() != null) {
                planTicks++;
                if (coverageState.plan().areas().stream().anyMatch(a -> a.lastSuccessfulVisit() >= 0)) visitTicks++;
            }
            helper.assertTrue(coverageState.terrainUnits() <= VillageCoverageRuntime.TERRAIN_UNITS_PER_TICK
                    && coverageState.paths() <= VillageCoverageRuntime.PATHS_PER_TICK, "Coverage exceeded its work budget");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tick", ticks);
            row.put("stage", ticks < 160 ? "peace" : ticks < 360 ? "incursion" : "recovery");
            row.put("coverage", coverageState);
            Map<String, Object> actors = new LinkedHashMap<>();
            guards.forEach((name, guard) -> {
                var lease = VillageCoverageRuntime.lease(guard);
                if (lease != null) {
                    leaseTicks.merge(name, 1, Integer::sum);
                    if (ticks >= 360) returnLeaseTicks.merge(name, 1, Integer::sum);
                }
                var operator = IGunOperator.fromLivingEntity(guard);
                if (operator.getSynIsAiming() || operator.getSynReloadState().getStateType().isReloading()) nativeTicks++;
                var goal = guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                        .filter(GuardGunCombatGoal.class::isInstance).map(GuardGunCombatGoal.class::cast).findFirst().orElseThrow();
                boolean fighting = guard.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() == goal);
                if (fighting) {
                    combatTicks.merge(name, 1, Integer::sum);
                    if (!goal.tacticalController().assessment().complete()) incompleteTicks.merge(name, 1, Integer::sum);
                }
                Map<String, Object> actor = new LinkedHashMap<>();
                actor.put("position", position(guard));
                actor.put("availability", VillageCoverageRuntime.availability(guard).name());
                boolean affiliated = village.equals(VillageRegistrySavedData.get(level).guardVillage(guard.getUUID()));
                actor.put("affiliated", affiliated);
                if (name.equals("newcomer") && affiliated && newcomerAffiliatedAt < 0) newcomerAffiliatedAt = ticks;
                actor.put("squadMember", GuardSquadAccess.getSquadId(guard) != null);
                actor.put("orders", GuardOrdersAccess.getOrders(guard).toString());
                actor.put("lease", lease);
                actor.put("gunGoal", fighting);
                actor.put("activeGoals", guard.goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()).toList());
                actor.put("target", guard.getTarget() == null ? null : guard.getTarget().getUUID().toString());
                actor.put("intent", goal.tacticalController().decision().intent().name());
                actor.put("intentReason", goal.tacticalController().decision().reason().name());
                var assessment = goal.tacticalController().assessment();
                actor.put("threats", assessment.threats());
                actor.put("assessmentComplete", assessment.complete());
                actor.put("routeStatus", goal.tacticalNavigation().status());
                actor.put("routeDestination", goal.tacticalNavigation().destination() == null ? null
                        : goal.tacticalNavigation().destination().subtract(origin).toShortString());
                actor.put("navigationDone", guard.getNavigation().isDone());
                var path = guard.getNavigation().getPath();
                actor.put("nativePathTarget", path == null ? null : path.getTarget().subtract(origin).toShortString());
                // Historical shot state only: no diagnostic world query or property-script invocation.
                actor.put("shotSafety", GuardNativeShotAccess.controller(guard).currentShotSafety(guard.getTarget()).status().name());
                ItemStack stack = guard.getMainHandItem();
                actor.put("loadedRounds", stack.getItem() instanceof IGun gun ? gun.getCurrentAmmoCount(stack) : -1);
                actor.put("reloading", operator.getSynReloadState().getStateType().isReloading());
                actor.put("health", guard.getHealth());
                actors.put(name, actor);
                // These are existing participation contracts, not desired future intelligence.
                if (!coverage || name.startsWith("squad_") || name.equals("hold_position")
                        || !affiliated)
                    helper.assertTrue(lease == null, "Coverage took an excluded participant: " + name);
            });
            row.put("guards", actors);
            row.put("villagers", residents.stream().map(this::position).toList());
            row.put("attackers", attackers.stream().filter(Entity::isAlive).map(this::position).toList());
            enemyMovingTicks += (int) attackers.stream().filter(Entity::isAlive).filter(e -> !e.getNavigation().isDone()).count();
            samples.add(row);
        }
        void finish() {
            guards.forEach((name, guard) -> {
                helper.assertTrue(guard.isAlive() && !guard.isNoAi()
                        && guard.goalSelector.getAvailableGoals().size() == goalCounts.get(name), "Guard lost normal AI: " + name);
                helper.assertTrue(name.equals("newcomer") || village.equals(VillageRegistrySavedData.get(level).guardVillage(guard.getUUID())),
                        "Fixture affiliation changed: " + name);
            });
            for (String name : List.of("squad_a", "squad_b"))
                helper.assertTrue(squad.equals(GuardSquadAccess.getSquadId(guards.get(name))), "Squad membership changed");
            helper.assertTrue(GuardOrdersAccess.getOrders(guards.get("hold_position")).movement() == GuardOrders.MovementOrder.HOLD_POSITION,
                    "Hold order changed");
            helper.assertTrue(nativeTicks > 0 && !combatTicks.isEmpty() && enemyMovingTicks > 0,
                    "Incursion did not exercise live guard combat and enemy navigation");
            helper.assertTrue(!coverage || planTicks > 0, "Coverage fixture never published a plan");
            writeReport(true, null); close(); helper.succeed();
        }
        void writeReport(boolean valid, String failure) {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("samples", ticks); metrics.put("planTicks", planTicks); metrics.put("visitObservedTicks", visitTicks);
            metrics.put("leaseTicks", leaseTicks); metrics.put("combatTicks", combatTicks);
            metrics.put("incompleteAssessmentTicks", incompleteTicks); metrics.put("recoveryLeaseTicks", returnLeaseTicks);
            metrics.put("nativeActionTicks", nativeTicks); metrics.put("enemyNavigationTicks", enemyMovingTicks);
            metrics.put("newcomerAffiliatedAt", newcomerAffiliatedAt < 0 ? null : newcomerAffiliatedAt);
            Map<String, Double> displacement = new LinkedHashMap<>();
            guards.forEach((name, guard) -> displacement.put(name, guard.position().distanceTo(starts.get(name))));
            metrics.put("finalDisplacement", displacement);
            metrics.put("wallClockSeconds", (System.nanoTime() - began) / 1_000_000_000.0);
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("schema", 1); report.put("scenario", scenario); report.put("mode", "baseline");
            report.put("fixtureValid", valid); report.put("fixtureFailure", failure);
            report.put("settings", settings); report.put("metrics", metrics); report.put("samples", samples);
            report.put("limitations", List.of(
                    "Synthetic recorded village with two homes, no generated defense buildings, and an enclosed flat floor.",
                    "Actors retain normal goals and physics but have 10000 health; observations do not measure survival or balance.",
                    "Two guards share squad membership without an active squad command; automatic garrison duties are not exercised.",
                    "Three husks target a villager at tick 160 and are removed at tick 360; guard targets are never assigned by the fixture.",
                    "Real-time pacing preserves TaCZ action timing; metrics describe behavior without asserting future AI improvements."));
            try {
                Path directory = Path.of(System.getProperty("villagerguardgunners.combatReportDir"));
                Files.createDirectories(directory);
                Files.writeString(directory.resolve(scenario + ".json"),
                        new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report) + "\n");
                reported = true;
            } catch (IOException e) { throw new IllegalStateException("Cannot retain village observation", e); }
        }
        void close() {
            if (closed) return;
            try { if (!reported) writeReport(false, "Fixture ended before its observation window completed"); }
            finally {
                closed = true;
                guards.values().forEach(Entity::discard); residents.forEach(Entity::discard); attackers.forEach(Entity::discard);
                level.getEntitiesOfClass(Entity.class, new AABB(origin, origin.offset(49, 7, 33))).forEach(Entity::discard);
                VillageCoverageRuntime.clear(level);
                level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, originalRegistry);
                ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.accept(originalCoverage);
                OPEN.remove(this);
            }
        }
    }
}
