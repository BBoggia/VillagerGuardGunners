package com.gamergaming.villagerguardgunners.defense;

import com.gamergaming.villagerguardgunners.init.ModConfigs;

/** Fail-closed live policy for automatic duties and safe timing fallback. */
public final class VillageDefenseRuntimePolicy {
    private VillageDefenseRuntimePolicy() {
    }

    public static VillageDefenseTimings timings() {
        try {
            return ModConfigs.GUARD_GUNNERS.villageDefense.timings();
        } catch (RuntimeException exception) {
            return VillageDefenseTimings.DEFAULT;
        }
    }

    public static boolean automaticDutiesEnabled() {
        try {
            return ModConfigs.GUARD_GUNNERS.enabled.get()
                    && ModConfigs.GUARD_GUNNERS.villageDefense.automaticDuties.get();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static double dutyMovementSpeed() {
        try {
            return ModConfigs.GUARD_GUNNERS.villageDefense.dutyMovementSpeed.get();
        } catch (RuntimeException exception) {
            return 0.8D;
        }
    }
}
