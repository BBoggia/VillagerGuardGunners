package com.gamergaming.villagerguardgunners.sidearm;

import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import tallestegg.guardvillagers.entities.Guard;

/** Package-private access to the complete transactional storage. */
final class GuardSidearmStorageAccess {
    private GuardSidearmStorageAccess() {
    }

    static GuardSidearmStorage get(Guard guard) {
        return ((GuardSidearmAccess) guard).villagerGuardGunners$getSidearmStorage(
                SidearmStorageAccessKey.internal());
    }
}
