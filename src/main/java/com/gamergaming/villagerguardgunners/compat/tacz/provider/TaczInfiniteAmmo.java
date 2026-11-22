package com.gamergaming.villagerguardgunners.compat.tacz.provider;

import com.gamergaming.villagerguardgunners.ammunition.GuardInfiniteAmmo;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** TaCZ side of the infinite ammo modes: magazine top-ups and creative ammo boxes. */
public final class TaczInfiniteAmmo {
    private TaczInfiniteAmmo() {
    }

    /** Keep the magazine available while leaving chamber/action cycling to TaCZ. */
    public static void replenishMagazine(LivingEntity entity, ItemStack stack, GunData data) {
        if (entity == null || entity.level().isClientSide || !GuardInfiniteAmmo.withoutReloading(entity)
                || data == null || !(stack.getItem() instanceof IGun gun) || gun.useInventoryAmmo(stack)) return;
        int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(stack, data);
        if (capacity > 0 && gun.getCurrentAmmoCount(stack) < capacity) {
            gun.setCurrentAmmoCount(stack, capacity);
        }
    }

    public static void replenishMagazine(LivingEntity entity, ItemStack stack) {
        if (!GuardInfiniteAmmo.withoutReloading(entity) || !(stack.getItem() instanceof IGun gun)) return;
        TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                .ifPresent(index -> replenishMagazine(entity, stack, index.getGunData()));
    }

    /** A caliber-specific creative box for the gun. Throws when TaCZ cannot produce a matching one. */
    static ItemStack creativeBox(ItemStack gunStack) {
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
