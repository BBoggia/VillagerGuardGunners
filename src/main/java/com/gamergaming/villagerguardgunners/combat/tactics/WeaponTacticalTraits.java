package com.gamergaming.villagerguardgunners.combat.tactics;

/**
 * Stable structural traits read from a loaded TaCZ gun definition.
 *
 * <p>These traits supplement the pack-authored gun type. They are also kept
 * separate from profile selection so an explicit profile override cannot
 * disable an explosive safety rule.</p>
 */
public record WeaponTacticalTraits(
        boolean explosive,
        int projectileCount,
        double explosionRadius) {
    public static final int MAX_PROJECTILE_COUNT = 1_024;

    public static final WeaponTacticalTraits NONE = new WeaponTacticalTraits(false, 1, 0.0D);

    public WeaponTacticalTraits {
        projectileCount = Math.max(1, Math.min(projectileCount, MAX_PROJECTILE_COUNT));
        explosionRadius = Double.isFinite(explosionRadius) && explosionRadius > 0.0D
                ? explosionRadius
                : 0.0D;
        explosive = explosive || explosionRadius > 0.0D;
    }

    public boolean multiProjectile() {
        return projectileCount > 1;
    }
}
