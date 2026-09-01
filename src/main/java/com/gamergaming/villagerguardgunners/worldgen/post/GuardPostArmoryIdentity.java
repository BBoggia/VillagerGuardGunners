package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Persistent state for one generated Armory's one-shot setup. */
public record GuardPostArmoryIdentity(
        int schemaVersion,
        UUID postId,
        @Nullable ResourceLocation lootTableId,
        State state,
        long lootSeed) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public GuardPostArmoryIdentity {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported generated Armory schema");
        }
        postId = Objects.requireNonNull(postId, "postId");
        state = Objects.requireNonNull(state, "state");
        if (NIL_UUID.equals(postId)) {
            throw new IllegalArgumentException("Generated Armory post ID cannot be nil");
        }
        if (state == State.PENDING && (lootTableId == null || lootSeed == 0L)) {
            throw new IllegalArgumentException(
                    "Pending generated Armory setup requires a loot table and non-zero seed");
        }
    }

    public static GuardPostArmoryIdentity initialized(
            UUID postId,
            @Nullable ResourceLocation lootTableId) {
        return new GuardPostArmoryIdentity(
                CURRENT_SCHEMA_VERSION,
                postId,
                lootTableId,
                State.COMPLETE,
                0L);
    }

    public static GuardPostArmoryIdentity pending(
            UUID postId,
            ResourceLocation lootTableId,
            long lootSeed) {
        return new GuardPostArmoryIdentity(
                CURRENT_SCHEMA_VERSION,
                postId,
                Objects.requireNonNull(lootTableId, "lootTableId"),
                State.PENDING,
                lootSeed);
    }

    public GuardPostArmoryIdentity completed() {
        return new GuardPostArmoryIdentity(
                CURRENT_SCHEMA_VERSION,
                postId,
                lootTableId,
                State.COMPLETE,
                lootSeed);
    }

    public boolean pending() {
        return state == State.PENDING;
    }

    public enum State {
        PENDING,
        COMPLETE
    }
}
