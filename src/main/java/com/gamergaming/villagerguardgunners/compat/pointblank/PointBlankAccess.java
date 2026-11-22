package com.gamergaming.villagerguardgunners.compat.pointblank;

import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Everything the provider needs from a Point Blank build, in plain types.
 *
 * <p>Vic's Point Blank and Point Blank: Jelly share one mod ID and the same
 * methods under different package names. Each has one implementation of this
 * interface; the Jelly one is generated from the other at build time.</p>
 */
public interface PointBlankAccess {
    boolean isGun(ItemStack stack);

    boolean isAmmo(ItemStack stack);

    /** Gives a fresh stack the identity and fire mode it would get in a player's inventory. */
    void prepare(ItemStack gun);

    /** Empty when the gun is disabled or its definition cannot be read. May throw for malformed data. */
    Optional<Facts> facts(ItemStack gun);

    int loaded(ItemStack gun);

    void setLoaded(ItemStack gun, int rounds);

    /** True when the gun's current fire mode loads this item. */
    boolean acceptsAmmo(ItemStack gun, ItemStack candidate);

    /** Identifier of the first ammo the gun's current fire mode loads, or null when it names none. */
    @Nullable
    ResourceLocation firstAmmoId(ItemStack gun);

    /**
     * One hit-scan round with the mod's own trace and damage, then its fire sound.
     *
     * @return false when nothing was fired
     */
    boolean fireHitScan(Guard guard, ItemStack gun, Vec3 origin, Vec3 direction, double range,
            int pellets, double inaccuracy, long seed, Predicate<Entity> mayHit);

    /**
     * A gun as the mod defines it, attachments applied.
     *
     * @param inaccuracy the mod's own value for a standing, unaimed shot, pellet spread included
     * @param reloadMillis total reload time, or zero when the mod could not say
     * @param launcher true for guns that fire a projectile entity or explode on impact.
     *        The mod's code for those assumes a player and guards cannot use them.
     */
    record Facts(int rpm, boolean automatic, boolean hasAutomaticMode, boolean singleOnly, int capacity, int pellets,
            double inaccuracy, int range, float damage, long reloadMillis, boolean launcher) {
    }
}
