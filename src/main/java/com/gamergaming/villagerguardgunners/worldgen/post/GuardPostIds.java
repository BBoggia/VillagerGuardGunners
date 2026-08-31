package com.gamergaming.villagerguardgunners.worldgen.post;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Pure deterministic identity derivation for posts and their bounded guard slots. */
public final class GuardPostIds {
    private static final String POST_DOMAIN = "villagerguardgunners:watchtower_post:v1";
    private static final String GUARD_DOMAIN = "villagerguardgunners:watchtower_guard:v1";

    private GuardPostIds() {
    }

    public static UUID postId(
            long worldSeed,
            ResourceLocation dimensionId,
            BlockPos templateOrigin) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(templateOrigin, "templateOrigin");
        String input = POST_DOMAIN + '\n'
                + worldSeed + '\n'
                + dimensionId + '\n'
                + templateOrigin.getX() + ','
                + templateOrigin.getY() + ','
                + templateOrigin.getZ();
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    public static UUID guardId(UUID postId, int memberIndex) {
        Objects.requireNonNull(postId, "postId");
        if (memberIndex < 0
                || memberIndex >= GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT) {
            throw new IllegalArgumentException("Guard member index is outside its hard bound");
        }
        String input = GUARD_DOMAIN + '\n' + postId + '\n' + memberIndex;
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
    }
}
