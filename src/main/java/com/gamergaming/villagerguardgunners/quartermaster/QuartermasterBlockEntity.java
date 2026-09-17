package com.gamergaming.villagerguardgunners.quartermaster;

import com.gamergaming.villagerguardgunners.init.ModBlockEntities;
import com.gamergaming.villagerguardgunners.init.ModItems;
import com.gamergaming.villagerguardgunners.menu.QuartermasterMenu;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerBindingCodec;
import com.gamergaming.villagerguardgunners.squad.SquadRegistrySavedData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Unticked storage. Menu access and external transport have independent policies. */
public final class QuartermasterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int STOCK_SLOTS = 27;
    public static final int SLOTS = 36;
    public static final String DATA_TAG = "Quartermaster";
    public static final int PRIVATE = 0, SQUAD = 1, PUBLIC = 2;
    private UUID owner;
    private UUID squad;
    private UUID stationId = UUID.randomUUID();
    private UUID dropId = UUID.randomUUID();
    private final com.gamergaming.villagerguardgunners.quartermaster.service.ServiceReceipt serviceReceipt = new com.gamergaming.villagerguardgunners.quartermaster.service.ServiceReceipt(stationId);
    private int guardAccess = 1;
    private final java.util.List<BlockPos> linkedArmories = new java.util.ArrayList<>();
    private Tag preservedService;
    public UUID operator;
    public com.gamergaming.villagerguardgunners.quartermaster.service.ServiceStatus serviceStatus = com.gamergaming.villagerguardgunners.quartermaster.service.ServiceStatus.OPERATOR_UNAVAILABLE;
    public com.gamergaming.villagerguardgunners.quartermaster.service.ServiceReceipt serviceReceipt() { return serviceReceipt; }
    public boolean serviceReadable() { return readable() && preservedService == null && !released; }
    public int guardAccess() { return guardAccess; }
    public java.util.List<BlockPos> linkedArmories() { return java.util.List.copyOf(linkedArmories); }
    public boolean mayService(tallestegg.guardvillagers.entities.Guard guard) {
        if (!readable() || preservedService != null || released || isRemoved() || guardAccess == 0 || guard.level() != level) return false;
        if (guardAccess == 3) return true;
        if (guardAccess == 1) return owner != null && owner.equals(guard.getOwnerId());
        var membership = com.gamergaming.villagerguardgunners.api.GuardSquadAccess.getMembership(guard);
        return squad != null && membership != null && squad.equals(membership.squadId())
                && level instanceof ServerLevel server && SquadRegistrySavedData.get(server.getServer()).find(squad).isPresent();
    }
    private void saveService(CompoundTag root) {
        serviceReceipt.save(root);
        if (preservedService != null) { root.put("EquipmentService", preservedService.copy()); return; }
        CompoundTag service = new CompoundTag(); service.putInt("Version", 1); service.putInt("GuardAccess", guardAccess);
        service.putLongArray("Armories", linkedArmories.stream().mapToLong(BlockPos::asLong).toArray()); root.put("EquipmentService", service);
    }
    private void loadService(CompoundTag root) {
        serviceReceipt.load(root, stationId); guardAccess = 1; linkedArmories.clear(); preservedService = null;
        if (!root.contains("EquipmentService")) return;
        preservedService = root.get("EquipmentService").copy();
        if (!(preservedService instanceof CompoundTag value) || !value.getAllKeys().equals(Set.of("Version", "GuardAccess", "Armories"))
                || !value.contains("Version", Tag.TAG_INT) || value.getInt("Version") != 1 || !value.contains("GuardAccess", Tag.TAG_INT)
                || value.getInt("GuardAccess") < 0 || value.getInt("GuardAccess") > 3 || !value.contains("Armories", Tag.TAG_LONG_ARRAY)
                || value.getLongArray("Armories").length > 4) return;
        for (long packed : value.getLongArray("Armories")) {
            BlockPos pos = BlockPos.of(packed);
            if (linkedArmories.contains(pos)) { linkedArmories.clear(); return; }
            linkedArmories.add(pos);
        }
        guardAccess = value.getInt("GuardAccess"); preservedService = null;
    }
    @Override public void onLoad() {
        super.onLoad();
        com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime.loaded(this);
    }
    @Override public void setRemoved() {
        com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime.removed(this);
        super.setRemoved();
    }

    private int access;
    private boolean automation;
    private boolean released;
    @Nullable private Tag preserved;
    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            // Like shulker boxes, stations cannot be nested inside one another.
            return preserved == null && !released && stack.getItem().canFitInsideContainerItems();
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private long capabilityGeneration;
    private LazyOptional<IItemHandler> capability = LazyOptional.of(Transport::new);

    public QuartermasterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUARTERMASTER.get(), pos, state);
    }

    public ItemStackHandler inventory() { return inventory; }
    public boolean readable() { return preserved == null && !serviceReceipt.locked(); }
    public int accessMode() { return access; }
    public boolean automationEnabled() { return automation; }
    public boolean hasSquad() { return squad != null; }
    public UUID stationId() { return stationId; }
    public UUID dropId() { return dropId; }

    /** Called after BlockItem has restored any packed block-entity data. */
    public void placedBy(Player player) {
        if (owner == null && readable()) owner = player.getUUID();
        dropId = UUID.randomUUID();
        released = false;
        setChanged();
    }

    public boolean canManage(Player player) {
        return !player.isSpectator() && player.isAlive()
                && (player.hasPermissions(2) || player.getUUID().equals(owner));
    }

    public boolean canUse(Player player) {
        if (!readable() || released || player.isSpectator() || !player.isAlive()) return false;
        if (canManage(player)) return true;
        if (access == PUBLIC) return true;
        if (access != SQUAD || squad == null || !(level instanceof ServerLevel server)) return false;
        var registry = SquadRegistrySavedData.get(server.getServer());
        return registry.enabled() && registry.find(squad)
                .map(metadata -> player.getUUID().equals(metadata.createdBy())).orElse(false);
    }

    /** True only while this exact block entity remains reachable by the player. */
    public boolean canInteract(Player player) {
        return level != null && player.level() == level && !isRemoved()
                && !com.gamergaming.villagerguardgunners.quartermaster.service.ServiceTransactions.mutating(this)
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5,
                        worldPosition.getZ() + .5) <= 64
                && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this
                && canUse(player);
    }

    public boolean changeSetting(Player player, int button) {
        if (!canInteract(player) || !canManage(player)) return false;
        if (button == 3 && preservedService == null) guardAccess = (guardAccess + 1) % 4;
        else if (button == 4 && preservedService == null) {
            linkedArmories.clear(); linkedArmories.addAll(com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime.nearbyArmories(this));
        } else if (button == 5 && preservedService == null) linkedArmories.clear();
        else if (button == 0) access = (access + 1) % 3;
        else if (button == 1) automation = !automation;
        else if (button == 2) {
            if (squad != null) squad = null;
            else {
                if (!(level instanceof ServerLevel server)) return false;
                var registry = SquadRegistrySavedData.get(server.getServer());
                if (!registry.enabled()) return false;
                for (var hand : net.minecraft.world.InteractionHand.values()) {
                    ItemStack ledger = player.getItemInHand(hand);
                    if (!ledger.is(ModItems.SQUAD_COMMAND_LEDGER.get()) || ledger.getCount() != 1) continue;
                    var binding = SquadLedgerBindingCodec.readFrom(ledger.getTag());
                    if (binding.isValid() && registry.find(binding.binding().squadId()).isPresent()) {
                        squad = binding.binding().squadId();
                        break;
                    }
                }
                if (squad == null) {
                    player.displayClientMessage(Component.translatable("message.villagerguardgunners.quartermaster.ledger"), true);
                    return false;
                }
            }
        } else return false;
        setChanged();
        return true;
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.villagerguardgunners.quartermaster");
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return canInteract(player) ? new QuartermasterMenu(id, inv, this) : null;
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveService(tag);
        if (preserved != null) { tag.put(DATA_TAG, preserved.copy()); return; }
        CompoundTag data = new CompoundTag();
        data.putInt("Version", 1);
        data.putUUID("StationId", stationId);
        data.putUUID("DropId", dropId);
        if (owner != null) data.putUUID("Owner", owner);
        if (squad != null) data.putUUID("Squad", squad);
        data.putInt("Access", access);
        data.putBoolean("Automation", automation);
        data.put("Inventory", inventory.serializeNBT());
        tag.put(DATA_TAG, data);
    }

    @Override public void load(CompoundTag tag) {
        try {
            super.load(tag);
            owner = null; squad = null; access = PRIVATE; automation = false; released = false;
            stationId = UUID.randomUUID(); dropId = UUID.randomUUID();
            inventory.setSize(SLOTS);
            preserved = null;
            if (!tag.contains(DATA_TAG)) { loadService(tag); return; }
            preserved = tag.get(DATA_TAG).copy();
            if (!(preserved instanceof CompoundTag data)) return;
            // Retain unknown or malformed payloads verbatim; never turn them into empty stock.
            if (!data.contains("Version", Tag.TAG_INT) || data.getInt("Version") != 1
                    || !data.hasUUID("StationId") || !data.hasUUID("DropId") || !data.contains("Access", Tag.TAG_INT)
                    || data.getInt("Access") < PRIVATE || data.getInt("Access") > PUBLIC
                    || !data.contains("Automation", Tag.TAG_BYTE)
                    || (data.contains("Owner") && !data.hasUUID("Owner"))
                    || (data.contains("Squad") && !data.hasUUID("Squad"))
                    || !data.contains("Inventory", Tag.TAG_COMPOUND)) return;
            Set<String> known = Set.of("Version", "StationId", "DropId", "Owner", "Squad", "Access", "Automation", "Inventory");
            if (!known.containsAll(data.getAllKeys())) return;
            CompoundTag items = data.getCompound("Inventory");
            if (!items.contains("Size", Tag.TAG_INT) || items.getInt("Size") != SLOTS
                    || !items.contains("Items", Tag.TAG_LIST)
                    || !Set.of("Size", "Items").containsAll(items.getAllKeys())) return;
            var list = items.getList("Items", Tag.TAG_COMPOUND);
            if (list.size() != ((net.minecraft.nbt.ListTag) items.get("Items")).size()) return;
            Set<Integer> used = new HashSet<>();
            for (Tag entry : list) {
                CompoundTag item = (CompoundTag) entry;
                int slot = item.getInt("Slot");
                ItemStack stack = ItemStack.of(item);
                if (!item.contains("Slot", Tag.TAG_INT) || slot < 0 || slot >= SLOTS || !used.add(slot)
                        || stack.isEmpty() || stack.getCount() > Math.min(64, stack.getMaxStackSize())
                        || !stack.getItem().canFitInsideContainerItems()) return;
                CompoundTag roundTrip = stack.save(new CompoundTag());
                roundTrip.putInt("Slot", slot);
                if (!roundTrip.equals(item)) return;
            }
            inventory.deserializeNBT(items);
            owner = data.hasUUID("Owner") ? data.getUUID("Owner") : null;
            squad = data.hasUUID("Squad") ? data.getUUID("Squad") : null;
            stationId = data.getUUID("StationId");
            dropId = data.getUUID("DropId");
            access = data.getInt("Access");
            automation = data.getBoolean("Automation");
            preserved = null;
            loadService(tag);
        } finally {
            com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime.refreshIdentity(this);
        }
    }

    public ItemStack packedStack() {
        ItemStack packed = new ItemStack(ModItems.QUARTERMASTER.get());
        CompoundTag data = new CompoundTag();
        saveAdditional(data);
        // Packing retains just our storage payload; coordinates and capabilities are not portable.
        CompoundTag blockData = new CompoundTag();
        blockData.put(DATA_TAG, data.get(DATA_TAG).copy());
        saveService(blockData);
        packed.getOrCreateTag().put("BlockEntityTag", blockData);
        return packed;
    }

    public void releaseContents() {
        if (released || !(level instanceof ServerLevel server)) return;
        QuartermasterRecovery.get(server).enqueue(dropId, worldPosition, packedStack());
        released = true;
        invalidateCaps();
    }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !isRemoved()) return capability.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); capabilityGeneration++; capability.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); capability = LazyOptional.of(Transport::new); }

    private final class Transport implements IItemHandler {
        private final long generation = capabilityGeneration;
        private boolean allowed() { return generation == capabilityGeneration && automation && readable() && !released && !isRemoved()
                && !com.gamergaming.villagerguardgunners.quartermaster.service.ServiceTransactions.mutating(QuartermasterBlockEntity.this); }
        private boolean valid(int slot) { return slot >= 0 && slot < SLOTS; }
        @Override public int getSlots() { return SLOTS; }
        @Override public ItemStack getStackInSlot(int slot) {
            return allowed() && valid(slot) ? inventory.getStackInSlot(slot).copy() : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // Forge retains a fully accepted stack by reference; isolate the external caller's object.
            return isItemValid(slot, stack) ? inventory.insertItem(slot, stack.copy(), simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return allowed() && valid(slot) && slot >= STOCK_SLOTS
                    ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return valid(slot) ? 64 : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return allowed() && valid(slot) && slot < STOCK_SLOTS && inventory.isItemValid(slot, stack);
        }
    }
}
