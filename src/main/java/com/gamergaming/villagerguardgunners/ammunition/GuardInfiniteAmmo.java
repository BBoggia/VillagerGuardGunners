package com.gamergaming.villagerguardgunners.ammunition;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.common.entities.Guard;

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

    /** Keep the magazine available while leaving chamber/action cycling to TaCZ. */
    public static void replenishMagazine(LivingEntity entity, ItemStack stack, GunData data) {
        if (entity == null || entity.level().isClientSide || !withoutReloading(entity)
                || data == null || !(stack.getItem() instanceof IGun gun) || gun.useInventoryAmmo(stack)) return;
        int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(stack, data);
        if (capacity > 0 && gun.getCurrentAmmoCount(stack) < capacity) {
            gun.setCurrentAmmoCount(stack, capacity);
        }
    }

    public static void replenishMagazine(LivingEntity entity, ItemStack stack) {
        if (!withoutReloading(entity) || !(stack.getItem() instanceof IGun gun)) return;
        TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                .ifPresent(index -> replenishMagazine(entity, stack, index.getGunData()));
    }

    /** Called only during a new loadout's preparation, before its atomic commit. */
    public static ItemStack spawnSupply(ItemStack gunStack, ItemStack ordinaryAmmo) {
        if (mode() != InfiniteAmmoMode.CREATIVE_AMMO_BOX) return ordinaryAmmo;
        IGun gun = (IGun) gunStack.getItem();
        var index = TimelessAPI.getCommonGunIndex(gun.getGunId(gunStack)).orElseThrow();
        ItemStack stack = new ItemStack(ModItems.AMMO_BOX.get());
        IAmmoBox box = (IAmmoBox) stack.getItem();
        box.setAmmoId(stack, index.getGunData().getAmmoId());
        // TaCZ's boolean selects all-caliber vs caliber-specific creative boxes.
        box.setCreative(stack, false);
        if (!box.isCreative(stack) || !box.isAmmoBoxOfGun(gunStack, stack)) {
            throw new IllegalStateException("Could not prepare a compatible creative ammo box");
        }
        return stack;
    }
}
