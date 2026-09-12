package com.gamergaming.villagerguardgunners.entity;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoMaintenance;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.api.GuardMedicalAccess;
import com.gamergaming.villagerguardgunners.medical.MedicalTreatment;
import com.gamergaming.villagerguardgunners.medical.TreatmentProfiles;
import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.combat.GuardCombatStatus;
import com.gamergaming.villagerguardgunners.init.ModMenus;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.OrderField;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import com.gamergaming.villagerguardgunners.sidearm.SidearmRuntimeAssessment;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmRecovery;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.tacz.guns.api.item.IGun;
import com.mojang.datafixers.util.Pair;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import tallestegg.guardvillagers.GuardVillagers;
import tallestegg.guardvillagers.entities.Guard;

public class GuardGunnerContainer extends AbstractContainerMenu {
    public static final int GUARD_SLOT_COUNT = 6;
    public static final int AMMO_SLOT_COUNT = GuardAmmoHandler.SLOT_COUNT;
    public static final int SIDEARM_SLOT_INDEX = GUARD_SLOT_COUNT + AMMO_SLOT_COUNT;
    public static final int MEDICAL_SLOT_START = SIDEARM_SLOT_INDEX + 1;
    public static final int MEDICAL_SLOT_COUNT = 3;
    public static final int ADDON_SLOT_COUNT = MEDICAL_SLOT_START + MEDICAL_SLOT_COUNT;
    public static final int TREAT_BUTTON_ID = 200;
    public static final int PACK_MEDICAL_BUTTON_ID = 201;
    public static final int RECOVER_SIDEARM_BUTTON_ID = 100;

    private static final int AMMO_SLOT_END = GUARD_SLOT_COUNT + AMMO_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = ADDON_SLOT_COUNT;
    private static final int PLAYER_HOTBAR_START = PLAYER_INVENTORY_START + 27;
    private static final int PLAYER_SLOT_END = PLAYER_HOTBAR_START + 9;

    private int medicFlags, medicStatus;
    public int medicFlags() {
        var state = com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(guard);
        return guard.level().isClientSide ? medicFlags : (state.medic() ? 1 : 0) | (state.retreat() ? 2 : 0);
    }
    public int medicStatus() { return guard.level().isClientSide ? medicStatus : com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(guard).status.ordinal(); }
    private int servicePolicy, serviceStatus, serviceReplace;
    private final char[] serviceProfile = new char[96];
    public int servicePolicy() { return guard.level().isClientSide ? servicePolicy : com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).policy().ordinal(); }
    public int serviceStatus() { var state = com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard); return guard.level().isClientSide ? serviceStatus : (state.readable() ? state.status : com.gamergaming.villagerguardgunners.quartermaster.service.ServiceStatus.RECOVERY_REQUIRED).ordinal(); }
    public boolean serviceReplace() { return guard.level().isClientSide ? serviceReplace != 0 : com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).replaceExisting(); }
    public String serviceProfile() {
        if (guard.level().isClientSide) return new String(serviceProfile).replace("\u0000", "");
        var selected = com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).profile();
        return selected == null ? "" : selected.toString();
    }

    private final Container guardInventory;
    private final GuardAmmoHandler ammoHandler;
    private final Guard guard;
    @Nullable
    private final GuardGunCombatGoal statusGoal;
    private int synchronizedCombatStatusId = GuardCombatStatus.UNKNOWN.networkId();
    private int synchronizedSidearmStateId = SidearmState.PRIMARY_ACTIVE.networkId();
    private GuardOrders synchronizedOrders = GuardOrders.DEFAULT;

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
        this.statusGoal = guard.level().isClientSide ? null : findGunGoal(guard);
        guardInventory.startOpen(playerInventory.player);

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (!guard.level().isClientSide) {
                    GuardCombatStatus status = statusGoal == null
                            ? GuardCombatStatus.UNKNOWN
                            : statusGoal.getPlayerFacingStatus();
                    return status.networkId();
                }
                return synchronizedCombatStatusId;
            }

            @Override
            public void set(int value) {
                synchronizedCombatStatusId = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return guard.level().isClientSide
                        ? synchronizedSidearmStateId
                        : GuardSidearmAccess.getState(guard).networkId();
            }

            @Override
            public void set(int value) {
                synchronizedSidearmStateId = value;
            }
        });
        for (OrderField field : OrderField.values()) {
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return guard.level().isClientSide
                            ? synchronizedOrders.networkValue(field)
                            : GuardOrdersAccess.getOrders(guard).networkValue(field);
                }

                @Override
                public void set(int value) {
                    synchronizedOrders = synchronizedOrders.withNetworkValue(field, value);
                }
            });
        }

        addDataSlot(new DataSlot() {
            @Override public int get() { return medicFlags(); }
            @Override public void set(int value) { medicFlags = value; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return medicStatus(); }
            @Override public void set(int value) { medicStatus = value; }
        });
        for (int index = 0; index < 99; index++) {
            final int field = index;
            addDataSlot(new DataSlot() {
                @Override public int get() {
                    var service = com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard);
                    if (field == 0) return service.policy().ordinal();
                    if (field == 1) return service.readable() ? service.status.ordinal() : com.gamergaming.villagerguardgunners.quartermaster.service.ServiceStatus.RECOVERY_REQUIRED.ordinal();
                    if (field == 2) return service.replaceExisting() ? 1 : 0;
                    String id = service.profile() == null ? "" : service.profile().toString();
                    return field - 3 < id.length() ? id.charAt(field - 3) : 0;
                }
                @Override public void set(int value) {
                    if (field == 0) servicePolicy = value;
                    else if (field == 1) serviceStatus = value;
                    else if (field == 2) serviceReplace = value;
                    else serviceProfile[field - 3] = (char) value;
                }
            });
        }

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
                return mayEditGuard(playerInventory.player)
                        && mayConfigureMainHand();
            }

            @Override
            public void set(ItemStack stack) {
                super.set(stack);
                guard.setItemSlot(EquipmentSlot.MAINHAND, stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return mayEditGuard(player) && mayConfigureMainHand();
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

        IItemHandler configuredSidearm = GuardSidearmAccess.getConfiguredSidearmInventory(guard);
        addSlot(new SlotItemHandler(configuredSidearm, 0, 153, 44) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                if (!mayEditGuard(playerInventory.player)
                        || stack == null
                        || stack.isEmpty()
                        || stack.getCount() != 1
                        || !(stack.getItem() instanceof IGun)) {
                    return false;
                }
                // Client prediction remains structural. The authoritative
                // server performs the complete non-explosive pistol check.
                return guard.level().isClientSide
                        || SidearmRuntimeAssessment.assessEligibility(guard, stack).eligible();
            }

            @Override
            public boolean mayPickup(Player player) {
                return mayEditGuard(player)
                        && getSidearmState() == SidearmState.PRIMARY_ACTIVE;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            /**
             * SlotItemHandler#set assumes IItemHandlerModifiable. The public
             * sidearm view deliberately is not modifiable, so route menu writes
             * through its guarded extract/insert operations instead.
             */
            @Override
            public void set(ItemStack stack) {
                replaceConfiguredSidearm(configuredSidearm, stack);
                setChanged();
            }

            @Override
            public void initialize(ItemStack stack) {
                set(stack);
            }
        });

        var medical = GuardMedicalAccess.get(guard);
        for (int slot = 0; slot < MEDICAL_SLOT_COUNT; slot++) {
            addSlot(new SlotItemHandler(medical.inventory(), slot, 8 + slot * 18, 184) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return medical.available() && stillValid(playerInventory.player) && mayEditGuard(playerInventory.player) && super.mayPlace(stack);
                }
                @Override public boolean mayPickup(Player player) {
                    return medical.available() && stillValid(player) && mayEditGuard(player);
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
        return !com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard) && GuardVillagers.hotvChecker(player, guard);
    }

    private boolean mayConfigureMainHand() {
        return getSidearmState() == SidearmState.PRIMARY_ACTIVE;
    }

    private static void replaceConfiguredSidearm(
            IItemHandler configuredSidearm,
            ItemStack requestedStack) {
        ItemStack replacement = requestedStack == null
                ? ItemStack.EMPTY
                : requestedStack.copy();
        ItemStack existing = configuredSidearm.getStackInSlot(0);
        if (stacksMatch(existing, replacement)) {
            return;
        }

        ItemStack extracted = ItemStack.EMPTY;
        if (!existing.isEmpty()) {
            extracted = configuredSidearm.extractItem(0, existing.getCount(), false);
            if (!stacksMatch(existing, extracted)) {
                throw new IllegalStateException("Configured sidearm changed during menu update");
            }
        }

        if (replacement.isEmpty()) {
            return;
        }
        ItemStack remainder = configuredSidearm.insertItem(0, replacement, false);
        if (remainder.isEmpty()) {
            return;
        }

        // The server menu is single-threaded, but preserve the displaced weapon
        // if a third-party handler changes state during the same click.
        if (!extracted.isEmpty()) {
            ItemStack restoreRemainder = configuredSidearm.insertItem(0, extracted, false);
            if (!restoreRemainder.isEmpty()) {
                throw new IllegalStateException("Could not restore displaced configured sidearm");
            }
        }
        throw new IllegalStateException("Configured sidearm rejected an authorized menu update");
    }

    private static boolean stacksMatch(ItemStack first, ItemStack second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameTags(first, second);
    }

    public Guard getGuard() {
        return guard;
    }

    public GuardAmmoHandler getAmmoHandler() {
        return ammoHandler;
    }

    public GuardCombatStatus getCombatStatus() {
        if (getSidearmState() == SidearmState.RECOVERY_REQUIRED) {
            return GuardCombatStatus.RECOVERY_REQUIRED;
        }
        return GuardCombatStatus.fromNetworkId(synchronizedCombatStatusId);
    }

    public SidearmState getSidearmState() {
        return guard.level().isClientSide
                ? SidearmState.fromNetworkId(synchronizedSidearmStateId)
                : GuardSidearmAccess.getState(guard);
    }

    public GuardOrders getOrders() {
        return guard.level().isClientSide
                ? synchronizedOrders
                : GuardOrdersAccess.getOrders(guard);
    }

    @Nullable
    private static GuardGunCombatGoal findGunGoal(Guard guard) {
        return guard.goalSelector.getAvailableGoals().stream()
                .map(wrappedGoal -> wrappedGoal.getGoal())
                .filter(GuardGunCombatGoal.class::isInstance)
                .map(GuardGunCombatGoal.class::cast)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean stillValid(Player player) {
        return !com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard) && player.level() == guard.level() && !player.isSpectator() && player.isAlive() && !guard.isRemoved()
                && guardInventory.stillValid(player) && guard.isAlive() && guard.distanceTo(player) < 8.0F;
    }

    @Override
    public void clicked(int slot, int button, net.minecraft.world.inventory.ClickType type, Player player) {
        if (stillValid(player) && !GuardMedicalAccess.get(guard).busy()
                && com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).readable()
                && !com.gamergaming.villagerguardgunners.quartermaster.service.ServiceTransactions.mutating(guard)) super.clicked(slot, button, type, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == 310 || buttonId == 311) {
            if (!stillValid(player)) return false;
            boolean changed = com.gamergaming.villagerguardgunners.medical.MedicControls.change(player, guard, buttonId);
            broadcastChanges(); return changed;
        }
        if (buttonId >= 300 && buttonId <= 302) {
            if (!stillValid(player)) return false;
            boolean changed = com.gamergaming.villagerguardgunners.quartermaster.service.ServiceControls.change(player, guard, buttonId);
            broadcastChanges(); return changed;
        }
        if (buttonId == TREAT_BUTTON_ID || buttonId == PACK_MEDICAL_BUTTON_ID) {
            if (player.level().isClientSide || !stillValid(player) || !mayEditGuard(player)) return false;
            var result = buttonId == TREAT_BUTTON_ID ? MedicalTreatment.fromPouch(player, guard) : MedicalTreatment.pack(player, guard);
            MedicalTreatment.report(player, result);
            broadcastChanges();
            return result == MedicalTreatment.Result.HEALED || result == MedicalTreatment.Result.PACKED;
        }
        if (buttonId == RECOVER_SIDEARM_BUTTON_ID) {
            if (player.level().isClientSide
                    || !stillValid(player)
                    || !mayEditGuard(player)) {
                return false;
            }
            GuardSidearmRecovery.RecoveryResult recovery =
                    GuardSidearmRecovery.recover(guard, player);
            if (!recovery.recovered()) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "container.villagerguardgunners.sidearm.recover.failed"),
                        true);
                return false;
            }
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "container.villagerguardgunners.sidearm.recover.success",
                            recovery.recoveredStackCount()),
                    true);
            broadcastChanges();
            return true;
        }
        OrderField field = OrderField.fromButtonId(buttonId);
        if (field == null
                || player.level().isClientSide
                || !stillValid(player)
                || !GuardOrderPermissions.mayManage(player, guard)) {
            return false;
        }

        GuardOrders current = GuardOrdersAccess.getOrders(guard);
        if (!GuardOrdersAccess.setOrders(guard, current.cycle(field))) {
            return false;
        }
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || GuardMedicalAccess.get(guard).busy()
                || !com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess.get(guard).readable()
                || com.gamergaming.villagerguardgunners.quartermaster.service.ServiceTransactions.mutating(guard) || index < 0 || index >= slots.size()) {
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
                : moveFromPlayerInventory(player, sourceStack, index);

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

    private boolean moveFromPlayerInventory(Player player, ItemStack stack, int sourceIndex) {
        if (TreatmentProfiles.resolve(stack) != null && mayEditGuard(player)
                && GuardMedicalAccess.get(guard).available()
                && moveItemStackTo(stack, MEDICAL_SLOT_START, ADDON_SLOT_COUNT, false)) return true;
        if (GuardAmmoHandler.isAmmo(stack) && mayEditGuard(player)) {
            GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard);
            if (moveItemStackTo(stack, GUARD_SLOT_COUNT, AMMO_SLOT_END, false)) {
                return true;
            }
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
        if (!getSlot(SIDEARM_SLOT_INDEX).hasItem()
                && getSlot(SIDEARM_SLOT_INDEX).mayPlace(stack)
                && moveItemStackTo(
                        stack,
                        SIDEARM_SLOT_INDEX,
                        SIDEARM_SLOT_INDEX + 1,
                        false)) {
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
