package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardPostIdsTest {
    private static final ResourceLocation OVERWORLD = id("minecraft", "overworld");

    @Test
    void postIdentityIsStableAndIncludesEveryWorldKey() {
        BlockPos origin = new BlockPos(12, 64, -30);
        UUID expected = GuardPostIds.postId(42L, OVERWORLD, origin);

        assertEquals(expected, GuardPostIds.postId(42L, OVERWORLD, origin));
        assertNotEquals(expected, GuardPostIds.postId(-42L, OVERWORLD, origin));
        assertNotEquals(expected, GuardPostIds.postId(42L, id("minecraft", "the_nether"), origin));
        assertNotEquals(expected, GuardPostIds.postId(42L, OVERWORLD, origin.east()));
    }

    @Test
    void fourGuardSlotsAreStableDistinctAndHardBounded() {
        UUID post = GuardPostIds.postId(91L, OVERWORLD, BlockPos.ZERO);
        Set<UUID> generated = new HashSet<>();
        for (int index = 0; index < GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT; index++) {
            UUID guard = GuardPostIds.guardId(post, index);
            assertEquals(guard, GuardPostIds.guardId(post, index));
            generated.add(guard);
        }

        assertEquals(GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT, generated.size());
        assertThrows(IllegalArgumentException.class, () -> GuardPostIds.guardId(post, -1));
        assertThrows(IllegalArgumentException.class,
                () -> GuardPostIds.guardId(post, GuardPostGroupFormat.MAXIMUM_MEMBERS_PER_VARIANT));
    }

    @Test
    void derivedSquadLoadoutAndLootIdentitiesAreStableAndDomainSeparated() {
        UUID post = GuardPostIds.postId(91L, OVERWORLD, BlockPos.ZERO);
        UUID squad = GuardPostIds.squadId(post);
        long firstLoadout = GuardPostIds.memberLoadoutSeed(post, 0);

        assertEquals(squad, GuardPostIds.squadId(post));
        assertNotEquals(post, squad);
        assertEquals(firstLoadout, GuardPostIds.memberLoadoutSeed(post, 0));
        assertNotEquals(firstLoadout, GuardPostIds.memberLoadoutSeed(post, 1));
        assertNotEquals(firstLoadout, GuardPostIds.armoryLootSeed(post));
        assertThrows(IllegalArgumentException.class,
                () -> GuardPostIds.memberLoadoutSeed(post, 4));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
