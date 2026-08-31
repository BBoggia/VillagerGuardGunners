package com.gamergaming.villagerguardgunners.sidearm;

import com.tacz.guns.api.item.IGun;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Private, fixed-size weapon storage owned by one Guard Villager.
 *
 * <p>This handler is intentionally not exposed through the guard's Forge item
 * capability. Its first slot is the configured sidearm; its second slot is a
 * recovery-only home for the primary while the sidearm is active.</p>
 */
public final class GuardSidearmStorage extends ItemStackHandler {
    public static final String STORAGE_TAG = "villagerguardgunners:sidearm_loadout";
    public static final int CURRENT_SCHEMA_VERSION = 3;
    public static final int SIDEARM_SLOT = 0;
    public static final int STOWED_PRIMARY_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    private static final String VERSION_TAG = "Version";
    private static final String ACTIVE_TAG = "SidearmActive";
    private static final String SIDEARM_TAG = "Sidearm";
    private static final String STOWED_PRIMARY_TAG = "StowedPrimary";
    private static final String ACTIVATION_REASON_TAG = "ActivationReason";
    private static final String ACTIVATED_AT_TAG = "ActivatedAt";
    private static final String TARGET_TAG = "Target";
    private static final String PRIMARY_STANDOFF_TAG = "PrimaryStandoff";
    private static final String ACTIVE_TOKEN_TAG = "ActiveSidearmToken";
    static final String STACK_TOKEN_TAG = "villagerguardgunners:active_sidearm_token";
    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Set<String> BASE_KEYS = Set.of(VERSION_TAG, ACTIVE_TAG);

    private boolean internalMutation;
    private int contentRevision;
    @Nullable
    private Tag unreadableRecord;
    private LoadStatus loadStatus = LoadStatus.BLANK;
    private int storedVersion = -1;
    private boolean sidearmActive;
    private SidearmActivationReason activationReason = SidearmActivationReason.NONE;
    private long activatedAtGameTick = Long.MIN_VALUE;
    private double primaryStandoffDistance;
    @Nullable
    private UUID activationTargetId;
    @Nullable
    private UUID activeSidearmToken;
    private final IItemHandler configuredSidearmView = new ConfiguredSidearmView();

    public GuardSidearmStorage() {
        super(SLOT_COUNT);
    }

    /**
     * Returns the only externally usable inventory surface. It maps one slot to
     * the configured sidearm, returns defensive copies, and becomes read-only
     * while a transaction, active fallback, or recovery record owns the slots.
     */
    public IItemHandler configuredSidearmView() {
        return configuredSidearmView;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0
                && slot < SLOT_COUNT
                && stack != null
                && !stack.isEmpty()
                && stack.getCount() == 1
                && stack.getItem() instanceof IGun;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        ensureWritable();
        if (!stack.isEmpty() && !isItemValid(slot, stack)) {
            throw new IllegalArgumentException(
                    "Private sidearm storage accepts one TaCZ gun per slot");
        }
        super.setStackInSlot(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        Objects.requireNonNull(stack, "stack");
        if (!isWritable()) {
            return stack.copy();
        }
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!isWritable()) {
            return ItemStack.EMPTY;
        }
        return super.extractItem(slot, amount, simulate);
    }

    @Override
    protected void onContentsChanged(int slot) {
        contentRevision++;
    }

    /** Restores one complete record without partially accepting malformed data. */
    public LoadStatus loadFrom(@Nullable CompoundTag parent) {
        resetDecodedState();
        if (parent == null || !parent.contains(STORAGE_TAG)) {
            return loadStatus;
        }

        Tag rawRecord = parent.get(STORAGE_TAG);
        if (!(rawRecord instanceof CompoundTag encoded)) {
            preserveUnreadable(rawRecord, LoadStatus.CORRUPT, -1);
            return loadStatus;
        }
        if (!encoded.contains(VERSION_TAG, Tag.TAG_INT)) {
            preserveUnreadable(encoded, LoadStatus.CORRUPT, -1);
            return loadStatus;
        }

        int version = encoded.getInt(VERSION_TAG);
        if (version < 1 || version > CURRENT_SCHEMA_VERSION) {
            preserveUnreadable(encoded, LoadStatus.UNSUPPORTED_VERSION, version);
            return loadStatus;
        }

        try {
            DecodedRecord decoded = decodeSupported(encoded, version);
            applyDecoded(decoded);
            loadStatus = LoadStatus.VALID;
            storedVersion = version;
        } catch (RuntimeException exception) {
            preserveUnreadable(encoded, LoadStatus.CORRUPT, -1);
        }
        return loadStatus;
    }

    /** Writes unreadable records back unchanged so downgrades are non-destructive. */
    public void saveTo(CompoundTag parent, ItemStack mainHand) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(mainHand, "mainHand");
        if (unreadableRecord != null) {
            parent.put(STORAGE_TAG, unreadableRecord.copy());
            return;
        }

        ItemStack sidearm = getStackInSlot(SIDEARM_SLOT);
        ItemStack stowedPrimary = getStackInSlot(STOWED_PRIMARY_SLOT);
        if (sidearm.isEmpty() && stowedPrimary.isEmpty() && !sidearmActive) {
            parent.remove(STORAGE_TAG);
            return;
        }

        authenticateActiveSidearm(mainHand);
        int outputVersion = sidearmActive && activeSidearmToken == null
                ? 2
                : CURRENT_SCHEMA_VERSION;
        CompoundTag encoded = new CompoundTag();
        encoded.putInt(VERSION_TAG, outputVersion);
        boolean validActiveContext = hasValidActiveContext();
        encoded.putBoolean(ACTIVE_TAG, sidearmActive && validActiveContext);
        putStack(encoded, SIDEARM_TAG, sidearm);
        putStack(encoded, STOWED_PRIMARY_TAG, stowedPrimary);
        if (sidearmActive && validActiveContext) {
            encoded.putString(ACTIVATION_REASON_TAG, activationReason.name());
            encoded.putLong(ACTIVATED_AT_TAG, activatedAtGameTick);
            encoded.putUUID(TARGET_TAG, activationTargetId);
            encoded.putDouble(PRIMARY_STANDOFF_TAG, primaryStandoffDistance);
            if (outputVersion >= 3) {
                encoded.putUUID(ACTIVE_TOKEN_TAG, activeSidearmToken);
            }
        }
        parent.put(STORAGE_TAG, encoded);
    }

    public boolean isWritable() {
        return unreadableRecord == null;
    }

    public LoadStatus getLoadStatus() {
        return loadStatus;
    }

    public int getStoredVersion() {
        return storedVersion;
    }

    public int getContentRevision() {
        return contentRevision;
    }

    public boolean isSidearmActive() {
        return sidearmActive;
    }

    public SidearmActivationReason getActivationReason() {
        return activationReason;
    }

    public long getActivatedAtGameTick() {
        return activatedAtGameTick;
    }

    public double getPrimaryStandoffDistance() {
        return primaryStandoffDistance;
    }

    @Nullable
    public UUID getActivationTargetId() {
        return activationTargetId;
    }

    public SidearmState stateAgainst(ItemStack mainHand) {
        Objects.requireNonNull(mainHand, "mainHand");
        if (!isWritable()) {
            return SidearmState.RECOVERY_REQUIRED;
        }
        SidearmState structuralState = SidearmStateContract.resolve(
                sidearmActive,
                !getStackInSlot(SIDEARM_SLOT).isEmpty(),
                !getStackInSlot(STOWED_PRIMARY_SLOT).isEmpty(),
                !mainHand.isEmpty());
        if (structuralState == SidearmState.SIDEARM_ACTIVE
                && (!isSingleGun(mainHand)
                        || (activeSidearmToken != null
                                && !hasActiveToken(mainHand, activeSidearmToken)))) {
            return SidearmState.RECOVERY_REQUIRED;
        }
        return structuralState;
    }

    /**
     * Authenticates a legacy active layout once the entity's equipment has
     * finished loading. The token is identity metadata only; it is never
     * recovered as another owned item.
     */
    public boolean authenticateActiveSidearm(ItemStack mainHand) {
        Objects.requireNonNull(mainHand, "mainHand");
        if (!isWritable() || !sidearmActive || activeSidearmToken != null) {
            return activeSidearmToken != null;
        }
        if (!getStackInSlot(SIDEARM_SLOT).isEmpty()
                || getStackInSlot(STOWED_PRIMARY_SLOT).isEmpty()
                || !isSingleGun(mainHand)) {
            return false;
        }

        CompoundTag stackTag = mainHand.getOrCreateTag();
        if (stackTag.contains(STACK_TOKEN_TAG) && !stackTag.hasUUID(STACK_TOKEN_TAG)) {
            return false;
        }
        UUID token = stackTag.hasUUID(STACK_TOKEN_TAG)
                ? stackTag.getUUID(STACK_TOKEN_TAG)
                : UUID.randomUUID();
        if (NIL_UUID.equals(token)) {
            return false;
        }
        stackTag.putUUID(STACK_TOKEN_TAG, token);
        activeSidearmToken = token;
        contentRevision++;
        return true;
    }

    /** Captures the exact private state used by one synchronous transaction. */
    StorageSnapshot captureTransactionSnapshot() {
        ensureWritable();
        return new StorageSnapshot(
                getStackInSlot(SIDEARM_SLOT),
                getStackInSlot(STOWED_PRIMARY_SLOT),
                sidearmActive,
                activationReason,
                activatedAtGameTick,
                primaryStandoffDistance,
                activationTargetId,
                activeSidearmToken);
    }

    /** Commits the private half of a primary-to-sidearm transaction. */
    void commitActivation(
            ItemStack primary,
            UUID sidearmToken,
            SidearmActivationReason reason,
            long gameTick,
            double primaryStandoff,
            UUID targetId) {
        ensureWritable();
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(sidearmToken, "sidearmToken");
        if (sidearmActive
                || getStackInSlot(SIDEARM_SLOT).isEmpty()
                || !getStackInSlot(STOWED_PRIMARY_SLOT).isEmpty()
                || !isSingleGun(primary)
                || NIL_UUID.equals(sidearmToken)) {
            throw new IllegalStateException("Private storage is not activation-ready");
        }
        replaceStacks(ItemStack.EMPTY, primary);
        try {
            markSidearmActive(reason, gameTick, targetId, primaryStandoff);
            activeSidearmToken = sidearmToken;
        } catch (RuntimeException exception) {
            replaceStacks(ItemStack.EMPTY, ItemStack.EMPTY);
            throw exception;
        }
    }

    /** Commits the private half of a sidearm-to-primary transaction. */
    void commitReturn(ItemStack sidearm) {
        ensureWritable();
        Objects.requireNonNull(sidearm, "sidearm");
        if (!sidearmActive
                || !getStackInSlot(SIDEARM_SLOT).isEmpty()
                || getStackInSlot(STOWED_PRIMARY_SLOT).isEmpty()
                || !isSingleGun(sidearm)
                || activeSidearmToken == null
                || !hasActiveToken(sidearm, activeSidearmToken)) {
            throw new IllegalStateException("Private storage is not return-ready");
        }
        replaceStacks(withoutActiveToken(sidearm), ItemStack.EMPTY);
        clearActiveContext();
    }

    /** Restores a previously captured stable state after a failed commit. */
    void restoreTransactionSnapshot(StorageSnapshot snapshot) {
        ensureWritable();
        Objects.requireNonNull(snapshot, "snapshot");
        replaceStacks(snapshot.sidearm(), snapshot.stowedPrimary());
        sidearmActive = snapshot.sidearmActive();
        activationReason = snapshot.activationReason();
        activatedAtGameTick = snapshot.activatedAtGameTick();
        primaryStandoffDistance = snapshot.primaryStandoffDistance();
        activationTargetId = snapshot.activationTargetId();
        activeSidearmToken = snapshot.activeSidearmToken();
    }

    /**
     * Last-resort lossless layout for a failed rollback. Both weapons remain
     * private and the deliberately contradictory inactive/stowed combination
     * persists as {@link SidearmState#RECOVERY_REQUIRED}.
     */
    void retainForRecovery(ItemStack sidearm, ItemStack primary) {
        ensureWritable();
        Objects.requireNonNull(sidearm, "sidearm");
        Objects.requireNonNull(primary, "primary");
        if (!isSingleGun(sidearm) || !isSingleGun(primary)) {
            throw new IllegalArgumentException("Recovery requires two complete TaCZ guns");
        }
        replaceStacks(withoutActiveToken(sidearm), primary);
        clearActiveContext();
    }

    /**
     * Records context only after the transaction has moved the primary
     * into its private slot and the sidearm into the main hand.
     */
    public void markSidearmActive(
            SidearmActivationReason reason,
            long gameTick,
            UUID targetId) {
        markSidearmActive(reason, gameTick, targetId, 0.0D);
    }

    public void markSidearmActive(
            SidearmActivationReason reason,
            long gameTick,
            UUID targetId,
            double primaryStandoff) {
        ensureWritable();
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(targetId, "targetId");
        if (reason == SidearmActivationReason.NONE
                || gameTick < 0L
                || NIL_UUID.equals(targetId)
                || !Double.isFinite(primaryStandoff)
                || primaryStandoff < 0.0D
                || !getStackInSlot(SIDEARM_SLOT).isEmpty()
                || getStackInSlot(STOWED_PRIMARY_SLOT).isEmpty()) {
            throw new IllegalStateException(
                    "Sidearm activation context requires an empty sidearm slot and a stowed primary");
        }
        sidearmActive = true;
        activationReason = reason;
        activatedAtGameTick = gameTick;
        primaryStandoffDistance = primaryStandoff;
        activationTargetId = targetId;
    }

    /** Clears only role metadata; callers must first restore the primary stack. */
    public void clearActiveContext() {
        ensureWritable();
        sidearmActive = false;
        activationReason = SidearmActivationReason.NONE;
        activatedAtGameTick = Long.MIN_VALUE;
        primaryStandoffDistance = 0.0D;
        activationTargetId = null;
        activeSidearmToken = null;
    }

    /** Copies every recoverable private stack for normal death-loot handling. */
    public List<ItemStack> recoverableStacks() {
        if (unreadableRecord != null) {
            return recoverKnownStacks(unreadableRecord);
        }
        List<ItemStack> recovered = new ArrayList<>(SLOT_COUNT);
        copyNonEmpty(getStackInSlot(SIDEARM_SLOT), recovered);
        copyNonEmpty(getStackInSlot(STOWED_PRIMARY_SLOT), recovered);
        return List.copyOf(recovered);
    }

    /**
     * Removes every recognized private stack for entity conversion. Unknown
     * record data is cleared only when its recognized stacks are being handed
     * to the caller immediately before the source entity is discarded.
     */
    public List<ItemStack> drainRecoverableStacks() {
        List<ItemStack> recovered = recoverableStacks();
        resetDecodedState();
        return recovered;
    }

    @Nullable
    public Tag copyUnreadableRecord() {
        return unreadableRecord == null ? null : unreadableRecord.copy();
    }

    private DecodedRecord decodeSupported(CompoundTag encoded, int version) {
        if (!encoded.contains(ACTIVE_TAG, Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Missing sidearm-active flag");
        }

        Set<String> expectedKeys = new HashSet<>(BASE_KEYS);
        ItemStack sidearm = readOptionalStack(encoded, SIDEARM_TAG, expectedKeys);
        ItemStack stowedPrimary = readOptionalStack(
                encoded,
                STOWED_PRIMARY_TAG,
                expectedKeys);
        boolean active = encoded.getBoolean(ACTIVE_TAG);
        SidearmActivationReason reason = SidearmActivationReason.NONE;
        long activatedAt = Long.MIN_VALUE;
        double primaryStandoff = 0.0D;
        UUID targetId = null;
        UUID activeToken = null;
        if (active) {
            if (!encoded.contains(ACTIVATION_REASON_TAG, Tag.TAG_STRING)
                    || !encoded.contains(ACTIVATED_AT_TAG, Tag.TAG_LONG)
                    || !encoded.hasUUID(TARGET_TAG)) {
                throw new IllegalArgumentException("Incomplete active sidearm context");
            }
            expectedKeys.add(ACTIVATION_REASON_TAG);
            expectedKeys.add(ACTIVATED_AT_TAG);
            expectedKeys.add(TARGET_TAG);
            reason = SidearmActivationReason.valueOf(encoded.getString(ACTIVATION_REASON_TAG));
            activatedAt = encoded.getLong(ACTIVATED_AT_TAG);
            targetId = encoded.getUUID(TARGET_TAG);
            if (reason == SidearmActivationReason.NONE
                    || activatedAt < 0L
                    || NIL_UUID.equals(targetId)) {
                throw new IllegalArgumentException("Invalid active sidearm context");
            }
            if (version >= 2) {
                if (!encoded.contains(PRIMARY_STANDOFF_TAG, Tag.TAG_DOUBLE)) {
                    throw new IllegalArgumentException("Missing primary standoff context");
                }
                expectedKeys.add(PRIMARY_STANDOFF_TAG);
                primaryStandoff = encoded.getDouble(PRIMARY_STANDOFF_TAG);
                if (!Double.isFinite(primaryStandoff) || primaryStandoff < 0.0D) {
                    throw new IllegalArgumentException("Invalid primary standoff context");
                }
            } else if (reason == SidearmActivationReason.EXPLOSIVE_STANDOFF) {
                // Version 1 did not persist this threshold. Retain the sidearm
                // until the engagement ends/changes instead of guessing low.
                primaryStandoff = Double.MAX_VALUE;
            }
            if (version >= 3) {
                if (!encoded.hasUUID(ACTIVE_TOKEN_TAG)) {
                    throw new IllegalArgumentException("Missing active sidearm token");
                }
                expectedKeys.add(ACTIVE_TOKEN_TAG);
                activeToken = encoded.getUUID(ACTIVE_TOKEN_TAG);
                if (NIL_UUID.equals(activeToken)) {
                    throw new IllegalArgumentException("Invalid active sidearm token");
                }
            }
        }
        if (!encoded.getAllKeys().equals(expectedKeys)) {
            throw new IllegalArgumentException("Unknown or misplaced sidearm fields");
        }
        return new DecodedRecord(
                sidearm,
                stowedPrimary,
                active,
                reason,
                activatedAt,
                primaryStandoff,
                targetId,
                activeToken);
    }

    private static ItemStack readOptionalStack(
            CompoundTag encoded,
            String key,
            Set<String> expectedKeys) {
        if (!encoded.contains(key)) {
            return ItemStack.EMPTY;
        }
        if (!encoded.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Stored weapon is not an item compound");
        }
        expectedKeys.add(key);
        ItemStack stack = ItemStack.of(encoded.getCompound(key));
        if (stack.isEmpty()
                || stack.getCount() != 1
                || !(stack.getItem() instanceof IGun)) {
            throw new IllegalArgumentException("Stored weapon is not one TaCZ gun");
        }
        return stack.copy();
    }

    private void applyDecoded(DecodedRecord decoded) {
        replaceStacks(decoded.sidearm(), decoded.stowedPrimary());
        sidearmActive = decoded.sidearmActive();
        activationReason = decoded.activationReason();
        activatedAtGameTick = decoded.activatedAtGameTick();
        primaryStandoffDistance = decoded.primaryStandoffDistance();
        activationTargetId = decoded.activationTargetId();
        activeSidearmToken = decoded.activeSidearmToken();
    }

    private void preserveUnreadable(
            @Nullable Tag rawRecord,
            LoadStatus status,
            int version) {
        unreadableRecord = rawRecord == null ? new CompoundTag() : rawRecord.copy();
        loadStatus = status;
        storedVersion = version;
    }

    private void resetDecodedState() {
        replaceStacks(ItemStack.EMPTY, ItemStack.EMPTY);
        unreadableRecord = null;
        loadStatus = LoadStatus.BLANK;
        storedVersion = -1;
        sidearmActive = false;
        activationReason = SidearmActivationReason.NONE;
        activatedAtGameTick = Long.MIN_VALUE;
        primaryStandoffDistance = 0.0D;
        activationTargetId = null;
        activeSidearmToken = null;
    }

    private void ensureWritable() {
        if (!internalMutation && !isWritable()) {
            throw new IllegalStateException(
                    "Unreadable sidearm data is preserved read-only for recovery");
        }
    }

    private boolean hasValidActiveContext() {
        return activationReason != null
                && activationReason != SidearmActivationReason.NONE
                && activatedAtGameTick >= 0L
                && Double.isFinite(primaryStandoffDistance)
                && primaryStandoffDistance >= 0.0D
                && activationTargetId != null
                && !NIL_UUID.equals(activationTargetId);
    }

    private void replaceStacks(ItemStack sidearm, ItemStack stowedPrimary) {
        internalMutation = true;
        try {
            super.setStackInSlot(SIDEARM_SLOT, sidearm.copy());
            super.setStackInSlot(STOWED_PRIMARY_SLOT, stowedPrimary.copy());
        } finally {
            internalMutation = false;
        }
    }

    private static boolean isSingleGun(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getCount() == 1
                && stack.getItem() instanceof IGun;
    }

    private static boolean stacksMatch(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameTags(first, second);
    }

    static ItemStack withActiveToken(ItemStack stack, UUID token) {
        if (!isSingleGun(stack) || token == null || NIL_UUID.equals(token)) {
            throw new IllegalArgumentException("Active sidearm token requires one TaCZ gun");
        }
        ItemStack tagged = stack.copy();
        tagged.getOrCreateTag().putUUID(STACK_TOKEN_TAG, token);
        return tagged;
    }

    static ItemStack withoutActiveToken(ItemStack stack) {
        ItemStack cleaned = stack.copy();
        CompoundTag tag = cleaned.getTag();
        if (tag != null) {
            tag.remove(STACK_TOKEN_TAG);
            if (tag.isEmpty()) {
                cleaned.setTag(null);
            }
        }
        return cleaned;
    }

    private static boolean hasActiveToken(ItemStack stack, UUID expected) {
        CompoundTag tag = stack.getTag();
        return tag != null
                && tag.hasUUID(STACK_TOKEN_TAG)
                && expected.equals(tag.getUUID(STACK_TOKEN_TAG));
    }

    private final class ConfiguredSidearmView implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            requireConfiguredSlot(slot);
            return GuardSidearmStorage.this.getStackInSlot(SIDEARM_SLOT).copy();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            requireConfiguredSlot(slot);
            Objects.requireNonNull(stack, "stack");
            if (!isExternallyConfigurable()) {
                return stack.copy();
            }
            return GuardSidearmStorage.this.insertItem(
                    SIDEARM_SLOT,
                    stack,
                    simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            requireConfiguredSlot(slot);
            if (!isExternallyConfigurable()) {
                return ItemStack.EMPTY;
            }
            return GuardSidearmStorage.this.extractItem(
                    SIDEARM_SLOT,
                    amount,
                    simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            requireConfiguredSlot(slot);
            return GuardSidearmStorage.this.getSlotLimit(SIDEARM_SLOT);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            requireConfiguredSlot(slot);
            return isExternallyConfigurable()
                    && GuardSidearmStorage.this.isItemValid(SIDEARM_SLOT, stack);
        }

        private boolean isExternallyConfigurable() {
            return isWritable()
                    && !sidearmActive
                    && GuardSidearmStorage.this
                            .getStackInSlot(STOWED_PRIMARY_SLOT)
                            .isEmpty();
        }

        private void requireConfiguredSlot(int slot) {
            if (slot != 0) {
                throw new IndexOutOfBoundsException("Configured sidearm view has one slot");
            }
        }
    }

    private static void putStack(CompoundTag encoded, String key, ItemStack stack) {
        if (!stack.isEmpty()) {
            encoded.put(key, stack.save(new CompoundTag()));
        }
    }

    private static List<ItemStack> recoverKnownStacks(Tag rawRecord) {
        if (!(rawRecord instanceof CompoundTag encoded)) {
            return List.of();
        }
        List<ItemStack> recovered = new ArrayList<>(SLOT_COUNT);
        recoverKnownStack(encoded, SIDEARM_TAG, recovered);
        recoverKnownStack(encoded, STOWED_PRIMARY_TAG, recovered);
        return List.copyOf(recovered);
    }

    private static void recoverKnownStack(
            CompoundTag encoded,
            String key,
            List<ItemStack> destination) {
        if (!encoded.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        try {
            copyNonEmpty(ItemStack.of(encoded.getCompound(key)), destination);
        } catch (RuntimeException ignored) {
            // The unreadable raw record remains authoritative until removal.
        }
    }

    private static void copyNonEmpty(ItemStack stack, List<ItemStack> destination) {
        if (stack != null && !stack.isEmpty()) {
            destination.add(stack.copy());
        }
    }

    public enum LoadStatus {
        BLANK,
        VALID,
        CORRUPT,
        UNSUPPORTED_VERSION
    }

    static final class StorageSnapshot {
        private final ItemStack sidearm;
        private final ItemStack stowedPrimary;
        private final boolean sidearmActive;
        private final SidearmActivationReason activationReason;
        private final long activatedAtGameTick;
        private final double primaryStandoffDistance;
        @Nullable
        private final UUID activationTargetId;
        @Nullable
        private final UUID activeSidearmToken;

        private StorageSnapshot(
                ItemStack sidearm,
                ItemStack stowedPrimary,
                boolean sidearmActive,
                SidearmActivationReason activationReason,
                long activatedAtGameTick,
                double primaryStandoffDistance,
                @Nullable UUID activationTargetId,
                @Nullable UUID activeSidearmToken) {
            this.sidearm = sidearm.copy();
            this.stowedPrimary = stowedPrimary.copy();
            this.sidearmActive = sidearmActive;
            this.activationReason = activationReason;
            this.activatedAtGameTick = activatedAtGameTick;
            this.primaryStandoffDistance = primaryStandoffDistance;
            this.activationTargetId = activationTargetId;
            this.activeSidearmToken = activeSidearmToken;
        }

        ItemStack sidearm() {
            return sidearm.copy();
        }

        ItemStack stowedPrimary() {
            return stowedPrimary.copy();
        }

        boolean sidearmActive() {
            return sidearmActive;
        }

        SidearmActivationReason activationReason() {
            return activationReason;
        }

        long activatedAtGameTick() {
            return activatedAtGameTick;
        }

        double primaryStandoffDistance() {
            return primaryStandoffDistance;
        }

        @Nullable
        UUID activationTargetId() {
            return activationTargetId;
        }

        @Nullable
        UUID activeSidearmToken() {
            return activeSidearmToken;
        }

        boolean matches(GuardSidearmStorage storage) {
            return storage.isWritable()
                    && stacksMatch(sidearm, storage.getStackInSlot(SIDEARM_SLOT))
                    && stacksMatch(
                            stowedPrimary,
                            storage.getStackInSlot(STOWED_PRIMARY_SLOT))
                    && sidearmActive == storage.sidearmActive
                    && activationReason == storage.activationReason
                    && activatedAtGameTick == storage.activatedAtGameTick
                    && Double.compare(
                            primaryStandoffDistance,
                            storage.primaryStandoffDistance) == 0
                    && Objects.equals(activationTargetId, storage.activationTargetId)
                    && Objects.equals(activeSidearmToken, storage.activeSidearmToken);
        }
    }

    private record DecodedRecord(
            ItemStack sidearm,
            ItemStack stowedPrimary,
            boolean sidearmActive,
            SidearmActivationReason activationReason,
            long activatedAtGameTick,
            double primaryStandoffDistance,
            @Nullable UUID activationTargetId,
            @Nullable UUID activeSidearmToken) {
    }
}
