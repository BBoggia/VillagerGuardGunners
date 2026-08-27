package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class NativeEngagementRangeTest {
    @Test
    void effectiveRangeIsSanitizedForNavigation() {
        assertAll(
                () -> assertEquals(16.0F, NativeGunController.normalizeEngagementRange(Float.NaN, 32.0F)),
                () -> assertEquals(16.0F, NativeGunController.normalizeEngagementRange(-1.0F, 32.0F)),
                () -> assertEquals(8.0F, NativeGunController.normalizeEngagementRange(2.0F, 32.0F)),
                () -> assertEquals(24.0F, NativeGunController.normalizeEngagementRange(24.0F, 32.0F)),
                () -> assertEquals(32.0F, NativeGunController.normalizeEngagementRange(Float.MAX_VALUE, 32.0F)),
                () -> assertEquals(64.0F, NativeGunController.normalizeEngagementRange(Float.MAX_VALUE, 64.0F)),
                () -> assertEquals(12.0F, NativeGunController.normalizeEngagementRange(24.0F, 12.0F)),
                () -> assertEquals(32.0F, NativeGunController.normalizeEngagementRange(48.0F, Float.NaN)));
    }

    @Test
    void configuredMaximumIsAnIndependentHardFiringCap() {
        assertAll(
                () -> assertEquals(32.0F, NativeGunController.normalizeMaximumFiringRange(Float.NaN)),
                () -> assertEquals(32.0F, NativeGunController.normalizeMaximumFiringRange(-1.0F)),
                () -> assertEquals(8.0F, NativeGunController.normalizeMaximumFiringRange(8.0F)),
                () -> assertEquals(32.0F, NativeGunController.normalizeMaximumFiringRange(32.0F)),
                () -> assertEquals(64.0F, NativeGunController.normalizeMaximumFiringRange(64.0F)));
    }
}
