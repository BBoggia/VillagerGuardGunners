package com.gamergaming.villagerguardgunners.armory.resupply;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ArmoryResupplySettingsTest {

    @Test
    void snapshotExposesBoundedDerivedValuesWithoutOwningRuntimeState() {
        ArmoryResupplySettings settings = settings(1, 3);

        assertAll(
                () -> assertTrue(settings.enabled()),
                () -> assertTrue(settings.validReserveThresholds()),
                () -> assertEquals(48.0D * 48.0D, settings.searchRadiusSquared()));
    }

    @Test
    void targetBelowTriggerIsRepresentableButFailsTheNeedBoundaryClosed() {
        ArmoryResupplySettings settings = settings(4, 3);

        assertAll(
                () -> assertFalse(settings.validReserveThresholds()),
                () -> assertEquals(
                        ArmoryResupplyNeed.NeedStatus.INVALID_THRESHOLDS,
                        ArmoryResupplyNeed.assess(null, 4, 3).status()));
    }

    @Test
    void constructorRejectsValuesOutsideEveryHardSafetyFamily() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(0, 3, 0, 48.0D, 8, 0.8D, 1200, 400, 100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 201, 48.0D, 8, 0.8D, 1200, 400, 100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 40, 65.0D, 8, 0.8D, 1200, 400, 100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 40, 48.0D, 33, 0.8D, 1200, 400, 100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(-1, 3)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 40, 48.0D, 8, 0.0D, 1200, 400, 100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 40, 48.0D, 8, 0.09D, 1200, 400, 100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 40, 48.0D, 8, 0.8D, 2401, 400, 100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 40, 48.0D, 8, 0.8D, 19, 400, 100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 40, 48.0D, 8, 0.8D, 1200, 1201, 100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 40, 48.0D, 8, 0.8D, 1200, 19, 100)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 40, 48.0D, 8, 0.8D, 1200, 400, 2401)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> settings(1, 3, 40, 48.0D, 8, 0.8D, 1200, 400, 19)));
    }

    private static ArmoryResupplySettings settings(int low, int target) {
        return settings(low, target, 40, 48.0D, 8, 0.8D, 1200, 400, 100);
    }

    private static ArmoryResupplySettings settings(
            int low,
            int target,
            int interval,
            double radius,
            int candidates,
            double speed,
            int requestLifetime,
            int reservationLifetime,
            int retry) {
        return new ArmoryResupplySettings(
                true,
                interval,
                radius,
                candidates,
                low,
                target,
                speed,
                requestLifetime,
                reservationLifetime,
                retry,
                retry,
                retry);
    }
}
