package com.gamergaming.villagerguardgunners.worldgen.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class GuardPostMarkerStateTest {
    private static final ResourceLocation GROUP = ResourceLocation.fromNamespaceAndPath(
            "villagerguardgunners",
            "watchtower");

    @Test
    void recognizedMarkersAreConsumedOnceAndStayWithinTheHardLimit() {
        GuardPostMarkerState state = GuardPostMarkerState.fresh(71L);
        GuardPostMarker.Action action = GuardPostMarker.Action.GUARD_POST;

        assertEquals(GuardPostMarkerState.ClaimResult.CLAIMED,
                state.claim(new BlockPos(0, 1, 0), action));
        assertEquals(GuardPostMarkerState.ClaimResult.ALREADY_CLAIMED,
                state.claim(new BlockPos(0, 1, 0), action));
        for (int index = 1; index < GuardPostMarkerState.MAXIMUM_RECOGNIZED_MARKERS; index++) {
            assertEquals(GuardPostMarkerState.ClaimResult.DUPLICATE_ACTION,
                    state.claim(new BlockPos(index, 1, 0), action));
        }
        assertEquals(GuardPostMarkerState.ClaimResult.LIMIT_REACHED,
                state.claim(new BlockPos(9, 1, 0), action));
        assertEquals(GuardPostMarkerState.MAXIMUM_RECOGNIZED_MARKERS,
                state.claimedMarkerCount());
    }

    @Test
    void identitySelectionAndProgressSurviveAnExactRoundTrip() {
        GuardPostMarkerState state = GuardPostMarkerState.fresh(-913L);
        state.claim(new BlockPos(4, 65, 4), GuardPostMarker.Action.GUARD_POST);
        UUID post = state.ensurePostId(
                123L,
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new BlockPos(0, 64, 0));
        GuardPostGroupSelection selection = new GuardPostGroupSelection(
                GROUP,
                "normal_patrol",
                List.of(new GuardPostGroupMember(
                        ResourceLocation.fromNamespaceAndPath("villagerguardgunners", "normal"),
                        null,
                        100)));
        state.recordSelection(selection);
        state.recordSpawnedGuard();

        GuardPostMarkerState restored = GuardPostMarkerState.load(state.save());
        assertTrue(restored.enabled());
        assertEquals(-913L, restored.selectionSeed());
        assertEquals(post, restored.postId());
        assertEquals(GROUP, restored.selectedGroup());
        assertEquals("normal_patrol", restored.selectedVariant());
        assertEquals(1, restored.claimedMarkerCount());
        assertEquals(1, restored.spawnedGuards());
        assertEquals(GuardPostMarkerState.ClaimResult.ALREADY_CLAIMED,
                restored.claim(new BlockPos(4, 65, 4), GuardPostMarker.Action.GUARD_POST));
    }

    @Test
    void missingFutureAndMalformedStateDisableRetroactiveWork() {
        assertDisabled(GuardPostMarkerState.load(null));

        CompoundTag future = GuardPostMarkerState.fresh(1L).save();
        future.putInt("Version", 2);
        assertDisabled(GuardPostMarkerState.load(future));

        CompoundTag extra = GuardPostMarkerState.fresh(1L).save();
        extra.putString("Command", "summon minecraft:creeper");
        assertDisabled(GuardPostMarkerState.load(extra));

        CompoundTag duplicatedPositions = GuardPostMarkerState.fresh(1L).save();
        duplicatedPositions.putLongArray("ClaimedMarkers", new long[] {1L, 1L});
        assertDisabled(GuardPostMarkerState.load(duplicatedPositions));

        CompoundTag tooManySpawns = GuardPostMarkerState.fresh(1L).save();
        tooManySpawns.putInt("SpawnedGuards", 5);
        assertDisabled(GuardPostMarkerState.load(tooManySpawns));
    }

    @Test
    void stateTransitionsRejectInvalidOrderingOrReplacement() {
        GuardPostMarkerState state = GuardPostMarkerState.fresh(0L);
        GuardPostGroupSelection first = selection("easy_sentry");
        GuardPostGroupSelection replacement = selection("hard_squad");

        assertThrows(IllegalStateException.class, () -> state.recordSelection(first));
        state.claim(BlockPos.ZERO, GuardPostMarker.Action.GUARD_POST);
        assertThrows(IllegalStateException.class, state::recordSpawnedGuard);
        state.ensurePostId(1L, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO);
        state.recordSelection(first);
        assertThrows(IllegalStateException.class, () -> state.recordSelection(replacement));
        for (int index = 0; index < 4; index++) {
            state.recordSpawnedGuard();
        }
        assertThrows(IllegalStateException.class, state::recordSpawnedGuard);
    }

    private static GuardPostGroupSelection selection(String variant) {
        return new GuardPostGroupSelection(
                GROUP,
                variant,
                List.of(new GuardPostGroupMember(
                        ResourceLocation.fromNamespaceAndPath("villagerguardgunners", "easy"),
                        null,
                        100)));
    }

    private static void assertDisabled(GuardPostMarkerState state) {
        assertFalse(state.enabled());
        assertNull(state.postId());
        assertEquals(GuardPostMarkerState.ClaimResult.DISABLED,
                state.claim(BlockPos.ZERO, GuardPostMarker.Action.GUARD_POST));
        assertThrows(IllegalStateException.class,
                () -> state.ensurePostId(
                        1L,
                        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                        BlockPos.ZERO));
    }
}
