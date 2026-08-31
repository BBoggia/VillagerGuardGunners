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
import com.gamergaming.villagerguardgunners.ai.GuardArmoryResupplyGoal;
import com.gamergaming.villagerguardgunners.ai.SquadCommandMovementGoal;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoDeposits;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardArmoryResupplyAccess;
import com.gamergaming.villagerguardgunners.api.GuardBurstSafetyAccess;
import com.gamergaming.villagerguardgunners.api.GuardNaturalLoadoutAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.api.GuardSidearmAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadCommandAccess;
import com.gamergaming.villagerguardgunners.entity.GuardGunnerContainer;
import com.gamergaming.villagerguardgunners.armory.resupply.GuardArmoryResupplyController;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoMigration;
import com.gamergaming.villagerguardgunners.orders.GuardOrderTemplateInteractions;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutCandidate;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutIssuance;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutProvenance;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutProvenanceCodec;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardPopulationIndex;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardLoadoutSelector;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnIntegration;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardSpawnSource;
import com.gamergaming.villagerguardgunners.sidearm.GuardSidearmStorage;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.gamergaming.villagerguardgunners.sidearm.SidearmStorageAccessKey;
import com.gamergaming.villagerguardgunners.squad.SquadMembership;
import com.gamergaming.villagerguardgunners.squad.SquadMembershipCodec;
import com.gamergaming.villagerguardgunners.squad.SquadLedgerInteractions;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import com.gamergaming.villagerguardgunners.squad.SquadCommandCodec;
import com.tacz.guns.api.entity.IGunOperator;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
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
public abstract class GuardMixin extends Mob implements GuardAmmoAccess, GuardArmoryResupplyAccess,
        GuardBurstSafetyAccess,
        GuardNaturalLoadoutAccess, GuardOrdersAccess, GuardSidearmAccess, GuardSquadAccess,
        GuardSquadCommandAccess {
    @Unique
    private static final String VILLAGER_GUARD_GUNNERS_AMMO_TAG = "villagerguardgunners:ammo_inventory";

    @Unique
    private static final String VILLAGER_GUARD_GUNNERS_OVERFLOW_TAG = "villagerguardgunners:ammo_overflow";

    @Unique
    private static final String VILLAGER_GUARD_GUNNERS_ORDERS_TAG = "villagerguardgunners:orders";

    @Unique
    private static final String VILLAGER_GUARD_GUNNERS_PENDING_NATURAL_SPAWN_TAG =
            "villagerguardgunners:pending_natural_loadout_spawn";

    @Unique
    private GuardAmmoHandler villagerGuardGunners$ammoHandler;

    @Unique
    private GuardSidearmStorage villagerGuardGunners$sidearmStorage;

    @Unique
    @Nullable
    private GuardArmoryResupplyController villagerGuardGunners$armoryResupplyController;

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

    @Unique
    private GuardOrders villagerGuardGunners$orders = GuardOrders.DEFAULT;

    @Unique
    private int villagerGuardGunners$ordersRevision;

    @Unique
    @Nullable
    private SquadMembership villagerGuardGunners$squadMembership;

    @Unique
    private int villagerGuardGunners$squadRevision;

    @Unique
    @Nullable
    private SquadCommand villagerGuardGunners$squadCommand;

    @Unique
    private int villagerGuardGunners$squadCommandRevision;

    @Unique
    @Nullable
    private volatile NaturalGuardSpawnSource villagerGuardGunners$pendingNaturalLoadoutSpawnSource;

    @Unique
    private volatile boolean villagerGuardGunners$naturalLoadoutSelectionAttempted;

    @Unique
    @Nullable
    private NaturalGuardLoadoutCandidate villagerGuardGunners$naturalLoadoutCandidate;

    @Unique
    @Nullable
    private NaturalGuardLoadoutProvenance villagerGuardGunners$naturalLoadoutProvenance;

    @Unique
    @Nullable
    private volatile NaturalGuardLoadoutSelector.DecisionStatus
            villagerGuardGunners$naturalLoadoutDecisionStatus;

    @Unique
    @Nullable
    private volatile NaturalGuardLoadoutIssuance.IssuanceStatus
            villagerGuardGunners$naturalLoadoutIssuanceStatus;

    protected GuardMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeAddonState(EntityType<? extends Guard> entityType, Level level, CallbackInfo ci) {
        villagerGuardGunners$ensureAmmoStorage();
        villagerGuardGunners$ensureSidearmStorage();
        if (!level.isClientSide) {
            IGunOperator.fromLivingEntity((Guard) (Object) this).initialData();
        }
    }

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void markNaturalLoadoutSpawnSource(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData spawnData,
            @Nullable CompoundTag spawnTag,
            CallbackInfoReturnable<SpawnGroupData> cir) {
        NaturalGuardSpawnIntegration.markInitialSpawn((Guard) (Object) this, spawnType);
    }

    /*
     * Guard Villagers deliberately postpones its sword/crossbow loot roll from
     * finalizeSpawn until the first AI step. Natural loadout eligibility must
     * observe that result, especially while replaceCrossbowsOnly is enabled.
     */
    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Ltallestegg/guardvillagers/entities/Guard;"
                            + "getItemsFromLootTable(Lnet/minecraft/world/entity/LivingEntity;)"
                            + "Ljava/util/List;",
                    shift = At.Shift.AFTER,
                    remap = false),
            require = 1)
    private void selectNaturalLoadoutAfterInitialEquipment(CallbackInfo ci) {
        NaturalGuardSpawnIntegration.onInitialEquipmentReady((Guard) (Object) this);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void flushMigrationOverflow(CallbackInfo ci) {
        if (!level().isClientSide && !villagerGuardGunners$pendingMigrationDrops.isEmpty()) {
            villagerGuardGunners$dropPendingMigrationItems();
        }
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void handleAddonHeldItemInteraction(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        InteractionHand squadLedgerHand =
                SquadLedgerInteractions.findLedgerHand(player, hand);
        if (squadLedgerHand != null) {
            cir.setReturnValue(SquadLedgerInteractions.interact(
                    player,
                    (Guard) (Object) this,
                    squadLedgerHand));
            return;
        }

        InteractionHand ledgerHand =
                GuardOrderTemplateInteractions.findLedgerHand(player, hand);
        if (ledgerHand != null) {
            cir.setReturnValue(GuardOrderTemplateInteractions.interact(
                    player,
                    (Guard) (Object) this,
                    ledgerHand));
            return;
        }

        ItemStack heldStack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !GuardAmmoHandler.isAmmo(heldStack)) {
            return;
        }
        if (level().isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        Guard guard = (Guard) (Object) this;
        GuardAmmoDeposits.DepositResult result =
                GuardAmmoDeposits.deposit(player, guard, hand);
        if (result.deposited()) {
            guard.playSound(SoundEvents.ITEM_PICKUP, 0.2F, 1.0F);
        }
        player.displayClientMessage(villagerGuardGunners$depositMessage(result), true);
        cir.setReturnValue(InteractionResult.CONSUME);
    }

    @Unique
    private static Component villagerGuardGunners$depositMessage(
            GuardAmmoDeposits.DepositResult result) {
        return switch (result.status()) {
            case DEPOSITED -> Component.translatable(
                    "message.villagerguardgunners.ammo_deposit.success",
                    result.inserted())
                    .withStyle(ChatFormatting.GREEN);
            case UNAUTHORIZED -> Component.translatable(
                    "message.villagerguardgunners.ammo_deposit.unauthorized")
                    .withStyle(ChatFormatting.RED);
            case INACTIVE_GUARD -> Component.translatable(
                    "message.villagerguardgunners.ammo_deposit.inactive_guard")
                    .withStyle(ChatFormatting.YELLOW);
            case NO_GUN -> Component.translatable(
                    "message.villagerguardgunners.ammo_deposit.no_gun")
                    .withStyle(ChatFormatting.YELLOW);
            case UNREADABLE_GUN -> Component.translatable(
                    "message.villagerguardgunners.ammo_deposit.unreadable_gun")
                    .withStyle(ChatFormatting.YELLOW);
            case GUN_DOES_NOT_USE_PHYSICAL_AMMO -> Component.translatable(
                    "message.villagerguardgunners.ammo_deposit.physical_ammo_unused")
                    .withStyle(ChatFormatting.YELLOW);
            case INCOMPATIBLE_AMMUNITION -> Component.translatable(
                    "message.villagerguardgunners.ammo_deposit.incompatible")
                    .withStyle(ChatFormatting.YELLOW);
            case UNREADABLE_AMMUNITION -> Component.translatable(
                    "message.villagerguardgunners.ammo_deposit.unreadable")
                    .withStyle(ChatFormatting.YELLOW);
            case INVENTORY_FULL -> Component.translatable(
                    "message.villagerguardgunners.ammo_deposit.full")
                    .withStyle(ChatFormatting.YELLOW);
            case TRANSACTION_FAILED, INVALID_REQUEST, NOT_AMMUNITION -> Component.translatable(
                    "message.villagerguardgunners.ammo_deposit.failed")
                    .withStyle(ChatFormatting.RED);
        };
    }

    @Inject(method = "registerGoals", at = @At("RETURN"))
    private void addNativeGunGoal(CallbackInfo ci) {
        goalSelector.addGoal(1, new SquadCommandMovementGoal((Guard) (Object) this));
        goalSelector.addGoal(2, new GuardGunCombatGoal((Guard) (Object) this));
        villagerGuardGunners$armoryResupplyController =
                new GuardArmoryResupplyController((Guard) (Object) this);
        goalSelector.addGoal(
                3,
                new GuardArmoryResupplyGoal(villagerGuardGunners$armoryResupplyController));
    }

    @Override
    @Nullable
    public GuardArmoryResupplyController
            villagerGuardGunners$getArmoryResupplyController() {
        return villagerGuardGunners$armoryResupplyController;
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void readAmmoStorage(CompoundTag compound, CallbackInfo ci) {
        villagerGuardGunners$ensureAmmoStorage();
        villagerGuardGunners$ensureSidearmStorage();
        villagerGuardGunners$sidearmStorage.loadFrom(compound);
        villagerGuardGunners$readPendingNaturalLoadoutSpawn(compound);
        villagerGuardGunners$setOrders(compound.contains(
                VILLAGER_GUARD_GUNNERS_ORDERS_TAG,
                Tag.TAG_COMPOUND)
                ? GuardOrders.load(compound.getCompound(VILLAGER_GUARD_GUNNERS_ORDERS_TAG))
                : GuardOrders.DEFAULT);
        villagerGuardGunners$setSquadMembership(
                SquadMembershipCodec.readGuardRecord(compound));
        villagerGuardGunners$setSquadCommand(
                SquadCommandCodec.readGuardRecord(compound));
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

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void authenticateNaturalLoadoutAfterRead(CompoundTag compound, CallbackInfo ci) {
        // Vanilla equipment is not available at HEAD. Authenticate only after
        // Guard Villagers has restored the main-hand item.
        villagerGuardGunners$sidearmStorage.authenticateActiveSidearm(getMainHandItem());
        villagerGuardGunners$reconcileNaturalLoadoutProvenance();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void authenticateActiveSidearmBeforeEquipmentSave(
            CompoundTag compound,
            CallbackInfo ci) {
        // Vanilla serializes HandItems before the TAIL injection writes our
        // private record. Bind legacy active layouts first so both halves save
        // the same ownership token.
        villagerGuardGunners$ensureSidearmStorage();
        villagerGuardGunners$sidearmStorage.authenticateActiveSidearm(
                getMainHandItem());
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveAmmoStorage(CompoundTag compound, CallbackInfo ci) {
        villagerGuardGunners$ensureAmmoStorage();
        villagerGuardGunners$ensureSidearmStorage();
        compound.put(VILLAGER_GUARD_GUNNERS_AMMO_TAG, villagerGuardGunners$ammoHandler.serializeNBT());
        villagerGuardGunners$sidearmStorage.saveTo(compound, getMainHandItem());

        // Do not preserve a stale identity after a player or another mod has
        // removed/replaced the naturally issued main-hand gun.
        villagerGuardGunners$reconcileNaturalLoadoutProvenance();
        NaturalGuardLoadoutProvenanceCodec.writeGuardRecord(
                compound,
                villagerGuardGunners$naturalLoadoutProvenance);
        NaturalGuardPopulationIndex.synchronize((Guard) (Object) this);

        NaturalGuardSpawnSource pendingSpawnSource =
                villagerGuardGunners$pendingNaturalLoadoutSpawnSource;
        if (!villagerGuardGunners$naturalLoadoutSelectionAttempted
                && pendingSpawnSource != null) {
            compound.putString(
                    VILLAGER_GUARD_GUNNERS_PENDING_NATURAL_SPAWN_TAG,
                    pendingSpawnSource.name());
        } else {
            compound.remove(VILLAGER_GUARD_GUNNERS_PENDING_NATURAL_SPAWN_TAG);
        }

        GuardOrders orders = villagerGuardGunners$getOrders();
        if (orders.equals(GuardOrders.DEFAULT)) {
            compound.remove(VILLAGER_GUARD_GUNNERS_ORDERS_TAG);
        } else {
            compound.put(VILLAGER_GUARD_GUNNERS_ORDERS_TAG, orders.save());
        }
        SquadMembershipCodec.writeGuardRecord(
                compound,
                villagerGuardGunners$getSquadMembership());
        SquadCommandCodec.writeGuardRecord(
                compound,
                villagerGuardGunners$getSquadCommand());

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
    private void dropAddonStorage(DamageSource source, int looting, boolean recentlyHit, CallbackInfo ci) {
        villagerGuardGunners$ensureAmmoStorage();
        villagerGuardGunners$ensureSidearmStorage();
        villagerGuardGunners$dropPendingMigrationItems();

        for (int slot = 0; slot < villagerGuardGunners$ammoHandler.getSlots(); slot++) {
            villagerGuardGunners$dropStoredItemByChance(
                    villagerGuardGunners$ammoHandler.getStackInSlot(slot));
        }
        for (ItemStack stack : villagerGuardGunners$sidearmStorage.recoverableStacks()) {
            villagerGuardGunners$dropStoredItemByChance(stack);
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
    public GuardSidearmStorage villagerGuardGunners$getSidearmStorage(
            SidearmStorageAccessKey accessKey) {
        if (!SidearmStorageAccessKey.authorizes(accessKey)) {
            throw new SecurityException("Complete sidearm storage is internal");
        }
        villagerGuardGunners$ensureSidearmStorage();
        return villagerGuardGunners$sidearmStorage;
    }

    @Override
    public IItemHandler villagerGuardGunners$getConfiguredSidearmInventory() {
        villagerGuardGunners$ensureSidearmStorage();
        return villagerGuardGunners$sidearmStorage.configuredSidearmView();
    }

    @Override
    public SidearmState villagerGuardGunners$getSidearmState() {
        villagerGuardGunners$ensureSidearmStorage();
        return villagerGuardGunners$sidearmStorage.stateAgainst(getMainHandItem());
    }

    @Override
    public ItemStack villagerGuardGunners$getEffectivePrimaryCopy() {
        villagerGuardGunners$ensureSidearmStorage();
        SidearmState state = villagerGuardGunners$sidearmStorage.stateAgainst(
                getMainHandItem());
        if (state == SidearmState.RECOVERY_REQUIRED) {
            return ItemStack.EMPTY;
        }
        return (state == SidearmState.SIDEARM_ACTIVE
                ? villagerGuardGunners$sidearmStorage.getStackInSlot(
                        GuardSidearmStorage.STOWED_PRIMARY_SLOT)
                : getMainHandItem()).copy();
    }

    @Override
    public GuardOrders villagerGuardGunners$getOrders() {
        if (villagerGuardGunners$orders == null) {
            villagerGuardGunners$orders = GuardOrders.DEFAULT;
        }
        return villagerGuardGunners$orders;
    }

    @Override
    public void villagerGuardGunners$setOrders(GuardOrders orders) {
        GuardOrders normalized = orders == null ? GuardOrders.DEFAULT : orders;
        if (!normalized.equals(villagerGuardGunners$getOrders())) {
            villagerGuardGunners$orders = normalized;
            villagerGuardGunners$ordersRevision++;
        }
    }

    @Override
    public int villagerGuardGunners$getOrdersRevision() {
        return villagerGuardGunners$ordersRevision;
    }

    @Override
    @Nullable
    public SquadMembership villagerGuardGunners$getSquadMembership() {
        return villagerGuardGunners$squadMembership;
    }

    @Override
    public void villagerGuardGunners$setSquadMembership(
            @Nullable SquadMembership membership) {
        if (!java.util.Objects.equals(
                membership,
                villagerGuardGunners$squadMembership)) {
            villagerGuardGunners$squadMembership = membership;
            villagerGuardGunners$squadRevision++;
        }
    }

    @Override
    public int villagerGuardGunners$getSquadRevision() {
        return villagerGuardGunners$squadRevision;
    }

    @Override
    @Nullable
    public SquadCommand villagerGuardGunners$getSquadCommand() {
        return villagerGuardGunners$squadCommand;
    }

    @Override
    public void villagerGuardGunners$setSquadCommand(
            @Nullable SquadCommand command) {
        if (!java.util.Objects.equals(
                command,
                villagerGuardGunners$squadCommand)) {
            villagerGuardGunners$squadCommand = command;
            villagerGuardGunners$squadCommandRevision++;
        }
    }

    @Override
    public int villagerGuardGunners$getSquadCommandRevision() {
        return villagerGuardGunners$squadCommandRevision;
    }

    @Override
    public synchronized void villagerGuardGunners$markNaturalLoadoutSpawnSource(
            NaturalGuardSpawnSource source) {
        if (!villagerGuardGunners$naturalLoadoutSelectionAttempted
                && villagerGuardGunners$pendingNaturalLoadoutSpawnSource == null) {
            villagerGuardGunners$pendingNaturalLoadoutSpawnSource = source;
        }
    }

    @Override
    @Nullable
    public synchronized NaturalGuardSpawnSource
            villagerGuardGunners$claimNaturalLoadoutSpawnSource() {
        if (villagerGuardGunners$naturalLoadoutSelectionAttempted
                || villagerGuardGunners$pendingNaturalLoadoutSpawnSource == null) {
            return null;
        }
        NaturalGuardSpawnSource source = villagerGuardGunners$pendingNaturalLoadoutSpawnSource;
        villagerGuardGunners$pendingNaturalLoadoutSpawnSource = null;
        villagerGuardGunners$naturalLoadoutSelectionAttempted = true;
        return source;
    }

    @Override
    public boolean villagerGuardGunners$hasNaturalLoadoutSelectionAttempted() {
        return villagerGuardGunners$naturalLoadoutSelectionAttempted;
    }

    @Override
    @Nullable
    public NaturalGuardLoadoutSelector.DecisionStatus
            villagerGuardGunners$getNaturalLoadoutDecisionStatus() {
        return villagerGuardGunners$naturalLoadoutDecisionStatus;
    }

    @Override
    public void villagerGuardGunners$setNaturalLoadoutDecisionStatus(
            NaturalGuardLoadoutSelector.DecisionStatus status) {
        villagerGuardGunners$naturalLoadoutDecisionStatus = status;
    }

    @Override
    @Nullable
    public NaturalGuardLoadoutCandidate villagerGuardGunners$getNaturalLoadoutCandidate() {
        return villagerGuardGunners$naturalLoadoutCandidate;
    }

    @Override
    public void villagerGuardGunners$setNaturalLoadoutCandidate(
            @Nullable NaturalGuardLoadoutCandidate candidate) {
        villagerGuardGunners$naturalLoadoutCandidate = candidate;
    }

    @Override
    @Nullable
    public NaturalGuardLoadoutProvenance villagerGuardGunners$getNaturalLoadoutProvenance() {
        return villagerGuardGunners$naturalLoadoutProvenance;
    }

    @Override
    public void villagerGuardGunners$setNaturalLoadoutProvenance(
            @Nullable NaturalGuardLoadoutProvenance provenance) {
        villagerGuardGunners$naturalLoadoutProvenance = provenance;
        if (provenance != null) {
            villagerGuardGunners$naturalLoadoutSelectionAttempted = true;
        } else {
            villagerGuardGunners$naturalLoadoutCandidate = null;
            NaturalGuardPopulationIndex.remove((Guard) (Object) this);
        }
    }

    @Override
    @Nullable
    public NaturalGuardLoadoutIssuance.IssuanceStatus
            villagerGuardGunners$getNaturalLoadoutIssuanceStatus() {
        return villagerGuardGunners$naturalLoadoutIssuanceStatus;
    }

    @Override
    public void villagerGuardGunners$setNaturalLoadoutIssuanceStatus(
            NaturalGuardLoadoutIssuance.IssuanceStatus status) {
        villagerGuardGunners$naturalLoadoutIssuanceStatus = status;
    }

    @Unique
    private void villagerGuardGunners$readPendingNaturalLoadoutSpawn(CompoundTag compound) {
        villagerGuardGunners$pendingNaturalLoadoutSpawnSource = null;
        villagerGuardGunners$naturalLoadoutSelectionAttempted = false;
        villagerGuardGunners$naturalLoadoutCandidate = null;
        villagerGuardGunners$naturalLoadoutProvenance =
                NaturalGuardLoadoutProvenanceCodec.readGuardRecord(compound);
        villagerGuardGunners$naturalLoadoutDecisionStatus = null;
        villagerGuardGunners$naturalLoadoutIssuanceStatus = null;
        if (!compound.contains(
                VILLAGER_GUARD_GUNNERS_PENDING_NATURAL_SPAWN_TAG,
                Tag.TAG_STRING)) {
            return;
        }
        try {
            villagerGuardGunners$pendingNaturalLoadoutSpawnSource =
                    NaturalGuardSpawnSource.valueOf(compound.getString(
                            VILLAGER_GUARD_GUNNERS_PENDING_NATURAL_SPAWN_TAG));
        } catch (IllegalArgumentException ignored) {
            // Unknown/future source values are not eligible for automatic equipment.
        }
    }

    @Unique
    private void villagerGuardGunners$reconcileNaturalLoadoutProvenance() {
        NaturalGuardLoadoutProvenance provenance =
                villagerGuardGunners$naturalLoadoutProvenance;
        Guard guard = (Guard) (Object) this;
        if (NaturalGuardLoadoutProvenanceCodec.matchesIssuedGun(
                guard,
                provenance)) {
            villagerGuardGunners$naturalLoadoutSelectionAttempted = true;
            villagerGuardGunners$naturalLoadoutCandidate =
                    new NaturalGuardLoadoutCandidate(
                            provenance.poolId(),
                            provenance.gunId(),
                            provenance.difficulty(),
                            provenance.spawnSource());
            return;
        }
        villagerGuardGunners$naturalLoadoutProvenance = null;
        villagerGuardGunners$naturalLoadoutCandidate = null;
        NaturalGuardPopulationIndex.remove(guard);
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
    private void villagerGuardGunners$ensureSidearmStorage() {
        if (villagerGuardGunners$sidearmStorage == null) {
            villagerGuardGunners$sidearmStorage = new GuardSidearmStorage();
        }
    }

    @Unique
    private void villagerGuardGunners$dropStoredItemByChance(ItemStack stack) {
        if (!stack.isEmpty()
                && !EnchantmentHelper.hasVanishingCurse(stack)
                && getRandom().nextFloat() < GuardConfig.COMMON.chanceToDropEquipment.get()) {
            spawnAtLocation(stack.copy());
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
        villagerGuardGunners$ensureSidearmStorage();
        villagerGuardGunners$dropPendingMigrationItems();
        for (int slot = 0; slot < villagerGuardGunners$ammoHandler.getSlots(); slot++) {
            ItemStack stack = villagerGuardGunners$ammoHandler.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
        for (ItemStack stack : villagerGuardGunners$sidearmStorage.drainRecoverableStacks()) {
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
    }
}
