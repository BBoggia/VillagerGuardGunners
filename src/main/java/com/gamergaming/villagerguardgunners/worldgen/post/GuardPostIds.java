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
    private static final String SQUAD_DOMAIN = "villagerguardgunners:watchtower_squad:v1";
    private static final String LOADOUT_DOMAIN = "villagerguardgunners:watchtower_loadout:v1";
    private static final String ARMORY_LOOT_DOMAIN = "villagerguardgunners:watchtower_armory_loot:v1";
    private static final long ZERO_LOOT_SEED_REPLACEMENT = 0x6A09E667F3BCC909L;

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

    public static UUID squadId(UUID postId) {
        return namespacedUuid(SQUAD_DOMAIN, Objects.requireNonNull(postId, "postId").toString());
    }

    public static long memberLoadoutSeed(UUID postId, int memberIndex) {
        if (memberIndex < 0
                || memberIndex >= GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT) {
            throw new IllegalArgumentException("Guard member index is outside its hard bound");
        }
        UUID identity = namespacedUuid(
                LOADOUT_DOMAIN,
                Objects.requireNonNull(postId, "postId") + "\n" + memberIndex);
        return identity.getMostSignificantBits() ^ identity.getLeastSignificantBits();
    }

    public static long armoryLootSeed(UUID postId) {
        UUID identity = namespacedUuid(
                ARMORY_LOOT_DOMAIN,
                Objects.requireNonNull(postId, "postId").toString());
        return nonZeroLootSeed(
                identity.getMostSignificantBits() ^ identity.getLeastSignificantBits());
    }

    static long nonZeroLootSeed(long seed) {
        return seed == 0L ? ZERO_LOOT_SEED_REPLACEMENT : seed;
    }

    private static UUID namespacedUuid(String domain, String value) {
        return UUID.nameUUIDFromBytes(
                (domain + '\n' + value).getBytes(StandardCharsets.UTF_8));
    }
}
