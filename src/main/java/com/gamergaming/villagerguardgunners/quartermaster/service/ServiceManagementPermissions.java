package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import tallestegg.guardvillagers.entities.Guard;

/** Pure authorization checks; never claims a job site or verifies/mutates a journal. */
public final class ServiceManagementPermissions {
    public enum Result { ALLOWED, STATION_UNAVAILABLE, STALE_STATION, STATION_DENIED,
        GUARD_UNAVAILABLE, GUARD_DENIED, GUARD_TOO_FAR, RECOVERY_REQUIRED, BUSY,
        SERVICE_DENIED, WRONG_ASSIGNMENT, PROFILE_MISSING, ASSIGNMENT_UNRESOLVED }
    public record Context(UUID station, UUID placement, ResourceLocation dimension, BlockPos position) {
        public Context {
            java.util.Objects.requireNonNull(station); java.util.Objects.requireNonNull(placement);
            java.util.Objects.requireNonNull(dimension); position = java.util.Objects.requireNonNull(position).immutable();
        }
        public static Context of(QuartermasterBlockEntity station) {
            return new Context(station.stationId(), station.dropId(), station.getLevel().dimension().location(), station.getBlockPos());
        }
    }
    public record Capabilities(boolean viewStock, boolean manage, Result reason) {}
    public static Capabilities station(Player player, QuartermasterBlockEntity station, Context expected) {
        if (player == null || station == null || expected == null || !(station.getLevel() instanceof ServerLevel)
                || player.level() != station.getLevel() || !player.isAlive() || player.isRemoved() || player.isSpectator()
                || station.isRemoved()) return new Capabilities(false, false, Result.STATION_UNAVAILABLE);
        if (!expected.station().equals(station.stationId()) || !expected.placement().equals(station.dropId())
                || !expected.dimension().equals(station.getLevel().dimension().location())
                || !expected.position().equals(station.getBlockPos())) return new Capabilities(false, false, Result.STALE_STATION);
        if (!station.serviceReadable()) return new Capabilities(false, false, Result.RECOVERY_REQUIRED);
        if (ServiceTransactions.mutating(station)) return new Capabilities(false, false, Result.BUSY);
        boolean view = station.canInteract(player);
        boolean manage = view && station.canManage(player);
        return new Capabilities(view, manage, manage ? Result.ALLOWED : Result.STATION_DENIED);
    }
    public static Result change(Player player, QuartermasterBlockEntity station, Context expected,
            Guard guard, ServiceSettings requested) {
        var access = station(player, station, expected);
        if (!access.manage()) return access.reason();
        if (guard == null || requested == null || !guard.isAlive() || guard.isRemoved() || guard.level() != player.level()
                || ((ServerLevel) player.level()).getEntity(guard.getUUID()) != guard) return Result.GUARD_UNAVAILABLE;
        if (!GuardOrderPermissions.mayManage(player, guard)) return Result.GUARD_DENIED;
        if (guard.blockPosition().distSqr(station.getBlockPos()) > 48 * 48) return Result.GUARD_TOO_FAR;
        if (!GuardEquipmentServiceAccess.get(guard).readable()) return Result.RECOVERY_REQUIRED;
        if (ServiceTransactions.mutating(guard)) return Result.BUSY;
        if (requested.policy() == GuardServiceState.Policy.PROFILE
                && (requested.profile() == null || !EquipmentProfiles.snapshot().containsKey(requested.profile()))) return Result.PROFILE_MISSING;
        var assigned = requested.assignment();
        // Suspending service does not grant a new destination or consume its supplies.
        if (requested.policy() == GuardServiceState.Policy.MANUAL
                && (assigned == null || assigned.equals(GuardEquipmentServiceAccess.get(guard).assignment()))) return Result.ALLOWED;
        if (assigned != null) {
            if (!assigned.stationId().equals(station.stationId())
                    || !assigned.dimension().equals(player.level().dimension().location())) return Result.WRONG_ASSIGNMENT;
            if (QuartermasterServiceRuntime.assignedStation((ServerLevel) player.level(), assigned, guard.blockPosition()).station() != station)
                return Result.ASSIGNMENT_UNRESOLVED;
        }
        // Clearing a revoked assignment or disabling service must remain possible.
        if ((assigned != null || requested.policy() != GuardServiceState.Policy.MANUAL) && !station.mayService(guard)) return Result.SERVICE_DENIED;
        return Result.ALLOWED;
    }
}
