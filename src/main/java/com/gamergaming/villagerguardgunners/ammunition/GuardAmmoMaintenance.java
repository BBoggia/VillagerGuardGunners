package com.gamergaming.villagerguardgunners.ammunition;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.guns.GunProvider;
import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Event-driven maintenance for the guard's small dedicated ammunition
 * inventory. TaCZ preserves a depleted finite ammo box as an empty item, so
 * automated restocking must make room without deleting that reusable box.
 */
public final class GuardAmmoMaintenance {
    private GuardAmmoMaintenance() {
    }

    /**
     * A deliberately loose item-only filter for the dropped-item mixin. It
     * excludes depleted finite boxes before they can schedule guard searches,
     * while preserving TaCZ's loose-ammo precedence for unusual items that
     * implement both ammunition interfaces.
     */
    public static boolean isPotentialSupply(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (GunProvider provider : GunProviders.all()) {
            if (provider.isUsableSupply(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ejects depleted finite TaCZ ammo boxes intact beside the guard. This is
     * called only from reload/restocking events; it never adds a guard tick
     * scan. A failed spawn restores the original slot instead of losing it.
     */
    public static int ejectEmptyFiniteBoxes(Guard guard) {
        if (guard == null
                || guard.level().isClientSide
                || com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard) || !guard.isAlive()) {
            return 0;
        }

        final GuardAmmoHandler handler;
        try {
            handler = GuardAmmoAccess.getAmmoHandler(guard);
        } catch (RuntimeException exception) {
            return 0;
        }

        int ejected = 0;
        int slots = Math.min(GuardAmmoHandler.SLOT_COUNT, handler.getSlots());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack original;
            try {
                original = handler.getStackInSlot(slot).copy();
            } catch (RuntimeException exception) {
                continue;
            }
            if (!isEmptyFiniteBox(original)) {
                continue;
            }

            try {
                ItemStack extracted = handler.extractItem(slot, original.getCount(), false);
                if (extracted.isEmpty()
                        || extracted.getCount() != original.getCount()
                        || !ItemStack.isSameItemSameTags(original, extracted)) {
                    handler.setStackInSlot(slot, original);
                    continue;
                }

                ItemEntity droppedBox = guard.spawnAtLocation(extracted.copy());
                // spawnAtLocation returns an entity even if Forge cancels
                // its world insertion. Drop capture is a valid handoff too.
                if (droppedBox == null
                        || (!droppedBox.isAddedToWorld()
                                && (guard.captureDrops() == null
                                        || !guard.captureDrops().contains(droppedBox)))) {
                    handler.setStackInSlot(slot, original);
                    continue;
                }
                ejected += extracted.getCount();
            } catch (RuntimeException exception) {
                // GuardAmmoHandler is owned by this add-on, so restoring its
                // exact pre-operation snapshot is safe and lossless.
                handler.setStackInSlot(slot, original);
            }
        }
        return ejected;
    }

    static boolean isEmptyFiniteBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (GunProvider provider : GunProviders.all()) {
            if (provider.isSpentAmmoContainer(stack)) {
                return true;
            }
        }
        return false;
    }
}
