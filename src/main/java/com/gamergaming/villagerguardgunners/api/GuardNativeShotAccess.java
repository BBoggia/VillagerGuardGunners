package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import tallestegg.guardvillagers.entities.Guard;

/** Entity-owned controller reference; never stored in a global entity map or NBT. */
public interface GuardNativeShotAccess {
    NativeGunController villagerGuardGunners$getShotController();
    void villagerGuardGunners$setShotController(NativeGunController controller);

    static NativeGunController controller(Guard guard) {
        return ((GuardNativeShotAccess) guard).villagerGuardGunners$getShotController();
    }
}
