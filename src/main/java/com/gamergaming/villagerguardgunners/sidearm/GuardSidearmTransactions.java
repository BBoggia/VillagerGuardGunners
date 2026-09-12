package com.gamergaming.villagerguardgunners.sidearm;

import com.tacz.guns.api.item.IGun;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Server-authoritative, synchronous movement of complete TaCZ gun stacks.
 *
 * <p>This layer deliberately knows nothing about targeting or tactical policy.
 * The caller must first obtain a transition decision and pass the final native
 * action-state safety result. Every commit retains exact rollback snapshots;
 * a failed rollback moves both original weapons into a persistent recovery
 * layout instead of overwriting either one.</p>
 */
public final class GuardSidearmTransactions {
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private GuardSidearmTransactions() {
    }

    /**
     * Places one already validated natural sidearm into an otherwise blank,
     * stable configured slot. This never displaces a player- or mod-supplied
     * weapon.
     */
    public static NaturalSidearmResult configureNaturalSidearm(
            Guard guard,
            ItemStack sidearm) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(sidearm, "sidearm");
        if (!canMutate(guard, true) || !isSingleGun(sidearm)) {
            return NaturalSidearmResult.failed(NaturalSidearmStatus.INVALID_REQUEST);
        }

        GuardSidearmStorage storage = GuardSidearmStorageAccess.get(guard);
        SidearmState before = storage.stateAgainst(guard.getMainHandItem());
        if (before == SidearmState.RECOVERY_REQUIRED) {
            return NaturalSidearmResult.failed(NaturalSidearmStatus.RECOVERY_REQUIRED);
        }
        if (before != SidearmState.PRIMARY_ACTIVE) {
            return NaturalSidearmResult.failed(NaturalSidearmStatus.SLOT_UNAVAILABLE);
        }

        final GuardSidearmStorage.StorageSnapshot snapshot;
        try {
            snapshot = storage.captureTransactionSnapshot();
        } catch (RuntimeException exception) {
            return NaturalSidearmResult.failed(NaturalSidearmStatus.RECOVERY_REQUIRED);
        }
        if (!snapshot.sidearm().isEmpty()
                || !snapshot.stowedPrimary().isEmpty()
                || !snapshot.matches(storage)) {
            return NaturalSidearmResult.failed(NaturalSidearmStatus.SLOT_UNAVAILABLE);
        }

        try {
            storage.setStackInSlot(
                    GuardSidearmStorage.SIDEARM_SLOT,
                    sidearm.copy());
            if (storage.stateAgainst(guard.getMainHandItem()) == SidearmState.PRIMARY_ACTIVE
                    && stacksMatch(
                            sidearm,
                            storage.getStackInSlot(GuardSidearmStorage.SIDEARM_SLOT))
                    && storage.getStackInSlot(
                            GuardSidearmStorage.STOWED_PRIMARY_SLOT).isEmpty()) {
                return new NaturalSidearmResult(
                        NaturalSidearmStatus.CONFIGURED,
                        sidearm.copy());
            }
        } catch (RuntimeException ignored) {
            // Restore the exact stable snapshot below.
        }
        return NaturalSidearmResult.failed(restoreNaturalSnapshot(storage, snapshot)
                ? NaturalSidearmStatus.ROLLED_BACK
                : NaturalSidearmStatus.ROLLBACK_FAILED);
    }

    /** Removes only the exact natural sidearm installed by the caller. */
    public static boolean rollbackNaturalSidearm(
            Guard guard,
            ItemStack expectedSidearm) {
        if (guard == null || expectedSidearm == null || expectedSidearm.isEmpty()) {
            return false;
        }
        try {
            GuardSidearmStorage storage = GuardSidearmStorageAccess.get(guard);
            if (storage.stateAgainst(guard.getMainHandItem()) != SidearmState.PRIMARY_ACTIVE
                    || !stacksMatch(
                            expectedSidearm,
                            storage.getStackInSlot(GuardSidearmStorage.SIDEARM_SLOT))
                    || !storage.getStackInSlot(
                            GuardSidearmStorage.STOWED_PRIMARY_SLOT).isEmpty()) {
                return false;
            }
            storage.setStackInSlot(GuardSidearmStorage.SIDEARM_SLOT, ItemStack.EMPTY);
            return storage.stateAgainst(guard.getMainHandItem()) == SidearmState.PRIMARY_ACTIVE
                    && storage.getStackInSlot(
                            GuardSidearmStorage.SIDEARM_SLOT).isEmpty()
                    && storage.getStackInSlot(
                            GuardSidearmStorage.STOWED_PRIMARY_SLOT).isEmpty();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean restoreNaturalSnapshot(
            GuardSidearmStorage storage,
            GuardSidearmStorage.StorageSnapshot snapshot) {
        try {
            storage.restoreTransactionSnapshot(snapshot);
            return snapshot.matches(storage);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Moves the configured sidearm to the main hand and stows the primary. */
    public static SidearmTransitionResult activate(
            Guard guard,
            SidearmActivationReason reason,
            long gameTick,
            UUID targetId,
            boolean nativeSwapSafe) {
        return activate(
                guard,
                reason,
                gameTick,
                targetId,
                0.0D,
                nativeSwapSafe);
    }

    public static SidearmTransitionResult activate(
            Guard guard,
            SidearmActivationReason reason,
            long gameTick,
            UUID targetId,
            double primaryStandoffDistance,
            boolean nativeSwapSafe) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(targetId, "targetId");

        GuardSidearmStorage storage = GuardSidearmStorageAccess.get(guard);
        ItemStack mainHand = guard.getMainHandItem();
        SidearmState before = storage.stateAgainst(mainHand);
        if (before == SidearmState.RECOVERY_REQUIRED) {
            return SidearmTransitionResult.recoveryRequired(before);
        }
        if (before != SidearmState.PRIMARY_ACTIVE
                || !canMutate(guard, nativeSwapSafe)
                || reason == SidearmActivationReason.NONE
                || gameTick < 0L
                || NIL_UUID.equals(targetId)
                || !Double.isFinite(primaryStandoffDistance)
                || primaryStandoffDistance < 0.0D
                || !isSingleGun(mainHand)) {
            return SidearmTransitionResult.rolledBack(before);
        }

        GuardSidearmStorage.StorageSnapshot storageBefore;
        try {
            storageBefore = storage.captureTransactionSnapshot();
        } catch (RuntimeException exception) {
            return SidearmTransitionResult.recoveryRequired(before);
        }
        ItemStack primaryBefore = mainHand.copy();
        ItemStack sidearmBefore = storageBefore.sidearm();
        if (!isSingleGun(sidearmBefore)
                || !storageBefore.stowedPrimary().isEmpty()
                || !storageBefore.matches(storage)
                || !stacksMatch(primaryBefore, guard.getMainHandItem())) {
            return SidearmTransitionResult.rolledBack(before);
        }
        UUID sidearmToken = UUID.randomUUID();
        ItemStack activeSidearm = GuardSidearmStorage.withActiveToken(
                sidearmBefore,
                sidearmToken);

        try {
            guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            storage.commitActivation(
                    primaryBefore,
                    sidearmToken,
                    reason,
                    gameTick,
                    primaryStandoffDistance,
                    targetId);
            guard.setItemSlot(EquipmentSlot.MAINHAND, activeSidearm.copy());
            if (activationCommitted(
                    guard,
                    storage,
                    primaryBefore,
                    activeSidearm,
                    reason,
                    gameTick,
                    primaryStandoffDistance,
                    targetId)) {
                return SidearmTransitionResult.activated();
            }
        } catch (RuntimeException ignored) {
            // The exact snapshots below remain authoritative for rollback.
        }
        return rollback(
                guard,
                storage,
                before,
                primaryBefore,
                storageBefore,
                sidearmBefore,
                primaryBefore);
    }

    /** Restores the stowed primary and returns the held sidearm to its slot. */
    public static SidearmTransitionResult returnPrimary(
            Guard guard,
            boolean nativeSwapSafe) {
        Objects.requireNonNull(guard, "guard");

        GuardSidearmStorage storage = GuardSidearmStorageAccess.get(guard);
        ItemStack mainHand = guard.getMainHandItem();
        SidearmState before = storage.stateAgainst(mainHand);
        if (before == SidearmState.RECOVERY_REQUIRED) {
            return SidearmTransitionResult.recoveryRequired(before);
        }
        if (before != SidearmState.SIDEARM_ACTIVE
                || !canMutate(guard, nativeSwapSafe)
                || !isSingleGun(mainHand)) {
            return SidearmTransitionResult.rolledBack(before);
        }

        GuardSidearmStorage.StorageSnapshot storageBefore;
        try {
            storageBefore = storage.captureTransactionSnapshot();
        } catch (RuntimeException exception) {
            return SidearmTransitionResult.recoveryRequired(before);
        }
        ItemStack sidearmBefore = mainHand.copy();
        ItemStack configuredSidearm = GuardSidearmStorage.withoutActiveToken(sidearmBefore);
        ItemStack primaryBefore = storageBefore.stowedPrimary();
        if (!isSingleGun(primaryBefore)
                || !storageBefore.sidearm().isEmpty()
                || !storageBefore.matches(storage)
                || !stacksMatch(sidearmBefore, guard.getMainHandItem())) {
            return SidearmTransitionResult.recoveryRequired(before);
        }

        try {
            guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            storage.commitReturn(sidearmBefore);
            guard.setItemSlot(EquipmentSlot.MAINHAND, primaryBefore.copy());
            if (returnCommitted(
                    guard,
                    storage,
                    primaryBefore,
                    configuredSidearm)) {
                return SidearmTransitionResult.returned();
            }
        } catch (RuntimeException ignored) {
            // The exact snapshots below remain authoritative for rollback.
        }
        return rollback(
                guard,
                storage,
                before,
                sidearmBefore,
                storageBefore,
                sidearmBefore,
                primaryBefore);
    }

    private static boolean canMutate(Guard guard, boolean nativeSwapSafe) {
        return nativeSwapSafe
                && guard.isAlive()
                && !com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard)
                && !guard.isRemoved()
                && !guard.level().isClientSide
                && guard.getServer() != null
                && guard.getServer().isSameThread();
    }

    private static boolean activationCommitted(
            Guard guard,
            GuardSidearmStorage storage,
            ItemStack primary,
            ItemStack sidearm,
            SidearmActivationReason reason,
            long gameTick,
            double primaryStandoffDistance,
            UUID targetId) {
        return storage.stateAgainst(guard.getMainHandItem())
                        == SidearmState.SIDEARM_ACTIVE
                && stacksMatch(sidearm, guard.getMainHandItem())
                && storage.getStackInSlot(GuardSidearmStorage.SIDEARM_SLOT).isEmpty()
                && stacksMatch(
                        primary,
                        storage.getStackInSlot(GuardSidearmStorage.STOWED_PRIMARY_SLOT))
                && storage.isSidearmActive()
                && storage.getActivationReason() == reason
                && storage.getActivatedAtGameTick() == gameTick
                && Double.compare(
                        storage.getPrimaryStandoffDistance(),
                        primaryStandoffDistance) == 0
                && targetId.equals(storage.getActivationTargetId());
    }

    private static boolean returnCommitted(
            Guard guard,
            GuardSidearmStorage storage,
            ItemStack primary,
            ItemStack sidearm) {
        return storage.stateAgainst(guard.getMainHandItem())
                        == SidearmState.PRIMARY_ACTIVE
                && stacksMatch(primary, guard.getMainHandItem())
                && stacksMatch(
                        sidearm,
                        storage.getStackInSlot(GuardSidearmStorage.SIDEARM_SLOT))
                && storage.getStackInSlot(
                        GuardSidearmStorage.STOWED_PRIMARY_SLOT).isEmpty()
                && !storage.isSidearmActive()
                && storage.getActivationReason() == SidearmActivationReason.NONE
                && storage.getActivatedAtGameTick() == Long.MIN_VALUE
                && storage.getPrimaryStandoffDistance() == 0.0D
                && storage.getActivationTargetId() == null;
    }

    private static SidearmTransitionResult rollback(
            Guard guard,
            GuardSidearmStorage storage,
            SidearmState before,
            ItemStack mainHandBefore,
            GuardSidearmStorage.StorageSnapshot storageBefore,
            ItemStack sidearm,
            ItemStack primary) {
        try {
            guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            storage.restoreTransactionSnapshot(storageBefore);
            guard.setItemSlot(EquipmentSlot.MAINHAND, mainHandBefore.copy());
            if (stacksMatch(mainHandBefore, guard.getMainHandItem())
                    && storageBefore.matches(storage)
                    && storage.stateAgainst(guard.getMainHandItem()) == before) {
                return SidearmTransitionResult.rolledBack(before);
            }
        } catch (RuntimeException ignored) {
            // Fall through to the persistent, explicitly recoverable layout.
        }

        try {
            guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (guard.getMainHandItem().isEmpty()) {
                storage.retainForRecovery(sidearm, primary);
            }
        } catch (RuntimeException ignored) {
            // ROLLBACK_FAILED is intentionally surfaced even if recovery setup
            // itself encounters an unexpected third-party equipment failure.
        }
        return SidearmTransitionResult.rollbackFailed(before);
    }

    private static boolean isSingleGun(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getCount() == 1
                && stack.getItem() instanceof IGun;
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

    public enum NaturalSidearmStatus {
        CONFIGURED,
        INVALID_REQUEST,
        SLOT_UNAVAILABLE,
        RECOVERY_REQUIRED,
        ROLLED_BACK,
        ROLLBACK_FAILED
    }

    public record NaturalSidearmResult(
            NaturalSidearmStatus status,
            ItemStack configuredSidearm) {
        public NaturalSidearmResult {
            status = Objects.requireNonNull(status, "status");
            configuredSidearm = configuredSidearm == null
                    ? ItemStack.EMPTY
                    : configuredSidearm.copy();
            if ((status == NaturalSidearmStatus.CONFIGURED)
                    != !configuredSidearm.isEmpty()) {
                throw new IllegalArgumentException(
                        "Natural sidearm transaction result is inconsistent");
            }
        }

        public boolean configured() {
            return status == NaturalSidearmStatus.CONFIGURED;
        }

        @Override
        public ItemStack configuredSidearm() {
            return configuredSidearm.copy();
        }

        private static NaturalSidearmResult failed(NaturalSidearmStatus status) {
            return new NaturalSidearmResult(status, ItemStack.EMPTY);
        }
    }
}
