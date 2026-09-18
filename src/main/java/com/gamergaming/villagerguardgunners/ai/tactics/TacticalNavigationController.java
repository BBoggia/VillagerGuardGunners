package com.gamergaming.villagerguardgunners.ai.tactics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import com.gamergaming.villagerguardgunners.squad.SquadCommandType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Owns one screened route; failures back off without surrendering combat ownership. */
public final class TacticalNavigationController {
    private static final int FAILED_LIMIT = 8;
    public static final int MAX_LANE_TICKS = 120;
    private final Guard guard;
    private final TacticalPositionPlanner planner;
    private final Map<BlockPos, Long> failed = new LinkedHashMap<>();
    private Vec3 engagementAnchor;
    private TacticalAssignmentBounds bounds = TacticalAssignmentBounds.UNBOUNDED;
    private Path path;
    private BlockPos destination;
    private String status = "IDLE";
    private long nextValidation;
    private long lastProgress;
    private long retryAt;
    private int lastNode;
    private double bestRemaining;
    private int pathAttempts;
    private boolean laneChange;
    private long nextTerrainCheck;
    private boolean hazardous;
    private UUID laneTarget;
    private ItemStack laneWeapon;
    private int laneOrdersRevision;
    private SquadCommand laneCommand;
    private long laneStartedAt;

    public TacticalNavigationController(Guard guard) { this.guard = guard; planner = new TacticalPositionPlanner(guard); }

    public void tick(GuardTacticalController tactics, SquadCommand command, double engagementRange,
            java.util.function.BooleanSupplier pathSlot, double speed, boolean reposition, boolean allowPlanning) {
        tick(tactics, command, engagementRange, pathSlot, speed, reposition, allowPlanning, null);
    }

    public void tick(GuardTacticalController tactics, SquadCommand command, double engagementRange,
            java.util.function.BooleanSupplier pathSlot, double speed, boolean reposition, boolean allowPlanning,
            TacticalFiringLane.Request firing) {
        try {
            tickSafely(tactics, command, engagementRange, pathSlot, speed, reposition, allowPlanning, firing);
        } catch (RuntimeException ignored) {
            fail(guard.level().getGameTime(), "INTEGRATION_FAILED");
        }
    }

    private void tickSafely(GuardTacticalController tactics, SquadCommand command, double engagementRange,
            java.util.function.BooleanSupplier pathSlot, double speed, boolean reposition, boolean allowPlanning,
            TacticalFiringLane.Request firing) {
        long now = guard.level().getGameTime();
        if (engagementAnchor == null) {
            engagementAnchor = guard.getPatrolPos() == null ? guard.position() : Vec3.atBottomCenterOf(guard.getPatrolPos());
            for (var wrapped : guard.goalSelector.getAvailableGoals()) {
                if (wrapped.getGoal() instanceof com.gamergaming.villagerguardgunners.ai.GuardAutomaticDutyMovementGoal duty) {
                    var route = duty.capturePatrolDiagnostics();
                    if (route.destination() != null) engagementAnchor = Vec3.atBottomCenterOf(route.destination());
                }
            }
        }
        var updatedBounds = bounds(command, engagementRange);
        if (!updatedBounds.equals(bounds) && path != null) {
            bounds = updatedBounds; fail(now, "ASSIGNMENT_CHANGED"); return;
        }
        bounds = updatedBounds;
        failed.entrySet().removeIf(e -> now >= e.getValue());
        if (path != null && laneChange && (!reposition || !Objects.equals(laneCommand, command))) {
            cancel(); status = "INTENT_CHANGED";
        }
        if (path != null) {
            if (laneChange && now - laneStartedAt >= MAX_LANE_TICKS) { fail(now, "LANE_TIMEOUT"); return; }
            if (guard.getNavigation().getPath() != path) { fail(now, "ROUTE_REPLACED"); return; }
            guard.getNavigation().setSpeedModifier(speed);
            if (guard.position().distanceToSqr(Vec3.atBottomCenterOf(destination)) < 1) {
                boolean retainPosition = laneChange;
                Vec3 arrived = planner.standingPosition(destination);
                cancel(); status = "ARRIVED"; retryAt = now + 10;
                if (retainPosition && arrived != null) FiringPositionReservations.reserve(guard, arrived);
            } else if (guard.getNavigation().isDone()) {
                fail(now, "STOPPED_SHORT");
            } else {
                int node = path.getNextNodeIndex();
                double remaining = remainingDistance(path, node, guard.position());
                if (node > lastNode || remaining < bestRemaining - .25) {
                    lastNode = node; bestRemaining = remaining; lastProgress = now;
                }
                if (now - lastProgress >= Math.max(20, com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.performance.repositionProgressCheckTicks.get() * 2)) { fail(now, "NO_PROGRESS"); return; }
                if (now >= nextValidation) {
                    nextValidation = now + 10;
                    if (!planner.safeRoute(path, node, tactics.tracker().observations(), bounds,
                            !planner.safeStanding(guard.position()))) { fail(now, "ROUTE_INVALIDATED"); return; }
                    if (laneChange) {
                        Vec3 reservedPoint = planner.standingPosition(destination);
                        if (reservedPoint == null || !FiringPositionReservations.reserve(guard, reservedPoint)) {
                            fail(now, "POSITION_RESERVED"); return;
                        }
                        var lane = TacticalFiringLane.capture(guard, firing);
                        if (lane == null || planner.standingPosition(destination) == null
                                || !lane.clearFrom(planner.standingPosition(destination))) {
                            fail(now, "LANE_INVALIDATED"); return;
                        }
                    }
                }
                return;
            }
        }
        // Native ground navigation cannot plan while airborne. Landing is not a failed destination.
        if (!allowPlanning || now < retryAt || (!guard.onGround() && !guard.isInWater() && !guard.isInLava()) || !pathSlot.getAsBoolean()) return;
        var plan = planner.plan(tactics.tracker().observations(), bounds, failed.keySet(), reposition, tactics.tracker().supportPositions(), firing);
        pathAttempts += planner.pathsBuilt();
        status = plan.reason();
        destination = plan.destination();
        if (plan.path() == null) {
            rememberFailure(destination, now);
            retryAt = now + 20;
            return;
        }
        if (reposition && !FiringPositionReservations.reserve(guard, planner.standingPosition(destination))) {
            fail(now, "POSITION_RESERVED"); return;
        }
        laneChange = reposition;
        if (!guard.getNavigation().moveTo(plan.path(), speed)) { fail(now, "START_FAILED"); return; }
        // Vanilla may retain an equivalent existing Path. Own and validate the installed object.
        path = guard.getNavigation().getPath();
        if (path == null || !path.canReach() || path.getEndNode() == null
                || path.getEndNode().asBlockPos().distSqr(destination) > 1
                || !planner.safeRoute(path, path.getNextNodeIndex(), tactics.tracker().observations(), bounds,
                        !planner.safeStanding(guard.position()))) { fail(now, "INSTALLED_ROUTE_INVALID"); return; }
        if (reposition) {
            laneTarget = firing.target().getUUID(); laneWeapon = guard.getMainHandItem();
            laneOrdersRevision = GuardRoleResolver.effectiveRevision(guard); laneCommand = command; laneStartedAt = now;
        }
        status = "MOVING";
        lastNode = path.getNextNodeIndex();
        bestRemaining = remainingDistance(path, lastNode, guard.position());
        lastProgress = now;
        nextValidation = now + 10;
    }

    private TacticalAssignmentBounds bounds(SquadCommand command, double range) {
        if (command != null && command.type() == SquadCommandType.DEFEND) {
            return new TacticalAssignmentBounds(Vec3.atBottomCenterOf(command.position()), Math.max(.5, command.radius() - 1));
        }
        var movement = GuardRoleResolver.effectiveOrders(guard).movement();
        if (movement.treatAsPatrolling(guard.isPatrolling()) || guard.isFollowing()) {
            return new TacticalAssignmentBounds(engagementAnchor, Math.max(4, Math.min(32, range)));
        }
        return GuardCombatDuty.bounds(guard);
    }

    public static double remainingDistance(Path path, int from, Vec3 current) {
        double distance = 0;
        for (int i = from; i < path.getNodeCount(); i++) {
            Vec3 next = Vec3.atBottomCenterOf(path.getNodePos(i));
            distance += current.distanceTo(next); current = next;
        }
        return distance;
    }

    private void fail(long now, String reason) {
        rememberFailure(destination, now); cancel(); FiringPositionReservations.release(guard); status = reason; retryAt = now + 20;
    }
    private void rememberFailure(BlockPos pos, long now) {
        if (pos == null) return;
        if (failed.size() >= FAILED_LIMIT && !failed.containsKey(pos)) failed.remove(failed.keySet().iterator().next());
        failed.put(pos.immutable(), now + 80);
    }
    public boolean isLaneChange() { return path != null && laneChange; }
    public boolean canContinueLane(LivingEntity target, boolean visible,
            boolean permitted, WeaponReadiness readiness) {
        return isLaneChange() && ownsPath() && target != null && target.isAlive() && visible && permitted
                && target.getUUID().equals(laneTarget) && guard.getMainHandItem() == laneWeapon
                && GuardRoleResolver.effectiveRevision(guard) == laneOrdersRevision
                && readiness.permitsOffensiveMovement();
    }
    public boolean ownsPath() { return path != null && guard.getNavigation().getPath() == path; }
    public void invalidateForWorldChange() { fail(guard.level().getGameTime(), "WORLD_CHANGED"); }
    public void cancel() {
        if (path != null) FiringPositionReservations.release(guard);
        if (path != null && guard.getNavigation().getPath() == path) guard.getNavigation().stop();
        if (path != null) status = "CANCELLED";
        path = null;
        laneChange = false; laneTarget = null; laneWeapon = null; laneCommand = null;
    }
    public void clear() {
        cancel(); FiringPositionReservations.release(guard); failed.clear(); engagementAnchor = null; destination = null; status = "IDLE";
        retryAt = 0; nextValidation = 0; pathAttempts = 0; nextTerrainCheck = 0; hazardous = false;
    }
    public boolean needsHazardEscape() {
        long now = guard.level().getGameTime();
        if (now >= nextTerrainCheck) {
            nextTerrainCheck = now + 10;
            try { hazardous = !planner.safeStanding(guard.position()); }
            catch (RuntimeException ignored) { hazardous = true; }
        }
        return hazardous;
    }
    public boolean isMoving() { return ownsPath() && !path.isDone() && !guard.getNavigation().isDone(); }
    public Vec3 lookDestination() { return isMoving() ? path.getNextEntityPos(guard) : null; }
    public String status() { return status; }
    public BlockPos destination() { return destination; }
    public int pathAttempts() { return pathAttempts; }
    public int failedDestinations() { return failed.size(); }
    public int candidatesChecked() { return planner.candidatesChecked(); }
}
