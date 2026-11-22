package com.gamergaming.villagerguardgunners.quartermaster.service;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import tallestegg.guardvillagers.entities.Guard;

/** Temporary MOVE owner below combat/orders and existing armory trips, above idle coverage. */
public final class QuartermasterServiceGoal extends Goal {
    private final Guard guard;
    private long nextPath, nextProgress;
    private double lastDistance;
    private int failures;
    private boolean owns;
    public QuartermasterServiceGoal(Guard guard) { this.guard = guard; setFlags(EnumSet.of(Flag.MOVE)); }
    @Override public boolean canUse() { var plan = QuartermasterServiceRuntime.plan(guard); return !guard.isNoAi() && plan != null && ServiceTransactions.valid(plan); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { owns = true; guard.getNavigation().stop(); nextPath = 0; failures = 0; nextProgress = guard.level().getGameTime() + 80; lastDistance = Double.POSITIVE_INFINITY; QuartermasterServiceRuntime.travelling(guard); }
    @Override public void stop() { if (owns) guard.getNavigation().stop(); owns = false; QuartermasterServiceRuntime.cancel(guard, ServiceStatus.RETRY); }
    @Override public void tick() {
        var plan = QuartermasterServiceRuntime.plan(guard); if (plan == null || !owns) return;
        var pos = plan.station().getBlockPos(); long now = guard.level().getGameTime();
        double distance = guard.distanceToSqr(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        if (distance <= 9) { guard.getNavigation().stop(); return; }
        if (now >= nextProgress) {
            if (lastDistance - distance < .25) { QuartermasterServiceRuntime.cancel(guard, ServiceStatus.PATH_BLOCKED); return; }
            lastDistance = distance; nextProgress = now + 80;
        }
        if (now < nextPath || !guard.getNavigation().isDone()) return;
        nextPath = now + 40;
        if (!pathRegionLoaded()) { QuartermasterServiceRuntime.cancel(guard, ServiceStatus.PATH_BLOCKED); return; }
        try {
        var path = guard.getNavigation().createPath(pos, 2);
        if (path == null || !guard.getNavigation().moveTo(path, .8)) {
            if (++failures >= 3) QuartermasterServiceRuntime.cancel(guard, ServiceStatus.PATH_BLOCKED);
        }
        } catch (RuntimeException exception) { QuartermasterServiceRuntime.cancel(guard, ServiceStatus.PATH_BLOCKED); }
    }
    private boolean pathRegionLoaded() {
        if (!(guard.level() instanceof ServerLevel level)) return false;
        double range = guard.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (!Double.isFinite(range) || range < 0 || range > 64) return false;
        int radius = (int) Math.ceil(range) + 16;
        var pos = guard.blockPosition();
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++)
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++) if (!level.getChunkSource().hasChunk(x, z)) return false;
        return true;
    }
}
