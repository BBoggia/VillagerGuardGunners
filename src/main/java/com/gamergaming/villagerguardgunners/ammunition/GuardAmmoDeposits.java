package com.gamergaming.villagerguardgunners.ammunition;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import tallestegg.guardvillagers.GuardVillagers;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Server-authoritative, on-interaction ammunition handoff. This path never
 * runs from a guard or world tick and inserts only into the add-on's dedicated
 * ammunition slots. Authorization follows Guard Villagers' give-equipment
 * policy; it does not grant the player access to take from the guard.
 */
public final class GuardAmmoDeposits {
    private GuardAmmoDeposits() {
    }

    public static DepositResult deposit(
            Player player,
            Guard guard,
            InteractionHand hand) {
        if (player == null
                || guard == null
                || hand == null
                || player.level().isClientSide
                || !guard.isAlive()
                || !player.isSecondaryUseActive()) {
            return DepositResult.failed(DepositStatus.INVALID_REQUEST);
        }

        ItemStack offered = player.getItemInHand(hand);
        if (!GuardAmmoHandler.isAmmo(offered)) {
            return DepositResult.failed(DepositStatus.NOT_AMMUNITION);
        }
        if (player.isSpectator()
                || guard.getTarget() == player
                || !GuardVillagers.hotvChecker(player, guard)) {
            return DepositResult.failed(DepositStatus.UNAUTHORIZED);
        }
        if (!guard.isEffectiveAi()) {
            return DepositResult.failed(DepositStatus.INACTIVE_GUARD);
        }

        ItemStack gunStack = guard.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) {
            return DepositResult.failed(DepositStatus.NO_GUN);
        }

        GuardAmmoSummary.Snapshot ammoState = GuardAmmoSummary.capture(gunStack.copy(), null);
        if (!ammoState.isReady()) {
            return DepositResult.failed(DepositStatus.UNREADABLE_GUN);
        }
        switch (ammoState.reserveSource()) {
            case PHYSICAL -> {
                // Continue into TaCZ's item-level compatibility check below.
            }
            case DUMMY, NATIVE_INFINITE -> {
                return DepositResult.failed(DepositStatus.GUN_DOES_NOT_USE_PHYSICAL_AMMO);
            }
            case NONE -> {
                return DepositResult.failed(DepositStatus.UNREADABLE_GUN);
            }
        }

        AmmoCompatibility.Assessment compatibility =
                AmmoCompatibility.assess(gunStack, offered);
        if (compatibility.compatibility() == AmmoCompatibility.Compatibility.UNKNOWN) {
            return DepositResult.failed(DepositStatus.UNREADABLE_AMMUNITION);
        }
        if (!compatibility.isCompatible()) {
            return DepositResult.failed(DepositStatus.INCOMPATIBLE_AMMUNITION);
        }

        GuardAmmoHandler destination = GuardAmmoAccess.getAmmoHandler(guard);
        GuardAmmoMaintenance.ejectEmptyFiniteBoxes(guard);
        GuardAmmoTransactions.InsertionResult insertion =
                GuardAmmoTransactions.insert(destination, offered.copy());
        if (insertion.status() == GuardAmmoTransactions.TransactionStatus.NO_OP) {
            return DepositResult.failed(DepositStatus.INVENTORY_FULL);
        }
        if (!insertion.committed() || insertion.inserted() <= 0) {
            return DepositResult.failed(DepositStatus.TRANSACTION_FAILED);
        }

        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, insertion.remainder().copy());
        }
        return new DepositResult(
                DepositStatus.DEPOSITED,
                insertion.inserted(),
                insertion.remainder().copy());
    }

    public enum DepositStatus {
        DEPOSITED,
        NOT_AMMUNITION,
        UNAUTHORIZED,
        INACTIVE_GUARD,
        NO_GUN,
        UNREADABLE_GUN,
        GUN_DOES_NOT_USE_PHYSICAL_AMMO,
        INCOMPATIBLE_AMMUNITION,
        UNREADABLE_AMMUNITION,
        INVENTORY_FULL,
        TRANSACTION_FAILED,
        INVALID_REQUEST
    }

    public record DepositResult(
            DepositStatus status,
            int inserted,
            ItemStack remainder) {
        public boolean deposited() {
            return status == DepositStatus.DEPOSITED;
        }

        private static DepositResult failed(DepositStatus status) {
            return new DepositResult(status, 0, ItemStack.EMPTY);
        }
    }
}
