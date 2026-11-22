package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** The bounded, deterministic result later marker handling may consume once. */
public record GuardPostGroupSelection(
        ResourceLocation groupId,
        String variantName,
        List<GuardPostGroupMember> members) {
    public GuardPostGroupSelection {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(variantName, "variantName");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (members.isEmpty()
                || members.size() > GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT) {
            throw new IllegalArgumentException("A selected group must contain one through four members");
        }
    }
}
