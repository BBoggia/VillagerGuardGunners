package com.gamergaming.villagerguardgunners.inventory;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Add-on metadata only. Gun and ammo identity remain TaCZ data components. */
public final class ItemCustomData {
    private ItemCustomData() {}
    public static CompoundTag read(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
    public static void set(ItemStack stack, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static void update(ItemStack stack, Consumer<CompoundTag> change) {
        CompoundTag tag = read(stack);
        change.accept(tag);
        set(stack, tag);
    }
}
