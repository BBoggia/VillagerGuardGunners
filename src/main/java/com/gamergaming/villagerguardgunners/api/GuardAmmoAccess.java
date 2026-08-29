package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import tallestegg.guardvillagers.entities.Guard;

public interface GuardAmmoAccess {
    GuardAmmoHandler villagerGuardGunners$getAmmoHandler();

    static GuardAmmoHandler getAmmoHandler(Guard guard) {
        return ((GuardAmmoAccess) guard).villagerGuardGunners$getAmmoHandler();
    }

    /**
     * Returns the same combined equipment-and-ammunition view exposed to TaCZ.
     * Falling back to the dedicated slots keeps inspection fail-soft if the
     * entity capability is temporarily unavailable during teardown.
     */
    static IItemHandler getTaCZInventoryHandler(Guard guard) {
        GuardAmmoHandler dedicatedAmmo = getAmmoHandler(guard);
        try {
            return guard.getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .resolve()
                    .orElse(dedicatedAmmo);
        } catch (RuntimeException exception) {
            return dedicatedAmmo;
        }
    }
}
