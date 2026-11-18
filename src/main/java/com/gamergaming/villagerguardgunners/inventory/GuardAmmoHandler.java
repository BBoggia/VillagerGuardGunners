package com.gamergaming.villagerguardgunners.inventory;

import com.gamergaming.villagerguardgunners.guns.GunProviders;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class GuardAmmoHandler extends ItemStackHandler {
    public static final int SLOT_COUNT = 3;

    public GuardAmmoHandler() {
        super(SLOT_COUNT);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isAmmo(stack);
    }

    public static boolean isAmmo(ItemStack stack) {
        return !stack.isEmpty() && GunProviders.isAmmo(stack);
    }

    public ItemStack insert(ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < getSlots() && !remainder.isEmpty(); slot++) {
            remainder = insertItem(slot, remainder, false);
        }
        return remainder;
    }

    public List<ItemStack> loadSafely(net.minecraft.core.HolderLookup.Provider registries, CompoundTag inventoryTag) {
        clear();
        List<ItemStack> rejected = new ArrayList<>();
        ListTag serializedItems = inventoryTag.getList("Items", Tag.TAG_COMPOUND);

        for (int index = 0; index < serializedItems.size(); index++) {
            CompoundTag serializedStack = serializedItems.getCompound(index);
            ItemStack stack = ItemStack.parseOptional(registries, serializedStack);
            if (stack.isEmpty()) {
                continue;
            }

            int requestedSlot = serializedStack.getInt("Slot");
            ItemStack remainder = stack;
            if (requestedSlot >= 0 && requestedSlot < getSlots() && isItemValid(requestedSlot, stack)) {
                remainder = insertItem(requestedSlot, stack, false);
            }
            if (!remainder.isEmpty()) {
                remainder = insert(remainder);
            }
            if (!remainder.isEmpty()) {
                rejected.add(remainder.copy());
            }
        }

        return rejected;
    }

    public List<ItemStack> importLegacyStacks(net.minecraft.core.HolderLookup.Provider registries, List<CompoundTag> serializedStacks) {
        List<ItemStack> rejected = new ArrayList<>();
        for (CompoundTag serializedStack : serializedStacks) {
            ItemStack stack = ItemStack.parseOptional(registries, serializedStack);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack remainder = insert(stack);
            if (!remainder.isEmpty()) {
                rejected.add(remainder.copy());
            }
        }
        return rejected;
    }

    /**
     * Merges legacy guard slots into an already-restored add-on inventory.
     * Exact item/count matches are treated as stale rollback copies, while any
     * unmatched quantity is still imported so mixed-format saves do not lose
     * ammunition that exists only in the legacy inventory.
     */
    public List<ItemStack> importLegacyStacksDeduplicated(net.minecraft.core.HolderLookup.Provider registries,
            List<CompoundTag> serializedStacks,
            List<ItemStack> alreadyRestoredOverflow) {
        List<ItemStack> unmatchedRestored = new ArrayList<>();
        for (int slot = 0; slot < getSlots(); slot++) {
            copyNonEmpty(getStackInSlot(slot), unmatchedRestored);
        }
        for (ItemStack stack : alreadyRestoredOverflow) {
            copyNonEmpty(stack, unmatchedRestored);
        }

        List<ItemStack> rejected = new ArrayList<>();
        for (CompoundTag serializedStack : serializedStacks) {
            ItemStack legacyStack = ItemStack.parseOptional(registries, serializedStack);
            if (legacyStack.isEmpty()) {
                continue;
            }

            consumeRestoredMatches(legacyStack, unmatchedRestored);
            if (!legacyStack.isEmpty()) {
                ItemStack remainder = insert(legacyStack);
                if (!remainder.isEmpty()) {
                    rejected.add(remainder.copy());
                }
            }
        }
        return rejected;
    }

    private static void consumeRestoredMatches(ItemStack legacyStack, List<ItemStack> restoredStacks) {
        for (ItemStack restored : restoredStacks) {
            if (legacyStack.isEmpty()) {
                return;
            }
            if (!restored.isEmpty() && ItemStack.isSameItemSameComponents(legacyStack, restored)) {
                int duplicateCount = Math.min(legacyStack.getCount(), restored.getCount());
                legacyStack.shrink(duplicateCount);
                restored.shrink(duplicateCount);
            }
        }
    }

    private static void copyNonEmpty(ItemStack stack, List<ItemStack> destination) {
        if (!stack.isEmpty()) {
            destination.add(stack.copy());
        }
    }

    public void clear() {
        for (int slot = 0; slot < getSlots(); slot++) {
            setStackInSlot(slot, ItemStack.EMPTY);
        }
    }
}
