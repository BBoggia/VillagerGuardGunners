package com.gamergaming.villagerguardgunners.village.coverage.live;

import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageReservations.Lease;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import tallestegg.guardvillagers.entities.Guard;

/** Owns navigation only while selected by the existing automatic MOVE goal. */
public final class GuardCoverageMovementController {
    private final Guard guard;
    private Lease lease;
    private Path ownedPath;
    private boolean active;
    private final com.gamergaming.villagerguardgunners.ai.navigation.GuardRouteProgress progress =
            new com.gamergaming.villagerguardgunners.ai.navigation.GuardRouteProgress();

    public GuardCoverageMovementController(Guard guard) { this.guard = guard; }
    public boolean canStart() { return VillageCoverageRuntime.lease(guard) != null; }
    public boolean shouldContinue() { return active && lease != null && lease.equals(VillageCoverageRuntime.lease(guard)); }
    public void start() {
        lease = VillageCoverageRuntime.lease(guard);
        active = lease != null;
        ownedPath = null;
        progress.clear();
        if (active) {
            guard.getNavigation().stop();
            VillageCoverageRuntime.requestPath(guard, lease);
        }
    }
    public void tick() {
        if (!shouldContinue()) return;
        var destination = VillageCoverageRuntime.destination(guard, lease);
        if (destination == null || !VillageCoverageRuntime.safe(guard, destination.position())) { finish(false, true); return; }
        BlockPos target = destination.position();
        if (com.gamergaming.villagerguardgunners.ai.navigation.GuardRouteArrival.at(guard, target, 1.5, 1)) { finish(true, false); return; }
        if (ownedPath == null) {
            var route = VillageCoverageRuntime.takePath(guard);
            if (route == null) return;
            if (!lease.equals(route.lease())) { VillageCoverageRuntime.requestPath(guard, lease); return; }
            if (route.positions().isEmpty() || route.positions().stream().anyMatch(p -> !VillageCoverageRuntime.safe(guard, p))) {
                finish(false, true); return;
            }
            for (int i = 1; i < route.positions().size(); i++) {
                if (!VillageCoverageRuntime.safeTransition(guard, route.positions().get(i - 1), route.positions().get(i))) {
                    finish(false, true); return;
                }
            }
            // Nodes came from a bounded, loaded-only walk graph. Native pathfinding
            // would construct a region that can request absent chunks.
            ownedPath = com.gamergaming.villagerguardgunners.ai.navigation.GuardLocalRoutes.fromVerifiedGround(route.positions(), target);
            try {
                if (!guard.getNavigation().moveTo(ownedPath, 0.8)) { finish(false, true); return; }
                ownedPath = guard.getNavigation().getPath();
                if (ownedPath == null) { finish(false, true); return; }
            } catch (RuntimeException exception) { finish(false, true); return; }
            progress.start(guard, ownedPath, guard.level().getGameTime());
        }
        if (!ownsPath()) { finish(false, false); return; }
        for (int i = ownedPath.getNextNodeIndex(); i < Math.min(ownedPath.getNodeCount(), ownedPath.getNextNodeIndex() + 8); i++) {
            if (!VillageCoverageRuntime.safe(guard, ownedPath.getNodePos(i))
                    || (i > 0 && !VillageCoverageRuntime.safeTransition(guard, ownedPath.getNodePos(i - 1), ownedPath.getNodePos(i)))) {
                finish(false, true); return;
            }
        }
        if (guard.getNavigation().isDone()) { finish(false, true); return; }
        if (progress.stalled(guard, ownedPath, guard.level().getGameTime(), 40)) {
            finish(false, true);
        }
    }
    public boolean ownsPath() { return progress.owns(guard, ownedPath); }
    public void stop() {
        if (active && lease != null) VillageCoverageRuntime.finish(guard, lease, false, false);
        stopOwnedPath(); active = false; lease = null;
    }
    private void finish(boolean arrived, boolean failed) {
        VillageCoverageRuntime.finish(guard, lease, arrived, failed);
        stopOwnedPath(); active = false;
    }
    private void stopOwnedPath() {
        if (ownsPath()) guard.getNavigation().stop();
        ownedPath = null;
        progress.clear();
    }

}
