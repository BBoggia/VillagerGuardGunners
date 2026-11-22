package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import com.gamergaming.villagerguardgunners.quartermaster.service.QuartermasterServiceRuntime;
import com.gamergaming.villagerguardgunners.quartermaster.service.ServiceTransactions;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import com.gamergaming.villagerguardgunners.sidearm.SidearmState;
import com.gamergaming.villagerguardgunners.squad.SquadCommandRuntime;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteResolver;
import tallestegg.guardvillagers.entities.Guard;

public final class MedicEligibility {
    public static boolean enabled() { return ModConfigs.GUARD_GUNNERS.medical.enabled.get() && ModConfigs.GUARD_GUNNERS.medical.medicAi.get(); }
    public static double fraction(Guard guard) {
        if (GuardLifeRuntime.casualty(guard)) return GuardLifeAccess.get(guard).stage() == GuardLifeState.Stage.DOWNED ? -2 : -1;
        double health = guard.getHealth(), maximum = guard.getMaxHealth();
        return Double.isFinite(health) && Double.isFinite(maximum) && maximum > 0 ? health / maximum : 1;
    }
    public static boolean playerControl(Guard guard) {
        var movement = GuardRoleResolver.effectiveOrders(guard).movement();
        return guard.interacting || ((GuardCoverageAccess) guard).villagerGuardGunners$hasFollowOrder() || guard.isPatrolling() || movement.holdsPosition()
                || movement == GuardOrders.MovementOrder.PATROL_BOUND || SquadCommandRuntime.activeCommand(guard) != null
                || SquadPatrolRouteResolver.hasLoadedWaypoint(guard);
    }
    public static boolean available(Guard guard, boolean allowCombat) {
        return request(guard, allowCombat) && ((GuardCoverageAccess)guard).villagerGuardGunners$nativeEquipmentServiceSafe();
    }
    public static boolean request(Guard guard, boolean allowCombat) {
        if (GuardLifeRuntime.blocked(guard) || !enabled() || guard.level().isClientSide || !guard.isAlive() || guard.isRemoved() || guard.isNoAi()
                || guard.isPassenger() || guard.isSleeping() || playerControl(guard)
                || !GuardMedicAccess.get(guard).known() || !GuardEquipmentServiceAccess.get(guard).readable()
                || !GuardMedicalAccess.get(guard).readable() || GuardMedicalAccess.get(guard).released()
                || ServiceTransactions.mutating(guard) || QuartermasterServiceRuntime.plan(guard) != null) return false;
        var resupply = GuardArmoryResupplyAccess.getController(guard);
        if (resupply != null && resupply.hasActiveRequest()) return false;
        return (allowCombat || guard.getTarget() == null || !guard.getTarget().isAlive())
                && GuardSidearmAccess.getState(guard) == SidearmState.PRIMARY_ACTIVE;
    }
    public static boolean allied(Guard medic, Guard patient) {
        return medic != patient && medic.level() == patient.level() && medic.getTarget() != patient && patient.getTarget() != medic
                && GuardAllyProtection.isProtected(medic, patient) && GuardAllyProtection.isProtected(patient, medic);
    }
    public static int supplySlot(Guard guard) {
        var pouch = GuardMedicalAccess.get(guard);
        if (!pouch.available()) return -1;
        for (int i = 0; i < MedicalStorage.SLOTS; i++) if (TreatmentProfiles.resolve(pouch.inventory().getStackInSlot(i)) != null) return i;
        return -1;
    }
}
