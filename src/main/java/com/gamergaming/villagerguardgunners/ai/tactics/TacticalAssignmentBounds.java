package com.gamergaming.villagerguardgunners.ai.tactics;

import net.minecraft.world.phys.Vec3;

/** Transient assignment constraint applied to every route sample, not just its endpoint. */
public record TacticalAssignmentBounds(Vec3 center, double radius) {
    public static final TacticalAssignmentBounds UNBOUNDED = new TacticalAssignmentBounds(Vec3.ZERO, Double.POSITIVE_INFINITY);

    public boolean contains(Vec3 position) {
        double x = position.x - center.x;
        double z = position.z - center.z;
        return x * x + z * z <= radius * radius;
    }
}
