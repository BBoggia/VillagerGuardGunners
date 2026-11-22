package com.gamergaming.villagerguardgunners.medical;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Slow, loaded-only transport. Both pickup and carrying paths are screened before movement. */
public final class TransportMovementGoal extends Goal {
    private final Guard guard;
    private Path ownedPath;
    private long nextPath, progressAt;
    private Vec3 previous;
    private String status = "idle", pathFailure = "none";
    public String status() { return status; }
    private boolean noPath(String reason) { pathFailure = reason; return false; }
    public TransportMovementGoal(Guard guard) { this.guard = guard; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    @Override public boolean canUse() { var lease = TransportRuntime.lease(guard); return lease != null && lease.carrier == guard && TransportRuntime.valid(lease); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { guard.getNavigation().stop(); ownedPath = null; nextPath = 0; progressAt = guard.level().getGameTime() + 80; previous = guard.position(); }
    @Override public void stop() { if (TransportRuntime.lease(guard) != null) status = TransportRuntime.valid(TransportRuntime.lease(guard)) ? "goal controls disabled" : "lease invalid"; if (ownedPath != null && guard.getNavigation().getPath() == ownedPath) guard.getNavigation().stop(); ownedPath = null; TransportRuntime.cancel(guard); }
    public void invalidateForWorldChange() {
        guard.getNavigation().stop(); ownedPath = null; nextPath = 0;
    }
    @Override public void tick() {
        var lease = TransportRuntime.lease(guard); if (!TransportRuntime.valid(lease)) { TransportRuntime.cancel(guard); return; }
        long now = guard.level().getGameTime();
        if (guard.getNavigation().getPath() != ownedPath) invalidateForWorldChange();
        Vec3 target = lease.carried ? TransportRuntime.destination(lease) : lease.patient.position();
        if (target == null) { TransportRuntime.cancel(guard); return; }
        guard.getLookControl().setLookAt(target.x, target.y, target.z);
        if (lease.carried && guard.distanceToSqr(target) <= 9) {
            guard.getNavigation().stop(); status = "delivering";
            if (!TransportRuntime.deliver(lease)) { status = "delivery refused"; TransportRuntime.cancel(guard); }
            else status = "delivered";
            return;
        }
        if (!CasualtyCarePolicy.timeForEvacuation(lease.patient)) { status = "time reserved for field care"; TransportRuntime.cancel(guard); return; }
        if (!lease.carried && guard.distanceToSqr(target) <= 4) {
            // Verify a route with passenger clearance before putting anyone on the carrier.
            var cot = TransportRuntime.destination(lease);
            if (cot == null || !pathTo(cot, true)) { status = "carry path: " + pathFailure; TransportRuntime.failed(guard); return; }
            if (!TransportRuntime.pickup(lease)) { status = "pickup rejected"; TransportRuntime.cancel(guard); return; }
            status = "carrying";
            nextPath = now + 20; previous = guard.position(); progressAt = now + 80; return;
        }
        if (now >= progressAt) {
            if (guard.position().distanceToSqr(previous) < .0625) { status = "no movement progress"; TransportRuntime.failed(guard); return; }
            previous = guard.position(); progressAt = now + 80;
        }
        if (ownedPath != null && lease.carried) for (int i = ownedPath.getNextNodeIndex(); i < Math.min(ownedPath.getNodeCount(), ownedPath.getNextNodeIndex() + 8); i++) {
            if (!TransportRuntime.safeCarryStanding(guard, ownedPath.getNodePos(i))) { status = "upcoming path blocked"; TransportRuntime.failed(guard); return; }
        }
        if (now >= nextPath) { nextPath = now + 20; if (!pathTo(target, lease.carried)) { status = "travel path: " + pathFailure; TransportRuntime.failed(guard); } }
    }
    private boolean pathTo(Vec3 target, boolean carrying) {
        if (!(guard.level() instanceof ServerLevel level)) return false;
        double range = guard.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (!Double.isFinite(range) || range < 0 || range > 64) return noPath("follow range " + range);
        int radius = (int) Math.ceil(range) + 16; var pos = guard.blockPosition();
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++)
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++) if (!level.getChunkSource().hasChunk(x, z)) return noPath("unloaded chunk " + x + "," + z);
        Path path;
        if (carrying) {
            path = CotApproachRoutes.prepare(guard.getNavigation(), guard.position(),
                    TransportRuntime.approaches(TransportRuntime.lease(guard)),
                    point -> TransportRuntime.safeCarryStanding(guard, point));
            if (path == null) return noPath("no reachable cot approach with passenger clearance");
        } else {
            try { path = guard.getNavigation().createPath(BlockPos.containing(target), 0); }
            catch (RuntimeException exception) { return noPath("native path exception"); }
            if (path == null || !path.canReach() || path.getNodeCount() > CotApproachRoutes.MAX_NODES)
                return noPath("unreachable or oversized native path");
        }
        if (!guard.getNavigation().moveTo(path, carrying ? .45 : .65)) return noPath("native move rejected");
        Path installed = guard.getNavigation().getPath();
        if (carrying && CotApproachRoutes.select(java.util.List.of(path.getTarget()), point -> installed,
                point -> TransportRuntime.safeCarryStanding(guard, point), guard.position()) == null) {
            guard.getNavigation().stop(); return noPath("native installation changed screened route");
        }
        ownedPath = installed; return true;
    }
}
