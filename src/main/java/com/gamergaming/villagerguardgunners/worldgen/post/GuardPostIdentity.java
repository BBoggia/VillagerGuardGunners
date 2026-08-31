package com.gamergaming.villagerguardgunners.worldgen.post;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/** Immutable provenance linking one generated guard to one deterministic post and selection. */
public record GuardPostIdentity(
        int schemaVersion,
        UUID postId,
        ResourceLocation groupId,
        String variantName,
        int memberIndex) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final UUID NIL_UUID = new UUID(0L, 0L);
    private static final Pattern VARIANT_NAME = Pattern.compile("[a-z0-9._/-]{1,64}");

    public GuardPostIdentity {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported guard-post identity schema");
        }
        postId = Objects.requireNonNull(postId, "postId");
        if (NIL_UUID.equals(postId)) {
            throw new IllegalArgumentException("Post ID cannot be the nil UUID");
        }
        Objects.requireNonNull(groupId, "groupId");
        if (variantName == null || !VARIANT_NAME.matcher(variantName).matches()) {
            throw new IllegalArgumentException("Invalid guard-post variant name");
        }
        if (memberIndex < 0
                || memberIndex >= GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT) {
            throw new IllegalArgumentException("Guard-post member index is outside its hard bound");
        }
    }

    public static GuardPostIdentity assigned(
            UUID postId,
            ResourceLocation groupId,
            String variantName,
            int memberIndex) {
        return new GuardPostIdentity(
                CURRENT_SCHEMA_VERSION,
                postId,
                groupId,
                variantName,
                memberIndex);
    }
}
