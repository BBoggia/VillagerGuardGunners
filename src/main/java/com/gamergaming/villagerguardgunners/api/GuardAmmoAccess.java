package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import tallestegg.guardvillagers.common.entities.Guard;

public interface GuardAmmoAccess {
    GuardAmmoHandler villagerGuardGunners$getAmmoHandler();
    IItemHandler villagerGuardGunners$getCombinedItemHandler();
    void villagerGuardGunners$invalidateInventory();
    void villagerGuardGunners$reviveInventory();

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
            IItemHandler handler = guard.getCapability(Capabilities.ItemHandler.ENTITY);
            return handler == null ? dedicatedAmmo : handler;
        } catch (RuntimeException exception) {
            return dedicatedAmmo;
        }
    }
}
