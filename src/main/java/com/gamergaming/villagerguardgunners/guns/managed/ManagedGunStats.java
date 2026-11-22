package com.gamergaming.villagerguardgunners.guns.managed;

import com.gamergaming.villagerguardgunners.combat.GuardShotEnvelope;
import com.gamergaming.villagerguardgunners.combat.tactics.WeaponTacticalTraits;
import java.util.Objects;

/**
 * What {@link ManagedWeaponDriver} needs to know about one gun, read from its
 * mod with attachments and enchantments already applied.
 *
 * @param fireIntervalMillis time between rounds while the trigger is held
 * @param automatic whether several rounds may leave within one server tick
 * @param reloadTicks duration of one reload step
 * @param roundsPerReloadStep rounds loaded per step; the magazine capacity for a whole-magazine reload
 * @param pellets projectiles per round
 * @param effectiveRange distance in blocks at which the gun is meant to be used
 * @param explosionRadius blast radius in blocks; zero unless {@code explosive}
 * @param ballistics flight of one projectile, including the gun's own spread.
 *        Its inherited velocity is zero unless {@code inheritsShooterMotion}.
 * @param inheritsShooterMotion whether the mod adds the shooter's motion to the projectile.
 *        The driver then supplies the guard's motion per shot.
 */
public record ManagedGunStats(
        int fireIntervalMillis,
        boolean automatic,
        int magazineCapacity,
        int reloadTicks,
        int roundsPerReloadStep,
        int pellets,
        float effectiveRange,
        boolean explosive,
        double explosionRadius,
        GuardShotEnvelope.Ballistics ballistics,
        boolean inheritsShooterMotion) {

    public ManagedGunStats {
        Objects.requireNonNull(ballistics, "ballistics");
        if (fireIntervalMillis < 1) {
            throw new IllegalArgumentException("Fire interval must be at least one millisecond");
        }
        if (magazineCapacity < 1) {
            throw new IllegalArgumentException("Magazine capacity must be at least one round");
        }
        if (reloadTicks < 0) {
            throw new IllegalArgumentException("Reload time cannot be negative");
        }
        if (roundsPerReloadStep < 1) {
            throw new IllegalArgumentException("A reload step must load at least one round");
        }
        if (pellets < 1 || pellets > WeaponTacticalTraits.MAX_PROJECTILE_COUNT) {
            throw new IllegalArgumentException("Invalid pellet count: " + pellets);
        }
        if (!Double.isFinite(explosionRadius) || explosionRadius < 0.0D) {
            throw new IllegalArgumentException("Invalid explosion radius: " + explosionRadius);
        }
    }
}
