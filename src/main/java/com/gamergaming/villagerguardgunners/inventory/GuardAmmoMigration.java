package com.gamergaming.villagerguardgunners.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class GuardAmmoMigration {
    public static final int FIRST_LEGACY_AMMO_SLOT = 6;
    public static final int LEGACY_AMMO_SLOT_COUNT = 3;
    public static final int LEGACY_AMMO_SLOT_END = FIRST_LEGACY_AMMO_SLOT + LEGACY_AMMO_SLOT_COUNT;

    private GuardAmmoMigration() {
    }

    public static List<CompoundTag> extractLegacyAmmoSlots(CompoundTag guardTag) {
        Tag rawInventory = guardTag.get("Inventory");
        if (!(rawInventory instanceof ListTag inventory) || inventory.getElementType() != Tag.TAG_COMPOUND) {
            return List.of();
        }

        ListTag retainedInventory = new ListTag();
        List<CompoundTag> extractedStacks = new ArrayList<>();

        for (Tag rawEntry : inventory) {
            CompoundTag entry = (CompoundTag) rawEntry;
            int slot = entry.getByte("Slot") & 255;
            if (slot >= FIRST_LEGACY_AMMO_SLOT && slot < LEGACY_AMMO_SLOT_END) {
                extractedStacks.add(entry.copy());
            } else {
                retainedInventory.add(entry.copy());
            }
        }

        guardTag.put("Inventory", retainedInventory);
        return extractedStacks;
    }
}
