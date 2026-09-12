package com.gamergaming.villagerguardgunners.menu;

import com.gamergaming.villagerguardgunners.init.ModMenus;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** All clicks, including drag/swap and stale packets, recheck the current station policy. */
public final class QuartermasterMenu extends AbstractContainerMenu {
    @Nullable private final QuartermasterBlockEntity station;
    private final Player viewer;
    private final ContainerData settings;
    private final ItemStackHandler items;

    public QuartermasterMenu(int id, Inventory inventory, BlockPos position) {
        this(id, inventory, null, new ItemStackHandler(QuartermasterBlockEntity.SLOTS), new SimpleContainerData(7));
    }
    public QuartermasterMenu(int id, Inventory inventory, QuartermasterBlockEntity station) {
        this(id, inventory, station, station.inventory(), new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> station.accessMode();
                    case 1 -> station.automationEnabled() ? 1 : 0;
                    case 2 -> station.hasSquad() ? 1 : 0;
                    case 3 -> station.canManage(inventory.player) ? 1 : 0;
                    case 4 -> station.guardAccess();
                    case 5 -> station.linkedArmories().size();
                    case 6 -> com.gamergaming.villagerguardgunners.quartermaster.service.ServiceEligibility.station(station).ordinal();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 7; }
        });
    }
    private QuartermasterMenu(int id, Inventory inventory, @Nullable QuartermasterBlockEntity station,
            ItemStackHandler items, ContainerData settings) {
        super(ModMenus.QUARTERMASTER.get(), id);
        this.station = station;
        this.viewer = inventory.player;
        this.items = items;
        this.settings = settings;
        addDataSlots(settings);
        for (int index = 0; index < QuartermasterBlockEntity.SLOTS; index++) {
            int row = index / 9;
            addSlot(new SlotItemHandler(items, index, 8 + index % 9 * 18,
                    row < 3 ? 43 + row * 18 : 111) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return stillValid(viewer) && stack.getItem().canFitInsideContainerItems() && super.mayPlace(stack);
                }
                @Override public boolean mayPickup(Player player) {
                    return stillValid(player) && super.mayPickup(player);
                }
                @Override public void setChanged() {
                    if (station != null) station.setChanged();
                }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + (row + 1) * 9, 8 + column * 18, 146 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 204));
    }
    public int guardAccess() { return settings.get(4); }
    public int linkedArmories() { return settings.get(5); }
    public int serviceStatus() { return settings.get(6); }
    public int accessMode() { return settings.get(0); }
    public boolean automationEnabled() { return settings.get(1) != 0; }
    public boolean hasSquad() { return settings.get(2) != 0; }
    public boolean canManage() { return settings.get(3) != 0; }
    @Override public boolean stillValid(Player player) {
        return player == viewer && (player.level().isClientSide || station != null && station.canInteract(player));
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player)) super.clicked(slot, button, type, player);
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        return stillValid(player) && station != null && station.changeSetting(player, id);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = source.getItem();
        ItemStack original = stack.copy();
        if (index < 36) {
            if (!moveItemStackTo(stack, 36, 72, true)) return ItemStack.EMPTY;
        } else if (stack.getItem().canFitInsideContainerItems() && items.isItemValid(0, stack)) {
            if (!moveItemStackTo(stack, 0, 27, false)) return ItemStack.EMPTY;
        } else if (index < 63) {
            if (!moveItemStackTo(stack, 63, 72, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 36, 63, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) source.set(ItemStack.EMPTY); else source.setChanged();
        source.onTake(player, stack);
        return original;
    }
}
