package com.gamergaming.villagerguardgunners.entity;

import com.gamergaming.villagerguardgunners.init.ModMenus;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import tallestegg.guardvillagers.GuardVillagers;
import tallestegg.guardvillagers.entities.Guard;

public class GuardGunnerContainer extends AbstractContainerMenu {
    public static final int GUARD_SLOT_COUNT = 6;
    public static final int AMMO_SLOT_COUNT = GuardAmmoHandler.SLOT_COUNT;
    public static final int ADDON_SLOT_COUNT = GUARD_SLOT_COUNT + AMMO_SLOT_COUNT;

    private static final int PLAYER_INVENTORY_START = ADDON_SLOT_COUNT;
    private static final int PLAYER_HOTBAR_START = PLAYER_INVENTORY_START + 27;
    private static final int PLAYER_SLOT_END = PLAYER_HOTBAR_START + 9;

    private final Container guardInventory;
    private final GuardAmmoHandler ammoHandler;
    private final Guard guard;

    public GuardGunnerContainer(
            int id,
            Inventory playerInventory,
            Container guardInventory,
            GuardAmmoHandler ammoHandler,
            Guard guard) {
        super(ModMenus.GUARD_GUNNER_CONTAINER.get(), id);
        this.guardInventory = guardInventory;
        this.ammoHandler = ammoHandler;
        this.guard = guard;
        guardInventory.startOpen(playerInventory.player);

        addArmorSlot(playerInventory, 0, 8, 8, EquipmentSlot.HEAD, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
        addArmorSlot(playerInventory, 1, 8, 26, EquipmentSlot.CHEST, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE);
        addArmorSlot(playerInventory, 2, 8, 44, EquipmentSlot.LEGS, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS);
        addArmorSlot(playerInventory, 3, 8, 62, EquipmentSlot.FEET, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);

        addSlot(new Slot(guardInventory, 4, 77, 62) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return mayEditGuard(playerInventory.player);
            }

            @Override
            public void set(ItemStack stack) {
                super.set(stack);
                guard.setItemSlot(EquipmentSlot.OFFHAND, stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return mayEditGuard(player);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });

        addSlot(new Slot(guardInventory, 5, 77, 44) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return mayEditGuard(playerInventory.player);
            }

            @Override
            public void set(ItemStack stack) {
                super.set(stack);
                guard.setItemSlot(EquipmentSlot.MAINHAND, stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return mayEditGuard(player);
            }
        });

        for (int slot = 0; slot < AMMO_SLOT_COUNT; slot++) {
            addSlot(new SlotItemHandler(ammoHandler, slot, 99 + slot * 18, 44) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return super.mayPlace(stack) && mayEditGuard(playerInventory.player);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return mayEditGuard(player);
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + (row + 1) * 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    private void addArmorSlot(
            Inventory playerInventory,
            int inventoryIndex,
            int x,
            int y,
            EquipmentSlot equipmentSlot,
            ResourceLocation emptySlotIcon) {
        addSlot(new Slot(guardInventory, inventoryIndex, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.canEquip(equipmentSlot, guard) && mayEditGuard(playerInventory.player);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void set(ItemStack stack) {
                super.set(stack);
                guard.setItemSlot(equipmentSlot, stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return mayEditGuard(player);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, emptySlotIcon);
            }
        });
    }

    private boolean mayEditGuard(Player player) {
        return GuardVillagers.hotvChecker(player, guard);
    }

    public Guard getGuard() {
        return guard;
    }

    public GuardAmmoHandler getAmmoHandler() {
        return ammoHandler;
    }

    @Override
    public boolean stillValid(Player player) {
        return guardInventory.stillValid(player) && guard.isAlive() && guard.distanceTo(player) < 8.0F;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack originalStack = sourceStack.copy();
        boolean moved = index < ADDON_SLOT_COUNT
                ? moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, PLAYER_SLOT_END, true)
                : moveFromPlayerInventory(sourceStack, index);

        if (!moved) {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        if (sourceStack.getCount() == originalStack.getCount()) {
            return ItemStack.EMPTY;
        }

        sourceSlot.onTake(player, sourceStack);
        return originalStack;
    }

    private boolean moveFromPlayerInventory(ItemStack stack, int sourceIndex) {
        if (GuardAmmoHandler.isAmmo(stack) && moveItemStackTo(stack, GUARD_SLOT_COUNT, ADDON_SLOT_COUNT, false)) {
            return true;
        }

        for (int armorSlot = 0; armorSlot < 4; armorSlot++) {
            Slot destination = getSlot(armorSlot);
            if (!destination.hasItem() && destination.mayPlace(stack)
                    && moveItemStackTo(stack, armorSlot, armorSlot + 1, false)) {
                return true;
            }
        }

        if (!getSlot(5).hasItem() && getSlot(5).mayPlace(stack) && moveItemStackTo(stack, 5, 6, false)) {
            return true;
        }
        if (!getSlot(4).hasItem() && getSlot(4).mayPlace(stack) && moveItemStackTo(stack, 4, 5, false)) {
            return true;
        }

        if (sourceIndex >= PLAYER_INVENTORY_START && sourceIndex < PLAYER_HOTBAR_START) {
            return moveItemStackTo(stack, PLAYER_HOTBAR_START, PLAYER_SLOT_END, false);
        }
        if (sourceIndex >= PLAYER_HOTBAR_START && sourceIndex < PLAYER_SLOT_END) {
            return moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_HOTBAR_START, false);
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        guardInventory.stopOpen(player);
        guard.interacting = false;
    }
}
