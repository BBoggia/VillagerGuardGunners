package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Immutable, bounded result of the current server session's startup installation. */
public record VillageBuildingInstallationSnapshot(
        boolean attempted,
        List<VillageBuildingPoolEntry> requestedEntries,
        VillagePoolInjectionReport report) {

    public VillageBuildingInstallationSnapshot {
        requestedEntries = List.copyOf(Objects.requireNonNull(
                requestedEntries, "requestedEntries"));
        Objects.requireNonNull(report, "report");
        if (!attempted && (!requestedEntries.isEmpty() || report.requested() != 0)) {
            throw new IllegalArgumentException(
                    "An unattempted village-building installation cannot contain results");
        }
        if (attempted && report.requested() != requestedEntries.size()) {
            throw new IllegalArgumentException(
                    "Village-building installation request count does not match its entries");
        }
    }

    public static VillageBuildingInstallationSnapshot empty() {
        return new VillageBuildingInstallationSnapshot(
                false, List.of(), VillagePoolInjectionReport.disabled(0));
    }

    /** Bounded lookup of the exact definition used for this session's live pools. */
    public @Nullable VillageBuildingPoolEntry requestedEntry(ResourceLocation entryId) {
        Objects.requireNonNull(entryId, "entryId");
        return requestedEntries.stream()
                .filter(entry -> entry.entryId().equals(entryId))
                .findFirst()
                .orElse(null);
    }
}
