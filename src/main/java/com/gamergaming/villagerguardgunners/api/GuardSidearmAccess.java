package com.gamergaming.villagerguardgunners.api;

import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmStorage;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.gamergaming.villagerguardgunners.sidearm.SidearmStorageAccessKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import tallestegg.guardvillagers.entities.Guard;

/** Safe public view of guard sidearm state; it is not a Forge capability. */
public interface GuardSidearmAccess {
    GuardSidearmStorage villagerGuardGunners$getSidearmStorage(
            SidearmStorageAccessKey accessKey);

    IItemHandler villagerGuardGunners$getConfiguredSidearmInventory();

    SidearmState villagerGuardGunners$getSidearmState();

    ItemStack villagerGuardGunners$getEffectivePrimaryCopy();

    static IItemHandler getConfiguredSidearmInventory(Guard guard) {
        return ((GuardSidearmAccess) guard)
                .villagerGuardGunners$getConfiguredSidearmInventory();
    }

    static SidearmState getState(Guard guard) {
        return ((GuardSidearmAccess) guard).villagerGuardGunners$getSidearmState();
    }

    static ItemStack getEffectivePrimaryCopy(Guard guard) {
        return ((GuardSidearmAccess) guard).villagerGuardGunners$getEffectivePrimaryCopy();
    }
}
