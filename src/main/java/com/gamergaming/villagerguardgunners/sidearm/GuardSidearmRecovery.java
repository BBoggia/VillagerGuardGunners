package com.gamergaming.villagerguardgunners.sidearm;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.GuardVillagers;
import tallestegg.guardvillagers.entities.Guard;

/** Explicit, server-authoritative escape hatch for a fail-closed sidearm record. */
public final class GuardSidearmRecovery {
    private GuardSidearmRecovery() {
    }

    public static RecoveryResult recover(Guard guard, Player player) {
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(player, "player");
        if (!canRecover(guard, player)) {
            return RecoveryResult.failed(RecoveryStatus.UNAUTHORIZED);
        }

        GuardSidearmStorage storage = GuardSidearmStorageAccess.get(guard);
        if (storage.stateAgainst(guard.getMainHandItem()) != SidearmState.RECOVERY_REQUIRED) {
            return RecoveryResult.failed(RecoveryStatus.NOT_REQUIRED);
        }

        ItemStack mainHandBefore = guard.getMainHandItem().copy();
        ItemStack cleanedMainHand = GuardSidearmStorage.withoutActiveToken(mainHandBefore);
        List<ItemStack> recovered;
        try {
            // Never guess a weapon role during recovery. The equipped item stays
            // equipped; only this mod's private authentication marker is removed.
            if (!stacksMatch(mainHandBefore, cleanedMainHand)) {
                guard.setItemSlot(EquipmentSlot.MAINHAND, cleanedMainHand.copy());
                if (!stacksMatch(cleanedMainHand, guard.getMainHandItem())) {
                    guard.setItemSlot(EquipmentSlot.MAINHAND, mainHandBefore.copy());
                    return RecoveryResult.failed(RecoveryStatus.TRANSACTION_FAILED);
                }
            }
            recovered = storage.drainRecoverableStacks();
            if (storage.stateAgainst(guard.getMainHandItem())
                    != SidearmState.PRIMARY_ACTIVE) {
                return RecoveryResult.failed(RecoveryStatus.TRANSACTION_FAILED);
            }
        } catch (RuntimeException exception) {
            try {
                guard.setItemSlot(EquipmentSlot.MAINHAND, mainHandBefore.copy());
            } catch (RuntimeException ignored) {
                // The failure is surfaced; no additional item is synthesized.
            }
            return RecoveryResult.failed(RecoveryStatus.TRANSACTION_FAILED);
        }

        for (ItemStack stack : recovered) {
            if (!stack.isEmpty()) {
                // Vanilla inserts as much as possible and drops any overflow at
                // the player, so a full inventory cannot delete a recovered gun.
                player.getInventory().placeItemBackInInventory(stack.copy());
            }
        }
        return new RecoveryResult(RecoveryStatus.RECOVERED, recovered.size());
    }

    private static boolean canRecover(Guard guard, Player player) {
        return guard.isAlive()
                && !guard.isRemoved()
                && !guard.level().isClientSide
                && !player.level().isClientSide
                && guard.getServer() != null
                && guard.getServer().isSameThread()
                && player.level() == guard.level()
                && guard.distanceTo(player) < 8.0F
                && GuardVillagers.hotvChecker(player, guard);
    }

    private static boolean stacksMatch(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameTags(first, second);
    }

    public enum RecoveryStatus {
        RECOVERED,
        NOT_REQUIRED,
        UNAUTHORIZED,
        TRANSACTION_FAILED
    }

    public record RecoveryResult(RecoveryStatus status, int recoveredStackCount) {
        public RecoveryResult {
            Objects.requireNonNull(status, "status");
            if (recoveredStackCount < 0
                    || (status != RecoveryStatus.RECOVERED && recoveredStackCount != 0)) {
                throw new IllegalArgumentException("Invalid sidearm recovery result");
            }
        }

        public boolean recovered() {
            return status == RecoveryStatus.RECOVERED;
        }

        private static RecoveryResult failed(RecoveryStatus status) {
            return new RecoveryResult(status, 0);
        }
    }
}
