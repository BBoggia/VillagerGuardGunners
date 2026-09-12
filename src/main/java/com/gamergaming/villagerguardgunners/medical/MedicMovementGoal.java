package com.gamergaming.villagerguardgunners.medical;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import tallestegg.guardvillagers.entities.Guard;

/** Temporary navigation ownership; explicit commands, injury and hand actions invalidate it immediately. */
public final class MedicMovementGoal extends Goal {
    private final Guard guard;
    private long nextPath, progressAt;
    private double previousDistance;
    private int failures;
    private boolean owns;
    public MedicMovementGoal(Guard guard) { this.guard = guard; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    @Override public boolean canUse() {
        if (MedicRuntime.retreating(guard) && (MedicEligibility.fraction(guard) >= MedicRuntime.RECOVER_AT
                || !com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(guard).retreat()
                || !com.gamergaming.villagerguardgunners.init.ModConfigs.GUARD_GUNNERS.medical.retreat.get())) {
            MedicRuntime.stopRetreat(guard); com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(guard).recovering = false;
            com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(guard).status = MedicState.Status.IDLE;
        }
        var destination = MedicRuntime.destination(guard);
        if (destination == null) return false;
        var lease = MedicRuntime.lease(guard);
        boolean valid = lease != null ? MedicRuntime.valid(lease) : MedicEligibility.request(guard, true)
                && MedicEligibility.available(destination, false) && com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(destination).medic()
                && MedicEligibility.supplySlot(destination) >= 0 && guard.distanceToSqr(destination) <= 1600;
        if (!valid) { MedicRuntime.cancel(guard, MedicState.Status.INTERRUPTED); MedicRuntime.stopRetreat(guard); }
        return valid && (lease != null || MedicEligibility.available(guard, true));
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { owns = true; guard.getNavigation().stop(); nextPath = 0; progressAt = guard.level().getGameTime()+80; previousDistance = Double.POSITIVE_INFINITY; failures = 0; }
    @Override public void stop() { if (owns) guard.getNavigation().stop(); owns = false; MedicRuntime.cancel(guard, MedicState.Status.INTERRUPTED); }
    @Override public void tick() {
        Guard destination = MedicRuntime.destination(guard); if (!owns || destination == null) return;
        var lease = MedicRuntime.lease(guard); long now = guard.level().getGameTime(); double distance = guard.distanceToSqr(destination);
        guard.getLookControl().setLookAt(destination, 30, 30);
        if (distance <= 4) {
            guard.getNavigation().stop();
            if (lease != null && lease.medic == guard) {
                // Navigation stopping leaves residual motion; wait for arrival before measuring an uninterrupted channel.
                if (lease.preparedAt < 0 && (guard.getDeltaMovement().horizontalDistanceSqr() > .0004
                        || lease.patient.getDeltaMovement().horizontalDistanceSqr() > .0004)) return;
                MedicRuntime.treat(lease);
            }
            return;
        }
        if (lease != null) lease.preparedAt = -1;
        if (now >= progressAt) { if (previousDistance-distance < .25) { failed(); return; } previousDistance=distance; progressAt=now+80; }
        if (now < nextPath) return; nextPath=now+20;
        if (!loadedRegion()) { failed(); return; }
        try {
            var path = guard.getNavigation().createPath(destination, 1);
            if (path == null || !guard.getNavigation().moveTo(path, .8)) { if (++failures >= 3) failed(); }
        } catch (RuntimeException exception) { failed(); }
    }
    private void failed() {
        var lease = MedicRuntime.lease(guard);
        if (lease != null && lease.medic == guard) {
            var duty = com.gamergaming.villagerguardgunners.api.GuardMedicAccess.get(guard);
            duty.unreachablePatient = lease.patient.getUUID(); duty.unreachableUntil = guard.level().getGameTime() + 200;
        }
        MedicRuntime.cancel(guard, MedicState.Status.INTERRUPTED); MedicRuntime.stopRetreat(guard); guard.getNavigation().stop(); }
    private boolean loadedRegion() {
        if (!(guard.level() instanceof ServerLevel level)) return false;
        double range=guard.getAttributeValue(Attributes.FOLLOW_RANGE); if (!Double.isFinite(range) || range<0 || range>64) return false;
        int radius=(int)Math.ceil(range)+16; var pos=guard.blockPosition();
        for(int x=(pos.getX()-radius)>>4;x<=(pos.getX()+radius)>>4;x++) for(int z=(pos.getZ()-radius)>>4;z<=(pos.getZ()+radius)>>4;z++)
            if(!level.getChunkSource().hasChunk(x,z))return false;
        return true;
    }
}
