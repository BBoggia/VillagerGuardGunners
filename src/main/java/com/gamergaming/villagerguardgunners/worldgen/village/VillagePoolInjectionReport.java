package com.gamergaming.villagerguardgunners.worldgen.village;

/** Bounded startup diagnostic; it never retains pools, levels, or servers. */
public record VillagePoolInjectionReport(
        boolean enabled,
        int requested,
        int injected,
        int alreadyPresent,
        int missingPools,
        int missingProcessorLists) {

    public static VillagePoolInjectionReport disabled(int requested) {
        return new VillagePoolInjectionReport(false, requested, 0, 0, 0, 0);
    }

    public boolean hasWarnings() {
        return missingPools > 0 || missingProcessorLists > 0;
    }
}
