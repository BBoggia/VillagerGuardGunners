package com.gamergaming.villagerguardgunners.role;

import com.gamergaming.villagerguardgunners.orders.GuardOrders;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** One immutable, fully resolved datapack-defined behavioral role. */
public record GuardRoleDefinition(
        ResourceLocation id,
        List<ResourceLocation> parents,
        GuardOrders declaredDefaults,
        GuardOrders effectiveDefaults) {
    public GuardRoleDefinition {
        id = Objects.requireNonNull(id, "id");
        parents = List.copyOf(Objects.requireNonNull(parents, "parents"));
        declaredDefaults = Objects.requireNonNull(declaredDefaults, "declaredDefaults");
        effectiveDefaults = Objects.requireNonNull(effectiveDefaults, "effectiveDefaults");
    }
}
