package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Immutable last-known-good definitions plus the bounded latest reload result. */
public record VillageBuildingDefinitionReloadSnapshot(
        long generation,
        boolean lastReloadAccepted,
        Map<ResourceLocation, VillageBuildingPoolEntry> definitions,
        List<RejectedDocument> lastErrors) {
    public VillageBuildingDefinitionReloadSnapshot {
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(definitions, "definitions")));
        lastErrors = List.copyOf(Objects.requireNonNull(lastErrors, "lastErrors"));
    }

    public static VillageBuildingDefinitionReloadSnapshot empty() {
        return new VillageBuildingDefinitionReloadSnapshot(0L, true, Map.of(), List.of());
    }

    public @Nullable VillageBuildingPoolEntry get(ResourceLocation id) {
        return definitions.get(Objects.requireNonNull(id, "id"));
    }

    public List<VillageBuildingPoolEntry> entries() {
        return List.copyOf(definitions.values());
    }

    /** The ID is null only when a publication-wide hard bound fails. */
    public record RejectedDocument(@Nullable ResourceLocation id, String reason) {
        public RejectedDocument {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
