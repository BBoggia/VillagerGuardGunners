package com.gamergaming.villagerguardgunners.ai.tactics;

import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;

/** Pure intent rules; no entity access, actions, scans, or navigation. */
public final class CombatIntentPolicy {
    public static final int MINIMUM_WITHDRAW_TICKS = 20;
    public enum Intent { ENGAGE, HOLD, REPOSITION, CREATE_DISTANCE, WITHDRAW, FINISH_COMBAT }
    public enum Reason {
        NO_COMBAT, CONTACT_EXPIRED, MOVEMENT_ORDER, FIRE_NOT_PERMITTED, UNOBSERVED_TARGET,
        INCOMPLETE_ASSESSMENT, WEAPON_UNAVAILABLE, EMPTY_WEAPON, VULNERABLE_ACTION,
        CLOSE_CONTACT, EXPLOSIVE_DANGER, LOW_HEALTH, RECENT_DAMAGE, SURROUNDED,
        OVERWHELMED, WITHDRAWAL_COMMITMENT, RECOVERY_NOT_READY, BLOCKED_SHOT,
        MANAGEABLE_ENGAGEMENT, TARGET_GAP
    }
    public record Input(boolean combatActive, boolean targetPresent, boolean targetVisible,
            boolean mayEngage, boolean movementLocked, boolean commandMoving,
            boolean friendlyBlocked, double healthFraction, double retreatDistance,
            WeaponReadiness readiness, ThreatAssessment threats) { }
    public record Decision(Intent intent, Reason reason) { }
    private CombatIntentPolicy() { }

    public static Decision decide(Input in, Intent previous, long activeTicks) {
        if (!in.combatActive()) return decision(Intent.FINISH_COMBAT, in.targetPresent() ? Reason.CONTACT_EXPIRED : Reason.NO_COMBAT);
        if (in.movementLocked() || in.commandMoving()) return decision(Intent.HOLD, Reason.MOVEMENT_ORDER);
        ThreatAssessment danger = in.threats();
        boolean pressure = danger.threats() > 0 || danger.recentDamageFraction() > 0;
        if (danger.explosiveDanger()) return decision(Intent.WITHDRAW, Reason.EXPLOSIVE_DANGER);
        if (pressure && in.healthFraction() <= .3) return decision(Intent.WITHDRAW, Reason.LOW_HEALTH);
        if (danger.recentDamageFraction() >= .2) return decision(Intent.WITHDRAW, Reason.RECENT_DAMAGE);
        if (danger.surrounded() && danger.pressure() > 1.5 + danger.support()) {
            return decision(Intent.WITHDRAW, Reason.SURROUNDED);
        }
        if (danger.pressure() > 2.5 + danger.support()) return decision(Intent.WITHDRAW, Reason.OVERWHELMED);
        if (in.readiness() == WeaponReadiness.DRY && pressure) return decision(Intent.WITHDRAW, Reason.EMPTY_WEAPON);
        if (previous == Intent.WITHDRAW && pressure) {
            if (activeTicks < MINIMUM_WITHDRAW_TICKS) return decision(Intent.WITHDRAW, Reason.WITHDRAWAL_COMMITMENT);
            if (!in.readiness().permitsOffensiveMovement() || in.healthFraction() < .5
                    || danger.nearestDistance() < 8 || danger.pressure() > 1 + danger.support()) {
                return decision(Intent.WITHDRAW, Reason.RECOVERY_NOT_READY);
            }
        }
        if (!in.targetPresent()) return decision(Intent.HOLD, Reason.TARGET_GAP);
        if (!in.readiness().permitsOffensiveMovement()) {
            if (pressure && danger.nearestDistance() <= 16
                    && (in.readiness() == WeaponReadiness.RELOADING
                        || in.readiness() == WeaponReadiness.NEEDS_RELOAD
                        || in.readiness() == WeaponReadiness.CYCLING_ACTION
                        || in.readiness() == WeaponReadiness.DRAWING)) {
                return decision(Intent.CREATE_DISTANCE, Reason.VULNERABLE_ACTION);
            }
            return decision(Intent.HOLD, Reason.WEAPON_UNAVAILABLE);
        }
        if (!in.mayEngage()) return decision(Intent.HOLD, Reason.FIRE_NOT_PERMITTED);
        if (!in.targetVisible()) return decision(Intent.HOLD, Reason.UNOBSERVED_TARGET);
        if (!danger.complete()) return decision(Intent.HOLD, Reason.INCOMPLETE_ASSESSMENT);
        if (danger.nearestDistance() <= in.retreatDistance()) return decision(Intent.CREATE_DISTANCE, Reason.CLOSE_CONTACT);
        if (in.friendlyBlocked()) return decision(Intent.REPOSITION, Reason.BLOCKED_SHOT);
        return decision(Intent.ENGAGE, Reason.MANAGEABLE_ENGAGEMENT);
    }

    private static Decision decision(Intent intent, Reason reason) { return new Decision(intent, reason); }
}
