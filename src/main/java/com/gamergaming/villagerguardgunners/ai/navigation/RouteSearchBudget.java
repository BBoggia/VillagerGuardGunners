package com.gamergaming.villagerguardgunners.ai.navigation;

import java.util.*;

/** Reserve one slot for the oldest request; other slots remain usable while that guard is absent. */
final class RouteSearchBudget {
    static final int MAX_WAITERS = 256, REQUEST_TICKS = 100;
    private final int limit;
    private final LinkedHashMap<UUID, Long> waiting = new LinkedHashMap<>();
    private long tick = Long.MIN_VALUE;
    private int used;

    RouteSearchBudget(int limit) {
        if (limit < 1 || limit > MAX_WAITERS) throw new IllegalArgumentException("Invalid search limit");
        this.limit = limit;
    }

    boolean request(UUID guard, long now) {
        if (tick != now) {
            tick = now;
            used = 0;
            waiting.values().removeIf(at -> now - at >= REQUEST_TICKS);
        }
        if (waiting.size() < MAX_WAITERS || waiting.containsKey(guard)) waiting.put(guard, now);
        boolean oldest = !waiting.isEmpty() && waiting.keySet().iterator().next().equals(guard);
        // Retain priority through different owner retry intervals. A one-tick grant
        // would be missed by a 20-tick caller while fast callers consumed its next turn.
        if (used < limit && (oldest || used < limit - 1)) {
            waiting.remove(guard);
            used++;
            return true;
        }
        return false;
    }

    void cancel(UUID guard) { waiting.remove(guard); }
}
