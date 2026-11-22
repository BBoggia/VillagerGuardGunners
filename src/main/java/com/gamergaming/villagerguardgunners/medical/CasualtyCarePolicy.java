package com.gamergaming.villagerguardgunners.medical;

import com.gamergaming.villagerguardgunners.api.*;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import net.minecraft.core.BlockPos;
import tallestegg.guardvillagers.entities.Guard;

/** One immutable timing decision per dose, shared by medic and player treatment. */
public record CasualtyCarePolicy(Mode mode, int channelTicks, int recoveryTicks, int fieldRecoveryTicks, BlockPos cot) {
    public enum Mode { NORMAL, COT, FIELD }
    public static CasualtyCarePolicy forPatient(Guard patient) {
        if (!CotRestRuntime.enabled() || GuardLifeAccess.get(patient).stage() != GuardLifeState.Stage.STABILIZED)
            return new CasualtyCarePolicy(Mode.NORMAL, 40, GuardLifeState.RECOVERY_TICKS, 0, null);
        var settings = ModConfigs.GUARD_GUNNERS.medical;
        int cotChannel = settings.cotRevivalTicks.get(), fieldChannel = Math.max(cotChannel, settings.fieldRevivalTicks.get());
        int cotRest = settings.cotRecoveryTicks.get(), fieldRest = Math.max(cotRest, settings.fieldRecoveryTicks.get());
        return CotRestRuntime.valid(patient)
                ? new CasualtyCarePolicy(Mode.COT, cotChannel, cotRest, fieldRest, GuardLifeAccess.cot(patient).head())
                : new CasualtyCarePolicy(Mode.FIELD, fieldChannel, fieldRest, fieldRest, null);
    }
    public boolean validFor(Guard patient) {
        if (mode == Mode.NORMAL) return !CotRestRuntime.enabled() || GuardLifeAccess.get(patient).stage() != GuardLifeState.Stage.STABILIZED;
        if (!CotRestRuntime.enabled() || GuardLifeAccess.get(patient).stage() != GuardLifeState.Stage.STABILIZED) return false;
        return mode == Mode.COT ? CotRestRuntime.valid(patient) && cot.equals(GuardLifeAccess.cot(patient).head()) : !CotRestRuntime.anchored(patient);
    }
    public static boolean timeForEvacuation(Guard patient) {
        long cooldown = Math.max(0, GuardMedicalAccess.get(patient).readyAt() - patient.level().getGameTime());
        var settings = ModConfigs.GUARD_GUNNERS.medical;
        return GuardLifeAccess.get(patient).remaining() > cooldown + Math.max(settings.cotRevivalTicks.get(), settings.fieldRevivalTicks.get()) + 60;
    }
}
