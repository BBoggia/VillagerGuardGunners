package com.gamergaming.villagerguardgunners.defense;

import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetwork;
import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import com.gamergaming.villagerguardgunners.mixin.accessor.RaidsAccessor;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * Server-authoritative routing, throttling, raid association, and diagnostics
 * for defense-state events. Every position lookup uses the persisted building
 * chunk index; the runtime performs no entity search or chunk access.
 */
public final class VillageDefenseEventRuntime {
    public static final double EVENT_LINK_RADIUS =
            VillageDefenseNetworkSavedData.DEFAULT_LINK_RADIUS;
    public static final double RAID_LINK_RADIUS =
            VillageDefenseNetworkSavedData.ABSOLUTE_MAXIMUM_LINK_RADIUS;
    public static final long BELL_COOLDOWN_TICKS = 10L;
    public static final long TARGET_COOLDOWN_TICKS = 20L;
    public static final long DAMAGE_COOLDOWN_TICKS = 10L;
    public static final long DEATH_COOLDOWN_TICKS = 10L;
    public static final int MAXIMUM_ACTIVE_RAID_OBSERVATIONS = 4_096;

    private static final Map<MinecraftServer, ServerRuntime> SERVERS =
            new IdentityHashMap<>();

    private VillageDefenseEventRuntime() {
    }

    public static synchronized RouteResult signalAt(
            ServerLevel level,
            BlockPos position,
            VillageDefenseSignal signal,
            VillageDefenseEventSource source) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(source, "source");
        DimensionRuntime runtime = runtime(level, true);
        if (!level.getServer().isSameThread()) {
            runtime.rejectedEvents++;
            return runtime.record(
                    level.getGameTime(),
                    source,
                    signal,
                    null,
                    RouteOutcome.REJECTED);
        }
        VillageDefenseNetwork network = VillageDefenseNetworkSavedData.get(level)
                .findNearest(position, EVENT_LINK_RADIUS);
        if (network == null) {
            runtime.unmatchedEvents++;
            return runtime.record(
                    level.getGameTime(),
                    source,
                    signal,
                    null,
                    RouteOutcome.NO_NETWORK);
        }
        return routeResolved(level, runtime, network.networkId(), signal, source);
    }

    public static synchronized void observeRaid(Raid raid) {
        Objects.requireNonNull(raid, "raid");
        if (!(raid.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) {
            return;
        }
        if (raid.isStopped() || raid.isOver()) {
            observeRaidEnded(level, raid.getId(), raid.getCenter(),
                    VillageDefenseEventSource.RAID_ENDED);
        } else {
            observeRaidStarted(level, raid.getId(), raid.getCenter(),
                    VillageDefenseEventSource.RAID_STARTED);
        }
    }

    public static synchronized VillageDefenseReviewFacts reviewFacts(
            ServerLevel level,
            VillageDefenseNetworkSavedData data,
            UUID networkId,
            long gameTime) {
        DimensionRuntime runtime = runtime(level, false);
        if (runtime == null) {
            return VillageDefenseReviewFacts.QUIET;
        }
        UUID canonical = data.resolveNetworkId(networkId);
        if (canonical == null) {
            return VillageDefenseReviewFacts.QUIET;
        }
        boolean raidActive = hasActiveRaid(runtime, data, canonical);
        boolean threatActive = confirmedThreatActive(
                runtime, data, canonical, gameTime);
        return new VillageDefenseReviewFacts(raidActive, threatActive);
    }

    public static synchronized DiagnosticsSnapshot diagnostics(ServerLevel level) {
        DimensionRuntime runtime = runtime(level, false);
        if (runtime == null) {
            return DiagnosticsSnapshot.EMPTY;
        }
        return new DiagnosticsSnapshot(
                Map.copyOf(runtime.acceptedBySignal),
                runtime.routedEvents,
                runtime.throttledEvents,
                runtime.unmatchedEvents,
                runtime.rejectedEvents,
                runtime.raidStarts,
                runtime.raidEnds,
                runtime.restartReconciliations,
                runtime.activeRaids.size(),
                runtime.raidObservationOverflows,
                runtime.raidTrackingOverflowed,
                runtime.signalGate.trackedNetworks(),
                runtime.signalGate.evictions(),
                runtime.lastEvent);
    }

    public static synchronized void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        SERVERS.remove(server);
        for (ServerLevel level : server.getAllLevels()) {
            VillageDefenseNetworkSavedData data = VillageDefenseNetworkSavedData.get(level);
            Set<UUID> activeNetworks = new HashSet<>();
            Map<Integer, Raid> raids = ((RaidsAccessor) level.getRaids())
                    .villagerGuardGunners$getRaidMap();
            for (Raid raid : raids.values()) {
                if (raid.isStopped() || raid.isOver()) {
                    continue;
                }
                RouteResult result = observeRaidStarted(
                        level,
                        raid.getId(),
                        raid.getCenter(),
                        VillageDefenseEventSource.RAID_STARTED);
                if (result.networkId() != null) {
                    activeNetworks.add(result.networkId());
                }
            }
            for (VillageDefenseNetwork network : data.snapshot()) {
                if (network.defenseStatus().state() != VillageDefenseState.SIEGE
                        || activeNetworks.contains(network.networkId())) {
                    continue;
                }
                DimensionRuntime runtime = runtime(level, true);
                boolean confirmedThreat = confirmedThreatActive(
                        runtime,
                        data,
                        network.networkId(),
                        level.getGameTime());
                VillageDefenseNetworkSavedData.StateUpdateResult result =
                        VillageDefenseStateService.raidEnded(
                                level,
                                network.networkId(),
                                confirmedThreat);
                runtime.restartReconciliations++;
                runtime.record(
                        level.getGameTime(),
                        VillageDefenseEventSource.RESTART_RECONCILIATION,
                        VillageDefenseSignal.RAID_ACTIVE,
                        network.networkId(),
                        result.status().successful()
                                ? RouteOutcome.APPLIED
                                : RouteOutcome.REJECTED);
            }
        }
    }

    public static synchronized void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ServerRuntime serverRuntime = SERVERS.get(level.getServer());
        if (serverRuntime == null) {
            return;
        }
        serverRuntime.dimensions.remove(level.dimension());
        if (serverRuntime.dimensions.isEmpty()) {
            SERVERS.remove(level.getServer());
        }
    }

    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        SERVERS.remove(event.getServer());
    }

    private static RouteResult observeRaidStarted(
            ServerLevel level,
            int raidId,
            BlockPos center,
            VillageDefenseEventSource source) {
        DimensionRuntime runtime = runtime(level, true);
        RaidObservation existing = runtime.activeRaids.get(raidId);
        if (existing != null) {
            return new RouteResult(
                    RouteOutcome.THROTTLED,
                    existing.networkId(),
                    VillageDefenseSignal.RAID_ACTIVE);
        }
        VillageDefenseNetwork network = VillageDefenseNetworkSavedData.get(level)
                .findNearest(center, RAID_LINK_RADIUS);
        UUID networkId = network == null ? null : network.networkId();
        if (runtime.activeRaids.size() < MAXIMUM_ACTIVE_RAID_OBSERVATIONS) {
            runtime.activeRaids.put(raidId, new RaidObservation(networkId));
        } else {
            // Losing an association could make a Siege end while an untracked
            // raid is still active. Overflow therefore fails conservatively
            // until a restart rebuilds the bounded set from vanilla state.
            runtime.raidObservationOverflows++;
            runtime.raidTrackingOverflowed = true;
        }
        runtime.raidStarts++;
        if (networkId == null) {
            runtime.unmatchedEvents++;
            return runtime.record(
                    level.getGameTime(),
                    source,
                    VillageDefenseSignal.RAID_ACTIVE,
                    null,
                    RouteOutcome.NO_NETWORK);
        }
        return routeResolved(
                level,
                runtime,
                networkId,
                VillageDefenseSignal.RAID_ACTIVE,
                source);
    }

    private static RouteResult observeRaidEnded(
            ServerLevel level,
            int raidId,
            BlockPos center,
            VillageDefenseEventSource source) {
        DimensionRuntime runtime = runtime(level, true);
        RaidObservation observation = runtime.activeRaids.remove(raidId);
        VillageDefenseNetworkSavedData data = VillageDefenseNetworkSavedData.get(level);
        UUID networkId = observation == null ? null : observation.networkId();
        if (networkId == null) {
            VillageDefenseNetwork nearest = data.findNearest(center, RAID_LINK_RADIUS);
            networkId = nearest == null ? null : nearest.networkId();
        }
        runtime.raidEnds++;
        if (networkId == null) {
            runtime.unmatchedEvents++;
            return runtime.record(
                    level.getGameTime(),
                    source,
                    VillageDefenseSignal.RAID_ACTIVE,
                    null,
                    RouteOutcome.NO_NETWORK);
        }
        UUID canonical = data.resolveNetworkId(networkId);
        if (canonical == null) {
            runtime.rejectedEvents++;
            return runtime.record(
                    level.getGameTime(),
                    source,
                    VillageDefenseSignal.RAID_ACTIVE,
                    networkId,
                    RouteOutcome.REJECTED);
        }
        boolean anotherRaidActive = hasActiveRaid(runtime, data, canonical);
        if (anotherRaidActive) {
            return runtime.record(
                    level.getGameTime(),
                    source,
                    VillageDefenseSignal.RAID_ACTIVE,
                    canonical,
                    RouteOutcome.RAID_REMAINS_ACTIVE);
        }
        boolean confirmedThreat = confirmedThreatActive(
                runtime, data, canonical, level.getGameTime());
        VillageDefenseNetworkSavedData.StateUpdateResult result =
                VillageDefenseStateService.raidEnded(
                        level, canonical, confirmedThreat);
        if (result.status().successful()) {
            runtime.routedEvents++;
        } else {
            runtime.rejectedEvents++;
        }
        return runtime.record(
                level.getGameTime(),
                source,
                VillageDefenseSignal.RAID_ACTIVE,
                canonical,
                result.status().successful()
                        ? RouteOutcome.APPLIED
                        : RouteOutcome.REJECTED);
    }

    private static RouteResult routeResolved(
            ServerLevel level,
            DimensionRuntime runtime,
            UUID networkId,
            VillageDefenseSignal signal,
            VillageDefenseEventSource source) {
        long gameTime = level.getGameTime();
        if (!runtime.signalGate.shouldRoute(
                networkId,
                signal,
                gameTime,
                cooldown(signal))) {
            runtime.throttledEvents++;
            return runtime.record(
                    gameTime,
                    source,
                    signal,
                    networkId,
                    RouteOutcome.THROTTLED);
        }
        VillageDefenseNetworkSavedData.StateUpdateResult result =
                VillageDefenseStateService.signal(level, networkId, signal);
        if (result.status().successful()) {
            if (confirmsThreat(signal)) {
                VillageDefenseTimings timings = VillageDefenseRuntimePolicy.timings();
                runtime.signalGate.confirmThreat(
                        result.networkId(),
                        gameTime,
                        timings.alertTicks());
            }
            runtime.routedEvents++;
            runtime.acceptedBySignal.merge(signal, 1L, Long::sum);
        } else {
            runtime.rejectedEvents++;
        }
        return runtime.record(
                gameTime,
                source,
                signal,
                result.networkId() == null ? networkId : result.networkId(),
                result.status().successful()
                        ? RouteOutcome.APPLIED
                        : RouteOutcome.REJECTED);
    }

    private static boolean confirmsThreat(VillageDefenseSignal signal) {
        return signal == VillageDefenseSignal.HOSTILE_CONFIRMED
                || signal == VillageDefenseSignal.FRIENDLY_DAMAGED
                || signal == VillageDefenseSignal.FRIENDLY_KILLED;
    }

    private static boolean hasActiveRaid(
            DimensionRuntime runtime,
            VillageDefenseNetworkSavedData data,
            UUID canonicalNetworkId) {
        if (runtime.raidTrackingOverflowed) {
            return true;
        }
        for (RaidObservation observation : runtime.activeRaids.values()) {
            if (observation.networkId() == null) {
                continue;
            }
            UUID resolved = data.resolveNetworkId(observation.networkId());
            if (canonicalNetworkId.equals(resolved)) {
                return true;
            }
        }
        return false;
    }

    private static boolean confirmedThreatActive(
            DimensionRuntime runtime,
            VillageDefenseNetworkSavedData data,
            UUID canonicalNetworkId,
            long gameTime) {
        return runtime.signalGate.confirmedThreatActiveResolved(
                canonicalNetworkId,
                data::resolveNetworkId,
                gameTime);
    }

    private static long cooldown(VillageDefenseSignal signal) {
        return switch (signal) {
            case BELL_RUNG -> BELL_COOLDOWN_TICKS;
            case HOSTILE_CONFIRMED -> TARGET_COOLDOWN_TICKS;
            case FRIENDLY_DAMAGED -> DAMAGE_COOLDOWN_TICKS;
            case FRIENDLY_KILLED -> DEATH_COOLDOWN_TICKS;
            case RAID_ACTIVE -> 0L;
        };
    }

    private static @Nullable DimensionRuntime runtime(
            ServerLevel level,
            boolean create) {
        ServerRuntime serverRuntime = SERVERS.get(level.getServer());
        if (serverRuntime == null) {
            if (!create) {
                return null;
            }
            serverRuntime = new ServerRuntime();
            SERVERS.put(level.getServer(), serverRuntime);
        }
        DimensionRuntime runtime = serverRuntime.dimensions.get(level.dimension());
        if (runtime == null && create) {
            runtime = new DimensionRuntime();
            serverRuntime.dimensions.put(level.dimension(), runtime);
        }
        return runtime;
    }

    public enum RouteOutcome {
        APPLIED,
        THROTTLED,
        NO_NETWORK,
        REJECTED,
        RAID_REMAINS_ACTIVE
    }

    public record RouteResult(
            RouteOutcome outcome,
            @Nullable UUID networkId,
            VillageDefenseSignal signal) {
        public RouteResult {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(signal, "signal");
        }
    }

    public record LastEvent(
            long gameTime,
            VillageDefenseEventSource source,
            VillageDefenseSignal signal,
            @Nullable UUID networkId,
            RouteOutcome outcome) {
    }

    public record DiagnosticsSnapshot(
            Map<VillageDefenseSignal, Long> acceptedBySignal,
            long routedEvents,
            long throttledEvents,
            long unmatchedEvents,
            long rejectedEvents,
            long raidStarts,
            long raidEnds,
            long restartReconciliations,
            int activeRaids,
            long raidObservationOverflows,
            boolean raidTrackingOverflowed,
            int trackedNetworks,
            long evictions,
            @Nullable LastEvent lastEvent) {
        private static final DiagnosticsSnapshot EMPTY = new DiagnosticsSnapshot(
                Map.of(), 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0, 0L, false, 0, 0L, null);

        public DiagnosticsSnapshot {
            acceptedBySignal = Map.copyOf(acceptedBySignal);
        }
    }

    private static final class ServerRuntime {
        private final Map<ResourceKey<Level>, DimensionRuntime> dimensions =
                new LinkedHashMap<>();
    }

    private static final class DimensionRuntime {
        private final VillageDefenseSignalGate signalGate =
                new VillageDefenseSignalGate();
        private final EnumMap<VillageDefenseSignal, Long> acceptedBySignal =
                new EnumMap<>(VillageDefenseSignal.class);
        private final Map<Integer, RaidObservation> activeRaids = new HashMap<>();
        private long routedEvents;
        private long throttledEvents;
        private long unmatchedEvents;
        private long rejectedEvents;
        private long raidStarts;
        private long raidEnds;
        private long restartReconciliations;
        private long raidObservationOverflows;
        private boolean raidTrackingOverflowed;
        @Nullable
        private LastEvent lastEvent;

        private RouteResult record(
                long gameTime,
                VillageDefenseEventSource source,
                VillageDefenseSignal signal,
                @Nullable UUID networkId,
                RouteOutcome outcome) {
            lastEvent = new LastEvent(
                    gameTime, source, signal, networkId, outcome);
            return new RouteResult(outcome, networkId, signal);
        }
    }

    private record RaidObservation(@Nullable UUID networkId) {
    }
}
