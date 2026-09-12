package com.gamergaming.villagerguardgunners.village.coverage;

import static org.junit.jupiter.api.Assertions.*;

import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageReservations.Kind;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VillageCoverageReservationsTest {
    private final VillageCoverageReservations book = new VillageCoverageReservations();

    @Test void guardAndTargetClaimsAreExclusiveAcrossVillagesAndWorkKinds() {
        var lease = book.claim(id(1), id(2), id(3), id(4), Kind.COVERAGE, 0, 40);
        assertNotNull(lease);
        assertNull(book.claim(id(1), id(5), id(3), id(6), Kind.COVERAGE, 0, 40));
        assertNull(book.claim(id(1), id(2), id(7), id(8), Kind.FIXED_POST, 0, 40));
        assertNull(book.claim(id(9), id(2), id(10), id(11), Kind.COVERAGE, 0, 40));
        assertEquals(1, book.size());
        assertTrue(book.release(lease));
        assertFalse(book.release(lease));
        assertEquals(0, book.size());
    }

    @Test void expiryPermitsReplacementButStaleCallbacksCannotReleaseIt() {
        var first = book.claim(id(1), id(2), id(3), id(4), Kind.COVERAGE, 0, 40);
        var next = book.claim(id(1), id(2), id(3), id(4), Kind.COVERAGE, 40, 40);
        assertNotNull(next);
        assertNotEquals(first.token(), next.token());
        assertFalse(book.release(first));
        assertEquals(next, book.forGuard(id(2)));
        book.releaseVillage(id(1));
        assertTrue(book.forVillage(id(1)).isEmpty());
        assertEquals(0, book.size());
    }

    @Test void capacityDoesNotEvictLiveClaimsAndVillageReleaseIsScoped() {
        for (int i = 0; i < VillageCoverageReservations.MAX_LEASES; i++) {
            assertNotNull(book.claim(id(i % 2), id(100 + i), id(10_000 + i), id(20_000 + i), Kind.COVERAGE, 0, 40));
        }
        assertNull(book.claim(id(5), id(999_999), id(1), id(2), Kind.COVERAGE, 1, 40));
        book.releaseVillage(id(0));
        assertEquals(VillageCoverageReservations.MAX_LEASES / 2, book.size());
        assertNotNull(book.claim(id(5), id(999_999), id(1), id(2), Kind.COVERAGE, 1, 40));
        book.clear();
        assertEquals(0, book.size());
        assertTrue(book.forVillage(id(1)).isEmpty());
    }

    @Test void invalidDurationCannotExpireOrReplaceAnExistingClaim() {
        var lease = book.claim(id(1), id(2), id(3), id(4), Kind.COVERAGE, 0, 40);
        assertThrows(IllegalArgumentException.class, () -> book.claim(id(1), id(2), id(3), id(4), Kind.COVERAGE, 50, 24_001));
        assertThrows(IllegalArgumentException.class, () -> book.claim(id(1), id(2), id(3), id(4), Kind.COVERAGE, Long.MAX_VALUE, 40));
        assertEquals(lease, book.forGuard(id(2)));
    }

    @Test void expiredAbandonedVillagesCannotPermanentlyExhaustCapacity() {
        for (int i = 0; i < VillageCoverageReservations.MAX_LEASES; i++) {
            book.claim(id(i), id(100 + i), id(10_000 + i), id(20_000 + i), Kind.COVERAGE, 0, 40);
        }
        assertNotNull(book.claim(id(999_999), id(999_999), id(1), id(2), Kind.COVERAGE, 40, 40));
        assertEquals(VillageCoverageReservations.MAX_LEASES - VillageCoverageReservations.MAX_EXPIRATIONS_PER_CLAIM + 1,
                book.size());
    }

    private static UUID id(long number) { return new UUID(0, number); }
}
