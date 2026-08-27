package com.gamergaming.villagerguardgunners.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

final class NativeFireCadenceTest {
    @Test
    void ordinaryCadenceOnlyProducesOneDueShotAtItsConfiguredInterval() {
        NativeFireCadence cadence = new NativeFireCadence();

        cadence.beginTick(0L, 100L, 1);
        assertEquals(List.of(0L), drain(cadence));

        cadence.beginTick(50L, 100L, 1);
        assertTrue(drain(cadence).isEmpty());

        cadence.beginTick(100L, 100L, 1);
        assertEquals(List.of(100L), drain(cadence));
    }

    @Test
    void subTickAutomaticCadencePreservesTimestampsAcrossServerTicks() {
        NativeFireCadence cadence = new NativeFireCadence();

        cadence.beginTick(0L, 41L, NativeFireCadence.MAX_SHOTS_PER_TICK);
        assertEquals(List.of(0L), drain(cadence));

        cadence.beginTick(50L, 41L, NativeFireCadence.MAX_SHOTS_PER_TICK);
        assertEquals(List.of(41L), drain(cadence));

        cadence.beginTick(100L, 41L, NativeFireCadence.MAX_SHOTS_PER_TICK);
        assertEquals(List.of(82L), drain(cadence));

        cadence.beginTick(150L, 41L, NativeFireCadence.MAX_SHOTS_PER_TICK);
        assertEquals(List.of(123L), drain(cadence));

        cadence.beginTick(200L, 41L, NativeFireCadence.MAX_SHOTS_PER_TICK);
        assertEquals(List.of(164L, 205L), drain(cadence));
    }

    @Test
    void lagBacklogIsDiscardedInsteadOfCreatingACatchUpBurst() {
        NativeFireCadence cadence = new NativeFireCadence();
        cadence.beginTick(0L, 10L, NativeFireCadence.MAX_SHOTS_PER_TICK);
        drain(cadence);

        cadence.beginTick(1_000L, 10L, NativeFireCadence.MAX_SHOTS_PER_TICK);
        List<Long> timestamps = drain(cadence);

        assertFalse(timestamps.isEmpty());
        assertEquals(1_000L, timestamps.get(0));
        assertTrue(timestamps.size() <= NativeFireCadence.MAX_SHOTS_PER_TICK);
    }

    @Test
    void perTickWorkIsHardCappedEvenForMalformedExtremeRates() {
        NativeFireCadence cadence = new NativeFireCadence();
        cadence.beginTick(100L, 1L, Integer.MAX_VALUE);

        assertEquals(NativeFireCadence.MAX_SHOTS_PER_TICK, drain(cadence).size());
        assertFalse(cadence.hasShotDue());
    }

    @Test
    void zeroIntervalNeverBecomesDue() {
        NativeFireCadence cadence = new NativeFireCadence();
        cadence.beginTick(100L, 0L, NativeFireCadence.MAX_SHOTS_PER_TICK);

        assertFalse(cadence.hasShotDue());
    }

    @Test
    void cooldownCalculationMatchesTaCZsFiveMillisecondTolerance() {
        assertEquals(0L, NativeGunController.calculateShootCooldown(0L, 100L, -1L));
        assertEquals(45L, NativeGunController.calculateShootCooldown(50L, 100L, 0L));
        assertEquals(0L, NativeGunController.calculateShootCooldown(95L, 100L, 0L));
        assertEquals(95L, NativeGunController.calculateShootCooldown(40L, 100L, 50L));
    }

    @Test
    void burstSafetyDeadlineSaturatesInsteadOfOverflowing() {
        assertEquals(150L, NativeGunController.saturatingAdd(100L, 50L));
        assertEquals(Long.MAX_VALUE, NativeGunController.saturatingAdd(100L, Long.MAX_VALUE));
        assertEquals(Long.MIN_VALUE, NativeGunController.saturatingAdd(-100L, Long.MIN_VALUE));
    }

    private static List<Long> drain(NativeFireCadence cadence) {
        List<Long> timestamps = new ArrayList<>();
        while (cadence.hasShotDue()) {
            timestamps.add(cadence.getDueTimestamp());
            cadence.recordSuccessfulShot();
        }
        return timestamps;
    }
}
