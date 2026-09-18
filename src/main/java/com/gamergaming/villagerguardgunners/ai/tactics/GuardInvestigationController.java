package com.gamergaming.villagerguardgunners.ai.tactics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import com.gamergaming.villagerguardgunners.squad.SquadCommandType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** One bounded peek at a frozen sighting, using the combat goal's shared path slot. */
public final class GuardInvestigationController {
    public static final int MAX_SIGHTING_AGE = 100, MAX_TICKS = 80, COOLDOWN = 100, MAX_ATTEMPTS = 2;
    private final Guard guard;
    private final TacticalPositionPlanner planner;
    private ThreatMemory.Observation sighting;
    private Vec3 origin;
    private Path path;
    private BlockPos destination;
    private long startedAt, retryAt, cooldownUntil, nextValidation, lastProgress;
    private int attempts, lastNode, revision;
    private double bestRemaining;
    private ItemStack weapon;
    private SquadCommand command;
    private final Set<BlockPos> failed = new HashSet<>();
    private String status = "IDLE";
    public GuardInvestigationController(Guard guard) { this.guard = guard; planner = new TacticalPositionPlanner(guard); }

    public void observe(LivingEntity target, GuardThreatTracker tracker) {
        if (target == null || (sighting != null && !sighting.id().equals(target.getUUID()))) clear();
        if (target == null) return;
        var fresh = tracker.remembered(target.getUUID());
        if (fresh != null && (sighting == null || fresh.observedAt() > sighting.observedAt())) sighting = fresh;
    }
    public boolean canInvestigate(LivingEntity target, boolean visible, boolean permitted,
                                  WeaponReadiness readiness, SquadCommand active) {
        long now = guard.level().getGameTime();
        var movement = GuardRoleResolver.effectiveOrders(guard).movement();
        boolean defend = active != null && active.type() == SquadCommandType.DEFEND;
        boolean allowed = target != null && target.isAlive() && !target.isRemoved()
                && target.level() == guard.level() && !visible && permitted
                && readiness.permitsOffensiveMovement() && sighting != null && target.getUUID().equals(sighting.id())
                && sighting.dimension().equals(guard.level().dimension().location().toString())
                && now >= sighting.observedAt() && now - sighting.observedAt() < MAX_SIGHTING_AGE
                && now >= cooldownUntil && !guard.isFollowing()
                && (!(guard instanceof com.gamergaming.villagerguardgunners.api.GuardCoverageAccess access)
                    || !access.villagerGuardGunners$hasFollowOrder())
                && !movement.treatAsPatrolling(guard.isPatrolling()) && (!movement.holdsPosition() || defend)
                && (active == null || defend || active.type() == SquadCommandType.FOCUS_TARGET);
        if (origin != null && (now - startedAt >= MAX_TICKS || revision != GuardRoleResolver.effectiveRevision(guard)
                || weapon != guard.getMainHandItem() || !Objects.equals(command, active))) {
            finish("CONTEXT_CHANGED"); return false;
        }
        return allowed;
    }
    public void tick(GuardTacticalController tactics, SquadCommand active,
                     java.util.function.BooleanSupplier pathSlot, double speed) {
        try { tickSafely(tactics, active, pathSlot, speed); }
        catch (RuntimeException ignored) { finish("INTEGRATION_FAILED"); }
    }
    private void tickSafely(GuardTacticalController tactics, SquadCommand active,
                            java.util.function.BooleanSupplier pathSlot, double speed) {
        long now = guard.level().getGameTime();
        if (sighting == null || now < cooldownUntil) return;
        if (origin == null) {
            origin = guard.position(); startedAt = now; attempts = 0; failed.clear();
            revision = GuardRoleResolver.effectiveRevision(guard); weapon = guard.getMainHandItem(); command = active;
        }
        var bounds = active != null && active.type() == SquadCommandType.DEFEND
                ? new TacticalAssignmentBounds(Vec3.atBottomCenterOf(active.position()), Math.max(.5, active.radius() - 1))
                : GuardCombatDuty.bounds(guard);
        var threats = new ArrayList<>(tactics.tracker().observations());
        if (threats.stream().noneMatch(t -> t.id().equals(sighting.id()))) threats.add(sighting);
        if (path != null) {
            if (!ownsPath()) { finish("ROUTE_REPLACED"); return; }
            if (guard.position().distanceToSqr(Vec3.atBottomCenterOf(destination)) < 1) { finish("SEARCHED"); return; }
            if (guard.getNavigation().isDone()) { invalidate(); return; }
            int node = path.getNextNodeIndex();
            double remaining = TacticalNavigationController.remainingDistance(path, node, guard.position());
            if (node > lastNode || remaining < bestRemaining - .25) {
                lastNode = node; bestRemaining = remaining; lastProgress = now;
            }
            if (now - lastProgress >= 30) { finish("NO_PROGRESS"); return; }
            if (now >= nextValidation) {
                nextValidation = now + 10;
                if (!planner.safePeekRoute(path, threats, bounds, origin)) { invalidate(); return; }
            }
            return;
        }
        if (now < retryAt || !guard.onGround()) return;
        if (attempts >= MAX_ATTEMPTS) { finish("ATTEMPTS_EXHAUSTED"); return; }
        if (!pathSlot.getAsBoolean()) return;
        attempts++;
        var plan = planner.planPeek(sighting.position(), origin, threats, bounds, failed);
        destination = plan.destination(); status = plan.reason(); retryAt = now + 20;
        if (plan.path() == null) { if (destination != null) failed.add(destination); return; }
        if (!guard.getNavigation().moveTo(plan.path(), speed)) { invalidate(); return; }
        path = guard.getNavigation().getPath();
        if (path == null || !path.canReach() || path.getEndNode() == null
                || path.getEndNode().asBlockPos().distSqr(destination) > 1
                || !planner.safePeekRoute(path, threats, bounds, origin)) { invalidate(); return; }
        status = "MOVING"; lastProgress = now; nextValidation = now + 10;
        lastNode = path.getNextNodeIndex(); bestRemaining = TacticalNavigationController.remainingDistance(path, lastNode, guard.position());
    }
    public void invalidate() {
        if (destination != null) failed.add(destination);
        stopPath(); retryAt = guard.level().getGameTime() + 20; status = "ROUTE_INVALIDATED";
    }
    public void cancel() {
        if (origin != null) finish("INTERRUPTED");
    }
    private void finish(String reason) {
        stopPath(); origin = null; status = reason; cooldownUntil = guard.level().getGameTime() + COOLDOWN;
    }
    private void stopPath() { if (ownsPath()) guard.getNavigation().stop(); path = null; }
    public void clear() {
        stopPath(); sighting = null; origin = null; destination = null; weapon = null; command = null;
        attempts = 0; failed.clear(); cooldownUntil = 0; retryAt = 0; status = "IDLE";
    }
    public boolean ownsPath() { return path != null && guard.getNavigation().getPath() == path; }
    public boolean isMoving() { return ownsPath() && !guard.getNavigation().isDone(); }
    public Vec3 lookDestination() { return isMoving() ? path.getNextEntityPos(guard) : null; }
    public Vec3 lastSeenPosition() { return sighting == null ? null : sighting.position(); }
    public BlockPos destination() { return destination; }
    public int attempts() { return attempts; }
    public String status() { return status; }
}
