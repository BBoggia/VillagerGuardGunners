package com.gamergaming.villagerguardgunners.worldgen.village;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/** Chunk-load-driven resumption for durable, unfinished building actions. */
public final class VillageBuildingActivationRecovery {
    private static final Map<MinecraftServer, VillageBuildingActivationQueue> SCHEDULED =
            new WeakHashMap<>();
    private static final Map<MinecraftServer, Set<DiagnosticKey>> REPORTED = new WeakHashMap<>();

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        MinecraftServer server = event.getServer();
        VillageBuildingActivationQueue queue = SCHEDULED.get(server);
        if (queue != null) {
            // The Overworld game clock is shared across dimensions and does
            // not jump when players change the time of day.
            for (var attempt : queue.takeDue(server.overworld().getGameTime())) {
                VillageBuildingAnchorLifecycle.activatePending(
                        server, ResourceKey.create(Registries.DIMENSION, attempt.dimension()),
                        attempt.buildingId(), attempt.attempt());
            }
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        Objects.requireNonNull(event, "event");
        if (event.getLevel() instanceof ServerLevel level) {
            MinecraftServer server = level.getServer();
            ResourceKey<Level> dimension = level.dimension();
            ChunkPos chunkPosition = event.getChunk().getPos();
            Runnable resume = () -> {
                ServerLevel liveLevel = server.getLevel(dimension);
                if (liveLevel != null) {
                    resumeLoadedChunk(liveLevel, chunkPosition);
                }
            };
            if (server.isSameThread()) {
                resume.run();
            } else {
                server.execute(resume);
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        Objects.requireNonNull(event, "event");
        synchronized (SCHEDULED) {
            SCHEDULED.remove(event.getServer());
        }
        synchronized (REPORTED) {
            REPORTED.remove(event.getServer());
        }
    }

    static void resumeLoadedChunk(ServerLevel level, ChunkPos chunkPosition) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunkPosition, "chunkPosition");
        for (VillageBuildingRecord record
                : VillageBuildingSavedData.get(level).pendingActivationsIn(chunkPosition)) {
            schedule(level, record.identity().buildingId());
        }
    }

    static void schedule(ServerLevel level, UUID buildingId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(buildingId, "buildingId");
        MinecraftServer server = level.getServer();
        SCHEDULED.computeIfAbsent(server, ignored -> new VillageBuildingActivationQueue())
                .schedule(level.dimension().location(), buildingId, server.overworld().getGameTime());
    }

    static void retry(MinecraftServer server, ResourceKey<Level> dimension,
                      UUID buildingId, int attempt, int delay) {
        VillageBuildingActivationQueue queue = SCHEDULED.get(server);
        if (queue != null) {
            queue.retry(dimension.location(), buildingId, attempt, server.overworld().getGameTime(), delay);
        }
    }

    static void release(MinecraftServer server, UUID buildingId) {
        synchronized (SCHEDULED) {
            VillageBuildingActivationQueue scheduled = SCHEDULED.get(server);
            if (scheduled == null) {
                return;
            }
            scheduled.release(buildingId);
        }
    }

    static boolean shouldReport(
            MinecraftServer server,
            VillageBuildingIdentity identity,
            String category,
            String status) {
        DiagnosticKey key = new DiagnosticKey(
                identity.dimensionId().toString(),
                identity.buildingType(),
                category,
                status);
        synchronized (REPORTED) {
            return REPORTED.computeIfAbsent(server, ignored -> new HashSet<>()).add(key);
        }
    }

    private record DiagnosticKey(
            String dimension,
            VillageBuildingType buildingType,
            String category,
            String status) {
    }

    private VillageBuildingActivationRecovery() {
    }
}
