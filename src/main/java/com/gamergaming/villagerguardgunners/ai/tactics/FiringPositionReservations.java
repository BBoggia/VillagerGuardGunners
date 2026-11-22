package com.gamergaming.villagerguardgunners.ai.tactics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import tallestegg.guardvillagers.entities.Guard;

/** Server-thread-only, value-only leases. Worlds are weak keys; each world retains at most 128. */
public final class FiringPositionReservations {
    public static final int CAPACITY = 128;
    public static final int LIFETIME = 40;
    private record Lease(Vec3 position, long until) { }
    private static final Map<ServerLevel, Map<UUID, Lease>> WORLDS = new WeakHashMap<>();
    private static Map<UUID, Lease> leases(Guard guard) {
        var level = (ServerLevel) guard.level();
        var leases = WORLDS.computeIfAbsent(level, ignored -> new HashMap<>());
        long now = level.getGameTime();
        leases.entrySet().removeIf(entry -> now >= entry.getValue().until()
                || entry.getValue().until() > now + LIFETIME
                || !(level.getEntity(entry.getKey()) instanceof Guard owner) || !owner.isAlive() || owner.isRemoved());
        return leases;
    }
    public static java.util.List<Vec3> positions(Guard guard) {
        if (!(guard.level() instanceof ServerLevel)) return java.util.List.of();
        return leases(guard).entrySet().stream().filter(e -> !e.getKey().equals(guard.getUUID()))
                .map(e -> e.getValue().position()).toList();
    }
    public static boolean available(Guard guard, Vec3 point) {
        if (!(guard.level() instanceof ServerLevel) || point == null
                || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return false;
        for (var entry : leases(guard).entrySet()) {
            if (!entry.getKey().equals(guard.getUUID()) && entry.getValue().position().distanceToSqr(point) < 4) return false;
        }
        return true;
    }
    public static boolean reserve(Guard guard, Vec3 point) {
        if (!available(guard, point)) return false;
        var leases = leases(guard);
        if (leases.size() >= CAPACITY && !leases.containsKey(guard.getUUID())) return false;
        leases.put(guard.getUUID(), new Lease(point, guard.level().getGameTime() + LIFETIME));
        return true;
    }
    public static void release(Guard guard) {
        if (guard.level() instanceof ServerLevel level) {
            var leases = WORLDS.get(level);
            if (leases != null) leases.remove(guard.getUUID());
        }
    }
    private FiringPositionReservations() { }
}
