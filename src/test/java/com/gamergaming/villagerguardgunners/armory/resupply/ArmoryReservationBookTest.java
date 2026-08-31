package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.ammunition.AmmoCompatibility;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class ArmoryReservationBookTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation GUN_ID =
            ResourceLocation.fromNamespaceAndPath("test", "rifle");
    private static final ResourceLocation AMMO_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath("test", "rifle_ammo");
    private static final ArmoryReservationTarget TARGET =
            new ArmoryReservationTarget(DIMENSION, new BlockPos(8, 70, -4), 2);
    private static final ArmorySupplyFingerprint FINGERPRINT =
            new ArmorySupplyFingerprint(AMMO_ITEM_ID, null);

    @Test
    void concurrentLooseClaimsCannotExceedTheCandidateSnapshot() {
        ArmoryReservationBook book = new ArmoryReservationBook();
        ArmorySupplyCandidate candidate = looseCandidate(48, 32);

        ArmoryReservationBook.ReservationResult first = book.reserve(
                request(1), candidate, 10L, 100);
        ArmoryReservationBook.ReservationResult second = book.reserve(
                request(2), candidate, 10L, 100);
        ArmoryReservationBook.ReservationResult third = book.reserve(
                request(3), candidate, 10L, 100);

        assertEquals(ArmoryReservationBook.ReservationStatus.RESERVED, first.status());
        assertEquals(32, first.reservation().reservedItems());
        assertEquals(ArmoryReservationBook.ReservationStatus.RESERVED, second.status());
        assertEquals(16, second.reservation().reservedItems());
        assertEquals(ArmoryReservationBook.ReservationStatus.FULLY_RESERVED, third.status());
        assertEquals(2, book.activeCount(10L));
    }

    @Test
    void duplicateRequestIsIdempotentAndClaimIsExactlyOnce() {
        ArmoryReservationBook book = new ArmoryReservationBook();
        ArmoryResupplyRequest request = request(1);
        ArmoryReservationBook.ReservationResult created = book.reserve(
                request, looseCandidate(64, 32), 20L, 80);
        ArmoryReservationBook.ReservationResult repeated = book.reserve(
                request, looseCandidate(64, 8), 21L, 80);

        assertEquals(ArmoryReservationBook.ReservationStatus.RESERVED, created.status());
        assertEquals(ArmoryReservationBook.ReservationStatus.ALREADY_RESERVED, repeated.status());
        assertEquals(created.reservation(), repeated.reservation());
        assertEquals(
                ArmoryReservationBook.ClaimStatus.CLAIMED,
                book.claim(request.key(), 22L).status());
        assertEquals(
                ArmoryReservationBook.ClaimStatus.NOT_FOUND,
                book.claim(request.key(), 22L).status());
        assertEquals(0, book.activeCount(22L));
    }

    @Test
    void expiryIsClampedToTheRequestAndReleasesContentionLazily() {
        ArmoryReservationBook book = new ArmoryReservationBook();
        ArmoryResupplyRequest endingSoon = request(1, 0L, 50L);
        ArmoryReservationBook.ReservationResult result = book.reserve(
                endingSoon, looseCandidate(32, 32), 40L, 30);

        assertEquals(50L, result.reservation().expiresAtGameTick());
        assertTrue(book.find(endingSoon.key(), 49L).isPresent());
        assertFalse(book.find(endingSoon.key(), 50L).isPresent());
        assertEquals(0, book.activeCount(50L));

        ArmoryReservationBook.ReservationResult replacement = book.reserve(
                request(2, 0L, 200L), looseCandidate(32, 32), 50L, 30);
        assertTrue(replacement.reserved());
        assertEquals(32, replacement.reservation().reservedItems());
    }

    @Test
    void finiteBoxIsOneIndivisibleItemAndBulkReleaseIsBounded() {
        ArmoryReservationBook book = new ArmoryReservationBook();
        ArmorySupplyCandidate box = new ArmorySupplyCandidate(
                TARGET,
                FINGERPRINT,
                AmmoCompatibility.AmmoKind.BOX,
                1,
                1,
                120L);
        ArmoryReservationBook.ReservationResult result = book.reserve(
                request(1), box, 0L,
                ArmoryResupplyLimits.DEFAULT_RESERVATION_LIFETIME_TICKS);

        assertTrue(result.reserved());
        assertEquals(1, result.reservation().reservedItems());
        assertEquals(120L, result.reservation().reservedUnits());
        assertEquals(1, book.releaseArmory(DIMENSION, TARGET.position()));
        assertEquals(0, book.releaseGuard(request(1).guardId()));
    }

    @Test
    void invalidTimesAndMismatchedDimensionsFailClosed() {
        ArmoryReservationBook book = new ArmoryReservationBook();
        ArmorySupplyCandidate wrongDimension = new ArmorySupplyCandidate(
                new ArmoryReservationTarget(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"),
                        TARGET.position(),
                        TARGET.sourceSlot()),
                FINGERPRINT,
                AmmoCompatibility.AmmoKind.LOOSE,
                8,
                8,
                8L);

        assertEquals(
                ArmoryReservationBook.ReservationStatus.INVALID_TARGET,
                book.reserve(request(1), wrongDimension, 0L, 20).status());
        assertEquals(
                ArmoryReservationBook.ReservationStatus.INVALID_LIFETIME,
                book.reserve(request(1), looseCandidate(8, 8), 0L, 0).status());
        assertEquals(
                ArmoryReservationBook.ClaimStatus.INVALID_INPUT,
                book.claim(request(1).key(), -1L).status());
    }

    @Test
    void globalReservationCapacityIsAHardCeiling() {
        ArmoryReservationBook book = new ArmoryReservationBook();
        for (int index = 0;
                index < ArmoryResupplyLimits.HARD_MAX_ACTIVE_RESERVATIONS;
                index++) {
            ArmoryReservationTarget target = new ArmoryReservationTarget(
                    DIMENSION,
                    new BlockPos(index, 70, 0),
                    0);
            ArmorySupplyCandidate candidate = new ArmorySupplyCandidate(
                    target,
                    FINGERPRINT,
                    AmmoCompatibility.AmmoKind.LOOSE,
                    1,
                    1,
                    1L);
            assertTrue(book.reserve(request(index + 1L), candidate, 0L, 20).reserved());
        }

        ArmorySupplyCandidate overflow = new ArmorySupplyCandidate(
                new ArmoryReservationTarget(
                        DIMENSION,
                        new BlockPos(2_000, 70, 0),
                        0),
                FINGERPRINT,
                AmmoCompatibility.AmmoKind.LOOSE,
                1,
                1,
                1L);
        assertEquals(
                ArmoryReservationBook.ReservationStatus.CAPACITY_REACHED,
                book.reserve(
                                request(ArmoryResupplyLimits.HARD_MAX_ACTIVE_RESERVATIONS + 1L),
                                overflow,
                                0L,
                                20)
                        .status());
        assertEquals(
                ArmoryResupplyLimits.HARD_MAX_ACTIVE_RESERVATIONS,
                book.activeCount(0L));
    }

    private static ArmorySupplyCandidate looseCandidate(int available, int proposed) {
        return new ArmorySupplyCandidate(
                TARGET,
                FINGERPRINT,
                AmmoCompatibility.AmmoKind.LOOSE,
                available,
                proposed,
                proposed);
    }

    private static ArmoryResupplyRequest request(long generation) {
        return request(generation, 0L, 1_200L);
    }

    private static ArmoryResupplyRequest request(
            long generation,
            long createdAt,
            long expiresAt) {
        return new ArmoryResupplyRequest(
                ArmoryResupplyRequest.CURRENT_CONTRACT_VERSION,
                generation,
                new UUID(0x1000000000000000L + generation, generation + 1L),
                DIMENSION,
                BlockPos.ZERO,
                null,
                ArmoryResupplyWeaponRole.PRIMARY,
                GUN_ID,
                ArmoryResupplyUrgency.LOW_RESERVE,
                30,
                10,
                29L,
                90L,
                61L,
                createdAt,
                expiresAt);
    }
}
