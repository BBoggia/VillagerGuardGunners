package com.gamergaming.villagerguardgunners.ai.tactics;

import com.gamergaming.villagerguardgunners.combat.WeaponReadiness;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import com.gamergaming.villagerguardgunners.squad.SquadCommand;
import com.gamergaming.villagerguardgunners.squad.SquadCommandType;
import net.minecraft.world.entity.LivingEntity;
import tallestegg.guardvillagers.entities.Guard;

/** Owns transient observations and intent, independent of the navigation executor. */
public final class GuardTacticalController {
    private final Guard guard;
    private final GuardThreatTracker tracker;
    private ThreatAssessment assessment = ThreatAssessment.EMPTY;
    private CombatIntentPolicy.Decision decision = new CombatIntentPolicy.Decision(
            CombatIntentPolicy.Intent.FINISH_COMBAT, CombatIntentPolicy.Reason.NO_COMBAT);
    private long enteredAt;
    private long nextDecision;
    private long lastTargetEvidence = Long.MIN_VALUE;
    private WeaponReadiness previousReadiness;
    private net.minecraft.world.item.ItemStack previousWeapon;
    private SquadCommand previousCommand;
    private int previousOrdersRevision;
    private boolean previousTargetPresent;
    private boolean previousVisible;
    private float previousHealth;

    public GuardTacticalController(Guard guard) { this.guard = guard; tracker = new GuardThreatTracker(guard); }
    public void observe(LivingEntity target, boolean permittedTarget) {
        try { assessment = tracker.update(target, permittedTarget); }
        catch (RuntimeException ignored) { tracker.clear(); assessment = ThreatAssessment.EMPTY; }
    }
    public CombatIntentPolicy.Decision decide(LivingEntity target, boolean visible, boolean mayEngage,
            WeaponReadiness readiness, SquadCommand command, boolean friendlyBlocked, double retreatDistance) {
        long now = guard.level().getGameTime();
        boolean targetPresent = target != null && target.isAlive();
        if (targetPresent && (visible || lastTargetEvidence == Long.MIN_VALUE)) lastTargetEvidence = now;
        boolean targetContext = lastTargetEvidence != Long.MIN_VALUE && now >= lastTargetEvidence
                && now - lastTargetEvidence < ThreatMemory.LIFETIME_TICKS;
        boolean hold = GuardRoleResolver.effectiveOrders(guard).movement().holdsPosition();
        boolean movingCommand = command != null && (command.type() == SquadCommandType.RALLY
                || command.type() == SquadCommandType.FALL_BACK
                || (command.type() == SquadCommandType.DEFEND
                    && !new TacticalAssignmentBounds(net.minecraft.world.phys.Vec3.atBottomCenterOf(command.position()),
                            Math.max(.5, command.radius() - 1)).contains(guard.position())));
        if (command != null && command.type() == SquadCommandType.HOLD) hold = true;
        // DEFEND uses the command adapter's existing area boundary handling.
        if (command != null && command.type() == SquadCommandType.DEFEND) hold = false;
        int revision = GuardRoleResolver.effectiveRevision(guard);
        boolean equipmentOrOrdersChanged = readiness != previousReadiness
                || previousWeapon != guard.getMainHandItem() || revision != previousOrdersRevision;
        var next = CombatIntentPolicy.decide(new CombatIntentPolicy.Input(
                targetContext || tracker.hasContinuation(now), targetPresent, visible, mayEngage,
                hold, movingCommand, friendlyBlocked, guard.getHealth() / Math.max(1, guard.getMaxHealth()),
                retreatDistance, readiness, assessment), decision.intent(), equipmentOrOrdersChanged
                        ? CombatIntentPolicy.MINIMUM_WITHDRAW_TICKS : now - enteredAt);
        boolean urgent = equipmentOrOrdersChanged || command != previousCommand
                || revision != previousOrdersRevision || targetPresent != previousTargetPresent
                || visible != previousVisible || guard.getHealth() < previousHealth
                // Restrictive inputs revoke an old advance immediately. Only resuming
                // engagement may wait for the ordinary decision cadence.
                || (next.intent() != decision.intent()
                    && next.intent() != CombatIntentPolicy.Intent.ENGAGE)
                || next.intent() == CombatIntentPolicy.Intent.FINISH_COMBAT;
        if (now < nextDecision && !urgent) return decision;
        nextDecision = now + com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS
                .performance.movementDecisionIntervalTicks.get();
        previousReadiness = readiness; previousWeapon = guard.getMainHandItem(); previousCommand = command; previousOrdersRevision = revision;
        previousTargetPresent = targetPresent; previousVisible = visible; previousHealth = guard.getHealth();
        if (next.intent() != decision.intent()) enteredAt = now;
        decision = next;
        return next;
    }
    public boolean hasContinuation() {
        long now = guard.level().getGameTime();
        return tracker.hasContinuation(now) || (lastTargetEvidence != Long.MIN_VALUE
                && now >= lastTargetEvidence && now - lastTargetEvidence < ThreatMemory.LIFETIME_TICKS);
    }
    public CombatIntentPolicy.Decision decision() { return decision; }
    public ThreatAssessment assessment() { return assessment; }
    public GuardThreatTracker tracker() { return tracker; }
    public long enteredAt() { return enteredAt; }
    public void clear() {
        tracker.clear(); assessment = ThreatAssessment.EMPTY; enteredAt = 0; nextDecision = 0; lastTargetEvidence = Long.MIN_VALUE;
        previousReadiness = null; previousWeapon = null; previousCommand = null; previousOrdersRevision = 0;
        previousTargetPresent = false; previousVisible = false; previousHealth = 0;
        decision = new CombatIntentPolicy.Decision(CombatIntentPolicy.Intent.FINISH_COMBAT, CombatIntentPolicy.Reason.NO_COMBAT);
    }
}
