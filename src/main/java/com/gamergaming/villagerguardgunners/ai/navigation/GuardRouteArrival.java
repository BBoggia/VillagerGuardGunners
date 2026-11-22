package com.gamergaming.villagerguardgunners.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Proximity is arrival only on the actual floor and across a clear final approach. */
public final class GuardRouteArrival {
    private GuardRouteArrival() { }
    public static boolean at(Guard guard, BlockPos target, double horizontal, double vertical) {
        if (!(guard.level() instanceof ServerLevel level)) return false;
        // Resolving the floor preserves X/Z; distant guards cannot arrive on any floor.
        double dx = target.getX() + .5 - guard.getX(), dz = target.getZ() + .5 - guard.getZ();
        if (dx * dx + dz * dz > horizontal * horizontal) return false;
        Vec3 floor = GuardGroundTraversal.surface(level, Vec3.atBottomCenterOf(target));
        if (floor == null || Math.abs(floor.y - guard.getY()) > Math.min(.55, vertical)) return false;
        int samples = Math.max(1, (int) Math.ceil(guard.position().distanceTo(floor) * 4));
        for (int i = 0; i <= samples; i++) {
            Vec3 point = guard.position().lerp(floor, (double) i / samples);
            if (!GuardGroundTraversal.safeStanding(guard, point)) return false;
        }
        return true;
    }
}
