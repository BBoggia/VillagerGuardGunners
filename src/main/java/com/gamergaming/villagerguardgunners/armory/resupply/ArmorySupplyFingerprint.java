package com.gamergaming.villagerguardgunners.armory.resupply;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Item and full tag identity captured without retaining a mutable live stack. */
public record ArmorySupplyFingerprint(
        ResourceLocation itemId,
        @Nullable DataComponentPatch itemTag) {
    public ArmorySupplyFingerprint {
        itemId = Objects.requireNonNull(itemId, "itemId");
        itemTag = itemTag == null ? null : itemTag;
    }

    @Override
    @Nullable
    public DataComponentPatch itemTag() {
        return itemTag == null ? null : itemTag;
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
        return new ArmorySupplyFingerprint(itemId, stack.getComponentsPatch());
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return BuiltInRegistries.ITEM.get(itemId) == stack.getItem()
                    && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    && Objects.equals(itemTag, stack.getComponentsPatch());
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
