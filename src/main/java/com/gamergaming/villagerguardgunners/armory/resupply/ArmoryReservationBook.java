package com.gamergaming.villagerguardgunners.armory.resupply;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-thread reservation ledger with bounded size and lazy expiry.
 *
 * <p>The book stores no entity, level, block-entity, or item-handler reference,
 * has no ticker, and is intentionally not serializable. Synchronization makes
 * ownership atomic for tests and defensive integrations even though normal
 * access is expected on the Minecraft server thread.</p>
 */
public final class ArmoryReservationBook {
    private final Map<ArmoryResupplyRequest.RequestKey, ArmoryAmmoReservation>
            reservations = new LinkedHashMap<>();

    public synchronized ReservationResult reserve(
            ArmoryResupplyRequest request,
            ArmorySupplyCandidate candidate,
            long gameTick,
            int lifetimeTicks) {
        if (request == null || candidate == null || gameTick < 0L) {
            return ReservationResult.failed(ReservationStatus.INVALID_INPUT);
        }
        purgeExpiredInternal(gameTick);
        if (request.isExpired(gameTick)) {
            return ReservationResult.failed(ReservationStatus.REQUEST_EXPIRED);
        }
        if (lifetimeTicks <= 0
                || lifetimeTicks > ArmoryResupplyLimits.HARD_MAX_RESERVATION_LIFETIME_TICKS
                || gameTick > Long.MAX_VALUE - lifetimeTicks) {
            return ReservationResult.failed(ReservationStatus.INVALID_LIFETIME);
        }
        if (!request.dimension().equals(candidate.target().dimension())) {
            return ReservationResult.failed(ReservationStatus.INVALID_TARGET);
        }

        ArmoryAmmoReservation existing = reservations.get(request.key());
        if (existing != null) {
            return new ReservationResult(ReservationStatus.ALREADY_RESERVED, existing);
        }
        if (reservations.size() >= ArmoryResupplyLimits.HARD_MAX_ACTIVE_RESERVATIONS) {
            return ReservationResult.failed(ReservationStatus.CAPACITY_REACHED);
        }

        int alreadyClaimed = reservedItemsFor(candidate);
        int unclaimedItems = candidate.availableItems() <= alreadyClaimed
                ? 0
                : candidate.availableItems() - alreadyClaimed;
        int reservedItems = Math.min(candidate.proposedItems(), unclaimedItems);
        if (reservedItems <= 0) {
            return ReservationResult.failed(ReservationStatus.FULLY_RESERVED);
        }

        long reservedUnits;
        if (candidate.kind() == AmmoCompatibility.AmmoKind.LOOSE) {
            reservedUnits = reservedItems;
        } else if (candidate.kind() == AmmoCompatibility.AmmoKind.BOX
                && reservedItems == 1) {
            reservedUnits = candidate.proposedUnits();
        } else {
            return ReservationResult.failed(ReservationStatus.INVALID_INPUT);
        }

        long requestedExpiry = gameTick + lifetimeTicks;
        long expiry = Math.min(requestedExpiry, request.expiresAtGameTick());
        if (expiry <= gameTick) {
            return ReservationResult.failed(ReservationStatus.REQUEST_EXPIRED);
        }
        ArmoryAmmoReservation reservation = new ArmoryAmmoReservation(
                request,
                candidate.target(),
                candidate.fingerprint(),
                candidate.kind(),
                reservedItems,
                reservedUnits,
                gameTick,
                expiry);
        reservations.put(request.key(), reservation);
        return new ReservationResult(ReservationStatus.RESERVED, reservation);
    }

    public synchronized Optional<ArmoryAmmoReservation> find(
            ArmoryResupplyRequest.RequestKey requestKey,
            long gameTick) {
        if (requestKey == null || gameTick < 0L) {
            return Optional.empty();
        }
        ArmoryAmmoReservation reservation = reservations.get(requestKey);
        if (reservation != null && reservation.isExpired(gameTick)) {
            reservations.remove(requestKey);
            reservation = null;
        }
        purgeExpiredInternal(gameTick);
        return Optional.ofNullable(reservation);
    }

    /** Atomically consumes ownership before the caller performs a transfer. */
    public synchronized ClaimResult claim(
            ArmoryResupplyRequest.RequestKey requestKey,
            long gameTick) {
        if (requestKey == null || gameTick < 0L) {
            return ClaimResult.failed(ClaimStatus.INVALID_INPUT);
        }
        ArmoryAmmoReservation reservation = reservations.remove(requestKey);
        if (reservation == null) {
            purgeExpiredInternal(gameTick);
            return ClaimResult.failed(ClaimStatus.NOT_FOUND);
        }
        if (reservation.isExpired(gameTick)) {
            purgeExpiredInternal(gameTick);
            return ClaimResult.failed(ClaimStatus.EXPIRED);
        }
        purgeExpiredInternal(gameTick);
        return new ClaimResult(ClaimStatus.CLAIMED, reservation);
    }

    public synchronized boolean release(ArmoryResupplyRequest.RequestKey requestKey) {
        return requestKey != null && reservations.remove(requestKey) != null;
    }

    public synchronized int releaseGuard(UUID guardId) {
        if (guardId == null) {
            return 0;
        }
        return removeMatching(reservation -> reservation.request().guardId().equals(guardId));
    }

    public synchronized int releaseArmory(
            ResourceLocation dimension,
            BlockPos position) {
        if (dimension == null || position == null) {
            return 0;
        }
        return removeMatching(reservation -> reservation.target().dimension().equals(dimension)
                && reservation.target().position().equals(position));
    }

    public synchronized int purgeExpired(long gameTick) {
        if (gameTick < 0L) {
            return 0;
        }
        return purgeExpiredInternal(gameTick);
    }

    public synchronized int activeCount(long gameTick) {
        if (gameTick < 0L) {
            return 0;
        }
        purgeExpiredInternal(gameTick);
        return reservations.size();
    }

    public synchronized void clear() {
        reservations.clear();
    }

    private int reservedItemsFor(ArmorySupplyCandidate candidate) {
        int claimed = 0;
        for (ArmoryAmmoReservation reservation : reservations.values()) {
            if (!reservation.target().equals(candidate.target())
                    || !reservation.fingerprint().equals(candidate.fingerprint())) {
                continue;
            }
            if (Integer.MAX_VALUE - claimed < reservation.reservedItems()) {
                return Integer.MAX_VALUE;
            }
            claimed += reservation.reservedItems();
        }
        return claimed;
    }

    private int purgeExpiredInternal(long gameTick) {
        return removeMatching(reservation -> reservation.isExpired(gameTick));
    }

    private int removeMatching(ReservationPredicate predicate) {
        Objects.requireNonNull(predicate, "predicate");
        int removed = 0;
        Iterator<ArmoryAmmoReservation> iterator = reservations.values().iterator();
        while (iterator.hasNext()) {
            if (predicate.test(iterator.next())) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public enum ReservationStatus {
        RESERVED,
        ALREADY_RESERVED,
        INVALID_INPUT,
        REQUEST_EXPIRED,
        INVALID_LIFETIME,
        INVALID_TARGET,
        CAPACITY_REACHED,
        FULLY_RESERVED
    }

    public record ReservationResult(
            ReservationStatus status,
            @Nullable ArmoryAmmoReservation reservation) {
        public ReservationResult {
            Objects.requireNonNull(status, "status");
            boolean hasReservation = status == ReservationStatus.RESERVED
                    || status == ReservationStatus.ALREADY_RESERVED;
            if (hasReservation != (reservation != null)) {
                throw new IllegalArgumentException(
                        "Reservation presence contradicts reservation status");
            }
        }

        public boolean reserved() {
            return status == ReservationStatus.RESERVED
                    || status == ReservationStatus.ALREADY_RESERVED;
        }

        private static ReservationResult failed(ReservationStatus status) {
            return new ReservationResult(status, null);
        }
    }

    public enum ClaimStatus {
        CLAIMED,
        NOT_FOUND,
        EXPIRED,
        INVALID_INPUT
    }

    public record ClaimResult(
            ClaimStatus status,
            @Nullable ArmoryAmmoReservation reservation) {
        public ClaimResult {
            Objects.requireNonNull(status, "status");
            if ((status == ClaimStatus.CLAIMED) != (reservation != null)) {
                throw new IllegalArgumentException(
                        "Reservation presence contradicts claim status");
            }
        }

        public boolean claimed() {
            return status == ClaimStatus.CLAIMED;
        }

        private static ClaimResult failed(ClaimStatus status) {
            return new ClaimResult(status, null);
        }
    }

    @FunctionalInterface
    private interface ReservationPredicate {
        boolean test(ArmoryAmmoReservation reservation);
    }
}
