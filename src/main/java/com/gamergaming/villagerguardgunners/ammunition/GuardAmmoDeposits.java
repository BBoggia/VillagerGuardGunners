package com.gamergaming.villagerguardgunners.ammunition;

import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
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
                || com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard) || !guard.isAlive()
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

        GuardWeaponAmmunition.SupplyAssessment compatibility =
                GuardWeaponAmmunition.assessPhysicalSupply(guard, offered);
        if (!compatibility.compatible()) {
            return DepositResult.failed(switch (compatibility.status()) {
                case NO_GUN -> DepositStatus.NO_GUN;
                case UNREADABLE_GUN, RECOVERY_REQUIRED -> DepositStatus.UNREADABLE_GUN;
                case NO_PHYSICAL_AMMO_WEAPON ->
                        DepositStatus.GUN_DOES_NOT_USE_PHYSICAL_AMMO;
                case UNREADABLE_AMMUNITION -> DepositStatus.UNREADABLE_AMMUNITION;
                case INCOMPATIBLE -> DepositStatus.INCOMPATIBLE_AMMUNITION;
                case COMPATIBLE -> DepositStatus.TRANSACTION_FAILED;
            });
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
