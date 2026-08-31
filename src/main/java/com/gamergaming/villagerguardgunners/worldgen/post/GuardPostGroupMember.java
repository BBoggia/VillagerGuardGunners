package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** One immutable occupant request; it contains identifiers and never creates equipment. */
public record GuardPostGroupMember(
        ResourceLocation loadoutPool,
        @Nullable ResourceLocation role,
        int chancePercent) {
    public GuardPostGroupMember {
        Objects.requireNonNull(loadoutPool, "loadoutPool");
        if (chancePercent <= 0
                || chancePercent > GuardPostGroupFormat.MAXIMUM_CHANCE_PERCENT) {
            throw new IllegalArgumentException("Member chance must be from 1 through 100");
        }
    }

    public boolean required() {
        return chancePercent == GuardPostGroupFormat.MAXIMUM_CHANCE_PERCENT;
    }
}
