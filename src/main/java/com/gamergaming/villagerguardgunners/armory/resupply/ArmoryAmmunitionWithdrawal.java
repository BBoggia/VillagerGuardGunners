package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoTransactions;
import com.gamergaming.villagerguardgunners.ammunition.GuardWeaponAmmunition;
import com.gamergaming.villagerguardgunners.api.GuardAmmoAccess;
import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.armory.ArmoryBlockEntity;
import com.gamergaming.villagerguardgunners.inventory.GuardAmmoHandler;
import java.util.Objects;
import javax.annotation.Nullable;
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
            Guard guard,
            boolean featureEnabled) {
        if (reservationBook == null || requestKey == null) {
            return WithdrawalResult.failed(WithdrawalStatus.INVALID_INPUT);
        }
        if (guard == null) {
            reservationBook.release(requestKey);
            return WithdrawalResult.failed(WithdrawalStatus.INVALID_INPUT);
        }

        final long gameTick;
        try {
            gameTick = guard.level().getGameTime();
        } catch (RuntimeException exception) {
            reservationBook.release(requestKey);
            return WithdrawalResult.failed(WithdrawalStatus.STATE_UNAVAILABLE);
        }
        ArmoryReservationBook.ClaimResult claim = reservationBook.claim(requestKey, gameTick);
        if (!claim.claimed()) {
            return WithdrawalResult.failed(switch (claim.status()) {
                case EXPIRED -> WithdrawalStatus.RESERVATION_EXPIRED;
                case NOT_FOUND -> WithdrawalStatus.NO_RESERVATION;
                case INVALID_INPUT -> WithdrawalStatus.INVALID_INPUT;
                case CLAIMED -> throw new IllegalStateException("Claim status contradicted its payload");
            });
        }

        ArmoryAmmoReservation reservation = Objects.requireNonNull(claim.reservation());
        Preflight preflight = preflight(reservation, armory, guard, featureEnabled, true);
        if (!preflight.ready()) {
            return WithdrawalResult.failed(withdrawalStatus(preflight.status()));
        }

        GuardAmmoTransactions.TransferResult transfer = GuardAmmoTransactions.transfer(
                armory.getInventory(),
                preflight.sourceSlot(),
                Objects.requireNonNull(preflight.destination()),
                preflight.maximumItems());
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
                : preflight.expectedBoxUnits();
        return new WithdrawalResult(
                WithdrawalStatus.TRANSFERRED,
                transfer.transferred(),
                transferredUnits,
                transfer.transferredStack());
    }

    /**
     * Cadenced travel validation. It intentionally performs no claim or item
     * movement and skips only the final proximity requirement.
     */
    static LiveStatus validateForTravel(
            ArmoryAmmoReservation reservation,
            ArmoryBlockEntity armory,
            Guard guard,
            boolean featureEnabled) {
        return preflight(reservation, armory, guard, featureEnabled, false).status();
    }

    private static Preflight preflight(
            ArmoryAmmoReservation reservation,
            ArmoryBlockEntity armory,
            Guard guard,
            boolean featureEnabled,
            boolean requireProximity) {
        if (reservation == null || armory == null || guard == null) {
            return Preflight.failed(LiveStatus.INVALID_INPUT);
        }
        try {
            return preflightChecked(
                    reservation,
                    armory,
                    guard,
                    featureEnabled,
                    requireProximity);
        } catch (RuntimeException exception) {
            return Preflight.failed(LiveStatus.STATE_UNAVAILABLE);
        }
    }

    private static Preflight preflightChecked(
            ArmoryAmmoReservation reservation,
            ArmoryBlockEntity armory,
            Guard guard,
            boolean featureEnabled,
            boolean requireProximity) {
        ArmoryResupplyRequest request = reservation.request();
        if (!featureEnabled) {
            return Preflight.failed(LiveStatus.LOGISTICS_PREEMPTED);
        }
        if (!request.guardId().equals(guard.getUUID())
                || !guard.isAlive()
                || guard.isRemoved()
                || guard.isNoAi()
                || guard.level().isClientSide) {
            return Preflight.failed(LiveStatus.GUARD_UNAVAILABLE);
        }
        Level level = guard.level();
        if (!request.dimension().equals(level.dimension().location())
                || !reservation.target().dimension().equals(level.dimension().location())) {
            return Preflight.failed(LiveStatus.DIMENSION_CHANGED);
        }
        if (armory.isRemoved()
                || armory.getLevel() != level
                || !armory.getBlockPos().equals(reservation.target().position())
                || !level.hasChunkAt(reservation.target().position())
                || level.getBlockEntity(reservation.target().position()) != armory) {
            return Preflight.failed(LiveStatus.ARMORY_UNAVAILABLE);
        }
        if (requireProximity
                && (!finitePosition(guard)
                        || guard.distanceToSqr(
                                        reservation.target().position().getX() + 0.5D,
                                        reservation.target().position().getY() + 0.5D,
                                        reservation.target().position().getZ() + 0.5D)
                                > ArmoryResupplyLimits.HARD_MAX_WITHDRAWAL_DISTANCE_SQUARED)) {
            return Preflight.failed(LiveStatus.TOO_FAR_AWAY);
        }
        if (!Objects.equals(request.squadId(), GuardSquadAccess.getSquadId(guard))) {
            return Preflight.failed(LiveStatus.SQUAD_CHANGED);
        }
        if (!armory.maySupply(request.squadId())) {
            return Preflight.failed(LiveStatus.ACCESS_DENIED);
        }
        ArmoryResupplyGate.GateStatus liveGate =
                ArmoryResupplyLiveGate.evaluate(featureEnabled, guard);
        if (!liveGate.ready()) {
            return Preflight.failed(LiveStatus.LOGISTICS_PREEMPTED);
        }

        GuardAmmoHandler destination;
        GuardWeaponAmmunition.Loadout loadout;
        try {
            destination = GuardAmmoAccess.getAmmoHandler(guard);
            loadout = GuardWeaponAmmunition.captureLoadout(guard);
        } catch (RuntimeException exception) {
            return Preflight.failed(LiveStatus.GUARD_INVENTORY_UNAVAILABLE);
        }
        ItemStack liveGun = activeRequestedGun(request, loadout);
        if (liveGun.isEmpty()) {
            return Preflight.failed(LiveStatus.WEAPON_CHANGED);
        }

        GuardAmmoSummary.Snapshot liveSummary = GuardAmmoSummary.capture(liveGun, destination);
        if (!liveSummary.isReady()
                || liveSummary.gunId() == null
                || !request.matchesLiveWeapon(
                        level.dimension().location(),
                        request.weaponRole(),
                        liveSummary.gunId())
                || liveSummary.magazineCapacity() != request.magazineCapacity()) {
            return Preflight.failed(LiveStatus.WEAPON_CHANGED);
        }
        if (liveSummary.reserveSource() != GuardAmmoSummary.ReserveSource.PHYSICAL
                || liveSummary.infiniteReserve()
                || !liveSummary.reserveScanComplete()) {
            return Preflight.failed(LiveStatus.RESERVE_UNAVAILABLE);
        }
        long remainingDemand = remainingDemand(request, liveSummary.reserveUnits());
        if (remainingDemand <= 0L) {
            return Preflight.failed(LiveStatus.NEED_SATISFIED);
        }

        int sourceSlot = reservation.target().sourceSlot();
        ItemStack source;
        try {
            int slots = armory.getInventory().getSlots();
            if (sourceSlot < 0 || sourceSlot >= slots) {
                return Preflight.failed(LiveStatus.SOURCE_CHANGED);
            }
            source = armory.getInventory().getStackInSlot(sourceSlot);
            if (!reservation.fingerprint().matches(source)) {
                return Preflight.failed(LiveStatus.SOURCE_CHANGED);
            }
            source = source.copy();
        } catch (RuntimeException exception) {
            return Preflight.failed(LiveStatus.SOURCE_CHANGED);
        }

        AmmoCompatibility.Assessment compatibility = AmmoCompatibility.assess(liveGun, source);
        if (!compatibility.isCompatible()
                || compatibility.infinite()
                || compatibility.units() <= 0L
                || compatibility.kind() != reservation.kind()) {
            return Preflight.failed(LiveStatus.INCOMPATIBLE_AMMUNITION);
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
                return Preflight.failed(LiveStatus.INCOMPATIBLE_AMMUNITION);
            }
            expectedBoxUnits = singleBoxCompatibility.units();
        }
        int maximumItems = transferableItems(reservation, source, remainingDemand);
        if (maximumItems <= 0) {
            return Preflight.failed(LiveStatus.SOURCE_CHANGED);
        }
        return Preflight.ready(destination, sourceSlot, maximumItems, expectedBoxUnits);
    }

    private static boolean finitePosition(Guard guard) {
        return Double.isFinite(guard.getX())
                && Double.isFinite(guard.getY())
                && Double.isFinite(guard.getZ());
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

    private static WithdrawalStatus withdrawalStatus(LiveStatus status) {
        return switch (status) {
            case INVALID_INPUT -> WithdrawalStatus.INVALID_INPUT;
            case GUARD_UNAVAILABLE -> WithdrawalStatus.GUARD_UNAVAILABLE;
            case DIMENSION_CHANGED -> WithdrawalStatus.DIMENSION_CHANGED;
            case ARMORY_UNAVAILABLE -> WithdrawalStatus.ARMORY_UNAVAILABLE;
            case TOO_FAR_AWAY -> WithdrawalStatus.TOO_FAR_AWAY;
            case SQUAD_CHANGED -> WithdrawalStatus.SQUAD_CHANGED;
            case ACCESS_DENIED -> WithdrawalStatus.ACCESS_DENIED;
            case GUARD_INVENTORY_UNAVAILABLE ->
                    WithdrawalStatus.GUARD_INVENTORY_UNAVAILABLE;
            case STATE_UNAVAILABLE -> WithdrawalStatus.STATE_UNAVAILABLE;
            case LOGISTICS_PREEMPTED -> WithdrawalStatus.LOGISTICS_PREEMPTED;
            case WEAPON_CHANGED -> WithdrawalStatus.WEAPON_CHANGED;
            case RESERVE_UNAVAILABLE -> WithdrawalStatus.RESERVE_UNAVAILABLE;
            case NEED_SATISFIED -> WithdrawalStatus.NEED_SATISFIED;
            case SOURCE_CHANGED -> WithdrawalStatus.SOURCE_CHANGED;
            case INCOMPATIBLE_AMMUNITION -> WithdrawalStatus.INCOMPATIBLE_AMMUNITION;
            case READY -> throw new IllegalArgumentException("A ready preflight is not a failure");
        };
    }

    enum LiveStatus {
        READY,
        INVALID_INPUT,
        GUARD_UNAVAILABLE,
        DIMENSION_CHANGED,
        ARMORY_UNAVAILABLE,
        TOO_FAR_AWAY,
        SQUAD_CHANGED,
        ACCESS_DENIED,
        GUARD_INVENTORY_UNAVAILABLE,
        STATE_UNAVAILABLE,
        LOGISTICS_PREEMPTED,
        WEAPON_CHANGED,
        RESERVE_UNAVAILABLE,
        NEED_SATISFIED,
        SOURCE_CHANGED,
        INCOMPATIBLE_AMMUNITION
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
        STATE_UNAVAILABLE,
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
            transferredStack = transferredStack == null ? ItemStack.EMPTY : transferredStack.copy();
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

    private record Preflight(
            LiveStatus status,
            @Nullable GuardAmmoHandler destination,
            int sourceSlot,
            int maximumItems,
            long expectedBoxUnits) {
        private Preflight {
            Objects.requireNonNull(status, "status");
            boolean ready = status == LiveStatus.READY;
            if (ready != (destination != null
                    && sourceSlot >= 0
                    && maximumItems > 0
                    && expectedBoxUnits >= 0L)) {
                throw new IllegalArgumentException("Preflight fields contradict status");
            }
        }

        private boolean ready() {
            return status == LiveStatus.READY;
        }

        private static Preflight ready(
                GuardAmmoHandler destination,
                int sourceSlot,
                int maximumItems,
                long expectedBoxUnits) {
            return new Preflight(
                    LiveStatus.READY,
                    Objects.requireNonNull(destination, "destination"),
                    sourceSlot,
                    maximumItems,
                    expectedBoxUnits);
        }

        private static Preflight failed(LiveStatus status) {
            return new Preflight(status, null, -1, 0, -1L);
        }
    }
}
