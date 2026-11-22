package com.gamergaming.villagerguardgunners.quartermaster.service;

import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Revocable transport view; normal native inventory behavior is preserved while the owner is available. */
public record ServiceInventoryView(IItemHandler delegate, BooleanSupplier available) implements IItemHandler {
    @Override public int getSlots() { return delegate.getSlots(); }
    @Override public ItemStack getStackInSlot(int slot) { return available.getAsBoolean() ? delegate.getStackInSlot(slot) : ItemStack.EMPTY; }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return available.getAsBoolean() ? delegate.insertItem(slot, stack, simulate) : stack; }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return available.getAsBoolean() ? delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY; }
    @Override public int getSlotLimit(int slot) { return delegate.getSlotLimit(slot); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return available.getAsBoolean() && delegate.isItemValid(slot, stack); }
}
