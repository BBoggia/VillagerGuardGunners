package com.gamergaming.villagerguardgunners.api;

import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

public interface ISteeringBehavior {
    Vec3 calculateForce();
}