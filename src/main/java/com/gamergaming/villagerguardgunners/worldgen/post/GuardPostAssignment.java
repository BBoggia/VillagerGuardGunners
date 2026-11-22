package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Objects;
import javax.annotation.Nullable;
import com.gamergaming.villagerguardgunners.spawn.NaturalGuardDifficulty;
import net.minecraft.resources.ResourceLocation;

/** Immutable, identifier-only work handed from world generation to the server thread. */
public record GuardPostAssignment(
        int schemaVersion,
        ResourceLocation loadoutPool,
        @Nullable ResourceLocation roleId,
        NaturalGuardDifficulty generationDifficulty) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public GuardPostAssignment {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported guard-post assignment schema");
        }
        loadoutPool = Objects.requireNonNull(loadoutPool, "loadoutPool");
        generationDifficulty = Objects.requireNonNull(
                generationDifficulty,
                "generationDifficulty");
    }

    public static GuardPostAssignment pending(
            GuardPostGroupMember member,
            NaturalGuardDifficulty generationDifficulty) {
        Objects.requireNonNull(member, "member");
        return new GuardPostAssignment(
                CURRENT_SCHEMA_VERSION,
                member.loadoutPool(),
                member.role(),
                generationDifficulty);
    }
}
