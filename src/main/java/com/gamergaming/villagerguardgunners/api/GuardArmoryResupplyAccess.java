package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.armory.resupply.GuardArmoryResupplyController;
import javax.annotation.Nullable;
import tallestegg.guardvillagers.entities.Guard;

/** Mixin-backed access to one guard's transient Armory runtime controller. */
public interface GuardArmoryResupplyAccess {
    @Nullable
    GuardArmoryResupplyController villagerGuardGunners$getArmoryResupplyController();

    @Nullable
    static GuardArmoryResupplyController getController(Guard guard) {
        return guard instanceof GuardArmoryResupplyAccess access
                ? access.villagerGuardGunners$getArmoryResupplyController()
                : null;
    }

    static void cancel(Guard guard) {
        GuardArmoryResupplyController controller = getController(guard);
        if (controller != null) {
            controller.discard();
        }
    }
}
