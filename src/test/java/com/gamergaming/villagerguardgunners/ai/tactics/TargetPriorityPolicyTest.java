package com.gamergaming.villagerguardgunners.ai.tactics;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TargetPriorityPolicyTest {
    private TargetPriorityPolicy.Candidate candidate(double distance, int urgency, int allies) {
        return new TargetPriorityPolicy.Candidate(distance, true, urgency, allies, false);
    }
    @Test void urgentAttackInterruptsPersistence() {
        assertTrue(TargetPriorityPolicy.shouldSwitch(candidate(10, 0, 0), candidate(16, 2, 0), 1));
    }
    @Test void minorDistanceChangesDoNotThrash() {
        assertFalse(TargetPriorityPolicy.shouldSwitch(candidate(15, 0, 0), candidate(12, 0, 0), 100));
        assertFalse(TargetPriorityPolicy.shouldSwitch(candidate(40, 0, 0), candidate(10, 0, 0), 29));
        assertTrue(TargetPriorityPolicy.shouldSwitch(candidate(40, 0, 0), candidate(10, 0, 0), 30));
    }
    @Test void coveringGuardMakesUnattendedThreatWorthSelecting() {
        assertTrue(TargetPriorityPolicy.shouldSwitch(candidate(14, 2, 1), candidate(17, 2, 0), 30));
        assertFalse(TargetPriorityPolicy.shouldSwitch(candidate(14, 2, 1), candidate(17, 0, 0), 30));
    }
    @Test void rangeAndBlockedLaneMatterWithoutOverridingUrgency() {
        var blocked = new TargetPriorityPolicy.Candidate(14, true, 0, 0, true);
        assertTrue(TargetPriorityPolicy.shouldSwitch(blocked, candidate(16, 0, 0), 30));
        var unreachable = new TargetPriorityPolicy.Candidate(14, false, 0, 0, false);
        assertTrue(TargetPriorityPolicy.shouldSwitch(unreachable, candidate(16, 0, 0), 30));
        assertFalse(TargetPriorityPolicy.shouldSwitch(candidate(14, 2, 2), candidate(16, 0, 0), 30));
    }
    @Test void missingCurrentContactAllowsVisibleReplacement() {
        assertTrue(TargetPriorityPolicy.shouldSwitch(null, candidate(16, 0, 0), 0));
        assertFalse(TargetPriorityPolicy.shouldSwitch(null, null, 100));
    }
}
