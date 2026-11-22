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
    private long nextCheck, retryAt;
    private final com.gamergaming.villagerguardgunners.ai.navigation.GuardRouteProgress routeProgress =
            new com.gamergaming.villagerguardgunners.ai.navigation.GuardRouteProgress();
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
        if (com.gamergaming.villagerguardgunners.ai.navigation.GuardRouteArrival.at(guard, duty.anchor(), 1.5, 1)) {
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
            if (!routeProgress.owns(guard, owned)) { routeProgress.clear(); owned = null; retryAt = now + 20; return; }
            boolean stalled = routeProgress.stalled(guard, owned, now, 40);
            if (now >= nextCheck) {
                nextCheck = now + 10;
                if (guard.getNavigation().isDone() || stalled
                        || !planner.safeRoute(owned, owned.getNextNodeIndex(), java.util.List.of(),
                                TacticalAssignmentBounds.UNBOUNDED, !planner.safeStanding(guard.position()))) stop();
            }
            return;
        }
        if (!budget.takePathSlot(now)) return;
        attempts++;
        var path = com.gamergaming.villagerguardgunners.ai.navigation.GuardLocalRoutes.create(guard, duty.anchor(), TacticalPositionPlanner.PATH_RANGE);
        if (path == null && com.gamergaming.villagerguardgunners.ai.navigation.GuardLocalRoutes.deferred(guard)) { attempts--; retryAt = now + 20; return; }
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
            nextCheck = now + 10;
            routeProgress.start(guard, owned, now);
        }
    }
    public boolean ownsPath() { return routeProgress.owns(guard, owned); }
    public void stop() {
        if (ownsPath()) guard.getNavigation().stop();
        routeProgress.clear();
        owned = null;
    }
}
