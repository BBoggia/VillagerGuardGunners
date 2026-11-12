package com.gamergaming.villagerguardgunners.compat.cgm;

import com.gamergaming.villagerguardgunners.combat.GuardShotEnvelope;
import com.gamergaming.villagerguardgunners.combat.aim.GuardShotAim;
import com.gamergaming.villagerguardgunners.guns.managed.ManagedGunBackend;
import com.gamergaming.villagerguardgunners.guns.managed.ManagedGunStats;
import com.mrcrayfish.framework.api.network.LevelLocation;
import com.mrcrayfish.guns.Config;
import com.mrcrayfish.guns.common.Gun;
import com.mrcrayfish.guns.common.ProjectileManager;
import com.mrcrayfish.guns.entity.ProjectileEntity;
import com.mrcrayfish.guns.init.ModItems;
import com.mrcrayfish.guns.interfaces.IProjectileFactory;
import com.mrcrayfish.guns.item.GunItem;
import com.mrcrayfish.guns.network.PacketHandler;
import com.mrcrayfish.guns.network.message.S2CMessageBulletTrail;
import com.mrcrayfish.guns.network.message.S2CMessageGunSound;
import com.mrcrayfish.guns.util.GunEnchantmentHelper;
import com.mrcrayfish.guns.util.GunItemData;
import com.mrcrayfish.guns.util.GunModifierHelper;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Reads and fires MrCrayfish's Gun Mod guns for a guard.
 *
 * <p>The mod's own shoot handler only accepts a player, but its projectile
 * factories accept any living shooter and credit damage to it. A round is
 * discharged the way that handler does it: one projectile per pellet aimed by
 * the shooter's rotation, then the trail and sound packets.</p>
 */
final class CgmGunBackend implements ManagedGunBackend {
    private static final double BASE_GRAVITY = -0.04D;
    private static final String AMMO_COUNT = "AmmoCount";
    private static final String IGNORE_AMMO = "IgnoreAmmo";

    /** The gun as modified by the stack's attachments, or null when the stack is not one of the mod's guns. */
    static Gun modifiedGun(ItemStack stack) {
        return stack.getItem() instanceof GunItem item ? item.getModifiedGun(stack) : null;
    }

    static boolean isExplosiveAmmo(ResourceLocation ammoId) {
        return ammoId != null
                && (ammoId.equals(ModItems.MISSILE.getId()) || ammoId.equals(ModItems.GRENADE.getId()));
    }

    static String gunType(Gun gun) {
        Gun.General general = gun.getGeneral();
        ResourceLocation grip = general.getGripType() == null ? null : general.getGripType().getId();
        return CgmGunTraits.inferType(
                grip == null ? "" : grip.getPath(),
                general.isAuto(),
                general.getRate(),
                Math.max(1, general.getProjectileAmount()),
                isExplosiveAmmo(gun.getProjectile().getItem()));
    }

    @Override
    public Optional<ManagedGunStats> stats(ItemStack stack) {
        Gun gun = modifiedGun(stack);
        if (gun == null) {
            return Optional.empty();
        }
        // With this setting the mod casts every shooter to a player when a
        // projectile passes a player. A guard's projectile would crash the server.
        if (Config.COMMON.gameplay.improvedHitboxes.get()) {
            CgmBootstrap.warnImprovedHitboxesOnce();
            return Optional.empty();
        }

        Gun.General general = gun.getGeneral();
        Gun.Projectile projectile = gun.getProjectile();
        int rateTicks = GunModifierHelper.getModifiedRate(stack, GunEnchantmentHelper.getRate(stack, gun));
        double speed = GunModifierHelper.getModifiedProjectileSpeed(
                stack, projectile.getSpeed() * GunEnchantmentHelper.getProjectileSpeedModifier(stack));
        int life = GunModifierHelper.getModifiedProjectileLife(stack, projectile.getLife());
        double gravity = projectile.isGravity()
                ? Math.abs(GunModifierHelper.getModifiedProjectileGravity(stack, BASE_GRAVITY))
                : 0.0D;
        float spread = GunModifierHelper.getModifiedSpread(stack, general.getSpread());

        ResourceLocation ammoId = projectile.getItem();
        boolean explosive = isExplosiveAmmo(ammoId);
        double explosionRadius = !explosive ? 0.0D
                : ammoId.equals(ModItems.MISSILE.getId())
                        ? Config.COMMON.missiles.explosionRadius.get()
                        // A grenade's blast grows with its damage; see the mod's GrenadeEntity.
                        : GunModifierHelper.getModifiedProjectileDamage(stack, projectile.getDamage()) / 5.0D;

        GuardShotEnvelope.Ballistics ballistics = new GuardShotEnvelope.Ballistics(
                speed,
                CgmGunTraits.safetyLifeTicks(speed, life),
                gravity,
                0.0D,
                CgmGunTraits.safetyInaccuracy(spread),
                Vec3.ZERO,
                // A vanilla-style explosion damages out to twice its radius.
                explosionRadius * 2.0D,
                Integer.MAX_VALUE);
        return Optional.of(new ManagedGunStats(
                CgmGunTraits.fireIntervalMillis(rateTicks),
                general.isAuto(),
                GunEnchantmentHelper.getAmmoCapacity(stack, gun),
                GunEnchantmentHelper.getReloadInterval(stack),
                Math.max(1, general.getReloadAmount()),
                Math.max(1, general.getProjectileAmount()),
                CgmGunTraits.effectiveRange(speed, life),
                explosive,
                explosionRadius,
                ballistics,
                false));
    }

    @Override
    public int loaded(ItemStack stack) {
        CompoundTag tag = GunItemData.getTag(stack);
        if (tag.getBoolean(IGNORE_AMMO)) {
            Gun gun = modifiedGun(stack);
            return gun == null ? 0 : GunEnchantmentHelper.getAmmoCapacity(stack, gun);
        }
        return Math.max(0, tag.getInt(AMMO_COUNT));
    }

    @Override
    public void setLoaded(ItemStack stack, int rounds) {
        CompoundTag tag = GunItemData.getTag(stack);
        if (tag.getBoolean(IGNORE_AMMO)) {
            return;
        }
        tag.putInt(AMMO_COUNT, Math.max(0, rounds));
        GunItemData.setTag(stack, tag);
    }

    @Override
    public Outcome fire(Guard guard, ItemStack stack, GuardShotAim aim, Predicate<Entity> mayHit) {
        if (!(guard.level() instanceof ServerLevel level) || !(stack.getItem() instanceof GunItem item)) {
            return Outcome.FAILED;
        }
        Gun gun = item.getModifiedGun(stack);
        if (gun == null) {
            return Outcome.FAILED;
        }
        Gun.Projectile projectile = gun.getProjectile();
        int count = Math.max(1, gun.getGeneral().getProjectileAmount());
        ProjectileEntity[] spawned = new ProjectileEntity[count];

        // The mod aims a projectile by its shooter's rotation, and applies the gun's own spread around it.
        float bodyYaw = guard.getYRot();
        float headYaw = guard.getYHeadRot();
        float pitch = guard.getXRot();
        guard.setYRot(aim.yaw());
        guard.setYHeadRot(aim.yaw());
        guard.setXRot(aim.pitch());
        try {
            IProjectileFactory factory = ProjectileManager.getInstance().getFactory(projectile.getItem());
            for (int index = 0; index < count; index++) {
                ProjectileEntity entity = factory.create(level, guard, stack, item, gun);
                entity.setWeapon(stack);
                entity.setAdditionalDamage(Gun.getAdditionalDamage(stack));
                level.addFreshEntity(entity);
                spawned[index] = entity;
                entity.tick();
            }
        } finally {
            guard.setYRot(bodyYaw);
            guard.setYHeadRot(headYaw);
            guard.setXRot(pitch);
        }

        if (!projectile.isVisible()) {
            double range = Config.COMMON.network.projectileTrackingRange.get();
            S2CMessageBulletTrail trail = new S2CMessageBulletTrail(
                    spawned, projectile, guard.getId(), GunEnchantmentHelper.getParticle(stack));
            PacketHandler.getPlayChannel().sendToNearbyPlayers(
                    () -> LevelLocation.create(level, guard.getX(), guard.getY() + 1.0D, guard.getZ(), range), trail);
        }
        level.gameEvent(GameEvent.PROJECTILE_SHOOT, guard.position(), GameEvent.Context.of(guard));
        playFireSound(guard, level, stack, gun);
        return Outcome.FIRED;
    }

    private static void playFireSound(Guard guard, ServerLevel level, ItemStack stack, Gun gun) {
        ResourceLocation sound = null;
        if (GunModifierHelper.isSilencedFire(stack)) {
            sound = gun.getSounds().getSilencedFire();
        } else if (stack.isEnchanted()) {
            sound = gun.getSounds().getEnchantedFire();
        }
        if (sound == null) {
            sound = gun.getSounds().getFire();
        }
        if (sound == null) {
            return;
        }
        double x = guard.getX();
        double y = guard.getY() + guard.getEyeHeight();
        double z = guard.getZ();
        double range = GunModifierHelper.getModifiedFireSoundRadius(stack, Config.SERVER.gunShotMaxDistance.get());
        S2CMessageGunSound message = new S2CMessageGunSound(
                sound, SoundSource.NEUTRAL, (float) x, (float) y, (float) z,
                GunModifierHelper.getFireSoundVolume(stack),
                0.9F + level.random.nextFloat() * 0.2F,
                guard.getId(),
                gun.getDisplay().getFlash() != null,
                false);
        PacketHandler.getPlayChannel().sendToNearbyPlayers(() -> LevelLocation.create(level, x, y, z, range), message);
    }

    /** Registry identifier of a stack's item. */
    static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
