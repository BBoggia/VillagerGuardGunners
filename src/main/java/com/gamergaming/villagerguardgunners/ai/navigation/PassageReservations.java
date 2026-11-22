package com.gamergaming.villagerguardgunners.ai.navigation;

import java.util.*;

/** Bounded FIFO admission; occupants renew their lease until they leave the passage. */
public final class PassageReservations<K> {
    public static final int MAX_PASSAGES = 128, MAX_WAITERS = 16, LEASE_TICKS = 30;
    private final Map<K, Passage> passages = new HashMap<>();
    private long lastExpiryTick = Long.MIN_VALUE;
    public boolean request(K key, UUID guard, long now) {
        expire(now);
        Passage passage = passages.get(key);
        if (passage == null) {
            if (passages.size() >= MAX_PASSAGES) return false;
            passage = new Passage(); passages.put(key, passage);
        }
        if (guard.equals(passage.owner)) { passage.until = now + LEASE_TICKS; return true; }
        if (passage.waiting.size() < MAX_WAITERS || passage.waiting.containsKey(guard)) passage.waiting.put(guard, now);
        if (passage.owner == null && !passage.waiting.isEmpty() && passage.waiting.keySet().iterator().next().equals(guard)) {
            passage.waiting.remove(guard); passage.owner = guard; passage.until = now + LEASE_TICKS; return true;
        }
        return false;
    }
    public void release(UUID guard) {
        passages.values().forEach(p -> { p.waiting.remove(guard); if (guard.equals(p.owner)) p.owner = null; });
        passages.values().removeIf(p -> p.owner == null && p.waiting.isEmpty());
    }
    public void expire(long now) {
        // All additions and renewals expire in the future; same-tick requests
        // cannot create more expired entries. Releases still take effect immediately.
        if (now == lastExpiryTick) return;
        lastExpiryTick = now;
        passages.values().forEach(p -> {
            if (p.until <= now) p.owner = null;
            p.waiting.values().removeIf(t -> now - t >= LEASE_TICKS);
        });
        passages.values().removeIf(p -> p.owner == null && p.waiting.isEmpty());
    }
    public int size() { return passages.size(); }
    private static final class Passage {
        UUID owner; long until;
        final LinkedHashMap<UUID, Long> waiting = new LinkedHashMap<>();
    }
}
