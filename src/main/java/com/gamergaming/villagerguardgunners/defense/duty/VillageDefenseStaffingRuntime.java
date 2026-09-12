package com.gamergaming.villagerguardgunners.defense.duty;

import com.gamergaming.villagerguardgunners.api.GuardPostAccess;
import com.gamergaming.villagerguardgunners.defense.VillageDefenseRuntimePolicy;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRuntimePolicy;
import com.gamergaming.villagerguardgunners.squad.SquadPatrolRouteResolver;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingPlanner.Candidate;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseStaffingPlanner.Plan;
import com.gamergaming.villagerguardgunners.defense.duty.VillageDefenseWeaponSuitability.WeaponClass;
import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetwork;
import com.gamergaming.villagerguardgunners.defense.network.VillageDefenseNetworkSavedData;
import com.gamergaming.villagerguardgunners.worldgen.post.GuardPostIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import tallestegg.guardvillagers.entities.Guard;

/**
 * Transient loaded-guard staffing index. It uses no entity scans, chunk loads,
 * or saved-data writes and reviews only explicitly queued networks.
 */
public final class VillageDefenseStaffingRuntime {
    public static final int MAXIMUM_LOADED_GUARDS_PER_DIMENSION = 4_096;
    public static final int MAXIMUM_REVIEWS_PER_TICK = 32;
    public static final long UNASSIGNED_NETWORK_RECHECK_TICKS = 200L;
    public static final long INVALID_SLOT_RECHECK_TICKS = 200L;

    private static final Map<MinecraftServer, ServerRuntime> SERVERS =
            new IdentityHashMap<>();

    private VillageDefenseStaffingRuntime() {
    }

    public static synchronized void onServerStarted(ServerStartedEvent event) {
        SERVERS.remove(event.getServer());
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getLevel() instanceof ServerLevel
                && event.getEntity() instanceof Guard guard) {
            synchronizeGuard(guard);
        }
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof Guard guard) {
            removeGuard(level, guard.getUUID());
        }
    }

    /** Called at a staggered cadence by the guard mixin. */
    public static synchronized void synchronizeGuard(Guard guard) {
        Objects.requireNonNull(guard, "guard");
        if (!(guard.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) {
            server.execute(() -> synchronizeGuard(guard));
            return;
        }
        DimensionRuntime runtime = dimension(server, level, true);
        if (runtime == null) {
            return;
        }
        UUID guardId = guard.getUUID();
        GuardEntry existing = runtime.guards.get(guardId);
        if (guard.isRemoved()
                || guard.isDeadOrDying()
                || guard.isNoAi()
                || com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard)
                || !VillageDefenseRuntimePolicy.automaticDutiesEnabled()) {
            removeGuard(level, guardId);
            return;
        }
        if (existing == null
                && runtime.guards.size() >= MAXIMUM_LOADED_GUARDS_PER_DIMENSION) {
            runtime.guardLimitRejections = increment(runtime.guardLimitRejections);
            return;
        }

        long chunkKey = ChunkPos.asLong(guard.blockPosition());
        WeaponClass weaponClass = VillageDefenseWeaponSuitability.classify(guard);
        boolean patrolRouteAssigned = SquadPatrolRuntimePolicy.executionEnabled()
                && SquadPatrolRouteResolver.hasLoadedWaypoint(guard);
        VillageDefenseNetworkSavedData networks = VillageDefenseNetworkSavedData.get(level);
        GuardPostIdentity postIdentity = GuardPostAccess.getIdentity(guard);
        UUID preferredBuildingId = postIdentity == null ? null : postIdentity.postId();
        UUID oldNetworkId = existing == null ? null : existing.networkId;
        UUID networkId = oldNetworkId == null
                ? null
                : networks.resolveNetworkId(oldNetworkId);
        long gameTime = level.getGameTime();
        boolean postIdentityChanged = existing != null
                && !Objects.equals(
                        existing.preferredBuildingId, preferredBuildingId);
        boolean lookupDue = existing == null
                || existing.chunkKey != chunkKey
                || postIdentityChanged
                || networkId == null && gameTime >= existing.nextNetworkLookupGameTime;
        if (lookupDue) {
            networkId = resolveNetworkId(networks, guard);
        }
        BlockPos position = guard.blockPosition().immutable();

        if (existing == null) {
            existing = new GuardEntry(
                    guard, position, chunkKey, weaponClass, networkId,
                    preferredBuildingId, nextNetworkLookup(gameTime, networkId),
                    patrolRouteAssigned);
            runtime.guards.put(guardId, existing);
            if (!patrolRouteAssigned) {
                addCandidateToNetwork(runtime, networkId, guardId, weaponClass);
            }
            requestReview(level, networkId);
            return;
        }

        boolean networkChanged = !Objects.equals(oldNetworkId, networkId);
        boolean weaponChanged = existing.weaponClass != weaponClass;
        boolean patrolRouteChanged =
                existing.patrolRouteAssigned != patrolRouteAssigned;
        existing.guard = guard;
        existing.position = position;
        existing.chunkKey = chunkKey;
        existing.weaponClass = weaponClass;
        existing.preferredBuildingId = preferredBuildingId;
        existing.patrolRouteAssigned = patrolRouteAssigned;
        existing.nextNetworkLookupGameTime = updatedNetworkLookupDeadline(
                existing.nextNetworkLookupGameTime,
                lookupDue,
                networkChanged,
                networkId,
                gameTime);
        if (networkChanged || weaponChanged || patrolRouteChanged) {
            removeCandidateFromNetwork(runtime, oldNetworkId, guardId);
            existing.networkId = networkId;
            if (!patrolRouteAssigned) {
                addCandidateToNetwork(runtime, networkId, guardId, weaponClass);
            }
        }
        if (networkChanged || weaponChanged || patrolRouteChanged) {
            VillageDefenseDutyAssignment released = runtime.reservations.releaseGuard(guardId);
            requestReview(level, oldNetworkId);
            requestReview(level, networkId);
            if (released != null) {
                requestReview(level, released.networkId());
            }
        }
    }

    public static synchronized @Nullable VillageDefenseDutyAssignment assignment(
            Guard guard) {
        Objects.requireNonNull(guard, "guard");
        if (!VillageDefenseRuntimePolicy.automaticDutiesEnabled()
                || com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(guard)
                || !(guard.level() instanceof ServerLevel level)) {
            return null;
        }
        DimensionRuntime runtime = dimension(level.getServer(), level, false);
        return runtime == null ? null : runtime.reservations.forGuard(guard.getUUID());
    }

    public static synchronized void requestReview(
            ServerLevel level,
            @Nullable UUID networkId) {
        Objects.requireNonNull(level, "level");
        if (networkId == null
                || !VillageDefenseRuntimePolicy.automaticDutiesEnabled()) {
            return;
        }
        VillageDefenseNetworkSavedData data = VillageDefenseNetworkSavedData.get(level);
        UUID canonical = data.resolveNetworkId(networkId);
        if (canonical == null) {
            return;
        }
        DimensionRuntime runtime = dimension(level.getServer(), level, true);
        if (runtime != null) {
            runtime.pendingNetworks.add(canonical);
        }
    }

    public static synchronized void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !VillageDefenseRuntimePolicy.automaticDutiesEnabled()) {
            return;
        }
        ServerRuntime serverRuntime = SERVERS.get(event.getServer());
        if (serverRuntime == null) {
            return;
        }
        List<Map.Entry<ResourceKey<Level>, DimensionRuntime>> dimensions =
                new ArrayList<>(serverRuntime.dimensions.entrySet());
        if (dimensions.isEmpty()) {
            return;
        }
        for (Map.Entry<ResourceKey<Level>, DimensionRuntime> entry : dimensions) {
            ServerLevel level = event.getServer().getLevel(entry.getKey());
            if (level != null) {
                enqueueDueShifts(entry.getValue(), level.getGameTime());
            }
        }
        int start = Math.floorMod(serverRuntime.dimensionCursor, dimensions.size());
        int remaining = MAXIMUM_REVIEWS_PER_TICK;
        boolean progressed;
        do {
            progressed = false;
            for (int offset = 0; offset < dimensions.size() && remaining > 0; offset++) {
                Map.Entry<ResourceKey<Level>, DimensionRuntime> entry = dimensions.get(
                        (start + offset) % dimensions.size());
                ServerLevel level = event.getServer().getLevel(entry.getKey());
                DimensionRuntime runtime = entry.getValue();
                if (level == null || runtime.pendingNetworks.isEmpty()) {
                    continue;
                }
                UUID networkId = runtime.pendingNetworks.iterator().next();
                runtime.pendingNetworks.remove(networkId);
                reviewNetwork(level, runtime, networkId);
                remaining--;
                progressed = true;
            }
        } while (progressed && remaining > 0);
        serverRuntime.dimensionCursor = (start + 1) % dimensions.size();
    }

    public static synchronized void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ServerRuntime runtime = SERVERS.get(level.getServer());
        if (runtime != null) {
            runtime.dimensions.remove(level.dimension());
            if (runtime.dimensions.isEmpty()) {
                SERVERS.remove(level.getServer());
            }
        }
    }

    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        SERVERS.remove(event.getServer());
    }

    public static synchronized Diagnostics diagnostics(ServerLevel level) {
        DimensionRuntime runtime = dimension(level.getServer(), level, false);
        return runtime == null
                ? Diagnostics.EMPTY
                : new Diagnostics(
                        runtime.guards.size(),
                        runtime.reservations.size(),
                        runtime.pendingNetworks.size(),
                        runtime.completedReviews,
                        runtime.guardLimitRejections);
    }

    public static synchronized NetworkDiagnostics networkDiagnostics(
            ServerLevel level,
            UUID networkId) {
        DimensionRuntime runtime = dimension(level.getServer(), level, false);
        if (runtime == null) {
            return NetworkDiagnostics.EMPTY;
        }
        VillageDefenseNetworkSavedData data = VillageDefenseNetworkSavedData.get(level);
        UUID canonical = data.resolveNetworkId(networkId);
        if (canonical == null) {
            return NetworkDiagnostics.EMPTY;
        }
        Set<UUID> guards = runtime.candidateGuardsByNetwork.get(canonical);
        List<VillageDefenseDutyAssignment> assignments =
                runtime.reservations.forNetwork(canonical);
        return new NetworkDiagnostics(
                guards == null ? 0 : guards.size(), assignments.size());
    }

    private static void reviewNetwork(
            ServerLevel level,
            DimensionRuntime runtime,
            UUID requestedNetworkId) {
        VillageDefenseNetworkSavedData networks = VillageDefenseNetworkSavedData.get(level);
        UUID networkId = networks.resolveNetworkId(requestedNetworkId);
        VillageDefenseNetwork network = networkId == null
                ? null
                : networks.getNetwork(networkId);
        if (network == null) {
            runtime.reservations.releaseNetwork(requestedNetworkId);
            cancelShift(runtime, requestedNetworkId);
            return;
        }
        reconcileAliases(runtime, networks, networkId);
        List<UUID> buildingIds = network.buildings().stream()
                .map(identity -> identity.buildingId())
                .toList();
        List<VillageDefenseDutySlot> recordedSlots = VillageDefenseDutySavedData.get(level)
                .slotsForBuildings(buildingIds, VillageDefenseStaffingPlanner.MAXIMUM_SLOTS);
        SlotScreen slotScreen = screenSlots(level, recordedSlots);
        List<VillageDefenseDutySlot> slots = slotScreen.usableSlots();
        Set<UUID> indexedGuards = runtime.candidateGuardsByNetwork.get(networkId);
        List<Candidate> candidates = new ArrayList<>();
        if (indexedGuards != null) {
            indexedGuards.stream()
                    .map(runtime.guards::get)
                    .filter(Objects::nonNull)
                    .filter(entry -> entry.guard.isAlive()
                            && !entry.guard.isRemoved()
                            && !entry.guard.isNoAi()
                            && !com.gamergaming.villagerguardgunners.medical.GuardLifeRuntime.blocked(entry.guard))
                    .map(entry -> new Candidate(
                            entry.guard.getUUID(),
                            entry.position,
                            entry.weaponClass,
                            entry.preferredBuildingId))
                    .map(candidate -> new RankedCandidate(
                            candidate,
                            VillageDefenseStaffingPlanner
                                    .nearestActiveSlotDistanceSquared(
                                            candidate,
                                            network.defenseStatus().state(),
                                            slots)))
                    .filter(candidate -> Double.isFinite(candidate.distanceSquared()))
                    .sorted(Comparator
                            .comparing((RankedCandidate candidate) ->
                                    candidate.candidate().weaponClass()
                                            != WeaponClass.RANGED)
                            .thenComparingDouble(RankedCandidate::distanceSquared)
                            .thenComparing(candidate -> candidate.candidate().guardId()))
                    .limit(VillageDefenseStaffingPlanner.MAXIMUM_CANDIDATES)
                    .map(RankedCandidate::candidate)
                    .forEach(candidates::add);
        }
        Plan plan = VillageDefenseStaffingPlanner.plan(
                networkId,
                network.defenseStatus().state(),
                slots,
                candidates,
                runtime.reservations.forNetwork(networkId),
                level.getGameTime());
        if (runtime.reservations.replaceNetwork(networkId, plan.assignments())) {
            com.gamergaming.villagerguardgunners.village.coverage.live.VillageCoverageRuntime.postsPublished(level, plan.assignments());
        }
        scheduleNextShift(
                runtime,
                networkId,
                plan.assignments(),
                slotScreen.invalidLoadedSlot()
                        ? saturatingAdd(level.getGameTime(), INVALID_SLOT_RECHECK_TICKS)
                        : VillageDefenseDutyAssignment.NO_SHIFT_END);
        runtime.completedReviews = increment(runtime.completedReviews);
    }

    static SlotScreen screenSlots(
            ServerLevel level,
            List<VillageDefenseDutySlot> recordedSlots) {
        List<VillageDefenseDutySlot> usableSlots = new ArrayList<>(recordedSlots.size());
        boolean invalidLoadedSlot = false;
        for (VillageDefenseDutySlot slot : recordedSlots) {
            VillageDefenseDutyDestination.Status status =
                    VillageDefenseDutyDestination.evaluate(level, slot.position());
            if (status.usableForStaffing()) {
                usableSlots.add(slot);
            } else if (status.invalidWhileLoaded()) {
                invalidLoadedSlot = true;
            }
        }
        return new SlotScreen(List.copyOf(usableSlots), invalidLoadedSlot);
    }

    private static void reconcileAliases(
            DimensionRuntime runtime,
            VillageDefenseNetworkSavedData networks,
            UUID canonicalNetworkId) {
        List<UUID> aliases = runtime.candidateGuardsByNetwork.keySet().stream()
                .filter(id -> !canonicalNetworkId.equals(id))
                .filter(id -> canonicalNetworkId.equals(networks.resolveNetworkId(id)))
                .toList();
        for (UUID alias : aliases) {
            NavigableSet<UUID> guardIds = runtime.candidateGuardsByNetwork.remove(alias);
            if (guardIds == null) {
                continue;
            }
            NavigableSet<UUID> canonical =
                    runtime.candidateGuardsByNetwork.computeIfAbsent(
                            canonicalNetworkId, ignored -> new TreeSet<>());
            for (UUID guardId : guardIds) {
                GuardEntry guard = runtime.guards.get(guardId);
                if (guard != null) {
                    guard.networkId = canonicalNetworkId;
                    canonical.add(guardId);
                }
            }
            runtime.reservations.releaseNetwork(alias);
            cancelShift(runtime, alias);
        }
    }

    private static @Nullable UUID resolveNetworkId(
            VillageDefenseNetworkSavedData networks,
            Guard guard) {
        GuardPostIdentity identity = GuardPostAccess.getIdentity(guard);
        if (identity != null) {
            VillageDefenseNetwork postNetwork = networks.networkForBuilding(
                    identity.postId());
            return postNetwork == null ? null : postNetwork.networkId();
        }
        VillageDefenseNetwork network = networks.findNearest(
                guard.blockPosition(),
                VillageDefenseNetworkSavedData.DEFAULT_LINK_RADIUS);
        return network == null ? null : network.networkId();
    }

    private static long nextNetworkLookup(long gameTime, @Nullable UUID networkId) {
        if (networkId != null) {
            return Long.MAX_VALUE;
        }
        return gameTime > Long.MAX_VALUE - UNASSIGNED_NETWORK_RECHECK_TICKS
                ? Long.MAX_VALUE
                : gameTime + UNASSIGNED_NETWORK_RECHECK_TICKS;
    }

    static long updatedNetworkLookupDeadline(
            long currentDeadline,
            boolean lookupDue,
            boolean networkChanged,
            @Nullable UUID networkId,
            long gameTime) {
        if (!lookupDue && !(networkChanged && networkId == null)) {
            return currentDeadline;
        }
        return nextNetworkLookup(gameTime, networkId);
    }

    private static long increment(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static long saturatingAdd(long value, long amount) {
        return amount > 0L && value > Long.MAX_VALUE - amount
                ? Long.MAX_VALUE
                : value + amount;
    }

    private static void enqueueDueShifts(DimensionRuntime runtime, long gameTime) {
        while (!runtime.shiftsByTime.isEmpty()
                && runtime.shiftsByTime.firstKey() <= gameTime) {
            Map.Entry<Long, LinkedHashSet<UUID>> due =
                    runtime.shiftsByTime.pollFirstEntry();
            for (UUID networkId : due.getValue()) {
                if (Long.valueOf(due.getKey()).equals(
                        runtime.nextShiftByNetwork.get(networkId))) {
                    runtime.nextShiftByNetwork.remove(networkId);
                    runtime.pendingNetworks.add(networkId);
                }
            }
        }
    }

    private static void scheduleNextShift(
            DimensionRuntime runtime,
            UUID networkId,
            List<VillageDefenseDutyAssignment> assignments,
            long additionalReviewTime) {
        cancelShift(runtime, networkId);
        long nextShift = assignments.stream()
                .mapToLong(VillageDefenseDutyAssignment::shiftEndGameTime)
                .filter(time -> time != VillageDefenseDutyAssignment.NO_SHIFT_END)
                .min()
                .orElse(VillageDefenseDutyAssignment.NO_SHIFT_END);
        if (additionalReviewTime != VillageDefenseDutyAssignment.NO_SHIFT_END
                && (nextShift == VillageDefenseDutyAssignment.NO_SHIFT_END
                        || additionalReviewTime < nextShift)) {
            nextShift = additionalReviewTime;
        }
        if (nextShift == VillageDefenseDutyAssignment.NO_SHIFT_END) {
            return;
        }
        runtime.nextShiftByNetwork.put(networkId, nextShift);
        runtime.shiftsByTime.computeIfAbsent(
                        nextShift, ignored -> new LinkedHashSet<>())
                .add(networkId);
    }

    private static void cancelShift(DimensionRuntime runtime, UUID networkId) {
        Long oldTime = runtime.nextShiftByNetwork.remove(networkId);
        if (oldTime == null) {
            return;
        }
        LinkedHashSet<UUID> bucket = runtime.shiftsByTime.get(oldTime);
        if (bucket != null) {
            bucket.remove(networkId);
            if (bucket.isEmpty()) {
                runtime.shiftsByTime.remove(oldTime);
            }
        }
    }

    private static void removeGuard(ServerLevel level, UUID guardId) {
        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) {
            server.execute(() -> removeGuard(level, guardId));
            return;
        }
        synchronized (VillageDefenseStaffingRuntime.class) {
            DimensionRuntime runtime = dimension(server, level, false);
            if (runtime == null) {
                return;
            }
            GuardEntry removed = runtime.guards.remove(guardId);
            if (removed != null) {
                removeCandidateFromNetwork(runtime, removed.networkId, guardId);
                requestReview(level, removed.networkId);
            }
            VillageDefenseDutyAssignment assignment =
                    runtime.reservations.releaseGuard(guardId);
            if (assignment != null) {
                requestReview(level, assignment.networkId());
            }
        }
    }

    private static void addCandidateToNetwork(
            DimensionRuntime runtime,
            @Nullable UUID networkId,
            UUID guardId,
            WeaponClass weaponClass) {
        if (networkId != null && weaponClass != WeaponClass.UNARMED) {
            runtime.candidateGuardsByNetwork.computeIfAbsent(
                            networkId, ignored -> new TreeSet<>())
                    .add(guardId);
        }
    }

    private static void removeCandidateFromNetwork(
            DimensionRuntime runtime,
            @Nullable UUID networkId,
            UUID guardId) {
        if (networkId == null) {
            return;
        }
        NavigableSet<UUID> guards = runtime.candidateGuardsByNetwork.get(networkId);
        if (guards != null) {
            guards.remove(guardId);
            if (guards.isEmpty()) {
                runtime.candidateGuardsByNetwork.remove(networkId);
            }
        }
    }

    private static @Nullable DimensionRuntime dimension(
            MinecraftServer server,
            ServerLevel level,
            boolean create) {
        ServerRuntime serverRuntime = create
                ? SERVERS.computeIfAbsent(server, ignored -> new ServerRuntime())
                : SERVERS.get(server);
        if (serverRuntime == null) {
            return null;
        }
        return create
                ? serverRuntime.dimensions.computeIfAbsent(
                        level.dimension(), ignored -> new DimensionRuntime())
                : serverRuntime.dimensions.get(level.dimension());
    }

    private static final class ServerRuntime {
        private final Map<ResourceKey<Level>, DimensionRuntime> dimensions =
                new LinkedHashMap<>();
        private int dimensionCursor;
    }

    private record RankedCandidate(Candidate candidate, double distanceSquared) {
        private RankedCandidate {
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    record SlotScreen(
            List<VillageDefenseDutySlot> usableSlots,
            boolean invalidLoadedSlot) {
    }

    private static final class DimensionRuntime {
        private final Map<UUID, GuardEntry> guards = new HashMap<>();
        private final Map<UUID, NavigableSet<UUID>> candidateGuardsByNetwork =
                new HashMap<>();
        private final VillageDefenseDutyReservationBook reservations =
                new VillageDefenseDutyReservationBook();
        private final LinkedHashSet<UUID> pendingNetworks = new LinkedHashSet<>();
        private final NavigableMap<Long, LinkedHashSet<UUID>> shiftsByTime =
                new TreeMap<>();
        private final Map<UUID, Long> nextShiftByNetwork = new HashMap<>();
        private long completedReviews;
        private long guardLimitRejections;
    }

    private static final class GuardEntry {
        private Guard guard;
        private BlockPos position;
        private long chunkKey;
        private WeaponClass weaponClass;
        @Nullable
        private UUID networkId;
        @Nullable
        private UUID preferredBuildingId;
        private long nextNetworkLookupGameTime;
        private boolean patrolRouteAssigned;

        private GuardEntry(
                Guard guard,
                BlockPos position,
                long chunkKey,
                WeaponClass weaponClass,
                @Nullable UUID networkId,
                @Nullable UUID preferredBuildingId,
                long nextNetworkLookupGameTime,
                boolean patrolRouteAssigned) {
            this.guard = guard;
            this.position = position;
            this.chunkKey = chunkKey;
            this.weaponClass = weaponClass;
            this.networkId = networkId;
            this.preferredBuildingId = preferredBuildingId;
            this.nextNetworkLookupGameTime = nextNetworkLookupGameTime;
            this.patrolRouteAssigned = patrolRouteAssigned;
        }
    }

    public record Diagnostics(
            int loadedGuards,
            int reservations,
            int pendingNetworks,
            long completedReviews,
            long guardLimitRejections) {
        public static final Diagnostics EMPTY = new Diagnostics(0, 0, 0, 0L, 0L);

        public Diagnostics {
            if (loadedGuards < 0
                    || reservations < 0
                    || pendingNetworks < 0
                    || completedReviews < 0L
                    || guardLimitRejections < 0L) {
                throw new IllegalArgumentException("Staffing diagnostics cannot be negative");
            }
        }
    }

    public record NetworkDiagnostics(int candidateGuards, int assignments) {
        public static final NetworkDiagnostics EMPTY = new NetworkDiagnostics(0, 0);

        public NetworkDiagnostics {
            if (candidateGuards < 0 || assignments < 0) {
                throw new IllegalArgumentException(
                        "Network staffing diagnostics cannot be negative");
            }
        }
    }
}
