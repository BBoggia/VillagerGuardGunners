package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardInfiniteAmmo;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.common.entities.Guard;

/** Ammo demand follows each weapon's actual compatible supplies, independent of the box issuance mode. */
final class ServiceAmmoDemand {
    private ServiceAmmoDemand() {}

    static boolean hasCreativeReserve(ItemStack gun, List<ItemStack> inventory) {
        for (int slot = 6; slot < 9; slot++) {
            var ammo = AmmoCompatibility.assess(gun, inventory.get(slot));
            if (ammo.isCompatible() && ammo.infinite()) return true;
        }
        return false;
    }

    /** Rechecked for both pending previews and the final transfer; medical/equipment slots bypass this. */
    static boolean deliveryNeeded(Guard guard, ItemStack ammo) {
        if (GuardInfiniteAmmo.virtualReserve(guard)) return false;
        var inventory = ServiceEquipment.snapshot(guard);
        for (int weapon = 0; weapon < 2; weapon++) {
            var gun = inventory.get(weapon);
            if (AmmoCompatibility.assess(gun, ammo).isCompatible() && !hasCreativeReserve(gun, inventory)) {
                return true;
            }
        }
        return false;
    }
}
