package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.squad.SquadMembership;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRoute;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteResolver;
import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Bounded scale coverage for automatic-duty hot-path eligibility checks. */
@GameTestHolder(VillagerGuardGunners.MODID + "_performance")
@PrefixGameTestTemplate(false)
public final class AutomaticDutyScalePerformanceGameTests {
    private static final String EMPTY_TEMPLATE = "villagerguardgunners/gametest_arena";
    private static final int GUARD_COUNT = 100;
    private static final int SAMPLE_TICKS = 200;

    private AutomaticDutyScalePerformanceGameTests() {
    }

    @GameTest(
            batch = "phase8_20_automatic_duty_scale",
            templateNamespace = "minecraft",
            template = EMPTY_TEMPLATE,
            timeoutTicks = 400)
    public static void automaticDutyEligibilityRemainsBoundedAtScale(
            GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(origin.above(), Blocks.AIR.defaultBlockState(), 3);
        BlockPos unloaded = firstUnloadedDestination(level, origin);
        List<BlockPos> unloadedRoute = new ArrayList<>(64);
        for (int index = 0; index < 64; index++) {
            unloadedRoute.add(unloaded.offset(index % 8, 0, index / 8));
        }
        UUID squadId = UUID.fromString("ec5fbace-aa2a-4057-877a-b39a0a1b23e9");
        UUID buildingId = UUID.fromString("b80d8da2-a075-4530-907e-ddb09285bd0a");
        SquadRegistrySavedData registry = SquadRegistrySavedData.get(level.getServer());
        registry.remove(squadId);
        registry.register(squadId, "Scale Route", null, level.getGameTime())
                .orElseThrow();
        SquadPatrolRoute route = registry.replacePatrolRoute(
                        squadId,
                        level.dimension().location(),
                        unloadedRoute,
                        level.getGameTime())
                .route();
        helper.assertTrue(route != null && route.executable(),
                "Could not create the automatic-duty scale route");

        List<Guard> guards = new ArrayList<>(GUARD_COUNT);
        for (int index = 0; index < GUARD_COUNT; index++) {
            Guard guard = GuardEntityType.GUARD.get().create(level);
            helper.assertTrue(guard != null,
                    "Could not create automatic-duty scale guard " + index);
            GuardSquadAccess.setMembership(guard, SquadMembership.assigned(squadId));
            guards.add(guard);
        }
        List<VillageDefenseDutySlot> slots = new ArrayList<>(
                VillageDefenseStaffingPlanner.MAXIMUM_SLOTS);
        for (int index = 0;
                index < VillageDefenseStaffingPlanner.MAXIMUM_SLOTS;
                index++) {
            slots.add(new VillageDefenseDutySlot(
                    VillageDefenseDutySlot.CURRENT_SCHEMA_VERSION,
                    VillageDefenseDutyIds.slotId(buildingId, "scale_" + index),
                    buildingId,
                    level.dimension().location(),
                    "scale_" + index,
                    origin,
                    Direction.NORTH,
                    VillageDefenseDutySlotKind.SENTRY,
                    VillageDefenseDutyWeaponRequirement.ANY));
        }

        long[] guardEvaluations = {0L};
        long[] slotEvaluations = {0L};
        helper.onEachTick(() -> {
            if (helper.getTick() >= SAMPLE_TICKS) {
                return;
            }
            for (Guard guard : guards) {
                helper.assertTrue(!SquadPatrolRouteResolver.hasLoadedWaypoint(guard),
                        "An unloaded scale route unexpectedly claimed movement");
                guardEvaluations[0]++;
            }
            VillageDefenseStaffingRuntime.SlotScreen screen =
                    VillageDefenseStaffingRuntime.screenSlots(level, slots);
            helper.assertTrue(screen.usableSlots().size() == slots.size(),
                    "Loaded duty-slot screening changed the bounded scale fixture");
            slotEvaluations[0] += slots.size();
        });
        helper.runAtTickTime(SAMPLE_TICKS, () -> {
            helper.assertTrue(
                    guardEvaluations[0] == (long) GUARD_COUNT * SAMPLE_TICKS,
                    "The patrol scale workload did not execute every bounded lookup");
            helper.assertTrue(
                    slotEvaluations[0]
                            == (long) VillageDefenseStaffingPlanner.MAXIMUM_SLOTS
                                    * SAMPLE_TICKS,
                    "The staffing scale workload did not execute every bounded slot check");
            helper.assertTrue(
                    unloadedRoute.stream().noneMatch(level::hasChunkAt),
                    "Automatic-duty scale evaluation force-loaded route chunks");
            registry.remove(squadId);
            helper.succeed();
        });
    }

    private static BlockPos firstUnloadedDestination(
            net.minecraft.server.level.ServerLevel level,
            BlockPos origin) {
        for (int chunkOffset = 8; chunkOffset <= 128; chunkOffset += 8) {
            int chunkX = (origin.getX() >> 4) + chunkOffset;
            int chunkZ = origin.getZ() >> 4;
            BlockPos candidate = new BlockPos(
                    (chunkX << 4) + 4,
                    origin.getY(),
                    (chunkZ << 4) + 4);
            if (level.isInWorldBounds(candidate)
                    && level.getWorldBorder().isWithinBounds(candidate)
                    && !level.hasChunkAt(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No unloaded route chunk was available");
    }
}
