package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Bounded startup diagnostic; it never retains pools, levels, or servers. */
public record VillagePoolInjectionReport(
        boolean enabled,
        int requested,
        int injected,
        int alreadyPresent,
        int missingPools,
        int missingProcessorLists,
        int missingTemplates,
        int invalidTemplates,
        List<ResourceLocation> rejectedEntries) {

    public VillagePoolInjectionReport {
        rejectedEntries = List.copyOf(rejectedEntries);
    }

    public static VillagePoolInjectionReport disabled(int requested) {
        return new VillagePoolInjectionReport(
                false, requested, 0, 0, 0, 0, 0, 0, List.of());
    }

    public boolean hasWarnings() {
        return missingPools > 0
                || missingProcessorLists > 0
                || missingTemplates > 0
                || invalidTemplates > 0;
    }
}
