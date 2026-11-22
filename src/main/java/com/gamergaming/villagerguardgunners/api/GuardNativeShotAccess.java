package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.combat.NativeGunController;
import tallestegg.guardvillagers.entities.Guard;

/** TaCZ-side view of the guard's shot controller. Only TaCZ code may use this class. */
public final class GuardNativeShotAccess {
    private GuardNativeShotAccess() {
    }

    public static NativeGunController controller(Guard guard) {
        return ((GuardShotControllerHolder) guard).villagerGuardGunners$getShotController()
                instanceof NativeGunController controller ? controller : null;
    }

    public static void bind(Guard guard, NativeGunController controller) {
        ((GuardShotControllerHolder) guard).villagerGuardGunners$setShotController(controller);
    }
}
