package com.gamergaming.villagerguardgunners.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

final class GuardAmmoMigrationTest {
    @Test
    void extractsOnlyTheThreeHistoricalAddonSlots() {
        CompoundTag guardTag = new CompoundTag();
        ListTag inventory = new ListTag();
        inventory.add(stackAtSlot(0));
        inventory.add(stackAtSlot(5));
        inventory.add(stackAtSlot(6));
        inventory.add(stackAtSlot(8));
        inventory.add(stackAtSlot(12));
        guardTag.put("Inventory", inventory);

        List<CompoundTag> extracted = GuardAmmoMigration.extractLegacyAmmoSlots(guardTag);
        ListTag retained = guardTag.getList("Inventory", Tag.TAG_COMPOUND);

        assertEquals(List.of(6, 8), extracted.stream().map(GuardAmmoMigrationTest::slot).toList());
        assertEquals(List.of(0, 5, 12), retained.stream().map(GuardAmmoMigrationTest::slot).toList());
    }

    @Test
    void leavesMissingInventoryDataUntouched() {
        CompoundTag guardTag = new CompoundTag();

        assertTrue(GuardAmmoMigration.extractLegacyAmmoSlots(guardTag).isEmpty());
        assertTrue(guardTag.isEmpty());
    }

    private static CompoundTag stackAtSlot(int slot) {
        CompoundTag stack = new CompoundTag();
        stack.putByte("Slot", (byte) slot);
        return stack;
    }

    private static int slot(Tag rawTag) {
        return ((CompoundTag) rawTag).getByte("Slot") & 255;
    }
}
