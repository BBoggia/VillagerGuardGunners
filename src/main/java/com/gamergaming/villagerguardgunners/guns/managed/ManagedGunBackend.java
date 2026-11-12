package com.gamergaming.villagerguardgunners.guns.managed;

import com.gamergaming.villagerguardgunners.combat.aim.GuardShotAim;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.common.entities.Guard;

/**
 * The gun-mod-specific half of {@link ManagedWeaponDriver}, for mods that have
 * no way for a non-player to operate a gun.
 *
 * <p>The driver owns timing, reloading, ammo, aim, and shot safety. A backend
 * only reads a gun's numbers, reads and writes its magazine, and discharges one
 * round. Every stack passed in is a gun owned by the backend's provider.</p>
 */
public interface ManagedGunBackend {
    /** Empty when the gun's definition cannot be read. May throw for malformed data. */
    Optional<ManagedGunStats> stats(ItemStack gun);

    /** Rounds currently in the magazine. */
    int loaded(ItemStack gun);

    void setLoaded(ItemStack gun, int rounds);

    /**
     * One-time setup when a guard takes the gun in hand, for mods that
     * otherwise initialize a stack from a player's inventory tick.
     */
    default void prepare(ItemStack gun) {
    }

    /**
     * Discharges one round along an aim the driver has already checked:
     * projectiles or hit scan, sound, and effects. Must not change the
     * magazine; the driver spends the round after {@link Outcome#FIRED}.
     *
     * @param mayHit false for entities this guard must never damage. Hit-scan
     *        backends filter their hits through it.
     */
    Outcome fire(Guard guard, ItemStack gun, GuardShotAim aim, Predicate<Entity> mayHit);

    enum Outcome {
        FIRED,
        /** Nothing left the gun. The driver waits briefly and tries again. */
        FAILED
    }
}
