package com.gamergaming.villagerguardgunners.combat;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSharing;
import com.gamergaming.villagerguardgunners.combat.tactics.CachedTacticalState;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * A gun controller's state for an administrator inspection, in plain types so
 * the command and the combat goal never name a gun mod's classes.
 */
public record WeaponControllerDiagnostics(
            @Nullable ResourceLocation gunId,
            String fireMode,
            boolean readableGunState,
            boolean controllerBound,
            boolean weaponDataReady,
            boolean suspended,
            WeaponReadiness readiness,
            boolean dryConfirmed,
            long dryRetryTicks,
            int loadedAmmo,
            boolean chambered,
            boolean inventoryFed,
            int reserveItems,
            int occupiedReserveSlots,
            String reloadState,
            boolean reloading,
            long shootCooldownMillis,
            long drawCooldownMillis,
            boolean bolting,
            boolean aiming,
            float chargeProgress,
            @Nullable String lastShootResult,
            GuardAmmoSharing.SharingStatus lastAmmoSharingStatus,
            int lastAmmoSharedItems,
            CachedTacticalState tacticalState) {

    /** What is reported for a guard that has no such controller, for example when its gun mod is not installed. */
    public static WeaponControllerDiagnostics unavailable(CachedTacticalState tacticalState) {
        return new WeaponControllerDiagnostics(null, "UNAVAILABLE", false, false, false, false,
                WeaponReadiness.UNBOUND, false, 0L, -1, false, false, 0, 0, "unavailable", false,
                -1L, -1L, false, false, 0.0F, null,
                GuardAmmoSharing.SharingStatus.NOT_ATTEMPTED, 0, tacticalState);
    }
}
