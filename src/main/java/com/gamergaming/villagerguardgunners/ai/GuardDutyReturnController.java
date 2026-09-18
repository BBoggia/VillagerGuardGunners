package com.gamergaming.villagerguardgunners.ai;

import com.gamergaming.villagerguardgunners.ai.tactics.*;
import com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Resumes the saved local duty after combat without changing permanent orders. */
public final class GuardDutyReturnController {
    public static final int MAX_ATTEMPTS = 4, MAX_RETURN_TICKS = 160;
    private final Guard guard;
    private GuardCombatDuty.Duty returningDuty;
    private long returnStarted;
    private int attempts;
    private final SquadCommandMovementController budget;
    private final TacticalPositionPlanner planner;
    private Path owned;
    private long nextCheck, lastProgress, retryAt;
    private double bestRemaining;
    private int lastNode;
    public GuardDutyReturnController(Guard guard) { this.guard = guard; budget = new SquadCommandMovementController(guard); planner = new TacticalPositionPlanner(guard); }
    public boolean canStart() {
        try { return eligible(); }
        catch (RuntimeException ignored) { return false; }
    }
    private boolean eligible() {
        var duty = GuardCombatDuty.current(guard);
        if (duty == null || (guard.getTarget() != null && guard.getTarget().isAlive())) return false;
        if (returningDuty != null && returningDuty.equals(duty)
                && (attempts >= MAX_ATTEMPTS && owned == null
                    || guard.level().getGameTime() - returnStarted >= MAX_RETURN_TICKS)) {
            stop(); GuardCombatDuty.forget(guard); returningDuty = null; return false;
        }
        if (!(guard instanceof com.gamergaming.villagerguardgunners.api.GuardCoverageAccess access)
                || !access.villagerGuardGunners$nativeCoverageMovementSafe()
                || com.gamergaming.villagerguardgunners.api.GuardSidearmAccess.getState(guard)
                    == com.gamergaming.villagerguardgunners.sidearm.SidearmState.RECOVERY_REQUIRED
                || !com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).readable()) return false;
        if (guard.position().distanceToSqr(Vec3.atBottomCenterOf(duty.anchor())) <= 2.25) {
            GuardCombatDuty.forget(guard); return false;
        }
        var resupply = GuardArmoryResupplyAccess.getController(guard);
        return !com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard)
                && !com.gamergaming.villagerguardgunners.medical.MedicRuntime.busy(guard)
                && !com.gamergaming.villagerguardgunners.medical.TransportRuntime.busy(guard)
                && (resupply == null || !resupply.hasActiveRequest())
                && com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime.plan(guard) == null;
    }
    public void tick() {
        if (guard.level().getGameTime() < retryAt) return;
        try { tickSafely(); }
        catch (RuntimeException ignored) { stop(); retryAt = guard.level().getGameTime() + 20; }
    }
    private void tickSafely() {
        if (!canStart()) { stop(); return; }
        var duty = GuardCombatDuty.current(guard);
        long now = guard.level().getGameTime();
        if (!duty.equals(returningDuty)) {
            stop(); returningDuty = duty; returnStarted = now; attempts = 0;
        }
        if (owned != null) {
            if (guard.getNavigation().getPath() != owned) { owned = null; return; }
            int node = owned.getNextNodeIndex();
            double remaining = TacticalNavigationController.remainingDistance(owned, node, guard.position());
            if (node > lastNode || remaining < bestRemaining - .25) {
                lastNode = node; bestRemaining = remaining; lastProgress = now;
            }
            if (now >= nextCheck) {
                nextCheck = now + 10;
                if (guard.getNavigation().isDone() || now - lastProgress >= 40
                        || !planner.safeRoute(owned, owned.getNextNodeIndex(), java.util.List.of(),
                                TacticalAssignmentBounds.UNBOUNDED, !planner.safeStanding(guard.position()))) stop();
            }
            return;
        }
        if (!budget.takePathSlot(now)) return;
        attempts++;
        if (!planner.pathRegionLoaded()) return;
        var path = guard.getNavigation().createPath(duty.anchor(), 0, TacticalPositionPlanner.PATH_RANGE);
        if (path == null || !path.canReach() || path.getEndNode() == null
                || path.getEndNode().asBlockPos().distSqr(duty.anchor()) > 1
                || !planner.safeRoute(path, 0, java.util.List.of(), TacticalAssignmentBounds.UNBOUNDED,
                        !planner.safeStanding(guard.position()))) return;
        if (guard.getNavigation().moveTo(path, .8)) {
            owned = guard.getNavigation().getPath();
            if (owned == null || !owned.canReach() || owned.getEndNode() == null
                    || owned.getEndNode().asBlockPos().distSqr(duty.anchor()) > 1
                    || !planner.safeRoute(owned, owned.getNextNodeIndex(), java.util.List.of(),
                            TacticalAssignmentBounds.UNBOUNDED, !planner.safeStanding(guard.position()))) {
                stop(); return;
            }
            lastNode = owned.getNextNodeIndex();
            bestRemaining = TacticalNavigationController.remainingDistance(owned, lastNode, guard.position());
            lastProgress = now; nextCheck = now + 10;
        }
    }
    public boolean ownsPath() { return owned != null && guard.getNavigation().getPath() == owned; }
    public void stop() {
        if (owned != null && guard.getNavigation().getPath() == owned) guard.getNavigation().stop();
        owned = null;
    }
}
