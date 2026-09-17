package com.gamergaming.villagerguardgunners.quartermaster.service;

import com.gamergaming.villagerguardgunners.api.GuardEquipmentServiceAccess;
import com.gamergaming.villagerguardgunners.api.GuardRoleAccess;
import com.gamergaming.villagerguardgunners.orders.GuardOrderPermissions;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import tallestegg.guardvillagers.entities.Guard;

/** Native menu buttons and commands share the same explicit authorization boundary. */
public final class ServiceControls {
    public static final int POLICY_BUTTON = 300, PROFILE_BUTTON = 301, REPLACE_BUTTON = 302;
    public static boolean change(Player player, Guard guard, int button) {
        if (player.level().isClientSide || player.level() != guard.level() || !player.isAlive() || player.isSpectator()
                || ServiceTransactions.mutating(guard) || !GuardOrderPermissions.mayManage(player, guard)
                || player.distanceToSqr(guard) >= 64 || !guard.isAlive() || guard.isRemoved()) return false;
        var state = GuardEquipmentServiceAccess.get(guard); if (!state.readable()) return false;
        var profiles = EquipmentProfiles.snapshot();
        ServiceSettings requested = next(state.settings(), button, profiles, GuardRoleAccess.getRoleId(guard));
        return requested != null && state.update(requested);
    }
    /** Computes a button's explicit request without mutating an entity. */
    static ServiceSettings next(ServiceSettings current, int button, java.util.Map<ResourceLocation, EquipmentProfile> profiles, ResourceLocation role) {
        List<ResourceLocation> ids = profiles.keySet().stream().sorted().toList();
        var policy = current.policy(); var profile = current.profile(); boolean replace = current.replaceExisting();
        if (button == POLICY_BUTTON) {
            var values = GuardServiceState.Policy.values(); policy = values[(policy.ordinal() + 1) % values.length];
            if (policy == GuardServiceState.Policy.PROFILE && profile == null && !ids.isEmpty()) {
                var effectiveRole = role == null ? ResourceLocation.parse("villagerguardgunners:generalist") : role;
                profile = ids.stream().filter(id -> profiles.get(id).roles().contains(effectiveRole.toString()))
                        .findFirst().orElse(ids.get(0));
            }
        } else if (button == PROFILE_BUTTON && !ids.isEmpty()) profile = ids.get((ids.indexOf(profile) + 1) % ids.size());
        else if (button == REPLACE_BUTTON) replace = !replace;
        else return null;
        return new ServiceSettings(policy, profile, replace, current.assignment());
    }
    /** Future menus pass a server-captured station context, never just a position. */
    public static ServiceManagementPermissions.Result applyAtStation(Player player,
            com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity station,
            ServiceManagementPermissions.Context context, Guard guard, ServiceSettings requested) {
        var result = ServiceManagementPermissions.change(player, station, context, guard, requested);
        if (result != ServiceManagementPermissions.Result.ALLOWED) return result;
        var assignment = requested.assignment() == null ? null : new ServiceStationAssignment(station.stationId(),
                station.getLevel().dimension().location(), station.getBlockPos());
        GuardEquipmentServiceAccess.get(guard).update(new ServiceSettings(requested.policy(), requested.profile(),
                requested.replaceExisting(), assignment));
        return ServiceManagementPermissions.Result.ALLOWED;
    }
    /** A batch is validated completely before any settings change, on the server thread. */
    public record BatchEntry(Guard guard, ServiceSettings expected, long revision, int squadRevision, ServiceSettings requested) {}
    public static boolean applyBatch(Player player,
            com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity station,
            ServiceManagementPermissions.Context context, List<BatchEntry> entries) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !level.getServer().isSameThread() || entries.isEmpty() || entries.size()>64) return false;
        var unique=new java.util.HashSet<java.util.UUID>();
        for (var entry:entries) {
            var guard=entry.guard(); if(guard==null||!unique.add(guard.getUUID()))return false;
            var state=GuardEquipmentServiceAccess.get(guard);
            if(state.settingsRevision()!=entry.revision()||!state.settings().equals(entry.expected())
                    ||com.gamergaming.villagerguardgunners.api.GuardSquadAccess.getRevision(guard)!=entry.squadRevision()
                    ||ServiceManagementPermissions.change(player,station,context,guard,entry.requested())!=ServiceManagementPermissions.Result.ALLOWED)return false;
        }
        // No native inventory/name callbacks in this loop. Ordinary updates use this same core.
        for(var entry:entries)GuardEquipmentServiceAccess.get(entry.guard()).update(entry.requested(),false);
        // Roster names are advisory metadata; refreshing them must not interrupt the batch.
        for(var entry:entries) {
            try { ServiceRoster.get(level).observe(entry.guard()); }
            catch(RuntimeException ignored) { /* The normal service tick retries roster observation. */ }
        }
        return true;
    }
    /** Forget only an unavailable roster hint; an eventual live guard is authoritative. */
    public static boolean forgetUnavailable(Player player,
            com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity station,
            ServiceManagementPermissions.Context context, java.util.UUID guardId) {
        if (!canForgetUnavailable(player,station,context,guardId)) return false;
        ServiceRoster.get((net.minecraft.server.level.ServerLevel)player.level()).forget(guardId);
        return true;
    }
    public static boolean canForgetUnavailable(Player player,
            com.gamergaming.villagerguardgunners.quartermaster.QuartermasterBlockEntity station,
            ServiceManagementPermissions.Context context, java.util.UUID guardId) {
        if (guardId == null) return false;
        if (!ServiceManagementPermissions.station(player, station, context).manage()) return false;
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        for (var dimension : level.getServer().getAllLevels()) if (dimension.getEntity(guardId) != null) return false;
        var roster = ServiceRoster.get(level); var entry = roster.find(guardId);
        if (entry == null || !entry.assignment().stationId().equals(station.stationId())
                || !entry.assignment().dimension().equals(level.dimension().location())) return false;
        return true;
    }
}
