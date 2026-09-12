package com.gamergaming.villagerguardgunners.defense;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VillageDefenseTransitionPolicyTest {
    @Test
    void readinessRanksAreExplicitAndRecoveryRemainsPartiallyMobilized() {
        assertAll(
                () -> assertTrue(VillageDefenseState.SIEGE.isAtLeast(
                        VillageDefenseState.ALERT)),
                () -> assertTrue(VillageDefenseState.WATCH.isAtLeast(
                        VillageDefenseState.RECOVERY)),
                () -> assertFalse(VillageDefenseState.RECOVERY.isAtLeast(
                        VillageDefenseState.WATCH)),
                () -> assertFalse(VillageDefenseState.PEACE.isMobilized()),
                () -> assertTrue(VillageDefenseState.RECOVERY.isMobilized()),
                () -> assertTrue(VillageDefenseState.ALERT.isConfirmedThreatState()),
                () -> assertFalse(VillageDefenseState.WATCH.isConfirmedThreatState()));
    }

    @Test
    void signalsEscalateToTheirMinimumStateWithoutDeescalating() {
        assertAll(
                () -> assertEquals(
                        VillageDefenseState.WATCH,
                        signal(VillageDefenseState.PEACE, VillageDefenseSignal.BELL_RUNG)),
                () -> assertEquals(
                        VillageDefenseState.ALERT,
                        signal(
                                VillageDefenseState.RECOVERY,
                                VillageDefenseSignal.HOSTILE_CONFIRMED)),
                () -> assertEquals(
                        VillageDefenseState.ALERT,
                        signal(
                                VillageDefenseState.WATCH,
                                VillageDefenseSignal.FRIENDLY_DAMAGED)),
                () -> assertEquals(
                        VillageDefenseState.ALERT,
                        signal(
                                VillageDefenseState.ALERT,
                                VillageDefenseSignal.FRIENDLY_KILLED)),
                () -> assertEquals(
                        VillageDefenseState.SIEGE,
                        signal(VillageDefenseState.ALERT, VillageDefenseSignal.RAID_ACTIVE)),
                () -> assertEquals(
                        VillageDefenseState.SIEGE,
                        signal(VillageDefenseState.SIEGE, VillageDefenseSignal.BELL_RUNG)));
    }

    @Test
    void raidEndRetainsOnlyTheReadinessSupportedByRemainingThreats() {
        assertAll(
                () -> assertEquals(
                        VillageDefenseState.ALERT,
                        VillageDefenseTransitionPolicy.onRaidEnded(
                                VillageDefenseState.SIEGE, true)),
                () -> assertEquals(
                        VillageDefenseState.RECOVERY,
                        VillageDefenseTransitionPolicy.onRaidEnded(
                                VillageDefenseState.SIEGE, false)),
                () -> assertEquals(
                        VillageDefenseState.WATCH,
                        VillageDefenseTransitionPolicy.onRaidEnded(
                                VillageDefenseState.WATCH, false)));
    }

    @Test
    void quietDeadlinesRequireLiveFactsBeforeDeescalating() {
        assertAll(
                () -> assertEquals(
                        VillageDefenseState.SIEGE,
                        quiet(VillageDefenseState.ALERT, true, false)),
                () -> assertEquals(
                        VillageDefenseState.ALERT,
                        quiet(VillageDefenseState.SIEGE, false, true)),
                () -> assertEquals(
                        VillageDefenseState.PEACE,
                        quiet(VillageDefenseState.WATCH, false, false)),
                () -> assertEquals(
                        VillageDefenseState.RECOVERY,
                        quiet(VillageDefenseState.ALERT, false, false)),
                () -> assertEquals(
                        VillageDefenseState.RECOVERY,
                        quiet(VillageDefenseState.SIEGE, false, false)),
                () -> assertEquals(
                        VillageDefenseState.PEACE,
                        quiet(VillageDefenseState.RECOVERY, false, false)));
    }

    private static VillageDefenseState signal(
            VillageDefenseState current,
            VillageDefenseSignal signal) {
        return VillageDefenseTransitionPolicy.onSignal(current, signal);
    }

    private static VillageDefenseState quiet(
            VillageDefenseState current,
            boolean raidActive,
            boolean threatActive) {
        return VillageDefenseTransitionPolicy.onQuietDeadline(
                current, raidActive, threatActive);
    }
}
