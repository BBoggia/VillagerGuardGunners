package com.gamergaming.villagerguardgunners.compat.pointblank;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PointBlankTraitsTest {
    /** Guns the mod ships, with the values from its own item files. */
    @Test
    void shippedGunsGetTheExpectedWeaponClass() {
        assertAll(
                () -> assertEquals("pistol", PointBlankTraits.inferType(0, 17, 800, false, true, 5.0F), "glock17"),
                () -> assertEquals("pistol", PointBlankTraits.inferType(0, 8, 500, false, true, 10.0F), "deserteagle"),
                () -> assertEquals("smg", PointBlankTraits.inferType(0, 20, 1100, true, false, 5.0F), "glock18"),
                () -> assertEquals("smg", PointBlankTraits.inferType(0, 30, 800, true, false, 3.5F), "mp5"),
                () -> assertEquals("rifle", PointBlankTraits.inferType(0, 30, 600, true, false, 7.0F), "ak47"),
                () -> assertEquals("rifle", PointBlankTraits.inferType(0, 30, 750, false, false, 8.0F), "star15"),
                () -> assertEquals("dmr", PointBlankTraits.inferType(0, 20, 500, false, true, 15.0F), "uar10"),
                () -> assertEquals("sniper", PointBlankTraits.inferType(0, 5, 43, false, true, 28.0F), "l96a1"),
                () -> assertEquals("sniper", PointBlankTraits.inferType(0, 6, 181, false, true, 22.0F), "wa2000"),
                () -> assertEquals("shotgun", PointBlankTraits.inferType(30, 6, 110, false, true, 1.9F), "m870"),
                () -> assertEquals("shotgun", PointBlankTraits.inferType(30, 32, 400, true, false, 1.0F), "aa12"),
                () -> assertEquals("mg", PointBlankTraits.inferType(0, 100, 800, true, false, 5.0F), "m249"),
                () -> assertEquals("mg", PointBlankTraits.inferType(0, 200, 2000, true, false, 5.0F), "m134minigun"));
    }

    @Test
    void safetyConeMatchesTheModsPerComponentSpread() {
        // The mod moves each direction component by up to its inaccuracy: at most sqrt(3) times that in total.
        double coneError = Math.sqrt(3.0D) * 0.0172275D * PointBlankTraits.safetyInaccuracy(0.03D);

        assertEquals(Math.sqrt(3.0D) * 0.03D, coneError, 1.0E-12);
        assertEquals(0.0D, PointBlankTraits.safetyInaccuracy(0.0D));
        assertEquals(0.0D, PointBlankTraits.safetyInaccuracy(-0.1D));
        assertEquals(0.0D, PointBlankTraits.safetyInaccuracy(Double.NaN));
    }

    @Test
    void hitScanRangeIsTheGunsRangeUpToTheMaximum() {
        assertEquals(50.0D, PointBlankTraits.hitScanRange(50.0D, 0.0D));
        assertEquals(PointBlankTraits.MAX_HIT_SCAN_RANGE, PointBlankTraits.hitScanRange(200.0D, 0.0D));
        assertEquals(PointBlankTraits.MAX_HIT_SCAN_RANGE, PointBlankTraits.hitScanRange(200.0D, 0.03D));
    }

    @Test
    void wideSpreadShortensTheRangeSoTheConeStaysCheckable() {
        double inaccuracy = 0.2D;
        double range = PointBlankTraits.hitScanRange(200.0D, inaccuracy);
        double error = Math.sqrt(3.0D) * inaccuracy;

        assertTrue(range < PointBlankTraits.MAX_HIT_SCAN_RANGE);
        assertEquals(PointBlankTraits.MAX_CONE_RADIUS, range * error / (1.0D - error), 1.0E-9);
    }

    @Test
    void unusableGunsHaveNoRange() {
        assertAll(
                () -> assertTrue(Double.isNaN(PointBlankTraits.hitScanRange(0.0D, 0.0D))),
                () -> assertTrue(Double.isNaN(PointBlankTraits.hitScanRange(Double.NaN, 0.0D))),
                () -> assertTrue(Double.isNaN(PointBlankTraits.hitScanRange(200.0D, -0.1D))),
                // Spread so wide the cone can never be covered.
                () -> assertTrue(Double.isNaN(PointBlankTraits.hitScanRange(200.0D, 0.6D))),
                // Range left after shortening is below the minimum.
                () -> assertTrue(Double.isNaN(PointBlankTraits.hitScanRange(200.0D, 0.45D))),
                () -> assertTrue(Double.isNaN(PointBlankTraits.hitScanRange(4.0D, 0.0D))));
    }

    @Test
    void fireRateComesFromRoundsPerMinute() {
        assertEquals(100, PointBlankTraits.fireIntervalMillis(600));
        assertEquals(30, PointBlankTraits.fireIntervalMillis(2000));
        assertEquals(-1, PointBlankTraits.fireIntervalMillis(0));
        assertEquals(-1, PointBlankTraits.fireIntervalMillis(-1));
    }

    @Test
    void reloadTimeIsRoundedUpToTicksWithADefaultAndACap() {
        assertEquals(49, PointBlankTraits.reloadTicks(2_450L));
        assertEquals(1, PointBlankTraits.reloadTicks(1L));
        assertEquals(PointBlankTraits.DEFAULT_RELOAD_TICKS, PointBlankTraits.reloadTicks(0L));
        assertEquals(PointBlankTraits.MAX_RELOAD_TICKS, PointBlankTraits.reloadTicks(600_000L));
    }
}
