package com.gamergaming.villagerguardgunners.armory.resupply;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Item and full tag identity captured without retaining a mutable live stack. */
public record ArmorySupplyFingerprint(
        ResourceLocation itemId,
        @Nullable CompoundTag itemTag) {
    public ArmorySupplyFingerprint {
        itemId = Objects.requireNonNull(itemId, "itemId");
        itemTag = itemTag == null ? null : itemTag.copy();
    }

    @Override
    @Nullable
    public CompoundTag itemTag() {
        return itemTag == null ? null : itemTag.copy();
    }

    public static ArmorySupplyFingerprint capture(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot fingerprint an empty ammunition stack");
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || BuiltInRegistries.ITEM.get(itemId) != stack.getItem()) {
            throw new IllegalArgumentException("Cannot fingerprint an unregistered ammunition item");
        }
        return new ArmorySupplyFingerprint(itemId, stack.getTag());
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return BuiltInRegistries.ITEM.get(itemId) == stack.getItem()
                    && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    && Objects.equals(itemTag, stack.getTag());
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
