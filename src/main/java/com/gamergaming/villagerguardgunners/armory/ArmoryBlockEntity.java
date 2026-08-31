package com.gamergaming.villagerguardgunners.armory;

import com.gamergaming.villagerguardgunners.armory.ArmorySquadLockCodec.DecodeResult;
import com.gamergaming.villagerguardgunners.armory.ArmorySquadLockCodec.DecodeStatus;
import com.gamergaming.villagerguardgunners.armory.resupply.ArmoryResupplyServerServices;
import com.gamergaming.villagerguardgunners.init.ModBlockEntities;
import com.gamergaming.villagerguardgunners.menu.ArmoryMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/** Unticked, fixed-size ammunition storage with an optional squad restriction. */
public final class ArmoryBlockEntity extends BlockEntity implements MenuProvider {
    private static final String INVENTORY_TAG = "Inventory";

    private final ArmoryItemHandler inventory = new ArmoryItemHandler(this::inventoryChanged);
    private final IItemHandler automationView = new IItemHandler() {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    };
    private final List<ItemStack> pendingRejectedStacks = new ArrayList<>();
    private LazyOptional<IItemHandler> inventoryCapability =
            LazyOptional.of(() -> automationView);
    @Nullable
    private ArmorySquadLock squadLock;
    private DecodeStatus lockStatus = DecodeStatus.BLANK;
    @Nullable
    private Tag unreadableLockTag;
    private int unreadableLockVersion = -1;
    private int lastComparatorOutput;

    public ArmoryBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ARMORY.get(), position, state);
    }

    public ArmoryItemHandler getInventory() {
        return inventory;
    }

    @Nullable
    public ArmorySquadLock getSquadLock() {
        return squadLock;
    }

    public DecodeStatus getLockStatus() {
        return lockStatus;
    }

    public boolean hasReadableLock() {
        return lockStatus == DecodeStatus.BLANK || lockStatus == DecodeStatus.VALID;
    }

    /** Step 6 uses this constant-time check; malformed lock data fails closed. */
    public boolean maySupply(@Nullable UUID squadId) {
        if (!hasReadableLock()) {
            return false;
        }
        return squadLock == null
                || (squadId != null && squadLock.squadId().equals(squadId));
    }

    public void setSquadLock(@Nullable ArmorySquadLock replacement) {
        if (replacement == null && squadLock == null && lockStatus == DecodeStatus.BLANK) {
            return;
        }
        if (replacement != null
                && replacement.equals(squadLock)
                && lockStatus == DecodeStatus.VALID) {
            return;
        }
        squadLock = replacement;
        lockStatus = replacement == null ? DecodeStatus.BLANK : DecodeStatus.VALID;
        unreadableLockTag = null;
        unreadableLockVersion = -1;
        lockChanged();
    }

    public int comparatorOutput() {
        return inventory.comparatorOutput();
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack extracted;
            while (!(extracted = inventory.extractItem(
                            slot,
                            inventory.getSlotLimit(slot),
                            false))
                    .isEmpty()) {
                Containers.dropItemStack(
                        level,
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D,
                        extracted);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.villagerguardgunners.armory");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(
            int containerId,
            Inventory playerInventory,
            Player player) {
        return new ArmoryMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(INVENTORY_TAG, inventory.serializeNBT());
        writePersistentLock(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        pendingRejectedStacks.clear();
        if (tag.contains(INVENTORY_TAG, CompoundTag.TAG_COMPOUND)) {
            pendingRejectedStacks.addAll(inventory.loadSafely(
                    tag.getCompound(INVENTORY_TAG)));
        } else {
            pendingRejectedStacks.addAll(inventory.loadSafely(new CompoundTag()));
        }
        lastComparatorOutput = inventory.comparatorOutput();
        applyDecodedLock(tag);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!inventoryCapability.isPresent()) {
            inventoryCapability = LazyOptional.of(() -> automationView);
        }
        if (level != null && !level.isClientSide && !pendingRejectedStacks.isEmpty()) {
            for (ItemStack stack : List.copyOf(pendingRejectedStacks)) {
                Containers.dropItemStack(
                        level,
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D,
                        stack);
            }
            pendingRejectedStacks.clear();
            setChanged();
        }
        if (level instanceof ServerLevel serverLevel) {
            ArmoryResupplyServerServices.forLevel(serverLevel)
                    .loadedArmories()
                    .register(serverLevel, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            ArmoryResupplyServerServices.Services services =
                    ArmoryResupplyServerServices.existingForLevel(serverLevel);
            if (services != null) {
                services.loadedArmories().unregister(
                        serverLevel,
                        worldPosition,
                        this);
                services.reservations().releaseArmory(
                        serverLevel.dimension().location(),
                        worldPosition);
            }
        }
        super.setRemoved();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventoryCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inventoryCapability = LazyOptional.of(() -> automationView);
    }

    @Override
    public <T> LazyOptional<T> getCapability(
            Capability<T> capability,
            @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ArmorySquadLockCodec.writeTo(tag, squadLock);
        if (lockStatus == DecodeStatus.CORRUPT) {
            tag.putString(ArmorySquadLockCodec.LOCK_TAG, "unreadable");
        } else if (lockStatus == DecodeStatus.UNSUPPORTED_VERSION) {
            CompoundTag invalid = new CompoundTag();
            invalid.putInt("Version", unreadableLockVersion);
            tag.put(ArmorySquadLockCodec.LOCK_TAG, invalid);
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        applyDecodedLock(tag);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(
            Connection connection,
            ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag);
        }
    }

    private void inventoryChanged() {
        setChanged();
        int comparatorOutput = inventory.comparatorOutput();
        if (comparatorOutput == lastComparatorOutput) {
            return;
        }
        lastComparatorOutput = comparatorOutput;
        if (level != null && !level.isClientSide) {
            level.updateNeighbourForOutputSignal(
                    worldPosition,
                    getBlockState().getBlock());
        }
    }

    private void lockChanged() {
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private void writePersistentLock(CompoundTag tag) {
        if (hasReadableLock()) {
            ArmorySquadLockCodec.writeTo(tag, squadLock);
            return;
        }
        if (unreadableLockTag != null) {
            tag.put(ArmorySquadLockCodec.LOCK_TAG, unreadableLockTag.copy());
            return;
        }
        // Defensive fallback: an unreadable in-memory state must never serialize
        // as a blank/public lock.
        tag.putString(ArmorySquadLockCodec.LOCK_TAG, "unreadable");
    }

    private void applyDecodedLock(CompoundTag tag) {
        DecodeResult decoded = ArmorySquadLockCodec.readFrom(tag);
        lockStatus = decoded.status();
        squadLock = decoded.lock();
        unreadableLockVersion = decoded.storedVersion();
        Tag encoded = tag.get(ArmorySquadLockCodec.LOCK_TAG);
        unreadableLockTag = hasReadableLock() || encoded == null
                ? null
                : encoded.copy();
    }
}
