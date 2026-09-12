package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import tallestegg.guardvillagers.entities.Guard;

/** Early Guard death boundary, before upstream conversion and ordinary death side effects. */
public final class GuardLifeRuntime {
    public static final TagKey<DamageType> BYPASSES_DOWNING = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("villagerguardgunners", "bypasses_downing"));
    private static final ThreadLocal<Guard> TREATMENT_EVENT = new ThreadLocal<>();
    private GuardLifeRuntime() {}
    public static boolean enabled() { return ModConfigs.GUARD_GUNNERS.medical.downed.get()
            && ModConfigs.GUARD_GUNNERS.medical.enabled.get() && !TreatmentProfiles.snapshot().isEmpty(); }
    public static boolean blocked(Guard guard) {
        return guard.level().isClientSide ? ((GuardLifeAccess)guard).villagerGuardGunners$getLifeVisual() != 0 : GuardLifeAccess.get(guard).blocked();
    }
    public static boolean casualty(Guard guard) { return GuardLifeAccess.get(guard).casualty(); }
    public static boolean bypass(DamageSource source) { return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(BYPASSES_DOWNING); }
    public static boolean interceptDeath(Guard guard, DamageSource source) {
        if (!(guard.level() instanceof ServerLevel)) return false;
        TransportRuntime.cancel(guard);
        var life = GuardLifeAccess.get(guard);
        if (life.dying) return true; // Reentrant/duplicate death must never repeat upstream conversion or loot.
        if (guard.isRemoved()) return false;
        if (!bypass(source) && life.casualty()) {
            life.finishDamage(Float.MAX_VALUE, guard.level().getGameTime()); guard.setHealth(1); return true;
        }
        if (!bypass(source) && enabled() && GuardMedicalAccess.get(guard).readable() && !GuardMedicalAccess.get(guard).released()
                && GuardEquipmentServiceAccess.get(guard).readable() && !com.gamergaming.villagerguardgunners.quartermaster.service.ServiceTransactions.mutating(guard)
                && Float.isFinite(guard.getMaxHealth()) && guard.getMaxHealth() > 1
                && life.down(source, guard.level().getGameTime())) {
            GuardMedicalAccess.get(guard).readyAt(guard.level().getGameTime());
            guard.setHealth(1); guard.deathTime = 0; interrupt(guard); synchronize(guard); return true;
        }
        // With the feature off and no history, preserve the ordinary upstream path exactly.
        if (life.blocked() || life.cooldown() > 0) life.dying = true;
        CotRestRuntime.release(guard, false);
        return false;
    }
    public static void deathReturned(Guard guard) {
        var life = GuardLifeAccess.get(guard);
        if (life.dying && guard.isAlive() && !guard.isRemoved()) { life.recover(); synchronize(guard); }
    }
    public static void damaged(LivingDamageEvent event) {
        if (event.isCanceled() || !(event.getAmount() > 0) || !(event.getEntity() instanceof Guard guard)
                || !(guard.level() instanceof ServerLevel) || !casualty(guard) || bypass(event.getSource())) return;
        TransportRuntime.cancel(guard);
        PlayerTreatmentRuntime.cancel(guard);
        GuardMedicAccess.get(guard).damageRevision++; MedicRuntime.cancel(guard, MedicState.Status.INTERRUPTED);
        GuardLifeAccess.get(guard).finishDamage(event.getAmount(), guard.level().getGameTime());
        event.setCanceled(true);
    }
    public static void reload(com.tacz.guns.api.event.common.GunReloadEvent event) {
        if (event.getEntity() instanceof Guard guard && (blocked(guard) || TransportRuntime.busy(guard))) event.setCanceled(true);
    }
    public static void melee(com.tacz.guns.api.event.common.GunMeleeEvent event) {
        if (event.getShooter() instanceof Guard guard && (blocked(guard) || TransportRuntime.busy(guard))) event.setCanceled(true);
    }
    public static void healed(LivingHealEvent event) {
        if (event.getEntity() instanceof Guard guard && casualty(guard) && TREATMENT_EVENT.get() != guard) event.setCanceled(true);
    }
    public static float treatmentEvent(Guard guard, float amount) {
        Guard previous = TREATMENT_EVENT.get(); TREATMENT_EVENT.set(guard);
        try { return net.minecraftforge.event.ForgeEventFactory.onLivingHeal(guard, amount); }
        finally { if (previous == null) TREATMENT_EVENT.remove(); else TREATMENT_EVENT.set(previous); }
    }
    public static void completeTreatment(Guard guard, float healedHealth, int treatmentCooldown) {
        completeTreatment(guard, healedHealth, treatmentCooldown, CasualtyCarePolicy.forPatient(guard));
    }
    public static void completeTreatment(Guard guard, float healedHealth, int treatmentCooldown, CasualtyCarePolicy care) {
        TransportRuntime.cancel(guard);
        var life = GuardLifeAccess.get(guard);
        if (life.casualty()) {
            if (life.stage() == GuardLifeState.Stage.STABILIZED) life.recover(care.recoveryTicks(), care.fieldRecoveryTicks(), care.mode() == CasualtyCarePolicy.Mode.COT);
            else life.treated(treatmentCooldown); guard.setHealth(life.casualty() ? 1 : Math.min(guard.getMaxHealth(), 4)); synchronize(guard);
        } else guard.setHealth(healedHealth);
    }
    public static void tick(Guard guard) {
        if (!(guard.level() instanceof ServerLevel) || !guard.isAlive() || guard.isRemoved()) return;
        var life = GuardLifeAccess.get(guard);
        if (!life.blocked() && life.cooldown() == 0) return;
        if (life.casualty() && !enabled()) {
            TransportRuntime.cancel(guard);
            life.recover(); guard.setHealth(Math.min(guard.getMaxHealth(), 4)); MedicRuntime.cancel(guard, MedicState.Status.INTERRUPTED);
        }
        life.tick(CotRestRuntime.anchored(guard));
        if (life.casualty()) guard.setHealth(1);
        if (life.blocked()) stopActions(guard);
        synchronize(guard);
        if (life.casualty() && life.remaining() == 0 && !life.dying) {
            DamageSource source = source(guard);
            // Vanilla recent-attacker timers expire before a full bleed-out window.
            if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) guard.setLastHurtByMob(attacker);
            if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player) guard.setLastHurtByPlayer(player);
            guard.setHealth(0);
            // The recursive call enters the ordinary path, with interception disabled for this attempt.
            life.recover(); life.dying = false;
            guard.die(source);
        }
    }
    private static DamageSource source(Guard guard) {
        var life = GuardLifeAccess.get(guard); if (life.source != null) return life.source;
        var level = (ServerLevel)guard.level(); var registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        var type = registry.getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, life.damageType()));
        return type.map(holder -> new DamageSource(holder, life.attacker() == null ? null : level.getEntity(life.attacker())))
                .orElseGet(() -> level.damageSources().generic());
    }
    private static void interrupt(Guard guard) {
        MedicRuntime.cancel(guard, MedicState.Status.INTERRUPTED); MedicRuntime.stopRetreat(guard);
        com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingRuntime.synchronizeGuard(guard);
        // Stop goals so their movement/service ownership is released before the AI tick is suspended.
        guard.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        guard.targetSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        guard.stopRiding(); if (guard.isSleeping()) guard.stopSleeping(); stopActions(guard);
    }
    private static void stopActions(Guard guard) {
        guard.setTarget(null); guard.getNavigation().stop(); guard.stopUsingItem(); guard.setSprinting(false);
        guard.setSpeed(0); guard.setZza(0); guard.setXxa(0); guard.setJumping(false);
        guard.setDeltaMovement(0, guard.getDeltaMovement().y, 0);
        var operator = IGunOperator.fromLivingEntity(guard); operator.cancelReload(); operator.aim(false);
        operator.getDataHolder().meleePrepTickCount = -1;
        operator.getDataHolder().isBolting = false; operator.getDataHolder().boltTimestamp = -1;
    }
    public static void synchronize(Guard guard) {
        var access = (GuardLifeAccess)guard; var life = GuardLifeAccess.get(guard);
        access.villagerGuardGunners$setLifeVisual(life.stage().ordinal());
        int care = 0;
        if (life.casualty()) {
            care = PlayerTreatmentRuntime.visual(guard);
            var lease = MedicRuntime.lease(guard);
            if (care == 0 && lease != null && lease.patient == guard && lease.preparedAt >= 0 && lease.care != null)
                care = PlayerTreatmentRuntime.visual(lease.care, guard.level().getGameTime() - lease.preparedAt);
            if (care == 0 && life.stage() == GuardLifeState.Stage.STABILIZED && CotRestRuntime.enabled()) {
                String reason = GuardMedicAccess.get(guard).transportReason;
                care = reason.equals("no_cot") ? 5000 : reason.equals("route_blocked") ? 6000 : 4000;
            }
        } else if (life.stage() == GuardLifeState.Stage.RECOVERING && life.remaining() == 0 && CotRestRuntime.anchored(guard)) care = 7000;
        access.villagerGuardGunners$setCareVisual(care);
    }
    public static boolean patientAvailable(Guard guard) {
        return enabled() && casualty(guard) && guard.isAlive() && !guard.isRemoved() && !guard.isPassenger() && !guard.isSleeping()
                && GuardMedicalAccess.get(guard).readable() && !GuardMedicalAccess.get(guard).released()
                && GuardEquipmentServiceAccess.get(guard).readable();
    }
}
