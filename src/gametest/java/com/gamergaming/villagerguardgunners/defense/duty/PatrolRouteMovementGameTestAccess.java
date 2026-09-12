package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.squad.SquadMembership;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRoute;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteResolver;
import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import tallestegg.guardvillagers.GuardEntityType;
import tallestegg.guardvillagers.entities.Guard;

/** Test-only live proof for route execution through the production goal. */
public final class PatrolRouteMovementGameTestAccess {
    private PatrolRouteMovementGameTestAccess() {
    }

    public static void verifyLoadedRouteExecution(GameTestHelper helper) {
        var level = helper.getLevel();
        boolean originalEnabled = ModConfigs.GUARD_GUNNERS.enabled.get();
        boolean originalPatrolExecution =
                ModConfigs.GUARD_GUNNERS.squads.patrolRouteExecution.get();
        double originalPatrolSpeed =
                ModConfigs.GUARD_GUNNERS.squads.patrolMovementSpeed.get();
        int originalDwell =
                ModConfigs.GUARD_GUNNERS.squads.patrolWaypointDwellTicks.get();
        int originalFailures = ModConfigs.GUARD_GUNNERS.squads
                .patrolMaximumFailuresPerWaypoint.get();
        ModConfigs.GUARD_GUNNERS.enabled.accept(true);
        ModConfigs.GUARD_GUNNERS.squads.patrolRouteExecution.accept(true);
        ModConfigs.GUARD_GUNNERS.squads.patrolMovementSpeed.accept(1.1D);
        ModConfigs.GUARD_GUNNERS.squads.patrolWaypointDwellTicks.accept(0);
        ModConfigs.GUARD_GUNNERS.squads.patrolMaximumFailuresPerWaypoint.accept(5);
        BlockPos first = helper.absolutePos(new BlockPos(30003, 2, 30008));
        BlockPos second = helper.absolutePos(new BlockPos(30009, 2, 30008));
        var area = new LoadedDutyTestArea(level, first);
        for (int x = -2; x <= 11; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos floor = first.offset(x - 3, -1, z);
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        Guard guard = GuardEntityType.GUARD.get().create(level);
        helper.assertTrue(guard != null, "Could not create the route-movement guard");
        UUID squadId = UUID.fromString("1b529dd7-ee14-4b62-8eaf-b66d10021ed1");
        SquadRegistrySavedData registry = SquadRegistrySavedData.get(
                level.getServer());
        registry.remove(squadId);
        registry.register(squadId, "Live Route", null, level.getGameTime())
                .orElseThrow();
        SquadPatrolRoute route = registry.replacePatrolRoute(
                        squadId,
                        level.dimension().location(),
                        List.of(first, second),
                        level.getGameTime())
                .route();
        helper.assertTrue(route != null && route.executable(),
                "Could not author the live executable route");

        // Exercise the production route goal without wandering/avoidance goals leaving
        // the prepared corridor while the route is being acquired.
        guard.goalSelector.removeAllGoals(goal -> !(goal instanceof GuardAutomaticDutyMovementGoal));
        guard.targetSelector.removeAllGoals(goal -> true);
        guard.setInvulnerable(true);
        helper.onEachTick(() -> guard.setTarget(null));
        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        GuardSquadAccess.setMembership(guard, SquadMembership.assigned(squadId));
        guard.setPatrolling(true);
        guard.setPatrolPos(first.offset(-6, 0, 0));
        guard.moveTo(first.getX() + 0.5D, first.getY(), first.getZ() + 0.5D);
        level.addFreshEntity(guard);

        Runnable restore = () -> {
            guard.discard(); registry.remove(squadId); area.close();
            ModConfigs.GUARD_GUNNERS.enabled.accept(originalEnabled);
            ModConfigs.GUARD_GUNNERS.squads.patrolRouteExecution.accept(
                    originalPatrolExecution);
            ModConfigs.GUARD_GUNNERS.squads.patrolMovementSpeed.accept(
                    originalPatrolSpeed);
            ModConfigs.GUARD_GUNNERS.squads.patrolWaypointDwellTicks.accept(
                    originalDwell);
            ModConfigs.GUARD_GUNNERS.squads
                    .patrolMaximumFailuresPerWaypoint.accept(originalFailures);
        };
        helper.runAfterDelay(150, restore);
        helper.succeedWhen(() -> {
            boolean reachedSecond = GuardPatrolRouteMovementController.atPosition(
                    guard.getX(), guard.getY(), guard.getZ(), second);
            helper.assertTrue(reachedSecond,
                    "The routed guard did not reach its second loaded waypoint; position="
                            + guard.blockPosition() + ", expected=" + second
                            + ", target=" + guard.getTarget() + ", ai=" + guard.isEffectiveAi()
                            + ", goals=" + guard.goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()).toList()
                            + ", route=" + guard.goalSelector.getAvailableGoals().stream().map(g -> g.getGoal())
                                    .filter(GuardAutomaticDutyMovementGoal.class::isInstance).map(GuardAutomaticDutyMovementGoal.class::cast)
                                    .map(GuardAutomaticDutyMovementGoal::capturePatrolDiagnostics).toList());
            helper.assertTrue(guard.isPatrolling(),
                    "Route execution rewrote Guard Villagers' upstream patrol flag");
            helper.assertTrue(squadId.equals(GuardSquadAccess.getSquadId(guard)),
                    "Route execution changed the guard's squad membership");
            GuardAutomaticDutyMovementGoal goal = guard.goalSelector
                    .getAvailableGoals()
                    .stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(GuardAutomaticDutyMovementGoal.class::isInstance)
                    .map(GuardAutomaticDutyMovementGoal.class::cast)
                    .findFirst()
                    .orElseThrow();
            GuardPatrolRouteMovementController.RuntimeSnapshot snapshot =
                    goal.capturePatrolDiagnostics();
            helper.assertTrue(snapshot.executionEnabled()
                            && snapshot.movementSpeed() == 1.1D
                            && snapshot.waypointDwellTicks() == 0
                            && snapshot.maximumFailuresPerWaypoint() == 5,
                    "The production route controller did not consume the configured policy: "
                            + snapshot);
            restore.run();
            registry.remove(squadId);
            guard.discard();
        });
    }

    public static void verifyUnloadedRouteYieldsWithoutLoadingChunks(
            GameTestHelper helper) {
        var level = helper.getLevel();
        boolean originalEnabled = ModConfigs.GUARD_GUNNERS.enabled.get();
        boolean originalPatrolExecution =
                ModConfigs.GUARD_GUNNERS.squads.patrolRouteExecution.get();
        ModConfigs.GUARD_GUNNERS.enabled.accept(true);
        ModConfigs.GUARD_GUNNERS.squads.patrolRouteExecution.accept(true);

        BlockPos near = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos first = firstUnloadedDestination(level, near);
        BlockPos second = first.offset(4, 0, 0);
        helper.assertTrue(
                !level.hasChunkAt(first) && !level.hasChunkAt(second),
                "Could not locate unloaded route chunks for the live test");

        Guard guard = GuardEntityType.GUARD.get().create(level);
        helper.assertTrue(guard != null, "Could not create the unloaded-route guard");
        UUID squadId = UUID.fromString("f1ccfb35-78b4-47ae-9544-3ec754d3a9e7");
        SquadRegistrySavedData registry = SquadRegistrySavedData.get(level.getServer());
        registry.remove(squadId);
        registry.register(squadId, "Unloaded Route", null, level.getGameTime())
                .orElseThrow();
        SquadPatrolRoute route = registry.replacePatrolRoute(
                        squadId,
                        level.dimension().location(),
                        List.of(first, second),
                        level.getGameTime())
                .route();
        helper.assertTrue(route != null && route.executable(),
                "Could not author the unloaded executable route");

        guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        GuardSquadAccess.setMembership(guard, SquadMembership.assigned(squadId));
        guard.moveTo(near.getX() + 0.5D, near.getY(), near.getZ() + 0.5D);
        level.addFreshEntity(guard);

        try {
            GuardPatrolRouteMovementController controller =
                    new GuardPatrolRouteMovementController(guard);
            GuardPatrolRouteMovementController.RuntimeSnapshot snapshot =
                    controller.snapshot();
            helper.assertTrue(
                    !SquadPatrolRouteResolver.hasLoadedWaypoint(guard)
                            && !controller.hasExecutableRoute()
                            && !controller.canStart(),
                    "An entirely unloaded route still claimed guard movement");
            helper.assertTrue(
                    snapshot.status()
                                    == GuardPatrolRouteMovementController.MovementStatus
                                            .DESTINATION_UNLOADED
                            && !snapshot.destinationLoaded(),
                    "The route controller did not diagnose its unloaded destination: "
                            + snapshot);
            helper.assertTrue(
                    !level.hasChunkAt(first) && !level.hasChunkAt(second),
                    "Route evaluation force-loaded an unloaded waypoint chunk");
            helper.succeed();
        } finally {
            ModConfigs.GUARD_GUNNERS.enabled.accept(originalEnabled);
            ModConfigs.GUARD_GUNNERS.squads.patrolRouteExecution.accept(
                    originalPatrolExecution);
            registry.remove(squadId);
            guard.discard();
        }
    }

    private static BlockPos firstUnloadedDestination(
            net.minecraft.server.level.ServerLevel level,
            BlockPos origin) {
        for (int chunkOffset = 8; chunkOffset <= 128; chunkOffset += 8) {
            BlockPos candidate = origin.offset(chunkOffset * 16, 0, 0);
            if (level.isInWorldBounds(candidate)
                    && level.isInWorldBounds(candidate.above())
                    && level.isInWorldBounds(candidate.below())
                    && level.getWorldBorder().isWithinBounds(candidate)
                    && !level.hasChunkAt(candidate)
                    && !level.hasChunkAt(candidate.offset(4, 0, 0))) {
                return candidate;
            }
        }
        throw new IllegalStateException("No unloaded in-bounds route chunk was available");
    }
}
