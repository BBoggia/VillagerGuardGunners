package com.gamergaming.villagerguardgunners.ai.navigation;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PassageReservationsTest {
    @Test void occupantKeepsPassageAndWaitingOrderSurvivesRepeatedRequests() {
        var book = new PassageReservations<String>();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        assertTrue(book.request("bridge", a, 0));
        assertFalse(book.request("bridge", b, 1));
        assertFalse(book.request("bridge", c, 2));
        assertTrue(book.request("bridge", a, 20));
        assertFalse(book.request("bridge", b, 20));
        book.release(a);
        assertFalse(book.request("bridge", c, 21));
        assertTrue(book.request("bridge", b, 21));
        book.release(b);
        assertTrue(book.request("bridge", c, 22));
    }
    @Test void expiredAndInterruptedClaimsDoNotBlockLaterTraffic() {
        var book = new PassageReservations<String>();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(book.request("door", a, 0));
        assertFalse(book.request("door", b, 10));
        assertTrue(book.request("door", b, 30));
        book.release(b);
        assertEquals(0, book.size());
        assertTrue(book.request("door", a, 31));
        book.expire(61);
        assertEquals(0, book.size());
    }

    @Test void releasesAndAdmissionsRemainImmediateWithinOneTick() {
        var book = new PassageReservations<String>();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        assertTrue(book.request("bridge", a, 10));
        assertFalse(book.request("bridge", b, 10));
        assertFalse(book.request("bridge", c, 10));
        book.expire(10);
        book.release(a);
        assertFalse(book.request("bridge", c, 10));
        assertTrue(book.request("bridge", b, 10));
        book.release(b);
        assertTrue(book.request("bridge", c, 10));
        assertTrue(book.request("bridge", c, 20));
        assertFalse(book.request("bridge", a, 49));
        assertTrue(book.request("bridge", a, 50));
    }

    @Test void tickBoundaryExpiresEveryPassageAndKeepsLiveWaitersInOrder() {
        var book = new PassageReservations<Integer>();
        UUID owner = UUID.randomUUID(), waiter = UUID.randomUUID(), newcomer = UUID.randomUUID();
        for (int i = 0; i < PassageReservations.MAX_PASSAGES; i++) assertTrue(book.request(i, owner, 0));
        assertFalse(book.request(0, waiter, 1));
        assertFalse(book.request(1000, newcomer, 1));
        assertTrue(book.request(1000, newcomer, 30));
        assertEquals(2, book.size());
        assertFalse(book.request(0, newcomer, 30));
        assertTrue(book.request(0, waiter, 30));
        book.expire(60);
        assertEquals(0, book.size());
    }
}
