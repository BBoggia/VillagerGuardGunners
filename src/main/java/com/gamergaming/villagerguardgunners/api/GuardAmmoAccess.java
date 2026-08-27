package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import tallestegg.guardvillagers.entities.Guard;

public interface GuardAmmoAccess {
    GuardAmmoHandler villagerGuardGunners$getAmmoHandler();

    static GuardAmmoHandler getAmmoHandler(Guard guard) {
        return ((GuardAmmoAccess) guard).villagerGuardGunners$getAmmoHandler();
    }
}
