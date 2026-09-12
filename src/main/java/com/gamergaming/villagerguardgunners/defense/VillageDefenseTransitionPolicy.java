package com.gamergaming.villagerguardgunners.defense;

import java.util.Objects;

/**
 * Pure transition contract for the future persistent defense-state service.
 * Signals may only escalate readiness. De-escalation requires an explicit
 * raid-end or quiet-time review so a low-severity event cannot clear combat.
 */
public final class VillageDefenseTransitionPolicy {
    private VillageDefenseTransitionPolicy() {
    }

    public static VillageDefenseState onSignal(
            VillageDefenseState current,
            VillageDefenseSignal signal) {
        VillageDefenseState normalizedCurrent = Objects.requireNonNull(
                current, "current");
        VillageDefenseState requested = Objects.requireNonNull(
                signal, "signal").minimumState();
        return normalizedCurrent.isAtLeast(requested)
                ? normalizedCurrent
                : requested;
    }

    /**
     * A finished raid leaves Siege immediately. A still-confirmed threat keeps
     * the network at Alert; otherwise the village begins Recovery.
     */
    public static VillageDefenseState onRaidEnded(
            VillageDefenseState current,
            boolean confirmedThreatActive) {
        VillageDefenseState normalizedCurrent = Objects.requireNonNull(
                current, "current");
        if (normalizedCurrent != VillageDefenseState.SIEGE) {
            return normalizedCurrent;
        }
        return confirmedThreatActive
                ? VillageDefenseState.ALERT
                : VillageDefenseState.RECOVERY;
    }

    /**
     * Applies one deadline review after the caller has rechecked live raid and
     * threat facts. The policy itself never scans entities or loads chunks.
     */
    public static VillageDefenseState onQuietDeadline(
            VillageDefenseState current,
            boolean raidActive,
            boolean confirmedThreatActive) {
        VillageDefenseState normalizedCurrent = Objects.requireNonNull(
                current, "current");
        if (raidActive) {
            return VillageDefenseState.SIEGE;
        }
        if (confirmedThreatActive) {
            return normalizedCurrent == VillageDefenseState.SIEGE
                    ? VillageDefenseState.ALERT
                    : onSignal(
                            normalizedCurrent,
                            VillageDefenseSignal.HOSTILE_CONFIRMED);
        }
        return switch (normalizedCurrent) {
            case PEACE -> VillageDefenseState.PEACE;
            case WATCH -> VillageDefenseState.PEACE;
            case ALERT, SIEGE -> VillageDefenseState.RECOVERY;
            case RECOVERY -> VillageDefenseState.PEACE;
        };
    }
}
