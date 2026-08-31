package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Persistent proof that one generated Armory has completed its one-shot setup. */
public record GuardPostArmoryIdentity(
        int schemaVersion,
        UUID postId,
        @Nullable ResourceLocation lootTableId) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public GuardPostArmoryIdentity {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported generated Armory schema");
        }
        postId = Objects.requireNonNull(postId, "postId");
        if (NIL_UUID.equals(postId)) {
            throw new IllegalArgumentException("Generated Armory post ID cannot be nil");
        }
    }

    public static GuardPostArmoryIdentity initialized(
            UUID postId,
            @Nullable ResourceLocation lootTableId) {
        return new GuardPostArmoryIdentity(CURRENT_SCHEMA_VERSION, postId, lootTableId);
    }
}
