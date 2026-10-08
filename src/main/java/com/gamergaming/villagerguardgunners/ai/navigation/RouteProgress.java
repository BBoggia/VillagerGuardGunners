package com.gamergaming.villagerguardgunners.ai.navigation;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Monotonic progress along one installed route; sampling is constant time. */
public final class RouteProgress {
    public static final int MAX_POINTS = 4096;
    public static final int OBSTRUCTION_GRACE_TICKS = 60;
    private List<Vec3> points = List.of();
    private double[] suffix = new double[0];
    private double bestRemaining;
    private int furthestNode;
    private long lastProgress;
    private boolean graceUsed;
    private Status status = Status.IDLE;

    public enum Status { IDLE, MOVING, WAITING_FOR_OBSTRUCTION, WAITING_TO_LAND, STALLED }

    public void start(List<Vec3> points, int node, Vec3 position, long now) {
        if (points.isEmpty() || points.size() > MAX_POINTS) throw new IllegalArgumentException("Invalid route size");
        this.points = List.copyOf(points);
        suffix = new double[points.size()];
        for (int i = points.size() - 2; i >= 0; i--)
            suffix[i] = suffix[i + 1] + points.get(i).distanceTo(points.get(i + 1));
        furthestNode = Math.max(0, Math.min(node, points.size()));
        bestRemaining = remaining(node, position);
        lastProgress = now;
        graceUsed = false;
        status = Status.MOVING;
    }

    public Status sample(int node, Vec3 position, long now, int timeout, boolean obstructed, boolean airborne) {
        if (points.isEmpty()) return status = Status.IDLE;
        double remaining = remaining(node, position);
        if (node > furthestNode || remaining < bestRemaining - .25) {
            furthestNode = Math.max(furthestNode, node);
            bestRemaining = Math.min(bestRemaining, remaining);
            lastProgress = now;
            graceUsed = false;
        }
        long stalledFor = Math.max(0, now - lastProgress);
        // Grace never advances the progress clock. A permanent crowd/airborne state
        // therefore cannot keep a dead journey alive indefinitely.
        graceUsed |= obstructed || airborne;
        if (stalledFor >= timeout + (graceUsed ? OBSTRUCTION_GRACE_TICKS : 0))
            return status = Status.STALLED;
        if (stalledFor >= timeout && obstructed) return status = Status.WAITING_FOR_OBSTRUCTION;
        if (stalledFor >= timeout && airborne) return status = Status.WAITING_TO_LAND;
        return status = Status.MOVING;
    }

    public double remaining(int node, Vec3 position) {
        if (node >= points.size()) return 0;
        int index = Math.max(0, node);
        return points.isEmpty() ? Double.POSITIVE_INFINITY : suffix[index] + position.distanceTo(points.get(index));
    }
    public long lastProgressTick() { return lastProgress; }
    public Status status() { return status; }
    public void clear() { points = List.of(); suffix = new double[0]; status = Status.IDLE; graceUsed = false; }
}
