package com.gamergaming.villagerguardgunners.squad;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SquadPatrolRouteRegistryTest {
    private static final UUID SQUAD =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID MISSING =
            UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation NETHER =
            ResourceLocation.tryParse("minecraft:the_nether");

    @Test
    void routeUpdatesRoundTripAndSquadRemovalOwnsRouteLifecycle() {
        SquadRegistrySavedData registry = new SquadRegistrySavedData();
        registry.register(SQUAD, "Rangers", null, 10L).orElseThrow();
        List<BlockPos> draft = List.of(new BlockPos(10, 64, 10));
        List<BlockPos> ready = List.of(
                new BlockPos(10, 64, 10),
                new BlockPos(20, 64, 10));

        SquadRegistrySavedData.RouteUpdateResult missing =
                registry.replacePatrolRoute(MISSING, OVERWORLD, draft, 20L);
        SquadRegistrySavedData.RouteUpdateResult created =
                registry.replacePatrolRoute(SQUAD, OVERWORLD, draft, 20L);
        SquadRegistrySavedData.RouteUpdateResult unchanged =
                registry.replacePatrolRoute(SQUAD, OVERWORLD, draft, 21L);
        SquadRegistrySavedData.RouteUpdateResult wrongDimension =
                registry.replacePatrolRoute(SQUAD, NETHER, ready, 22L);
        SquadRegistrySavedData.RouteUpdateResult revised =
                registry.replacePatrolRoute(SQUAD, OVERWORLD, ready, 22L);
        SquadRegistrySavedData restored = SquadRegistrySavedData.load(
                registry.save(new CompoundTag()));

        assertAll(
                () -> assertEquals(
                        SquadRegistrySavedData.RouteUpdateStatus.SQUAD_NOT_FOUND,
                        missing.status()),
                () -> assertEquals(
                        SquadRegistrySavedData.RouteUpdateStatus.UPDATED,
                        created.status()),
                () -> assertEquals(
                        SquadRegistrySavedData.RouteUpdateStatus.UNCHANGED,
                        unchanged.status()),
                () -> assertEquals(
                        SquadRegistrySavedData.RouteUpdateStatus.WRONG_DIMENSION,
                        wrongDimension.status()),
                () -> assertEquals(2L, revised.route().revision()),
                () -> assertEquals(revised.route(), restored.findPatrolRoute(SQUAD).orElseThrow()),
                () -> assertFalse(restored.isDirty()),
                () -> assertTrue(restored.remove(SQUAD)),
                () -> assertTrue(restored.findPatrolRoute(SQUAD).isEmpty()));
    }

    @Test
    void invalidAndOrphanedRouteDataDisablesAndPreservesTheWholeRoot() {
        SquadRegistrySavedData registry = new SquadRegistrySavedData();
        registry.register(SQUAD, "Rangers", null, 10L).orElseThrow();
        registry.replacePatrolRoute(
                SQUAD,
                OVERWORLD,
                List.of(new BlockPos(10, 64, 10)),
                20L);
        CompoundTag malformed = registry.save(new CompoundTag());
        ListTag routes = malformed.getList("Routes", Tag.TAG_COMPOUND);
        routes.getCompound(0).putUUID("SquadId", MISSING);

        SquadRegistrySavedData disabled = SquadRegistrySavedData.load(malformed);

        assertAll(
                () -> assertFalse(disabled.enabled()),
                () -> assertEquals(0, disabled.size()),
                () -> assertEquals(0, disabled.patrolRouteCount()),
                () -> assertEquals(malformed, disabled.save(new CompoundTag())),
                () -> assertEquals(
                        SquadRegistrySavedData.RouteUpdateStatus.DISABLED,
                        disabled.replacePatrolRoute(
                                SQUAD,
                                OVERWORLD,
                                List.of(BlockPos.ZERO),
                                30L).status()));
    }

    @Test
    void clearIsAtomicAndInvalidReplacementLeavesExistingRouteUntouched() {
        SquadRegistrySavedData registry = new SquadRegistrySavedData();
        registry.register(SQUAD, "Rangers", null, 10L).orElseThrow();
        SquadPatrolRoute route = registry.replacePatrolRoute(
                        SQUAD,
                        OVERWORLD,
                        List.of(new BlockPos(10, 64, 10)),
                        20L)
                .route();
        registry.setDirty(false);
        SquadRegistrySavedData.RouteUpdateResult invalid = registry.replacePatrolRoute(
                SQUAD,
                OVERWORLD,
                List.of(new BlockPos(30_000_001, 64, 0)),
                21L);

        assertAll(
                () -> assertEquals(
                        SquadRegistrySavedData.RouteUpdateStatus.INVALID_ROUTE,
                        invalid.status()),
                () -> assertFalse(registry.isDirty()),
                () -> assertEquals(route, registry.findPatrolRoute(SQUAD).orElseThrow()),
                () -> assertEquals(
                        SquadRegistrySavedData.RouteUpdateStatus.CLEARED,
                        registry.replacePatrolRoute(SQUAD, OVERWORLD, List.of(), 22L).status()),
                () -> assertTrue(registry.findPatrolRoute(SQUAD).isEmpty()));
    }

    @Test
    void clearAndRecreateKeepsRevisionMonotonicAcrossSaveLoad() {
        SquadRegistrySavedData registry = new SquadRegistrySavedData();
        registry.register(SQUAD, "Rangers", null, 10L).orElseThrow();
        registry.replacePatrolRoute(
                SQUAD, OVERWORLD, List.of(new BlockPos(10, 64, 10)), 20L);
        registry.replacePatrolRoute(SQUAD, OVERWORLD, List.of(), 21L);
        SquadRegistrySavedData restored = SquadRegistrySavedData.load(
                registry.save(new CompoundTag()));

        SquadRegistrySavedData.RouteUpdateResult recreated =
                restored.replacePatrolRoute(
                        SQUAD,
                        NETHER,
                        List.of(new BlockPos(20, 64, 20)),
                        22L);
        restored.remove(SQUAD);
        restored.register(SQUAD, "Recovered Rangers", null, 23L).orElseThrow();
        SquadRegistrySavedData.RouteUpdateResult recovered =
                restored.replacePatrolRoute(
                        SQUAD,
                        OVERWORLD,
                        List.of(new BlockPos(30, 64, 30)),
                        24L);

        assertAll(
                () -> assertEquals(
                        SquadRegistrySavedData.RouteUpdateStatus.UPDATED,
                        recreated.status()),
                () -> assertEquals(2L, recreated.route().revision()),
                () -> assertEquals(NETHER, recreated.route().dimensionId()),
                () -> assertEquals(3L, recovered.route().revision()));
    }

    @Test
    void malformedWaypointInputAndBackwardClearFailWithoutMutation() {
        SquadRegistrySavedData registry = new SquadRegistrySavedData();
        registry.register(SQUAD, "Rangers", null, 10L).orElseThrow();
        SquadPatrolRoute route = registry.replacePatrolRoute(
                        SQUAD,
                        OVERWORLD,
                        List.of(new BlockPos(10, 64, 10)),
                        20L)
                .route();
        List<BlockPos> nullWaypoint = new java.util.ArrayList<>();
        nullWaypoint.add(null);

        assertAll(
                () -> assertEquals(
                        SquadRegistrySavedData.RouteUpdateStatus.INVALID_ROUTE,
                        registry.replacePatrolRoute(
                                SQUAD, OVERWORLD, nullWaypoint, 21L).status()),
                () -> assertEquals(
                        SquadRegistrySavedData.RouteUpdateStatus.INVALID_ROUTE,
                        registry.replacePatrolRoute(
                                SQUAD, OVERWORLD, List.of(), 19L).status()),
                () -> assertEquals(route, registry.findPatrolRoute(SQUAD).orElseThrow()));
    }

    @Test
    void routeUpdateResultsRejectContradictoryPayloads() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SquadRegistrySavedData.RouteUpdateResult(
                                SquadRegistrySavedData.RouteUpdateStatus.UPDATED,
                                null)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SquadRegistrySavedData.RouteUpdateResult(
                                SquadRegistrySavedData.RouteUpdateStatus.CLEARED,
                                SquadPatrolRoute.create(
                                        SQUAD,
                                        OVERWORLD,
                                        List.of(BlockPos.ZERO),
                                        0L))));
    }
}
