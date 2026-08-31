package com.gamergaming.villagerguardgunners.sidearm;

import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import tallestegg.guardvillagers.entities.Guard;

/** Development-only fixture access to complete private sidearm state. */
public final class GuardSidearmGameTestAccess {
    private GuardSidearmGameTestAccess() {
    }

    public static GuardSidearmStorage getStorage(Guard guard) {
        return ((GuardSidearmAccess) guard).villagerGuardGunners$getSidearmStorage(
                SidearmStorageAccessKey.internal());
    }
}
