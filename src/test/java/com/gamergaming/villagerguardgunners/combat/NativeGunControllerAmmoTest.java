package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.ammunition.GuardAmmoSummary;
import org.junit.jupiter.api.Test;

final class NativeGunControllerAmmoTest {
    @Test
    void failedReloadRetriesOnlyForAConfirmedUsableReserve() {
        GuardAmmoSummary.Snapshot completePhysical = snapshot(
                GuardAmmoSummary.ReserveSource.PHYSICAL, 10L, false, true);
        GuardAmmoSummary.Snapshot incompletePhysical = snapshot(
                GuardAmmoSummary.ReserveSource.PHYSICAL, 10L, false, false);
        GuardAmmoSummary.Snapshot emptyPhysical = snapshot(
                GuardAmmoSummary.ReserveSource.PHYSICAL, 0L, false, true);
        GuardAmmoSummary.Snapshot dummyReserve = snapshot(
                GuardAmmoSummary.ReserveSource.DUMMY, 10L, false, false);
        GuardAmmoSummary.Snapshot nativeInfinite = snapshot(
                GuardAmmoSummary.ReserveSource.NATIVE_INFINITE, 0L, true, false);

        assertAll(
                () -> assertTrue(NativeGunController.failedReloadHasUsableReserve(
                        true, completePhysical)),
                () -> assertFalse(NativeGunController.failedReloadHasUsableReserve(
                        false, completePhysical)),
                () -> assertFalse(NativeGunController.failedReloadHasUsableReserve(
                        true, incompletePhysical)),
                () -> assertFalse(NativeGunController.failedReloadHasUsableReserve(
                        true, emptyPhysical)),
                () -> assertTrue(NativeGunController.failedReloadHasUsableReserve(
                        true, dummyReserve)),
                () -> assertTrue(NativeGunController.failedReloadHasUsableReserve(
                        true, nativeInfinite)),
                () -> assertFalse(NativeGunController.failedReloadHasUsableReserve(
                        true, null)));
    }

    private static GuardAmmoSummary.Snapshot snapshot(
            GuardAmmoSummary.ReserveSource reserveSource,
            long reserveUnits,
            boolean infinite,
            boolean complete) {
        return new GuardAmmoSummary.Snapshot(
                GuardAmmoSummary.Status.READY,
                null,
                null,
                0,
                10,
                true,
                false,
                reserveUnits,
                infinite,
                reserveSource,
                reserveUnits > 0L || infinite ? 1 : 0,
                0,
                0,
                complete);
    }
}
