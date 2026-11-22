package com.gamergaming.villagerguardgunners.role;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Immutable diagnostic view of the last attempted and last successful role reload. */
public record GuardRoleReloadSnapshot(
        long generation,
        boolean lastReloadAccepted,
        Map<ResourceLocation, GuardRoleDefinition> roles,
        List<String> lastErrors) {
    public GuardRoleReloadSnapshot {
        roles = Map.copyOf(Objects.requireNonNull(roles, "roles"));
        lastErrors = List.copyOf(Objects.requireNonNull(lastErrors, "lastErrors"));
    }

    public static GuardRoleReloadSnapshot empty() {
        return new GuardRoleReloadSnapshot(0L, true, Map.of(), List.of());
    }

    public @Nullable GuardRoleDefinition get(ResourceLocation id) {
        return roles.get(id);
    }
}
