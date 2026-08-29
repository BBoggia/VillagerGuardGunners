package com.gamergaming.villagerguardgunners.combat;

import com.gamergaming.villagerguardgunners.VillagerGuardGunners;
import com.gamergaming.villagerguardgunners.api.GuardBurstSafetyAccess;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.util.block.ProjectileExplosion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import tallestegg.guardvillagers.entities.Guard;

/** Final server-side backstops for shots that reach a protected entity. */
@Mod.EventBusSubscriber(modid = VillagerGuardGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuardFriendlyFireEvents {
    private GuardFriendlyFireEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGuardGunFire(GunFireEvent event) {
        if (event.getLogicalSide().isServer()
                && event.getShooter() instanceof Guard guard
                && GuardBurstSafetyAccess.shouldSuppress(guard)) {
            // TaCZ posts this event for every CycleTask burst round before it
            // consumes ammo, applies heat, creates a projectile, or plays sound.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityHurtByGun(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }

        Guard shooter = resolveGuardShooter(event.getAttacker(), event.getBullet());
        if (shooter != null && GuardFriendlyFireSafety.isProtected(shooter, event.getHurtEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide
                || !(event.getExplosion() instanceof ProjectileExplosion)
                || !(event.getExplosion().getIndirectSourceEntity() instanceof Guard shooter)) {
            return;
        }

        event.getAffectedEntities().removeIf(entity -> GuardFriendlyFireSafety.isProtected(shooter, entity));
        if (ModConfigs.GUARD_GUNNERS.safety.preserveBlocksFromGuardExplosions.get()) {
            // Forge exposes TaCZ's already-computed mutable block list here.
            // Clearing it retains the native explosion, hostile entity effects,
            // particles, sound, and knockback without an extra world query.
            event.getAffectedBlocks().clear();
        }
    }

    private static Guard resolveGuardShooter(Entity attacker, Entity bullet) {
        if (attacker instanceof Guard guard) {
            return guard;
        }
        if (bullet instanceof Projectile projectile && projectile.getOwner() instanceof Guard guard) {
            return guard;
        }
        return null;
    }
}
