package com.gamergaming.villagerguardgunners.ai.tactics;

import java.util.UUID;
import com.gamergaming.villagerguardgunners.api.GuardAllyProtection;
import com.gamergaming.villagerguardgunners.combat.engagement.GuardRulesOfEngagement;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import com.gamergaming.villagerguardgunners.squad.SquadCommandType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.raid.Raider;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.entities.Guard;

/** Ranks the existing perception sample, without another spatial query or sight pass. */
public final class GuardTargetRanking {
    private final Guard guard;
    private UUID selected;
    private long selectedAt;
    private long rankedAt = Long.MIN_VALUE;
    private String status = "IDLE";
    public GuardTargetRanking(Guard guard) { this.guard = guard; }

    public LivingEntity choose(GuardTacticalController tactics, LivingEntity current, SquadCommand command,
                               double minimum, double maximum, boolean blockedCurrent) {
        long now = guard.level().getGameTime();
        if (current != null && !current.getUUID().equals(selected)) {
            selected = current.getUUID(); selectedAt = now;
        }
        if (command != null && command.type() == SquadCommandType.FOCUS_TARGET) {
            status = "PLAYER_FOCUS"; return current;
        }
        if (!(guard.level() instanceof ServerLevel level) || tactics.assessment().sampledAt() != now
                || rankedAt == now || !Double.isFinite(minimum) || !Double.isFinite(maximum)) return current;
        rankedAt = now;
        try {
            LivingEntity best = current;
            TargetPriorityPolicy.Candidate currentRank = null, bestRank = null;
            for (var observation : tactics.tracker().observations()) {
                if (observation.observedAt() != now) continue; // No live facts about hidden enemies.
                if (!(level.getEntity(observation.id()) instanceof LivingEntity candidate)
                        || candidate.isRemoved() || !candidate.isAlive() || !guard.canAttack(candidate)
                        || !GuardRulesOfEngagement.mayEngage(guard, candidate)
                        || (candidate != current && !acquirable(candidate, observation, now))) continue;
                double distance = guard.position().distanceTo(observation.position());
                if (distance > GuardThreatTracker.SCAN_RADIUS && candidate != current) continue;
                int urgency = observation.explosive() && distance <= 8 ? 3 : distance <= 5 ? 2 : 0;
                if (candidate instanceof Mob mob && mob.getTarget() != null
                        && GuardAllyProtection.isProtected(guard, mob.getTarget())) {
                    boolean immediate = candidate.distanceToSqr(mob.getTarget()) <= 64;
                    urgency = Math.max(urgency, immediate ? 2 : 1);
                }
                if (observation.attackEvidenceAt() != Long.MIN_VALUE && now - observation.attackEvidenceAt() < 40)
                    urgency = Math.max(urgency, 1);
                var rank = new TargetPriorityPolicy.Candidate(distance, distance >= minimum && distance <= maximum,
                        urgency, tactics.tracker().coveringAllies(candidate.getUUID()), candidate == current && blockedCurrent);
                if (candidate == current) currentRank = rank;
                if (bestRank == null || rank.score() > bestRank.score()
                        || (rank.score() == bestRank.score() && best != null && candidate.getId() < best.getId())) {
                    best = candidate; bestRank = rank;
                }
            }
            if (best != current && TargetPriorityPolicy.shouldSwitch(currentRank, bestRank, now - selectedAt)) {
                // Persistence follows the actual entity target on the next tick, not a proposal
                // that a Forge target-change listener may reject.
                status = "HIGHER_PRIORITY"; return best;
            }
            status = "KEEP_TARGET";
        } catch (RuntimeException ignored) { status = "INTEGRATION_FAILED"; }
        return current;
    }

    private boolean acquirable(LivingEntity candidate, ThreatMemory.Observation observation, long now) {
        if (GuardConfig.COMMON.MobBlackList.get().contains(candidate.getEncodeId())) return false;
        if (observation.attackEvidenceAt() != Long.MIN_VALUE && now >= observation.attackEvidenceAt()
                && now - observation.attackEvidenceAt() < ThreatMemory.LIFETIME_TICKS) return true;
        // Match Guard Villagers' ordinary hostile families and its configurable selection rules.
        if (GuardConfig.COMMON.MobWhiteList.get().contains(candidate.getEncodeId())) return true;
        return GuardConfig.AttackAllMobs ? candidate instanceof Enemy
                : candidate instanceof Raider || candidate instanceof Witch
                    || (candidate instanceof Zombie && !(candidate instanceof ZombifiedPiglin));
    }
    public void rejectedChange() { status = "TARGET_CHANGE_REJECTED"; }
    public String status() { return status; }
    public void clear() { selected = null; selectedAt = 0; rankedAt = Long.MIN_VALUE; status = "IDLE"; }
}
