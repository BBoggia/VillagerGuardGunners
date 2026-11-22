package com.gamergaming.villagerguardgunners.ammunition;

import com.gamergaming.villagerguardgunners.guns.GunProviders;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/** Virtual reserves never appear as items or modify the inventory capability. */
public final class GuardInfiniteAmmo {
    private GuardInfiniteAmmo() {}

    public static InfiniteAmmoMode mode() {
        return ModConfigs.GUARD_GUNNERS.ammunition.infiniteAmmoMode.get();
    }

    public static boolean virtualReserve(LivingEntity entity) {
        return entity instanceof Guard && mode().virtualReserve();
    }

    public static boolean withoutReloading(LivingEntity entity) {
        return entity instanceof Guard && mode() == InfiniteAmmoMode.WITHOUT_RELOADING;
    }

    /** Called only during a new loadout's preparation, before its atomic commit. */
    public static ItemStack spawnSupply(ItemStack gunStack, ItemStack ordinaryAmmo) {
        if (mode() != InfiniteAmmoMode.CREATIVE_AMMO_BOX) return ordinaryAmmo;
        var provider = GunProviders.find(gunStack)
                .orElseThrow(() -> new IllegalStateException("No provider for loadout gun"));
        if (!provider.supportsCreativeSupply()) return ordinaryAmmo;
        return provider.creativeSupply(gunStack)
                .filter(stack -> !stack.isEmpty())
                .orElseThrow(() -> new IllegalStateException("Could not prepare a compatible creative ammo box"));
    }
}
