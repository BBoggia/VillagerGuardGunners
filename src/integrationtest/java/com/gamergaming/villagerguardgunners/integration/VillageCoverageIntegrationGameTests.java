package com.gamergaming.villagerguardgunners.integration;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.squad.SquadMembership;
import com.gamergaming.villagerguardgunners.village.*;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageAvailability.Reason;
import com.gamergaming.villagerguardgunners.village.coverage.live.*;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

@GameTestHolder(VillagerGuardGunners.MODID)
@PrefixGameTestTemplate(false)
public final class VillageCoverageIntegrationGameTests {
    private static final String NS = VillageIntegrationGameTests.NAMESPACE, ARENA = VillageIntegrationGameTests.ARENA;

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_terrain")
    public static void terrainGraphUsesCachedBlocksAndSeparatesBarriersUntilRepaired(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            BlockPos left = f.origin.offset(7, 0, 8), right = f.origin.offset(30, 0, 8);
            for (int z = -4; z <= 26; z++) for (int y = -2; y <= 6; y++) {
                f.level.setBlock(f.origin.offset(19, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            try (ProbeScope scope = new ProbeScope()) {
                var split = f.terrain();
                helper.assertTrue(split.component(left) != null && split.component(right) != null,
                        "Both occupied sides must be observed");
                helper.assertTrue(!split.component(left).equals(split.component(right)) && split.path(left, right).isEmpty(),
                        "A solid wall was treated as traversable");
                helper.assertTrue(scope.missingChunkRequests == 0 && scope.poiReads == 0, "Terrain observation requested missing world data");
            }
            for (int y = 0; y <= 2; y++) f.level.setBlock(f.origin.offset(19, y, 8), Blocks.AIR.defaultBlockState(), 3);
            try (ProbeScope scope = new ProbeScope()) {
                var repaired = f.terrain();
                helper.assertTrue(repaired.component(left).equals(repaired.component(right)) && !repaired.path(left, right).isEmpty(),
                        "Repair did not reconnect the walking graph");
                BlockPos danger = f.origin.offset(10, 0, 8);
                f.level.setBlock(danger.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                helper.assertTrue(!repaired.walkable(danger), "Hazardous footing was accepted");
                helper.assertTrue(scope.missingChunkRequests == 0 && scope.poiReads == 0, "Graph paths requested missing world data");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_movement", timeoutTicks = 950)
    public static void ordinaryVillageGuardVisitsBothSidesWithoutRewritingOrders(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Guard guard = f.guard(f.origin.offset(18, 0, 8));
        // This probe measures coverage and target-based interruption. Other combat,
        // wandering and avoidance goals are exercised separately and can leave its floor.
        guard.setInvulnerable(true);
        guard.goalSelector.removeAllGoals(goal -> !(goal instanceof
                com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal));
        var approaches = f.terrain().approaches();
        double leftArrival = approaches.stream().mapToInt(BlockPos::getX).min().orElseThrow() + 2;
        double rightArrival = approaches.stream().mapToInt(BlockPos::getX).max().orElseThrow() - 1;
        boolean[] reached = {false, false, false, false, false};
        helper.runAfterDelay(940, f::close);
        var attacker = net.minecraft.world.entity.EntityType.ZOMBIE.create(f.level);
        attacker.setNoAi(true);
        attacker.setInvulnerable(true);
        attacker.moveTo(f.origin.getX() + 18.5, f.origin.getY(), f.origin.getZ() + 18.5);
        helper.runAfterDelay(200, () -> {
            f.level.addFreshEntity(attacker);
            guard.setTarget(attacker);
            helper.assertTrue(VillageCoverageRuntime.lease(guard) == null, "Combat did not immediately release movement eligibility");
        });
        helper.runAfterDelay(240, () -> { guard.setTarget(null); attacker.discard(); });
        for (int tick = 10; tick <= 900; tick += 5) {
            final int observationTick = tick;
            helper.runAfterDelay(tick, () -> {
                if (f.closed) return;
                reached[0] |= guard.getX() <= leftArrival;
                reached[1] |= guard.getX() >= rightArrival;
                if (observationTick > 250) {
                    reached[3] |= guard.getX() <= leftArrival;
                    reached[4] |= guard.getX() >= rightArrival;
                }
                var state = VillageCoverageRuntime.snapshot(f.level, f.identity.id());
                reached[2] |= state.plan() != null && state.plan().areas().stream().anyMatch(a -> a.lastSuccessfulVisit() >= 0);
                helper.assertTrue(state.terrainUnits() <= VillageCoverageRuntime.TERRAIN_UNITS_PER_TICK
                        && state.paths() <= VillageCoverageRuntime.PATHS_PER_TICK, "Live work exceeded its tick budget");
            });
        }
        helper.runAfterDelay(910, () -> {
            try {
                var state = VillageCoverageRuntime.snapshot(f.level, f.identity.id());
                helper.assertTrue(reached[0] && reached[1] && reached[3] && reached[4], "Guard did not patrol both sides: " + Arrays.toString(reached)
                        + ", pos=" + guard.blockPosition() + ", alive=" + guard.isAlive()
                        + ", health=" + guard.getHealth() + ", target=" + guard.getTarget() + ", coverage=" + state);
                helper.assertTrue(reached[2], "Movement did not credit a real arrival");
                helper.assertTrue(!guard.isPatrolling() && GuardOrdersAccess.getOrders(guard).equals(GuardOrders.DEFAULT),
                        "Automatic coverage rewrote manual orders or patrol flags");
            } finally { f.close(); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_precedence", timeoutTicks = 250)
    public static void liveEligibilityProtectsOfflineFollowManualSquadsCombatAndNativeActions(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Guard guard = f.guard(f.origin.offset(18, 0, 8));
        helper.runAfterDelay(240, f::close);
        helper.runAfterDelay(150, () -> {
            try {
                helper.assertTrue(VillageCoverageRuntime.availability(guard) == Reason.AVAILABLE, "Resident was unavailable initially");
                guard.setFollowing(true);
                helper.assertTrue(VillageCoverageRuntime.availability(guard) == Reason.FOLLOWING && VillageCoverageRuntime.lease(guard) == null,
                        "Offline follow order was overridden");
                guard.setFollowing(false);
                GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION));
                helper.assertTrue(VillageCoverageRuntime.availability(guard) == Reason.HOLD_POSITION, "Hold was overridden");
                GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT);
                GuardSquadAccess.setMembership(guard, SquadMembership.assigned(UUID.randomUUID()));
                helper.assertTrue(VillageCoverageRuntime.availability(guard) == Reason.MANUAL_SQUAD, "Manual squad was split");
                GuardSquadAccess.setMembership(guard, null);
                guard.setNoAi(true);
                helper.assertTrue(VillageCoverageRuntime.availability(guard) == Reason.NO_AI, "NoAI guard was available");
                guard.setNoAi(false);
                guard.setHealth(1);
                helper.assertTrue(VillageCoverageRuntime.availability(guard) == Reason.INJURED, "Injured guard was available");
                guard.setHealth(guard.getMaxHealth());
                var target = net.minecraft.world.entity.EntityType.ZOMBIE.create(f.level);
                target.setNoAi(true);
                target.moveTo(f.origin.getX() + 20.5, f.origin.getY(), f.origin.getZ() + 8.5);
                f.level.addFreshEntity(target);
                guard.setTarget(target);
                helper.assertTrue(VillageCoverageRuntime.availability(guard) == Reason.FIGHTING, "Combat did not preempt coverage");
                guard.setTarget(null);
                target.discard();
                var gun = GunItemBuilder.create().setId(ResourceLocation.parse("tacz:ak47")).build();
                guard.setItemSlot(EquipmentSlot.MAINHAND, gun);
                var nativeData = IGunOperator.fromLivingEntity(guard).getDataHolder();
                nativeData.isBolting = true;
                helper.assertTrue(VillageCoverageRuntime.availability(guard) == Reason.NATIVE_ACTION, "Native action did not preempt coverage");
                nativeData.isBolting = false;
                ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.accept(false);
                VillageCoverageRuntime.tick(f.level);
                helper.assertTrue(VillageCoverageRuntime.lease(guard) == null
                        && VillageCoverageRuntime.snapshot(f.level, f.identity.id()).loadedGuards() == 0, "Disabling coverage retained live claims");
            } finally { f.close(); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_unloaded")
    public static void unavailableTerrainRemainsUnknownWithoutChunkOrPoiReads(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            BlockPos remote = f.origin.offset(800, 0, 0);
            helper.assertTrue(f.level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
                    "Missing-chunk fixture was already loaded");
            var original = f.record.footprint().pieces().get(0);
            var distant = new VillageFootprint.Piece(remote, "NONE", ResourceLocation.parse("test:remote"),
                    new VillageFootprint.Bounds(remote.getX(), remote.getY() - 1, remote.getZ(), remote.getX() + 10,
                            remote.getY() + 4, remote.getZ() + 10));
            var record = new VillageRecord(f.identity, VillageFootprint.of(List.of(original, distant)), 2,
                    Set.of(f.origin.offset(8, 0, 11), remote.offset(5, 0, 5)));
            try (ProbeScope scope = new ProbeScope()) {
                var terrain = new CoverageTerrain(f.level, record);
                for (int i = 0; i < 500 && !terrain.complete(); i++) terrain.advance(128);
                helper.assertTrue(terrain.usable() && terrain.approach(remote.offset(5, 0, 5)) == null,
                        "Unavailable terrain was presented as reachable");
                helper.assertTrue(scope.missingChunkRequests == 0 && scope.poiReads == 0, "Unloaded observation requested world data");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_path_ownership", timeoutTicks = 100)
    public static void graphNavigationIsLoadedOnlyAndStopsOnlyItsOwnPath(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Guard guard = f.guard(f.origin.offset(18, 0, 8));
        guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal()).toList().forEach(guard.goalSelector::removeGoal);
        helper.runAfterDelay(90, f::close);
        helper.startSequence().thenIdle(20).thenWaitUntil(() ->
                helper.assertTrue(f.level.getEntity(guard.getUUID()) == guard, "Waiting for fixture guard to enter the live index"))
                .thenWaitUntil(() -> {
                    VillageCoverageRuntime.synchronizeGuard(guard);
                    for (int i = 0; i < 200; i++) VillageCoverageRuntime.tick(f.level);
                    // A chunk publication can rebuild terrain between scheduled reviews.
                    // Repeated calls in one game tick cannot advance the 20-tick review clock.
                    helper.assertTrue(VillageCoverageRuntime.lease(guard) != null,
                            "Waiting for coverage review: " + VillageCoverageRuntime.snapshot(f.level, f.identity.id()));
                }).thenExecute(() -> {
            try {
                var controller = new GuardCoverageMovementController(guard);
                try (ProbeScope scope = new ProbeScope()) {
                    controller.start();
                    VillageCoverageRuntime.tick(f.level);
                    controller.tick();
                    helper.assertTrue(guard.getNavigation().getPath() != null, "Graph path was not installed");
                    guard.getNavigation().tick();
                    helper.assertTrue(scope.missingChunkRequests == 0 && scope.poiReads == 0, "Native navigation requested uncached data");
                }
                var building = UUID.randomUUID();
                var slot = new com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutySlot(
                        com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutySlot.CURRENT_SCHEMA_VERSION,
                        com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyIds.slotId(building, "coverage_preemption"),
                        building, f.level.dimension().location(), "coverage_preemption", guard.blockPosition(), net.minecraft.core.Direction.NORTH,
                        com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutySlotKind.SENTRY,
                        com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyWeaponRequirement.ANY);
                var post = new com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseDutyAssignment(UUID.randomUUID(),
                        slot, guard.getUUID(), f.level.getGameTime(), f.level.getGameTime() + 400);
                VillageCoverageRuntime.postsPublished(f.level, List.of(post));
                helper.assertTrue(VillageCoverageRuntime.lease(guard) == null && !controller.shouldContinue(),
                        "Fixed post publication did not preempt coverage");
                var replacement = new net.minecraft.world.level.pathfinder.Path(new ArrayList<>(List.of(
                        new net.minecraft.world.level.pathfinder.Node(guard.getBlockX(), guard.getBlockY(), guard.getBlockZ()),
                        new net.minecraft.world.level.pathfinder.Node(guard.getBlockX() + 1, guard.getBlockY(), guard.getBlockZ()))),
                        guard.blockPosition().east(), true);
                guard.getNavigation().moveTo(replacement, 0.5);
                controller.stop();
                helper.assertTrue(guard.getNavigation().getPath() == replacement, "Stopping coverage canceled another owner's path");
                helper.assertTrue(VillageCoverageRuntime.lease(guard) == null, "Stopping coverage retained the claim");
                VillageCoverageRuntime.bell(f.level, f.origin.offset(8, 0, 8));
                VillageCoverageRuntime.tick(f.level);
                helper.assertTrue(VillageCoverageRuntime.snapshot(f.level, f.identity.id()).readiness()
                        == com.gamergaming.villagerguardgunners.defense.VillageDefenseState.WATCH, "Ordinary village did not receive bell readiness");
                VillageCoverageRuntime.onChunkChange(new net.minecraftforge.event.level.ChunkEvent.Unload(f.level.getChunkAt(f.origin)));
                helper.assertTrue(VillageCoverageRuntime.lease(guard) == null, "Chunk lifecycle did not invalidate coverage");
            } finally { f.close(); }
        }).thenSucceed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_recovery", timeoutTicks = 200)
    public static void blockedPathYieldsToAnotherDestinationAndRepairRestoresCoverage(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Guard guard = f.guard(f.origin.offset(18, 0, 8));
        guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal()).toList().forEach(guard.goalSelector::removeGoal);
        var controller = new GuardCoverageMovementController(guard);
        BlockPos[] blocked = new BlockPos[1];
        UUID[] failedDestination = new UUID[1];
        helper.runAfterDelay(190, f::close);
        helper.runAfterDelay(20, () -> {
            VillageCoverageRuntime.synchronizeGuard(guard);
            for (int i = 0; i < 200; i++) VillageCoverageRuntime.tick(f.level);
            var lease = VillageCoverageRuntime.lease(guard);
            helper.assertTrue(lease != null, "Recovery fixture received no assignment");
            failedDestination[0] = lease.destinationId();
            controller.start();
            VillageCoverageRuntime.tick(f.level);
            controller.tick();
            var path = guard.getNavigation().getPath();
            helper.assertTrue(path != null && path.getNodeCount() > 3, "Recovery fixture received no walking path");
            blocked[0] = path.getNodePos(2);
            f.level.setBlock(blocked[0], Blocks.STONE.defaultBlockState(), 3);
            controller.tick();
            helper.assertTrue(VillageCoverageRuntime.lease(guard) == null && guard.getNavigation().isDone(),
                    "Blocked path retained its claim or navigation");
        });
        helper.runAfterDelay(30, () -> {
            VillageCoverageRuntime.tick(f.level);
            var alternative = VillageCoverageRuntime.lease(guard);
            helper.assertTrue(alternative != null && !alternative.destinationId().equals(failedDestination[0]),
                    "Failed destination prevented alternate coverage: lease=" + alternative + ", failed=" + failedDestination[0]
                            + ", blocked=" + blocked[0] + ", guard=" + guard.position()
                            + ", availability=" + VillageCoverageRuntime.availability(guard)
                            + ", snapshot=" + VillageCoverageRuntime.snapshot(f.level, f.identity.id()));
            controller.start();
            VillageCoverageRuntime.tick(f.level);
            controller.tick();
            helper.assertTrue(guard.getNavigation().getPath() != null, "Alternate destination received no path");
            controller.stop();
            f.level.setBlock(blocked[0], Blocks.AIR.defaultBlockState(), 3);
            VillageCoverageRuntime.onChunkChange(new net.minecraftforge.event.level.ChunkEvent.Load(f.level.getChunkAt(blocked[0]), false));
        });
        helper.startSequence().thenIdle(140).thenExecute(() -> {
            for (int i = 0; i < 200; i++) VillageCoverageRuntime.tick(f.level);
        }).thenWaitUntil(() -> {
            // Completing a resurvey need not coincide with the next scheduled review.
            helper.assertTrue(VillageCoverageRuntime.lease(guard) != null,
                    "Coverage did not resume after repair: " + VillageCoverageRuntime.snapshot(f.level, f.identity.id())
                            + ", availability=" + VillageCoverageRuntime.availability(guard) + ", position=" + guard.position());
        }).thenExecute(() -> {
            try {
                controller.start();
                VillageCoverageRuntime.tick(f.level);
                controller.tick();
                helper.assertTrue(guard.getNavigation().getPath() != null && !guard.getNavigation().isDone(),
                        "Repaired terrain did not receive usable navigation; guard=" + guard.position()
                                + ", lease=" + VillageCoverageRuntime.lease(guard) + ", path=" + guard.getNavigation().getPath()
                                + ", snapshot=" + VillageCoverageRuntime.snapshot(f.level, f.identity.id()));
                controller.stop();
            } finally { f.close(); }
        }).thenSucceed();
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_budget")
    public static void eightActiveVillagesShareTerrainAndPathBudgetsWithoutStarvation(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        helper.runAfterDelay(90, f::close);
        var data = VillageRegistrySavedData.get(f.level);
        List<Guard> guards = new ArrayList<>();
        // Overlapping test records isolate scheduler fairness from discovery and world size.
        var bounds = new VillageFootprint.Bounds(f.origin.getX() + 8, f.origin.getY() - 1, f.origin.getZ() + 6,
                f.origin.getX() + 28, f.origin.getY() + 3, f.origin.getZ() + 16);
        for (int i = 0; i < 8; i++) {
            var id = new VillageIdentity(f.level.dimension().location(), ResourceLocation.parse("minecraft:village_plains"),
                    net.minecraft.world.level.ChunkPos.asLong((f.origin.getX() >> 4) + i + 1, f.origin.getZ() >> 4));
            data.observe(id, VillageFootprint.of(List.of(new VillageFootprint.Piece(f.origin, "NONE",
                    ResourceLocation.parse("minecraft:village/plains/houses/plains_small_house_1"), bounds))));
            Guard guard = f.guard(f.origin.offset(18, 0, 8), id.id());
            guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal()).toList().forEach(guard.goalSelector::removeGoal);
            guards.add(guard);
        }
        helper.runAfterDelay(20, () -> {
            try {
                VillageCoverageRuntime.clear(f.level);
                guards.forEach(VillageCoverageRuntime::synchronizeGuard);
                int peakUnits = 0;
                long elapsed = 0, maximum = 0;
                try (ProbeScope scope = new ProbeScope()) {
                    for (int i = 0; i < 200; i++) {
                        long start = System.nanoTime();
                        VillageCoverageRuntime.tick(f.level);
                        long duration = System.nanoTime() - start;
                        elapsed += duration;
                        maximum = Math.max(maximum, duration);
                        int units = VillageCoverageRuntime.snapshot(f.level, f.identity.id()).terrainUnits();
                        peakUnits = Math.max(peakUnits, units);
                        helper.assertTrue(units <= VillageCoverageRuntime.TERRAIN_UNITS_PER_TICK, "Combined terrain work exceeded its budget");
                    }
                    for (Guard guard : guards) {
                        var lease = VillageCoverageRuntime.lease(guard);
                        helper.assertTrue(lease != null, "An active village starved during observation: "
                                + VillageCoverageRuntime.snapshot(f.level, data.guardVillage(guard.getUUID()))
                                + ", availability=" + VillageCoverageRuntime.availability(guard));
                        VillageCoverageRuntime.requestPath(guard, lease);
                    }
                    VillageCoverageRuntime.tick(f.level);
                    int first = 0;
                    for (Guard guard : guards) if (VillageCoverageRuntime.takePath(guard) != null) first++;
                    helper.assertTrue(first == 4, "First path batch was not bounded to four requests: " + first);
                    VillageCoverageRuntime.tick(f.level);
                    int second = 0;
                    for (Guard guard : guards) if (VillageCoverageRuntime.takePath(guard) != null) second++;
                    helper.assertTrue(second == 4, "Second path batch starved: " + second);
                    helper.assertTrue(peakUnits == 512, "Terrain budget was not exercised: " + peakUnits);
                    helper.assertTrue(scope.missingChunkRequests == 0 && scope.poiReads == 0, "Active multi-village work requested missing data");
                }
                System.out.println("VGG_COVERAGE_BUDGET villages=8 slices=200 peakTerrain=" + peakUnits
                        + " meanSliceMs=" + elapsed / 200_000_000.0 + " maxSliceMs=" + maximum / 1_000_000.0);
            } finally { f.close(); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_casualty", timeoutTicks = 250)
    public static void ordinaryVillageCoverageExcludesCasualtiesAndResumesAfterRecovery(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Guard patient = f.guard(f.origin.offset(18, 0, 8));
        patient.goalSelector.removeAllGoals(goal -> true);
        boolean downed = ModConfigs.GUARD_GUNNERS.medical.downed.get();
        helper.runAfterDelay(240, f::close);
        helper.runAfterDelay(150, () -> {
            try {
                helper.assertTrue(VillageCoverageRuntime.availability(patient) == Reason.AVAILABLE, "Initial resident unavailable");
                var membership = VillageRegistrySavedData.get(f.level).guardVillage(patient.getUUID());
                var held = patient.getMainHandItem().copy();
                ModConfigs.GUARD_GUNNERS.medical.downed.accept(true);
                patient.hurt(f.level.damageSources().generic(), 1000);
                helper.assertTrue(com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.casualty(patient)
                        && VillageCoverageRuntime.availability(patient) != Reason.AVAILABLE
                        && VillageCoverageRuntime.lease(patient) == null, "Casualty retained active coverage");
                ModConfigs.GUARD_GUNNERS.medical.downed.accept(false);
                com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.tick(patient);
                helper.assertTrue(!com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.casualty(patient)
                        && patient.getHealth() <= 4 && VillageCoverageRuntime.availability(patient) != Reason.AVAILABLE,
                        "Feature-disable recovery granted immediate healthy coverage");
                // Later ordinary healing restores readiness without reenrolling the resident.
                for (int i = 0; i < 100; i++) com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.tick(patient);
                patient.setHealth(patient.getMaxHealth()); patient.hurtTime = 0;
                helper.assertTrue(VillageCoverageRuntime.availability(patient) == Reason.AVAILABLE,
                        "Recovered resident could not resume coverage: " + VillageCoverageRuntime.availability(patient));
                helper.assertTrue(membership.equals(VillageRegistrySavedData.get(f.level).guardVillage(patient.getUUID()))
                        && ItemStack.matches(held, patient.getMainHandItem()) && GuardOrdersAccess.getOrders(patient).equals(GuardOrders.DEFAULT),
                        "Casualty recovery rewrote membership, equipment, or orders");
            } finally { ModConfigs.GUARD_GUNNERS.medical.downed.accept(downed); f.close(); }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_combat_duty", timeoutTicks = 180)
    public static void villageCombatDutySurvivesMovementAndReturnsWithoutChangingOrders(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Guard guard = f.guard(f.origin.offset(12, 0, 8));
        guard.goalSelector.removeAllGoals(goal -> !(goal instanceof com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal));
        var anchor = guard.blockPosition();
        com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.capture(guard);
        var duty = com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.current(guard);
        helper.assertTrue(duty != null && duty.bounds().contains(guard.position())
                && !duty.bounds().contains(guard.position().add(25, 0, 0)), "Village engagement has no local boundary");
        guard.moveTo(guard.getX() + 10, guard.getY(), guard.getZ());
        com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.capture(guard);
        helper.assertTrue(com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.current(guard).anchor().equals(anchor),
                "Movement moved the engagement anchor");
        helper.runAfterDelay(170, f::close);
        helper.onEachTick(() -> {
            if (f.closed || helper.getTick() < 10) return;
            if (guard.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(anchor)) <= 2.25) {
                helper.assertTrue(GuardOrdersAccess.getOrders(guard).equals(GuardOrders.DEFAULT), "Return rewrote orders");
                com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.capture(guard);
                GuardOrdersAccess.setOrders(guard, GuardOrders.DEFAULT.withMovement(GuardOrders.MovementOrder.HOLD_POSITION));
                helper.assertTrue(com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.current(guard) == null,
                        "Manual Hold Position retained automatic duty ownership");
                f.close(); helper.succeed();
            }
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_return_failure", timeoutTicks = 210)
    public static void unreachableCombatDutyReleasesAutomaticMovement(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        Guard guard = f.guard(f.origin.offset(12, 0, 8));
        guard.goalSelector.removeAllGoals(goal -> true);
        com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.capture(guard);
        helper.assertTrue(com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.current(guard) != null, "No duty captured");
        guard.moveTo(guard.getX() + 10, guard.getY(), guard.getZ());
        for (int x = 10; x <= 14; x++) for (int z = 6; z <= 10; z++)
            if (x == 10 || x == 14 || z == 6 || z == 10)
                for (int y = 0; y < 4; y++) f.level.setBlock(f.origin.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
        var controller = new com.gamergaming.villagerguardgunners.ai.GuardDutyReturnController(guard);
        helper.runAfterDelay(200, f::close);
        helper.onEachTick(() -> {
            if (f.closed) return;
            controller.tick();
            if (helper.getTick() > 1 && !controller.canStart()) {
                helper.assertTrue(com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.current(guard) == null
                        && !controller.ownsPath() && GuardOrdersAccess.getOrders(guard).equals(GuardOrders.DEFAULT),
                        "Unreachable return retained movement ownership or changed orders");
                f.close(); helper.succeed();
            }
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_return_slab", timeoutTicks = 180)
    public static void combatDutyOnSlabReturnsToNativeAirCell(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        var anchor = f.origin.offset(12, 0, 8);
        f.level.setBlock(anchor.below(), Blocks.STONE_SLAB.defaultBlockState(), 3);
        Guard guard = f.guard(anchor); guard.setPos(guard.getX(), guard.getY() - .5, guard.getZ());
        guard.goalSelector.removeAllGoals(goal -> true);
        com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.capture(guard);
        var duty = com.gamergaming.villagerguardgunners.ai.tactics.GuardCombatDuty.current(guard);
        helper.assertTrue(duty != null && duty.anchor().equals(anchor), "Fractional support stored a solid-block duty target");
        guard.moveTo(guard.getX() + 8, f.origin.getY(), guard.getZ());
        var controller = new com.gamergaming.villagerguardgunners.ai.GuardDutyReturnController(guard);
        helper.runAfterDelay(170, f::close);
        helper.onEachTick(() -> {
            if (f.closed) return;
            controller.tick();
            if (guard.distanceToSqr(anchor.getX() + .5, anchor.getY() - .5, anchor.getZ() + .5) <= 2.25) {
                f.close(); helper.succeed();
            }
        });
    }

    @GameTest(templateNamespace = NS, template = ARENA, batch = "coverage_partial")
    public static void cappedTerrainRetainsVerifiedLocalComponents(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper); ProbeScope scope = new ProbeScope()) {
            var bounds = new VillageFootprint.Bounds(f.origin.getX(), f.origin.getY() - 1, f.origin.getZ(),
                    f.origin.getX() + 130, f.origin.getY() + 4, f.origin.getZ() + 130);
            var footprint = VillageFootprint.of(List.of(new VillageFootprint.Piece(f.origin, "NONE", ResourceLocation.parse("test:partial"), bounds)));
            Set<BlockPos> homes = new HashSet<>();
            for (int x = 3; x <= 120; x += 13) for (int z = 3; z <= 120; z += 13) homes.add(f.origin.offset(x, 0, z));
            var terrain = new CoverageTerrain(f.level, new VillageRecord(f.identity, footprint, 1, homes));
            for (int i = 0; i < 500 && !terrain.complete(); i++) helper.assertTrue(terrain.advance(128) <= 128, "Survey exceeded budget");
            helper.assertTrue(terrain.limited() && terrain.usable() && terrain.nodeCount() > 0
                    && terrain.anchors().size() == CoverageTerrain.MAX_ANCHORS, "Cap discarded verified local coverage");
            var left = f.origin.offset(7, 0, 8); var right = f.origin.offset(30, 0, 8);
            helper.assertTrue(!terrain.path(left, right).isEmpty(), "Verified connected route was lost at the anchor cap");
            helper.assertTrue(terrain.component(f.origin.offset(120, 0, 120)) == null, "Unknown terrain was promoted to reachable");
            helper.assertTrue(scope.missingChunkRequests == 0 && scope.poiReads == 0, "Partial survey requested unknown terrain");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final BlockPos origin;
        final VillageRegistrySavedData original;
        final VillageIdentity identity;
        final VillageRecord record;
        final List<Guard> guards = new ArrayList<>();
        final boolean enabled = ModConfigs.GUARD_GUNNERS.enabled.get();
        final boolean coverage = ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.get();
        final boolean duties = ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.get();
        final boolean buildings = ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.get();
        boolean closed;
        Fixture(GameTestHelper helper) {
            level = helper.getLevel();
            // Keep long movement/repair probes clear of natural cave ceilings and falling gravel.
            origin = helper.absolutePos(new BlockPos(2, 2, 2)).atY(level.getMaxBuildHeight() - 10);
            VillageCoverageRuntime.clear(level);
            ModConfigs.GUARD_GUNNERS.enabled.accept(true);
            ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.accept(true);
            ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.accept(false);
            ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.accept(false);
            original = VillageRegistrySavedData.get(level);
            var data = new VillageRegistrySavedData(level.dimension().location());
            level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, data);
            for (int x = 0; x <= 35; x++) for (int z = 0; z <= 21; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y < 10; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
            identity = new VillageIdentity(level.dimension().location(), ResourceLocation.parse("minecraft:village_plains"),
                    net.minecraft.world.level.ChunkPos.asLong(origin));
            var bounds = new VillageFootprint.Bounds(origin.getX(), origin.getY() - 1, origin.getZ(),
                    origin.getX() + 35, origin.getY() + 4, origin.getZ() + 21);
            data.observe(identity, VillageFootprint.of(List.of(new VillageFootprint.Piece(origin, "NONE", ResourceLocation.parse("test:layout"), bounds))));
            List<BlockPos> homes = List.of(origin.offset(8, 0, 11), origin.offset(31, 0, 11));
            for (BlockPos home : homes) {
                var bed = Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
                level.setBlock(home.north(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
                level.setBlock(home, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
            }
            Map<Long, List<BlockPos>> sections = new HashMap<>();
            for (BlockPos p : homes) sections.computeIfAbsent(SectionPos.asLong(p), k -> new ArrayList<>()).add(p);
            sections.forEach(data::observeHomes);
            record = data.get(identity.id());
        }
        Guard guard(BlockPos position) {
            return guard(position, identity.id());
        }
        Guard guard(BlockPos position, UUID village) {
            Guard guard = GuardEntityType.GUARD.get().create(level);
            guard.setPersistenceRequired();
            guard.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
            guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            guard.targetSelector.getAvailableGoals().stream().map(g -> g.getGoal()).toList().forEach(guard.targetSelector::removeGoal);
            level.addFreshEntity(guard);
            VillageRegistrySavedData.get(level).affiliateGuard(guard.getUUID(), village);
            guards.add(guard);
            VillageCoverageRuntime.synchronizeGuard(guard);
            return guard;
        }
        CoverageTerrain terrain() {
            var terrain = new CoverageTerrain(level, record);
            for (int i = 0; i < 500 && !terrain.complete(); i++) {
                if (terrain.advance(128) > 128) throw new AssertionError("Terrain exceeded the slice budget");
            }
            if (!terrain.usable()) throw new AssertionError("Terrain unresolved: " + terrain.nodeCount() + ", limited=" + terrain.limited());
            return terrain;
        }
        public void close() {
            if (closed) return;
            closed = true;
            guards.forEach(Guard::discard);
            VillageCoverageRuntime.clear(level);
            level.getDataStorage().set(VillageRegistrySavedData.DATA_NAME, original);
            ModConfigs.GUARD_GUNNERS.enabled.accept(enabled);
            ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.accept(coverage);
            ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.accept(duties);
            ModConfigs.GUARD_GUNNERS.villageBuildings.enabled.accept(buildings);
        }
    }
}
