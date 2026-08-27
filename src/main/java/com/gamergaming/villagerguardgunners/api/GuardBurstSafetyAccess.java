package com.gamergaming.villagerguardgunners.api;

import java.util.function.BooleanSupplier;
import tallestegg.guardvillagers.entities.Guard;

/** Server-owned state consulted by TaCZ's cancellable per-round fire event. */
public interface GuardBurstSafetyAccess {
    boolean villagerGuardGunners$isNativeBurstSuppressed();

    void villagerGuardGunners$setNativeBurstSuppressed(boolean suppressed);

    boolean villagerGuardGunners$isNativeBurstRoundSafe();

    void villagerGuardGunners$setNativeBurstSafetyCheck(BooleanSupplier safetyCheck);

    void villagerGuardGunners$clearNativeBurstSafetyCheck();

    static boolean isSuppressed(Guard guard) {
        return ((GuardBurstSafetyAccess) guard).villagerGuardGunners$isNativeBurstSuppressed();
    }

    static void setSuppressed(Guard guard, boolean suppressed) {
        ((GuardBurstSafetyAccess) guard).villagerGuardGunners$setNativeBurstSuppressed(suppressed);
    }

    static void setSafetyCheck(Guard guard, BooleanSupplier safetyCheck) {
        ((GuardBurstSafetyAccess) guard).villagerGuardGunners$setNativeBurstSafetyCheck(safetyCheck);
    }

    static void clearSafetyCheck(Guard guard) {
        ((GuardBurstSafetyAccess) guard).villagerGuardGunners$clearNativeBurstSafetyCheck();
    }

    static boolean shouldSuppress(Guard guard) {
        GuardBurstSafetyAccess access = (GuardBurstSafetyAccess) guard;
        if (access.villagerGuardGunners$isNativeBurstSuppressed()) {
            return true;
        }
        try {
            return !access.villagerGuardGunners$isNativeBurstRoundSafe();
        } catch (RuntimeException exception) {
            // A stale goal reference or malformed third-party callback must fail
            // closed inside TaCZ's cancellable pre-fire event.
            return true;
        }
    }
}
