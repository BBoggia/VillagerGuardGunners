package com.gamergaming.villagerguardgunners.village.coverage;

import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Transient, server-thread lease book shared by village policies in one dimension.
 * Fixed-post adapters can claim the same guard index before coverage is scheduled.
 * Live fixed-post publication preempts coverage and mirrors claims into this index.
 */
public final class VillageCoverageReservations {
    public static final int MAX_LEASES = 4_096;
    public static final int MAX_EXPIRATIONS_PER_CLAIM = 32;
    private final Map<UUID, Lease> guards = new HashMap<>();
    private final Map<Target, Lease> targets = new HashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> villages = new HashMap<>();
    private final TreeSet<Lease> deadlines = new TreeSet<>(Comparator.comparingLong(Lease::expiresAt)
            .thenComparing(Lease::token));

    public @Nullable Lease forGuard(UUID guardId) { return guards.get(guardId); }
    public int size() { return guards.size(); }

    public List<Lease> forVillage(UUID villageId) {
        var ids = villages.get(villageId);
        return ids == null ? List.of() : ids.stream().map(guards::get)
                .sorted((left, right) -> left.guardId().compareTo(right.guardId())).toList();
    }

    /** An existing live claim is never replaced, including a claim by another work kind. */
    public @Nullable Lease claim(UUID villageId, UUID guardId, UUID areaId,
            UUID destinationId, Kind kind, long now, long duration) {
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(guardId, "guardId");
        Objects.requireNonNull(areaId, "areaId");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(kind, "kind");
        if (now < 0 || duration < 1 || duration > 24_000 || now > Long.MAX_VALUE - duration) {
            throw new IllegalArgumentException("Invalid lease clock or duration");
        }
        Target target = new Target(villageId, areaId, kind);
        for (int expired = 0; expired < MAX_EXPIRATIONS_PER_CLAIM
                && !deadlines.isEmpty() && deadlines.first().expiresAt() <= now; expired++) {
            release(deadlines.first());
        }
        expire(guards.get(guardId), now);
        expire(targets.get(target), now);
        if (guards.containsKey(guardId) || targets.containsKey(target) || guards.size() == MAX_LEASES) {
            return null;
        }
        Lease lease = new Lease(UUID.randomUUID(), villageId, guardId, areaId,
                destinationId, kind, now, now + duration);
        guards.put(guardId, lease);
        targets.put(target, lease);
        deadlines.add(lease);
        villages.computeIfAbsent(villageId, key -> new LinkedHashSet<>()).add(guardId);
        return lease;
    }

    /** Token matching prevents an old callback from releasing a replacement lease. */
    public boolean release(Lease lease) {
        Objects.requireNonNull(lease, "lease");
        if (!guards.remove(lease.guardId(), lease)) { return false; }
        targets.remove(new Target(lease.villageId(), lease.areaId(), lease.kind()), lease);
        deadlines.remove(lease);
        var ids = villages.get(lease.villageId());
        ids.remove(lease.guardId());
        if (ids.isEmpty()) { villages.remove(lease.villageId()); }
        return true;
    }

    /** Called by the owning village's bounded review/unload, not a historical global scan. */
    public void releaseVillage(UUID villageId) { forVillage(villageId).forEach(this::release); }

    public void clear() {
        guards.clear();
        targets.clear();
        villages.clear();
        deadlines.clear();
    }

    private void expire(@Nullable Lease lease, long now) {
        if (lease != null && lease.expiresAt() <= now) { release(lease); }
    }

    public enum Kind { COVERAGE, FIXED_POST }
    private record Target(UUID villageId, UUID areaId, Kind kind) { }
    public record Lease(UUID token, UUID villageId, UUID guardId, UUID areaId,
            UUID destinationId, Kind kind, long assignedAt, long expiresAt) { }
}
