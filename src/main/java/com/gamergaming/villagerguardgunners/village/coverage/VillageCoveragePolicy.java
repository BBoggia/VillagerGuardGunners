package com.gamergaming.villagerguardgunners.village.coverage;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseState;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageAvailability.Reason;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageLayout.Area;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageReservations.Kind;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageReservations.Lease;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/**
 * Bounded coverage decisions over explicit observations. Owns no world, entity,
 * navigation or saved-data references. Callers publish a complete village view.
 */
public final class VillageCoveragePolicy {
    public static final int MAX_CANDIDATES = 128;
    private final UUID villageId;
    private final VillageCoverageReservations reservations;
    private final Settings settings;
    private final Map<UUID, AreaState> areas = new HashMap<>();
    private final Map<UUID, Failure> failures = new HashMap<>();
    private final Map<UUID, Long> destinationOffers = new HashMap<>();
    private Map<UUID, Destination> destinations = Map.of();
    private Map<UUID, Area> areasToLayout = Map.of();
    private Set<UUID> criticalAreas = Set.of();
    private long lastTime = -1;
    private long latestPatrolRound;
    private long layoutRevision;

    public VillageCoveragePolicy(UUID villageId, VillageCoverageReservations reservations, Settings settings) {
        this.villageId = Objects.requireNonNull(villageId, "villageId");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Reachability components and destination states must come from loaded terrain
     * validation; cell adjacency is insufficient. Unknown observations cannot schedule.
     * Input validation finishes before any previous state or reservation is changed.
     */
    public Plan schedule(VillageCoverageLayout layout, Collection<Destination> observedDestinations,
            Collection<Candidate> observedCandidates, Set<UUID> critical, Set<UUID> threats,
            VillageDefenseState readiness, long now) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(readiness, "readiness");
        checkTime(now);
        if (layout.revision() < layoutRevision || critical.size() > VillageCoverageLayout.MAX_AREAS
                || threats.size() > VillageCoverageLayout.MAX_AREAS
                || !villageId.equals(layout.villageId())
                || observedDestinations.size() > VillageCoverageLayout.MAX_DESTINATIONS
                || observedCandidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("Coverage publication exceeds its scope or bound");
        }
        Map<UUID, Area> nextAreas = new HashMap<>();
        layout.areas().forEach(area -> nextAreas.put(area.id(), area));
        Set<UUID> nextCritical = Set.copyOf(critical);
        Set<UUID> nextThreats = Set.copyOf(threats);
        if (!nextAreas.keySet().containsAll(nextCritical) || !nextAreas.keySet().containsAll(nextThreats)) {
            throw new IllegalArgumentException("Unknown priority area");
        }
        Map<UUID, Destination> nextDestinations = new HashMap<>();
        Map<UUID, UUID> componentsByArea = new HashMap<>();
        for (Destination destination : observedDestinations) {
            Area area = nextAreas.get(destination.areaId());
            if (area == null || !area.contains(destination.position())
                    || nextDestinations.put(destination.id(), destination) != null) {
                throw new IllegalArgumentException("Invalid or duplicate destination");
            }
            if (destination.state() == DestinationState.READY) {
                UUID previous = componentsByArea.putIfAbsent(destination.areaId(), destination.component());
                if ((previous != null && !previous.equals(destination.component()))
                        || (area.component() != null && !area.component().equals(destination.component()))) {
                    throw new IllegalArgumentException("Split disconnected terrain into separate coverage areas");
                }
            }
        }
        Map<UUID, Candidate> candidates = new HashMap<>();
        for (Candidate candidate : observedCandidates) {
            if (candidates.put(candidate.guardId(), candidate) != null) {
                throw new IllegalArgumentException("Duplicate candidate");
            }
        }

        Map<UUID, Destination> previousDestinations = destinations;
        advance(now);
        // Failed destinations are retried only after cooldown or new terrain evidence.
        failures.entrySet().removeIf(entry -> !Objects.equals(
                destinations.get(entry.getKey()), nextDestinations.get(entry.getKey())));
        destinationOffers.keySet().retainAll(nextDestinations.keySet());
        destinations = Map.copyOf(nextDestinations);
        areasToLayout = Map.copyOf(nextAreas);
        layoutRevision = layout.revision();
        Set<UUID> previousCritical = criticalAreas;
        long currentCriticalRound = areas.entrySet().stream()
                .filter(entry -> previousCritical.contains(entry.getKey()) && nextCritical.contains(entry.getKey()))
                .mapToLong(entry -> entry.getValue().offered).max().orElse(0);
        criticalAreas = nextCritical;
        areas.keySet().retainAll(nextAreas.keySet());
        for (Area area : layout.areas()) {
            AreaState state = areas.computeIfAbsent(area.id(), key -> new AreaState(now));
            long groupRound = criticalAreas.contains(area.id()) ? currentCriticalRound : latestPatrolRound;
            if (previousCritical.contains(area.id()) != criticalAreas.contains(area.id())) {
                state.offered = groupRound;
            }
            boolean loaded = destinations.values().stream().anyMatch(destination ->
                    destination.areaId().equals(area.id()) && destination.state() != DestinationState.UNLOADED);
            if (loaded && !state.loaded) {
                // Start at the back of the current rotation, with no offline age windfall.
                state.age = 0;
                state.offered = groupRound;
                state.reentryUntil = deadline(now, settings.reentryTicks());
            }
            state.loaded = loaded;
        }

        for (Lease lease : ownLeases()) {
            Candidate candidate = candidates.get(lease.guardId());
            Destination destination = destinations.get(lease.destinationId());
            if (lease.expiresAt() <= now
                    || !Objects.equals(previousDestinations.get(lease.destinationId()), destination)
                    || !eligible(candidate, destination, now)
                    || !lease.areaId().equals(destination.areaId())) {
                reservations.release(lease);
            }
        }

        EnumMap<Reason, Integer> unavailable = new EnumMap<>(Reason.class);
        List<Candidate> free = new ArrayList<>();
        for (Candidate candidate : candidates.values()) {
            Lease claim = reservations.forGuard(candidate.guardId());
            if (claim != null && claim.expiresAt() <= now) {
                reservations.release(claim);
                claim = null;
            }
            if (candidate.availability() != Reason.AVAILABLE) {
                unavailable.merge(candidate.availability(), 1, Integer::sum);
            } else if (claim == null) {
                free.add(candidate);
            } else if (claim.kind() != Kind.COVERAGE || !claim.villageId().equals(villageId)) {
                unavailable.merge(Reason.OTHER_DUTY, 1, Integer::sum);
            }
        }
        free.sort(Comparator.comparing(Candidate::guardId));
        Set<UUID> assigned = new HashSet<>();
        ownLeases().forEach(lease -> assigned.add(lease.areaId()));
        Set<UUID> limitAreas = new HashSet<>();
        long latestCriticalRound = areas.entrySet().stream().filter(entry -> criticalAreas.contains(entry.getKey()))
                .mapToLong(entry -> entry.getValue().offered).max().orElse(0);
        areas.forEach((id, state) -> {
            long latest = criticalAreas.contains(id) ? latestCriticalRound : latestPatrolRound;
            // Blocked areas cannot bank hundreds of catch-up offers while the
            // reachable village rotates. At most one old round remains owed.
            state.offered = Math.max(state.offered, Math.max(0, latest - 1));
        });
        // Existing leases provide hysteresis but cannot renew their own deadline.
        List<Area> ordered = layout.areas().stream().sorted(areaOrder(nextThreats, readiness, now)).toList();
        allocate(ordered.stream().filter(area -> criticalAreas.contains(area.id())
                && !assigned.contains(area.id())).toList(), free, assigned, limitAreas, free.size(), now);
        int reserveCount = readiness.isConfirmedThreatState()
                ? Math.min(settings.reserveGuards(), Math.max(0, free.size() - 1)) : 0;
        allocate(ordered.stream().filter(area -> !criticalAreas.contains(area.id())
                && !assigned.contains(area.id())).toList(), free, assigned, limitAreas, free.size() - reserveCount, now);
        // Choose reserves from guards left after priority-aware matching, so the
        // only guard who can reach a threatened area is never withheld by UUID order.
        List<UUID> reserve = free.stream().map(Candidate::guardId).sorted().limit(reserveCount).toList();
        List<AreaReport> reports = new ArrayList<>();
        for (Area area : layout.areas()) {
            AreaState state = areas.get(area.id());
            reports.add(new AreaReport(area.id(), reason(area.id(), candidates.values(), assigned,
                    limitAreas, now), state.lastVisit, state.age, state.reentryUntil > now));
        }
        return new Plan(ownLeases(), reports, unavailable, reserve, layout.status());
    }

    private Comparator<Area> areaOrder(Set<UUID> threats, VillageDefenseState readiness, long now) {
        return Comparator.comparing((Area area) -> !criticalAreas.contains(area.id()))
                // Fair offers, not only successful visits: interruptions cannot monopolize the next turn.
                .thenComparingLong(area -> areas.get(area.id()).offered)
                .thenComparing(Comparator.comparingLong((Area area) -> score(area, threats, readiness, now)).reversed())
                .thenComparing(Area::id);
    }

    private long score(Area area, Set<UUID> threats, VillageDefenseState readiness, long now) {
        AreaState state = areas.get(area.id());
        long age = state.reentryUntil > now ? Math.min(state.age, settings.reentryTicks()) : state.age;
        return age + (long) area.importance() * settings.importanceBonusTicks()
                + (readiness.isConfirmedThreatState() && threats.contains(area.id())
                        ? settings.threatBonusTicks() : 0);
    }

    private void allocate(List<Area> ordered, List<Candidate> free, Set<UUID> assigned,
            Set<UUID> limitAreas, int capacity, long now) {
        if (capacity == 0 || ordered.isEmpty()) { return; }
        Map<UUID, List<Choice>> edges = new HashMap<>();
        Map<UUID, Integer> alternatives = countOptions(free, List.copyOf(areasToLayout.values()), assigned, now);
        for (Area area : ordered) {
            List<Choice> choices = new ArrayList<>();
            for (Candidate candidate : free) {
                destinations.values().stream()
                        .filter(destination -> destination.areaId().equals(area.id()) && eligible(candidate, destination, now))
                        .map(destination -> new Choice(candidate, destination, candidate.position().distSqr(destination.position())))
                        .min(Comparator.comparingLong((Choice choice) -> destinationOffers.getOrDefault(choice.destination().id(), 0L))
                                .thenComparing(CHOICE_ORDER)).ifPresent(choices::add);
            }
            // Prefer a guard with fewer alternatives, then shorter travel. The
            // augmenting search below also handles cases that greedy choice cannot.
            choices.sort(Comparator.comparingInt((Choice choice) -> alternatives.get(choice.candidate().guardId()))
                    .thenComparing(CHOICE_ORDER));
            edges.put(area.id(), choices);
        }
        Map<UUID, Choice> matchedGuards = new HashMap<>();
        for (Area area : ordered) {
            if (matchedGuards.size() == capacity) { break; }
            match(area.id(), edges, matchedGuards, new HashSet<>());
        }
        Map<UUID, Choice> matchedAreas = new HashMap<>();
        matchedGuards.values().forEach(choice -> matchedAreas.put(choice.destination().areaId(), choice));
        for (Area area : ordered) {
            Choice best = matchedAreas.get(area.id());
            if (best == null) { continue; }
            Lease lease = reservations.claim(villageId, best.candidate().guardId(), area.id(),
                    best.destination().id(), Kind.COVERAGE, now, settings.leaseTicks());
            if (lease == null) {
                limitAreas.add(area.id());
                continue;
            }
            destinationOffers.merge(best.destination().id(), 1L, Long::sum);
            assigned.add(area.id());
            free.remove(best.candidate());
            AreaState state = areas.get(area.id());
            state.offered++;
            if (!criticalAreas.contains(area.id())) {
                latestPatrolRound = Math.max(latestPatrolRound, state.offered);
            }
        }
    }

    private static boolean match(UUID area, Map<UUID, List<Choice>> edges,
            Map<UUID, Choice> matchedGuards, Set<UUID> tried) {
        // Prefer a free candidate before moving a decision made earlier in this
        // publication. Previously issued leases never enter this search.
        List<Choice> choices = edges.get(area).stream().sorted(Comparator.comparing(
                (Choice choice) -> matchedGuards.containsKey(choice.candidate().guardId()))).toList();
        for (Choice choice : choices) {
            UUID guard = choice.candidate().guardId();
            if (!tried.add(guard)) { continue; }
            Choice previous = matchedGuards.get(guard);
            if (previous == null || match(previous.destination().areaId(), edges, matchedGuards, tried)) {
                matchedGuards.put(guard, choice);
                return true;
            }
        }
        return false;
    }

    private Map<UUID, Integer> countOptions(List<Candidate> candidates, List<Area> ordered,
            Set<UUID> assigned, long now) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (Candidate candidate : candidates) {
            counts.put(candidate.guardId(), reachableAreas(candidate, ordered, assigned, now));
        }
        return counts;
    }

    private int reachableAreas(Candidate candidate, List<Area> ordered, Set<UUID> assigned, long now) {
        return (int) ordered.stream().filter(area -> !assigned.contains(area.id()))
                .filter(area -> destinations.values().stream().anyMatch(destination ->
                        destination.areaId().equals(area.id()) && eligible(candidate, destination, now))).count();
    }

    private boolean eligible(@Nullable Candidate candidate, @Nullable Destination destination, long now) {
        if (candidate == null || destination == null || candidate.availability() != Reason.AVAILABLE
                || destination.state() != DestinationState.READY
                || !destination.component().equals(candidate.component())) { return false; }
        Failure failure = failures.get(destination.id());
        return (failure == null || failure.until <= now)
                && candidate.position().distSqr(destination.position()) <= settings.maximumTravelBlocks() * settings.maximumTravelBlocks();
    }

    private UncoveredReason reason(UUID area, Collection<Candidate> candidates, Set<UUID> assigned,
            Set<UUID> limitAreas, long now) {
        if (assigned.contains(area)) { return UncoveredReason.ASSIGNED; }
        if (limitAreas.contains(area)) { return UncoveredReason.RESERVATION_LIMIT; }
        List<Destination> options = destinations.values().stream().filter(d -> d.areaId().equals(area)).toList();
        if (options.isEmpty()) { return UncoveredReason.NO_VALIDATED_DESTINATION; }
        if (options.stream().allMatch(d -> d.state() == DestinationState.UNLOADED)) { return UncoveredReason.UNLOADED; }
        if (options.stream().noneMatch(d -> d.state() == DestinationState.READY)) {
            return options.stream().anyMatch(d -> d.state() == DestinationState.UNVALIDATED)
                    ? UncoveredReason.NO_VALIDATED_DESTINATION : UncoveredReason.BLOCKED;
        }
        if (options.stream().filter(d -> d.state() == DestinationState.READY).allMatch(d ->
                failures.containsKey(d.id()) && failures.get(d.id()).until > now)) { return UncoveredReason.RETRY_COOLDOWN; }
        if (candidates.stream().noneMatch(c -> c.availability() == Reason.AVAILABLE)) { return UncoveredReason.NO_AVAILABLE_GUARDS; }
        if (candidates.stream().noneMatch(c -> options.stream().anyMatch(d -> eligible(c, d, now)))) {
            return UncoveredReason.NO_REACHABLE_GUARD;
        }
        return UncoveredReason.INSUFFICIENT_GUARDS;
    }

    /** The live adapter must pass the actual current position after validating ownership. */
    public boolean arrive(Lease lease, BlockPos actualPosition, long now) {
        checkTime(now);
        if (!current(lease, now)) { return false; }
        Destination destination = destinations.get(lease.destinationId());
        AreaState state = areas.get(lease.areaId());
        BlockPos position = VillageCoverageLayout.checkedPosition(actualPosition);
        if (destination == null || destination.state() != DestinationState.READY || state == null
                || !areasToLayout.get(lease.areaId()).contains(position)
                || position.distSqr(destination.position()) > 9.0
                || Math.abs(position.getY() - destination.position().getY()) > 1
                || lease.token().equals(state.arrivalToken)) { return false; }
        advance(now);
        failures.remove(lease.destinationId());
        state.lastVisit = now;
        state.age = 0;
        state.arrivalToken = lease.token();
        if (!criticalAreas.contains(lease.areaId())) { reservations.release(lease); }
        return true;
    }

    /** Combat/follow/unload releases without falsely crediting a visit or a path failure. */
    public boolean interrupt(Lease lease, long now) {
        checkTime(now);
        if (!current(lease, now)) { return false; }
        advance(now);
        return reservations.release(lease);
    }

    /** Each failed navigation attempt releases its lease; repeated failures extend suppression. */
    public boolean navigationFailed(Lease lease, long now) {
        checkTime(now);
        if (!current(lease, now)) { return false; }
        advance(now);
        Failure failure = failures.computeIfAbsent(lease.destinationId(), key -> new Failure());
        failure.count = Math.min(16, failure.count + 1);
        long delay = Math.min(settings.maximumRetryTicks(), (long) settings.retryTicks() << (failure.count - 1));
        failure.until = deadline(now, delay);
        reservations.release(lease);
        return true;
    }

    /** Completed terrain repair permits reevaluating old navigation failures without ending valid leases. */
    public void terrainRepaired() { failures.clear(); }

    /** Unload/disable disposes only coverage work owned by this policy. */
    public void clear() {
        ownLeases().forEach(reservations::release);
        areas.clear();
        failures.clear();
        destinationOffers.clear();
        destinations = Map.of();
        areasToLayout = Map.of();
        criticalAreas = Set.of();
        lastTime = -1;
        latestPatrolRound = 0;
        layoutRevision = 0;
    }

    private boolean current(Lease lease, long now) {
        return lease != null && lease.kind() == Kind.COVERAGE && villageId.equals(lease.villageId())
                && lease.equals(reservations.forGuard(lease.guardId()))
                && lease.assignedAt() <= now && lease.expiresAt() > now;
    }

    private List<Lease> ownLeases() {
        return reservations.forVillage(villageId).stream().filter(lease -> lease.kind() == Kind.COVERAGE).toList();
    }

    private void advance(long now) {
        if (lastTime >= 0) {
            for (AreaState state : areas.values()) {
                if (state.loaded) {
                    state.age = Math.min(settings.maximumAgeTicks(), state.age + now - lastTime);
                }
            }
        }
        lastTime = now;
    }

    private void checkTime(long now) {
        if (now < 0 || now < lastTime || now > Long.MAX_VALUE - 24_000) {
            throw new IllegalArgumentException("Coverage requires a monotonic simulation clock");
        }
    }

    private static long deadline(long now, long ticks) { return now + ticks; }
    private static final Comparator<Choice> CHOICE_ORDER = Comparator.comparingDouble(Choice::distance)
            .thenComparing(choice -> choice.candidate().guardId()).thenComparing(choice -> choice.destination().id());
    private record Choice(Candidate candidate, Destination destination, double distance) { }
    private static final class AreaState {
        private boolean loaded;
        private long age;
        private long offered;
        private long lastVisit = -1;
        private long reentryUntil;
        private UUID arrivalToken;
        private AreaState(long now) { reentryUntil = now; }
    }
    private static final class Failure { private int count; private long until; }

    public record Settings(int leaseTicks, int retryTicks, int maximumRetryTicks,
            int reentryTicks, int maximumAgeTicks, int importanceBonusTicks,
            int threatBonusTicks, int reserveGuards, double maximumTravelBlocks) {
        public static final Settings DEFAULT = new Settings(400, 100, 1_600, 200, 24_000, 200, 600, 1, 128);
        public Settings {
            if (leaseTicks < 1 || leaseTicks > 24_000 || retryTicks < 1 || maximumRetryTicks < retryTicks
                    || maximumRetryTicks > 24_000 || reentryTicks < 0 || reentryTicks > 24_000
                    || maximumAgeTicks < reentryTicks || maximumAgeTicks > 24_000
                    || importanceBonusTicks < 0 || importanceBonusTicks > 24_000
                    || threatBonusTicks < 0 || threatBonusTicks > 24_000
                    || reserveGuards < 0 || reserveGuards > VillageCoverageLayout.MAX_AREAS
                    || !Double.isFinite(maximumTravelBlocks) || maximumTravelBlocks < 1 || maximumTravelBlocks > 1_024) {
                throw new IllegalArgumentException("Coverage settings exceed supported bounds");
            }
        }
    }

    public record Destination(UUID id, UUID areaId, BlockPos position,
            DestinationState state, @Nullable UUID component, long terrainRevision) {
        public Destination {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(areaId, "areaId");
            position = VillageCoverageLayout.checkedPosition(position);
            Objects.requireNonNull(state, "state");
            if (terrainRevision < 0 || (state == DestinationState.READY && component == null)) {
                throw new IllegalArgumentException("Ready destinations require reachability evidence");
            }
        }
    }
    public enum DestinationState { UNLOADED, UNVALIDATED, BLOCKED, READY }
    public record Candidate(UUID guardId, BlockPos position, @Nullable UUID component, Reason availability) {
        public Candidate {
            Objects.requireNonNull(guardId, "guardId");
            position = VillageCoverageLayout.checkedPosition(position);
            Objects.requireNonNull(availability, "availability");
        }
    }
    public enum UncoveredReason {
        ASSIGNED, UNLOADED, NO_VALIDATED_DESTINATION, BLOCKED, RETRY_COOLDOWN,
        NO_AVAILABLE_GUARDS, NO_REACHABLE_GUARD, INSUFFICIENT_GUARDS, RESERVATION_LIMIT
    }
    public record AreaReport(UUID areaId, UncoveredReason reason, long lastSuccessfulVisit,
            long activeAgeTicks, boolean recoveringFromUnload) { }
    public record Plan(List<Lease> assignments, List<AreaReport> areas,
            Map<Reason, Integer> unavailableGuards, List<UUID> reserveGuards, VillageCoverageLayout.Status layoutStatus) {
        public Plan {
            assignments = List.copyOf(assignments);
            areas = List.copyOf(areas);
            unavailableGuards = Map.copyOf(unavailableGuards);
            reserveGuards = List.copyOf(reserveGuards);
        }
    }
}
