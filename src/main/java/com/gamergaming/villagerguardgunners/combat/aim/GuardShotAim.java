package com.gamergaming.villagerguardgunners.combat.aim;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable proposed aim, NOT permission to fire. Safety must approve this exact
 * geometry before its angles are passed to TaCZ. Native spread/velocity still apply.
 */
public record GuardShotAim(long gameTick, long shotIndex, Vec3 aimOrigin, Vec3 projectileOrigin,
                           Vec3 targetPoint, Vec3 direction, float yaw, float pitch) {}
