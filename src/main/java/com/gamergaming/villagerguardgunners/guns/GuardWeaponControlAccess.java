package com.gamergaming.villagerguardgunners.guns;

import java.util.Optional;
import tallestegg.guardvillagers.common.entities.Guard;

/** Entity-owned reference to a guard's weapon switch; never stored in a global entity map or NBT. */
public interface GuardWeaponControlAccess {
    GuardWeaponControl villagerGuardGunners$getWeaponControl();

    void villagerGuardGunners$setWeaponControl(GuardWeaponControl control);

    /** Empty until the guard's combat goal has been created. */
    static Optional<GuardWeaponControl> control(Guard guard) {
        return Optional.ofNullable(((GuardWeaponControlAccess) guard).villagerGuardGunners$getWeaponControl());
    }
}
