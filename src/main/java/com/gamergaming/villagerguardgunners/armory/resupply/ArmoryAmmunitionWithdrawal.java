package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoTransactions;
import com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardOrdersAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadCommandAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import com.tacz.guns.api.entity.IGunOperator;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import tallestegg.guardvillagers.entities.Guard;

/** Final, one-shot Armory-to-guard ammunition transaction. */
public final class ArmoryAmmunitionWithdrawal {
    private ArmoryAmmunitionWithdrawal() {
    }

    /**
     * Consumes the reservation before inspecting live state, so every outcome
     * releases ownership exactly once. No chunk is loaded and no path, target,
     * equipment, native weapon action, or AI state is changed by this method;
     * only a successful transaction changes the two ammunition inventories.
     */
    public static WithdrawalResult withdraw(
            ArmoryReservationBook reservationBook,
            ArmoryResupplyRequest.RequestKey requestKey,
            ArmoryBlockEntity armory,
            Guard guard) {
        if (reservationBook == null || requestKey == null) {
            return WithdrawalResult.failed(WithdrawalStatus.INVALID_INPUT);
        }
        if (guard == null) {
            reservationBook.release(requestKey);
            return WithdrawalResult.failed(WithdrawalStatus.INVALID_INPUT);
        }
        Level level = guard.level();
        long gameTick = level.getGameTime();
        ArmoryReservationBook.ClaimResult claim =
                reservationBook.claim(requestKey, gameTick);
        if (!claim.claimed()) {
            return WithdrawalResult.failed(switch (claim.status()) {
                case EXPIRED -> WithdrawalStatus.RESERVATION_EXPIRED;
                case NOT_FOUND -> WithdrawalStatus.NO_RESERVATION;
                case INVALID_INPUT -> WithdrawalStatus.INVALID_INPUT;
                case CLAIMED -> throw new IllegalStateException("Claim status contradicted its payload");
            });
        }

        ArmoryAmmoReservation reservation = Objects.requireNonNull(claim.reservation());
        ArmoryResupplyRequest request = reservation.request();
        if (armory == null) {
            return WithdrawalResult.failed(WithdrawalStatus.INVALID_INPUT);
        }
        if (!request.guardId().equals(guard.getUUID())
                || !guard.isAlive()
                || guard.isRemoved()
                || guard.isNoAi()
                || guard.level().isClientSide) {
            return WithdrawalResult.failed(WithdrawalStatus.GUARD_UNAVAILABLE);
        }

        if (!request.dimension().equals(level.dimension().location())
                || !reservation.target().dimension().equals(level.dimension().location())) {
            return WithdrawalResult.failed(WithdrawalStatus.DIMENSION_CHANGED);
        }
        if (armory.isRemoved()
                || armory.getLevel() != level
                || !armory.getBlockPos().equals(reservation.target().position())
                || !level.hasChunkAt(reservation.target().position())
                || level.getBlockEntity(reservation.target().position()) != armory) {
            return WithdrawalResult.failed(WithdrawalStatus.ARMORY_UNAVAILABLE);
        }
        if (guard.distanceToSqr(
                        reservation.target().position().getX() + 0.5D,
                        reservation.target().position().getY() + 0.5D,
                        reservation.target().position().getZ() + 0.5D)
                > ArmoryResupplyLimits.HARD_MAX_WITHDRAWAL_DISTANCE_SQUARED) {
            return WithdrawalResult.failed(WithdrawalStatus.TOO_FAR_AWAY);
        }
        if (!Objects.equals(request.squadId(), GuardSquadAccess.getSquadId(guard))) {
            return WithdrawalResult.failed(WithdrawalStatus.SQUAD_CHANGED);
        }
        if (!armory.maySupply(request.squadId())) {
            return WithdrawalResult.failed(WithdrawalStatus.ACCESS_DENIED);
        }

        GuardAmmoHandler destination;
        GuardWeaponAmmunition.Loadout loadout;
        try {
            destination = GuardAmmoAccess.getAmmoHandler(guard);
            loadout = GuardWeaponAmmunition.captureLoadout(guard);
        } catch (RuntimeException exception) {
            return WithdrawalResult.failed(WithdrawalStatus.GUARD_INVENTORY_UNAVAILABLE);
        }
        ArmoryResupplyGate.GateStatus liveGate = ArmoryResupplyGate.evaluate(
                true,
                true,
                guard.isAlive() && !guard.isRemoved(),
                !guard.isNoAi(),
                loadout.state(),
                guard.getTarget() != null && guard.getTarget().isAlive(),
                GuardSquadCommandAccess.getCommand(guard) != null,
                guard.isFollowing(),
                GuardOrdersAccess.getOrders(guard).movement(),
                guard.isPatrolling(),
                nativeWeaponBusy(guard));
        if (!liveGate.ready()) {
            return WithdrawalResult.failed(WithdrawalStatus.LOGISTICS_PREEMPTED);
        }
        ItemStack liveGun = activeRequestedGun(request, loadout);
        if (liveGun.isEmpty()) {
            return WithdrawalResult.failed(WithdrawalStatus.WEAPON_CHANGED);
        }

        GuardAmmoSummary.Snapshot liveSummary = GuardAmmoSummary.capture(liveGun, destination);
        if (!liveSummary.isReady()
                || liveSummary.gunId() == null
                || !request.matchesLiveWeapon(
                        level.dimension().location(),
                        request.weaponRole(),
                        liveSummary.gunId())
                || liveSummary.magazineCapacity() != request.magazineCapacity()) {
            return WithdrawalResult.failed(WithdrawalStatus.WEAPON_CHANGED);
        }
        if (liveSummary.reserveSource() != GuardAmmoSummary.ReserveSource.PHYSICAL
                || liveSummary.infiniteReserve()
                || !liveSummary.reserveScanComplete()) {
            return WithdrawalResult.failed(WithdrawalStatus.RESERVE_UNAVAILABLE);
        }
        long remainingDemand = remainingDemand(request, liveSummary.reserveUnits());
        if (remainingDemand <= 0L) {
            return WithdrawalResult.failed(WithdrawalStatus.NEED_SATISFIED);
        }

        int sourceSlot = reservation.target().sourceSlot();
        ItemStack source;
        try {
            if (sourceSlot < 0 || sourceSlot >= armory.getInventory().getSlots()) {
                return WithdrawalResult.failed(WithdrawalStatus.SOURCE_CHANGED);
            }
            source = armory.getInventory().getStackInSlot(sourceSlot);
            if (!reservation.fingerprint().matches(source)) {
                return WithdrawalResult.failed(WithdrawalStatus.SOURCE_CHANGED);
            }
            source = source.copy();
        } catch (RuntimeException exception) {
            return WithdrawalResult.failed(WithdrawalStatus.SOURCE_CHANGED);
        }

        AmmoCompatibility.Assessment compatibility = AmmoCompatibility.assess(liveGun, source);
        if (!compatibility.isCompatible()
                || compatibility.infinite()
                || compatibility.units() <= 0L
                || compatibility.kind() != reservation.kind()) {
            return WithdrawalResult.failed(WithdrawalStatus.INCOMPATIBLE_AMMUNITION);
        }
        long expectedBoxUnits = 0L;
        if (reservation.kind() == AmmoCompatibility.AmmoKind.BOX) {
            ItemStack singleBox = source.copy();
            singleBox.setCount(1);
            AmmoCompatibility.Assessment singleBoxCompatibility =
                    AmmoCompatibility.assess(liveGun, singleBox);
            if (!singleBoxCompatibility.isCompatible()
                    || singleBoxCompatibility.infinite()
                    || singleBoxCompatibility.units() <= 0L
                    || singleBoxCompatibility.kind() != AmmoCompatibility.AmmoKind.BOX) {
                return WithdrawalResult.failed(WithdrawalStatus.INCOMPATIBLE_AMMUNITION);
            }
            expectedBoxUnits = singleBoxCompatibility.units();
        }
        int maximumItems = transferableItems(reservation, source, remainingDemand);
        if (maximumItems <= 0) {
            return WithdrawalResult.failed(WithdrawalStatus.SOURCE_CHANGED);
        }

        GuardAmmoTransactions.TransferResult transfer = GuardAmmoTransactions.transfer(
                armory.getInventory(),
                sourceSlot,
                destination,
                maximumItems);
        if (!transfer.committed()) {
            return WithdrawalResult.failed(switch (transfer.status()) {
                case NO_OP -> WithdrawalStatus.DESTINATION_FULL;
                case INVALID_HANDLER -> WithdrawalStatus.TRANSACTION_INVALID;
                case ROLLED_BACK -> WithdrawalStatus.TRANSACTION_ROLLED_BACK;
                case ROLLBACK_FAILED -> WithdrawalStatus.TRANSACTION_ROLLBACK_FAILED;
                case COMMITTED -> throw new IllegalStateException(
                        "Transaction status contradicted its committed flag");
            });
        }

        long transferredUnits = reservation.kind() == AmmoCompatibility.AmmoKind.LOOSE
                ? transfer.transferred()
                : expectedBoxUnits;
        return new WithdrawalResult(
                WithdrawalStatus.TRANSFERRED,
                transfer.transferred(),
                transferredUnits,
                transfer.transferredStack());
    }

    private static ItemStack activeRequestedGun(
            ArmoryResupplyRequest request,
            GuardWeaponAmmunition.Loadout loadout) {
        if (loadout == null || loadout.recoveryRequired()) {
            return ItemStack.EMPTY;
        }
        if (request.weaponRole() == ArmoryResupplyWeaponRole.PRIMARY
                && loadout.activeRole() == GuardWeaponAmmunition.ActiveRole.PRIMARY) {
            return loadout.primary();
        }
        if (request.weaponRole() == ArmoryResupplyWeaponRole.SIDEARM
                && loadout.activeRole() == GuardWeaponAmmunition.ActiveRole.SIDEARM) {
            return loadout.sidearm();
        }
        return ItemStack.EMPTY;
    }

    private static long remainingDemand(
            ArmoryResupplyRequest request,
            long liveReserveUnits) {
        if (liveReserveUnits < 0L || liveReserveUnits >= request.targetReserveUnits()) {
            return 0L;
        }
        return Math.min(
                request.requestedUnits(),
                request.targetReserveUnits() - liveReserveUnits);
    }

    private static int transferableItems(
            ArmoryAmmoReservation reservation,
            ItemStack source,
            long remainingDemand) {
        int available = Math.min(source.getCount(), reservation.reservedItems());
        if (available <= 0) {
            return 0;
        }
        if (reservation.kind() == AmmoCompatibility.AmmoKind.BOX) {
            return 1;
        }
        return (int) Math.min((long) available, remainingDemand);
    }

    /** Unreadable TaCZ state is busy so logistics never interrupts it. */
    private static boolean nativeWeaponBusy(Guard guard) {
        try {
            IGunOperator operator = IGunOperator.fromLivingEntity(guard);
            var data = operator.getDataHolder();
            return data.reloadStateType == null
                    || data.reloadStateType.isReloading()
                    || data.isBolting
                    || data.meleePrepTickCount >= 0
                    || data.chargeProgress > 0.0F
                    || operator.getSynReloadState().getStateType().isReloading()
                    || operator.getSynIsBolting()
                    || operator.getSynMeleeCoolDown() > 0L
                    || operator.getSynDrawCoolDown() > 0L
                    || operator.getSynShootCoolDown() > 0L;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    public enum WithdrawalStatus {
        TRANSFERRED,
        INVALID_INPUT,
        NO_RESERVATION,
        RESERVATION_EXPIRED,
        GUARD_UNAVAILABLE,
        DIMENSION_CHANGED,
        ARMORY_UNAVAILABLE,
        TOO_FAR_AWAY,
        SQUAD_CHANGED,
        ACCESS_DENIED,
        GUARD_INVENTORY_UNAVAILABLE,
        LOGISTICS_PREEMPTED,
        WEAPON_CHANGED,
        RESERVE_UNAVAILABLE,
        NEED_SATISFIED,
        SOURCE_CHANGED,
        INCOMPATIBLE_AMMUNITION,
        DESTINATION_FULL,
        TRANSACTION_INVALID,
        TRANSACTION_ROLLED_BACK,
        TRANSACTION_ROLLBACK_FAILED
    }

    public record WithdrawalResult(
            WithdrawalStatus status,
            int transferredItems,
            long transferredUnits,
            ItemStack transferredStack) {
        public WithdrawalResult {
            Objects.requireNonNull(status, "status");
            transferredStack = transferredStack == null
                    ? ItemStack.EMPTY
                    : transferredStack.copy();
            boolean transferred = status == WithdrawalStatus.TRANSFERRED;
            if (transferred != (transferredItems > 0 && transferredUnits > 0L)
                    || (!transferred && (transferredItems != 0 || transferredUnits != 0L))) {
                throw new IllegalArgumentException(
                        "Transferred quantities contradict withdrawal status");
            }
        }

        @Override
        public ItemStack transferredStack() {
            return transferredStack.copy();
        }

        public boolean transferred() {
            return status == WithdrawalStatus.TRANSFERRED;
        }

        private static WithdrawalResult failed(WithdrawalStatus status) {
            return new WithdrawalResult(status, 0, 0L, ItemStack.EMPTY);
        }
    }
}
