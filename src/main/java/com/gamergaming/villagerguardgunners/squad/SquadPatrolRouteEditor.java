package com.gamergaming.villagerguardgunners.squad;

import com.gamergaming.villagerguardgunners.init.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** One-shot, server-authoritative editing for one ledger-bound patrol route. */
public final class SquadPatrolRouteEditor {
    public static final double MAXIMUM_EDIT_DISTANCE = 8.0D;

    private SquadPatrolRouteEditor() {
    }

    public static EditResult edit(
            Player player,
            ItemStack ledger,
            EditAction action,
            @Nullable BlockPos waypoint) {
        EditResult invalid = validateRequest(player, ledger, action, waypoint);
        if (invalid != null) {
            return invalid;
        }
        ServerLevel level = (ServerLevel) player.level();
        SquadLedgerBindingCodec.DecodeResult decoded =
                SquadLedgerBindingCodec.readFrom(ledger.getTag());
        if (decoded.status() == SquadLedgerBindingCodec.DecodeStatus.BLANK) {
            return EditResult.failed(EditStatus.BLANK_LEDGER);
        }
        if (decoded.status() == SquadLedgerBindingCodec.DecodeStatus.CORRUPT) {
            return EditResult.failed(EditStatus.CORRUPT_LEDGER);
        }
        if (decoded.status()
                == SquadLedgerBindingCodec.DecodeStatus.UNSUPPORTED_VERSION) {
            return EditResult.unsupported(decoded.storedVersion());
        }
        SquadLedgerBinding binding = decoded.binding();
        if (binding == null) {
            return EditResult.failed(EditStatus.CORRUPT_LEDGER);
        }

        SquadRegistrySavedData registry = SquadRegistrySavedData.get(level.getServer());
        if (!registry.enabled()) {
            return EditResult.failed(EditStatus.REGISTRY_DISABLED);
        }
        SquadMetadata metadata = registry.find(binding.squadId()).orElse(null);
        if (metadata == null) {
            return EditResult.failed(EditStatus.SQUAD_NOT_REGISTERED);
        }
        if (action == EditAction.ADD_WAYPOINT) {
            EditStatus waypointStatus = validateLiveWaypoint(
                    level, player, Objects.requireNonNull(waypoint, "waypoint"));
            if (waypointStatus != null) {
                return EditResult.named(waypointStatus, metadata.displayName());
            }
        }
        return apply(
                registry,
                metadata,
                level.dimension().location(),
                action,
                waypoint,
                level.getGameTime());
    }

    static EditResult apply(
            SquadRegistrySavedData registry,
            SquadMetadata metadata,
            ResourceLocation dimensionId,
            EditAction action,
            @Nullable BlockPos waypoint,
            long gameTime) {
        Objects.requireNonNull(registry, "registry");
        SquadMetadata squad = Objects.requireNonNull(metadata, "metadata");
        ResourceLocation dimension = Objects.requireNonNull(dimensionId, "dimensionId");
        EditAction requested = Objects.requireNonNull(action, "action");
        SquadPatrolRoute existing = registry.findPatrolRoute(squad.squadId()).orElse(null);

        if (requested == EditAction.INSPECT) {
            return existing == null
                    ? EditResult.route(EditStatus.NO_ROUTE, squad.displayName(), null)
                    : EditResult.route(
                            existing.executable()
                                    ? EditStatus.ROUTE_READY
                                    : EditStatus.ROUTE_DRAFT,
                            squad.displayName(),
                            existing);
        }
        if (requested == EditAction.ADD_WAYPOINT) {
            if (waypoint == null) {
                return EditResult.named(EditStatus.INVALID_WAYPOINT, squad.displayName());
            }
            if (existing != null && !existing.dimensionId().equals(dimension)) {
                return EditResult.route(
                        EditStatus.WRONG_DIMENSION, squad.displayName(), existing);
            }
            if (existing != null
                    && existing.waypoints().size() >= SquadPatrolRoute.MAXIMUM_WAYPOINTS) {
                return EditResult.route(EditStatus.ROUTE_FULL, squad.displayName(), existing);
            }
            List<BlockPos> replacement = existing == null
                    ? new ArrayList<>(1)
                    : new ArrayList<>(existing.waypoints());
            replacement.add(waypoint.immutable());
            return mapUpdate(
                    registry.replacePatrolRoute(
                            squad.squadId(), dimension, replacement, gameTime),
                    squad.displayName(),
                    EditAction.ADD_WAYPOINT);
        }
        if (existing == null) {
            return EditResult.route(EditStatus.NO_ROUTE, squad.displayName(), null);
        }
        List<BlockPos> replacement = new ArrayList<>(existing.waypoints());
        replacement.remove(replacement.size() - 1);
        return mapUpdate(
                registry.replacePatrolRoute(
                        squad.squadId(), existing.dimensionId(), replacement, gameTime),
                squad.displayName(),
                EditAction.REMOVE_LAST);
    }

    @Nullable
    private static EditResult validateRequest(
            Player player,
            ItemStack ledger,
            EditAction action,
            @Nullable BlockPos waypoint) {
        if (player == null
                || ledger == null
                || action == null
                || !(player.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread()
                || !player.isAlive()
                || player.isRemoved()
                || player.isSpectator()
                || ledger.isEmpty()
                || ledger.getCount() != 1
                || !ledger.is(ModItems.SQUAD_COMMAND_LEDGER.get())
                || action == EditAction.ADD_WAYPOINT && waypoint == null
                || action != EditAction.ADD_WAYPOINT && waypoint != null) {
            return EditResult.failed(EditStatus.INVALID_REQUEST);
        }
        return null;
    }

    @Nullable
    private static EditStatus validateLiveWaypoint(
            ServerLevel level,
            Player player,
            BlockPos waypoint) {
        if (player.distanceToSqr(
                        waypoint.getX() + 0.5D,
                        waypoint.getY(),
                        waypoint.getZ() + 0.5D)
                > MAXIMUM_EDIT_DISTANCE * MAXIMUM_EDIT_DISTANCE) {
            return EditStatus.OUT_OF_RANGE;
        }
        BlockPos floorPosition = waypoint.below();
        BlockPos headPosition = waypoint.above();
        if (!level.isInWorldBounds(waypoint)
                || !level.isInWorldBounds(floorPosition)
                || !level.isInWorldBounds(headPosition)
                || !level.getWorldBorder().isWithinBounds(waypoint)) {
            return EditStatus.OUTSIDE_WORLD;
        }
        if (!level.hasChunkAt(waypoint)
                || !level.hasChunkAt(floorPosition)
                || !level.hasChunkAt(headPosition)) {
            return EditStatus.DESTINATION_UNLOADED;
        }
        BlockState feet = level.getBlockState(waypoint);
        BlockState head = level.getBlockState(headPosition);
        BlockState floor = level.getBlockState(floorPosition);
        if (!feet.getCollisionShape(level, waypoint).isEmpty()
                || !head.getCollisionShape(level, headPosition).isEmpty()
                || !feet.getFluidState().isEmpty()
                || !head.getFluidState().isEmpty()
                || !floor.isFaceSturdy(level, floorPosition, Direction.UP)) {
            return EditStatus.INVALID_DESTINATION;
        }
        return null;
    }

    private static EditResult mapUpdate(
            SquadRegistrySavedData.RouteUpdateResult update,
            String squadName,
            EditAction action) {
        SquadPatrolRoute route = update.route();
        return switch (update.status()) {
            case UPDATED -> EditResult.route(
                    action == EditAction.ADD_WAYPOINT
                            ? EditStatus.WAYPOINT_ADDED
                            : EditStatus.WAYPOINT_REMOVED,
                    squadName,
                    route);
            case CLEARED -> EditResult.route(EditStatus.ROUTE_CLEARED, squadName, null);
            case WRONG_DIMENSION -> EditResult.route(
                    EditStatus.WRONG_DIMENSION, squadName, route);
            case INVALID_ROUTE -> EditResult.route(
                    EditStatus.INVALID_WAYPOINT, squadName, route);
            case REVISION_EXHAUSTED -> EditResult.route(
                    EditStatus.REVISION_EXHAUSTED, squadName, route);
            case DISABLED -> EditResult.named(EditStatus.REGISTRY_DISABLED, squadName);
            case SQUAD_NOT_FOUND -> EditResult.named(
                    EditStatus.SQUAD_NOT_REGISTERED, squadName);
            case UNCHANGED -> EditResult.route(EditStatus.UNCHANGED, squadName, route);
        };
    }

    public enum EditAction {
        ADD_WAYPOINT,
        REMOVE_LAST,
        INSPECT
    }

    public enum EditStatus {
        WAYPOINT_ADDED,
        WAYPOINT_REMOVED,
        ROUTE_CLEARED,
        ROUTE_DRAFT,
        ROUTE_READY,
        NO_ROUTE,
        UNCHANGED,
        BLANK_LEDGER,
        CORRUPT_LEDGER,
        UNSUPPORTED_VERSION,
        REGISTRY_DISABLED,
        SQUAD_NOT_REGISTERED,
        ROUTE_FULL,
        WRONG_DIMENSION,
        INVALID_WAYPOINT,
        INVALID_DESTINATION,
        DESTINATION_UNLOADED,
        OUTSIDE_WORLD,
        OUT_OF_RANGE,
        REVISION_EXHAUSTED,
        INVALID_REQUEST
    }

    public record EditResult(
            EditStatus status,
            String squadName,
            @Nullable ResourceLocation dimensionId,
            int waypointCount,
            long revision,
            boolean executable,
            int storedVersion) {
        public EditResult {
            Objects.requireNonNull(status, "status");
            squadName = Objects.requireNonNull(squadName, "squadName");
            if (waypointCount < 0 || revision < -1L) {
                throw new IllegalArgumentException("Patrol editor result has invalid counters");
            }
            if ((dimensionId == null) != (revision == -1L)
                    || (dimensionId == null) != (waypointCount == 0)
                    || executable && waypointCount < 2
                    || status == EditStatus.UNSUPPORTED_VERSION && storedVersion < 0
                    || status != EditStatus.UNSUPPORTED_VERSION && storedVersion != -1) {
                throw new IllegalArgumentException("Patrol editor result is contradictory");
            }
        }

        public boolean changed() {
            return status == EditStatus.WAYPOINT_ADDED
                    || status == EditStatus.WAYPOINT_REMOVED
                    || status == EditStatus.ROUTE_CLEARED;
        }

        public boolean informational() {
            return status == EditStatus.ROUTE_DRAFT
                    || status == EditStatus.ROUTE_READY
                    || status == EditStatus.NO_ROUTE;
        }

        private static EditResult route(
                EditStatus status,
                String squadName,
                @Nullable SquadPatrolRoute route) {
            return new EditResult(
                    status,
                    squadName,
                    route == null ? null : route.dimensionId(),
                    route == null ? 0 : route.waypoints().size(),
                    route == null ? -1L : route.revision(),
                    route != null && route.executable(),
                    -1);
        }

        private static EditResult named(EditStatus status, String squadName) {
            return route(status, squadName, null);
        }

        private static EditResult failed(EditStatus status) {
            return named(status, "");
        }

        private static EditResult unsupported(int storedVersion) {
            return new EditResult(
                    EditStatus.UNSUPPORTED_VERSION,
                    "",
                    null,
                    0,
                    -1L,
                    false,
                    storedVersion);
        }
    }
}
