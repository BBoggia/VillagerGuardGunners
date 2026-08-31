package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Immutable view of the latest attempt and the last accepted guard-group generation. */
public record GuardPostGroupReloadSnapshot(
        long generation,
        boolean lastReloadAccepted,
        Map<ResourceLocation, GuardPostGroupPool> groups,
        List<RejectedDocument> lastErrors) {
    public GuardPostGroupReloadSnapshot {
        groups = Map.copyOf(Objects.requireNonNull(groups, "groups"));
        lastErrors = List.copyOf(Objects.requireNonNull(lastErrors, "lastErrors"));
    }

    public static GuardPostGroupReloadSnapshot empty() {
        return new GuardPostGroupReloadSnapshot(0L, true, Map.of(), List.of());
    }

    public @Nullable GuardPostGroupPool get(ResourceLocation id) {
        return groups.get(Objects.requireNonNull(id, "id"));
    }

    /** The ID is null only for a registry-wide bound failure. */
    public record RejectedDocument(@Nullable ResourceLocation id, String reason) {
        public RejectedDocument {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
