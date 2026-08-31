package com.gamergaming.villagerguardgunners.menu;

import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.armory.ArmoryItemHandler;
import com.gamergaming.villagerguardgunners.init.ModBlocks;
import com.gamergaming.villagerguardgunners.init.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Nine ammunition slots plus the opening player's standard inventory. */
public final class ArmoryMenu extends AbstractContainerMenu {
    private static final int ARMORY_SLOT_START = 0;
    private static final int ARMORY_SLOT_END = ArmoryItemHandler.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = ARMORY_SLOT_END;
    private static final int PLAYER_HOTBAR_START = PLAYER_INVENTORY_START + 27;
    private static final int PLAYER_SLOT_END = PLAYER_HOTBAR_START + 9;

    private final ArmoryBlockEntity armory;
    private final ContainerLevelAccess levelAccess;

    public ArmoryMenu(
            int containerId,
            Inventory playerInventory,
            ArmoryBlockEntity armory) {
        super(ModMenus.ARMORY.get(), containerId);
        this.armory = armory;
        this.levelAccess = ContainerLevelAccess.create(
                playerInventory.player.level(),
                armory.getBlockPos());

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int slot = column + row * 3;
                addSlot(new SlotItemHandler(
                        armory.getInventory(),
                        slot,
                        62 + column * 18,
                        17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + (row + 1) * 9,
                        8 + column * 18,
                        84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    playerInventory,
                    column,
                    8 + column * 18,
                    142));
        }
    }

    public ArmoryBlockEntity getArmory() {
        return armory;
    }

    @Override
    public boolean stillValid(Player player) {
        if (armory.isRemoved()) {
            return false;
        }
        return levelAccess.evaluate((level, position) ->
                        level.getBlockEntity(position) == armory
                                && level.getBlockState(position).is(ModBlocks.ARMORY.get())
                                && player.distanceToSqr(
                                        position.getX() + 0.5D,
                                        position.getY() + 0.5D,
                                        position.getZ() + 0.5D) <= 64.0D,
                false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = source.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < ARMORY_SLOT_END) {
            moved = moveItemStackTo(
                    stack,
                    PLAYER_INVENTORY_START,
                    PLAYER_SLOT_END,
                    true);
        } else if (armory.getInventory().isItemValid(0, stack)) {
            moved = moveItemStackTo(
                    stack,
                    ARMORY_SLOT_START,
                    ARMORY_SLOT_END,
                    false);
        } else if (index < PLAYER_HOTBAR_START) {
            moved = moveItemStackTo(
                    stack,
                    PLAYER_HOTBAR_START,
                    PLAYER_SLOT_END,
                    false);
        } else {
            moved = moveItemStackTo(
                    stack,
                    PLAYER_INVENTORY_START,
                    PLAYER_HOTBAR_START,
                    false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        source.onTake(player, stack);
        return original;
    }
}
