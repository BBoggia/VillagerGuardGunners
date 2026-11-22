package com.gamergaming.villagerguardgunners.api;

/**
 * Entity-owned reference to a gun mod's per-guard controller; never stored in
 * a global entity map or NBT. Typed as {@link Object} so the guard itself
 * never names a gun mod's classes.
 */
public interface GuardShotControllerHolder {
    Object villagerGuardGunners$getShotController();

    void villagerGuardGunners$setShotController(Object controller);
}
