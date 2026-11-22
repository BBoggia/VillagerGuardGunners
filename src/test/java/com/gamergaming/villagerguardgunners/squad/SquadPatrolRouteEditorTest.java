package com.gamergaming.villagerguardgunners.squad;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteEditor.EditAction;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteEditor.EditResult;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteEditor.EditStatus;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SquadPatrolRouteEditorTest {
    private static final UUID SQUAD = UUID.fromString(
            "70000000-0000-0000-0000-000000000001");
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation NETHER =
            ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");

    @Test
    void addInspectRemoveAndRecreateAreAtomic() {
        SquadRegistrySavedData registry = registry();
        SquadMetadata metadata = registry.find(SQUAD).orElseThrow();

        EditResult first = apply(
                registry, metadata, OVERWORLD, EditAction.ADD_WAYPOINT,
                new BlockPos(0, 64, 0), 20L);
        EditResult second = apply(
                registry, metadata, OVERWORLD, EditAction.ADD_WAYPOINT,
                new BlockPos(10, 64, 0), 21L);
        EditResult inspected = apply(
                registry, metadata, OVERWORLD, EditAction.INSPECT, null, 22L);
        EditResult removed = apply(
                registry, metadata, OVERWORLD, EditAction.REMOVE_LAST, null, 23L);
        EditResult cleared = apply(
                registry, metadata, OVERWORLD, EditAction.REMOVE_LAST, null, 24L);
        boolean routeAbsentAfterClear = registry.findPatrolRoute(SQUAD).isEmpty();
        EditResult recreated = apply(
                registry, metadata, NETHER, EditAction.ADD_WAYPOINT,
                new BlockPos(2, 70, 2), 25L);

        assertAll(
                () -> assertEquals(EditStatus.WAYPOINT_ADDED, first.status()),
                () -> assertFalse(first.executable()),
                () -> assertEquals(1L, first.revision()),
                () -> assertEquals(EditStatus.WAYPOINT_ADDED, second.status()),
                () -> assertTrue(second.executable()),
                () -> assertEquals(EditStatus.ROUTE_READY, inspected.status()),
                () -> assertEquals(2, inspected.waypointCount()),
                () -> assertEquals(EditStatus.WAYPOINT_REMOVED, removed.status()),
                () -> assertFalse(removed.executable()),
                () -> assertEquals(EditStatus.ROUTE_CLEARED, cleared.status()),
                () -> assertTrue(routeAbsentAfterClear),
                () -> assertTrue(registry.findPatrolRoute(SQUAD).isPresent()),
                () -> assertEquals(4L, recreated.revision()),
                () -> assertEquals(NETHER, recreated.dimensionId()));
    }

    @Test
    void invalidCrossDimensionAndFullEditsLeaveTheRouteUntouched() {
        SquadRegistrySavedData registry = registry();
        SquadMetadata metadata = registry.find(SQUAD).orElseThrow();
        List<BlockPos> maximum = IntStream.range(
                        0, SquadPatrolRoute.MAXIMUM_WAYPOINTS)
                .mapToObj(index -> new BlockPos(index, 64, 0))
                .toList();
        SquadPatrolRoute original = registry.replacePatrolRoute(
                        SQUAD, OVERWORLD, maximum, 20L)
                .route();
        SquadRegistrySavedData invalidRegistry = registry();
        SquadMetadata invalidMetadata = invalidRegistry.find(SQUAD).orElseThrow();
        SquadPatrolRoute invalidOriginal = invalidRegistry.replacePatrolRoute(
                        SQUAD,
                        OVERWORLD,
                        List.of(new BlockPos(5, 64, 5)),
                        20L)
                .route();

        EditResult full = apply(
                registry, metadata, OVERWORLD, EditAction.ADD_WAYPOINT,
                new BlockPos(64, 64, 0), 21L);
        EditResult wrongDimension = apply(
                registry, metadata, NETHER, EditAction.ADD_WAYPOINT,
                new BlockPos(64, 64, 0), 22L);
        EditResult invalid = apply(
                invalidRegistry, invalidMetadata, OVERWORLD, EditAction.ADD_WAYPOINT,
                new BlockPos(5, 64, 5), 23L);

        assertAll(
                () -> assertEquals(EditStatus.ROUTE_FULL, full.status()),
                () -> assertEquals(EditStatus.WRONG_DIMENSION, wrongDimension.status()),
                () -> assertEquals(EditStatus.INVALID_WAYPOINT, invalid.status()),
                () -> assertEquals(original, registry.findPatrolRoute(SQUAD).orElseThrow()),
                () -> assertEquals(
                        invalidOriginal,
                        invalidRegistry.findPatrolRoute(SQUAD).orElseThrow()));
    }

    @Test
    void missingRouteAndMalformedActionPayloadsFailClosed() {
        SquadRegistrySavedData registry = registry();
        SquadMetadata metadata = registry.find(SQUAD).orElseThrow();

        EditResult none = apply(
                registry, metadata, OVERWORLD, EditAction.INSPECT, null, 20L);
        EditResult removeNone = apply(
                registry, metadata, OVERWORLD, EditAction.REMOVE_LAST, null, 20L);
        EditResult missingPoint = apply(
                registry, metadata, OVERWORLD, EditAction.ADD_WAYPOINT, null, 20L);

        assertAll(
                () -> assertEquals(EditStatus.NO_ROUTE, none.status()),
                () -> assertEquals(EditStatus.NO_ROUTE, removeNone.status()),
                () -> assertEquals(EditStatus.INVALID_WAYPOINT, missingPoint.status()),
                () -> assertEquals(0, registry.patrolRouteCount()));
    }

    private static EditResult apply(
            SquadRegistrySavedData registry,
            SquadMetadata metadata,
            ResourceLocation dimension,
            EditAction action,
            @Nullable BlockPos waypoint,
            long gameTime) {
        return SquadPatrolRouteEditor.apply(
                registry, metadata, dimension, action, waypoint, gameTime);
    }

    private static SquadRegistrySavedData registry() {
        SquadRegistrySavedData registry = new SquadRegistrySavedData();
        registry.register(SQUAD, "Route Test", null, 10L).orElseThrow();
        return registry;
    }
}
