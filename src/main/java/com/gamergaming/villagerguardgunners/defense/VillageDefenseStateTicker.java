package com.gamergaming.villagerguardgunners.defense;

import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingRuntime;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * Tracks only dimensions with a scheduled defense review. It never scans
 * networks on ordinary ticks; the saved-data deadline index supplies bounded
 * due work when a tracked dimension reaches its next deadline.
 */
public final class VillageDefenseStateTicker {
    private static final Map<MinecraftServer, Map<ResourceKey<Level>, Long>>
            NEXT_REVIEWS = new IdentityHashMap<>();

    private VillageDefenseStateTicker() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        synchronized (NEXT_REVIEWS) {
            NEXT_REVIEWS.remove(server);
        }
        for (ServerLevel level : server.getAllLevels()) {
            VillageDefenseNetworkSavedData data = VillageDefenseNetworkSavedData.get(
                    level);
            VillageDefenseTimings timings = VillageDefenseRuntimePolicy.timings();
            data.reviewDue(
                    level.getGameTime(),
                    timings,
                    VillageDefenseNetworkSavedData.MAXIMUM_REVIEWS_PER_TICK,
                    (networkId, status) -> {
                        VillageDefenseStaffingRuntime.requestReview(level, networkId);
                        return VillageDefenseEventRuntime.reviewFacts(
                                level,
                                data,
                                networkId,
                                level.getGameTime());
                    });
            track(level, data);
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ResourceKey<Level> dimension : dueDimensions(server)) {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                untrack(server, dimension);
                continue;
            }
            VillageDefenseNetworkSavedData data = VillageDefenseNetworkSavedData.get(
                    level);
            VillageDefenseTimings timings = VillageDefenseRuntimePolicy.timings();
            data.reviewDue(
                    level.getGameTime(),
                    timings,
                    VillageDefenseNetworkSavedData.MAXIMUM_REVIEWS_PER_TICK,
                    (networkId, status) -> {
                        VillageDefenseStaffingRuntime.requestReview(level, networkId);
                        return VillageDefenseEventRuntime.reviewFacts(
                                level,
                                data,
                                networkId,
                                level.getGameTime());
                    });
            track(level, data);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        synchronized (NEXT_REVIEWS) {
            NEXT_REVIEWS.remove(event.getServer());
        }
    }

    static void track(
            ServerLevel level,
            VillageDefenseNetworkSavedData data) {
        long nextReview = data.nextReviewGameTime();
        MinecraftServer server = level.getServer();
        synchronized (NEXT_REVIEWS) {
            Map<ResourceKey<Level>, Long> tracked = NEXT_REVIEWS.computeIfAbsent(
                    server, ignored -> new LinkedHashMap<>());
            if (nextReview == VillageDefenseStatus.NO_REVIEW) {
                tracked.remove(level.dimension());
                if (tracked.isEmpty()) {
                    NEXT_REVIEWS.remove(server);
                }
            } else {
                tracked.put(level.dimension(), nextReview);
            }
        }
    }

    private static List<ResourceKey<Level>> dueDimensions(
            MinecraftServer server) {
        synchronized (NEXT_REVIEWS) {
            Map<ResourceKey<Level>, Long> tracked = NEXT_REVIEWS.get(server);
            if (tracked == null || tracked.isEmpty()) {
                return List.of();
            }
            List<ResourceKey<Level>> due = null;
            Iterator<Map.Entry<ResourceKey<Level>, Long>> entries =
                    tracked.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<ResourceKey<Level>, Long> entry = entries.next();
                ServerLevel level = server.getLevel(entry.getKey());
                if (level == null) {
                    entries.remove();
                } else if (entry.getValue() <= level.getGameTime()) {
                    if (due == null) {
                        due = new ArrayList<>();
                    }
                    due.add(entry.getKey());
                }
            }
            if (tracked.isEmpty()) {
                NEXT_REVIEWS.remove(server);
            }
            return due == null ? List.of() : due;
        }
    }

    private static void untrack(
            MinecraftServer server,
            ResourceKey<Level> dimension) {
        synchronized (NEXT_REVIEWS) {
            Map<ResourceKey<Level>, Long> tracked = NEXT_REVIEWS.get(server);
            if (tracked == null) {
                return;
            }
            tracked.remove(dimension);
            if (tracked.isEmpty()) {
                NEXT_REVIEWS.remove(server);
            }
        }
    }
}
