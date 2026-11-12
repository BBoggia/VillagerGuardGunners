package com.gamergaming.villagerguardgunners.compat.cgm;

import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.mrcrayfish.guns.event.GunProjectileHitEvent;
import net.minecraft.world.phys.EntityHitResult;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Last line of ally protection for a guard's projectile. The pre-fire check
 * covers the lane at the moment of firing; this stops a hit on anyone the
 * guard protects who steps into a projectile already in flight. The projectile
 * passes through and keeps going.
 */
final class CgmAllyProtection {
    private CgmAllyProtection() {
    }

    static void onProjectileHit(GunProjectileHitEvent event) {
        if (event.getProjectile().getShooter() instanceof Guard guard
                && event.getRayTrace() instanceof EntityHitResult hit
                && GuardFriendlyFireSafety.isProtected(guard, hit.getEntity())) {
            event.setCanceled(true);
        }
    }
}
