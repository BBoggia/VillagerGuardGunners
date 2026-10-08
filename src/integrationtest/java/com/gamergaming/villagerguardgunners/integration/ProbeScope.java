package com.gamergaming.villagerguardgunners.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import tallestegg.guardvillagers.entities.Guard;

/** Test-only scopes; redirects always restore context, including exceptional exits. */
public final class ProbeScope implements AutoCloseable {
    private static final ThreadLocal<ProbeScope> ACTIVE = new ThreadLocal<>();
    public static final ThreadLocal<StructureStart> START = new ThreadLocal<>();
    public static final ThreadLocal<ResourceLocation> TEMPLATE = new ThreadLocal<>();
    public final List<SpawnRequest> spawns = new ArrayList<>();
    public int poiReads;
    public int missingChunkRequests;
    public int cachedChunkLookups;
    public String firstMissingChunkTrace;
    public BiPredicate<StructureStart, ResourceLocation> defer = (start, template) -> false;
    private final ProbeScope previous = ACTIVE.get();

    public ProbeScope() {
        ACTIVE.set(this);
    }

    public static void finalized(Guard guard) {
        ProbeScope scope = ACTIVE.get();
        StructureStart start = com.gamergaming.villagerguardgunners.village.population.VillagePopulationPlacement.currentStart();
        if (start == null) start = START.get();
        ResourceLocation template = TEMPLATE.get();
        if (scope != null && start != null && template != null) {
            boolean deferred = scope.defer.test(start, template);
            scope.spawns.add(new SpawnRequest(start.getChunkPos().toLong(), template, deferred));
            if (deferred) {
                // Both WorldGenRegion and ServerLevel honor Forge's spawn-canceled flag.
                guard.setSpawnCancelled(true);
            }
        }
    }

    public static void poiRead() {
        if (ACTIVE.get() != null) ACTIVE.get().poiReads++;
    }

    public static void cachedChunkLookup() {
        ProbeScope scope = ACTIVE.get();
        if (scope != null) scope.cachedChunkLookups++;
    }

    public static void missingChunkRequest() {
        if (ACTIVE.get() != null) {
            ACTIVE.get().missingChunkRequests++;
            if (ACTIVE.get().firstMissingChunkTrace == null) ACTIVE.get().firstMissingChunkTrace =
                    java.util.Arrays.stream(Thread.currentThread().getStackTrace()).limit(20).map(Object::toString).collect(java.util.stream.Collectors.joining("\n"));
        }
    }

    @Override
    public void close() {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }

    public record SpawnRequest(long startChunk, ResourceLocation template, boolean deferred) {}
}
