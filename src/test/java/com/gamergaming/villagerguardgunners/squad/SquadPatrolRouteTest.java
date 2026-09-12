package com.gamergaming.villagerguardgunners.squad;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SquadPatrolRouteTest {
    private static final UUID SQUAD =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.tryParse("minecraft:overworld");

    @Test
    void onePointDraftBecomesAnImmutableVersionedLoop() {
        ArrayList<BlockPos> source = new ArrayList<>();
        source.add(new BlockPos(10, 64, 10));
        SquadPatrolRoute draft = SquadPatrolRoute.create(SQUAD, OVERWORLD, source, 20L);
        source.add(new BlockPos(20, 64, 10));
        SquadPatrolRoute ready = draft.revised(
                List.of(new BlockPos(10, 64, 10), new BlockPos(20, 64, 10)),
                25L);

        assertAll(
                () -> assertFalse(draft.executable()),
                () -> assertEquals(1, draft.waypoints().size()),
                () -> assertTrue(ready.executable()),
                () -> assertEquals(2L, ready.revision()),
                () -> assertEquals(20L, ready.createdGameTime()),
                () -> assertEquals(25L, ready.updatedGameTime()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> ready.waypoints().add(BlockPos.ZERO)));
    }

    @Test
    void invalidCountsPositionsSegmentsAndTimesFailClosed() {
        List<BlockPos> tooMany = java.util.stream.IntStream.rangeClosed(
                        0, SquadPatrolRoute.MAXIMUM_WAYPOINTS)
                .mapToObj(index -> new BlockPos(index, 64, 0))
                .toList();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SquadPatrolRoute.create(SQUAD, OVERWORLD, List.of(), 0L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SquadPatrolRoute.create(SQUAD, OVERWORLD, tooMany, 0L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SquadPatrolRoute.create(
                                SQUAD,
                                OVERWORLD,
                                List.of(BlockPos.ZERO, BlockPos.ZERO),
                                0L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SquadPatrolRoute.create(
                                SQUAD,
                                OVERWORLD,
                                List.of(
                                        BlockPos.ZERO,
                                        new BlockPos(
                                                SquadPatrolRoute.MAXIMUM_SEGMENT_DISTANCE + 1,
                                                0,
                                                0)),
                                0L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SquadPatrolRoute.create(
                                SQUAD,
                                OVERWORLD,
                                List.of(new BlockPos(30_000_000, 64, 0)),
                                0L)),
                () -> assertEquals(
                        new BlockPos(-30_000_000, 64, 0),
                        SquadPatrolRoute.create(
                                SQUAD,
                                OVERWORLD,
                                List.of(new BlockPos(-30_000_000, 64, 0)),
                                0L).waypoints().get(0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SquadPatrolRoute.create(
                                SQUAD,
                                OVERWORLD,
                                List.of(BlockPos.ZERO),
                                -1L)));
    }
}
