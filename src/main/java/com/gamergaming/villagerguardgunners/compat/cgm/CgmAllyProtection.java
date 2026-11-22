package com.gamergaming.villagerguardgunners.compat.cgm;

import com.gamergaming.villagerguardgunners.combat.GuardFriendlyFireSafety;
import com.mrcrayfish.guns.event.GunProjectileHitEvent;
import com.mrcrayfish.guns.entity.ProjectileEntity;
import com.mrcrayfish.guns.world.ProjectileExplosion;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraft.world.phys.EntityHitResult;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Last line of ally protection for a guard's projectile. The pre-fire check
 * covers the lane at the moment of firing; this stops a hit on anyone the
 * guard protects who steps into a projectile already in flight. The projectile
 * passes through and keeps going.
 */
final class CgmAllyProtection {
    private CgmAllyProtection() {
    }

    static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide
                || !(event.getExplosion() instanceof ProjectileExplosion)
                || !(event.getExplosion().getDirectSourceEntity() instanceof ProjectileEntity projectile)
                || !(projectile.getShooter() instanceof Guard guard)) {
            return;
        }
        // CGM projectiles extend Entity, so vanilla's indirect-source lookup cannot find their shooter.
        event.getAffectedEntities().removeIf(entity -> GuardFriendlyFireSafety.isProtected(guard, entity));
        if (ModConfigs.GUARD_GUNNERS.safety.preserveBlocksFromGuardExplosions.get()) {
            event.getAffectedBlocks().clear();
        }
    }

    static void onProjectileHit(GunProjectileHitEvent event) {
        if (event.getProjectile().getShooter() instanceof Guard guard
                && event.getRayTrace() instanceof EntityHitResult hit
                && GuardFriendlyFireSafety.isProtected(guard, hit.getEntity())) {
            event.setCanceled(true);
        }
    }
}
