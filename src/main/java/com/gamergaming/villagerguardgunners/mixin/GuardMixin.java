package com.gamergaming.villagerguardgunners.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardBurstSafetyAccess;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoMigration;
import com.tacz.guns.api.entity.IGunOperator;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.network.NetworkHooks;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.entities.Guard;

@Mixin(Guard.class)
public abstract class GuardMixin extends Mob implements GuardAmmoAccess, GuardBurstSafetyAccess {
    @Unique
    private static final String VILLAGER_GUARD_GUNNERS_AMMO_TAG = "villagerguardgunners:ammo_inventory";

    @Unique
    private static final String VILLAGER_GUARD_GUNNERS_OVERFLOW_TAG = "villagerguardgunners:ammo_overflow";

    @Unique
    private GuardAmmoHandler villagerGuardGunners$ammoHandler;

    @Unique
    private LazyOptional<IItemHandler> villagerGuardGunners$combinedItemHandler;

    @Unique
    private boolean villagerGuardGunners$itemCapabilityValid = true;

    @Unique
    private boolean villagerGuardGunners$nativeBurstSuppressed;

    @Unique
    @Nullable
    private BooleanSupplier villagerGuardGunners$nativeBurstSafetyCheck;

    @Unique
    private List<ItemStack> villagerGuardGunners$pendingMigrationDrops = new ArrayList<>();

    protected GuardMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeAddonState(EntityType<? extends Guard> entityType, Level level, CallbackInfo ci) {
        villagerGuardGunners$ensureAmmoStorage();
        if (!level.isClientSide) {
            IGunOperator.fromLivingEntity((Guard) (Object) this).initialData();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void flushMigrationOverflow(CallbackInfo ci) {
        if (!level().isClientSide && !villagerGuardGunners$pendingMigrationDrops.isEmpty()) {
            villagerGuardGunners$dropPendingMigrationItems();
        }
    }

    @Inject(method = "registerGoals", at = @At("RETURN"))
    private void addNativeGunGoal(CallbackInfo ci) {
        goalSelector.addGoal(2, new GuardGunCombatGoal((Guard) (Object) this));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void readAmmoStorage(CompoundTag compound, CallbackInfo ci) {
        villagerGuardGunners$ensureAmmoStorage();
        villagerGuardGunners$pendingMigrationDrops.clear();

        List<CompoundTag> legacyAmmo = GuardAmmoMigration.extractLegacyAmmoSlots(compound);
        List<ItemStack> savedOverflow = new ArrayList<>();
        ListTag serializedOverflow = compound.getList(VILLAGER_GUARD_GUNNERS_OVERFLOW_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < serializedOverflow.size(); index++) {
            ItemStack stack = ItemStack.of(serializedOverflow.getCompound(index));
            if (!stack.isEmpty()) {
                savedOverflow.add(stack);
            }
        }

        if (compound.contains(VILLAGER_GUARD_GUNNERS_AMMO_TAG, Tag.TAG_COMPOUND)) {
            List<ItemStack> rejectedAddonStacks = villagerGuardGunners$ammoHandler.loadSafely(
                    compound.getCompound(VILLAGER_GUARD_GUNNERS_AMMO_TAG));
            List<ItemStack> alreadyRestoredOverflow = new ArrayList<>(rejectedAddonStacks);
            alreadyRestoredOverflow.addAll(savedOverflow);
            villagerGuardGunners$pendingMigrationDrops.addAll(rejectedAddonStacks);
            villagerGuardGunners$pendingMigrationDrops.addAll(savedOverflow);
            villagerGuardGunners$pendingMigrationDrops.addAll(
                    villagerGuardGunners$ammoHandler.importLegacyStacksDeduplicated(
                            legacyAmmo,
                            alreadyRestoredOverflow));
        } else {
            villagerGuardGunners$ammoHandler.clear();
            villagerGuardGunners$pendingMigrationDrops.addAll(
                    villagerGuardGunners$ammoHandler.importLegacyStacks(legacyAmmo));
            villagerGuardGunners$pendingMigrationDrops.addAll(savedOverflow);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveAmmoStorage(CompoundTag compound, CallbackInfo ci) {
        villagerGuardGunners$ensureAmmoStorage();
        compound.put(VILLAGER_GUARD_GUNNERS_AMMO_TAG, villagerGuardGunners$ammoHandler.serializeNBT());

        if (villagerGuardGunners$pendingMigrationDrops.isEmpty()) {
            compound.remove(VILLAGER_GUARD_GUNNERS_OVERFLOW_TAG);
        } else {
            ListTag serializedOverflow = new ListTag();
            for (ItemStack stack : villagerGuardGunners$pendingMigrationDrops) {
                if (!stack.isEmpty()) {
                    serializedOverflow.add(stack.save(new CompoundTag()));
                }
            }
            compound.put(VILLAGER_GUARD_GUNNERS_OVERFLOW_TAG, serializedOverflow);
        }
    }

    @Inject(method = "openGui", at = @At("HEAD"), cancellable = true, remap = false)
    private void openGunnerMenu(ServerPlayer player, CallbackInfo ci) {
        villagerGuardGunners$ensureAmmoStorage();
        Guard guard = (Guard) (Object) this;

        // Guard#mobInteract has already performed Guard Villagers' authorization checks.
        guard.setOwnerId(player.getUUID());
        guard.interacting = true;
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (windowId, playerInventory, menuPlayer) -> new GuardGunnerContainer(
                                windowId,
                                playerInventory,
                                guard.guardInventory,
                                villagerGuardGunners$ammoHandler,
                                guard),
                        guard.getDisplayName()),
                buffer -> buffer.writeInt(guard.getId()));
        ci.cancel();
    }

    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true, remap = false)
    private <T> void exposeCombinedItemHandler(
            Capability<T> capability,
            @Nullable Direction facing,
            CallbackInfoReturnable<LazyOptional<T>> cir) {
        if (capability == ForgeCapabilities.ITEM_HANDLER
                && isAlive()
                && villagerGuardGunners$itemCapabilityValid) {
            villagerGuardGunners$ensureAmmoStorage();
            cir.setReturnValue(villagerGuardGunners$combinedItemHandler.cast());
        }
    }

    @Inject(method = "invalidateCaps", at = @At("TAIL"), remap = false)
    private void invalidateCombinedItemHandler(CallbackInfo ci) {
        villagerGuardGunners$itemCapabilityValid = false;
        if (villagerGuardGunners$combinedItemHandler != null) {
            villagerGuardGunners$combinedItemHandler.invalidate();
            villagerGuardGunners$combinedItemHandler = null;
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        villagerGuardGunners$itemCapabilityValid = true;
        villagerGuardGunners$ensureAmmoStorage();
    }

    @Inject(method = "dropCustomDeathLoot", at = @At("TAIL"))
    private void dropAmmoStorage(DamageSource source, int looting, boolean recentlyHit, CallbackInfo ci) {
        villagerGuardGunners$ensureAmmoStorage();
        villagerGuardGunners$dropPendingMigrationItems();

        for (int slot = 0; slot < villagerGuardGunners$ammoHandler.getSlots(); slot++) {
            ItemStack stack = villagerGuardGunners$ammoHandler.getStackInSlot(slot);
            if (!stack.isEmpty()
                    && !EnchantmentHelper.hasVanishingCurse(stack)
                    && getRandom().nextFloat() < GuardConfig.COMMON.chanceToDropEquipment.get()) {
                spawnAtLocation(stack.copy());
            }
        }
    }

    /*
     * Guard Villagers converts zombie-killed guards and lightning-struck guards
     * by discarding the original entity. Minecraft's normal death-loot path
     * refuses to run after that removal, and Mob#convertTo copies only vanilla
     * equipment slots. Preserve the isolated add-on storage immediately before
     * each conversion discard instead.
     */
    @Inject(
            method = "die",
            at = @At(
                    value = "INVOKE",
                    target = "Ltallestegg/guardvillagers/entities/Guard;discard()V"))
    private void dropAmmoBeforeZombieConversion(DamageSource source, CallbackInfo ci) {
        villagerGuardGunners$dropConversionItems();
    }

    @Inject(
            method = "thunderHit",
            at = @At(
                    value = "INVOKE",
                    target = "Ltallestegg/guardvillagers/entities/Guard;discard()V"))
    private void dropAmmoBeforeWitchConversion(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.LightningBolt lightning,
            CallbackInfo ci) {
        villagerGuardGunners$dropConversionItems();
    }

    @Override
    public GuardAmmoHandler villagerGuardGunners$getAmmoHandler() {
        villagerGuardGunners$ensureAmmoStorage();
        return villagerGuardGunners$ammoHandler;
    }

    @Override
    public boolean villagerGuardGunners$isNativeBurstSuppressed() {
        return villagerGuardGunners$nativeBurstSuppressed;
    }

    @Override
    public void villagerGuardGunners$setNativeBurstSuppressed(boolean suppressed) {
        villagerGuardGunners$nativeBurstSuppressed = suppressed;
    }

    @Override
    public boolean villagerGuardGunners$isNativeBurstRoundSafe() {
        return villagerGuardGunners$nativeBurstSafetyCheck == null
                || villagerGuardGunners$nativeBurstSafetyCheck.getAsBoolean();
    }

    @Override
    public void villagerGuardGunners$setNativeBurstSafetyCheck(BooleanSupplier safetyCheck) {
        villagerGuardGunners$nativeBurstSafetyCheck = safetyCheck;
    }

    @Override
    public void villagerGuardGunners$clearNativeBurstSafetyCheck() {
        villagerGuardGunners$nativeBurstSafetyCheck = null;
    }

    @Unique
    private void villagerGuardGunners$ensureAmmoStorage() {
        if (villagerGuardGunners$ammoHandler == null) {
            villagerGuardGunners$ammoHandler = new GuardAmmoHandler();
        }
        if (villagerGuardGunners$pendingMigrationDrops == null) {
            villagerGuardGunners$pendingMigrationDrops = new ArrayList<>();
        }
        if (villagerGuardGunners$itemCapabilityValid
                && villagerGuardGunners$combinedItemHandler == null) {
            Guard guard = (Guard) (Object) this;
            IItemHandler combined = new CombinedInvWrapper(
                    new InvWrapper(guard.guardInventory),
                    villagerGuardGunners$ammoHandler);
            villagerGuardGunners$combinedItemHandler = LazyOptional.of(() -> combined);
        }
    }

    @Unique
    private void villagerGuardGunners$dropPendingMigrationItems() {
        if (level().isClientSide || villagerGuardGunners$pendingMigrationDrops.isEmpty()) {
            return;
        }
        for (ItemStack stack : villagerGuardGunners$pendingMigrationDrops) {
            if (!stack.isEmpty()) {
                spawnAtLocation(stack.copy());
            }
        }
        villagerGuardGunners$pendingMigrationDrops.clear();
    }

    @Unique
    private void villagerGuardGunners$dropConversionItems() {
        if (level().isClientSide) {
            return;
        }

        villagerGuardGunners$ensureAmmoStorage();
        villagerGuardGunners$dropPendingMigrationItems();
        for (int slot = 0; slot < villagerGuardGunners$ammoHandler.getSlots(); slot++) {
            ItemStack stack = villagerGuardGunners$ammoHandler.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
    }
}
