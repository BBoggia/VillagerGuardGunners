package com.gamergaming.villagerguardgunners.ai.navigation;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Transient route measurements shared by navigation owners; it never moves an entity. */
public final class GuardRouteProgress {
    private static final Map<Guard, Diagnostic> DIAGNOSTICS = new WeakHashMap<>();
    private final RouteProgress progress = new RouteProgress();
    // The session retains only this token, never a controller or guard reference.
    private final Object ownership = new Object();
    private Path path;
    private Diagnostic diagnostic;
    private java.lang.ref.WeakReference<Guard> owner = new java.lang.ref.WeakReference<>(null);

    public void start(Guard guard, Path installed, long now) {
        if (owner.get() == guard && path == installed && installed == guard.getNavigation().getPath()
                && GuardNavigationRuntime.owns(guard, installed, ownership)) return;
        clear();
        if (installed == null || installed != guard.getNavigation().getPath()
                || installed.getNodeCount() == 0 || installed.getNodeCount() > RouteProgress.MAX_POINTS) return;
        owner = new java.lang.ref.WeakReference<>(guard);
        path = installed;
        GuardNavigationRuntime.register(guard, installed, ownership);
        var points = new ArrayList<Vec3>(installed.getNodeCount());
        for (int i = 0; i < installed.getNodeCount(); i++) {
            Vec3 point = Vec3.atBottomCenterOf(installed.getNodePos(i));
            Vec3 floor = guard.level() instanceof ServerLevel level ? GuardGroundTraversal.surface(level, point) : null;
            points.add(floor == null ? point : floor);
        }
        progress.start(points, installed.getNextNodeIndex(), guard.position(), now);
    }

    public boolean stalled(Guard guard, Path installed, long now, int timeout) {
        // Sampling is read-only with respect to ownership. A detached tracker
        // cannot reacquire a replacement owner's path on a later sample.
        if (owner.get() != guard || path != installed || !owns(guard, installed)
                || !GuardNavigationRuntime.owns(guard, installed, ownership)) { clear(); return true; }
        boolean delayed = now - progress.lastProgressTick() >= timeout;
        boolean obstructed = GuardNavigationRuntime.waiting(guard) || delayed && obstructed(guard, installed);
        var status = progress.sample(installed.getNextNodeIndex(), guard.position(), now, timeout,
                obstructed, !guard.onGround() && !guard.isInWater() && !guard.isInLava());
        if (diagnostic == null) {
            diagnostic = new Diagnostic(installed, ownership);
            DIAGNOSTICS.put(guard, diagnostic);
        }
        diagnostic.status = status.name();
        diagnostic.lastProgressTick = progress.lastProgressTick();
        diagnostic.nextNode = installed.getNextNodeIndex();
        diagnostic.remainingDistance = progress.remaining(diagnostic.nextNode, guard.position());
        return status == RouteProgress.Status.STALLED || status == RouteProgress.Status.IDLE;
    }

    /** Also permits cleanup of a newly installed path before progress.start, unless another owner retained it. */
    public boolean owns(Guard guard, Path installed) {
        return installed != null && installed == guard.getNavigation().getPath()
                && !GuardNavigationRuntime.ownedByOther(guard, installed, ownership);
    }

    private static boolean obstructed(Guard guard, Path path) {
        if (path.isDone()) return false;
        Vec3 next = path.getNextEntityPos(guard);
        Vec3 direction = next.subtract(guard.position());
        if (direction.lengthSqr() > 4) direction = direction.normalize().scale(2);
        var corridor = guard.getBoundingBox().expandTowards(direction).inflate(.1);
        var blockers = new ArrayList<LivingEntity>(8);
        guard.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class),
                corridor, entity -> entity != guard && entity.isAlive() && !entity.isSpectator(), blockers, 8);
        return !blockers.isEmpty();
    }
    public void clear() {
        Guard guard = owner.get();
        if (guard != null) {
            Diagnostic published = DIAGNOSTICS.get(guard);
            if (published != null && published.owner == ownership) DIAGNOSTICS.remove(guard);
            GuardNavigationRuntime.release(guard, path, ownership);
        }
        path = null; diagnostic = null; progress.clear(); owner.clear();
    }
    public static Snapshot snapshot(Guard guard) {
        if (!GuardNavigationRuntime.owns(guard)) return new Snapshot("IDLE", -1, 0, 0);
        Diagnostic diagnostic = DIAGNOSTICS.get(guard);
        return diagnostic == null || diagnostic.path != guard.getNavigation().getPath()
                || !GuardNavigationRuntime.owns(guard, diagnostic.path, diagnostic.owner)
                ? new Snapshot("IDLE", -1, 0, 0) : new Snapshot(diagnostic.status, diagnostic.lastProgressTick,
                        diagnostic.nextNode, diagnostic.remainingDistance);
    }
    // One mutable sample per route, with no reference back to the weak Guard key.
    // Sampling reuses it; inspection creates an immutable snapshot.
    private static final class Diagnostic {
        final Path path;
        final Object owner;
        String status;
        long lastProgressTick;
        int nextNode;
        double remainingDistance;
        Diagnostic(Path path, Object owner) { this.path = path; this.owner = owner; }
    }
    public record Snapshot(String status, long lastProgressTick, int nextNode, double remainingDistance) { }
}
