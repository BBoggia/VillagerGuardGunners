package com.gamergaming.villagerguardgunners.guns.managed;

import com.gamergaming.villagerguardgunners.guns.GuardWeaponControlAccess;
import java.util.Optional;
import tallestegg.guardvillagers.common.entities.Guard;

/** Finds the managed driver a provider created for a guard. */
public final class ManagedDrivers {
    private ManagedDrivers() {
    }

    /** Empty when the guard has never held one of that provider's guns. */
    public static Optional<ManagedWeaponDriver> find(Guard guard, String providerId) {
        return GuardWeaponControlAccess.control(guard)
                .flatMap(control -> control.driverFor(providerId))
                .filter(ManagedWeaponDriver.class::isInstance)
                .map(ManagedWeaponDriver.class::cast);
    }
}
