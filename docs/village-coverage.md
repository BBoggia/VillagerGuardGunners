# Village coverage policy

Villager Guard Gunners includes a bounded coverage planner for ordinary villages
and villages with Village Defense Buildings. It partitions observed sites,
matches available guards to reachable areas, and produces temporary assignments
with reasons for gaps in coverage.

This Java policy API is used by the optional [live patrol coverage layer](village-patrol-coverage.md).
The policy itself performs no world access or navigation and stores no saved
fields or population credits. The live layer supplies validated observations,
movement ownership, configuration, and bounded diagnostics.

## Observation boundary

`VillageCoverageLayout.fromRecord` consumes an immutable
[`VillageRecord`](village-records.md), an explicit template-to-site classification
map, and service observations already associated with that village. Cached homes
provide occupied anchors without requiring a defense building. Unknown templates,
roads, and decorations do not independently establish occupancy. Template centers
and home positions are observations, never automatic navigation destinations.

The adapter does not guess residential status from template names. The caller
supplies the classifications; there is no new datapack classification format
yet. Service positions must lie in the observed horizontal footprint. These
observations are not a count of houses, current residents, or spawn allowances.

The lower-level `partition` API also accepts sites with explicit terrain
component IDs. A component represents reachability evidence supplied by the
terrain adapter, not a village, squad, or defense-network identity. Components
can split two sites within the same spatial cell when a barrier separates them.
Unknown components remain unknown and do not merge with known components.

## Spatial partition

Sites occupy 32-block cells. Negative coordinates use floor division. Vertical
layers remain separate. The builder groups compact 2-by-2 occupied squares when
possible, then uses bounded horizontal adjacency for remaining cells. Each area
contains at most four occupied cells. Empty cells and decorative outliers do not
bridge separate groups. Grouping is independent of input order and duplicate
observations.

Area IDs derive from the village UUID, cells, and component identity. Changes to
anchors within an unchanged group retain the area ID. A changed partition can
replace area IDs and invalidate assignments; it does not change village identity.
Rebuild on changed observation publications, not for every guard assessment.

Spatial adjacency is not path validation. A ready destination requires a loaded,
validated approach position and a known component. Every ready destination in
one area must use the same component. A mixed-component publication is rejected
before changing previous assignments; rebuild it with component-tagged sites.
Destination IDs are unique across the village, positions belong to their area,
and changes to position, component, state, or terrain revision cancel old leases.

If the partition needs more than 16 areas, the result reports `AREA_LIMIT` and
retains the first 16 independently partitioned areas in stable spatial order.
Verified destinations in those areas can still receive assignments. Empty
occupancy reports `NO_OCCUPIED_SITES` and creates no assignments. The cap never
joins disconnected areas or claims that omitted areas are covered.

Terrain surveys likewise retain verified nodes and edges when the anchor, domain,
or node cap is reached. `terrain_limit` identifies this partial observation.
Unknown positions have no component and cannot receive a path. Missing chunks,
blocked approaches, and disconnected components retain their existing eligibility
checks. Partial coverage can therefore keep the reachable local area active
without claiming that the whole village is patrolled.

## Guard availability and ownership

`VillageCoverageAvailability` combines the existing automatic-duty gate with
coverage-specific participation, health, equipment, native-action, and medical
inputs. These are supplied facts; the policy does not inspect entities or change
permanent membership.

Explicit follow commands, Hold Position, Patrol Bound, upstream patrol, active
squad commands, and saved routes prevent automatic coverage. A manual squad
requires its owner's opt-in before its members can be considered. Opt-in does
not override an active command or route. Dead, unloaded, NoAI, downed, transported,
injured, unequipped, fighting, recovering, or busy guards are unavailable. Routine
resupply, urgent medical service, and another movement owner also block coverage.

`VillageCoverageReservations` provides a shared guard index for coverage and
fixed-post leases across villages in one dimension. A guard cannot hold both,
and an area has at most one coverage lease. Each lease carries a unique token and
finite deadline. Stale callbacks cannot release or credit a replacement lease.

The live duty reservation book remains the source of fixed-post assignments.
Its publication hook preempts coverage and mirrors fixed-post claims into the
shared guard index. The live coverage gate also excludes any current fixed-post
assignment, using the same server-thread clock. Two allocators must never both
consider a guard free. Use one policy
instance per village and one shared coverage reservation book per dimension.

## Allocation and fairness

`VillageCoveragePolicy.schedule` accepts one complete, bounded snapshot of areas,
destinations, candidates, critical sites, confirmed-threat areas, and readiness.
It validates the publication before changing state. Time must be monotonic;
older layout revisions are rejected. Ready candidates still need a matching
terrain component and must fall within the configured travel-distance limit.

The planner retains valid in-flight leases until completion, interruption,
invalidation, or their original deadline. Repeated scheduling does not extend
the deadline. New assignments first cover explicitly identified critical sites,
then rotate other reachable areas. Critical sites can use all scarce guards;
the planner does not claim that remaining areas are protected in that case.

Within each priority group, areas receive fair rounds of offers. Issuing an
assignment consumes an offer even if combat interrupts it. Within a round, time
since arrival, site importance, and confirmed threats determine priority. This
allows threats to affect later rounds without letting an interrupted destination
take every turn. Blocked areas retain at most one old round of catch-up offers.
They cannot accumulate an unlimited queue while the reachable village rotates.

Destinations within an area also rotate by offers, so repeated visits to the
nearest point cannot leave the other approaches permanently unused. Guard
matching considers travel cost and alternative reachable areas. A bounded
augmenting search can move choices made within the current publication to avoid
wasting a guard needed elsewhere. It preserves higher-priority areas and does
not rearrange leases already issued to movement controllers.

Alert and Siege apply the confirmed-threat bonus. Watch does not. A configurable
number of currently free guards can remain in reserve after critical staffing,
while leaving at least one guard for rotating coverage when available. Reserves
are selected after matching so the only guard able to reach a priority area is
not withheld. The planner does not revoke existing valid leases solely to create
a reserve; new allocation opportunities reconcile that preference. Reserve IDs
are decisions for a future adapter, not navigation to invented reserve positions.

## Arrival, interruption, and recovery

Assignment is not a visit. `arrive` accepts only a current, unexpired lease and
an actual position within the area, within three blocks of the destination,
and within one block vertically. A successful arrival resets active visit age.
Routine coverage releases its lease; a critical post keeps its remaining lease.
Repeated arrival callbacks for that critical lease cannot continually refresh
the visit time.

Combat, follow commands, unload, or service detours call `interrupt` or remove
eligibility in the next publication. They release work without claiming a visit
or a navigation failure. `navigationFailed` releases the lease and suppresses
that destination with capped exponential backoff. Other destinations in the
area remain usable. New terrain evidence or the retry deadline permits another
attempt; successful arrival clears failure history.

Unloaded areas retain observation state but receive no work. Reload resets their
active age and puts them into the current patrol rotation with a bounded recovery
window. Offline time grants no neglect bonus. This layer requests no chunks,
paths, POIs, entities, or inventories and owns no world references.

`clear` disposes of this policy's coverage leases and transient history. It leaves
fixed-post claims to their owner. The shared book also supports scoped village
cleanup and dimension-wide disposal. An expiry index reclaims a bounded number
of old leases during new claims, so abandoned expired claims cannot permanently
fill the book. No lease or visit history is persisted by this API.

## Diagnostics and limits

Each plan contains assignments, reserve guard IDs, unavailable-guard counts by
reason, and one report per area. Area reports distinguish assignment, unload,
missing validation, blocked terrain, retry cooldown, no available guards, no
reachable guard, insufficient guards, and reservation capacity. Reports include
the last successful visit and bounded active age. `ASSIGNED` indicates reserved
work; it does not assert that a guard has arrived or the area is safe.

| Limit or default | Value |
| --- | --- |
| Site observations per partition | 2,048 |
| Areas per village / occupied cells per area | 16 / 4 |
| Destinations / candidates per village publication | 64 / 128 |
| Shared leases per dimension | 4,096 |
| Expiry-index removals per claim | At most 32, plus expired conflicting guard/target claims |
| Default lease | 400 simulation ticks; hard maximum 24,000 |
| Default retry delay | 100 ticks, doubling to 1,600; hard maximum 24,000 |
| Default reload recovery window / maximum active age | 200 / 24,000 ticks |
| Default importance / confirmed-threat bonus | 200 per importance level / 600 ticks |
| Default reserve in Alert/Siege | One free guard, subject to available staffing |
| Default maximum travel | 128 blocks; supported range 1–1,024 |

Settings are constructor-validated Java policy inputs. The live adapter uses
the defaults above; its independent enable setting is documented separately. All matching is bounded by the current village
publication. No decision enumerates historical village records. These bounds
are not measured TPS claims.

## Live adapter requirements

The movement layer must validate approach points against loaded terrain, safe
footing, hazards, doors, height differences, and reachable components. It must
recheck the actual guard, orders, native TaCZ state, ownership, loaded destination,
and terrain before starting movement or reporting arrival. It must provide
bounded path construction, progress sampling, failure callbacks, and lifecycle
cleanup. A policy-ready destination is an input contract, not proof produced by
this policy itself.

Automatic coverage must not rewrite Guard Orders or the upstream patrol flag.
The adapter must stop only navigation it owns. The live adapter provides readiness integration, scheduling budgets, command/
ledger diagnostics, and gameplay validation. Unit scenarios cover partitioning, fair allocation, explicit reachability,
precedence, matching, stale callbacks, recovery, unload, and resource limits;
they do not demonstrate a guard navigating a real village.

## Periodic survey replacement

A periodic survey prepares a replacement while the current complete navigation
map remains active. Identical completed observations preserve the publication,
destinations, leases, and visit history. Home-only observation updates let the
in-progress survey finish and queue a follow-up survey, rather than restarting
it repeatedly. Operator status reports `refreshing`
while an active map has a replacement survey in progress.

Changed footprints, block/chunk events, and survey errors still invalidate the
whole village. District-specific replacement is not implemented. Capped surveys
retain usable local components, but omitted areas need a later survey with space
inside the same limits. Running two survey maps temporarily
increases memory use within the existing village and terrain limits.

## Route step revalidation

Automatic coverage checks that each consecutive route node is an observed graph
edge and remains a supported cardinal step. It checks the complete route before
starting and upcoming edges during movement, including support, step headroom,
and resident chunks. A failed check cancels that automatic navigation attempt
through the existing retry policy. It does not change explicit Guard Orders.

Graph nodes identify the air cell above support, and step checks use the actual
surface height, including dry unlit campfire decks. Native navigation resolves
the floor height while moving. Water recovery, crossing reservations, and a
guarantee against all native navigation corner-cutting behavior remain outside
these checks. Broader generated bridge and Tidal/Sky/Integrated route coverage
still needs separate testing.

## Local alarm membership

Bell and damage alarms require one unambiguous village at the incident’s
elevation, using the bounded membership query with a 16-block evidence margin.
An affiliated guard can alert its own village only while locally recognized
there. Unaffiliated guards, villagers and bells use the same local evidence.
Unknown or overlapping membership produces no alarm. This prevents incidents
below a Sky settlement or during a distant manual excursion from alerting it.
Alarm lookup does not change guard affiliation.
