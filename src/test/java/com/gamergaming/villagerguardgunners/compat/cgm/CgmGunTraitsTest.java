package com.gamergaming.villagerguardgunners.compat.cgm;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CgmGunTraitsTest {
    /** The nine guns the mod ships, with the values from its own gun files. */
    @Test
    void shippedGunsGetTheExpectedWeaponClass() {
        assertAll(
                () -> assertEquals("pistol", CgmGunTraits.inferType("one_handed", false, 4, 1, false)),
                () -> assertEquals("smg", CgmGunTraits.inferType("one_handed", true, 2, 1, false)),
                () -> assertEquals("rifle", CgmGunTraits.inferType("two_handed", true, 3, 1, false)),
                () -> assertEquals("rifle", CgmGunTraits.inferType("two_handed", false, 8, 1, false)),
                () -> assertEquals("sniper", CgmGunTraits.inferType("two_handed", false, 40, 1, false)),
                () -> assertEquals("shotgun", CgmGunTraits.inferType("two_handed", false, 8, 5, false)),
                () -> assertEquals("mg", CgmGunTraits.inferType("mini_gun", true, 2, 1, false)),
                () -> assertEquals("rpg", CgmGunTraits.inferType("bazooka", false, 80, 1, true)),
                () -> assertEquals("rpg", CgmGunTraits.inferType("two_handed", false, 20, 1, true)));
    }

    @Test
    void explosiveAmmoDecidesTheClassBeforeGripOrPellets() {
        assertEquals("rpg", CgmGunTraits.inferType("one_handed", true, 2, 5, true));
        assertEquals("rifle", CgmGunTraits.inferType("", false, 5, 1, false));
    }

    @Test
    void safetyConeContainsTheModsWorstCaseSpread() {
        // Half the spread on pitch and on yaw at once: sqrt(2) * spread / 2 degrees off the aim.
        double worstAngle = Math.toRadians(Math.sqrt(2.0D) * 20.0D / 2.0D);
        double coneError = Math.sqrt(3.0D) * 0.0172275D * CgmGunTraits.safetyInaccuracy(20.0F);

        assertEquals(worstAngle, coneError, 1.0E-9);
        // The envelope widens by error / (1 - error) per block, which must cover tan(angle).
        assertTrue(coneError / (1.0D - coneError) >= Math.tan(worstAngle));
        assertTrue(coneError < 1.0D);
    }

    @Test
    void gunWithoutSpreadHasNoSafetyCone() {
        assertEquals(0.0D, CgmGunTraits.safetyInaccuracy(0.0F));
        assertEquals(0.0D, CgmGunTraits.safetyInaccuracy(-1.0F));
        assertEquals(0.0D, CgmGunTraits.safetyInaccuracy(Float.NaN));
    }

    @Test
    void checkedFlightIsTheProjectilesLifeCutOffAtTheMaximumTravel() {
        assertAll(
                // Shotgun: 10 blocks per tick for 5 ticks stays whole.
                () -> assertEquals(5, CgmGunTraits.safetyLifeTicks(10.0D, 5)),
                // Heavy rifle: 25 blocks per tick for 30 ticks is cut to 192 blocks.
                () -> assertEquals(8, CgmGunTraits.safetyLifeTicks(25.0D, 30)),
                () -> assertEquals(96, CgmGunTraits.safetyLifeTicks(2.0D, 150)),
                () -> assertEquals(1, CgmGunTraits.safetyLifeTicks(500.0D, 30)),
                () -> assertEquals(1, CgmGunTraits.safetyLifeTicks(0.0D, 30)),
                () -> assertEquals(1, CgmGunTraits.safetyLifeTicks(Double.NaN, 30)),
                () -> assertEquals(1, CgmGunTraits.safetyLifeTicks(10.0D, 0)));
    }

    @Test
    void effectiveRangeIsReachCappedAndUnreadableWhenInvalid() {
        assertEquals(50.0F, CgmGunTraits.effectiveRange(10.0D, 5));
        assertEquals(CgmGunTraits.MAX_EFFECTIVE_RANGE, CgmGunTraits.effectiveRange(25.0D, 30));
        assertTrue(Float.isNaN(CgmGunTraits.effectiveRange(0.0D, 30)));
        assertTrue(Float.isNaN(CgmGunTraits.effectiveRange(Double.NaN, 30)));
    }

    @Test
    void fireRateIsTicksBetweenRounds() {
        assertEquals(150, CgmGunTraits.fireIntervalMillis(3));
        assertEquals(50, CgmGunTraits.fireIntervalMillis(0));
    }
}
