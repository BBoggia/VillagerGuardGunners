package com.gamergaming.villagerguardgunners.village.coverage.live;

import com.gamergaming.villagerguardgunners.api.GuardSquadAccess;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseState;
import com.gamergaming.villagerguardgunners.defense.duty.*;
import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.orders.GuardOrders.MovementOrder;
import com.gamergaming.villagerguardgunners.role.GuardRoleResolver;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteResolver;
import com.gamergaming.villagerguardgunners.village.*;
import com.gamergaming.villagerguardgunners.village.compat.SettlementDefinitions;
import com.gamergaming.villagerguardgunners.village.coverage.*;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoveragePolicy.*;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageReservations.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import tallestegg.guardvillagers.entities.Guard;

/** Server-thread, loaded-resident index and bounded world-work scheduler. */
public final class VillageCoverageRuntime {
    public static final int MAX_VILLAGES = 64, MAX_GUARDS = 4_096;
    public static final int REVIEWS_PER_TICK = 4, TERRAIN_UNITS_PER_TICK = 512, PATHS_PER_TICK = 4;
    private static final Map<ServerLevel, Dimension> LEVELS = new WeakHashMap<>();
    private VillageCoverageRuntime() { }
    public static boolean enabled() {
        return ModConfigs.GUARD_GUNNERS.enabled.get() && ModConfigs.GUARD_GUNNERS.villageDefense.automaticCoverage.get();
    }
    public static void synchronizeGuard(Guard guard) {
        if (!(guard.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        if (!enabled() || !guard.isAlive() || guard.isRemoved()) { remove(level, guard.getUUID()); return; }
        UUID id = VillageRegistrySavedData.get(level).guardVillage(guard.getUUID());
        if (id == null) { remove(level, guard.getUUID()); return; }
        var record = VillageRegistrySavedData.get(level).get(id);
        if (record == null || !SettlementDefinitions.lookup(record.identity().structure()).friendly()) { remove(level, guard.getUUID()); return; }
        Dimension dimension = LEVELS.computeIfAbsent(level, k -> new Dimension());
        if (!dimension.guards.containsKey(guard.getUUID()) && dimension.guards.size() >= MAX_GUARDS) { dimension.rejections++; return; }
        Village village = dimension.villages.get(id);
        if (village == null) {
            if (dimension.villages.size() >= MAX_VILLAGES) { dimension.rejections++; return; }
            village = new Village(id, dimension.book);
            dimension.villages.put(id, village);
        }
        if (village.guards.size() >= VillageCoveragePolicy.MAX_CANDIDATES && !village.guards.contains(guard.getUUID())) {
            dimension.rejections++; return;
        }
        UUID previous = dimension.guards.put(guard.getUUID(), id);
        if (previous != null && !previous.equals(id)) removeFromVillage(dimension, previous, guard.getUUID());
        village.guards.add(guard.getUUID());
    }
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel level : event.getServer().getAllLevels()) tick(level);
    }
    public static void tick(ServerLevel level) {
        Dimension dimension = LEVELS.get(level);
        if (dimension == null) return;
        if (!enabled() || dimension.definitionsGeneration != SettlementDefinitions.snapshot().generation()) { clear(level); return; }
        dimension.lastTerrainUnits = 0;
        dimension.lastPaths = 0;
        int visits = Math.min(REVIEWS_PER_TICK, dimension.villages.size());
        for (int i = 0; i < visits; i++) {
            UUID id = dimension.villages.keySet().iterator().next();
            Village village = dimension.villages.remove(id);
            dimension.villages.put(id, village);
            if (level.getGameTime() < village.retryAt) continue;
            try { reviewVillage(level, dimension, village, id); }
            catch (RuntimeException exception) {
                invalidate(dimension, village);
                village.terrain = null;
                village.pendingTerrain = null;
                village.retryAt = level.getGameTime() + 600;
                dimension.rejections++;
            }
        }
        int paths = Math.min(PATHS_PER_TICK, dimension.requests.size());
        for (int i = 0; i < paths; i++) {
            UUID guardId = dimension.requests.keySet().iterator().next();
            Lease requested = dimension.requests.remove(guardId);
            var entity = level.getEntity(guardId);
            if (!(entity instanceof Guard guard) || !requested.equals(lease(guard))) continue;
            Village village = dimension.villages.get(requested.villageId());
            Destination destination = village.destinations.stream().filter(d -> d.id().equals(requested.destinationId())).findFirst().orElse(null);
            if (destination == null) continue;
            dimension.lastPaths++;
            List<BlockPos> path = village.terrain.path(guard.blockPosition(), destination.position());
            dimension.paths.put(guardId, new Route(requested, path));
        }
    }
    private static void reviewVillage(ServerLevel level, Dimension dimension, Village village, UUID id) {
        VillageRecord record = VillageRegistrySavedData.get(level).get(id);
        if (record == null || !VillageRegistrySavedData.get(level).enabled() || !SettlementDefinitions.lookup(record.identity().structure()).friendly()) { invalidate(dimension, village); return; }
        long now = level.getGameTime();
        if (!record.footprint().equals(village.footprint)) {
            // A changed footprint is not a periodic refresh; retire its observations immediately.
            invalidate(dimension, village);
            village.terrain = null;
            village.pendingTerrain = null;
            village.footprint = record.footprint();
        }
        if (village.terrain != null && !village.queuedChanges.isEmpty()) {
            // Merge bursts before spending another survey slice or rebuilding seed metadata.
            village.pendingTerrain = CoverageTerrain.repair(level, record, village.terrain);
            village.pendingRevision = record.revision();
            village.queuedChanges.clear();
        }
        if (village.pendingTerrain == null && (village.terrain == null || now >= village.rebuildAt || village.recordRevision != record.revision())) {
            village.pendingTerrain = new CoverageTerrain(level, record);
            village.pendingRevision = record.revision();
        }
        if (village.pendingTerrain != null) {
            CoverageTerrain pending = village.pendingTerrain;
            if (!pending.complete()) {
                dimension.lastTerrainUnits += pending.advance(TERRAIN_UNITS_PER_TICK / REVIEWS_PER_TICK);
            }
            if (pending.complete()) {
                boolean unchanged = !village.repairing && village.layout != null && pending.sameNavigation(village.terrain);
                if (unchanged) village.unchangedRefreshes++;
                if (!unchanged) {
                    village.publication++;
                }
                village.terrain = pending;
                village.pendingTerrain = null;
                village.recordRevision = village.pendingRevision;
                village.rebuildAt = village.recordRevision == record.revision() ? now + 600 : now;
                if (!unchanged) { publish(village, level); village.reviewAt = 0; }
                if (village.repairing) village.policy.terrainRepaired();
                village.repairing = false;
            }
        }
        // A complete active publication remains usable while the replacement is surveyed.
        if (village.layout != null && !village.repairing && now >= village.reviewAt) {
            review(level, dimension, village, now);
            village.reviewAt = now + 20;
        }
    }
    private static void publish(Village village, ServerLevel level) {
        CoverageTerrain terrain = village.terrain;
        if (!terrain.usable()) { village.layout = null; village.plan = null; return; }
        List<VillageCoverageLayout.Site> sites = new ArrayList<>();
        for (BlockPos anchor : terrain.anchors()) {
            BlockPos approach = terrain.approach(anchor);
            BlockPos p = approach == null ? anchor : approach;
            sites.add(new VillageCoverageLayout.Site(p, terrain.kind(anchor),
                    approach == null ? null : terrain.component(approach)));
        }
        village.layout = VillageCoverageLayout.partition(village.id, village.publication, sites);
        List<Destination> destinations = new ArrayList<>();
        Set<BlockPos> used = new HashSet<>();
        for (var site : sites) {
            if (!used.add(site.position())) continue;
            var area = village.layout.areas().stream().filter(a -> a.contains(site.position())
                    && Objects.equals(a.component(), site.component())).findFirst().orElse(null);
            if (area == null) continue;
            boolean loaded = level.getChunkSource().getChunkNow(site.position().getX() >> 4, site.position().getZ() >> 4) != null;
            DestinationState state = !loaded ? DestinationState.UNLOADED
                    : site.component() != null && terrain.walkable(site.position()) ? DestinationState.READY : DestinationState.BLOCKED;
            UUID id = UUID.nameUUIDFromBytes((area.id() + ":" + site.position()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Destination old = village.destinations.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
            long revision = old != null && old.state() == state && Objects.equals(old.component(), site.component())
                    && old.position().equals(site.position()) && !village.changedDestinations.contains(id)
                    ? old.terrainRevision() : village.publication;
            destinations.add(new Destination(id, area.id(), site.position(), state, site.component(), revision));
        }
        village.destinations = List.copyOf(destinations);
        village.changedDestinations.clear();
    }
    private static void review(ServerLevel level, Dimension dimension, Village village, long now) {
        List<Candidate> candidates = new ArrayList<>();
        Set<UUID> threats = new HashSet<>();
        VillageDefenseState state = now < village.alertUntil ? VillageDefenseState.ALERT
                : now < village.recoveryUntil ? VillageDefenseState.RECOVERY
                : now < village.watchUntil ? VillageDefenseState.WATCH : VillageDefenseState.PEACE;
        for (UUID id : List.copyOf(village.guards)) {
            if (!(level.getEntity(id) instanceof Guard guard)) { remove(level, id); continue; }
            if (guard.getTarget() != null && guard.getTarget().isAlive()) {
                village.alertUntil = now + ModConfigs.GUARD_GUNNERS.villageDefense.alertDurationTicks.get();
                village.recoveryUntil = village.alertUntil + ModConfigs.GUARD_GUNNERS.villageDefense.recoveryDurationTicks.get();
                if (state.readinessRank() < VillageDefenseState.ALERT.readinessRank()) state = VillageDefenseState.ALERT;
                BlockPos threat = guard.getTarget().blockPosition();
                village.layout.areas().stream().filter(a -> a.contains(threat)).forEach(a -> threats.add(a.id()));
            }
            var post = VillageDefenseStaffingRuntime.assignment(guard);
            Lease claim = dimension.book.forGuard(id);
            if (post == null && claim != null && claim.kind() == Kind.FIXED_POST) dimension.book.release(claim);
            if (post != null) {
                var network = VillageDefenseNetworkSavedData.get(level).getNetwork(post.networkId());
                if (network != null && network.defenseStatus().state().readinessRank() > state.readinessRank()) state = network.defenseStatus().state();
            }
            candidates.add(new Candidate(id, guard.blockPosition(), village.terrain.component(guard.blockPosition()), availability(guard)));
        }
        // Native raid lookup reads the live raid registry; it does not locate/load a structure.
        if (!village.terrain.anchors().isEmpty()) {
            var raid = level.getRaidAt(village.terrain.anchors().get(0));
            if (raid != null && !raid.isStopped() && !raid.isOver()) state = VillageDefenseState.SIEGE;
        }
        if (now < village.alertUntil) {
            for (BlockPos threat : village.threats) village.layout.areas().stream().filter(a -> a.contains(threat)).forEach(a -> threats.add(a.id()));
        } else village.threats.clear();
        village.readiness = state;
        village.plan = village.policy.schedule(village.layout, village.destinations, candidates, Set.of(), threats, state, now);
    }
    public static VillageCoverageAvailability.Reason availability(Guard guard) {
        try {
            if (!(guard instanceof com.gamergaming.villagerguardgunners.api.GuardCoverageAccess access)) {
                return VillageCoverageAvailability.Reason.DISABLED_OR_UNKNOWN;
            }
            if (access.villagerGuardGunners$hasFollowOrder()) return VillageCoverageAvailability.Reason.FOLLOWING;
            MovementOrder movement = GuardRoleResolver.effectiveOrders(guard).movement();
            return VillageCoverageAvailability.evaluate(new VillageCoverageAvailability.Context(
                    !guard.isRemoved(), guard.isPassenger() || guard.isVehicle(), nativeIdle(guard), true,
                    GuardSquadAccess.getSquadId(guard) != null, false, SquadPatrolRouteResolver.resolve(guard).isPresent(),
                    movement, guard.isPatrolling(), false, VillageDefenseStaffingRuntime.assignment(guard) != null,
                    guard.getHealth() >= guard.getMaxHealth() * 0.5F,
                    VillageDefenseWeaponSuitability.classify(guard) != VillageDefenseWeaponSuitability.WeaponClass.UNARMED,
                    GuardAutomaticDutyLiveGate.evaluate(enabled(), guard, AutomaticDutyKind.DEFENSE_POST, false)));
        } catch (RuntimeException exception) { return VillageCoverageAvailability.Reason.DISABLED_OR_UNKNOWN; }
    }
    private static boolean nativeIdle(Guard guard) {
        return guard instanceof com.gamergaming.villagerguardgunners.api.GuardCoverageAccess access
                && access.villagerGuardGunners$nativeCoverageMovementSafe();
    }
    public static Lease lease(Guard guard) {
        if (!enabled() || !(guard.level() instanceof ServerLevel level) || availability(guard) != VillageCoverageAvailability.Reason.AVAILABLE) return null;
        Dimension d = LEVELS.get(level);
        if (d == null || d.definitionsGeneration != SettlementDefinitions.snapshot().generation()) return null;
        Lease lease = d.book.forGuard(guard.getUUID());
        if (lease == null || lease.kind() != Kind.COVERAGE || lease.expiresAt() <= level.getGameTime()) return null;
        Village village = d.villages.get(lease.villageId());
        return village != null && village.terrain != null && village.terrain.usable() && village.layout != null ? lease : null;
    }
    public static Destination destination(Guard guard, Lease lease) {
        if (!(guard.level() instanceof ServerLevel level)) return null;
        Dimension d = LEVELS.get(level);
        Village v = d == null ? null : d.villages.get(lease.villageId());
        return v == null ? null : v.destinations.stream().filter(p -> p.id().equals(lease.destinationId())).findFirst().orElse(null);
    }
    public static void requestPath(Guard guard, Lease lease) {
        Dimension d = LEVELS.get((ServerLevel) guard.level());
        if (d != null && lease.equals(lease(guard))) d.requests.putIfAbsent(guard.getUUID(), lease);
    }
    public static Route takePath(Guard guard) {
        Dimension d = LEVELS.get((ServerLevel) guard.level());
        return d == null ? null : d.paths.remove(guard.getUUID());
    }
    /** Optional cache reuse after the caller acquires its own bounded search slot. */
    public static List<BlockPos> knownRoute(Guard guard, BlockPos target, int workLimit) {
        if (!(guard.level() instanceof ServerLevel level) || !enabled()) return List.of();
        Dimension d = LEVELS.get(level);
        UUID id = d == null ? null : d.guards.get(guard.getUUID());
        Village v = id == null ? null : d.villages.get(id);
        if (v == null || v.terrain == null || v.repairing || !v.terrain.usable()) return List.of();
        List<BlockPos> route = v.terrain.path(guard.blockPosition(), target, workLimit);
        for (int i = 1; i < route.size(); i++) if (!v.terrain.safeTransition(route.get(i - 1), route.get(i))) return List.of();
        return route;
    }
    public static boolean safe(Guard guard, BlockPos p) {
        Dimension d = LEVELS.get((ServerLevel) guard.level());
        UUID id = d == null ? null : d.guards.get(guard.getUUID());
        Village v = id == null ? null : d.villages.get(id);
        try { return v != null && v.terrain != null && v.terrain.usable() && v.terrain.walkable(p)
                && com.gamergaming.villagerguardgunners.ai.navigation.GuardGroundTraversal.safeStanding(guard, net.minecraft.world.phys.Vec3.atBottomCenterOf(p), true); }
        catch (RuntimeException exception) { return false; }
    }
    public static boolean safeTransition(Guard guard, BlockPos from, BlockPos to) {
        if (!(guard.level() instanceof ServerLevel level)) return false;
        Dimension d = LEVELS.get(level);
        UUID id = d == null ? null : d.guards.get(guard.getUUID());
        Village v = id == null ? null : d.villages.get(id);
        try { return v != null && v.terrain != null && v.terrain.usable() && v.terrain.safeTransition(from, to); }
        catch (RuntimeException exception) { return false; }
    }
    public static void finish(Guard guard, Lease lease, boolean arrived, boolean failed) {
        Dimension d = LEVELS.get((ServerLevel) guard.level());
        if (d == null) return;
        Village v = d.villages.get(lease.villageId());
        if (v != null) {
            long now = guard.level().getGameTime();
            if (arrived) {
                if (!v.policy.arrive(lease, BlockPos.containing(guard.getX(), Math.ceil(guard.getY()), guard.getZ()), now)) {
                    v.policy.interrupt(lease, now);
                }
            }
            else if (failed) v.policy.navigationFailed(lease, now);
            else v.policy.interrupt(lease, now);
            v.reviewAt = Math.min(v.reviewAt, now + 1);
        }
        d.requests.remove(guard.getUUID(), lease);
        d.paths.remove(guard.getUUID());
    }
    /** Called synchronously after a successful fixed-post publication, before any goal can run. */
    public static void postsPublished(ServerLevel level, List<VillageDefenseDutyAssignment> assignments) {
        if (!enabled()) return;
        Dimension d = LEVELS.computeIfAbsent(level, k -> new Dimension());
        for (var post : assignments) {
            Lease old = d.book.forGuard(post.guardId());
            if (old != null) d.book.release(old);
            d.requests.remove(post.guardId()); d.paths.remove(post.guardId());
            d.book.claim(post.networkId(), post.guardId(), post.slotId(), post.slotId(), Kind.FIXED_POST, level.getGameTime(), 400);
        }
    }
    public static void bell(ServerLevel level, BlockPos pos) {
        Dimension d = LEVELS.get(level);
        if (d == null) return;
        UUID local = VillageRegistrySavedData.get(level).alarmVillage(pos, null);
        if (local == null) return;
        for (UUID id : List.of(local)) {
            Village v = d.villages.get(id);
            if (v != null) { v.watchUntil = level.getGameTime() + ModConfigs.GUARD_GUNNERS.villageDefense.watchDurationTicks.get(); v.reviewAt = 0; }
        }
    }
    public static void onDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getAmount() <= 0 || !(event.getEntity().level() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Guard || event.getEntity() instanceof net.minecraft.world.entity.npc.Villager)) return;
        Dimension d = LEVELS.get(level);
        if (d == null || !enabled()) return;
        var registry = VillageRegistrySavedData.get(level);
        UUID affiliation = event.getEntity() instanceof Guard ? registry.guardVillage(event.getEntity().getUUID()) : null;
        UUID local = registry.alarmVillage(event.getEntity().blockPosition(), affiliation);
        if (local == null) return;
        for (UUID id : List.of(local)) {
            Village v = d.villages.get(id);
            if (v == null) continue;
            v.alertUntil = level.getGameTime() + ModConfigs.GUARD_GUNNERS.villageDefense.alertDurationTicks.get();
            v.recoveryUntil = v.alertUntil + ModConfigs.GUARD_GUNNERS.villageDefense.recoveryDurationTicks.get();
            if (v.threats.size() < 16) v.threats.add(event.getEntity().blockPosition().immutable());
            v.reviewAt = 0;
        }
    }
    public static void onBlockChange(BlockEvent event) {
        if ((event instanceof BlockEvent.BreakEvent || event instanceof BlockEvent.EntityPlaceEvent
                || event instanceof BlockEvent.FluidPlaceBlockEvent) && event.getLevel() instanceof ServerLevel level) {
            dirty(level, net.minecraft.world.level.ChunkPos.asLong(event.getPos()));
        }
    }
    public static void onChunkChange(ChunkEvent event) {
        if ((event instanceof ChunkEvent.Load || event instanceof ChunkEvent.Unload)
                && event.getLevel() instanceof ServerLevel level) dirty(level, event.getChunk().getPos().toLong());
    }
    public static void dirty(ServerLevel level, long chunk) {
        if (!level.getServer().isSameThread()) { level.getServer().execute(() -> dirty(level, chunk)); return; }
        Dimension d = LEVELS.get(level);
        if (d != null) for (Village v : d.villages.values()) {
            if ((v.terrain != null && v.terrain.chunks().contains(chunk))
                    || (v.pendingTerrain != null && v.pendingTerrain.chunks().contains(chunk))) {
                if (v.terrain == null) { v.pendingTerrain = null; continue; }
                if (!v.queuedChanges.add(chunk)) continue;
                v.terrain.invalidateChunk(chunk);
                v.repairing = true;
                for (Destination destination : v.destinations)
                    if (v.terrain.affected(destination.position())) v.changedDestinations.add(destination.id());
                for (Lease lease : d.book.forVillage(v.id)) {
                    if (lease.kind() != Kind.COVERAGE) continue;
                    boolean affected = v.changedDestinations.contains(lease.destinationId());
                    var entity = level.getEntity(lease.guardId());
                    if (entity instanceof Guard guard) {
                        var path = guard.getNavigation().getPath();
                        if (path != null) for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++)
                            affected |= v.terrain.affected(path.getNodePos(i));
                    }
                    Route queued = d.paths.get(lease.guardId());
                    if (queued != null) affected |= queued.positions().stream().anyMatch(v.terrain::affected);
                    if (affected) {
                        v.policy.interrupt(lease, level.getGameTime());
                        d.requests.remove(lease.guardId()); d.paths.remove(lease.guardId());
                    }
                }
            }
        }
    }
    private static void invalidate(Dimension d, Village v) {
        for (Lease lease : d.book.forVillage(v.id)) if (lease.kind() == Kind.COVERAGE) {
            d.book.release(lease); d.requests.remove(lease.guardId()); d.paths.remove(lease.guardId());
        }
        v.layout = null; v.plan = null; v.destinations = List.of(); v.repairing = false; v.changedDestinations.clear(); v.queuedChanges.clear();
    }
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Guard guard) remove(level, guard.getUUID());
    }
    private static void remove(ServerLevel level, UUID guard) {
        Dimension d = LEVELS.get(level);
        if (d == null) return;
        UUID village = d.guards.remove(guard);
        Lease lease = d.book.forGuard(guard);
        if (lease != null) d.book.release(lease);
        d.requests.remove(guard); d.paths.remove(guard);
        if (village != null) removeFromVillage(d, village, guard);
    }
    private static void removeFromVillage(Dimension d, UUID id, UUID guard) {
        Village v = d.villages.get(id);
        if (v != null) { v.guards.remove(guard); if (v.guards.isEmpty()) { v.policy.clear(); d.villages.remove(id); } }
    }
    public static void clear(ServerLevel level) { Dimension d = LEVELS.remove(level); if (d != null) d.book.clear(); }
    public static void onLevelUnload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) clear(level); }
    public static void onServerStopping(ServerStoppingEvent event) { LEVELS.clear(); }
    public static Snapshot snapshot(ServerLevel level, UUID village) {
        Dimension d = LEVELS.get(level);
        Village v = d == null ? null : d.villages.get(village);
        return new Snapshot(v == null ? 0 : v.guards.size(), v == null ? null : v.plan,
                v == null ? "inactive" : level.getGameTime() < v.retryAt ? "observation_error" : v.terrain == null || !v.terrain.complete() ? "surveying"
                : v.repairing ? "repairing" : v.pendingTerrain != null ? "refreshing" : v.terrain.limited() ? "terrain_limit" : v.layout == null ? "unresolved" : v.layout.status().name(),
                v == null ? VillageDefenseState.PEACE : v.readiness,
                d == null ? 0 : d.lastTerrainUnits, d == null ? 0 : d.lastPaths, d == null ? 0 : d.rejections);
    }
    public static long unchangedRefreshes(ServerLevel level, UUID village) {
        Dimension d = LEVELS.get(level);
        Village v = d == null ? null : d.villages.get(village);
        return v == null ? 0 : v.unchangedRefreshes;
    }
    public record Snapshot(int loadedGuards, Plan plan, String status, VillageDefenseState readiness, int terrainUnits, int paths, long rejections) { }
    public record Route(Lease lease, List<BlockPos> positions) { }
    private static final class Dimension {
        final long definitionsGeneration = SettlementDefinitions.snapshot().generation();
        final VillageCoverageReservations book = new VillageCoverageReservations();
        final LinkedHashMap<UUID, Village> villages = new LinkedHashMap<>();
        final Map<UUID, UUID> guards = new HashMap<>();
        final LinkedHashMap<UUID, Lease> requests = new LinkedHashMap<>();
        final Map<UUID, Route> paths = new HashMap<>();
        int lastTerrainUnits, lastPaths;
        long rejections;
    }
    private static final class Village {
        final UUID id;
        final VillageCoveragePolicy policy;
        final Set<UUID> guards = new LinkedHashSet<>();
        final Set<BlockPos> threats = new LinkedHashSet<>();
        CoverageTerrain terrain, pendingTerrain;
        boolean repairing;
        final Set<UUID> changedDestinations = new HashSet<>();
        final Set<Long> queuedChanges = new HashSet<>();
        VillageFootprint footprint;
        VillageCoverageLayout layout;
        List<Destination> destinations = List.of();
        Plan plan;
        long recordRevision, pendingRevision, publication, reviewAt, rebuildAt, alertUntil, recoveryUntil, watchUntil, retryAt, unchangedRefreshes;
        VillageDefenseState readiness = VillageDefenseState.PEACE;
        Village(UUID id, VillageCoverageReservations book) { this.id = id; policy = new VillageCoveragePolicy(id, book, Settings.DEFAULT); }
    }
}
