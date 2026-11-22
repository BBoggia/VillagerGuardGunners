package com.gamergaming.villagerguardgunners.ai.tactics;

import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.villagerguardgunners.ai.tactics.CombatIntentPolicy.*;
import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import org.junit.jupiter.api.Test;

class CombatIntentPolicyTest {
    private static ThreatAssessment danger(double pressure, double support, int sectors, boolean explosive, double damage, double nearest, boolean complete) {
        return new ThreatAssessment(100, 3, 3, nearest, pressure, 0, explosive, sectors, support, damage, complete);
    }
    private static Input input(WeaponReadiness ready, double health, ThreatAssessment danger) {
        return new Input(true, true, true, true, false, false, false, health, 4, ready, danger);
    }
    private static final ThreatAssessment MANAGEABLE = danger(.75, 0, 1, false, 0, 6, true);
    @Test void sameEncounterDistinguishesLoadedReloadingDryAndInjured() {
        assertEquals(Intent.ENGAGE, decide(input(WeaponReadiness.READY, 1, MANAGEABLE), Intent.HOLD, 30).intent());
        assertEquals(Intent.CREATE_DISTANCE, decide(input(WeaponReadiness.RELOADING, 1, MANAGEABLE), Intent.HOLD, 30).intent());
        assertEquals(Reason.EMPTY_WEAPON, decide(input(WeaponReadiness.DRY, 1, MANAGEABLE), Intent.HOLD, 30).reason());
        assertEquals(Reason.LOW_HEALTH, decide(input(WeaponReadiness.READY, .2, MANAGEABLE), Intent.HOLD, 30).reason());
    }
    @Test void usefulSupportChangesDecisionButCrowdingAloneIsNotSupport() {
        assertEquals(Intent.WITHDRAW, decide(input(WeaponReadiness.READY, 1, danger(3, 0, 1, false, 0, 8, true)), Intent.ENGAGE, 30).intent());
        assertEquals(Intent.ENGAGE, decide(input(WeaponReadiness.READY, 1, danger(3, 2, 1, false, 0, 8, true)), Intent.ENGAGE, 30).intent());
    }
    @Test void surroundingDirectionsAndDamageOverrideManageableCounts() {
        assertEquals(Reason.SURROUNDED, decide(input(WeaponReadiness.READY, 1, danger(2, 0, 3, false, 0, 6, true)), Intent.ENGAGE, 0).reason());
        assertEquals(Reason.RECENT_DAMAGE, decide(input(WeaponReadiness.READY, 1, danger(.5, 0, 1, false, .25, 10, true)), Intent.ENGAGE, 0).reason());
        assertEquals(Reason.EXPLOSIVE_DANGER, decide(input(WeaponReadiness.READY, 1, danger(.5, 3, 1, true, 0, 6, true)), Intent.HOLD, 0).reason());
    }
    @Test void withdrawalNeedsCommitmentSeparationAndReadinessToRecover() {
        var safe = danger(.2, 0, 1, false, 0, 10, true);
        assertEquals(Reason.WITHDRAWAL_COMMITMENT, decide(input(WeaponReadiness.READY, 1, safe), Intent.WITHDRAW, 19).reason());
        assertEquals(Intent.ENGAGE, decide(input(WeaponReadiness.READY, 1, safe), Intent.WITHDRAW, 20).intent());
        assertEquals(Intent.WITHDRAW, decide(input(WeaponReadiness.RELOADING, 1, safe), Intent.WITHDRAW, 30).intent());
        assertEquals(Intent.WITHDRAW, decide(input(WeaponReadiness.READY, 1, MANAGEABLE), Intent.WITHDRAW, 30).intent());
    }
    @Test void explicitOrdersInterruptWithdrawalImmediately() {
        for (boolean hold : new boolean[]{true, false}) {
            var i = new Input(true, true, true, true, hold, !hold, false, .1, 4, WeaponReadiness.DRY, MANAGEABLE);
            assertEquals(Reason.MOVEMENT_ORDER, decide(i, Intent.WITHDRAW, 0).reason());
        }
    }
    @Test void missingTargetAndPermissionNeverProduceOffensiveIntent() {
        var gap = new Input(true, false, false, false, false, false, false, 1, 4, WeaponReadiness.READY, MANAGEABLE);
        assertEquals(Reason.TARGET_GAP, decide(gap, Intent.ENGAGE, 30).reason());
        var denied = new Input(true, true, true, false, false, false, false, 1, 4, WeaponReadiness.READY, MANAGEABLE);
        assertEquals(Reason.FIRE_NOT_PERMITTED, decide(denied, Intent.ENGAGE, 30).reason());
        var hidden = new Input(true, true, false, true, false, false, false, 1, 4, WeaponReadiness.READY, MANAGEABLE);
        assertEquals(Reason.UNOBSERVED_TARGET, decide(hidden, Intent.ENGAGE, 30).reason());
    }
    @Test void incompleteKnowledgeAndUnreadableGunPreventPursuit() {
        assertEquals(Reason.INCOMPLETE_ASSESSMENT, decide(input(WeaponReadiness.READY, 1, danger(.5, 0, 1, false, 0, 8, false)), Intent.HOLD, 30).reason());
        assertEquals(Reason.WEAPON_UNAVAILABLE, decide(input(WeaponReadiness.SUSPENDED, 1, MANAGEABLE), Intent.HOLD, 30).reason());
    }
    @Test void combatEndBypassesWithdrawalCommitment() {
        var done = new Input(false, false, false, false, false, false, false, .1, 4, WeaponReadiness.DRY, ThreatAssessment.EMPTY);
        assertEquals(Intent.FINISH_COMBAT, decide(done, Intent.WITHDRAW, 0).intent());
    }
}
