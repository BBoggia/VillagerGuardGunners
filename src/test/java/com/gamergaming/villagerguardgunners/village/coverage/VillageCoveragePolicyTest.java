package com.gamergaming.villagerguardgunners.village.coverage;

import static org.junit.jupiter.api.Assertions.*;

import com.gamergaming.villagerguardgunners.defense.VillageDefenseState;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageAvailability.Reason;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageLayout.Site;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageLayout.SiteKind;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoveragePolicy.Candidate;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoveragePolicy.Destination;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoveragePolicy.DestinationState;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoveragePolicy.Plan;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoveragePolicy.Settings;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoveragePolicy.UncoveredReason;
import com.gamergaming.villagerguardgunners.village.coverage.VillageCoverageReservations.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class VillageCoveragePolicyTest {
    private static final UUID VILLAGE = id(1), COMPONENT = id(2);
    private static final Settings SETTINGS = new Settings(40, 10, 80, 20, 24_000, 200, 600, 1, 1_024);
    private final VillageCoverageReservations book = new VillageCoverageReservations();
    private final VillageCoveragePolicy policy = new VillageCoveragePolicy(VILLAGE, book, SETTINGS);

    @Test void simultaneousGuardsSpreadOutAndAssignmentsDoNotCountAsVisits() {
        var layout = layout(4);
        var plan = plan(layout, 3, 0);
        assertEquals(3, plan.assignments().size());
        assertEquals(3, plan.assignments().stream().map(lease -> lease.areaId()).distinct().count());
        assertTrue(plan.areas().stream().allMatch(area -> area.lastSuccessfulVisit() == -1));
        assertEquals(1, plan.areas().stream().filter(area -> area.reason() == UncoveredReason.INSUFFICIENT_GUARDS).count());
        assertEquals(plan.assignments(), plan(layout, 3, 10).assignments());
    }

    @Test void arrivalRequiresTheCurrentLeaseAndActualProximityIncludingHeight() {
        var layout = layout(2);
        var lease = plan(layout, 1, 0).assignments().get(0);
        var target = destinations(layout).stream().filter(d -> d.id().equals(lease.destinationId())).findFirst().orElseThrow();
        assertFalse(policy.arrive(lease, target.position().offset(20, 0, 0), 1));
        assertFalse(policy.arrive(lease, target.position().above(2), 1));
        assertTrue(policy.arrive(lease, target.position(), 10));
        assertFalse(policy.arrive(lease, target.position(), 10));
        var next = plan(layout, 1, 11);
        assertNotEquals(lease.areaId(), next.assignments().get(0).areaId());
        var visited = next.areas().stream().filter(area -> area.areaId().equals(lease.areaId())).findFirst().orElseThrow();
        assertEquals(10, visited.lastSuccessfulVisit());
        assertEquals(1, visited.activeAgeTicks());
        assertFalse(policy.interrupt(lease, 11));
        assertEquals(1, book.size());
    }

    @Test void expirationRotatesWorkWithoutCreditingAnUnfinishedVisit() {
        var layout = layout(3);
        var first = plan(layout, 1, 0).assignments().get(0);
        var next = plan(layout, 1, SETTINGS.leaseTicks());
        assertNotEquals(first.areaId(), next.assignments().get(0).areaId());
        assertTrue(next.areas().stream().allMatch(area -> area.lastSuccessfulVisit() == -1));
        assertFalse(policy.navigationFailed(first, SETTINGS.leaseTicks()));
        assertEquals(1, book.size());
    }

    @Test void continualCombatInterruptionsCannotStarveAnyOfSixteenReachableAreas() {
        var layout = layout(16);
        Set<UUID> seen = new HashSet<>();
        for (int tick = 0; tick < 16; tick++) {
            var lease = plan(layout, 1, tick).assignments().get(0);
            assertTrue(seen.add(lease.areaId()), "An interrupted area stole another area's turn");
            assertTrue(policy.interrupt(lease, tick));
        }
        assertEquals(16, seen.size());
        assertTrue(plan(layout, 1, 16).areas().stream().allMatch(area -> area.lastSuccessfulVisit() == -1));
    }

    @Test void successfulVisitsRotateUnderScarceStaffingToo() {
        var layout = layout(6);
        var destinations = destinations(layout);
        Set<UUID> visited = new HashSet<>();
        for (int tick = 0; tick < 6; tick++) {
            var lease = plan(layout, 1, tick).assignments().get(0);
            var destination = destinations.stream().filter(d -> d.id().equals(lease.destinationId())).findFirst().orElseThrow();
            assertTrue(visited.add(lease.areaId()));
            assertTrue(policy.arrive(lease, destination.position(), tick));
        }
        assertEquals(6, visited.size());
    }

    @Test void navigationFailureUsesBoundedBackoffAndTerrainChangesPermitRevalidation() {
        var layout = layout(1);
        var first = plan(layout, 1, 0).assignments().get(0);
        assertTrue(policy.navigationFailed(first, 0));
        assertEquals(UncoveredReason.RETRY_COOLDOWN, plan(layout, 1, 9).areas().get(0).reason());
        var retry = plan(layout, 1, 10).assignments().get(0);
        assertTrue(policy.navigationFailed(retry, 10));
        assertTrue(plan(layout, 1, 29).assignments().isEmpty());
        var old = destinations(layout).get(0);
        var changed = new Destination(old.id(), old.areaId(), old.position(), old.state(), old.component(), 2);
        var revised = schedule(layout, List.of(changed), candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 29);
        assertEquals(1, revised.assignments().size());
        assertTrue(policy.arrive(revised.assignments().get(0), old.position(), 29));
    }

    @Test void failedDestinationUsesAnAlternativeWithoutSuppressingTheWholeArea() {
        var layout = layout(1);
        var first = destinations(layout).get(0);
        var alternative = new Destination(id(999), first.areaId(), first.position().offset(3, 0, 0),
                DestinationState.READY, COMPONENT, 1);
        var options = List.of(first, alternative);
        var lease = schedule(layout, options, candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 0).assignments().get(0);
        assertTrue(policy.navigationFailed(lease, 0));
        var retry = schedule(layout, options, candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 1).assignments().get(0);
        assertNotEquals(lease.destinationId(), retry.destinationId());
    }

    @Test void unloadingCancelsClaimsAndReloadingCannotAccumulateOfflinePriority() {
        var layout = layout(2);
        var initial = plan(layout, 1, 0).assignments().get(0);
        var options = destinations(layout).stream().map(d -> d.areaId().equals(initial.areaId())
                ? new Destination(d.id(), d.areaId(), d.position(), DestinationState.UNLOADED, null, 1) : d).toList();
        var unloaded = schedule(layout, options, candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 10);
        assertNotEquals(initial.areaId(), unloaded.assignments().get(0).areaId());
        assertTrue(unloaded.areas().stream().anyMatch(area -> area.reason() == UncoveredReason.UNLOADED));
        var reloaded = plan(layout, 1, 100_000);
        var report = reloaded.areas().stream().filter(area -> area.areaId().equals(initial.areaId())).findFirst().orElseThrow();
        assertEquals(0, report.activeAgeTicks());
        assertTrue(report.recoveringFromUnload());
        assertNotEquals(initial.areaId(), reloaded.assignments().get(0).areaId());
        assertTrue(policy.interrupt(reloaded.assignments().get(0), 100_000));
        assertEquals(initial.areaId(), plan(layout, 1, 100_021).assignments().get(0).areaId());
    }

    @Test void criticalPostsTakePrecedenceButLeaveRemainingGuardsToRotate() {
        var layout = layout(4);
        UUID critical = layout.areas().get(3).id();
        var first = schedule(layout, destinations(layout), candidates(2), Set.of(critical), Set.of(), VillageDefenseState.PEACE, 0);
        assertEquals(2, first.assignments().size());
        assertTrue(first.assignments().stream().anyMatch(lease -> lease.areaId().equals(critical)));
        var post = first.assignments().stream().filter(lease -> lease.areaId().equals(critical)).findFirst().orElseThrow();
        var destination = destinations(layout).stream().filter(d -> d.areaId().equals(critical)).findFirst().orElseThrow();
        assertTrue(policy.arrive(post, destination.position(), 1));
        assertFalse(policy.arrive(post, destination.position(), 2));
        assertEquals(post, book.forGuard(post.guardId()));
        var next = schedule(layout, destinations(layout), candidates(2), Set.of(critical), Set.of(), VillageDefenseState.PEACE, 40);
        assertTrue(next.assignments().stream().anyMatch(lease -> lease.areaId().equals(critical)));
        assertNotEquals(first.assignments().stream().filter(l -> !l.areaId().equals(critical)).findFirst().orElseThrow().areaId(),
                next.assignments().stream().filter(l -> !l.areaId().equals(critical)).findFirst().orElseThrow().areaId());
    }

    @ParameterizedTest @EnumSource(value = VillageDefenseState.class, names = {"ALERT", "SIEGE"})
    void confirmedThreatsRaisePriorityAndLeaveReserveWhenStaffingAllows(VillageDefenseState readiness) {
        var layout = layout(4);
        UUID threat = layout.areas().get(3).id();
        var result = schedule(layout, destinations(layout), candidates(2), Set.of(), Set.of(threat), readiness, 0);
        assertEquals(1, result.reserveGuards().size());
        assertEquals(threat, result.assignments().get(0).areaId());
        policy.clear();
        result = schedule(layout, destinations(layout), candidates(1), Set.of(), Set.of(threat), readiness, 0);
        assertTrue(result.reserveGuards().isEmpty());
        assertEquals(threat, result.assignments().get(0).areaId());
    }

    @Test void watchDoesNotTreatUnconfirmedThreatsAsConfirmedOrSetAsideReserve() {
        var layout = layout(4);
        var result = schedule(layout, destinations(layout), candidates(2), Set.of(), Set.of(layout.areas().get(3).id()),
                VillageDefenseState.WATCH, 0);
        assertEquals(2, result.assignments().size());
        assertTrue(result.reserveGuards().isEmpty());
        assertEquals(Set.of(layout.areas().get(0).id(), layout.areas().get(1).id()),
                result.assignments().stream().map(lease -> lease.areaId()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void reachabilityIsExplicitAndAnUnknownCandidateCannotBeSentAcrossARavine() {
        var layout = layout(1);
        var farComponent = new Candidate(id(500), BlockPos.ZERO, id(600), Reason.AVAILABLE);
        var unknown = new Candidate(id(501), BlockPos.ZERO, null, Reason.AVAILABLE);
        var result = schedule(layout, destinations(layout), List.of(farComponent, unknown), Set.of(), Set.of(), VillageDefenseState.PEACE, 0);
        assertTrue(result.assignments().isEmpty());
        assertEquals(UncoveredReason.NO_REACHABLE_GUARD, result.areas().get(0).reason());
    }

    @ParameterizedTest @EnumSource(value = DestinationState.class, names = {"UNLOADED", "UNVALIDATED", "BLOCKED"})
    void unsafeOrUnknownDestinationsNeverReceiveAssignments(DestinationState state) {
        var layout = layout(1);
        var d = destinations(layout).get(0);
        var result = schedule(layout, List.of(new Destination(d.id(), d.areaId(), d.position(), state, null, 1)), candidates(1),
                Set.of(), Set.of(), VillageDefenseState.PEACE, 0);
        assertTrue(result.assignments().isEmpty());
        assertEquals(switch (state) {
            case UNLOADED -> UncoveredReason.UNLOADED;
            case UNVALIDATED -> UncoveredReason.NO_VALIDATED_DESTINATION;
            case BLOCKED -> UncoveredReason.BLOCKED;
            default -> throw new AssertionError();
        }, result.areas().get(0).reason());
    }

    @ParameterizedTest @EnumSource(value = Reason.class, mode = EnumSource.Mode.EXCLUDE, names = "AVAILABLE")
    void unavailableGuardsReleaseClaimsWithoutDeletingMembershipOrCreditingVisits(Reason reason) {
        var layout = layout(1);
        var first = plan(layout, 1, 0).assignments().get(0);
        var guard = candidates(1).get(0);
        var result = schedule(layout, destinations(layout), List.of(new Candidate(guard.guardId(), guard.position(), COMPONENT, reason)),
                Set.of(), Set.of(), VillageDefenseState.PEACE, 1);
        assertTrue(result.assignments().isEmpty());
        assertEquals(1, result.unavailableGuards().get(reason));
        assertEquals(-1, result.areas().get(0).lastSuccessfulVisit());
        assertNull(book.forGuard(first.guardId()));
        assertEquals(1, plan(layout, 1, 2).assignments().size());
    }

    @Test void changedTerrainInvalidatesAStaleArrivalEvenWhenDestinationIdIsReused() {
        var layout = layout(1);
        var first = plan(layout, 1, 0).assignments().get(0);
        var old = destinations(layout).get(0);
        var changed = new Destination(old.id(), old.areaId(), old.position().offset(8, 0, 0), old.state(), COMPONENT, 2);
        var next = schedule(layout, List.of(changed), candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 1);
        assertNotEquals(first.token(), next.assignments().get(0).token());
        assertFalse(policy.arrive(first, changed.position(), 1));
        assertFalse(policy.navigationFailed(first, 1));
        assertEquals(next.assignments().get(0), book.forGuard(first.guardId()));
    }

    @Test void invalidPublicationDoesNotDestroyExistingClaims() {
        var layout = layout(1);
        var first = plan(layout, 1, 0);
        var destinations = destinations(layout);
        assertThrows(IllegalArgumentException.class, () -> schedule(layout, destinations,
                List.of(candidates(1).get(0), candidates(1).get(0)), Set.of(), Set.of(), VillageDefenseState.PEACE, 1));
        assertThrows(IllegalArgumentException.class, () -> schedule(layout,
                List.of(destinations.get(0), destinations.get(0)), candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 1));
        assertThrows(IllegalArgumentException.class, () -> schedule(layout, destinations,
                candidates(129), Set.of(), Set.of(), VillageDefenseState.PEACE, 1));
        assertThrows(IllegalArgumentException.class, () -> schedule(layout,
                Collections.nCopies(65, destinations.get(0)), candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 1));
        assertThrows(IllegalArgumentException.class, () -> schedule(layout, destinations,
                candidates(1), Set.of(id(999)), Set.of(), VillageDefenseState.PEACE, 1));
        assertEquals(first.assignments(), book.forVillage(VILLAGE));
        assertEquals(first.assignments(), plan(layout, 1, 1).assignments());
    }

    @Test void mixedTerrainComponentsRequireSeparateAreasRatherThanFalseCoverage() {
        var layout = layout(1);
        var d = destinations(layout).get(0);
        var other = new Destination(id(999), d.areaId(), d.position().offset(1, 0, 0), DestinationState.READY, id(9999), 1);
        assertThrows(IllegalArgumentException.class, () -> schedule(layout, List.of(d, other), candidates(2),
                Set.of(), Set.of(), VillageDefenseState.PEACE, 0));
        assertEquals(0, book.size());
    }

    @Test void sharedBookPreventsCrossVillageAndFixedPostDoubleAssignments() {
        var layout = layout(1);
        var fixed = book.claim(VILLAGE, candidates(1).get(0).guardId(), id(801), id(802), Kind.FIXED_POST, 0, 100);
        assertTrue(plan(layout, 1, 0).assignments().isEmpty());
        policy.clear();
        assertEquals(fixed, book.forGuard(fixed.guardId()));
        book.release(fixed);
        var assigned = plan(layout, 1, 1);
        var otherVillage = id(900);
        var otherLayout = VillageCoverageLayout.partition(otherVillage, 1, List.of(new Site(new BlockPos(0, 64, 0), SiteKind.HOME)));
        var otherPolicy = new VillageCoveragePolicy(otherVillage, book, SETTINGS);
        var other = otherPolicy.schedule(otherLayout, destinations(otherLayout), candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 1);
        assertTrue(other.assignments().isEmpty());
        assertEquals(1, other.unavailableGuards().get(Reason.OTHER_DUTY));
        assertEquals(assigned.assignments(), book.forVillage(VILLAGE));
    }

    @Test void oversizedReplacementRetainsUsefulPartialCoverage() {
        plan(layout(1), 1, 0);
        var oversized = VillageCoverageLayout.partition(VILLAGE, 2,
                IntStream.range(0, 17).mapToObj(i -> new Site(new BlockPos(i * 64, 64, 0), SiteKind.HOME)).toList());
        var plan = schedule(oversized, destinations(oversized), candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 1);
        assertEquals(VillageCoverageLayout.Status.AREA_LIMIT, plan.layoutStatus());
        assertEquals(1, plan.assignments().size());
        assertEquals(VillageCoverageLayout.MAX_AREAS, plan.areas().size());
        assertEquals(1, book.size());
    }

    @Test void clocksAreMonotonicAcrossCallbacksAndConfigurationIsBounded() {
        var layout = layout(1);
        var lease = plan(layout, 1, 0).assignments().get(0);
        assertTrue(policy.interrupt(lease, 10));
        assertThrows(IllegalArgumentException.class, () -> plan(layout, 1, 9));
        assertThrows(IllegalArgumentException.class, () -> plan(layout, 1, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new Settings(0, 10, 80, 20, 1_000, 200, 600, 1, 128));
        assertThrows(IllegalArgumentException.class, () -> new Settings(40, 10, 80, 20, 1_000, 200, 600, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Destination(id(4), id(5), BlockPos.ZERO, DestinationState.READY, null, 1));
    }

    @Test void travelLimitsAndMatchingAvoidWastingAReachableGuardOnTheWrongArea() {
        // Left can be reached by both guards; only the first can reach the right.
        var layout = VillageCoverageLayout.partition(VILLAGE, 1,
                List.of(new Site(new BlockPos(0, 64, 0), SiteKind.DEFENSE), new Site(new BlockPos(192, 64, 0), SiteKind.HOME)));
        var limited = new VillageCoveragePolicy(VILLAGE, book,
                new Settings(40, 10, 80, 20, 1_000, 200, 600, 0, 128));
        var guards = List.of(new Candidate(id(10), new BlockPos(80, 64, 0), COMPONENT, Reason.AVAILABLE),
                new Candidate(id(11), new BlockPos(-100, 64, 0), COMPONENT, Reason.AVAILABLE));
        var result = limited.schedule(layout, destinations(layout), guards, Set.of(), Set.of(), VillageDefenseState.PEACE, 0);
        assertEquals(2, result.assignments().size());
        var left = layout.areas().stream().filter(area -> area.contains(new BlockPos(0, 64, 0))).findFirst().orElseThrow();
        assertEquals(id(11), result.assignments().stream().filter(lease -> lease.areaId().equals(left.id())).findFirst().orElseThrow().guardId());
    }

    @Test void publicationOrderCannotChangeGuardToAreaDecisions() {
        var layout = layout(8);
        var first = plan(layout, 5, 0);
        policy.clear();
        var guards = new ArrayList<>(candidates(5));
        var options = new ArrayList<>(destinations(layout));
        Collections.reverse(guards);
        Collections.reverse(options);
        var reverse = schedule(layout, options, guards, Set.of(), Set.of(), VillageDefenseState.PEACE, 0);
        assertEquals(first.assignments().stream().map(lease -> List.of(lease.guardId(), lease.areaId(), lease.destinationId())).toList(),
                reverse.assignments().stream().map(lease -> List.of(lease.guardId(), lease.areaId(), lease.destinationId())).toList());
    }

    @Test void threatPriorityIsReevaluatedEachFairRoundAndCannotMonopolizeEveryRound() {
        var layout = layout(4);
        for (int tick = 0; tick < 4; tick++) {
            var lease = plan(layout, 1, tick).assignments().get(0);
            policy.interrupt(lease, tick);
        }
        UUID threat = layout.areas().get(3).id();
        Set<UUID> round = new HashSet<>();
        for (int tick = 4; tick < 8; tick++) {
            var next = schedule(layout, destinations(layout), candidates(1), Set.of(), Set.of(threat), VillageDefenseState.ALERT, tick);
            var lease = next.assignments().get(0);
            if (tick == 4) { assertEquals(threat, lease.areaId()); }
            assertTrue(round.add(lease.areaId()));
            policy.interrupt(lease, tick);
        }
        assertEquals(4, round.size());
    }

    @Test void reserveDoesNotWithholdTheOnlyGuardWhoCanReachTheThreat() {
        var layout = layout(4);
        UUID threat = layout.areas().get(3).id();
        UUID isolatedComponent = id(9999);
        var options = destinations(layout).stream().map(d -> d.areaId().equals(threat)
                ? new Destination(d.id(), d.areaId(), d.position(), d.state(), isolatedComponent, 1) : d).toList();
        var guards = List.of(candidates(1).get(0), new Candidate(id(101), new BlockPos(0, 64, 0), isolatedComponent, Reason.AVAILABLE));
        var result = schedule(layout, options, guards, Set.of(), Set.of(threat), VillageDefenseState.SIEGE, 0);
        assertEquals(threat, result.assignments().get(0).areaId());
        assertEquals(id(101), result.assignments().get(0).guardId());
        assertEquals(List.of(id(100)), result.reserveGuards());
    }

    @Test void retryBackoffStopsGrowingAtItsHardLimit() {
        var layout = layout(1);
        long now = 0;
        for (int attempt = 0; attempt < 10; attempt++) {
            var lease = plan(layout, 1, now).assignments().get(0);
            assertTrue(policy.navigationFailed(lease, now));
            long wait = Math.min(80, 10L << Math.min(attempt, 7));
            assertTrue(plan(layout, 1, now + wait - 1).assignments().isEmpty());
            now += wait;
        }
        assertEquals(1, plan(layout, 1, now).assignments().size());
    }

    @Test void smallestRetryCanStillGrowToTheConfiguredMaximum() {
        var layout = layout(1);
        var slow = new VillageCoveragePolicy(VILLAGE, book, new Settings(40, 1, 24_000, 20, 24_000, 200, 600, 0, 128));
        long now = 0;
        for (int attempt = 0; attempt < 18; attempt++) {
            var lease = slow.schedule(layout, destinations(layout), candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, now)
                    .assignments().get(0);
            assertTrue(slow.navigationFailed(lease, now));
            long wait = Math.min(24_000, 1L << Math.min(attempt, 15));
            assertTrue(slow.schedule(layout, destinations(layout), candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE,
                    now + wait - 1).assignments().isEmpty());
            now += wait;
        }
    }

    @Test void matchingAgreesWithExhaustiveCoverageCountAcrossVariedSmallLayouts() {
        java.util.Random random = new java.util.Random(413_227);
        for (int sample = 0; sample < 40; sample++) {
            book.clear();
            var layout = layout(5);
            List<Candidate> guards = IntStream.range(0, 5).mapToObj(index -> new Candidate(id(100 + index),
                    new BlockPos(random.nextInt(513) - 64, 64, random.nextInt(257) - 64), COMPONENT, Reason.AVAILABLE)).toList();
            var limited = new VillageCoveragePolicy(VILLAGE, book,
                    new Settings(40, 10, 80, 20, 1_000, 200, 600, 0, 160));
            var destinations = destinations(layout);
            var actual = limited.schedule(layout, destinations, guards, Set.of(), Set.of(), VillageDefenseState.PEACE, 0);
            assertEquals(maximumMatching(destinations, guards, 0, new HashSet<>()), actual.assignments().size(), "Sample " + sample);
        }
    }

    @Test void completeMaximumPublicationSchedulesSixteenAreasWithSixtyFourDestinationsAnd128Candidates() {
        var layout = layout(16);
        List<Destination> options = new ArrayList<>();
        for (int index = 0; index < layout.areas().size(); index++) {
            var area = layout.areas().get(index);
            for (int point = 0; point < 4; point++) {
                options.add(new Destination(id(10_000 + index * 4 + point), area.id(), area.anchors().get(0).offset(point, 0, 0),
                        DestinationState.READY, COMPONENT, 1));
            }
        }
        var result = schedule(layout, options, candidates(128), Set.of(), Set.of(), VillageDefenseState.PEACE, 0);
        assertEquals(16, result.assignments().size());
        assertEquals(16, result.assignments().stream().map(lease -> lease.guardId()).distinct().count());
        assertEquals(16, result.assignments().stream().map(lease -> lease.areaId()).distinct().count());
    }

    @Test void repairedAreaCannotBankAnUnboundedBacklogOfOffers() {
        var layout = layout(3);
        UUID blocked = layout.areas().get(0).id();
        var blockedOptions = destinations(layout).stream().map(d -> d.areaId().equals(blocked)
                ? new Destination(d.id(), d.areaId(), d.position(), DestinationState.BLOCKED, null, 1) : d).toList();
        for (int tick = 0; tick < 100; tick++) {
            var lease = schedule(layout, blockedOptions, candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, tick)
                    .assignments().get(0);
            policy.interrupt(lease, tick);
        }
        Set<UUID> offered = new HashSet<>();
        for (int tick = 100; tick < 104; tick++) {
            var lease = plan(layout, 1, tick).assignments().get(0);
            offered.add(lease.areaId());
            policy.interrupt(lease, tick);
        }
        assertEquals(3, offered.size());
    }

    @Test void changingBetweenCriticalAndRoutineDoesNotInheritAnUnrelatedGroupsRoundDebt() {
        var layout = layout(3);
        UUID critical = layout.areas().get(0).id();
        for (int tick = 0; tick < 100; tick++) {
            var result = schedule(layout, destinations(layout), candidates(2), Set.of(critical), Set.of(), VillageDefenseState.PEACE, tick);
            for (var lease : result.assignments()) { policy.interrupt(lease, tick); }
        }
        Set<UUID> seen = new HashSet<>();
        for (int tick = 100; tick < 103; tick++) {
            var lease = plan(layout, 1, tick).assignments().get(0);
            assertTrue(seen.add(lease.areaId()));
            policy.interrupt(lease, tick);
        }
        assertEquals(3, seen.size());

        policy.clear();
        for (int tick = 0; tick < 100; tick++) {
            var result = schedule(layout, destinations(layout), candidates(2), Set.of(critical), Set.of(), VillageDefenseState.PEACE, tick);
            for (var lease : result.assignments()) {
                if (!lease.areaId().equals(critical)) { policy.interrupt(lease, tick); }
            }
        }
        for (var lease : book.forVillage(VILLAGE)) { policy.interrupt(lease, 100); }
        Set<UUID> allCritical = layout.areas().stream().map(VillageCoverageLayout.Area::id).collect(java.util.stream.Collectors.toSet());
        seen.clear();
        for (int tick = 100; tick < 103; tick++) {
            var lease = schedule(layout, destinations(layout), candidates(1), allCritical, Set.of(), VillageDefenseState.PEACE, tick)
                    .assignments().get(0);
            assertTrue(seen.add(lease.areaId()));
            policy.interrupt(lease, tick);
        }
        assertEquals(3, seen.size());
    }

    @Test void smallVillageRotatesApproachesInsteadOfRevisitingOnlyTheNearestPoint() {
        var layout = layout(1);
        var near = destinations(layout).get(0);
        var far = new Destination(id(999), near.areaId(), near.position().offset(20, 0, 0), DestinationState.READY, COMPONENT, 1);
        var first = schedule(layout, List.of(near, far), candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 0)
                .assignments().get(0);
        assertEquals(near.id(), first.destinationId());
        assertTrue(policy.arrive(first, near.position(), 1));
        var next = schedule(layout, List.of(near, far), candidates(1), Set.of(), Set.of(), VillageDefenseState.PEACE, 2)
                .assignments().get(0);
        assertEquals(far.id(), next.destinationId());
    }

    private static int maximumMatching(List<Destination> destinations, List<Candidate> guards, int area, Set<UUID> used) {
        if (area == destinations.size()) { return 0; }
        int best = maximumMatching(destinations, guards, area + 1, used);
        for (Candidate candidate : guards) {
            if (candidate.position().distSqr(destinations.get(area).position()) <= 160 * 160 && used.add(candidate.guardId())) {
                best = Math.max(best, 1 + maximumMatching(destinations, guards, area + 1, used));
                used.remove(candidate.guardId());
            }
        }
        return best;
    }

    private Plan plan(VillageCoverageLayout layout, int guards, long now) {
        return schedule(layout, destinations(layout), candidates(guards), Set.of(), Set.of(), VillageDefenseState.PEACE, now);
    }
    private Plan schedule(VillageCoverageLayout layout, List<Destination> destinations, List<Candidate> candidates,
            Set<UUID> critical, Set<UUID> threats, VillageDefenseState state, long now) {
        return policy.schedule(layout, destinations, candidates, critical, threats, state, now);
    }
    private static UUID id(long value) { return new UUID(0, value); }
    private static VillageCoverageLayout layout(int count) {
        return VillageCoverageLayout.partition(VILLAGE, 1, IntStream.range(0, count)
                .mapToObj(i -> new Site(new BlockPos((i % 4) * 128, 64, (i / 4) * 128), SiteKind.HOME)).toList());
    }
    private static List<Destination> destinations(VillageCoverageLayout layout) {
        return layout.areas().stream().map(area -> new Destination(area.id(), area.id(), area.anchors().get(0),
                DestinationState.READY, COMPONENT, 1)).toList();
    }
    private static List<Candidate> candidates(int count) {
        return IntStream.range(0, count).mapToObj(i -> new Candidate(id(100 + i), new BlockPos(0, 64, 0), COMPONENT, Reason.AVAILABLE)).toList();
    }
}
