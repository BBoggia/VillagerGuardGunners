package com.gamergaming.villagerguardgunners.village;

import com.gamergaming.villagerguardgunners.mixin.accessor.VillagePoiStorageAccessor;
import com.gamergaming.villagerguardgunners.village.compat.*;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.worldgen.village.VillageBuildingSavedData;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import tallestegg.guardvillagers.entities.Guard;

/** Server-thread discovery over currently loaded facts. Every cursor is transient and fair across chunks. */
public final class VillageDiscoveryRuntime {
    public static final int MAX_LOADED_CHUNKS = 32_768;
    public static final int MAX_PENDING_GUARDS = 16_384;
    public static final int CHUNKS_PER_TICK = 4;
    public static final int UNITS_PER_CHUNK = 8;
    public static final int REVIEW_INTERVAL = 600;
    private static final int MAX_LAYOUT_SCANS = 1_024;
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();
    private static volatile long tagRevision;

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk) {
            long chunk = event.getChunk().getPos().toLong();
            dispatch(level, () -> trackChunk(level, chunk));
        }
    }
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            long chunk = event.getChunk().getPos().toLong();
            dispatch(level, () -> {
                State state = STATES.get(level);
                if (state != null) {
                    state.chunks.remove(chunk);
                    var layouts = state.layouts.values().iterator();
                    while (layouts.hasNext()) {
                        LayoutScan scan = layouts.next();
                        if (scan.identity.startChunk() == chunk) {
                            // References may still hold this cursor until their next turn.
                            scan.invalidate();
                            layouts.remove();
                        }
                    }
                }
            });
        }
    }
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof Guard guard && event.getLevel() instanceof ServerLevel level) {
            UUID id = guard.getUUID();
            dispatch(level, () -> {
                State state = state(level);
                if (state.guards.size() < MAX_PENDING_GUARDS) state.guards.putIfAbsent(id, 0L);
                else state.rejections++;
            });
        }
    }
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Guard guard && event.getLevel() instanceof ServerLevel level) {
            State state = STATES.get(level);
            if (state != null) { state.guards.remove(guard.getUUID()); state.missingGuards.remove(guard.getUUID()); }
        }
    }
    public static void onTagsUpdated(TagsUpdatedEvent event) { tagRevision++; }
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level);
    }
    public static void onServerStopping(ServerStoppingEvent event) {
        STATES.keySet().removeIf(level -> level.getServer() == event.getServer());
    }
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            for (ServerLevel level : event.getServer().getAllLevels()) tick(level);
        }
    }
    private static void dispatch(ServerLevel level, Runnable work) {
        if (level.getServer().isSameThread()) work.run();
        else level.getServer().execute(() -> {
            if (level.getServer().getLevel(level.dimension()) == level) work.run();
        });
    }
    private static State state(ServerLevel level) { return STATES.computeIfAbsent(level, ignored -> new State()); }

    /** Lifecycle entry point also used by native GameTests. Does not request or load this chunk. */
    public static void trackChunk(ServerLevel level, long chunk) {
        State state = state(level);
        if (state.chunks.containsKey(chunk)) return;
        if (state.chunks.size() == MAX_LOADED_CHUNKS) { state.rejections++; return; }
        state.chunks.put(chunk, new ChunkWork(chunk));
    }

    public static void tick(ServerLevel level) {
        State state = STATES.get(level);
        if (state == null) return;
        state.lastUnits = 0;
        if (!ModConfigs.GUARD_GUNNERS.enabled.get()) return;
        VillageRegistrySavedData data = VillageRegistrySavedData.get(level);
        if (!data.enabled()) return;
        if (state.tagRevision != tagRevision) {
            state.layouts.clear();
            state.tagRevision = tagRevision;
        }
        long now = level.getGameTime();
        state.lastUnits = 0;
        int chunks = Math.min(CHUNKS_PER_TICK, state.chunks.size());
        for (int i = 0; i < chunks; i++) {
            var entry = state.chunks.entrySet().iterator().next();
            long key = entry.getKey();
            ChunkWork work = entry.getValue();
            state.chunks.remove(key);
            if (loaded(level, key) == null) continue;
            if (work.epoch != state.tagRevision) work.restart(state.tagRevision);
            if (work.nextReview <= now) {
                try {
                    for (int unit = 0; unit < UNITS_PER_CHUNK && work.nextReview <= now; unit++) {
                        work.advance(level, state, data, now);
                        state.lastUnits++;
                    }
                } catch (ConcurrentModificationException exception) {
                    // A changed native POI/reference collection invalidates only this transient cursor.
                    work.restart(state.tagRevision);
                    work.nextReview = now + 20;
                }
            }
            state.chunks.put(key, work);
        }
        for (int i = 0, count = Math.min(4, state.guards.size()); i < count; i++) {
            var entry = state.guards.entrySet().iterator().next();
            UUID id = entry.getKey();
            long due = entry.getValue();
            state.guards.remove(id);
            if (data.guardVillage(id) != null) { state.missingGuards.remove(id); continue; }
            if (due > now) { state.guards.put(id, due); continue; }
            var entity = level.getEntity(id);
            if (entity == null) {
                // Forge can publish a join before native entity tracking is visible.
                // Canceled/never-inserted joins retire after a bounded wait.
                if (state.missingGuards.merge(id, 1, Integer::sum) < 10) state.guards.put(id, now + 20);
                else state.missingGuards.remove(id);
                continue;
            }
            state.missingGuards.remove(id);
            if (!(entity instanceof Guard guard) || !guard.isAlive()) continue;
            if (due <= now) {
                ChunkWork work = state.chunks.get(guard.chunkPosition().toLong());
                if (work != null && work.epoch == state.tagRevision && work.complete && work.unresolvedStarts == 0) {
                    UUID village = data.membershipAt(guard.blockPosition()).unambiguous();
                    if (village != null && isVillage(level, data.get(village).identity().structure())
                            && data.affiliateGuard(id, village)) continue;
                }
                due = now + 100;
            }
            state.guards.put(id, due);
        }
    }

    private static ResourceLocation villageKey(ServerLevel level, Structure structure) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return registry.getResourceKey(structure).filter(key -> SettlementDefinitions.lookup(key.location()).friendly())
                .map(net.minecraft.resources.ResourceKey::location).orElse(null);
    }
    private static boolean isVillage(ServerLevel level, ResourceLocation id) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return registry.containsKey(id) && SettlementDefinitions.lookup(id).friendly();
    }

    private static LevelChunk loaded(ServerLevel level, long key) {
        return level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
    }
    public static Diagnostics diagnostics(ServerLevel level) {
        State s = STATES.get(level);
        if (s == null) return new Diagnostics(0, 0, 0, 0, 0, false);
        return new Diagnostics(s.chunks.size(), s.guards.size(), s.completed, s.rejections, s.lastUnits, false);
    }
    public record Diagnostics(int trackedChunks, int pendingGuards, long completedReviews, long deferredOrRejected,
                              int lastWorkUnits, boolean structureTypeLimit) {}
    public static ChunkStatus chunkStatus(ServerLevel level, long chunk) {
        State state = STATES.get(level);
        ChunkWork work = state == null ? null : state.chunks.get(chunk);
        return work == null ? new ChunkStatus(false, false, 0, 0)
                : new ChunkStatus(true, work.epoch == tagRevision && work.complete,
                        work.unresolvedStarts, work.unknownPoiSections);
    }
    public record ChunkStatus(boolean tracked, boolean complete, int unresolvedStarts, int unknownPoiSections) {}

    private static final class State {
        final LinkedHashMap<Long, ChunkWork> chunks = new LinkedHashMap<>();
        final LinkedHashMap<UUID, Long> guards = new LinkedHashMap<>();
        final Map<UUID, Integer> missingGuards = new java.util.HashMap<>();
        final Map<UUID, LayoutScan> layouts = new java.util.HashMap<>();
        long tagRevision = -1, completed, rejections;
        int lastUnits;
    }

    private static final class ChunkWork {
        final long chunk;
        long epoch = -1, nextReview;
        int sectionY, unresolvedStarts, unknownPoiSections;
        boolean startsDone, referencesDone, complete, poiStarted;
        Iterator<StructureStart> starts;
        Iterator<Map.Entry<Structure, it.unimi.dsi.fastutil.longs.LongSet>> referenceEntries;
        Iterator<Long> references;
        Structure referenceStructure;
        ResourceLocation referenceId;
        LayoutScan active;
        Iterator<PoiRecord> poiRecords;
        List<BlockPos> homes = new ArrayList<>();
        ChunkWork(long chunk) { this.chunk = chunk; }
        void restart(long epoch) {
            this.epoch = epoch;
            unresolvedStarts = unknownPoiSections = 0;
            startsDone = referencesDone = complete = poiStarted = false;
            starts = null;
            referenceEntries = null;
            referenceStructure = null;
            referenceId = null;
            references = null;
            active = null;
            poiRecords = null;
            homes.clear();
            nextReview = 0;
        }
        void advance(ServerLevel level, State state, VillageRegistrySavedData data, long now) {
            if (complete) restart(state.tagRevision);
            if (active != null) {
                active.advance(level, state, data);
                if (active.complete) {
                    if (!active.success) unresolvedStarts++;
                    active = null;
                }
                return;
            }
            LevelChunk current = loaded(level, chunk);
            if (current == null) { restart(state.tagRevision); return; }
            // Inspect only native metadata present in this chunk. Tag cardinality does
            // not add work to empty chunks or impose a global discovery ceiling.
            if (!startsDone) {
                if (starts == null) starts = current.getAllStarts().values().iterator();
                if (starts.hasNext()) {
                    StructureStart start = starts.next();
                    if (start != null && start.isValid()) {
                        ResourceLocation id = villageKey(level, start.getStructure());
                        if (id != null) begin(level, id, start, state, data, true);
                    }
                } else { startsDone = true; starts = null; }
                return;
            }
            if (!referencesDone) {
                if (references != null && references.hasNext()) {
                    LevelChunk origin = loaded(level, references.next());
                    StructureStart start = origin == null ? null : origin.getStartForStructure(referenceStructure);
                    if (start == null || !start.isValid()) unresolvedStarts++;
                    else begin(level, referenceId, start, state, data, false);
                    return;
                }
                references = null;
                if (referenceEntries == null) referenceEntries = current.getAllReferences().entrySet().iterator();
                if (referenceEntries.hasNext()) {
                    var entry = referenceEntries.next();
                    referenceStructure = entry.getKey();
                    referenceId = villageKey(level, referenceStructure);
                    if (referenceId != null) references = entry.getValue().iterator();
                } else { referencesDone = true; referenceEntries = null; }
                return;
            }
            if (!poiStarted) { sectionY = level.getMinSection(); poiStarted = true; }
            if (sectionY < level.getMaxSection()) {
                long section = SectionPos.asLong(ChunkPos.getX(chunk), sectionY, ChunkPos.getZ(chunk));
                if (poiRecords == null) {
                    var cached = ((VillagePoiStorageAccessor) level.getPoiManager()).villagerGuardGunners$cachedSection(section);
                    if (cached == null) { unknownPoiSections++; sectionY++; return; }
                    if (cached.isEmpty()) {
                        if (unresolvedStarts == 0) data.observeHomes(section, List.of());
                        else unknownPoiSections++;
                        sectionY++;
                        return;
                    }
                    poiRecords = ((PoiSection) cached.get()).getRecords(type -> type.is(PoiTypes.HOME), PoiManager.Occupancy.ANY).iterator();
                    homes.clear();
                }
                if (poiRecords.hasNext()) {
                    if (homes.size() == 256) {
                        unknownPoiSections++;
                        state.rejections++;
                        poiRecords = null;
                        sectionY++;
                    } else homes.add(poiRecords.next().getPos().immutable());
                    return;
                }
                if (unresolvedStarts == 0) {
                    if (!data.observeHomes(section, homes)) state.rejections++;
                } else unknownPoiSections++;
                poiRecords = null;
                sectionY++;
                return;
            }
            // Anchors use a saved-data chunk index; network aliases are resolved separately by building ID.
            if (unresolvedStarts == 0) {
                var buildings = VillageBuildingSavedData.get(level).identitiesInChunk(new ChunkPos(chunk), 65);
                var membershipBudget = new VillageFootprint.EvidenceBudget(VillageRegistrySavedData.MAX_EVIDENCE_CHECKS);
                if (buildings.size() > 64) state.rejections++;
                else for (var building : buildings) {
                    UUID village = data.membershipAt(building.anchorPosition(), membershipBudget).unambiguous();
                    if (village != null && isVillage(level, data.get(village).identity().structure())
                            && !data.associateBuilding(building.buildingId(), village)) state.rejections++;
                }
            }
            complete = true;
            nextReview = now + REVIEW_INTERVAL;
            state.completed++;
        }
        void begin(ServerLevel level, ResourceLocation structure, StructureStart start, State state,
                   VillageRegistrySavedData data, boolean refresh) {
            int count = start.getPieces().size();
            if (count == 0 || count > VillageFootprint.MAX_PIECES) {
                unresolvedStarts++; state.rejections++; return;
            }
            VillageIdentity identity = new VillageIdentity(level.dimension().location(), structure, start.getChunkPos().toLong());
            LayoutScan shared = state.layouts.get(identity.id());
            if (shared != null && shared.observedStart == start && shared.pieceCount == count) {
                active = shared;
                return;
            }
            var known = data.get(identity.id());
            // Population admission may have published only houses and spawn sources.
            if (!refresh && known != null && known.footprint().pieces().size() == count) return;
            if (shared == null && state.layouts.size() >= MAX_LAYOUT_SCANS) {
                unresolvedStarts++; state.rejections++; return;
            }
            active = new LayoutScan(identity, start);
            state.layouts.put(identity.id(), active);
        }
    }

    /** Referencing chunks advance one shared cursor; only a full layout is published. */
    private static final class LayoutScan {
        final VillageIdentity identity;
        final StructureStart observedStart;
        final int pieceCount;
        final List<VillageFootprint.Piece> pieces = new ArrayList<>();
        int pieceIndex;
        boolean complete, success;
        LayoutScan(VillageIdentity identity, StructureStart start) {
            this.identity = identity; observedStart = start; pieceCount = start.getPieces().size();
        }
        void invalidate() {
            complete = true; success = false;
            pieces.clear();
        }
        void finish(State state, boolean success) {
            this.complete = true; this.success = success;
            pieces.clear();
            state.layouts.remove(identity.id(), this);
        }
        void advance(ServerLevel level, State state, VillageRegistrySavedData data) {
            if (complete) return;
            LevelChunk origin = loaded(level, identity.startChunk());
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(identity.structure());
            StructureStart start = origin == null ? null : origin.getStartForStructure(structure);
            if (start == null || start != observedStart || start.getPieces().size() != pieceCount) {
                finish(state, false); return;
            }
            var piece = start.getPieces().get(pieceIndex++);
            try {
                var reading = PieceReaders.read(piece, SettlementDefinitions.lookup(identity.structure()).reader());
                pieces.add(reading.geometry());
                if (pieceIndex == pieceCount) {
                    boolean published = data.observe(identity, VillageFootprint.of(pieces));
                    if (!published) state.rejections++;
                    finish(state, published);
                }
            } catch (IllegalArgumentException exception) {
                state.rejections++;
                finish(state, false);
            }
        }
    }
    private VillageDiscoveryRuntime() {}
}
