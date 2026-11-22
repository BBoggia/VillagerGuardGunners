package com.gamergaming.villagerguardgunners.compat.pointblank.vpb;

import com.gamergaming.villagerguardgunners.compat.pointblank.PointBlankAccess;
import com.gamergaming.villagerguardgunners.compat.pointblank.PointBlankBootstrap;
import com.vicmatskiv.pointblank.feature.AccuracyFeature;
import com.vicmatskiv.pointblank.feature.SoundFeature;
import com.vicmatskiv.pointblank.item.AmmoItem;
import com.vicmatskiv.pointblank.item.FireMode;
import com.vicmatskiv.pointblank.item.FireModeInstance;
import com.vicmatskiv.pointblank.item.GunItem;
import com.vicmatskiv.pointblank.util.HitScan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Calls into one Point Blank build. This file is the template for the other
 * build's adapter, which the build generates by replacing the package names;
 * keep everything build-specific in here.
 */
public final class VpbAccess implements PointBlankAccess {
    @Override
    public boolean isGun(ItemStack stack) {
        return stack.getItem() instanceof GunItem;
    }

    @Override
    public boolean isAmmo(ItemStack stack) {
        return stack.getItem() instanceof AmmoItem;
    }

    @Override
    public void prepare(ItemStack gun) {
        GunItem.initStackForCrafting(gun);
    }

    /** The stack's selected fire mode, or the gun's first when the stack has never been prepared. */
    private static FireModeInstance fireMode(GunItem item, ItemStack gun) {
        FireModeInstance selected = GunItem.getFireModeInstance(gun);
        if (selected != null) {
            return selected;
        }
        List<FireModeInstance> modes = item.getMainFireModes();
        return modes == null || modes.isEmpty() ? null : modes.get(0);
    }

    @Override
    public Optional<Facts> facts(ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem item) || !item.isEnabled()) {
            return Optional.empty();
        }
        FireModeInstance mode = fireMode(item, gun);
        if (mode == null) {
            return Optional.empty();
        }
        boolean automatic = false;
        boolean singleOnly = true;
        for (FireModeInstance candidate : item.getMainFireModes()) {
            automatic |= candidate.getType() == FireMode.AUTOMATIC;
            singleOnly &= candidate.getType() == FireMode.SINGLE;
        }
        boolean launcher = item.getExplosion() != null || !mode.isUsingDefaultAmmoPool();
        for (AmmoItem ammo : item.getCompatibleAmmo()) {
            launcher |= ammo.isHasProjectile();
        }
        int rpm = mode.getRpm() != -1 ? mode.getRpm() : item.getRpm();
        int pellets = mode.getPelletCount();
        double inaccuracy = item.getInacuracy() + (pellets > 0 ? mode.getPelletSpread() : 0.0D);
        float accuracy = AccuracyFeature.getAccuracyModifier(gun);
        if (accuracy > 0.0F) {
            inaccuracy /= accuracy;
        }
        return Optional.of(new Facts(
                rpm,
                mode.getType() == FireMode.AUTOMATIC,
                automatic,
                singleOnly,
                item.getMaxAmmoCapacity(gun, mode),
                pellets,
                inaccuracy,
                mode.getMaxShootingDistance(),
                mode.getDamage(),
                reloadMillis(item, gun),
                launcher));
    }

    /** The mod's reload conditions may expect client state a guard does not have; then the time is unknown. */
    private static long reloadMillis(GunItem item, ItemStack gun) {
        try {
            long total = 0L;
            for (GunItem.ReloadPhase phase : GunItem.ReloadPhase.values()) {
                total += item.getReloadingCooldownTime(phase, null, null, gun);
            }
            return total;
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    @Override
    public int loaded(ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem item)) {
            return 0;
        }
        FireModeInstance mode = fireMode(item, gun);
        return mode == null ? 0 : GunItem.getAmmo(gun, mode);
    }

    @Override
    public void setLoaded(ItemStack gun, int rounds) {
        if (gun.getItem() instanceof GunItem item) {
            FireModeInstance mode = fireMode(item, gun);
            if (mode != null) {
                GunItem.setAmmo(gun, mode, rounds);
            }
        }
    }

    @Override
    public boolean acceptsAmmo(ItemStack gun, ItemStack candidate) {
        if (!(gun.getItem() instanceof GunItem item) || !(candidate.getItem() instanceof AmmoItem ammo)) {
            return false;
        }
        FireModeInstance mode = fireMode(item, gun);
        if (mode == null) {
            return false;
        }
        return mode.isUsingDefaultAmmoPool() ? item.getCompatibleAmmo().contains(ammo) : mode.getAmmo() == ammo;
    }

    @Override
    public ResourceLocation firstAmmoId(ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem item)) {
            return null;
        }
        List<AmmoItem> compatible = item.getCompatibleAmmo();
        // The first entry is the mod's creative ammo on most guns; prefer the gun's real caliber.
        AmmoItem chosen = compatible.isEmpty() ? null : compatible.get(compatible.size() - 1);
        return chosen == null ? null : BuiltInRegistries.ITEM.getKey(chosen);
    }

    @Override
    public boolean fireHitScan(Guard guard, ItemStack gun, Vec3 origin, Vec3 direction, double range,
            int pellets, double inaccuracy, long seed, Predicate<Entity> mayHit) {
        if (!(gun.getItem() instanceof GunItem item)) {
            return false;
        }
        // Guards never break blocks. Leaves and plants are passed through, as the mod does for players.
        List<BlockPos> brokenBlocks = new ArrayList<>();
        List<HitResult> hits = HitScan.getObjectsInCrosshair(guard, origin, direction, 0.0F, range, pellets,
                inaccuracy, seed, block -> false,
                block -> block instanceof BushBlock || block instanceof LeavesBlock, brokenBlocks);
        // The trace completed. Damage listeners and presentation run after discharge
        // and may throw even after changing the target's health.
        try {
            for (HitResult hit : hits) {
                if (hit instanceof EntityHitResult entityHit && mayHit.test(entityHit.getEntity())) {
                    item.hurtEntity(guard, entityHit, null, gun);
                }
            }
            playFireSound(guard, item, gun);
        } catch (RuntimeException failure) {
            PointBlankBootstrap.warnAfterShotOnce(failure);
        }
        return true;
    }

    private static void playFireSound(Guard guard, GunItem item, ItemStack gun) {
        SoundFeature.SoundDescriptor descriptor = SoundFeature.getFireSoundAndVolume(gun);
        SoundEvent sound = descriptor != null ? descriptor.soundSupplier().get() : item.getFireSound();
        float volume = descriptor != null ? descriptor.volume() : item.getFireSoundVolume();
        if (sound != null && volume > 0.0F) {
            guard.level().playSound(null, guard.getX(), guard.getY(), guard.getZ(), sound, SoundSource.NEUTRAL, volume, 1.0F);
        }
    }
}
