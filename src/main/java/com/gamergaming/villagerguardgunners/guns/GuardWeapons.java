package com.gamergaming.villagerguardgunners.guns;

import tallestegg.guardvillagers.common.entities.Guard;

/**
 * Guard-level weapon operations that do not depend on which gun mod is in use.
 *
 * <p>A guard can carry state from more than one provider, for example a
 * primary from one mod and a stowed sidearm from another. Operations that
 * settle or inspect that state therefore address every provider.</p>
 */
public final class GuardWeapons {
    private GuardWeapons() {
    }

    public static void onGuardCreated(Guard guard) {
        for (GunProvider provider : GunProviders.all()) {
            provider.onGuardCreated(guard);
        }
    }

    /** Cancels reloads and lowers the weapon for every provider. */
    public static void interrupt(Guard guard) {
        for (GunProvider provider : GunProviders.all()) {
            provider.interrupt(guard);
        }
    }

    /** {@link #interrupt} plus removal of queued follow-up actions. */
    public static void halt(Guard guard) {
        for (GunProvider provider : GunProviders.all()) {
            provider.interrupt(guard);
            provider.clearPendingActions(guard);
        }
    }

    /** True when the guard holds a gun and its provider has it raised to fire. */
    public static boolean isAimingHeldGun(Guard guard) {
        return GunProviders.find(guard.getMainHandItem())
                .map(provider -> provider.isAiming(guard))
                .orElse(false);
    }

    /** True when any provider is mid-action for the guard. */
    public static boolean isWeaponBusy(Guard guard) {
        for (GunProvider provider : GunProviders.all()) {
            if (provider.isWeaponBusy(guard)) {
                return true;
            }
        }
        return false;
    }
}
