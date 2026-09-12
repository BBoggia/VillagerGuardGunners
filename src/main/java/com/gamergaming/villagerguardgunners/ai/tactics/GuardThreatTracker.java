package com.gamergaming.villagerguardgunners.ai.tactics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import com.gamergaming.villagerguardgunners.ai.GuardGunCombatGoal;
import com.gamergaming.villagerguardgunners.api.GuardAllyProtection;
import com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime;
import com.gamergaming.villagerguardgunners.medical.MedicRuntime;
import com.tacz.guns.api.item.IGun;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Fixed-radius, capped perception. Only directly visible entities update position memory. */
public final class GuardThreatTracker {
    public static final int SAMPLE_INTERVAL = 10;
    public static final int CANDIDATE_LIMIT = 32;
    public static final int SIGHT_LIMIT = 12;
    public static final double SCAN_RADIUS = 24;
    private final Guard guard;
    private final ThreatMemory memory = new ThreatMemory();
    private final ArrayList<LivingEntity> candidates = new ArrayList<>(CANDIDATE_LIMIT + 1);
    private String dimension;
    private long nextSample = Long.MIN_VALUE;
    private long sampledAt = Long.MIN_VALUE;
    private long contextUntil = Long.MIN_VALUE;
    private long damagedAt = Long.MIN_VALUE;
    private float previousHealth = Float.NaN;
    private double recentDamage;
    private double support;
    private final ArrayList<Vec3> supportPositions = new ArrayList<>(3);
    private boolean complete;
    private int sightChecks;
    private int queryCount;
    private int candidateCount;

    public GuardThreatTracker(Guard guard) { this.guard = guard; }

    public ThreatAssessment update(LivingEntity target, boolean permittedTarget) {
        long now = guard.level().getGameTime();
        String currentDimension = guard.level().dimension().location().toString();
        if (!currentDimension.equals(dimension)) { clear(); dimension = currentDimension; }
        float health = guard.getHealth();
        if (Float.isFinite(previousHealth) && health < previousHealth) {
            if (now - damagedAt >= ThreatMemory.LIFETIME_TICKS) recentDamage = 0;
            recentDamage = Math.min(1, recentDamage + (previousHealth - health) / Math.max(1, guard.getMaxHealth()));
            damagedAt = now;
            contextUntil = now + ThreatMemory.LIFETIME_TICKS;
        }
        previousHealth = health;
        memory.expire(now, dimension);
        if (guard.level() instanceof ServerLevel level) {
            for (var observed : memory.observations()) {
                var loaded = level.getEntity(observed.id()); // Loaded lookup only; no chunk request.
                if (loaded instanceof LivingEntity living && (!eligible(living)
                        || GuardAllyProtection.isProtected(guard, living))) memory.remove(observed.id());
            }
            if (now >= nextSample || now < sampledAt) {
                // Stagger subsequent samples. Never catch up missed query slots after lag.
                nextSample = now + SAMPLE_INTERVAL + Math.floorMod(guard.getId() - now, SAMPLE_INTERVAL);
                sampledAt = now;
                scan(level, target, permittedTarget, now);
            }
        }
        return assess(now);
    }

    private void scan(ServerLevel level, LivingEntity target, boolean permittedTarget, long now) {
        sightChecks = 0;
        support = 0;
        supportPositions.clear();
        complete = true;
        candidateCount = 0;
        try {
            observe(target, true, now);
            if (permittedTarget && target != null) {
                var observation = memory.get(target.getUUID());
                if (observation != null && observation.observedAt() == now) {
                    contextUntil = now + ThreatMemory.LIFETIME_TICKS;
                }
            }
            LivingEntity attacker = recentAttacker();
            if (attacker != target) observe(attacker, true, now);
            candidates.clear();
            level.getEntities(EntityTypeTest.forClass(LivingEntity.class),
                    guard.getBoundingBox().inflate(SCAN_RADIUS), entity -> true,
                    candidates, CANDIDATE_LIMIT + 1);
            queryCount++;
            candidateCount = Math.min(candidates.size(), CANDIDATE_LIMIT);
            if (candidates.size() > CANDIDATE_LIMIT) complete = false;
            candidates.sort(Comparator.comparingInt(LivingEntity::getId));
            for (int i = 0; i < candidateCount; i++) {
                LivingEntity candidate = candidates.get(i);
                if (candidate == target || candidate == attacker || candidate == guard || !eligible(candidate)) continue;
                if (GuardAllyProtection.isProtected(guard, candidate)) {
                    if (candidate instanceof Guard ally && guard.distanceToSqr(ally) <= 256
                            && !GuardLifeRuntime.blocked(ally) && !MedicRuntime.busy(ally)) {
                        LivingEntity allyTarget = ally.getTarget();
                        if (allyTarget == null || (allyTarget != target && memory.get(allyTarget.getUUID()) == null)) continue;
                        for (var wrapped : ally.goalSelector.getAvailableGoals()) {
                            if (wrapped.getGoal() instanceof GuardGunCombatGoal goal
                                    && goal.providesTacticalSupport(now, allyTarget) && visible(ally)) {
                                if (supportPositions.size() < 3) supportPositions.add(ally.position());
                                support = Math.min(3, support + (allyTarget == target ? 1 : .5));
                                break;
                            }
                        }
                    }
                } else {
                    observe(candidate, false, now);
                }
            }
        } catch (RuntimeException ignored) {
            // Third-party relationship/type hooks cannot turn an incomplete scan into safety.
            complete = false;
        } finally {
            candidates.clear();
        }
    }

    private LivingEntity recentAttacker() {
        int age = guard.tickCount - guard.getLastHurtByMobTimestamp();
        return age >= 0 && age < ThreatMemory.LIFETIME_TICKS ? guard.getLastHurtByMob() : null;
    }

    private void observe(LivingEntity entity, boolean selectedOrAttacker, long now) {
        if (!eligible(entity) || entity == guard || GuardAllyProtection.isProtected(guard, entity)
                || guard.distanceToSqr(entity) > 64 * 64) return;
        boolean attackingAlly = entity instanceof Mob mob && mob.getTarget() != null
                && mob.getTarget().isAlive() && GuardAllyProtection.isProtected(guard, mob.getTarget());
        LivingEntity victim = entity.getLastHurtMob();
        int attackAge = entity.tickCount - entity.getLastHurtMobTimestamp();
        boolean recentAllyAttack = victim != null && attackAge >= 0 && attackAge < ThreatMemory.LIFETIME_TICKS
                && GuardAllyProtection.isProtected(guard, victim);
        if (!selectedOrAttacker && !(entity instanceof Enemy) && !attackingAlly && !recentAllyAttack) return;
        if (!visible(entity)) return;
        var previous = memory.get(entity.getUUID());
        Vec3 position = entity.position();
        Vec3 velocity = previous != null && now > previous.observedAt()
                ? position.subtract(previous.position()).scale(1.0 / (now - previous.observedAt()))
                : entity.getDeltaMovement();
        // Teleports and exotic movement cannot produce unbounded projected pressure.
        if (velocity.lengthSqr() > 1) velocity = velocity.normalize();
        boolean ranged = entity instanceof RangedAttackMob
                || entity.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || entity.getMainHandItem().getItem() instanceof IGun;
        boolean explosive = entity instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0);
        boolean attackedGuard = entity == recentAttacker();
        long attackTick = attackedGuard
                ? now - (guard.tickCount - guard.getLastHurtByMobTimestamp())
                : attackingAlly ? now : recentAllyAttack ? now - attackAge
                : previous == null ? Long.MIN_VALUE : previous.attackEvidenceAt();
        if (attackedGuard || attackingAlly || recentAllyAttack) {
            contextUntil = Math.max(contextUntil, attackTick + ThreatMemory.LIFETIME_TICKS);
        }
        if (previous == null && memory.size() >= ThreatMemory.CAPACITY) complete = false;
        if (!memory.observe(new ThreatMemory.Observation(entity.getUUID(), dimension, position, velocity,
                now, attackTick, ranged, explosive, !(entity instanceof Enemy) && !ranged), selectedOrAttacker)) complete = false;
    }

    private boolean visible(LivingEntity entity) {
        if (sightChecks >= SIGHT_LIMIT) { complete = false; return false; }
        sightChecks++;
        return guard.getSensing().hasLineOfSight(entity);
    }

    private static boolean eligible(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved() && !entity.isSpectator()
                && !(entity instanceof Player player && player.isCreative());
    }

    private ThreatAssessment assess(long now) {
        double nearest = Double.POSITIVE_INFINITY, closing = 0, ranged = 0;
        int visible = 0, sectors = 0;
        boolean explosive = false;
        for (var observed : memory.observations()) {
            double distance = guard.position().distanceTo(observed.position());
            double confidence = observed.confidence(now);
            nearest = Math.min(nearest, distance);
            if (observed.observedAt() == sampledAt) visible++;
            double futureDistance = guard.position().distanceTo(observed.position().add(observed.velocity().scale(10)));
            double weight = distance <= 4 ? 1.25 : distance <= 8 ? .75 : distance <= 16 ? .3 : .05;
            if (futureDistance + 1 < distance) weight += .5;
            if (observed.unknownAttack() && distance <= 8) weight += .25;
            closing += weight * confidence;
            if (observed.ranged() && observed.observedAt() == sampledAt) ranged += .6 * confidence;
            if (observed.explosive() && distance <= 8 && confidence > .5) explosive = true;
            if (distance <= 12 && confidence > .5) {
                Vec3 delta = observed.position().subtract(guard.position());
                int sector = Math.floorMod((int) Math.floor(Math.atan2(delta.z, delta.x) / (Math.PI / 2)), 4);
                sectors |= 1 << sector;
            }
        }
        double damage = now >= damagedAt && now - damagedAt < ThreatMemory.LIFETIME_TICKS ? recentDamage : 0;
        return new ThreatAssessment(sampledAt, memory.size(), visible, nearest, closing, ranged,
                explosive, Integer.bitCount(sectors), support, damage, complete);
    }

    public boolean hasContinuation(long now) {
        return now < contextUntil && now >= sampledAt
                && (memory.size() > 0 || (now >= damagedAt && now - damagedAt < ThreatMemory.LIFETIME_TICKS));
    }
    public ThreatMemory.Observation remembered(UUID id) { return memory.get(id); }
    public java.util.List<ThreatMemory.Observation> observations() { return java.util.List.copyOf(memory.observations()); }
    public java.util.List<Vec3> supportPositions() { return java.util.List.copyOf(supportPositions); }
    public int sightChecks() { return sightChecks; }
    public int candidateCount() { return candidateCount; }
    public int queryCount() { return queryCount; }
    public void clear() {
        memory.clear(); candidates.clear(); supportPositions.clear(); dimension = null; nextSample = Long.MIN_VALUE;
        sampledAt = Long.MIN_VALUE; contextUntil = Long.MIN_VALUE; damagedAt = Long.MIN_VALUE;
        previousHealth = Float.NaN; recentDamage = 0; support = 0; complete = false;
        sightChecks = 0; candidateCount = 0; queryCount = 0;
    }
}
