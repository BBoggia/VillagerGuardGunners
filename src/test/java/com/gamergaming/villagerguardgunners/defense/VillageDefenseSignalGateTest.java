package com.gamergaming.villagerguardgunners.defense;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class VillageDefenseSignalGateTest {
    private static final UUID FIRST = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString(
            "10000000-0000-0000-0000-000000000002");

    @Test
    void duplicateSignalsAreSuppressedWithoutSuppressingOtherKinds() {
        VillageDefenseSignalGate gate = new VillageDefenseSignalGate(2);

        assertAll(
                () -> assertTrue(gate.shouldRoute(
                        FIRST, VillageDefenseSignal.BELL_RUNG, 100L, 20L)),
                () -> assertFalse(gate.shouldRoute(
                        FIRST, VillageDefenseSignal.BELL_RUNG, 119L, 20L)),
                () -> assertTrue(gate.shouldRoute(
                        FIRST, VillageDefenseSignal.HOSTILE_CONFIRMED, 119L, 20L)),
                () -> assertTrue(gate.shouldRoute(
                        FIRST, VillageDefenseSignal.BELL_RUNG, 120L, 20L)));
    }

    @Test
    void threatLeasesAreExclusiveAtTheirDeadline() {
        VillageDefenseSignalGate gate = new VillageDefenseSignalGate(2);
        gate.confirmThreat(FIRST, 100L, 20L);

        assertAll(
                () -> assertTrue(gate.confirmedThreatActive(FIRST, 119L)),
                () -> assertFalse(gate.confirmedThreatActive(FIRST, 120L)),
                () -> assertFalse(gate.confirmedThreatActive(SECOND, 119L)));
    }

    @Test
    void accessOrderEvictionIsBoundedAndFailOpen() {
        VillageDefenseSignalGate gate = new VillageDefenseSignalGate(1);
        gate.shouldRoute(FIRST, VillageDefenseSignal.BELL_RUNG, 100L, 20L);
        gate.shouldRoute(SECOND, VillageDefenseSignal.BELL_RUNG, 100L, 20L);

        assertAll(
                () -> assertEquals(1, gate.trackedNetworks()),
                () -> assertEquals(1L, gate.evictions()),
                () -> assertTrue(gate.shouldRoute(
                        FIRST, VillageDefenseSignal.BELL_RUNG, 101L, 20L)),
                () -> assertEquals(2L, gate.evictions()));
    }

    @Test
    void retainedAliasLeaseResolvesAgainstItsCanonicalNetwork() {
        VillageDefenseSignalGate gate = new VillageDefenseSignalGate(2);
        gate.confirmThreat(FIRST, 100L, 20L);

        assertAll(
                () -> assertTrue(gate.confirmedThreatActiveResolved(
                        SECOND,
                        candidate -> candidate.equals(FIRST) ? SECOND : candidate,
                        119L)),
                () -> assertFalse(gate.confirmedThreatActiveResolved(
                        SECOND,
                        candidate -> candidate.equals(FIRST) ? SECOND : candidate,
                        120L)));
    }
}
