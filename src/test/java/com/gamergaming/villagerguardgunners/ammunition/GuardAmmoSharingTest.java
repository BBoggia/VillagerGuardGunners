package com.gamergaming.villagerguardgunners.ammunition;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuardAmmoSharingTest {
    @Test
    void oneMagazineRequestAlwaysHasAUsablePositiveBound() {
        assertAll(
                () -> assertEquals(1, GuardAmmoSharing.requestedItems(0)),
                () -> assertEquals(1, GuardAmmoSharing.requestedItems(-1)),
                () -> assertEquals(17, GuardAmmoSharing.requestedItems(17)),
                () -> assertEquals(
                        Integer.MAX_VALUE,
                        GuardAmmoSharing.requestedItems(Integer.MAX_VALUE)));
    }

    @Test
    void donorReserveFloorUsesAttachmentAdjustedMagazineCapacity() {
        assertAll(
                () -> assertEquals(0L, GuardAmmoSharing.retainedReserveUnits(17, 0)),
                () -> assertEquals(17L, GuardAmmoSharing.retainedReserveUnits(17, 1)),
                () -> assertEquals(160L, GuardAmmoSharing.retainedReserveUnits(40, 4)),
                () -> assertEquals(2L, GuardAmmoSharing.retainedReserveUnits(0, 2)),
                () -> assertEquals(
                        Long.MAX_VALUE,
                        GuardAmmoSharing.retainedReserveUnits(-1, 1)));
    }

    @Test
    void ownerPolicyAllowsMatchingAndUnownedPairsOnlyWhenEnabled() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertAll(
                () -> assertTrue(GuardAmmoSharing.ownersMayShare(first, first, true)),
                () -> assertTrue(GuardAmmoSharing.ownersMayShare(null, null, true)),
                () -> assertFalse(GuardAmmoSharing.ownersMayShare(first, second, true)),
                () -> assertFalse(GuardAmmoSharing.ownersMayShare(first, null, true)),
                () -> assertTrue(GuardAmmoSharing.ownersMayShare(first, second, false)),
                () -> assertTrue(GuardAmmoSharing.ownersMayShare(first, null, false)));
    }
}
