package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import org.junit.jupiter.api.Test;

final class TaCZExplosionResolverTest {
    @Test
    void missingBaseExplosionUsesTaCZsScriptableDisabledDefault() {
        ExplosionData fallback = TaCZExplosionResolver.resolveBaseExplosion(
                null,
                null);

        assertAll(
                () -> assertFalse(fallback.isExplode()),
                () -> assertEquals(0.0F, fallback.getRadius()),
                () -> assertEquals(0.0F, fallback.getDamage()),
                () -> assertEquals(30.0F, fallback.getDelay()),
                () -> assertFalse(fallback.isKnockback()),
                () -> assertFalse(fallback.isDestroyBlock()));
    }

    @Test
    void resolvedTraitsRejectImpossibleStates() {
        assertAll(
                () -> assertEquals(
                        new TaCZExplosionResolver.Traits(true, 3.5F),
                        new TaCZExplosionResolver.Traits(true, 3.5F)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TaCZExplosionResolver.Traits(false, 1.0F)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TaCZExplosionResolver.Traits(true, Float.NaN)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TaCZExplosionResolver.Traits(true, -1.0F)));
    }
}
