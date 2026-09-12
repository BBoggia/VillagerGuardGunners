package com.gamergaming.villagerguardgunners.defense;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Allocation-stable per-network event throttle and confirmed-threat lease.
 * Entries use bounded access-order eviction; eviction can only reduce event
 * suppression and never blocks a legitimate state transition.
 */
public final class VillageDefenseSignalGate {
    public static final int MAXIMUM_TRACKED_NETWORKS = 4_096;

    private final int maximumTrackedNetworks;
    private final LinkedHashMap<UUID, NetworkWindow> windows =
            new LinkedHashMap<>(16, 0.75F, true);
    private long evictions;

    public VillageDefenseSignalGate() {
        this(MAXIMUM_TRACKED_NETWORKS);
    }

    VillageDefenseSignalGate(int maximumTrackedNetworks) {
        if (maximumTrackedNetworks <= 0
                || maximumTrackedNetworks > MAXIMUM_TRACKED_NETWORKS) {
            throw new IllegalArgumentException(
                    "Defense signal gate capacity exceeds its hard bound");
        }
        this.maximumTrackedNetworks = maximumTrackedNetworks;
    }

    public boolean shouldRoute(
            UUID networkId,
            VillageDefenseSignal signal,
            long gameTime,
            long cooldownTicks) {
        if (gameTime < 0L || cooldownTicks < 0L) {
            throw new IllegalArgumentException(
                    "Defense signal times cannot be negative");
        }
        NetworkWindow window = window(Objects.requireNonNull(networkId, "networkId"));
        int index = Objects.requireNonNull(signal, "signal").ordinal();
        long last = window.lastSignals[index];
        if (last >= 0L && gameTime >= last && gameTime - last < cooldownTicks) {
            return false;
        }
        window.lastSignals[index] = gameTime;
        return true;
    }

    public void confirmThreat(
            UUID networkId,
            long gameTime,
            long leaseTicks) {
        if (gameTime < 0L || leaseTicks <= 0L) {
            throw new IllegalArgumentException(
                    "Confirmed-threat lease is outside its bounds");
        }
        long until = gameTime >= Long.MAX_VALUE - leaseTicks
                ? Long.MAX_VALUE
                : gameTime + leaseTicks;
        NetworkWindow window = window(Objects.requireNonNull(networkId, "networkId"));
        window.confirmedThreatUntil = Math.max(
                window.confirmedThreatUntil, until);
    }

    public boolean confirmedThreatActive(UUID networkId, long gameTime) {
        if (gameTime < 0L) {
            return false;
        }
        NetworkWindow window = windows.get(
                Objects.requireNonNull(networkId, "networkId"));
        return window != null && window.confirmedThreatUntil > gameTime;
    }

    /**
     * Resolves retained pre-merge IDs before checking their threat leases.
     * This bounded pass is used only by deadline and raid-end reviews, never
     * by ordinary ticks or entity events.
     */
    public boolean confirmedThreatActiveResolved(
            UUID canonicalNetworkId,
            Function<UUID, UUID> resolver,
            long gameTime) {
        if (gameTime < 0L) {
            return false;
        }
        UUID canonical = Objects.requireNonNull(
                canonicalNetworkId, "canonicalNetworkId");
        Function<UUID, UUID> normalizedResolver = Objects.requireNonNull(
                resolver, "resolver");
        for (Map.Entry<UUID, NetworkWindow> entry : windows.entrySet()) {
            if (entry.getValue().confirmedThreatUntil > gameTime
                    && canonical.equals(normalizedResolver.apply(entry.getKey()))) {
                return true;
            }
        }
        return false;
    }

    public int trackedNetworks() {
        return windows.size();
    }

    public long evictions() {
        return evictions;
    }

    private NetworkWindow window(UUID networkId) {
        NetworkWindow existing = windows.get(networkId);
        if (existing != null) {
            return existing;
        }
        if (windows.size() >= maximumTrackedNetworks) {
            Iterator<Map.Entry<UUID, NetworkWindow>> iterator =
                    windows.entrySet().iterator();
            iterator.next();
            iterator.remove();
            evictions++;
        }
        NetworkWindow created = new NetworkWindow();
        windows.put(networkId, created);
        return created;
    }

    private static final class NetworkWindow {
        private final long[] lastSignals = new long[VillageDefenseSignal.values().length];
        private long confirmedThreatUntil;

        private NetworkWindow() {
            Arrays.fill(lastSignals, -1L);
        }
    }
}
