package com.gamergaming.villagerguardgunners.armory;

import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** Fixed-size ammunition storage used by an armory and exposed to automation. */
public final class ArmoryItemHandler extends ItemStackHandler {
    public static final int SLOT_COUNT = 9;
    private static final int MAX_SERIALIZED_ENTRIES = 128;

    private final Runnable changeListener;
    private boolean suppressChangeNotifications;

    public ArmoryItemHandler(Runnable changeListener) {
        super(SLOT_COUNT);
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < SLOT_COUNT && GuardAmmoHandler.isAmmo(stack);
    }

    @Override
    public void onContentsChanged(int slot) {
        if (!suppressChangeNotifications) {
            changeListener.run();
        }
    }

    /**
     * Restores only valid ammunition without trusting an encoded handler size.
     * Rejected or excess stacks among the bounded inspected entries are
     * returned so the block entity can eject them instead of silently deleting
     * ordinary data. Entries beyond the corruption-safety limit are ignored.
     */
    public List<ItemStack> loadSafely(CompoundTag inventoryTag) {
        suppressChangeNotifications = true;
        try {
            clearInternal();
            List<ItemStack> rejected = new ArrayList<>();
            if (inventoryTag == null
                    || !inventoryTag.contains("Items", Tag.TAG_LIST)) {
                return rejected;
            }
            ListTag items = inventoryTag.getList("Items", Tag.TAG_COMPOUND);
            int inspected = Math.min(items.size(), MAX_SERIALIZED_ENTRIES);
            for (int index = 0; index < inspected; index++) {
                CompoundTag encoded = items.getCompound(index);
                ItemStack stack = ItemStack.of(encoded);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!GuardAmmoHandler.isAmmo(stack)) {
                    rejected.add(stack.copy());
                    continue;
                }
                int requestedSlot = encoded.contains("Slot", Tag.TAG_INT)
                        ? encoded.getInt("Slot")
                        : encoded.contains("Slot", Tag.TAG_BYTE)
                                ? encoded.getByte("Slot") & 255
                                : -1;
                ItemStack remainder = stack;
                if (requestedSlot >= 0 && requestedSlot < SLOT_COUNT) {
                    remainder = insertItem(requestedSlot, remainder, false);
                }
                for (int slot = 0; slot < SLOT_COUNT && !remainder.isEmpty(); slot++) {
                    if (slot != requestedSlot) {
                        remainder = insertItem(slot, remainder, false);
                    }
                }
                if (!remainder.isEmpty()) {
                    rejected.add(remainder.copy());
                }
            }
            return rejected;
        } finally {
            suppressChangeNotifications = false;
        }
    }

    public int comparatorOutput() {
        float fullness = 0.0F;
        boolean occupied = false;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = getStackInSlot(slot);
            if (!stack.isEmpty()) {
                int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
                fullness += (float) stack.getCount() / (float) Math.max(1, limit);
                occupied = true;
            }
        }
        return Mth.floor(fullness / SLOT_COUNT * 14.0F) + (occupied ? 1 : 0);
    }

    private void clearInternal() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            setStackInSlot(slot, ItemStack.EMPTY);
        }
    }
}
