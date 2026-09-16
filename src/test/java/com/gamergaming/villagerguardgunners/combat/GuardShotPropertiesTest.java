package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GuardShotPropertiesTest {
    @Test void nativeFieldsAndSafetyShareOneSnapshot() {
        var properties = new GuardShotProperties(.8F, .15F, .025F, true, 1.5F, .25F);
        var profile = properties.ballistics(15, .7F, Vec3.ZERO);
        assertEquals(Math.max(1, (int) (properties.nativeValue("bullet_life", Float.class) * 20)), profile.lifeTicks());
        assertEquals((double) properties.nativeValue("bullet_gravity", Float.class), profile.gravity());
        assertEquals((double) properties.nativeValue("bullet_friction", Float.class), profile.friction());
        assertTrue(properties.nativeValue("explode_enabled", Boolean.class));
        assertEquals(properties.nativeValue("explosion_radius", Float.class) * 2.0, profile.blastReach());
        assertEquals(5, profile.fuseTicks());
        assertNull(properties.nativeValue("explosion_damage", Float.class));
        assertNull(properties.nativeValue("damage", Float.class));
    }

    @Test void rawNativeValuesRetainFloatRoundingWhileBoundsMatchNativeClamps() {
        var properties = new GuardShotProperties(-1, -.2F, -.4F, true, -2, 0);
        var profile = properties.ballistics(15, .7F, Vec3.ZERO);
        assertEquals(-1F, properties.nativeValue("bullet_life", Float.class));
        assertEquals(1, profile.lifeTicks());
        assertEquals(0, profile.gravity());
        assertEquals(0, profile.friction());
        assertEquals(0, profile.blastReach());
        assertEquals(1, profile.fuseTicks());
    }

    @Test void disabledExplosionsAndNegativeFusesCannotCreatePrematureDetonation() {
        assertEquals(Integer.MAX_VALUE, new GuardShotProperties(1, 0, 0, false, 20, 0)
                .ballistics(15, 0, Vec3.ZERO).fuseTicks());
        assertEquals(0, new GuardShotProperties(1, 0, 0, false, 20, 0)
                .ballistics(15, 0, Vec3.ZERO).blastReach());
        assertEquals(Integer.MAX_VALUE, new GuardShotProperties(1, 0, 0, true, 1, -1)
                .ballistics(15, 0, Vec3.ZERO).fuseTicks());
    }

    @Test void allSafetyPropertiesRejectNonFiniteValues() {
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new GuardShotProperties(invalid, 0, 0, true, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new GuardShotProperties(1, invalid, 0, true, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new GuardShotProperties(1, 0, invalid, true, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new GuardShotProperties(1, 0, 0, true, invalid, 1));
            assertThrows(IllegalArgumentException.class, () -> new GuardShotProperties(1, 0, 0, true, 1, invalid));
        }
    }
}
