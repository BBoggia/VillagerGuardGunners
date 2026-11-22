package com.gamergaming.villagerguardgunners.compat.cgm;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CgmDischargeTest {
    @Test
    void rejectedProjectilesNeverTickOrAppearInTrails() {
        List<Integer> ticks = new ArrayList<>();
        var accepted = CgmDischarge.fire(6, i -> i, i -> false, ticks::add, failure -> fail(failure));
        assertTrue(accepted.isEmpty());
        assertTrue(ticks.isEmpty());
    }

    @Test
    void partiallyAcceptedShotContainsOnlyAcceptedPellets() {
        List<Integer> ticks = new ArrayList<>();
        var accepted = CgmDischarge.fire(6, i -> i, i -> i % 2 == 0, ticks::add, failure -> fail(failure));
        assertEquals(List.of(0, 2, 4), accepted);
        assertEquals(accepted, ticks);
    }

    @Test
    void laterFactoryFailureStillAccountsForTheDischargedRound() {
        List<RuntimeException> errors = new ArrayList<>();
        var accepted = CgmDischarge.fire(3, i -> {
            if (i == 1) throw new IllegalStateException("broken second pellet");
            return i;
        }, i -> true, i -> {}, errors::add);
        assertEquals(List.of(0), accepted);
        assertEquals(1, errors.size());
    }

    @Test
    void failingImpactStillCountsAnAcceptedProjectile() {
        List<RuntimeException> errors = new ArrayList<>();
        var accepted = CgmDischarge.fire(3, i -> i, i -> true,
                i -> { throw new IllegalStateException("impact callback failed"); }, errors::add);
        assertEquals(List.of(0), accepted);
        assertEquals(1, errors.size());
    }

    @Test
    void failureBeforeAnyAcceptedProjectilePropagatesToWeaponSuspension() {
        assertThrows(IllegalStateException.class, () -> CgmDischarge.fire(3,
                i -> { throw new IllegalStateException("factory unavailable"); },
                i -> true, i -> fail(), failure -> fail()));
    }
}
