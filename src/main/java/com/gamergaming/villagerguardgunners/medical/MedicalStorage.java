package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.init.ModItems;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import tallestegg.guardvillagers.entities.Guard;

/** Three slots plus an opaque recovery payload. Unknown/removed profiles never delete stored items. */
public final class MedicalStorage {
    public static final String KEY = "villagerguardgunners:medical";
    public static final int SLOTS = 3;
    private final Guard guard;
    private UUID dropId = UUID.randomUUID();
    private boolean released, busy;
    private long readyAt;
    private long transportGeneration;
    @Nullable private Tag preserved;
    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return available() && stack.getItem().canFitInsideContainerItems()
                    && (guard.level().isClientSide || TreatmentProfiles.resolve(stack) != null);
        }
    };
    public MedicalStorage(Guard guard) { this.guard = guard; }
    public boolean readable() { return preserved == null; }
    public boolean available() { return readable() && !released && !busy; }
    public boolean busy() { return busy; }
    public boolean released() { return released; }
    public void busy(boolean value) { busy = value; }
    public long readyAt() { return readyAt; }
    public void readyAt(long value) { readyAt = value; }
    public UUID dropId() { return dropId; }
    public boolean empty() {
        if (!readable() || released || busy) return false;
        for (int i = 0; i < SLOTS; i++) if (!items.getStackInSlot(i).isEmpty()) return false;
        return true;
    }
    /** Trusted menu and treatment owner. External transport uses the restricted view below. */
    public ItemStackHandler inventory() { return items; }
    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        if (preserved != null) root.put(KEY, preserved.copy());
        else {
            CompoundTag data = new CompoundTag();
            data.putInt("Version", 1); data.putUUID("DropId", dropId); data.putLong("ReadyAt", readyAt);
            data.putBoolean("Released", released); data.put("Inventory", items.serializeNBT()); root.put(KEY, data);
        }
        return root;
    }
    public void load(CompoundTag root) {
        items.setSize(SLOTS); preserved = null; released = false; busy = false; readyAt = 0; dropId = UUID.randomUUID();
        if (!root.contains(KEY)) return;
        preserved = root.get(KEY).copy();
        if (!(preserved instanceof CompoundTag data) || !data.getAllKeys().equals(Set.of("Version", "DropId", "ReadyAt", "Released", "Inventory"))
                || !data.contains("Version", Tag.TAG_INT) || data.getInt("Version") != 1 || !data.hasUUID("DropId")
                || !data.contains("ReadyAt", Tag.TAG_LONG) || !data.contains("Released", Tag.TAG_BYTE)
                || !data.contains("Inventory", Tag.TAG_COMPOUND)) return;
        var inventory = data.getCompound("Inventory");
        if (!inventory.getAllKeys().equals(Set.of("Size", "Items")) || inventory.getInt("Size") != SLOTS
                || !inventory.contains("Size", Tag.TAG_INT) || !inventory.contains("Items", Tag.TAG_LIST)) return;
        var entries = inventory.getList("Items", Tag.TAG_COMPOUND);
        if (entries.size() != ((net.minecraft.nbt.ListTag) inventory.get("Items")).size()) return;
        var seen = new HashSet<Integer>();
        for (Tag value : entries) {
            var entry = (CompoundTag) value; int slot = entry.getInt("Slot"); ItemStack stack = ItemStack.of(entry);
            if (!entry.contains("Slot", Tag.TAG_INT) || slot < 0 || slot >= SLOTS || !seen.add(slot)
                    || stack.isEmpty() || stack.getCount() > Math.min(64, stack.getMaxStackSize())
                    || !stack.getItem().canFitInsideContainerItems()) return;
            var encoded = stack.save(new CompoundTag()); encoded.putInt("Slot", slot);
            if (!encoded.equals(entry)) return;
        }
        items.deserializeNBT(inventory); readyAt = data.getLong("ReadyAt"); dropId = data.getUUID("DropId");
        released = data.getBoolean("Released"); preserved = null;
    }
    public ItemStack packed() {
        var stack = new ItemStack(ModItems.MEDICAL_POUCH.get()); stack.setTag(save()); return stack;
    }
    public void release() {
        if (released || guard.level() == null || guard.level().isClientSide) return;
        if (preserved == null && java.util.stream.IntStream.range(0, SLOTS).allMatch(i -> items.getStackInSlot(i).isEmpty())) return;
        if (guard.level() instanceof net.minecraft.server.level.ServerLevel level) {
            MedicalRecovery.get(level).enqueue(dropId, guard.blockPosition(), packed());
            released = true; preserved = null; items.setSize(SLOTS);
        }
    }
    public void clearAfterPacking() {
        long cooldown = readyAt;
        load(new CompoundTag());
        readyAt = cooldown;
    }
    public boolean restore(ItemStack packed) {
        if (!empty() || !packed.is(ModItems.MEDICAL_POUCH.get()) || !packed.hasTag() || !packed.getTag().contains(KEY)) return false;
        long cooldown = readyAt;
        load(packed.getTag()); dropId = UUID.randomUUID(); released = false;
        readyAt = Math.max(cooldown, readyAt); return true;
    }
    public void invalidateTransport() { transportGeneration++; }
    /** Dedicated capability slots: insertion is allowed, automatic withdrawal is not. */
    public MedicalSupplies transport() {
        long generation = transportGeneration;
        return new MedicalSupplies() {
            private boolean live() { return generation == transportGeneration && available()
                    && com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).readable()
                    && !com.gamergaming.villagerguardgunners.quartermaster.service.ServiceTransactions.mutating(guard) && guard.isAlive() && !guard.isRemoved(); }
            private boolean valid(int slot) { return slot >= 0 && slot < SLOTS; }
            @Override public int getSlots() { return SLOTS; }
            @Override public ItemStack getStackInSlot(int slot) { return live() && valid(slot) ? items.getStackInSlot(slot).copy() : ItemStack.EMPTY; }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return live() && valid(slot) ? items.insertItem(slot, stack.copy(), simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot) { return valid(slot) ? 64 : 0; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return live() && valid(slot) && items.isItemValid(slot, stack); }
        };
    }
}
