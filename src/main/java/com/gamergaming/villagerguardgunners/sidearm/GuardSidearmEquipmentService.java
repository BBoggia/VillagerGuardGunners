package com.gamergaming.villagerguardgunners.sidearm;

import com.gamergaming.villagerguardgunners.quartermaster.service.ServiceTransactions;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/** Trusted single-slot write, including exact rollback of an old configured item. */
public final class GuardSidearmEquipmentService {
    public static void replace(Guard guard, ItemStack stack) {
        if (!ServiceTransactions.mutating(guard) || GuardSidearmStorageAccess.get(guard).stateAgainst(guard.getMainHandItem()) != SidearmState.PRIMARY_ACTIVE)
            throw new IllegalStateException("Sidearm service does not own a stable guard");
        GuardSidearmStorageAccess.get(guard).setStackInSlot(GuardSidearmStorage.SIDEARM_SLOT, stack.copy());
    }
}
